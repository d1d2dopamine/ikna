#!/usr/bin/env python3
"""Measure Catalogue v2 source supply before publication/deck caps.

Part 5 is diagnostic only. It consumes the same normalized candidate files as the
Catalogue v2 builder, applies the same frequency ranks and target sieve, and
reports two deliberately different numbers:

* eligible targets: every unique exact target that can be taught from the source
  material before ``max_deck`` is applied;
* current selection: what the existing first-pass builder would actually choose
  with the requested ``max_deck`` and ``min_deck`` values.

The distinction lets the report tell source scarcity from an artificial deck cap
without writing or publishing any deck asset.
"""
from __future__ import annotations

import argparse
import csv
import json
import sqlite3
import sys
from collections import Counter
from pathlib import Path
from typing import Any

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import catalogue_core as core
from build_catalogue_v2 import build_ranks, pair_candidates, phrase_choices, stage_candidates
from segmentation import IcuUnavailable, prepare, utf16_length

COLLECTION_SOURCE = {"everyday": "tatoeba", "knowledge": "wikimatrix"}


def parse_codes(value: str) -> list[str]:
    return [part.strip().lower() for part in value.split(",") if part.strip()]


def load_wikimatrix_inventory(path: str | None) -> dict[tuple[str, str], dict[str, Any]]:
    if not path:
        return {}
    result: dict[tuple[str, str], dict[str, Any]] = {}
    with open(path, encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle, delimiter="\t")
        expected = {"first", "second", "status", "retained_rows", "acquisition_capped", "url"}
        if reader.fieldnames is None or set(reader.fieldnames) != expected:
            raise ValueError("WikiMatrix inventory has unexpected columns")
        for row in reader:
            first = row["first"].strip().lower()
            second = row["second"].strip().lower()
            if not first or not second or first == second:
                raise ValueError("WikiMatrix inventory contains an invalid pair")
            key = tuple(sorted((first, second)))
            if key in result:
                raise ValueError("duplicate WikiMatrix inventory pair %s-%s" % key)
            capped = row["acquisition_capped"].strip().lower()
            if capped not in {"true", "false"}:
                raise ValueError("acquisition_capped must be true or false")
            result[key] = {
                "status": row["status"].strip(),
                "retainedRows": int(row["retained_rows"] or 0),
                "acquisitionCapped": capped == "true",
                "url": row["url"].strip(),
            }
    return result


def pair_input_stats(
    db: sqlite3.Connection, collection: str, lang: str, meaning_lang: str, source_family: str
) -> dict[str, int]:
    row = db.execute(
        """
        SELECT input_candidates, unique_candidates, duplicate_candidates
        FROM input_stat
        WHERE collection=? AND lang=? AND meaning_lang=? AND source_family=?
        """,
        (collection, lang, meaning_lang, source_family),
    ).fetchone()
    if row is None:
        return {"inputCandidates": 0, "uniqueCandidates": 0, "duplicateCandidates": 0}
    return {
        "inputCandidates": int(row[0]),
        "uniqueCandidates": int(row[1]),
        "duplicateCandidates": int(row[2]),
    }


def measure_pair(
    db: sqlite3.Connection,
    collection: str,
    lang: str,
    meaning_lang: str,
    ranks: dict[str, int],
    max_deck: int,
    min_deck: int,
    function_top: int,
) -> dict[str, Any]:
    eligible: dict[str, set[str]] = {level: set() for level in core.LEVELS}
    selected: dict[str, set[str]] = {level: set() for level in core.LEVELS}
    row_stats: Counter[str] = Counter()

    for row in pair_candidates(db, collection, lang, meaning_lang):
        row_stats["sourceContexts"] += 1
        (_seq, sentence, meaning, _source_family, _source_version, _context_ref, _meaning_ref, _score, _attrib) = row
        if len(sentence) < (6 if lang in core.CJK else core.MIN_SENTENCE) or utf16_length(sentence) > core.MAX_SENTENCE:
            row_stats["sentenceLengthRejected"] += 1
            continue
        if not meaning or utf16_length(meaning) > core.MAX_TRANSLATION:
            row_stats["translationLengthRejected"] += 1
            continue
        row_stats["qualityAcceptedRows"] += 1

        choices = phrase_choices(sentence, ranks, lang, function_top)
        if not choices:
            row_stats["noUsableTargetAfterSieve"] += 1
            continue
        row_stats["rowsWithSieveChoices"] += 1

        # Supply is every unique exact target the mature phrase sieve can teach
        # from this row, not merely the one target the current builder happens to
        # choose first. This makes the pre-cap number a genuine supply measure.
        for _rank, _surface, tid, level in choices:
            eligible[level].add(tid)

        # Separately reproduce the current production first-pass choice so the
        # report can show what max_deck changes today.
        chosen: tuple[int, str, str, str] | None = None
        cap_blocked = False
        saw_unselected = False
        for choice in choices:
            _rank, _surface, tid, level = choice
            if tid in selected[level]:
                continue
            saw_unselected = True
            if len(selected[level]) >= max_deck:
                cap_blocked = True
                continue
            chosen = choice
            break

        if chosen is None:
            if cap_blocked:
                row_stats["deckCapBlockedRows"] += 1
            elif not saw_unselected:
                row_stats["duplicateTargetRows"] += 1
            else:
                row_stats["nothingSelectedRows"] += 1
            continue

        _rank, surface, tid, level = chosen
        if core.offsets(sentence, surface, lang) is None:
            row_stats["phraseNotInSentence"] += 1
            continue
        selected[level].add(tid)
        row_stats["selectedRows"] += 1

    eligible_counts = {level: len(eligible[level]) for level in core.LEVELS}
    selected_counts = {level: len(selected[level]) for level in core.LEVELS}
    total_selected = sum(selected_counts.values())
    if total_selected < min_deck:
        publishable = {level: 0 for level in core.LEVELS}
    else:
        publishable = {
            level: selected_counts[level] if selected_counts[level] >= min_deck else 0
            for level in core.LEVELS
        }

    return {
        "rows": {key: int(value) for key, value in sorted(row_stats.items())},
        "eligibleTargets": eligible_counts,
        "currentSelectedTargets": selected_counts,
        "currentPublishableTargets": publishable,
        "eligibleTotal": sum(eligible_counts.values()),
        "selectedTotal": total_selected,
        "publishableTotal": sum(publishable.values()),
    }


def classify_pair(
    collection: str,
    measured: dict[str, Any],
    source_meta: dict[str, Any],
    max_deck: int,
) -> str:
    if collection == "knowledge" and source_meta.get("status") == "missing":
        return "no-direct-source-file"
    # A bounded source scan is incomplete evidence even when the measured prefix
    # already contains enough targets to hit max_deck. Report the acquisition
    # bound first so a capped WikiMatrix prefix can never be mistaken for a
    # complete measurement of source supply. The per-level counts still expose
    # whether the current deck cap also truncates the measured prefix.
    if source_meta.get("acquisitionCapped"):
        return "source-scan-lower-bound"
    selected = measured["currentSelectedTargets"]
    eligible = measured["eligibleTargets"]
    if any(selected[level] >= max_deck and eligible[level] > selected[level] for level in core.LEVELS):
        return "deck-cap-truncated"
    if measured["eligibleTotal"] == 0:
        return "no-usable-supply"
    return "measured"


def build_pair_row(
    db: sqlite3.Connection,
    collection: str,
    source_family: str,
    lang: str,
    meaning_lang: str,
    ranks: dict[str, int],
    max_deck: int,
    min_deck: int,
    function_top: int,
    inventory: dict[tuple[str, str], dict[str, Any]] | None = None,
) -> dict[str, Any]:
    """Measure one directed pair using already-established collection ranks."""
    input_stats = pair_input_stats(db, collection, lang, meaning_lang, source_family)
    measured = measure_pair(
        db,
        collection,
        lang,
        meaning_lang,
        ranks,
        max_deck,
        min_deck,
        function_top,
    )
    inventory = inventory or {}
    if collection == "knowledge":
        source_meta = dict(inventory.get(tuple(sorted((lang, meaning_lang))), {}))
        if not source_meta:
            source_meta = {
                "status": "available" if input_stats["inputCandidates"] else "unknown",
                "retainedRows": input_stats["inputCandidates"],
                "acquisitionCapped": False,
                "url": "",
            }
    else:
        source_meta = {
            "status": "available" if input_stats["inputCandidates"] else "no-direct-rows",
            "retainedRows": input_stats["inputCandidates"],
            "acquisitionCapped": False,
            "url": "",
        }
    return {
        "collection": collection,
        "sourceFamily": source_family,
        "lang": lang,
        "meaningLang": meaning_lang,
        "source": source_meta,
        "input": input_stats,
        **measured,
        "diagnosis": classify_pair(collection, measured, source_meta, max_deck),
    }


def report_from_rows(
    learn: list[str],
    meanings: list[str],
    max_deck: int,
    min_deck: int,
    function_top: int,
    stage: dict[str, Any],
    rows: list[dict[str, Any]],
) -> dict[str, Any]:
    """Build the stable Part 5 report envelope from measured directed rows."""
    deck_levels = [
        (row, level)
        for row in rows
        for level in core.LEVELS
        if row["currentSelectedTargets"][level] > 0 or row["eligibleTargets"][level] > 0
    ]
    capped_levels = sum(
        1
        for row, level in deck_levels
        if row["currentSelectedTargets"][level] >= max_deck
        and row["eligibleTargets"][level] > row["currentSelectedTargets"][level]
    )
    thin_levels = sum(1 for row, level in deck_levels if row["currentSelectedTargets"][level] < 1000)
    below_min_levels = sum(1 for row, level in deck_levels if row["currentSelectedTargets"][level] < min_deck)
    lower_bound_pairs = sum(1 for row in rows if row["diagnosis"] == "source-scan-lower-bound")
    missing_wikimatrix = sum(
        1 for row in rows if row["collection"] == "knowledge" and row["diagnosis"] == "no-direct-source-file"
    )

    return {
        "reportVersion": 1,
        "purpose": "Catalogue v2 Part 5 supply census",
        "limits": {
            "maxDeckTargets": max_deck,
            "minDeckTargets": min_deck,
            "functionTop": function_top,
        },
        "staging": stage,
        "summary": {
            "plannedDirectedPairsPerCollection": sum(1 for lang in learn for meaning in meanings if lang != meaning),
            "pairCollectionRows": len(rows),
            "deckLevelsWithSupply": len(deck_levels),
            "deckLevelsArtificiallyTruncated": capped_levels,
            "deckLevelsBelow1000CurrentTargets": thin_levels,
            "deckLevelsBelowMinDeck": below_min_levels,
            "knowledgePairsWithLowerBoundOnly": lower_bound_pairs,
            "knowledgeDirectedPairsWithoutDirectSourceFile": missing_wikimatrix,
            "eligibleTargetsAcrossPairLevels": sum(
                sum(row["eligibleTargets"].values()) for row in rows
            ),
            "currentSelectedTargetsAcrossPairLevels": sum(
                sum(row["currentSelectedTargets"].values()) for row in rows
            ),
        },
        "pairs": rows,
    }


def build_report(args: argparse.Namespace) -> dict[str, Any]:
    learn = parse_codes(args.learn)
    meanings = parse_codes(args.meanings)
    unsupported = (set(learn) - set(core.LEARNABLE)) | (set(meanings) - set(core.MEANINGS))
    if unsupported:
        raise ValueError("unsupported languages: %s" % ", ".join(sorted(unsupported)))
    try:
        prepare(learn)
    except IcuUnavailable as exc:
        raise ValueError(str(exc)) from exc

    staging = Path(args.staging)
    stage = stage_candidates(args.candidates, staging)
    inventory = load_wikimatrix_inventory(args.wikimatrix_inventory)
    db = sqlite3.connect(staging)
    rows: list[dict[str, Any]] = []
    rank_cache: dict[tuple[str, str], dict[str, int]] = {}
    try:
        for collection, source_family in COLLECTION_SOURCE.items():
            for lang in learn:
                key = (collection, lang)
                if key not in rank_cache:
                    rank_cache[key] = build_ranks(db, collection, lang)
                ranks = rank_cache[key]
                for meaning_lang in meanings:
                    if lang == meaning_lang:
                        continue
                    rows.append(
                        build_pair_row(
                            db,
                            collection,
                            source_family,
                            lang,
                            meaning_lang,
                            ranks,
                            args.max_deck,
                            args.min_deck,
                            args.function_top,
                            inventory,
                        )
                    )
    finally:
        db.close()
        if not args.keep_staging and staging.exists():
            staging.unlink()

    return report_from_rows(
        learn,
        meanings,
        args.max_deck,
        args.min_deck,
        args.function_top,
        stage,
        rows,
    )


def counts_cell(row: dict[str, Any], field: str) -> str:
    values = row[field]
    return "/".join(str(values[level]) for level in core.LEVELS)


def markdown(report: dict[str, Any]) -> str:
    s = report["summary"]
    lines = [
        "# Catalogue v2 supply census",
        "",
        "Part 5 diagnostic only. No deck is published by this report.",
        "",
        "## Snapshot",
        "",
        "| metric | value |",
        "| --- | ---: |",
        "| normalized candidate rows | %s |" % f"{report['staging'].get('inputCandidates', 0):,}",
        "| unique normalized candidates | %s |" % f"{report['staging'].get('uniqueCandidates', 0):,}",
        "| pair/collection rows measured | %s |" % f"{s['pairCollectionRows']:,}",
        "| deck levels with measurable supply | %s |" % f"{s['deckLevelsWithSupply']:,}",
        "| deck levels proven truncated by max_deck | %s |" % f"{s['deckLevelsArtificiallyTruncated']:,}",
        "| deck levels below 1,000 current selections | %s |" % f"{s['deckLevelsBelow1000CurrentTargets']:,}",
        "| deck levels below min_deck | %s |" % f"{s['deckLevelsBelowMinDeck']:,}",
        "| Knowledge directed pairs that are only a lower bound | %s |" % f"{s['knowledgePairsWithLowerBoundOnly']:,}",
        "| Knowledge directed pairs without a direct WikiMatrix file | %s |" % f"{s['knowledgeDirectedPairsWithoutDirectSourceFile']:,}",
        "| eligible target memberships before deck caps | %s |" % f"{s['eligibleTargetsAcrossPairLevels']:,}",
        "| targets selected by the current capped first pass | %s |" % f"{s['currentSelectedTargetsAcrossPairLevels']:,}",
        "",
        "`beginner/middle/advanced` counts are shown in that order below.",
        "`source-scan-lower-bound` means the WikiMatrix acquisition limit was reached, so the pair is known to contain at least this much material and must not be called source-starved yet.",
        "",
        "## Pair inventory",
        "",
        "| collection | pair | source | source scan | candidate rows | source contexts | eligible targets | current selected | diagnosis |",
        "| --- | --- | --- | --- | ---: | ---: | ---: | ---: | --- |",
    ]
    for row in report["pairs"]:
        source = row["source"]
        scan = source.get("status", "unknown")
        if source.get("acquisitionCapped"):
            scan += " (capped)"
        lines.append(
            "| {collection} | `{lang}->{meaning}` | {family} | {scan} | {candidates:,} | {contexts:,} | {eligible} | {selected} | {diagnosis} |".format(
                collection=row["collection"],
                lang=row["lang"],
                meaning=row["meaningLang"],
                family=row["sourceFamily"],
                scan=scan,
                candidates=row["input"]["inputCandidates"],
                contexts=row["rows"].get("sourceContexts", 0),
                eligible=counts_cell(row, "eligibleTargets"),
                selected=counts_cell(row, "currentSelectedTargets"),
                diagnosis=row["diagnosis"],
            )
        )

    lines.extend(
        [
            "",
            "## Interpretation",
            "",
            "- `deck-cap-truncated`: the source contains more eligible targets than the current deck cap lets the builder choose.",
            "- `source-scan-lower-bound`: acquisition stopped at the configured WikiMatrix row limit; increase that limit before calling the pair genuinely thin.",
            "- `no-direct-source-file`: the direct WikiMatrix file is absent for that physical pair.",
            "- `no-usable-supply`: the measured normalized rows contain no target that survives the current phrase sieve.",
            "- `measured`: the measured source prefix is not proven truncated by the current deck cap.",
            "",
            "The JSON report also keeps sentence/translation-length rejects, duplicate-target rows, cap-blocked rows and exact per-level counts for follow-up analysis.",
            "",
        ]
    )
    return "\n".join(lines)


def parser() -> argparse.ArgumentParser:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--candidates", nargs="+", required=True)
    ap.add_argument("--json", required=True)
    ap.add_argument("--markdown", required=True)
    ap.add_argument("--learn", default=",".join(core.LEARNABLE))
    ap.add_argument("--meanings", default=",".join(core.MEANINGS))
    ap.add_argument("--max-deck", type=int, default=8000)
    ap.add_argument("--min-deck", type=int, default=40)
    ap.add_argument("--function-top", type=int, default=core.FUNCTION_TOP)
    ap.add_argument("--wikimatrix-inventory")
    ap.add_argument("--staging", required=True)
    ap.add_argument("--keep-staging", action="store_true")
    return ap


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    if args.max_deck < 1 or args.min_deck < 1:
        raise SystemExit("deck limits must be positive")
    if args.min_deck > args.max_deck:
        raise SystemExit("--min-deck cannot exceed --max-deck")
    report = build_report(args)
    Path(args.json).parent.mkdir(parents=True, exist_ok=True)
    Path(args.markdown).parent.mkdir(parents=True, exist_ok=True)
    Path(args.json).write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    Path(args.markdown).write_text(markdown(report), encoding="utf-8")
    print(json.dumps(report["summary"], ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
