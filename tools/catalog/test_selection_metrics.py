#!/usr/bin/env python3
"""Deterministic stage timing and honest process-memory attribution fixtures."""
import threading
import unittest
from unittest.mock import mock_open, patch

from selection_metrics import StageRecorder, current_rss_bytes, process_peak_rss_bytes


class Clock:
    def __init__(self):
        self.wall = 0.0
        self.cpu = 0.0

    def advance(self, wall, cpu):
        self.wall += wall
        self.cpu += cpu


class SelectionMetricsTests(unittest.TestCase):
    def recorder(self, values=(100,) * 30, **kwargs):
        self.clock = Clock()
        reads = iter(values)
        return StageRecorder(sample_interval_seconds=60, rss_reader=lambda: next(reads),
                             peak_reader=lambda: 900, wall_clock=lambda: self.clock.wall,
                             cpu_clock=lambda: self.clock.cpu, **kwargs)

    def test_nested_windows_inclusive_rss_and_exclusive_clocks(self):
        recorder = self.recorder((100, 300, 200, 150))
        with recorder:
            with recorder.measure("outer", pair="en-ru"):
                self.clock.advance(2, 1)
                with recorder.measure("inner"):
                    self.clock.advance(4, 2)
                self.clock.advance(1, 0.5)
        report = recorder.report()
        outer, inner = report["stages"]["outer"], report["stages"]["inner"]
        self.assertEqual(7, outer["elapsedSeconds"])
        self.assertEqual(3.5, outer["processCpuSeconds"])
        self.assertEqual(3, outer["exclusiveElapsedSeconds"])
        self.assertEqual(1.5, outer["exclusiveProcessCpuSeconds"])
        self.assertEqual(4, inner["elapsedSeconds"])
        self.assertEqual(4, inner["exclusiveElapsedSeconds"])
        self.assertEqual(300, outer["sampledPeakRssBytes"])
        self.assertEqual(300, inner["sampledPeakRssBytes"])
        self.assertEqual(4, outer["rssSamples"])
        self.assertEqual(2, inner["rssSamples"])
        self.assertEqual(4, report["rssReadAttempts"])
        self.assertEqual(900, report["processPeakRssBytes"])
        self.assertIn("lower bound", report["rssSemantics"])
        self.assertIn("not exact allocations", report["rssSemantics"])
        self.assertFalse(report["publicationSafe"])
        self.assertEqual("complete", report["status"])

    def test_repeated_stages_aggregate_without_losing_calls(self):
        recorder = self.recorder((100,) * 50, max_records=2)
        with recorder:
            for index in range(20):
                with recorder.measure("allocation", deck=index):
                    self.clock.advance(2, 0.5)
        report = recorder.report()
        stage = report["stages"]["allocation"]
        self.assertEqual(20, stage["calls"])
        self.assertEqual(40, stage["elapsedSeconds"])
        self.assertEqual(10, stage["processCpuSeconds"])
        self.assertEqual(40, stage["rssSamples"])
        self.assertEqual(16, len(stage["metadataSamples"]))
        self.assertTrue(stage["metadataSamplesTruncated"])
        self.assertEqual(2, len(report["records"]))
        self.assertTrue(report["recordsTruncated"])
        report["stages"].clear()
        self.assertIn("allocation", recorder.report()["stages"])

    def test_exception_closes_sampler_and_records_failure(self):
        recorder = self.recorder()
        with self.assertRaisesRegex(ValueError, "pipeline failure"):
            with recorder:
                with recorder.measure("staging"):
                    self.clock.advance(1, 0.5)
                    raise ValueError("pipeline failure")
        self.assertFalse(recorder._thread.is_alive())
        self.assertTrue(recorder._thread.daemon)
        report = recorder.report()
        self.assertEqual("failed", report["status"])
        self.assertEqual(1, report["stages"]["staging"]["failedCalls"])
        recorder.close()

    def test_caught_inner_failure_still_marks_failed(self):
        recorder = self.recorder()
        with recorder:
            with recorder.measure("outer"):
                try:
                    with recorder.measure("inner"):
                        raise RuntimeError("caught")
                except RuntimeError:
                    pass
        self.assertEqual("failed", recorder.report()["status"])
        self.assertEqual(0, recorder.report()["stages"]["outer"]["failedCalls"])

    def test_unavailable_rss_is_null_not_zero(self):
        recorder = self.recorder((None, None))
        with recorder:
            with recorder.measure("staging"):
                pass
        report = recorder.report()
        self.assertEqual("unavailable", report["rssAvailability"])
        self.assertIsNone(report["stages"]["staging"]["sampledPeakRssBytes"])
        self.assertEqual(0, report["stages"]["staging"]["rssSamples"])
        self.assertEqual(2, report["rssUnavailableReads"])

    def test_reader_exceptions_do_not_mask_pipeline_error(self):
        def unreadable():
            raise OSError("unreadable proc")
        recorder = StageRecorder(rss_reader=unreadable, peak_reader=unreadable)
        with self.assertRaisesRegex(ValueError, "real failure"):
            with recorder:
                with recorder.measure("validation"):
                    raise ValueError("real failure")
        self.assertIsNone(recorder.report()["processPeakRssBytes"])
        self.assertIsNone(recorder.report()["stages"]["validation"]["sampledPeakRssBytes"])
        self.assertFalse(recorder._thread.is_alive())

    def test_no_measurements_cannot_claim_success(self):
        with self.recorder() as recorder:
            pass
        report = recorder.report()
        self.assertEqual("no-measurements", report["status"])
        self.assertEqual(0, report["measurementCalls"])
        self.assertEqual({}, report["stages"])

    def test_lifecycle_and_main_thread_only(self):
        recorder = self.recorder()
        with self.assertRaisesRegex(RuntimeError, "active recorder"):
            with recorder.measure("not-started"):
                pass
        with recorder:
            with self.assertRaisesRegex(RuntimeError, "close recorder"):
                recorder.report()
            errors = []
            def other_thread():
                try:
                    with recorder.measure("invalid-worker"):
                        pass
                except RuntimeError as error:
                    errors.append(str(error))
            thread = threading.Thread(target=other_thread)
            thread.start()
            thread.join()
            self.assertEqual(["stage recording is restricted to the main thread"], errors)
            with recorder.measure("valid"):
                with self.assertRaisesRegex(RuntimeError, "active"):
                    recorder.close()
        with self.assertRaisesRegex(RuntimeError, "one-shot"):
            recorder.__enter__()

    def test_background_sampling_captures_between_boundaries(self):
        observed = threading.Event()
        def reader():
            if threading.current_thread() is not threading.main_thread():
                observed.set()
                return 500
            return 100
        with StageRecorder(sample_interval_seconds=0.001, rss_reader=reader) as recorder:
            with recorder.measure("waiting"):
                self.assertTrue(observed.wait(2), "daemon did not sample")
        stage = recorder.report()["stages"]["waiting"]
        self.assertEqual(500, stage["sampledPeakRssBytes"])
        self.assertGreaterEqual(stage["rssSamples"], 3)
        self.assertFalse(recorder._thread.is_alive())

    def test_linux_reader_and_unsupported_platform(self):
        with patch("selection_metrics.sys.platform", "linux"), \
                patch("builtins.open", mock_open(read_data="200 12 3 4\n")), \
                patch("selection_metrics.os.sysconf", return_value=4096):
            self.assertEqual(12 * 4096, current_rss_bytes())
        with patch("selection_metrics.sys.platform", "win32"):
            self.assertIsNone(current_rss_bytes())
            self.assertIsNone(process_peak_rss_bytes())
            with StageRecorder() as recorder:
                with recorder.measure("stage"):
                    pass
            self.assertEqual("unsupported-platform-current-rss", recorder.report()["rssSource"])
            self.assertEqual("unavailable", recorder.report()["rssAvailability"])

    def test_clock_failure_unwinds_stage_and_joins_sampler(self):
        times = iter([0.0])
        recorder = StageRecorder(wall_clock=lambda: next(times))
        # A broken injected clock must fail evidence, but leave no sampler behind.
        with self.assertRaises(RuntimeError):
            with recorder:
                with recorder.measure("broken-clock"):
                    pass
        self.assertFalse(recorder._thread.is_alive())
        self.assertEqual("failed", recorder.report()["status"])

    def test_recursive_name_counts_inclusive_calls_separately(self):
        recorder = self.recorder()
        with recorder:
            with recorder.measure("same"):
                self.clock.advance(1, 0.25)
                with recorder.measure("same"):
                    self.clock.advance(2, 0.5)
        stage = recorder.report()["stages"]["same"]
        self.assertEqual(2, stage["calls"])
        self.assertEqual(5, stage["elapsedSeconds"])
        self.assertEqual(3, stage["exclusiveElapsedSeconds"])

    def test_process_peak_units_and_lifetime_label(self):
        try:
            import resource
        except ImportError:
            self.skipTest("resource is not installed on this platform")
        class Usage:
            ru_maxrss = 123
        with patch.object(resource, "getrusage", return_value=Usage()):
            with patch("selection_metrics.sys.platform", "linux"):
                self.assertEqual(123 * 1024, process_peak_rss_bytes())
            with patch("selection_metrics.sys.platform", "darwin"):
                self.assertEqual(123, process_peak_rss_bytes())

    def test_live_snapshot_is_detached_and_never_success(self):
        recorder = self.recorder()
        with recorder:
            with recorder.measure("verification"):
                pass
            with recorder.measure("staging"):
                snapshot = recorder.snapshot()
                self.assertEqual("incomplete", snapshot["status"])
                self.assertEqual(["staging"], snapshot["activeStages"])
                self.assertEqual(1, snapshot["stages"]["verification"]["calls"])
                self.assertNotIn("staging", snapshot["stages"])
                self.assertNotIn("records", snapshot)
                self.assertFalse(snapshot["resumable"])
                snapshot["stages"].clear()
                self.assertIn("verification", recorder.snapshot()["stages"])
        self.assertEqual("incomplete", recorder.snapshot()["status"])
        self.assertEqual("complete", recorder.report()["status"])

    def test_parameters_and_empty_name_rejected(self):
        for interval in (0, -1, float("nan"), float("inf")):
            with self.assertRaises(ValueError):
                StageRecorder(sample_interval_seconds=interval)
        with self.recorder() as recorder:
            with self.assertRaises(ValueError):
                with recorder.measure(" "):
                    pass


if __name__ == "__main__":
    unittest.main()
