#!/usr/bin/env python3
"""Part 6/7 MASSIVE admission experiment and Everyday candidate-pool rebuild.

This tool deliberately stops before production deck selection.  It compares the
already-approved Tatoeba Everyday material with the experimental MASSIVE 1.1
source under one combined frequency space, reports MASSIVE-only target supply
and context opportunities, writes deterministic manual-review samples, and
produces one exact-deduplicated candidate pool that preserves every source
origin.

The resulting pool is a preview while MASSIVE remains ``publication.status =
candidate`` in the source registry.  The normal Catalogue v2 builder refuses to
publish candidate sources, so running this experiment cannot silently admit a
new corpus.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import heapq
import json
import sqlite3
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Iterable, Iterator

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import catalogue_core as core
from build_catalogue_v2 import build_ranks, phrase_choices, stage_candidates
from ingest.adapters import MASSIVE_LOCALES, read_massive_locale
from ingest.model import Candidate, merge_candidate_files
from ingest.registry import SourceRegistry
from segmentation import IcuUnavailable, prepare, utf16_length

DEFAULT_REGISTRY = HERE / "sources" / "catalogue-v2-sources.json"


def parse_codes(value: str) -> list[str]:
    return [part.strip().lower() for part in value.split(",") if part.strip()]


def open_jsonl(path: str):
    if path.endswith(".gz"):
        return gzip.open(path, "rt", encoding="utf-8", errors="replace")
    return open(path, encoding="utf-8", errors="replace")


def iter_candidates(paths: Iterable[str]) -> Iterator[Candidate]:
    for path in paths:
        with open_jsonl(path) as handle:
            for number, line in enumerate(handle, start=1):
                line = line.strip()
                if not line:
                    continue
                try:
                    yield Candidate.from_dict(json.loads(line))
                except Exception as exc:
                    raise ValueError("%s:%d: %s" % (path, number, exc)) from exc


def source_pair_rows(
    db: sqlite3.Connection,
    source_family: str,
    lang: str,
    meaning_lang: str,
):
    """Yield unique source contexts for one source/pair in deterministic order."""
    rows = db.execute(
        """
        SELECT seq,context,meaning,source_family,source_version,context_ref,meaning_ref,
               alignment_score,attribution_json
        FROM candidate
        WHERE collection='everyday' AND source_family=? AND lang=? AND meaning_lang=?
        ORDER BY context_ref, LENGTH(meaning), seq
        """,
        (source_family, lang, meaning_lang),
    )
    last_context: tuple[str, str] | None = None
    for row in rows:
        current = (row[3], row[5])
        if current == last_context:
            continue
        last_context = current
        yield row


def eligible_from_source(
    db: sqlite3.Connection,
    source_family: str,
    lang: str,
    meaning_lang: str,
    ranks: dict[str, int],
    function_top: int,
    *,
    sample_per_pair: int = 0,
) -> tuple[dict[str, set[str]], set[tuple[str, str]], Counter[str], list[dict[str, Any]]]:
    targets = {level: set() for level in core.LEVELS}
    target_contexts: set[tuple[str, str]] = set()
    stats: Counter[str] = Counter()
    sample_heap: list[tuple[int, str, dict[str, Any]]] = []

    for row in source_pair_rows(db, source_family, lang, meaning_lang):
        (_seq, sentence, meaning, _family, _version, context_ref, meaning_ref, _score, _attrib) = row
        stats["sourceContexts"] += 1
        if len(sentence) < (6 if lang in core.CJK else core.MIN_SENTENCE) or utf16_length(sentence) > core.MAX_SENTENCE:
            stats["sentenceLengthRejected"] += 1
            continue
        if not meaning or utf16_length(meaning) > core.MAX_TRANSLATION:
            stats["translationLengthRejected"] += 1
            continue
        stats["qualityAcceptedRows"] += 1
        choices = phrase_choices(sentence, ranks, lang, function_top)
        if not choices:
            stats["noUsableTargetAfterSieve"] += 1
            continue
        stats["rowsWithSieveChoices"] += 1
        for _rank, _surface, tid, level in choices:
            targets[level].add(tid)
            target_contexts.add((tid, context_ref))

        if sample_per_pair:
            rank, surface, tid, level = choices[0]
            score = int.from_bytes(
                hashlib.sha256(
                    (source_family + "\0" + lang + "\0" + meaning_lang + "\0" + context_ref).encode("utf-8")
                ).digest(),
                "big",
            )
            sample = {
                "lang": lang,
                "meaningLang": meaning_lang,
                "target": surface,
                "targetId": tid,
                "level": level,
                "freqRank": rank,
                "context": sentence,
                "meaning": meaning,
                "contextId": context_ref,
                "meaningId": meaning_ref,
            }
            # Keep the lowest hashes without storing every candidate sample.
            item = (-score, context_ref, sample)
            if len(sample_heap) < sample_per_pair:
                heapq.heappush(sample_heap, item)
            elif item > sample_heap[0]:
                heapq.heapreplace(sample_heap, item)

    samples = [item[2] for item in sorted(sample_heap, key=lambda part: (-part[0], part[1]))]
    return targets, target_contexts, stats, samples


def source_file_counts(paths: Iterable[str], source_family: str) -> dict[str, Any]:
    rows = 0
    pairs: Counter[str] = Counter()
    versions: Counter[str] = Counter()
    for record in iter_candidates(paths):
        rows += 1
        pairs[record.lang + "->" + record.meaning_lang] += 1
        families = {origin.source_family for origin in record.origins}
        if families != {source_family}:
            raise ValueError(
                "%s input contains candidate families %s" % (source_family, ",".join(sorted(families)))
            )
        for origin in record.origins:
            versions[origin.source_version] += 1
    return {
        "rows": rows,
        "directedPairs": len(pairs),
        "sourceVersions": dict(sorted(versions.items())),
        "rowsByPair": dict(sorted(pairs.items())),
    }


def merged_pool_counts(path: str) -> dict[str, Any]:
    rows = 0
    source_rows: Counter[str] = Counter()
    source_overlaps = 0
    pairs: Counter[str] = Counter()
    for record in iter_candidates([path]):
        rows += 1
        pairs[record.lang + "->" + record.meaning_lang] += 1
        families = sorted({origin.source_family for origin in record.origins})
        for family in families:
            source_rows[family] += 1
        if len(families) > 1:
            source_overlaps += 1
    return {
        "uniqueCandidates": rows,
        "directedPairs": len(pairs),
        "candidatesCarryingSource": dict(sorted(source_rows.items())),
        "exactCrossSourceOverlaps": source_overlaps,
        "rowsByPair": dict(sorted(pairs.items())),
    }


def massive_locale_quality(
    dump_dir: str,
    languages: Iterable[str],
    args: argparse.Namespace,
) -> dict[str, dict[str, int]]:
    result: dict[str, dict[str, int]] = {}
    for lang in languages:
        _rows, stats = read_massive_locale(
            dump_dir,
            lang,
            min_natural_votes=args.min_natural_votes,
            min_spelling_votes=args.min_spelling_votes,
            min_target_language_votes=args.min_target_language_votes,
            min_intent_votes=args.min_intent_votes,
        )
        result[lang] = stats
    return result


def build_report(args: argparse.Namespace) -> dict[str, Any]:
    learn = parse_codes(args.learn)
    meanings = parse_codes(args.meanings)
    unsupported = (set(learn) - set(core.LEARNABLE)) | (set(meanings) - set(core.MEANINGS))
    if unsupported:
        raise ValueError("unsupported languages: %s" % ", ".join(sorted(unsupported)))
    if set(learn) - set(MASSIVE_LOCALES) or set(meanings) - set(MASSIVE_LOCALES):
        raise ValueError("MASSIVE locale mapping is incomplete for requested languages")
    try:
        prepare(learn)
    except IcuUnavailable as exc:
        raise ValueError(str(exc)) from exc

    registry = SourceRegistry.load(args.registry)
    massive_policy = registry.get("massive")
    if massive_policy.publication_status != "candidate":
        raise ValueError("Part 6 expects MASSIVE to remain a candidate until manual review is accepted")

    output_pool = Path(args.pool)
    output_pool.parent.mkdir(parents=True, exist_ok=True)
    merge_input = list(args.tatoeba) + list(args.massive)
    if args.merge_db:
        merge_db_path = Path(args.merge_db)
        if merge_db_path.exists():
            merge_db_path.unlink()
        input_rows, output_rows = merge_candidate_files(
            merge_input, str(output_pool), db_path=str(merge_db_path)
        )
    else:
        input_rows, output_rows = merge_candidate_files(merge_input, str(output_pool))

    staging = Path(args.staging)
    # Baseline first is deliberate: exact cross-source duplicate rows are kept as
    # Tatoeba in the measurement DB, so MASSIVE receives no novelty credit for
    # material that is already present exactly in the approved baseline.
    stage = stage_candidates(list(args.tatoeba) + list(args.massive), staging)
    db = sqlite3.connect(staging)
    pair_rows: list[dict[str, Any]] = []
    all_samples: list[dict[str, Any]] = []
    summary = Counter()
    rank_cache: dict[str, dict[str, int]] = {}
    try:
        for lang in learn:
            if lang not in rank_cache:
                rank_cache[lang] = build_ranks(db, "everyday", lang)
            ranks = rank_cache[lang]
            for meaning_lang in meanings:
                if lang == meaning_lang:
                    continue
                baseline, _baseline_contexts, baseline_stats, _ = eligible_from_source(
                    db, "tatoeba", lang, meaning_lang, ranks, args.function_top
                )
                massive, massive_contexts, massive_stats, samples = eligible_from_source(
                    db, "massive", lang, meaning_lang, ranks, args.function_top,
                    sample_per_pair=args.sample_per_pair,
                )
                all_samples.extend(samples)
                new_by_level = {
                    level: len(massive[level] - baseline[level]) for level in core.LEVELS
                }
                overlap_by_level = {
                    level: len(massive[level] & baseline[level]) for level in core.LEVELS
                }
                potential_extra_contexts = len(
                    {
                        item
                        for item in massive_contexts
                        if any(item[0] in baseline[level] for level in core.LEVELS)
                    }
                )
                baseline_counts = {level: len(baseline[level]) for level in core.LEVELS}
                massive_counts = {level: len(massive[level]) for level in core.LEVELS}
                row = {
                    "lang": lang,
                    "meaningLang": meaning_lang,
                    "tatoebaEligibleTargets": baseline_counts,
                    "massiveEligibleTargets": massive_counts,
                    "massiveNewTargets": new_by_level,
                    "targetOverlap": overlap_by_level,
                    "massivePotentialExtraTargetContexts": potential_extra_contexts,
                    "tatoebaRows": {k: int(v) for k, v in sorted(baseline_stats.items())},
                    "massiveRows": {k: int(v) for k, v in sorted(massive_stats.items())},
                }
                pair_rows.append(row)
                summary["directedPairs"] += 1
                summary["massiveNewTargets"] += sum(new_by_level.values())
                summary["massiveEligibleTargets"] += sum(massive_counts.values())
                summary["tatoebaEligibleTargets"] += sum(baseline_counts.values())
                summary["massivePotentialExtraTargetContexts"] += potential_extra_contexts
                if sum(new_by_level.values()):
                    summary["pairsWithNewMassiveTargets"] += 1
    finally:
        db.close()

    quality = massive_locale_quality(args.massive_dump, sorted(set(learn) | set(meanings)), args)
    quality_summary = Counter()
    for stats in quality.values():
        quality_summary.update(stats)

    baseline_counts = source_file_counts(args.tatoeba, "tatoeba")
    massive_counts = source_file_counts(args.massive, "massive")
    pool_counts = merged_pool_counts(str(output_pool))

    return {
        "reportVersion": 1,
        "part": "6-7",
        "status": "requires-manual-review",
        "publicationSafe": False,
        "sourceDecision": {
            "tatoeba": "approved-baseline",
            "massive": "candidate-pending-manual-review",
        },
        "qualityGate": {
            "minNaturalVotes": args.min_natural_votes,
            "minSpellingVotes": args.min_spelling_votes,
            "minTargetLanguageVotes": args.min_target_language_votes,
            "minIntentVotes": args.min_intent_votes,
        },
        "massiveLocaleQuality": quality,
        "massiveQualitySummary": dict(sorted(quality_summary.items())),
        "inputs": {
            "tatoeba": baseline_counts,
            "massive": massive_counts,
            "mergeInputRows": input_rows,
        },
        "mergedPreviewPool": {
            **pool_counts,
            "writtenCandidates": output_rows,
            "path": str(output_pool),
        },
        "staging": stage,
        "summary": {k: int(v) for k, v in sorted(summary.items())},
        "pairs": pair_rows,
        "samples": sorted(all_samples, key=lambda row: (row["lang"], row["meaningLang"], row["contextId"])),
    }


def counts(values: dict[str, int]) -> str:
    return "/".join(str(values.get(level, 0)) for level in core.LEVELS)


def markdown(report: dict[str, Any]) -> str:
    s = report["summary"]
    q = report["massiveQualitySummary"]
    pool = report["mergedPreviewPool"]
    lines = [
        "# Everyday source admission and rebuild preview",
        "",
        "Parts 6-7 evidence. This report **does not admit or publish MASSIVE**.",
        "MASSIVE remains a candidate source until the deterministic samples are reviewed.",
        "",
        "## Snapshot",
        "",
        "| metric | value |",
        "| --- | ---: |",
        "| MASSIVE locale source rows inspected | %s |" % f"{q.get('sourceRows', 0):,}",
        "| MASSIVE locale rows accepted by source gate | %s |" % f"{q.get('qualityAcceptedRows', 0):,}",
        "| MASSIVE localized rows rejected by human-review gate | %s |" % f"{q.get('qualityRejectedRows', 0):,}",
        "| MASSIVE unjudged en-US seed rows accepted | %s |" % f"{q.get('qualityUnjudgedSeedRows', 0):,}",
        "| directed language pairs compared | %s |" % f"{s.get('directedPairs', 0):,}",
        "| pairs where MASSIVE adds at least one new eligible target | %s |" % f"{s.get('pairsWithNewMassiveTargets', 0):,}",
        "| MASSIVE eligible target memberships | %s |" % f"{s.get('massiveEligibleTargets', 0):,}",
        "| MASSIVE target memberships not present in Tatoeba | %s |" % f"{s.get('massiveNewTargets', 0):,}",
        "| MASSIVE potential extra contexts for Tatoeba targets | %s |" % f"{s.get('massivePotentialExtraTargetContexts', 0):,}",
        "| exact-deduplicated preview candidate rows | %s |" % f"{pool.get('uniqueCandidates', 0):,}",
        "| preview candidates carrying Tatoeba provenance | %s |" % f"{pool.get('candidatesCarryingSource', {}).get('tatoeba', 0):,}",
        "| preview candidates carrying MASSIVE provenance | %s |" % f"{pool.get('candidatesCarryingSource', {}).get('massive', 0):,}",
        "| exact candidate rows carrying both source families | %s |" % f"{pool.get('exactCrossSourceOverlaps', 0):,}",
        "",
        "`beginner/middle/advanced` counts are shown in that order.",
        "",
        "## MASSIVE source quality gate by locale",
        "",
        "| language | locale | source rows | accepted | rejected | unjudged seed |",
        "| --- | --- | ---: | ---: | ---: | ---: |",
    ]
    for lang, stats in sorted(report["massiveLocaleQuality"].items()):
        lines.append(
            "| `{}` | `{}` | {:,} | {:,} | {:,} | {:,} |".format(
                lang,
                MASSIVE_LOCALES[lang],
                stats.get("sourceRows", 0),
                stats.get("qualityAcceptedRows", 0),
                stats.get("qualityRejectedRows", 0),
                stats.get("qualityUnjudgedSeedRows", 0),
            )
        )
    lines.extend([
        "",
        "## Pair coverage",
        "",
        "| pair | Tatoeba eligible | MASSIVE eligible | MASSIVE new | overlap | possible extra contexts |",
        "| --- | ---: | ---: | ---: | ---: | ---: |",
    ])
    for row in report["pairs"]:
        lines.append(
            "| `{lang}->{meaning}` | {base} | {massive} | {new} | {overlap} | {contexts:,} |".format(
                lang=row["lang"],
                meaning=row["meaningLang"],
                base=counts(row["tatoebaEligibleTargets"]),
                massive=counts(row["massiveEligibleTargets"]),
                new=counts(row["massiveNewTargets"]),
                overlap=counts(row["targetOverlap"]),
                contexts=row["massivePotentialExtraTargetContexts"],
            )
        )
    lines.extend([
        "",
        "## Decision rule",
        "",
        "Do not promote MASSIVE to `ready` from these counts alone. Review the deterministic sample file for naturalness, usefulness, translation/localization fit and obvious assistant-domain repetition. If quality is acceptable and the new-target/context contribution is material, promote the source in the registry in the later integration batch. Otherwise keep Tatoeba-only Everyday.",
        "",
        "The merged preview pool intentionally preserves all origins for exact cross-source duplicates. It is an input/evidence artefact, not a release asset.",
        "",
    ])
    return "\n".join(lines)


def samples_markdown(report: dict[str, Any]) -> str:
    lines = [
        "# MASSIVE deterministic manual-review samples",
        "",
        "These are candidates that passed the applicable MASSIVE source gate and ikna's ordinary phrase sieve. Localized rows require the configured human-review votes; the original en-US seed is unjudged upstream. Review the sentence itself, the aligned meaning, the selected target and whether the material feels useful for Everyday learning.",
        "",
    ]
    current: tuple[str, str] | None = None
    for row in report["samples"]:
        pair = (row["lang"], row["meaningLang"])
        if pair != current:
            lines.extend(["## `%s -> %s`" % pair, ""])
            current = pair
        lines.extend([
            "- **{}** ({}, rank {}) — {}".format(row["target"], row["level"], row["freqRank"], row["context"]),
            "  - meaning: {}".format(row["meaning"]),
            "  - source: `{}`".format(row["contextId"]),
        ])
    return "\n".join(lines) + "\n"


def parser() -> argparse.ArgumentParser:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--tatoeba", nargs="+", required=True, help="approved Tatoeba candidate files")
    ap.add_argument("--massive", nargs="+", required=True, help="experimental MASSIVE candidate files")
    ap.add_argument("--massive-dump", required=True, help="extracted MASSIVE 1.1 root for quality census")
    ap.add_argument("--pool", required=True, help="merged Everyday preview candidate JSONL(.gz)")
    ap.add_argument("--json", required=True)
    ap.add_argument("--markdown", required=True)
    ap.add_argument("--samples", required=True)
    ap.add_argument("--staging", required=True)
    ap.add_argument("--merge-db")
    ap.add_argument("--registry", default=str(DEFAULT_REGISTRY))
    ap.add_argument("--learn", default=",".join(core.LEARNABLE))
    ap.add_argument("--meanings", default=",".join(core.MEANINGS))
    ap.add_argument("--function-top", type=int, default=core.FUNCTION_TOP)
    ap.add_argument("--sample-per-pair", type=int, default=5)
    ap.add_argument("--min-natural-votes", type=int, default=2)
    ap.add_argument("--min-spelling-votes", type=int, default=2)
    ap.add_argument("--min-target-language-votes", type=int, default=2)
    ap.add_argument("--min-intent-votes", type=int, default=2)
    return ap


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    for value, name in [
        (args.function_top, "--function-top"),
        (args.sample_per_pair, "--sample-per-pair"),
        (args.min_natural_votes, "--min-natural-votes"),
        (args.min_spelling_votes, "--min-spelling-votes"),
        (args.min_target_language_votes, "--min-target-language-votes"),
        (args.min_intent_votes, "--min-intent-votes"),
    ]:
        if value < 0:
            raise SystemExit(name + " must be non-negative")

    report = build_report(args)
    Path(args.json).parent.mkdir(parents=True, exist_ok=True)
    Path(args.markdown).parent.mkdir(parents=True, exist_ok=True)
    Path(args.samples).parent.mkdir(parents=True, exist_ok=True)
    Path(args.json).write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    Path(args.markdown).write_text(markdown(report), encoding="utf-8")
    Path(args.samples).write_text(samples_markdown(report), encoding="utf-8")
    print(json.dumps(report["summary"], ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
