#!/usr/bin/env python3
"""Bound a larger budget from saved selection evidence; never reselect or build."""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
from pathlib import Path

import catalogue_core as core


def impact(report: dict, budget: int, storage: dict | None = None) -> dict:
    old = report["policy"]["maxTargetsPerLevel"]
    if report.get("publicationSafe") is not False or not old <= budget <= core.V2_MAX_DECK_TARGETS:
        raise ValueError("expected nonpublishing evidence and a nondecreasing approved budget")
    rows = []
    for row in report["decks"]:
        selected, remaining = row["selectedTargets"], row["budgetRejected"]
        if not 0 <= selected <= old or remaining < 0 or (remaining and selected != old):
            raise ValueError("inconsistent selected/budget-rejected counts")
        included = row["decision"].startswith("publish")
        gain = min(budget - old, remaining) if included else 0
        rows.append({"deckId": row["deckId"], "included": included,
                     "baselineTargets": selected, "unallocatedBudgetCandidates": remaining,
                     "additionalMembershipsUpperBound": gain,
                     "futureTargetsUpperBound": selected + gain})
    baseline = sum(r["baselineTargets"] for r in rows if r["included"])
    gain = sum(r["additionalMembershipsUpperBound"] for r in rows)
    result = {"reportVersion": 1, "publicationSafe": False, "selectionReexecuted": False,
              "baselineBudget": old, "proposedBudget": budget,
              "baselineIncludedMemberships": baseline,
              "additionalMembershipsRange": [0, gain],
              "includedMembershipsRange": [baseline, baseline + gain],
              "decksAtBaselineCap": sum(r["baselineTargets"] == old for r in rows),
              "below1000UnaffectedByBudget": sum(r["baselineTargets"] < 1000 and not r["unallocatedBudgetCandidates"] for r in rows),
              "assumption": "same inputs, ranks, evidence and allocation order; remaining candidates may collide",
              "uniqueTargetGainMeasured": False, "finalBytesMeasured": False, "decks": rows}
    if storage:
        if storage["baseline"]["memberships"] != baseline:
            raise ValueError("storage membership count differs")
        counts = {r["deckId"]: r for r in rows}
        stress = []
        for deck in storage["baseline"]["decks"]:
            row = counts[deck["deckId"]]
            if deck["rows"] != row["baselineTargets"]:
                raise ValueError("storage deck count differs")
            # This is a hypothetical constant-bytes-per-row stress screen,
            # never a measured download size or an accepted import budget.
            raw = deck["rawBytes"] * row["futureTargetsUpperBound"] / deck["rows"]
            if raw > 24 * 1024 * 1024:
                stress.append({"deckId": deck["deckId"], "baselineLogicalBytes": deck["rawBytes"],
                               "hypotheticalLogicalBytes": round(raw), "futureTargetsUpperBound": row["futureTargetsUpperBound"]})
        result["sizeStress"] = {"method": "intermediate self-contained raw bytes per row held constant at target-count upper bound",
                                "actualSizeForecast": False, "hypotheticalDecksOver24MiB": stress}
    return result


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--selection", type=Path, required=True)
    ap.add_argument("--storage", type=Path)
    ap.add_argument("--max-deck", type=core.v2_deck_budget, default=core.V2_MAX_DECK_TARGETS)
    ap.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()
    if args.out.exists() or args.out.resolve() in {p.resolve() for p in (args.selection, args.storage) if p}:
        raise ValueError("output must be new and separate from inputs")
    raw = args.selection.read_bytes()
    report = json.loads(gzip.decompress(raw) if args.selection.suffix == ".gz" else raw)
    storage = json.loads(args.storage.read_bytes()) if args.storage else None
    result = impact(report, args.max_deck, storage)
    result["selectionFileSha256"] = hashlib.sha256(raw).hexdigest()
    if args.storage:
        result["storageFileSha256"] = hashlib.sha256(args.storage.read_bytes()).hexdigest()
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + "\n")
    print(json.dumps({k: v for k, v in result.items() if k not in ("decks", "sizeStress")}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
