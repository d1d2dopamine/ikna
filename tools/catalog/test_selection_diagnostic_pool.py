#!/usr/bin/env python3
"""Bounded diagnostic inputs preserve source bytes, pins, scope and stable choice."""
import gzip
import hashlib
import json
import unittest
from pathlib import Path
from unittest.mock import patch
from ingest.model import Candidate, Origin
import test_everyday_selection as fixtures
import selection_diagnostic_pool as diagnostic


class DiagnosticPoolTests(unittest.TestCase):
    def setUp(self):
        self.fixture = fixtures.EverydaySelectionTests(methodName="test_exact_handoff_and_zero_supply_inventory")
        self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)
        self.root = self.fixture.root

    def args(self, name="bounded", per_pair=1):
        return diagnostic.parser().parse_args([
            "--pool", str(self.fixture.pool_path), "--pool-report", str(self.fixture.pool_report),
            "--output-dir", str(self.root / name), "--per-pair", str(per_pair)])

    def test_exact_min_hash_membership_bytes_and_repeatability(self):
        original = gzip.decompress(self.fixture.pool_path.read_bytes()).splitlines(keepends=True)
        expected = min(original, key=lambda raw: hashlib.sha256(
            (diagnostic.SAMPLE_VERSION + "\0" + json.loads(raw)["id"]).encode()).digest())
        first = diagnostic.sample(self.args())
        second = diagnostic.sample(self.args("again"))
        self.assertEqual(first, second)
        self.assertEqual(expected, (self.root / "bounded/diagnostic-source.jsonl").read_bytes())
        self.assertEqual(1, first["samplePool"]["uniqueCandidates"])
        self.assertEqual(2, first["pairs"][0]["parentRecords"])
        self.assertFalse(first["productionSnapshot"])
        self.assertFalse(first["publicationSafe"])

    def test_under_cap_keeps_all_source_rows_in_original_order(self):
        result = diagnostic.sample(self.args(per_pair=128))
        self.assertEqual(gzip.decompress(self.fixture.pool_path.read_bytes()),
                         (self.root / "bounded/diagnostic-source.jsonl").read_bytes())
        self.assertEqual(2, result["samplePool"]["uniqueCandidates"])

    def test_interleaved_pairs_have_independent_caps_and_original_order(self):
        records = []
        for number in range(6):
            meaning = "es" if number % 2 == 0 else "fr"
            records.append(Candidate("everyday", "en", meaning,
                                     f"The interesting sample number {number} remains here.",
                                     f"Explicit test fixture meaning {number}.",
                                     [Origin("tatoeba", fixtures.VERSION, f"tatoeba:{10000+number}",
                                             f"tatoeba:{20000+number}")]))
        self.fixture.replace_pool(records)
        result = diagnostic.sample(self.args(per_pair=2))
        original = gzip.decompress(self.fixture.pool_path.read_bytes()).splitlines(keepends=True)
        expected = set()
        for language in ("es", "fr"):
            group = [raw for raw in original if json.loads(raw)["meaningLang"] == language]
            expected.update(sorted(group, key=lambda raw: hashlib.sha256(
                (diagnostic.SAMPLE_VERSION + "\0" + json.loads(raw)["id"]).encode()).digest())[:2])
        selected = (self.root / "bounded/diagnostic-source.jsonl").read_bytes()
        self.assertEqual(b"".join(raw for raw in original if raw in expected), selected)
        self.assertEqual([2, 2], [row["sampleRecords"] for row in result["pairs"]])
        self.assertEqual([3, 3], [row["parentRecords"] for row in result["pairs"]])

    def test_raw_mismatch_refused(self):
        self.fixture.pool_path.write_bytes(b"bad")
        with self.assertRaisesRegex(ValueError, "raw identity"):
            diagnostic.sample(self.args())
        self.assertFalse((self.root / "bounded").exists())

    def test_logical_mismatch_refused(self):
        self.fixture.report["pool"]["logicalSha256"] = "0" * 64
        self.fixture.save_report()
        with self.assertRaisesRegex(ValueError, "logical identity"):
            diagnostic.sample(self.args())
        self.assertFalse((self.root / "bounded").exists())

    def test_existing_output_and_invalid_limit_refused(self):
        (self.root / "bounded").mkdir()
        with self.assertRaisesRegex(ValueError, "must be new"):
            diagnostic.sample(self.args())
        with self.assertRaisesRegex(ValueError, "per-pair"):
            diagnostic.sample(self.args("other", 0))

    def test_registry_change_during_build_has_no_completion(self):
        original = diagnostic.pool.build_report
        def mutate(args):
            result = original(args)
            result["registrySha256"] = "0" * 64
            return result
        with patch.object(diagnostic.pool, "build_report", side_effect=mutate):
            with self.assertRaisesRegex(ValueError, "registry changed"):
                diagnostic.sample(self.args())
        self.assertFalse((self.root / "bounded/DIAGNOSTIC.json").exists())

    def test_parent_admission_and_registry_fail_closed(self):
        self.fixture.report["sourceAdmissionPassed"] = False
        self.fixture.save_report()
        with self.assertRaisesRegex(ValueError, "admitted parent"):
            diagnostic.sample(self.args())
        self.fixture.report["sourceAdmissionPassed"] = True
        self.fixture.report["registrySha256"] = "0" * 64
        self.fixture.save_report()
        with self.assertRaisesRegex(ValueError, "registry"):
            diagnostic.sample(self.args())


if __name__ == "__main__":
    unittest.main()
