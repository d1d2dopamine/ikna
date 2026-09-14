#!/usr/bin/env python3
"""Offline adapters from source dumps to Catalogue v2 normalized candidates."""

from __future__ import annotations

import gzip
import hashlib
import json
import os
import re
import sys
from contextlib import nullcontext
from collections import defaultdict
from itertools import zip_longest
from typing import Iterator

from .model import Candidate, Origin
from .registry import SourcePolicy

ISO3_TO_2 = {
    "eng": "en",
    "rus": "ru",
    "pol": "pl",
    "spa": "es",
    "fra": "fr",
    "deu": "de",
    "ita": "it",
    "por": "pt",
    "cmn": "zh",
    "jpn": "ja",
    "kor": "ko",
}

_WIKIMATRIX_NAME = re.compile(r"^WikiMatrix\.([a-z]{2,3})-([a-z]{2,3})\.tsv(?:\.gz)?$")


def _open_text(path: str):
    # ``-`` is useful for large, score-sorted corpora in CI: a downloader can
    # stream decompressed TSV into the adapter and the adapter can stop as soon
    # as the requested high-score prefix has been measured.
    if path == "-":
        return nullcontext(sys.stdin)
    if path.endswith(".gz"):
        return gzip.open(path, "rt", encoding="utf-8", errors="replace")
    return open(path, encoding="utf-8", errors="replace")


def _clean_segment(text: str) -> str:
    return " ".join(text.strip().split())


def _derived_ref(source_id: str, version: str, pair: str, line_number: int, text: str) -> str:
    digest = hashlib.sha256(text.encode("utf-8")).hexdigest()[:12]
    return "%s:%s:%s:%09d:%s" % (source_id, version, pair, line_number, digest)


def _candidate(
    policy: SourcePolicy,
    lang: str,
    meaning_lang: str,
    context: str,
    meaning: str,
    origin: Origin,
) -> Candidate:
    return Candidate(
        collection=policy.collection,
        lang=lang,
        meaning_lang=meaning_lang,
        context=_clean_segment(context),
        meaning=_clean_segment(meaning),
        origins=[origin],
    )


def _read_tatoeba_sentences(directory: str, wanted: set[str]) -> dict[str, tuple[str, str, str | None]]:
    """Read Tatoeba sentences, preferring the detailed export when present.

    `sentences_detailed.csv` adds the contributor who owns the sentence at export
    time. Catalogue v2 preserves that information when available, but the parser
    can still consume the smaller legacy `sentences.csv` fixture/dump.
    """
    detailed = os.path.join(directory, "sentences_detailed.csv")
    basic = os.path.join(directory, "sentences.csv")
    path = detailed if os.path.exists(detailed) else basic
    if not os.path.exists(path):
        raise ValueError("Tatoeba dump has neither sentences_detailed.csv nor sentences.csv")

    sentences: dict[str, tuple[str, str, str | None]] = {}
    with open(path, encoding="utf-8", errors="replace") as handle:
        for physical_line, line in enumerate(handle, start=1):
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 3:
                continue
            code = ISO3_TO_2.get(parts[1])
            if code not in wanted:
                continue
            text = _clean_segment(parts[2])
            if not text:
                continue
            contributor: str | None = None
            if path == detailed and len(parts) >= 4:
                raw = parts[3].strip()
                if raw and raw not in {"\\N", "NULL"}:
                    contributor = raw
            sentence_id = parts[0].strip()
            if not sentence_id:
                raise ValueError("%s:%d: empty Tatoeba sentence id" % (path, physical_line))
            sentences[sentence_id] = (code, text, contributor)
    return sentences


def iter_tatoeba(
    directory: str,
    policy: SourcePolicy,
    lang: str,
    meaning_lang: str,
    source_version: str | None = None,
) -> Iterator[Candidate]:
    """Read direct Tatoeba links for one directed language pair.

    This is an ingestion adapter, not the current quality sieve. It intentionally
    does not choose chunks or levels. Those remain later pipeline stages.
    """
    version = policy.resolve_source_version(source_version)
    links_path = os.path.join(directory, "links.csv")
    if not os.path.exists(links_path):
        raise ValueError("Tatoeba dump has no links.csv")

    sentences = _read_tatoeba_sentences(directory, {lang, meaning_lang})

    links: dict[str, list[str]] = defaultdict(list)
    with open(links_path, encoding="utf-8", errors="replace") as handle:
        for line in handle:
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 2:
                continue
            left, right = parts[0], parts[1]
            if left in sentences and right in sentences:
                links[left].append(right)

    emitted: set[tuple[str, str]] = set()
    for context_id, (context_lang, context, context_contributor) in sentences.items():
        if context_lang != lang:
            continue
        for meaning_id in links.get(context_id, ()):  # links are direct in the dump
            pair = (context_id, meaning_id)
            if pair in emitted:
                continue
            meaning_row = sentences.get(meaning_id)
            if meaning_row is None or meaning_row[0] != meaning_lang:
                continue
            emitted.add(pair)
            attribution = {}
            if context_contributor:
                attribution["contextContributor"] = context_contributor
            if meaning_row[2]:
                attribution["meaningContributor"] = meaning_row[2]
            origin = Origin(
                source_family=policy.id,
                source_version=version,
                context_ref="tatoeba:%s" % context_id,
                meaning_ref="tatoeba:%s" % meaning_id,
                attribution=attribution,
            )
            policy.validate_origin_for_ingestion(origin.to_dict())
            yield _candidate(policy, lang, meaning_lang, context, meaning_row[1], origin)



def iter_tatoeba_matrix(
    directory: str,
    policy: SourcePolicy,
    learn: set[str],
    meanings: set[str],
    source_version: str | None = None,
) -> Iterator[Candidate]:
    """Stream every requested directed Tatoeba pair in one pass over links.csv.

    Tatoeba translation links are relationships, not language directions. Reading
    the large dump once and considering both ends avoids one full dump scan for
    every learning/meaning pair while preserving the exact same direct-link rule.
    """
    version = policy.resolve_source_version(source_version)
    wanted = set(learn) | set(meanings)
    sentences = _read_tatoeba_sentences(directory, wanted)
    links_path = os.path.join(directory, "links.csv")
    if not os.path.exists(links_path):
        raise ValueError("Tatoeba dump has no links.csv")

    def emit(context_id: str, meaning_id: str) -> Candidate | None:
        context_row = sentences.get(context_id)
        meaning_row = sentences.get(meaning_id)
        if context_row is None or meaning_row is None:
            return None
        lang, context, context_contributor = context_row
        meaning_lang, meaning, meaning_contributor = meaning_row
        if lang == meaning_lang or lang not in learn or meaning_lang not in meanings:
            return None
        attribution = {}
        if context_contributor:
            attribution["contextContributor"] = context_contributor
        if meaning_contributor:
            attribution["meaningContributor"] = meaning_contributor
        origin = Origin(
            source_family=policy.id,
            source_version=version,
            context_ref="tatoeba:%s" % context_id,
            meaning_ref="tatoeba:%s" % meaning_id,
            attribution=attribution,
        )
        policy.validate_origin_for_ingestion(origin.to_dict())
        return _candidate(policy, lang, meaning_lang, context, meaning, origin)

    with open(links_path, encoding="utf-8", errors="replace") as handle:
        for physical_line, line in enumerate(handle, start=1):
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 2:
                continue
            left, right = parts[0].strip(), parts[1].strip()
            if not left or not right or left == right:
                continue
            # Tatoeba's export guarantees the reciprocal link is also present.
            # Keep one canonical orientation here, then emit both learning
            # directions below. This halves I/O without a multi-million-id set.
            try:
                if int(left) > int(right):
                    continue
            except ValueError:
                if left > right:
                    continue
            first = emit(left, right)
            if first is not None:
                yield first
            second = emit(right, left)
            if second is not None:
                yield second


MASSIVE_LOCALES = {
    "de": "de-DE",
    "en": "en-US",
    "es": "es-ES",
    "fr": "fr-FR",
    "it": "it-IT",
    "ja": "ja-JP",
    "ko": "ko-KR",
    "pl": "pl-PL",
    "pt": "pt-PT",
    "ru": "ru-RU",
    "zh": "zh-CN",
}


def _massive_data_dir(directory: str) -> str:
    candidates = [
        os.path.join(directory, "data"),
        os.path.join(directory, "1.1", "data"),
        directory,
    ]
    for candidate in candidates:
        if os.path.isdir(candidate) and any(
            os.path.exists(os.path.join(candidate, locale + ".jsonl"))
            for locale in MASSIVE_LOCALES.values()
        ):
            return candidate
    raise ValueError(
        "MASSIVE dump has no data/<locale>.jsonl files under %s" % directory
    )


def massive_judgment_counts(value: dict) -> dict[str, int]:
    """Return inspectable quality-vote counts from one MASSIVE source row.

    MASSIVE asks reviewers of localized rows whether the utterance sounds
    natural, is spelled correctly, is in the target language and matches the
    requested intent. The adapter uses those human judgments as a conservative
    pre-filter; the original en-US SLURP seed is unjudged by design. The later
    ikna phrase sieve still decides whether the utterance contains a useful
    learning target.
    """
    judgments = value.get("judgments")
    if not isinstance(judgments, list):
        judgments = []
    counts = {
        "judgments": 0,
        "natural": 0,
        "spelling": 0,
        "targetLanguage": 0,
        "intent": 0,
    }
    for judgment in judgments:
        if not isinstance(judgment, dict):
            continue
        counts["judgments"] += 1
        grammar = judgment.get("grammar_score")
        spelling = judgment.get("spelling_score")
        language = judgment.get("language_identification")
        intent = judgment.get("intent_score")
        if isinstance(grammar, (int, float)) and grammar >= 3:
            counts["natural"] += 1
        if isinstance(spelling, (int, float)) and spelling >= 2:
            counts["spelling"] += 1
        if language in {"target", 1, "1"}:
            counts["targetLanguage"] += 1
        if intent in {1, 2, "1", "2"}:
            counts["intent"] += 1
    return counts


def massive_row_passes_quality(
    value: dict,
    min_natural_votes: int = 2,
    min_spelling_votes: int = 2,
    min_target_language_votes: int = 2,
    min_intent_votes: int = 2,
) -> bool:
    counts = massive_judgment_counts(value)
    return (
        counts["natural"] >= min_natural_votes
        and counts["spelling"] >= min_spelling_votes
        and counts["targetLanguage"] >= min_target_language_votes
        and counts["intent"] >= min_intent_votes
    )


def read_massive_locale(
    directory: str,
    language: str,
    *,
    min_natural_votes: int = 2,
    min_spelling_votes: int = 2,
    min_target_language_votes: int = 2,
    min_intent_votes: int = 2,
) -> tuple[dict[str, dict], dict[str, int]]:
    """Read one MASSIVE locale and retain rows that pass its applicable quality gate.

    MASSIVE's ``en-US`` file is the original SLURP seed.  Upstream deliberately
    does not attach localization judgments to those rows, so an unjudged English
    seed row is allowed through this *source* gate and is still subjected to the
    normal ikna phrase/target sieve later.  Localized rows must pass the human
    review thresholds below.
    """
    locale = MASSIVE_LOCALES.get(language)
    if locale is None:
        raise ValueError("MASSIVE has no pinned ikna locale for %s" % language)
    path = os.path.join(_massive_data_dir(directory), locale + ".jsonl")
    if not os.path.exists(path):
        raise ValueError("MASSIVE locale file is missing: %s" % path)

    rows: dict[str, dict] = {}
    stats = defaultdict(int)
    with open(path, encoding="utf-8", errors="replace") as handle:
        for number, line in enumerate(handle, start=1):
            line = line.strip()
            if not line:
                continue
            stats["sourceRows"] += 1
            try:
                value = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError("%s:%d: invalid MASSIVE JSON" % (path, number)) from exc
            if not isinstance(value, dict):
                raise ValueError("%s:%d: MASSIVE row must be an object" % (path, number))
            if value.get("locale") != locale:
                raise ValueError(
                    "%s:%d: locale %r does not match %s"
                    % (path, number, value.get("locale"), locale)
                )
            source_id = str(value.get("id", "")).strip()
            utterance = _clean_segment(str(value.get("utt", "")))
            if not source_id or not utterance:
                stats["malformedRows"] += 1
                continue
            if source_id in rows:
                raise ValueError("%s:%d: duplicate MASSIVE id %s" % (path, number, source_id))
            quality = massive_judgment_counts(value)
            stats["judgments"] += quality["judgments"]
            judgments = value.get("judgments")
            is_unjudged_english_seed = locale == "en-US" and (
                not isinstance(judgments, list) or not judgments
            )
            if is_unjudged_english_seed:
                # The upstream en-US rows are the original SLURP seed, not a
                # localization, so MASSIVE intentionally provides no reviewer
                # judgments for them.  Do not fabricate votes or reject the
                # entire English side of the matrix.
                stats["qualityUnjudgedSeedRows"] += 1
            elif not massive_row_passes_quality(
                value,
                min_natural_votes=min_natural_votes,
                min_spelling_votes=min_spelling_votes,
                min_target_language_votes=min_target_language_votes,
                min_intent_votes=min_intent_votes,
            ):
                stats["qualityRejectedRows"] += 1
                continue
            stats["qualityAcceptedRows"] += 1
            rows[source_id] = {
                "id": source_id,
                "locale": locale,
                "utt": utterance,
                "partition": str(value.get("partition", "")),
                "scenario": str(value.get("scenario", "")),
                "intent": str(value.get("intent", "")),
                "quality": quality,
            }
    return rows, dict(stats)


def _massive_id_key(value: str) -> tuple[int, int | str]:
    return (0, int(value)) if value.isdigit() else (1, value)


def iter_massive_matrix(
    directory: str,
    policy: SourcePolicy,
    learn: set[str],
    meanings: set[str],
    source_version: str | None = None,
    *,
    min_natural_votes: int = 2,
    min_spelling_votes: int = 2,
    min_target_language_votes: int = 2,
    min_intent_votes: int = 2,
) -> Iterator[Candidate]:
    """Emit direct multiway-aligned MASSIVE candidates for requested languages.

    A shared MASSIVE ``id`` points back to the same SLURP seed utterance across
    locales.  We therefore align locales only by that stable id and never create
    a machine-translation pivot.  Rows must also agree on partition/scenario/
    intent so a corrupted or mismatched dump fails loudly.
    """
    version = policy.resolve_source_version(source_version)
    requested = sorted((set(learn) | set(meanings)) & set(MASSIVE_LOCALES))
    locale_rows: dict[str, dict[str, dict]] = {}
    for lang in requested:
        rows, _stats = read_massive_locale(
            directory,
            lang,
            min_natural_votes=min_natural_votes,
            min_spelling_votes=min_spelling_votes,
            min_target_language_votes=min_target_language_votes,
            min_intent_votes=min_intent_votes,
        )
        locale_rows[lang] = rows

    for lang in sorted(learn):
        left = locale_rows.get(lang, {})
        if not left:
            continue
        for meaning_lang in sorted(meanings):
            if lang == meaning_lang:
                continue
            right = locale_rows.get(meaning_lang, {})
            if not right:
                continue
            common = sorted(set(left) & set(right), key=_massive_id_key)
            for source_id in common:
                context_row = left[source_id]
                meaning_row = right[source_id]
                context_meta = (context_row["partition"], context_row["scenario"], context_row["intent"])
                meaning_meta = (meaning_row["partition"], meaning_row["scenario"], meaning_row["intent"])
                if context_meta != meaning_meta:
                    raise ValueError(
                        "MASSIVE id %s metadata mismatch between %s and %s"
                        % (source_id, context_row["locale"], meaning_row["locale"])
                    )
                origin = Origin(
                    source_family=policy.id,
                    source_version=version,
                    context_ref="massive:%s:%s:%s" % (version, context_row["locale"], source_id),
                    meaning_ref="massive:%s:%s:%s" % (version, meaning_row["locale"], source_id),
                )
                policy.validate_origin_for_ingestion(origin.to_dict())
                yield _candidate(
                    policy,
                    lang,
                    meaning_lang,
                    context_row["utt"],
                    meaning_row["utt"],
                    origin,
                )

def _aligned_lines(learn_path: str, meaning_path: str) -> Iterator[tuple[int, str, str]]:
    with _open_text(learn_path) as left, _open_text(meaning_path) as right:
        for number, pair in enumerate(zip_longest(left, right), start=1):
            learn_line, meaning_line = pair
            if learn_line is None or meaning_line is None:
                raise ValueError("parallel files have different line counts at line %d" % number)
            learn_text = _clean_segment(learn_line)
            meaning_text = _clean_segment(meaning_line)
            if learn_text and meaning_text:
                yield number, learn_text, meaning_text


def _scores(path: str | None) -> dict[int, float]:
    if not path:
        return {}
    result: dict[int, float] = {}
    with _open_text(path) as handle:
        for number, line in enumerate(handle, start=1):
            line = line.strip()
            if not line:
                continue
            try:
                result[number] = float(line)
            except ValueError as exc:
                raise ValueError("invalid alignment score at line %d" % number) from exc
    return result


def iter_wikimatrix(
    learn_path: str,
    meaning_path: str,
    policy: SourcePolicy,
    lang: str,
    meaning_lang: str,
    score_path: str | None = None,
    min_score: float | None = None,
    source_version: str | None = None,
) -> Iterator[Candidate]:
    """Read aligned WikiMatrix text files plus an optional one-score-per-line file."""
    version = policy.resolve_source_version(source_version)
    scores = _scores(score_path)
    pair_name = "%s-%s" % (lang, meaning_lang)
    for number, context, meaning in _aligned_lines(learn_path, meaning_path):
        score = scores.get(number)
        if min_score is not None:
            if score is None:
                raise ValueError("--min-score requires a score for every non-empty aligned line")
            if score < min_score:
                continue
        origin = Origin(
            source_family=policy.id,
            source_version=version,
            context_ref=_derived_ref(policy.id, version, pair_name, number, context),
            meaning_ref=_derived_ref(policy.id, version, pair_name, number, meaning),
            alignment_score=score,
        )
        policy.validate_origin_for_ingestion(origin.to_dict())
        yield _candidate(policy, lang, meaning_lang, context, meaning, origin)


def infer_wikimatrix_tsv_languages(path: str) -> tuple[str, str] | None:
    match = _WIKIMATRIX_NAME.match(os.path.basename(path))
    if match is None:
        return None
    return match.group(1), match.group(2)


def iter_wikimatrix_tsv(
    path: str,
    policy: SourcePolicy,
    lang: str,
    meaning_lang: str,
    min_score: float | None = None,
    source_version: str | None = None,
    tsv_languages: tuple[str, str] | None = None,
    max_rows: int | None = None,
) -> Iterator[Candidate]:
    """Read an upstream WikiMatrix v1 TSV(.gz).

    Upstream files store the pair in filename order, which is usually alphabetical
    and may be the reverse of the learner's requested direction. `tsv_languages`
    names the physical second/third TSV columns so the adapter can swap them safely.
    """
    version = policy.resolve_source_version(source_version)
    physical = tsv_languages or infer_wikimatrix_tsv_languages(path)
    if physical is None:
        raise ValueError(
            "cannot infer WikiMatrix TSV column languages from %s; pass --tsv-langs"
            % os.path.basename(path)
        )
    if set(physical) != {lang, meaning_lang} or physical[0] == physical[1]:
        raise ValueError(
            "WikiMatrix TSV languages %s-%s do not match requested %s-%s"
            % (physical[0], physical[1], lang, meaning_lang)
        )
    swap = physical == (meaning_lang, lang)

    pair_name = "%s-%s" % (lang, meaning_lang)
    emitted = 0
    with _open_text(path) as handle:
        for number, line in enumerate(handle, start=1):
            parts = line.rstrip("\n").split("\t", 2)
            if len(parts) != 3:
                raise ValueError("WikiMatrix line %d does not have score + two segments" % number)
            try:
                score = float(parts[0])
            except ValueError as exc:
                raise ValueError("WikiMatrix line %d has invalid alignment score" % number) from exc
            # Upstream WikiMatrix v1 files are sorted by margin score, so once
            # the threshold is crossed there is no useful tail left to scan.
            if min_score is not None and score < min_score:
                break
            first = _clean_segment(parts[1])
            second = _clean_segment(parts[2])
            if not first or not second:
                continue
            context, meaning = (second, first) if swap else (first, second)
            origin = Origin(
                source_family=policy.id,
                source_version=version,
                context_ref=_derived_ref(policy.id, version, pair_name, number, context),
                meaning_ref=_derived_ref(policy.id, version, pair_name, number, meaning),
                alignment_score=score,
            )
            policy.validate_origin_for_ingestion(origin.to_dict())
            yield _candidate(policy, lang, meaning_lang, context, meaning, origin)
            emitted += 1
            if max_rows is not None and emitted >= max_rows:
                break



def iter_wikimatrix_tsv_pair(
    path: str,
    policy: SourcePolicy,
    first_lang: str,
    second_lang: str,
    min_score: float | None = None,
    source_version: str | None = None,
    max_rows: int | None = None,
) -> Iterator[Candidate]:
    """Read one upstream WikiMatrix TSV once and emit both learning directions."""
    physical = infer_wikimatrix_tsv_languages(path)
    if physical is None:
        physical = (first_lang, second_lang)
    if set(physical) != {first_lang, second_lang}:
        raise ValueError("WikiMatrix pair filename does not match requested languages")
    # The one-direction adapter reopens the gzip. For production scale we keep
    # one pass here and materialize the two symmetric learning directions.
    version = policy.resolve_source_version(source_version)
    emitted = 0
    with _open_text(path) as handle:
        for number, line in enumerate(handle, start=1):
            parts = line.rstrip("\n").split("\t", 2)
            if len(parts) != 3:
                raise ValueError("WikiMatrix line %d does not have score + two segments" % number)
            try:
                score = float(parts[0])
            except ValueError as exc:
                raise ValueError("WikiMatrix line %d has invalid alignment score" % number) from exc
            if min_score is not None and score < min_score:
                break
            left = _clean_segment(parts[1])
            right = _clean_segment(parts[2])
            if not left or not right:
                continue
            mapping = {physical[0]: left, physical[1]: right}
            pair_name = "%s-%s" % physical
            left_ref = _derived_ref(policy.id, version, pair_name, number, left)
            right_ref = _derived_ref(policy.id, version, pair_name, number, right)
            for lang, meaning_lang, context, meaning, context_ref, meaning_ref in (
                (first_lang, second_lang, mapping[first_lang], mapping[second_lang],
                 left_ref if physical[0] == first_lang else right_ref,
                 right_ref if physical[1] == second_lang else left_ref),
                (second_lang, first_lang, mapping[second_lang], mapping[first_lang],
                 right_ref if physical[1] == second_lang else left_ref,
                 left_ref if physical[0] == first_lang else right_ref),
            ):
                origin = Origin(
                    source_family=policy.id,
                    source_version=version,
                    context_ref=context_ref,
                    meaning_ref=meaning_ref,
                    alignment_score=score,
                )
                policy.validate_origin_for_ingestion(origin.to_dict())
                yield _candidate(policy, lang, meaning_lang, context, meaning, origin)
            emitted += 1
            if max_rows is not None and emitted >= max_rows:
                break

def read_attribution_sidecar(path: str) -> dict[int, dict]:
    """Read line-number keyed record attribution for sources that require it."""
    rows: dict[int, dict] = {}
    with _open_text(path) as handle:
        for physical_line, line in enumerate(handle, start=1):
            line = line.strip()
            if not line:
                continue
            try:
                value = json.loads(line)
            except ValueError as exc:
                raise ValueError("%s:%d: invalid JSON" % (path, physical_line)) from exc
            if not isinstance(value, dict):
                raise ValueError("%s:%d: attribution row must be an object" % (path, physical_line))
            number = value.get("line")
            if not isinstance(number, int) or number < 1:
                raise ValueError("%s:%d: attribution line must be a positive integer" % (path, physical_line))
            if number in rows:
                raise ValueError("%s: duplicate attribution for line %d" % (path, number))
            rows[number] = {key: item for key, item in value.items() if key != "line"}
    return rows


def iter_globalvoices(
    learn_path: str,
    meaning_path: str,
    attribution_path: str,
    policy: SourcePolicy,
    lang: str,
    meaning_lang: str,
    source_version: str | None = None,
) -> Iterator[Candidate]:
    """Read aligned Global Voices segments with explicit record attribution.

    The sidecar is mandatory. Native OPUS text is useful, but document identity
    alone does not carry enough article/contributor metadata for ikna's publication gate.
    """
    version = policy.resolve_source_version(source_version)
    attribution = read_attribution_sidecar(attribution_path)
    pair_name = "%s-%s" % (lang, meaning_lang)
    for number, context, meaning in _aligned_lines(learn_path, meaning_path):
        row = attribution.get(number)
        if row is None:
            raise ValueError("Global Voices line %d has no attribution sidecar row" % number)
        origin = Origin(
            source_family=policy.id,
            source_version=version,
            context_ref=_derived_ref(policy.id, version, pair_name, number, context),
            meaning_ref=_derived_ref(policy.id, version, pair_name, number, meaning),
            attribution=row,
        )
        # Fail during ingestion, not after a million-line build.
        policy.validate_origin_for_ingestion(origin.to_dict())
        yield _candidate(policy, lang, meaning_lang, context, meaning, origin)
