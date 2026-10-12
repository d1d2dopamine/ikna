#!/usr/bin/env python3
"""End-to-end cache experiments must keep source gates and all selection evidence."""
import json
from pathlib import Path
import unittest
from unittest.mock import patch
import test_everyday_selection as fixtures
import selection_cache_experiment as experiment
import selection_experiment as selection


class CacheExperimentTests(unittest.TestCase):
    def setUp(self):
        self.fixture = fixtures.EverydaySelectionTests(methodName="test_exact_handoff_and_zero_supply_inventory")
        self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)
        self.root = self.fixture.root

    def args(self, name, mode="enabled", runs=1):
        return experiment.parser().parse_args([
            "--pool", str(self.fixture.pool_path), "--pool-report", str(self.fixture.pool_report),
            "--output-dir", str(self.root / name), "--input-scope", "fixture",
            "--cache-dir", str(self.root / "private-cache"), "--cache-mode", mode,
            "--runs", str(runs), "--max-deck", "20", "--min-deck", "1", "--thin-deck", "2"])

    def test_baseline_cold_warm_preserve_exact_reports_and_selected_material(self):
        baseline = experiment.run_experiment(self.args("baseline", "baseline"))
        cached = experiment.run_experiment(self.args("cached", runs=2))
        self.assertEqual([], baseline["cacheEvents"])
        self.assertEqual(["miss", "hit"], [r["status"] for r in cached["cacheEvents"]])
        self.assertTrue(all(identity == baseline["outputIdentities"][0] for identity in cached["outputIdentities"]))
        self.assertFalse(cached["productionCacheEnabled"])
        self.assertFalse(cached["publicationSafe"])

    def test_invalid_pool_rejected_before_cache_hit(self):
        experiment.run_experiment(self.args("populate"))
        self.fixture.pool_path.write_bytes(b"not the admitted pool")
        with patch.object(experiment, "run_cached_analysis", side_effect=AssertionError("cache bypassed validation")):
            with self.assertRaisesRegex(ValueError, "SHA-256/size"):
                experiment.run_experiment(self.args("invalid"))
        self.assertFalse((self.root / "invalid/CACHE-EXPERIMENT.json").exists())

    def test_wrapper_is_restored_after_cache_failure(self):
        original = selection.build_context_analysis
        with patch.object(experiment, "run_cached_analysis", side_effect=ValueError("cache corrupt")):
            with self.assertRaisesRegex(ValueError, "cache corrupt"):
                experiment.run_experiment(self.args("failed"))
        self.assertIs(original, selection.build_context_analysis)
        self.assertFalse((self.root / "failed/CACHE-EXPERIMENT.json").exists())

    def test_warm_cache_does_not_call_analyzer(self):
        experiment.run_experiment(self.args("populate"))
        with patch.object(selection, "build_context_analysis", side_effect=AssertionError("unexpected analysis")):
            warm = experiment.run_experiment(self.args("warm"))
        self.assertEqual(["hit"], [r["status"] for r in warm["cacheEvents"]])


if __name__ == "__main__":
    unittest.main()
