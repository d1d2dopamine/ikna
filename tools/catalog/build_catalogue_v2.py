#!/usr/bin/env python3
"""Build Catalogue v2 decks from normalized corpus candidates.

The source-specific work has already happened in ``ingest_sources.py``. This
builder owns the source-independent quality sieve, target extraction, level
assignment, optional morphology/phonetics enrichment, and the public v2 index.

It is intentionally disk-backed. Candidate text is staged in SQLite and each
collection/language is ranked separately, so a million-card build does not need
the complete corpus in Python objects at once.
"""

from __future__ import annotations

import argparse
import gzip
import json
import os
import sqlite3
import sys
from collections import Counter, defaultdict
from datetime import date
from pathlib import Path
from typing import Any, Iterable

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import catalogue_core as core
from catalogue_v2 import TARGET_IDENTITY_METHOD, TARGET_IDENTITY_VERSION, target_id
from ingest.model import Candidate
from ingest.registry import SourceRegistry
from morphology.model import MORPHOLOGY_POLICY, MORPHOLOGY_RULE_VERSION
from morphology.store import MorphologyResolver
from segmentation import IcuUnavailable, prepare, utf16_length

DEFAULT_REGISTRY = HERE / "sources" / "catalogue-v2-sources.json"
MAX_INDEX_BYTES = 2 * 1024 * 1024
MAX_DECK_BYTES = 24 * 1024 * 1024
MAX_RELEASE_ASSETS = 650  # leaves migration headroom beside the existing ~325 catalog assets
COLLECTIONS = {
    "everyday": {
        "id": "everyday",
        "title": "Everyday",
        "description": "Short, general and everyday language.",
    },
    "knowledge": {
        "id": "knowledge",
        "title": "Knowledge",
        "description": "Neutral and explanatory language from reference material.",
    },
    "world": {
        "id": "world",
        "title": "World",
        "description": "Journalism, society and culture.",
    },
}


def open_jsonl(path: str):
    if path.endswith(".gz"):
        return gzip.open(path, "rt", encoding="utf-8", errors="replace")
    return open(path, encoding="utf-8", errors="replace")


def primary_origin(record: Candidate) -> dict[str, Any]:
    families = {origin.source_family for origin in record.origins}
    if len(families) != 1:
        raise ValueError(
            "candidate %s merges several source families inside one collection: %s"
            % (record.id, ", ".join(sorted(families)))
        )
    # Alignment score is useful evidence when duplicate mined pairs were merged.
    # Prefer the strongest scored origin, otherwise preserve input order.
    ordered = sorted(
        enumerate(record.origins),
        key=lambda item: (
            item[1].alignment_score is not None,
            item[1].alignment_score if item[1].alignment_score is not None else float("-inf"),
            -item[0],
        ),
        reverse=True,
    )
    return ordered[0][1].to_dict()


STAGING_SCHEMA = """
PRAGMA journal_mode=OFF;
PRAGMA synchronous=OFF;
PRAGMA temp_store=FILE;
CREATE TABLE candidate (
    seq INTEGER PRIMARY KEY AUTOINCREMENT,
    id TEXT UNIQUE NOT NULL,
    collection TEXT NOT NULL,
    lang TEXT NOT NULL,
    meaning_lang TEXT NOT NULL,
    context TEXT NOT NULL,
    meaning TEXT NOT NULL,
    source_family TEXT NOT NULL,
    source_version TEXT NOT NULL,
    context_ref TEXT NOT NULL,
    meaning_ref TEXT NOT NULL,
    alignment_score REAL,
    attribution_json TEXT NOT NULL
);
CREATE INDEX candidate_pair ON candidate(collection, lang, meaning_lang, seq);
CREATE TABLE context (
    collection TEXT NOT NULL,
    lang TEXT NOT NULL,
    source_family TEXT NOT NULL,
    context_ref TEXT NOT NULL,
    context TEXT NOT NULL,
    PRIMARY KEY(collection, lang, source_family, context_ref)
);
CREATE INDEX context_language ON context(collection, lang);
"""


def stage_candidates(inputs: Iterable[str], db_path: Path) -> dict[str, Any]:
    if db_path.exists():
        db_path.unlink()
    db = sqlite3.connect(db_path)
    counts = Counter()
    try:
        db.executescript(STAGING_SCHEMA)
        for input_path in inputs:
            with open_jsonl(input_path) as handle:
                for number, line in enumerate(handle, start=1):
                    line = line.strip()
                    if not line:
                        continue
                    try:
                        record = Candidate.from_dict(json.loads(line))
                        origin = primary_origin(record)
                    except Exception as exc:
                        raise ValueError("%s:%d: %s" % (input_path, number, exc)) from exc
                    counts["inputCandidates"] += 1
                    before = db.total_changes
                    db.execute(
                        """
                        INSERT OR IGNORE INTO candidate(
                            id,collection,lang,meaning_lang,context,meaning,source_family,
                            source_version,context_ref,meaning_ref,alignment_score,attribution_json
                        ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                        """,
                        (
                            record.id,
                            record.collection,
                            record.lang,
                            record.meaning_lang,
                            record.context,
                            record.meaning,
                            origin["sourceFamily"],
                            origin["sourceVersion"],
                            origin["contextRef"],
                            origin["meaningRef"],
                            origin.get("alignmentScore"),
                            json.dumps(origin.get("attribution") or {}, ensure_ascii=False, sort_keys=True),
                        ),
                    )
                    inserted = db.total_changes > before
                    if inserted:
                        counts["uniqueCandidates"] += 1
                        db.execute(
                            "INSERT OR IGNORE INTO context VALUES(?,?,?,?,?)",
                            (
                                record.collection,
                                record.lang,
                                origin["sourceFamily"],
                                origin["contextRef"],
                                record.context,
                            ),
                        )
                    else:
                        counts["duplicateCandidates"] += 1
                    if counts["inputCandidates"] % 25000 == 0:
                        db.commit()
        db.commit()
        counts["uniqueContexts"] = db.execute("SELECT COUNT(*) FROM context").fetchone()[0]
        counts["pairs"] = db.execute(
            "SELECT COUNT(*) FROM (SELECT DISTINCT collection,lang,meaning_lang FROM candidate)"
        ).fetchone()[0]
        counts["collections"] = db.execute(
            "SELECT COUNT(DISTINCT collection) FROM candidate"
        ).fetchone()[0]
        return dict(counts)
    finally:
        db.close()


def build_ranks(db: sqlite3.Connection, collection: str, lang: str) -> dict[str, int]:
    counts: Counter[str] = Counter()
    for (text,) in db.execute(
        "SELECT context FROM context WHERE collection=? AND lang=? ORDER BY rowid",
        (collection, lang),
    ):
        counts.update(word.lower() for word in core.words(text, lang))
    return {form: position for position, (form, _n) in enumerate(counts.most_common(), start=1)}


def phrase_choices(
    sentence: str,
    ranks: dict[str, int],
    lang: str,
    function_top: int,
) -> list[tuple[int, str, str, str]]:
    """Eligible exact targets in rarest-first order.

    The v1 sieve is preserved: glue is ignored, the target must be one complete
    token, and it must occur exactly once so the UTF-16 span is unambiguous.
    Catalogue v2 changes only ownership: repeated targets become contexts of one
    target instead of independent scheduling units.
    """
    found = core.words(sentence, lang)
    choices: list[tuple[int, str, str, str]] = []
    for surface in found:
        low = surface.lower()
        rank = ranks.get(low)
        if rank is None or rank <= function_top:
            continue
        if len(surface) < core.minimum_phrase(lang) or utf16_length(surface) > core.MAX_PHRASE:
            continue
        if sum(1 for other in found if other.lower() == low) != 1:
            continue
        tid = target_id(lang, surface)
        choices.append((rank, surface, tid, core.level_of(rank)))
    choices.sort(key=lambda item: item[0], reverse=True)
    return choices


def tatoeba_credit(context_ref: str) -> str | None:
    if context_ref.startswith("tatoeba:"):
        tail = context_ref.split(":", 1)[1]
        if tail.isdigit():
            return "Tatoeba #" + tail
    return None


def transcriber_factory(enabled: bool, russian_stress: str | None):
    cache: dict[str, Any] = {}

    def get(code: str):
        if not enabled:
            return None
        if code in cache:
            return cache[code]
        made = None
        try:
            beside = HERE.parent / "phonetics"
            if str(beside) not in sys.path:
                sys.path.insert(0, str(beside))
            import g2p

            made = g2p.transcriber_for(code, russian_stress)
            print("  phonetics ready for %s" % code, flush=True)
        except Exception as exc:
            print("  no phonetics for %s: %s" % (code, exc), flush=True)
        cache[code] = made
        return made

    return get


def pair_candidates(
    db: sqlite3.Connection,
    collection: str,
    lang: str,
    meaning_lang: str,
):
    """Yield one preferred meaning for each source context in deterministic order."""
    rows = db.execute(
        """
        SELECT seq,context,meaning,source_family,source_version,context_ref,meaning_ref,
               alignment_score,attribution_json
        FROM candidate
        WHERE collection=? AND lang=? AND meaning_lang=?
        ORDER BY context_ref, LENGTH(meaning), seq
        """,
        (collection, lang, meaning_lang),
    )
    last_context: tuple[str, str] | None = None
    for row in rows:
        current = (row[3], row[5])
        if current == last_context:
            continue
        last_context = current
        yield row


def make_context(
    row: tuple,
    lang: str,
    ranks: dict[str, int],
    selected: dict[str, dict[str, dict[str, Any]]],
    seen_contexts: dict[str, set[str]],
    max_targets_per_level: int,
    max_contexts_per_target: int,
    function_top: int,
    phonetics: Any,
    morphology: MorphologyResolver | None,
    stats: Counter[str],
    existing_only: bool,
) -> tuple[str, str, dict[str, Any]] | None:
    (_seq, sentence, meaning, source_family, _source_version, context_ref, meaning_ref, _score, _attrib) = row
    if len(sentence) < (6 if lang in core.CJK else core.MIN_SENTENCE) or utf16_length(sentence) > core.MAX_SENTENCE:
        stats["sentence length"] += 1
        return None
    if not meaning or utf16_length(meaning) > core.MAX_TRANSLATION:
        stats["translation length"] += 1
        return None

    choice = None
    choices = phrase_choices(sentence, ranks, lang, function_top)
    for rank, surface, tid, level in choices:
        if context_ref in seen_contexts.get(tid, set()):
            continue
        existing = selected[level].get(tid)
        if existing_only:
            if existing is not None and 1 + len(existing.get("contexts", [])) < max_contexts_per_target:
                choice = (rank, surface, tid, level)
                break
        else:
            if existing is None and len(selected[level]) < max_targets_per_level:
                choice = (rank, surface, tid, level)
                break
    if choice is None:
        stats["nothing to teach"] += 1
        return None

    rank, surface, tid, level = choice
    span = core.offsets(sentence, surface, lang)
    if span is None:
        stats["phrase not in sentence"] += 1
        return None

    translation = meaning
    credit = tatoeba_credit(context_ref)
    if credit:
        translation += core.SOURCE_MARK + credit

    item: dict[str, Any] = {
        "context": sentence,
        "translation": translation,
        "targetStart": span[0],
        "targetEnd": span[1],
        "freqRank": rank,
        "tokens": core.token_list(sentence, ranks, {}, lang, function_top),
        "contextId": context_ref,
        "meaningId": meaning_ref,
        "sourceFamily": source_family,
    }
    if phonetics:
        ipa_context = phonetics.line(sentence)
        if ipa_context:
            item["ipaContext"] = ipa_context
    if morphology is not None:
        item, morph_stats = morphology.enrich_card(item, lang)
        stats.update({"morphology " + key: value for key, value in morph_stats.items()})
    return level, tid, {"text": surface, **item}


def add_context_to_group(
    group: dict[str, Any],
    context: dict[str, Any],
    phonetics: Any,
    stats: Counter[str],
) -> None:
    """Append a distinct natural context without creating another card row."""
    if context["contextId"] == group["contextId"]:
        return
    alt = {key: value for key, value in context.items() if key != "text"}
    group.setdefault("contexts", []).append(alt)
    stats["alternative contexts retained"] += 1


def new_target_group(
    tid: str,
    context: dict[str, Any],
    phonetics: Any,
    stats: Counter[str],
) -> dict[str, Any]:
    group = {"targetId": tid, **context}
    if phonetics:
        ipa = phonetics.line(context["text"])
        if ipa:
            group["ipa"] = ipa
        stats["card transcribed" if (ipa or context.get("ipaContext")) else "card not transcribed"] += 1
    return group


def _deck_row_bytes(deck_id: str, position: int, target: dict[str, Any]) -> bytes:
    # id remains deck-local for v1-compatible import. targetId is global
    # catalogue identity; runtime shared scheduling is introduced only when the
    # learner database can represent deck membership safely.
    row = {"id": "%s-%05d" % (deck_id, position), **target}
    return (json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")


def fit_deck_to_byte_cap(
    deck_id: str,
    targets: list[dict[str, Any]],
    max_bytes: int = MAX_DECK_BYTES,
) -> tuple[int, int]:
    """Fit a logical deck under the client byte ceiling without losing targets.

    Alternative contexts are optional evidence around one learning target.  If a
    very rich 8k-target deck grows beyond the static-download ceiling, dropping a
    few alternatives is safer than either rejecting the whole catalogue or
    silently reducing the number of learning targets.  Every target always keeps
    its primary context.

    Returns ``(final_size, removed_alternative_contexts)``.  The function mutates
    only each target's optional ``contexts`` list.
    """
    row_sizes = [_deck_row_bytes(deck_id, i, target) for i, target in enumerate(targets, start=1)]
    total = sum(map(len, row_sizes))
    if total <= max_bytes:
        return total, 0

    # First prove that all primary contexts fit.  If they do not, there is no
    # honest way to keep the requested target count within the client's hard
    # download ceiling; fail with a useful diagnostic rather than dropping
    # learning targets behind the caller's back.
    primary_sizes: list[int] = []
    for i, target in enumerate(targets, start=1):
        base = dict(target)
        base.pop("contexts", None)
        primary_sizes.append(len(_deck_row_bytes(deck_id, i, base)))
    primary_total = sum(primary_sizes)
    if primary_total > max_bytes:
        raise ValueError(
            "%s primary contexts alone are %.1f MiB; reduce --max-deck or split the logical deck"
            % (deck_id, primary_total / 1048576)
        )

    # Remove the largest optional context contribution first.  This reaches the
    # cap with the fewest removals in the common case and is deterministic.  A
    # target with three contexts may lose its third before another target loses
    # its second, but no target ever loses the primary context.
    import heapq

    heap: list[tuple[int, int]] = []

    def marginal_saving(index: int) -> int:
        target = targets[index]
        contexts = target.get("contexts") or []
        if not contexts:
            return 0
        before = len(_deck_row_bytes(deck_id, index + 1, target))
        trial = dict(target)
        remain = list(contexts[:-1])
        if remain:
            trial["contexts"] = remain
        else:
            trial.pop("contexts", None)
        after = len(_deck_row_bytes(deck_id, index + 1, trial))
        return before - after

    for index, target in enumerate(targets):
        if target.get("contexts"):
            saving = marginal_saving(index)
            if saving > 0:
                heapq.heappush(heap, (-saving, index))

    removed = 0
    while total > max_bytes and heap:
        neg_saving, index = heapq.heappop(heap)
        saving = -neg_saving
        contexts = list(targets[index].get("contexts") or [])
        if not contexts:
            continue
        contexts.pop()
        if contexts:
            targets[index]["contexts"] = contexts
        else:
            targets[index].pop("contexts", None)
        total -= saving
        removed += 1
        next_saving = marginal_saving(index)
        if next_saving > 0:
            heapq.heappush(heap, (-next_saving, index))

    # Recompute exactly instead of trusting accumulated deltas; this also makes
    # the size returned to index.json byte-for-byte identical to the file we
    # are about to write.
    total = sum(len(_deck_row_bytes(deck_id, i, target)) for i, target in enumerate(targets, start=1))
    if total > max_bytes:
        raise ValueError("%s could not be fitted below the deck byte ceiling" % deck_id)
    return total, removed


def write_deck(path: Path, deck_id: str, targets: list[dict[str, Any]]) -> tuple[int, int]:
    expected_size, removed = fit_deck_to_byte_cap(deck_id, targets)
    with path.open("wb") as handle:
        for position, target in enumerate(targets, start=1):
            handle.write(_deck_row_bytes(deck_id, position, target))
    size = path.stat().st_size
    if size != expected_size or size > MAX_DECK_BYTES:
        raise ValueError("%s deck byte accounting mismatch" % path.name)
    return size, removed

def tier(chunk_count: int, threshold: int) -> str:
    return "full" if chunk_count >= threshold else "thin"


def source_family_dict(policy) -> dict[str, Any]:
    return {
        "id": policy.id,
        "title": policy.title,
        "homepage": policy.homepage,
        "licence": policy.licence_name,
        "attribution": policy.attribution,
    }


def build(args: argparse.Namespace) -> dict[str, Any]:
    learn = [part.strip().lower() for part in args.learn.split(",") if part.strip()]
    meanings = [part.strip().lower() for part in args.meanings.split(",") if part.strip()]
    unsupported = (set(learn) - set(core.LEARNABLE)) | (set(meanings) - set(core.MEANINGS))
    if unsupported:
        raise ValueError("unsupported languages: %s" % ", ".join(sorted(unsupported)))
    try:
        segmentation = prepare(learn)
    except IcuUnavailable as exc:
        raise ValueError(str(exc)) from exc

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    staging = Path(args.staging or (out / ".catalogue-v2.sqlite3"))
    stage_stats = stage_candidates(args.candidates, staging)
    print("staged: " + json.dumps(stage_stats, sort_keys=True), flush=True)

    registry = SourceRegistry.load(args.registry)
    morphology: MorphologyResolver | None = MorphologyResolver(args.morphology_db) if args.morphology_db else None
    morphology_public = morphology.public_datasets() if morphology else []
    morphology_by_lang: dict[str, list[str]] = defaultdict(list)
    for dataset in morphology_public:
        morphology_by_lang[dataset["lang"]].append(dataset["id"])

    phonetics_for = transcriber_factory(args.phonetics, args.russian_stress)
    db = sqlite3.connect(staging)
    stats = Counter()
    decks: list[dict[str, Any]] = []
    collection_pairs: list[dict[str, Any]] = []
    active_sources: set[str] = set()
    output_contexts: set[tuple[str, str]] = set()
    output_targets: set[tuple[str, str]] = set()
    try:
        mixed = db.execute(
            "SELECT collection,lang,meaning_lang,COUNT(DISTINCT source_family) "
            "FROM candidate GROUP BY collection,lang,meaning_lang HAVING COUNT(DISTINCT source_family)>1"
        ).fetchall()
        if mixed:
            raise ValueError(
                "v2 builder currently requires one active source family per collection/pair; "
                "merge policy must be specified before mixing sources: %s" % (mixed[:5],)
            )
        pair_rows = db.execute(
            "SELECT DISTINCT collection,lang,meaning_lang,source_family FROM candidate ORDER BY collection,lang,meaning_lang"
        ).fetchall()
        rank_cache: dict[tuple[str, str], dict[str, int]] = {}
        for collection, lang, meaning_lang, source_family in pair_rows:
            if lang not in learn or meaning_lang not in meanings or lang == meaning_lang:
                continue
            policy = registry.get(source_family)
            if policy.collection != collection:
                raise ValueError("source %s is not registered for collection %s" % (source_family, collection))
            key = (collection, lang)
            if key not in rank_cache:
                print("ranking %s/%s" % key, flush=True)
                rank_cache[key] = build_ranks(db, collection, lang)
            ranks = rank_cache[key]
            if not ranks:
                continue

            targets_by_level: dict[str, dict[str, dict[str, Any]]] = {level: {} for level in core.LEVELS}
            seen_contexts: dict[str, set[str]] = defaultdict(set)
            pair_stats = Counter()
            transcriber = phonetics_for(lang)

            # Pass 1: choose unique learning targets with the mature v1 sieve.
            # A repeated exact target is not another card.
            for row in pair_candidates(db, collection, lang, meaning_lang):
                made = make_context(
                    row, lang, ranks, targets_by_level, seen_contexts,
                    args.max_deck, args.contexts_per_target, args.function_top,
                    transcriber, morphology, pair_stats, existing_only=False,
                )
                if made is None:
                    continue
                level, tid, context = made
                seen_contexts[tid].add(context["contextId"])
                targets_by_level[level][tid] = new_target_group(tid, context, transcriber, pair_stats)
                if all(len(targets_by_level[level]) >= args.max_deck for level in core.LEVELS):
                    break

            # Pass 2: revisit the same corpus only to attach unseen natural
            # contexts to targets already selected above. No scheduling unit is
            # created in this pass.
            if args.contexts_per_target > 1:
                open_targets = {
                    tid
                    for groups in targets_by_level.values()
                    for tid, group in groups.items()
                    if 1 + len(group.get("contexts", [])) < args.contexts_per_target
                }
                for row in pair_candidates(db, collection, lang, meaning_lang):
                    if not open_targets:
                        break
                    made = make_context(
                        row, lang, ranks, targets_by_level, seen_contexts,
                        args.max_deck, args.contexts_per_target, args.function_top,
                        transcriber, morphology, pair_stats, existing_only=True,
                    )
                    if made is None:
                        continue
                    level, tid, context = made
                    seen_contexts[tid].add(context["contextId"])
                    group = targets_by_level[level][tid]
                    add_context_to_group(group, context, transcriber, pair_stats)
                    if 1 + len(group.get("contexts", [])) >= args.contexts_per_target:
                        open_targets.discard(tid)

            pair_target_count = sum(len(groups) for groups in targets_by_level.values())
            pair_context_count = sum(
                1 + len(group.get("contexts", []))
                for groups in targets_by_level.values()
                for group in groups.values()
            )
            if pair_target_count < args.min_deck:
                stats["pair below minimum"] += 1
                continue
            active_sources.add(source_family)
            pair_decks = 0
            for level in core.LEVELS:
                targets = list(targets_by_level[level].values())
                if len(targets) < args.min_deck:
                    continue
                deck_id = "%s-%s-%s-%s" % (lang, meaning_lang, collection, level)
                path = out / (deck_id + ".jsonl")
                size, contexts_trimmed = write_deck(path, deck_id, targets)
                if contexts_trimmed:
                    pair_stats["alternative contexts dropped for deck byte cap"] += contexts_trimmed
                pair_decks += 1
                deck_contexts = sum(1 + len(target.get("contexts", [])) for target in targets)
                for target in targets:
                    output_targets.add((lang, target["targetId"]))
                    output_contexts.add((lang, target["contextId"]))
                    for alt in target.get("contexts", []):
                        output_contexts.add((lang, alt["contextId"]))
                decks.append(
                    {
                        "id": deck_id,
                        "title": "%s from %s - %s - %s"
                        % (
                            core.NAMES.get(lang, lang),
                            core.NAMES.get(meaning_lang, meaning_lang),
                            COLLECTIONS[collection]["title"],
                            level,
                        ),
                        "lang": lang,
                        "meaningLang": meaning_lang,
                        "chunkCount": len(targets),
                        "contextCount": deck_contexts,
                        "file": path.name,
                        "sizeBytes": size,
                        "subject": "",
                        "level": level,
                        "licence": policy.licence_name,
                        "attribution": policy.attribution,
                        "sources": ["%s, %s" % (policy.title, policy.licence_name)],
                        "phonetics": any(target.get("ipa") or target.get("ipaContext") for target in targets),
                        "version": 2,
                        "collection": collection,
                        "sourceFamily": source_family,
                        **(
                            {"morphologySources": sorted(morphology_by_lang.get(lang, []))}
                            if morphology_by_lang.get(lang)
                            else {}
                        ),
                    }
                )
            if pair_decks:
                written_decks = [
                    deck for deck in decks
                    if deck["collection"] == collection
                    and deck["lang"] == lang
                    and deck["meaningLang"] == meaning_lang
                ]
                written_targets = sum(deck["chunkCount"] for deck in written_decks)
                written_contexts = sum(deck["contextCount"] for deck in written_decks)
                collection_pairs.append(
                    {
                        "collection": collection,
                        "lang": lang,
                        "meaningLang": meaning_lang,
                        "tier": tier(written_targets, args.full_threshold),
                        "deckCount": pair_decks,
                        "chunkCount": written_targets,
                        "contextCount": written_contexts,
                    }
                )
            stats.update(pair_stats)
            retained_contexts = (
                collection_pairs[-1]["contextCount"]
                if pair_decks and collection_pairs
                else pair_context_count
            )
            trimmed_note = ""
            if retained_contexts != pair_context_count:
                trimmed_note = " / %d selected before byte cap" % pair_context_count
            print(
                "  %s %s->%s: %d targets / %d contexts%s (%s)"
                % (
                    collection,
                    lang,
                    meaning_lang,
                    pair_target_count,
                    retained_contexts,
                    trimmed_note,
                    ", ".join("%s=%d" % (level, len(targets_by_level[level])) for level in core.LEVELS),
                ),
                flush=True,
            )

        aggregate: dict[tuple[str, str], dict[str, int]] = defaultdict(lambda: {"deckCount": 0, "chunkCount": 0, "contextCount": 0})
        for row in collection_pairs:
            item = aggregate[(row["lang"], row["meaningLang"])]
            item["deckCount"] += row["deckCount"]
            item["chunkCount"] += row["chunkCount"]
            item["contextCount"] += row.get("contextCount", row["chunkCount"])
        pairs = [
            {
                "lang": lang,
                "meaningLang": meaning,
                "tier": tier(value["chunkCount"], args.full_threshold),
                "deckCount": value["deckCount"],
                "chunkCount": value["chunkCount"],
                "contextCount": value["contextCount"],
            }
            for (lang, meaning), value in sorted(aggregate.items())
        ]

        source_families = [source_family_dict(registry.get(source_id)) for source_id in sorted(active_sources)]
        index: dict[str, Any] = {
            "version": 2,
            "catalogueVersion": 2,
            "builtAt": date.today().isoformat(),
            "segmentation": segmentation,
            "targetIdentity": {
                "version": TARGET_IDENTITY_VERSION,
                "method": TARGET_IDENTITY_METHOD,
            },
            "collections": [COLLECTIONS[name] for name in ("everyday", "knowledge", "world")],
            "sourceFamilies": source_families,
            "decks": decks,
            "pairs": pairs,
            "collectionPairs": collection_pairs,
        }
        if morphology is not None:
            index["morphology"] = {
                "ruleVersion": MORPHOLOGY_RULE_VERSION,
                "policy": MORPHOLOGY_POLICY,
                "targetIdentityAffected": False,
                "datasets": morphology_public,
            }

        raw = json.dumps(index, ensure_ascii=False, indent=2) + "\n"
        if len(raw.encode("utf-8")) > MAX_INDEX_BYTES:
            raise ValueError("index.json exceeds the 2 MiB client limit")
        # index + BUILD.md + BUILD.json are release assets too. Leave extra headroom.
        if len(decks) + 3 > MAX_RELEASE_ASSETS:
            raise ValueError("Catalogue would create %d release assets; keep it below %d" % (len(decks) + 3, MAX_RELEASE_ASSETS))
        (out / "index.json").write_text(raw, encoding="utf-8")

        build_info = {
            "catalogueVersion": 2,
            "stage": stage_stats,
            "output": {
                "targetDeckMemberships": sum(deck["chunkCount"] for deck in decks),
                "contexts": sum(deck.get("contextCount", deck["chunkCount"]) for deck in decks),
                "decks": len(decks),
                "pairs": len(pairs),
                "collectionPairs": len(collection_pairs),
                "uniqueSourceContexts": len(output_contexts),
                "uniqueTargets": len(output_targets),
            },
            "limits": {
                "maxDeckTargets": args.max_deck,
                "maxContextsPerTarget": args.contexts_per_target,
                "minDeckTargets": args.min_deck,
                "fullThreshold": args.full_threshold,
                "functionTop": args.function_top,
            },
            "morphology": bool(morphology),
            "phonetics": bool(args.phonetics),
            "stats": dict(sorted(stats.items())),
        }
        (out / "BUILD.json").write_text(json.dumps(build_info, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        lines = [
            "# Catalogue v2 build",
            "",
            "| metric | value |",
            "| --- | ---: |",
            "| unique exact targets | %s |" % f"{len(output_targets):,}",
            "| target-deck memberships | %s |" % f"{build_info['output']['targetDeckMemberships']:,}",
            "| natural contexts retained | %s |" % f"{build_info['output']['contexts']:,}",
            "| unique source contexts | %s |" % f"{len(output_contexts):,}",
            "| decks | %s |" % f"{len(decks):,}",
            "| pairs | %s |" % f"{len(pairs):,}",
            "",
            "## Collections",
            "",
            "| collection | target memberships | contexts | decks |",
            "| --- | ---: | ---: | ---: |",
        ]
        for collection in ("everyday", "knowledge", "world"):
            selected = [deck for deck in decks if deck["collection"] == collection]
            lines.append(
                "| %s | %s | %s | %s |"
                % (
                    COLLECTIONS[collection]["title"],
                    f"{sum(d['chunkCount'] for d in selected):,}",
                    f"{sum(d.get('contextCount', d['chunkCount']) for d in selected):,}",
                    f"{len(selected):,}",
                )
            )
        lines.extend(
            [
                "",
                "World stays at zero until Global Voices records pass the required article/contributor attribution gate.",
                "",
            ]
        )
        (out / "BUILD.md").write_text("\n".join(lines), encoding="utf-8")
        return build_info
    finally:
        db.close()
        if morphology is not None:
            morphology.close()
        if not args.keep_staging and staging.exists():
            staging.unlink()


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(description=__doc__)
    root.add_argument("--candidates", nargs="+", required=True, help="candidate JSONL/JSONL.GZ files")
    root.add_argument("--out", required=True)
    root.add_argument("--registry", default=str(DEFAULT_REGISTRY))
    root.add_argument("--learn", default=",".join(core.LEARNABLE))
    root.add_argument("--meanings", default=",".join(core.MEANINGS))
    root.add_argument("--max-deck", type=int, default=3000)
    root.add_argument("--min-deck", type=int, default=40)
    root.add_argument("--full-threshold", type=int, default=3000)
    root.add_argument("--function-top", type=int, default=core.FUNCTION_TOP)
    root.add_argument("--contexts-per-target", type=int, default=3)
    root.add_argument("--morphology-db")
    root.add_argument("--phonetics", action="store_true")
    root.add_argument("--russian-stress")
    root.add_argument("--staging")
    root.add_argument("--keep-staging", action="store_true")
    return root


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    if args.max_deck < 1 or args.min_deck < 1 or args.contexts_per_target < 1:
        raise SystemExit("deck and context limits must be positive")
    if args.min_deck > args.max_deck:
        raise SystemExit("--min-deck cannot exceed --max-deck")
    info = build(args)
    print(json.dumps(info, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
