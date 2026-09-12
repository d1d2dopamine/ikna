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

import build_catalog as legacy
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
        counts.update(word.casefold() for word in legacy.words(text, lang))
    return {form: position for position, (form, _n) in enumerate(counts.most_common(), start=1)}


def pick_phrase_limited(
    sentence: str,
    ranks: dict[str, int],
    target_counts: Counter[str],
    lang: str,
    max_contexts_per_target: int,
) -> tuple[str, int, str] | None:
    found = legacy.words(sentence, lang)
    choices: list[tuple[int, str, str]] = []
    for surface in found:
        low = surface.casefold()
        rank = ranks.get(low)
        if rank is None or rank <= legacy.FUNCTION_TOP:
            continue
        if len(surface) < legacy.minimum_phrase(lang) or utf16_length(surface) > legacy.MAX_PHRASE:
            continue
        if sum(1 for other in found if other.casefold() == low) != 1:
            continue
        tid = target_id(lang, surface)
        if target_counts[tid] >= max_contexts_per_target:
            continue
        choices.append((rank, surface, tid))
    if not choices:
        return None
    # Rare word inside easier surrounding material, matching the v1 intuition.
    rank, surface, tid = max(choices, key=lambda item: (item[0], item[1].casefold()))
    return surface, rank, tid


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


def make_card(
    row: tuple,
    lang: str,
    ranks: dict[str, int],
    target_counts: Counter[str],
    max_contexts_per_target: int,
    phonetics: Any,
    morphology: MorphologyResolver | None,
    stats: Counter[str],
) -> tuple[str, str, dict[str, Any]] | None:
    (_seq, sentence, meaning, source_family, _source_version, context_ref, meaning_ref, _score, _attrib) = row
    if len(sentence) < (6 if lang in legacy.CJK else legacy.MIN_SENTENCE) or utf16_length(sentence) > legacy.MAX_SENTENCE:
        stats["sentence length"] += 1
        return None
    if not meaning or utf16_length(meaning) > legacy.MAX_TRANSLATION:
        stats["translation length"] += 1
        return None
    chosen = pick_phrase_limited(sentence, ranks, target_counts, lang, max_contexts_per_target)
    if chosen is None:
        stats["nothing to teach"] += 1
        return None
    surface, rank, tid = chosen
    span = legacy.offsets(sentence, surface, lang)
    if span is None:
        stats["phrase not in sentence"] += 1
        return None

    translation = meaning
    credit = tatoeba_credit(context_ref)
    if credit:
        translation += legacy.SOURCE_MARK + credit

    card: dict[str, Any] = {
        "text": surface,
        "context": sentence,
        "translation": translation,
        "targetStart": span[0],
        "targetEnd": span[1],
        "freqRank": rank,
        "tokens": legacy.token_list(sentence, ranks, {}, lang),
        "targetId": tid,
        "contextId": context_ref,
        "meaningId": meaning_ref,
        "sourceFamily": source_family,
    }
    if phonetics:
        ipa = phonetics.line(surface)
        ipa_context = phonetics.line(sentence)
        if ipa:
            card["ipa"] = ipa
        if ipa_context:
            card["ipaContext"] = ipa_context
        stats["card transcribed" if (ipa or ipa_context) else "card not transcribed"] += 1
    if morphology is not None:
        card, morph_stats = morphology.enrich_card(card, lang)
        stats.update({"morphology " + key: value for key, value in morph_stats.items()})
    return legacy.level_of(rank), tid, card


def write_deck(path: Path, deck_id: str, cards: list[dict[str, Any]]) -> int:
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        for position, card in enumerate(cards, start=1):
            row = {"id": "%s-%05d" % (deck_id, position), **card}
            handle.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")
    size = path.stat().st_size
    if size > MAX_DECK_BYTES:
        raise ValueError("%s is %.1f MiB; deck assets must stay below 24 MiB" % (path.name, size / 1048576))
    return size


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
    unsupported = (set(learn) - set(legacy.LEARNABLE)) | (set(meanings) - set(legacy.MEANINGS))
    if unsupported:
        raise ValueError("unsupported languages: %s" % ", ".join(sorted(unsupported)))
    legacy.FUNCTION_TOP = args.function_top
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

            cards_by_level: dict[str, list[dict[str, Any]]] = {level: [] for level in legacy.LEVELS}
            target_counts: Counter[str] = Counter()
            pair_stats = Counter()
            transcriber = phonetics_for(lang)
            for row in pair_candidates(db, collection, lang, meaning_lang):
                if all(len(cards_by_level[level]) >= args.max_deck for level in legacy.LEVELS):
                    break
                made = make_card(
                    row,
                    lang,
                    ranks,
                    target_counts,
                    args.contexts_per_target,
                    transcriber,
                    morphology,
                    pair_stats,
                )
                if made is None:
                    continue
                level, tid, card = made
                if len(cards_by_level[level]) >= args.max_deck:
                    continue
                target_counts[tid] += 1
                cards_by_level[level].append(card)
                output_contexts.add((lang, card["contextId"]))
                output_targets.add((lang, card["targetId"]))

            pair_count = sum(len(rows) for rows in cards_by_level.values())
            if pair_count < args.min_deck:
                stats["pair below minimum"] += 1
                continue
            active_sources.add(source_family)
            pair_decks = 0
            for level in legacy.LEVELS:
                cards = cards_by_level[level]
                if len(cards) < args.min_deck:
                    continue
                deck_id = "%s-%s-%s-%s" % (lang, meaning_lang, collection, level)
                path = out / (deck_id + ".jsonl")
                size = write_deck(path, deck_id, cards)
                pair_decks += 1
                decks.append(
                    {
                        "id": deck_id,
                        "title": "%s from %s - %s - %s"
                        % (
                            legacy.NAMES.get(lang, lang),
                            legacy.NAMES.get(meaning_lang, meaning_lang),
                            COLLECTIONS[collection]["title"],
                            level,
                        ),
                        "lang": lang,
                        "meaningLang": meaning_lang,
                        "chunkCount": len(cards),
                        "file": path.name,
                        "sizeBytes": size,
                        "subject": "",
                        "level": level,
                        "licence": policy.licence_name,
                        "attribution": policy.attribution,
                        "sources": ["%s, %s" % (policy.title, policy.licence_name)],
                        "phonetics": any(card.get("ipa") or card.get("ipaContext") for card in cards),
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
                collection_pairs.append(
                    {
                        "collection": collection,
                        "lang": lang,
                        "meaningLang": meaning_lang,
                        "tier": tier(pair_count, args.full_threshold),
                        "deckCount": pair_decks,
                        "chunkCount": pair_count,
                    }
                )
            stats.update(pair_stats)
            print(
                "  %s %s->%s: %d cards (%s)"
                % (collection, lang, meaning_lang, pair_count, ", ".join("%s=%d" % (l, len(cards_by_level[l])) for l in legacy.LEVELS)),
                flush=True,
            )

        aggregate: dict[tuple[str, str], dict[str, int]] = defaultdict(lambda: {"deckCount": 0, "chunkCount": 0})
        for row in collection_pairs:
            item = aggregate[(row["lang"], row["meaningLang"])]
            item["deckCount"] += row["deckCount"]
            item["chunkCount"] += row["chunkCount"]
        pairs = [
            {
                "lang": lang,
                "meaningLang": meaning,
                "tier": tier(value["chunkCount"], args.full_threshold),
                "deckCount": value["deckCount"],
                "chunkCount": value["chunkCount"],
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
                "cards": sum(deck["chunkCount"] for deck in decks),
                "decks": len(decks),
                "pairs": len(pairs),
                "collectionPairs": len(collection_pairs),
                "uniqueSourceContexts": len(output_contexts),
                "uniqueTargets": len(output_targets),
            },
            "limits": {
                "maxDeckCards": args.max_deck,
                "maxContextsPerTarget": args.contexts_per_target,
                "minDeckCards": args.min_deck,
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
            "| cards | %s |" % f"{build_info['output']['cards']:,}",
            "| decks | %s |" % f"{len(decks):,}",
            "| pairs | %s |" % f"{len(pairs):,}",
            "| unique source contexts in output | %s |" % f"{len(output_contexts):,}",
            "| unique exact targets in output | %s |" % f"{len(output_targets):,}",
            "",
            "## Collections",
            "",
            "| collection | cards | decks |",
            "| --- | ---: | ---: |",
        ]
        for collection in ("everyday", "knowledge", "world"):
            selected = [deck for deck in decks if deck["collection"] == collection]
            lines.append(
                "| %s | %s | %s |"
                % (COLLECTIONS[collection]["title"], f"{sum(d['chunkCount'] for d in selected):,}", f"{len(selected):,}")
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
    root.add_argument("--learn", default=",".join(legacy.LEARNABLE))
    root.add_argument("--meanings", default=",".join(legacy.MEANINGS))
    root.add_argument("--max-deck", type=int, default=8000)
    root.add_argument("--min-deck", type=int, default=40)
    root.add_argument("--full-threshold", type=int, default=3000)
    root.add_argument("--function-top", type=int, default=legacy.FUNCTION_TOP)
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
