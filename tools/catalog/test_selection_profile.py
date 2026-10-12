#!/usr/bin/env python3
"""Profiling must preserve selection and never weaken admitted-input checks."""
import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import selection_profile as profile
import everyday_selection as admitted
import test_everyday_selection as fixtures


class SelectionProfileTests(unittest.TestCase):
    def setUp(self):
        self.fixture = fixtures.EverydaySelectionTests(methodName="test_exact_handoff_and_zero_supply_inventory")
        self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)
        self.root = self.fixture.root

    def args(self, **changes):
        args = profile.parser().parse_args([
            "--pool", str(self.fixture.pool_path), "--pool-report", str(self.fixture.pool_report),
            "--output-dir", str(self.root / "profile"), "--input-scope", "fixture",
            "--max-deck", "20", "--min-deck", "1", "--thin-deck", "2"])
        for key, value in changes.items():
            setattr(args, key, value)
        return args

    def test_profile_matches_uninstrumented_and_repeated_complete_output(self):
        baseline = self.fixture.args("baseline")
        baseline.quality_policy = "boundary-diversity-v2"
        baseline.review_per_deck = 10
        baseline.selected_output = str(self.root / "baseline-selected.jsonl.gz")
        report, preview = admitted.build_report(baseline)
        result = profile.run_profile(self.args())
        self.assertEqual("complete-bounded-diagnostic", result["status"])
        self.assertTrue(result["outputsEqualAcrossRuns"])
        self.assertFalse(result["publicationSafe"])
        for run in result["runs"]:
            self.assertEqual(profile.canonical_hash(report), run["outputIdentity"]["reportSha256"])
            self.assertEqual(profile.canonical_hash(preview), run["outputIdentity"]["previewSha256"])
            self.assertEqual(report["selectedMaterial"]["sha256"], run["outputIdentity"]["selectedSha256"])
            for boundary in ("verification", "staging", "ranks", "contextAnalysis", "pairEvidence", "targetOrdering", "contextChoice", "selectedRowOutput", "selectedOutputFinalize"):
                self.assertGreater(run["boundaries"][boundary]["calls"], 0, boundary)
            self.assertGreater(run["stagingBytes"], 0)
        self.assertEqual(result, json.loads((self.root / "profile/PROFILE.json").read_text()))

    def test_explicit_stage_metrics_preserve_complete_output(self):
        base = profile.run_profile(self.args(output_dir=str(self.root / "cprofile"), runs=1))
        measured = profile.run_profile(self.args(measurement_mode="stages", runs=2))
        for run in measured["runs"]:
            self.assertEqual(base["runs"][0]["outputIdentity"], run["outputIdentity"])
            self.assertIsNone(run["boundaries"])
            self.assertEqual("complete", run["stageMetrics"]["status"])
            stages = run["stageMetrics"]["stages"]
            for name in ("verification", "staging", "ranks", "context_analysis", "pair_evidence",
                         "allocation", "selected_output", "selected_output_finalize", "diagnostic_output"):
                self.assertGreater(stages[name]["calls"], 0, name)
            self.assertEqual(3, stages["allocation"]["calls"])
        self.assertTrue(measured["outputsEqualAcrossRuns"])

    def test_full_scope_is_diagnostic_not_bounded_or_acceptance(self):
        result = profile.run_profile(self.args(input_scope="full-saved-input",
                                              measurement_mode="stages", runs=1))
        self.assertEqual("complete-full-saved-input-diagnostic", result["status"])
        self.assertFalse(result["publicationSafe"])
        self.assertIsNone(result["outputsEqualAcrossRuns"])
        self.assertIn("not resumable", result["measurementContract"]["resume"])
        self.assertEqual(64 * 1024 * 1024, self.args().max_input_bytes)

    def test_full_scope_requires_progress_mode_and_explicit_size_override(self):
        with self.assertRaisesRegex(ValueError, "requires stages"):
            profile.run_profile(self.args(input_scope="full-saved-input"))
        large = self.root / "large-pool.gz"
        with large.open("wb") as handle:
            handle.truncate(64 * 1024 * 1024 + 1)
        with patch.object(admitted, "build_report") as build:
            with self.assertRaisesRegex(ValueError, "byte limit"):
                profile.run_profile(self.args(pool=str(large), input_scope="full-saved-input",
                                              measurement_mode="stages"))
            build.assert_not_called()

    def test_failure_keeps_completed_verification_and_failed_partial(self):
        def failure(args, metrics):
            with metrics.measure("verification"):
                pass
            with metrics.measure("staging"):
                raise RuntimeError("staging failed")
        with patch.object(admitted, "build_report", side_effect=failure):
            with self.assertRaisesRegex(RuntimeError, "staging failed"):
                profile.run_profile(self.args(measurement_mode="stages", runs=1))
        progress = json.loads((self.root / "profile/run-01/STAGE-PROGRESS.json").read_text())
        self.assertEqual("incomplete", progress["status"])
        self.assertEqual(1, progress["stages"]["verification"]["calls"])
        self.assertEqual(1, progress["stages"]["staging"]["failedCalls"])
        partial = json.loads((self.root / "profile/PROFILE.partial.json").read_text())
        self.assertEqual("failed", partial["status"])
        self.assertFalse(partial["resumable"])
        self.assertFalse((self.root / "profile/PROFILE.json").exists())

    def test_hard_child_exit_preserves_last_complete_stage(self):
        progress = self.root / "hard-exit-progress.json"
        program = "\n".join([
            "import os", "from pathlib import Path",
            "from selection_metrics import StageRecorder",
            "from selection_profile import ProgressRecorder",
            "with StageRecorder() as recorder:",
            "    metrics = ProgressRecorder(recorder, Path(os.environ['PROGRESS_FILE']))",
            "    with metrics.measure('verification'):",
            "        pass",
            "    with metrics.measure('staging'):",
            "        os._exit(17)"])
        result = subprocess.run([sys.executable, "-c", program],
                                env={**os.environ, "PYTHONPATH": str(profile.HERE),
                                     "PROGRESS_FILE": str(progress)}, check=False)
        self.assertEqual(17, result.returncode)
        saved = json.loads(progress.read_text())
        self.assertEqual("incomplete", saved["status"])
        self.assertEqual(["staging"], saved["activeStages"])
        self.assertEqual(1, saved["stages"]["verification"]["calls"])
        self.assertNotIn("staging", saved["stages"])
        self.assertFalse(saved["resumable"])

    def test_existing_output_preserved(self):
        output = self.root / "profile"
        output.mkdir()
        sentinel = output / "PROFILE.json"
        sentinel.write_text("previous complete evidence")
        with self.assertRaisesRegex(ValueError, "must be new"):
            profile.run_profile(self.args())
        self.assertEqual("previous complete evidence", sentinel.read_text())

    def test_oversized_input_refused_before_selection(self):
        with patch.object(admitted, "build_report") as build:
            with self.assertRaisesRegex(ValueError, "byte limit"):
                profile.run_profile(self.args(max_input_bytes=1))
            build.assert_not_called()
        self.assertFalse((self.root / "profile").exists())

    def test_corrupt_pool_does_not_write_success(self):
        self.fixture.pool_path.write_bytes(b"corrupt gzip")
        with self.assertRaisesRegex(ValueError, "SHA-256/size"):
            profile.run_profile(self.args())
        self.assertFalse((self.root / "profile/PROFILE.json").exists())

    def test_failure_preserves_sources_and_no_success(self):
        before = self.fixture.pool_path.read_bytes()
        with patch.object(admitted, "build_report", side_effect=RuntimeError("interrupted")):
            with self.assertRaisesRegex(RuntimeError, "interrupted"):
                profile.run_profile(self.args())
        self.assertEqual(before, self.fixture.pool_path.read_bytes())
        self.assertFalse((self.root / "profile/PROFILE.json").exists())

    def test_changed_result_is_not_repeatability_success(self):
        original = admitted.build_report
        count = 0
        def change(args):
            nonlocal count
            report, preview = original(args)
            count += 1
            if count == 2:
                report["summary"]["selectedTargets"] += 1
            return report, preview
        with patch.object(admitted, "build_report", side_effect=change):
            with self.assertRaisesRegex(ValueError, "canonical output"):
                profile.run_profile(self.args())
        self.assertFalse((self.root / "profile/PROFILE.json").exists())

    def test_post_run_input_mutation_has_no_completed_evidence(self):
        original = admitted.build_report
        def mutate(args):
            result = original(args)
            with self.fixture.pool_report.open("a") as handle:
                handle.write(" ")
            return result
        with patch.object(admitted, "build_report", side_effect=mutate):
            with self.assertRaisesRegex(ValueError, "input changed"):
                profile.run_profile(self.args(runs=1))
        self.assertFalse((self.root / "profile/PROFILE.json").exists())

    def test_pipeline_mutation_has_no_completed_evidence(self):
        with patch.object(profile, "pipeline_identity", side_effect=[{"tool": "old"}, {"tool": "new"}]):
            with self.assertRaisesRegex(ValueError, "pipeline changed"):
                profile.run_profile(self.args(runs=1))
        self.assertFalse((self.root / "profile/PROFILE.json").exists())

    def test_legacy_policy_matches_uninstrumented(self):
        args = self.fixture.args("legacy")
        args.review_per_deck = 10
        args.selected_output = str(self.root / "legacy-selected.jsonl.gz")
        report, preview = admitted.build_report(args)
        result = profile.run_profile(self.args(quality_policy="legacy-v1", runs=1))
        self.assertEqual(profile.canonical_hash(report), result["runs"][0]["outputIdentity"]["reportSha256"])
        self.assertEqual(profile.canonical_hash(preview), result["runs"][0]["outputIdentity"]["previewSha256"])

    def test_invalid_limits_and_single_run_are_explicit(self):
        for changes in ({"runs": 0}, {"runs": 6}, {"max_input_bytes": 0}):
            with self.assertRaises(ValueError):
                profile.run_profile(self.args(**changes))
        result = profile.run_profile(self.args(runs=1))
        self.assertIsNone(result["outputsEqualAcrossRuns"])


if __name__ == "__main__":
    unittest.main()
