#!/usr/bin/env python3
"""Contract checks for the Part 6/7 Everyday source experiment."""
from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from everyday_rebuild import build_report, markdown, parser, samples_markdown
from ingest.adapters import iter_massive_matrix, iter_tatoeba
from ingest.model import Candidate, Origin, read_jsonl, write_jsonl
from ingest.registry import SourceRegistry

FIXTURES = HERE / "fixtures" / "ingest"
REGISTRY = HERE / "sources" / "catalogue-v2-sources.json"


def main() -> int:
    registry = SourceRegistry.load(str(REGISTRY))
    with tempfile.TemporaryDirectory(prefix="ikna-everyday-rebuild-") as td:
        root = Path(td)
        tatoeba_path = root / "tatoeba.jsonl.gz"
        massive_path = root / "massive.jsonl.gz"

        tatoeba = list(
            iter_tatoeba(
                str(FIXTURES / "tatoeba"), registry.get("tatoeba"),
                "en", "es", source_version="fixture-1",
            )
        )
        write_jsonl(str(tatoeba_path), tatoeba)

        massive = list(
            iter_massive_matrix(
                str(FIXTURES / "massive"), registry.get("massive"),
                {"en"}, {"es"}, source_version="1.1-fixture",
            )
        )
        # Add one exact cross-source duplicate to prove the merged preview pool
        # retains both provenance origins instead of dropping credit.
        duplicate = Candidate(
            collection="everyday", lang="en", meaning_lang="es",
            context=tatoeba[0].context, meaning=tatoeba[0].meaning,
            origins=[Origin("massive", "1.1-fixture", "massive:1.1-fixture:en-US:999", "massive:1.1-fixture:es-ES:999")],
        )
        write_jsonl(str(massive_path), massive + [duplicate])

        args = parser().parse_args([
            "--tatoeba", str(tatoeba_path),
            "--massive", str(massive_path),
            "--massive-dump", str(FIXTURES / "massive"),
            "--pool", str(root / "pool.jsonl.gz"),
            "--json", str(root / "report.json"),
            "--markdown", str(root / "report.md"),
            "--samples", str(root / "samples.md"),
            "--staging", str(root / "stage.sqlite3"),
            "--merge-db", str(root / "merge.sqlite3"),
            "--learn", "en", "--meanings", "es",
            "--function-top", "0", "--sample-per-pair", "2",
        ])
        report = build_report(args)
        assert report["status"] == "not-admitted-0.11-re-evaluation-evidence"
        assert report["publicationSafe"] is False
        assert report["massiveLocaleQuality"]["en"].get("qualityRejectedRows", 0) == 0
        assert report["massiveLocaleQuality"]["en"]["qualityUnjudgedSeedRows"] == 4
        assert report["massiveLocaleQuality"]["es"]["qualityRejectedRows"] == 2
        assert report["massiveLocaleQuality"]["es"]["qualityLocalizedSlotRejectedRows"] == 1
        assert report["mergedPreviewPool"]["exactCrossSourceOverlaps"] == 1
        assert report["summary"]["massiveNewTargets"] > 0
        assert report["samples"]
        assert "does not admit or publish MASSIVE" in markdown(report)
        assert "MASSIVE deterministic manual-review samples" in samples_markdown(report)

        pool = read_jsonl(str(root / "pool.jsonl.gz"))
        duplicate_row = next(row for row in pool if row.id == tatoeba[0].id)
        assert {origin.source_family for origin in duplicate_row.origins} == {"tatoeba", "massive"}

        subprocess.run([
            sys.executable, str(HERE / "everyday_rebuild.py"),
            "--tatoeba", str(tatoeba_path),
            "--massive", str(massive_path),
            "--massive-dump", str(FIXTURES / "massive"),
            "--pool", str(root / "cli-pool.jsonl.gz"),
            "--json", str(root / "cli-report.json"),
            "--markdown", str(root / "cli-report.md"),
            "--samples", str(root / "cli-samples.md"),
            "--staging", str(root / "cli-stage.sqlite3"),
            "--merge-db", str(root / "cli-merge.sqlite3"),
            "--learn", "en", "--meanings", "es",
            "--function-top", "0", "--sample-per-pair", "1",
        ], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        parsed = json.loads((root / "cli-report.json").read_text(encoding="utf-8"))
        assert parsed["reportVersion"] == 1
        assert (root / "cli-report.md").read_text(encoding="utf-8").startswith("# Everyday source admission")

    print("Everyday Part 6/7 experiment contracts: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
