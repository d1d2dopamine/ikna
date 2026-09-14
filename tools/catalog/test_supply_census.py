#!/usr/bin/env python3
"""Contract checks for the Part 5 source-supply census."""
from __future__ import annotations

import csv
import json
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from ingest.model import Candidate, Origin, write_jsonl
from supply_census import build_report, markdown, parser
from wikimatrix_plan import planned_pairs


def everyday(context: str, ref: str) -> Candidate:
    return Candidate(
        collection="everyday",
        lang="en",
        meaning_lang="es",
        context=context,
        meaning="Una traduccion humana suficientemente larga.",
        origins=[Origin("tatoeba", "fixture-1", ref, ref + ":es")],
    )


def main() -> int:
    assert planned_pairs(["en", "es", "ko"], "en", True) == [
        ("en", "es"), ("en", "ko"), ("es", "ko")
    ]
    assert planned_pairs(["en", "es", "ko"], "en", False) == [("en", "es"), ("en", "ko")]

    with tempfile.TemporaryDirectory(prefix="ikna-supply-census-") as td:
        root = Path(td)
        candidates = root / "candidates.jsonl.gz"
        # One source row contains several independently eligible exact targets.
        # max_deck=1 therefore proves that pre-cap supply and current selection
        # are deliberately different measurements.
        write_jsonl(
            str(candidates),
            [
                everyday("We observe zebra yak xylophone nearby.", "tatoeba:1"),
                everyday("They discuss quartz velvet lantern calmly.", "tatoeba:2"),
            ],
        )
        inventory = root / "wikimatrix.tsv"
        with inventory.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.writer(handle, delimiter="\t", lineterminator="\n")
            writer.writerow(["first", "second", "status", "retained_rows", "acquisition_capped", "url"])
            writer.writerow(["en", "es", "missing", "0", "false", "https://example.invalid/WikiMatrix.en-es.tsv.gz"])

        args = parser().parse_args(
            [
                "--candidates", str(candidates),
                "--json", str(root / "report.json"),
                "--markdown", str(root / "report.md"),
                "--learn", "en",
                "--meanings", "es",
                "--max-deck", "1",
                "--min-deck", "1",
                "--function-top", "0",
                "--wikimatrix-inventory", str(inventory),
                "--staging", str(root / "stage.sqlite3"),
            ]
        )
        report = build_report(args)
        assert report["summary"]["pairCollectionRows"] == 2
        everyday_row = next(row for row in report["pairs"] if row["collection"] == "everyday")
        assert everyday_row["eligibleTargets"]["beginner"] > 1
        assert everyday_row["currentSelectedTargets"]["beginner"] == 1
        assert everyday_row["diagnosis"] == "deck-cap-truncated"
        knowledge_row = next(row for row in report["pairs"] if row["collection"] == "knowledge")
        assert knowledge_row["diagnosis"] == "no-direct-source-file"
        text = markdown(report)
        assert "deck-cap-truncated" in text and "no-direct-source-file" in text

        # The CLI writes the same two machine/human report forms used by CI.
        subprocess.run(
            [
                sys.executable,
                str(HERE / "supply_census.py"),
                "--candidates", str(candidates),
                "--json", str(root / "cli.json"),
                "--markdown", str(root / "cli.md"),
                "--learn", "en",
                "--meanings", "es",
                "--max-deck", "1",
                "--min-deck", "1",
                "--function-top", "0",
                "--wikimatrix-inventory", str(inventory),
                "--staging", str(root / "cli-stage.sqlite3"),
            ],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        parsed = json.loads((root / "cli.json").read_text(encoding="utf-8"))
        assert parsed["reportVersion"] == 1
        assert (root / "cli.md").read_text(encoding="utf-8").startswith("# Catalogue v2 supply census")

    print("Catalogue v2 supply census contracts: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
