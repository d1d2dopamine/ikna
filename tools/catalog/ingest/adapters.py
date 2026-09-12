#!/usr/bin/env python3
"""Offline adapters from source dumps to Catalogue v2 normalized candidates."""

from __future__ import annotations

import gzip
import hashlib
import json
import os
import re
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
            policy.validate_origin_for_publication(origin.to_dict())
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
        policy.validate_origin_for_publication(origin.to_dict())
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
        policy.validate_origin_for_publication(origin.to_dict())
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
            policy.validate_origin_for_publication(origin.to_dict())
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
                policy.validate_origin_for_publication(origin.to_dict())
                yield _candidate(policy, lang, meaning_lang, context, meaning, origin)
            emitted += 1
            if max_rows is not None and emitted >= max_rows:
                break

def read_attribution_sidecar(path: str) -> dict[int, dict]:
    """Read line-number keyed record attribution for sources that require it."""
    rows: dict[int, dict] = {}
    with open(path, encoding="utf-8") as handle:
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

    The sidecar is mandatory. A plain Moses pair contains useful text but does not
    carry enough article/contributor metadata for ikna's publication gate.
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
        policy.validate_origin_for_publication(origin.to_dict())
        yield _candidate(policy, lang, meaning_lang, context, meaning, origin)
