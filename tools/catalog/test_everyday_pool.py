#!/usr/bin/env python3
"""Part 7 admitted-source, reproducibility and complete-input regressions."""
from __future__ import annotations

import dataclasses
import gzip
import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from everyday_pool import build_report, markdown, parser, sha256
from ingest.adapters import iter_tatoeba
from ingest.model import Candidate, Origin, read_jsonl, write_jsonl
from ingest.registry import SourceRegistry

REGISTRY = HERE / "sources" / "catalogue-v2-sources.json"
VERSION = "2026-09-12"


class EverydayPoolTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="ikna-everyday-pool-test-")
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        policy = SourceRegistry.load(str(REGISTRY)).get("tatoeba")
        self.records = list(iter_tatoeba(str(HERE / "fixtures/ingest/tatoeba"), policy, "en", "es", source_version=VERSION))
        self.input = self.root / "input.jsonl"
        write_jsonl(str(self.input), self.records)

    def arguments(self, prefix="out"):
        return parser().parse_args([
            "--candidates", str(self.input), "--source-version", VERSION,
            "--pool", str(self.root / (prefix + ".jsonl.gz")),
            "--json", str(self.root / (prefix + ".json")),
            "--markdown", str(self.root / (prefix + ".md")),
            "--samples", str(self.root / (prefix + "-samples.md")),
            "--staging", str(self.root / (prefix + ".sqlite3")),
            "--learn", "en", "--meanings", "es,fr", "--function-top", "0",
            "--sample-per-pair", "2",
        ])

    def test_pool_preserves_all_origins_and_does_not_pad_missing_pairs(self):
        duplicate = dataclasses.replace(self.records[0], origins=[Origin(
            "tatoeba", VERSION, "tatoeba:900", "tatoeba:901",
            attribution={"contextContributor": "Second writer"},
        )])
        write_jsonl(str(self.input), self.records + [duplicate])
        args = self.arguments()
        report = build_report(args)
        pool = read_jsonl(args.pool)
        self.assertEqual(len(self.records), len(pool))
        self.assertEqual(1, report["pool"]["duplicateCandidatesMerged"])
        merged = next(row for row in pool if row.id == duplicate.id)
        self.assertEqual({"tatoeba:100", "tatoeba:900"}, {origin.context_ref for origin in merged.origins})
        self.assertEqual("Alice", merged.origins[0].attribution["contextContributor"])
        missing = next(row for row in report["pairs"] if row["meaningLang"] == "fr")
        self.assertEqual("no-direct-rows", missing["diagnosis"])
        self.assertEqual(0, sum(missing["eligibleTargets"].values()))
        self.assertTrue(report["sourceAdmissionPassed"])
        self.assertTrue(report["completeInputScan"])
        self.assertFalse(report["publicationSafe"])
        self.assertEqual("provided-input-files-only", report["corpusCoverage"])
        self.assertIn("Selection, content review and freeze remain separate gates", markdown(report))

    def test_gzip_and_reports_are_reproducible_across_output_names(self):
        first_args, second_args = self.arguments("one"), self.arguments("two")
        first = build_report(first_args)
        second = build_report(second_args)
        self.assertEqual(Path(first_args.pool).read_bytes(), Path(second_args.pool).read_bytes())
        self.assertEqual(first, second)
        self.assertEqual(first["pool"]["sha256"], sha256(Path(first_args.pool)))
        self.assertEqual(first["pool"]["logicalSha256"], hashlib.sha256(gzip.decompress(Path(first_args.pool).read_bytes())).hexdigest())

    def test_gzip_inputs_keep_the_same_pool_and_coverage(self):
        plain = build_report(self.arguments("plain"))
        zipped = self.root / "input.jsonl.gz"
        write_jsonl(str(zipped), self.records)
        args = self.arguments("zipped")
        args.candidates = [str(zipped)]
        args.expect_sha256 = [sha256(zipped)]
        compressed = build_report(args)
        self.assertEqual(plain["pool"], compressed["pool"])
        self.assertEqual(plain["pairs"], compressed["pairs"])

    def test_registry_cannot_silently_readmit_an_experimental_policy(self):
        raw = json.loads(REGISTRY.read_text(encoding="utf-8"))
        next(row for row in raw["sources"] if row["id"] == "tatoeba")["publication"]["status"] = "candidate"
        altered = self.root / "registry.json"
        altered.write_text(json.dumps(raw), encoding="utf-8")
        args = self.arguments()
        args.registry = str(altered)
        with self.assertRaisesRegex(ValueError, "experimental candidate"):
            build_report(args)

    def test_global_targets_are_not_recounted_for_meaning_memberships(self):
        second_pair = Candidate("everyday", "en", "fr", self.records[0].context,
                                "Je prends soin de ma soeur.", [Origin("tatoeba", VERSION, "tatoeba:100", "tatoeba:902")])
        write_jsonl(str(self.input), self.records + [second_pair])
        report = build_report(self.arguments())
        summary = report["summary"]
        self.assertGreater(summary["eligibleTargetMemberships"], summary["uniqueEligibleGlobalTargets"])

    def test_outside_scope_is_counted_but_foreign_sources_still_fail_closed(self):
        reverse = Candidate("everyday", "es", "en", self.records[0].meaning,
                            self.records[0].context, [Origin("tatoeba", VERSION, "tatoeba:101", "tatoeba:100")])
        write_jsonl(str(self.input), self.records + [reverse])
        report = build_report(self.arguments())
        self.assertEqual(1, report["inputs"]["outsideRequestedScope"])
        self.assertEqual(len(self.records), report["pool"]["uniqueCandidates"])
        reverse.origins = [Origin("massive", "1.1", "massive:es:1", "massive:en:1")]
        write_jsonl(str(self.input), self.records + [reverse])
        with self.assertRaisesRegex(ValueError, "non-admitted family"):
            build_report(self.arguments())

    def test_every_origin_is_validated_and_previous_pool_survives_bad_input(self):
        args = self.arguments()
        build_report(args)
        before = Path(args.pool).read_bytes()
        for origin in (
            Origin("massive", "1.1", "massive:en:1", "massive:es:1"),
            Origin("tatoeba", "weekly", "tatoeba:100", "tatoeba:101"),
            Origin("tatoeba", VERSION, "", "tatoeba:101"),
            Origin("tatoeba", VERSION, "invented:100", "tatoeba:101"),
        ):
            with self.subTest(origin=origin):
                bad = dataclasses.replace(self.records[0], origins=self.records[0].origins + [origin])
                write_jsonl(str(self.input), self.records + [bad])
                with self.assertRaises(ValueError):
                    build_report(args)
                self.assertEqual(before, Path(args.pool).read_bytes())

    def test_pinned_digest_rejects_changed_bytes(self):
        args = self.arguments()
        args.expect_sha256 = [sha256(self.input)]
        build_report(args)
        with self.input.open("ab") as handle:
            handle.write(b"\n")
        with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
            build_report(args)

    def test_wrong_collection_version_scope_or_output_alias_is_rejected(self):
        for field, value in (("source_version", "weekly"), ("learn", ""), ("learn", "xx"),
                             ("meanings", "en"), ("function_top", -1), ("sample_per_pair", -1),
                             ("pool", str(self.input)), ("staging", str(REGISTRY)),
                             ("expect_sha256", ["bad"]), ("expect_sha256", ["0" * 64, "0" * 64])):
            with self.subTest(field=field, value=value):
                args = self.arguments()
                setattr(args, field, value)
                with self.assertRaises(ValueError):
                    build_report(args)
        bad = dataclasses.replace(self.records[0], collection="knowledge", id=None)
        write_jsonl(str(self.input), [bad])
        with self.assertRaisesRegex(ValueError, "foreign collection"):
            build_report(self.arguments())

    def test_utf8_errors_and_empty_inputs_do_not_create_a_pool(self):
        self.input.write_bytes(b'\xff\n')
        with self.assertRaises(UnicodeDecodeError):
            build_report(self.arguments())
        self.input.write_text("\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "no candidates"):
            build_report(self.arguments())
        self.assertFalse(Path(self.arguments().pool).exists())

    def test_cli_saves_pool_evidence_and_review_samples(self):
        args = self.arguments()
        command = [sys.executable, str(HERE / "everyday_pool.py")]
        for name in ("candidates", "source_version", "pool", "json", "markdown", "samples", "staging", "learn", "meanings", "function_top", "sample_per_pair"):
            value = getattr(args, name)
            command += ["--" + name.replace("_", "-")]
            command += value if isinstance(value, list) else [str(value)]
        result = subprocess.run(command, capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)
        report = json.loads(Path(args.json).read_text(encoding="utf-8"))
        self.assertEqual(7, report["part"])
        self.assertGreater(len(report["samples"]), 0)
        self.assertIn("tatoeba:", Path(args.samples).read_text(encoding="utf-8"))
        self.assertEqual(report["pool"]["sha256"], sha256(Path(args.pool)))


if __name__ == "__main__":
    unittest.main()
