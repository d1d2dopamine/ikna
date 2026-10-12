#!/usr/bin/env python3
"""Owned-child resource limits and durable failure evidence, without large inputs."""
import json
import os
import signal
import time
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import selection_profile_guard as guard
import test_everyday_selection as fixtures


class FakeChild:
    pid = 12345

    def __init__(self, stubborn=False):
        self.returncode = None
        self.terminated = False
        self.killed = False
        self.reaped = False
        self.stubborn = stubborn

    def poll(self):
        return self.returncode

    def terminate(self):
        self.terminated = True
        if not self.stubborn:
            self.returncode = -15

    def kill(self):
        self.killed = True
        self.returncode = -9

    def wait(self, timeout=None):
        if self.returncode is None:
            raise subprocess.TimeoutExpired("owned-fixture", timeout)
        self.reaped = True
        return self.returncode


@unittest.skipUnless(sys.platform.startswith("linux"), "Linux resource supervisor")
class SelectionProfileGuardTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def args(self, **changes):
        args = guard.parser().parse_args(["--evidence-dir", str(self.root / "guard"), "--",
                                         "--output-dir", str(self.root / "profile")])
        for key, value in changes.items():
            setattr(args, key, value)
        return args

    @staticmethod
    def sample(**changes):
        sample = {"rssBytes": 100, "virtualBytes": 1000, "availableBytes": 8 * guard.GIB,
                  "hostAvailableBytes": 8 * guard.GIB, "diskFreeBytes": 20 * guard.GIB,
                  "cgroupVisibility": "unavailable", "cgroupHeadroomBytes": None,
                  "monotonicSeconds": 1.0}
        sample.update(changes)
        return sample

    def evidence(self):
        return json.loads((self.root / "guard/RESOURCE.json").read_text())

    def test_preflight_headroom_refuses_without_spawning(self):
        with patch.object(guard, "resource_sample", return_value=self.sample(availableBytes=1)), \
                patch.object(guard.subprocess, "Popen") as spawn:
            result = guard.supervise(self.args())
        spawn.assert_not_called()
        self.assertEqual("refused-preflight", result["status"])
        self.assertEqual("available-memory-headroom", self.evidence()["stopReason"])

    def test_unavailable_required_resources_fail_closed(self):
        with patch.object(guard, "resource_sample", side_effect=OSError("missing /proc")), \
                patch.object(guard.subprocess, "Popen") as spawn:
            with self.assertRaises(OSError):
                guard.supervise(self.args())
        spawn.assert_not_called()
        self.assertEqual("guard-failed", self.evidence()["status"])

    def test_rss_stop_only_terminates_and_reaps_owned_child(self):
        child = FakeChild()
        with patch.object(guard, "resource_sample", side_effect=[self.sample(), self.sample(rssBytes=5 * guard.GIB)]), \
                patch.object(guard.subprocess, "Popen", return_value=child) as spawn:
            result = guard.supervise(self.args())
        self.assertEqual("resource-stop", result["status"])
        self.assertEqual("rss-limit", result["stopReason"])
        self.assertTrue(child.terminated)
        self.assertTrue(child.reaped)
        self.assertEqual(-15, self.evidence()["exitCode"])
        command = spawn.call_args.args[0]
        self.assertEqual(sys.executable, command[0])
        self.assertIn("--child-address-space", command)
        self.assertEqual(5 * guard.GIB, int(command[3]))
        child_env = spawn.call_args.kwargs["env"]
        for key in ("TMPDIR", "TMP", "TEMP", "SQLITE_TMPDIR"):
            self.assertEqual(str(self.root / "guard/tmp"), child_env[key])
        self.assertTrue((self.root / "guard/tmp").is_dir())

    def test_monitor_failure_and_interrupt_reap_child(self):
        for error in (OSError("rss became unavailable"), KeyboardInterrupt()):
            child = FakeChild()
            directory = self.root / ("interrupt" if isinstance(error, KeyboardInterrupt) else "error")
            with patch.object(guard, "resource_sample", side_effect=[self.sample(), error]), \
                    patch.object(guard.subprocess, "Popen", return_value=child):
                with self.assertRaises(type(error)):
                    guard.supervise(self.args(evidence_dir=str(directory)))
            self.assertTrue(child.reaped)
            self.assertTrue(child.terminated)
            self.assertEqual(-15, json.loads((directory / "RESOURCE.json").read_text())["exitCode"])

    def test_stubborn_child_is_killed_and_reaped(self):
        child = FakeChild(stubborn=True)
        guard.terminate_owned(child)
        self.assertTrue(child.terminated)
        self.assertTrue(child.killed)
        self.assertTrue(child.reaped)

    def test_disk_and_available_thresholds(self):
        args = self.args()
        self.assertEqual("disk-headroom", guard.violation(self.sample(diskFreeBytes=1), args))
        self.assertEqual("available-memory-headroom", guard.violation(self.sample(availableBytes=1), args))
        self.assertIsNone(guard.violation(self.sample(), args))

    def test_resource_limits_and_paths_rejected(self):
        for changes in ({"poll_seconds": float("nan")}, {"poll_seconds": 0},
                        {"max_rss_bytes": 0}, {"address_space_bytes": guard.GIB}):
            with self.assertRaises(ValueError):
                guard.supervise(self.args(**changes))
        with self.assertRaisesRegex(ValueError, "disjoint"):
            guard.supervise(self.args(profile_args=["--output-dir", str(self.root / "guard/profile")]))

    def test_sigterm_handler_restored_after_preflight_failure(self):
        before = signal.getsignal(signal.SIGTERM)
        with patch.object(guard, "resource_sample", side_effect=OSError("missing RSS")):
            with self.assertRaises(OSError):
                guard.supervise(self.args())
        self.assertEqual(before, signal.getsignal(signal.SIGTERM))

    def test_supervisor_signals_do_not_leave_running_child(self):
        for signum in (signal.SIGTERM, signal.SIGKILL):
            with self.subTest(signal=signum):
                folder = self.root / str(signum)
                folder.mkdir()
                fifo = folder / "blocked-pool.gz"
                os.mkfifo(fifo)
                report = folder / "pool-report.json"
                report.write_text("{}")
                command = [sys.executable, str(Path(guard.__file__).resolve()),
                           "--evidence-dir", str(folder / "guard"),
                           "--min-available-bytes", "1", "--min-disk-free-bytes", "1",
                           "--poll-seconds", "0.01", "--", "--pool", str(fifo),
                           "--pool-report", str(report), "--output-dir", str(folder / "profile"),
                           "--input-scope", "fixture", "--measurement-mode", "stages", "--runs", "1"]
                supervisor = subprocess.Popen(command, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                try:
                    evidence_path = folder / "guard/RESOURCE.json"
                    deadline = time.monotonic() + 5
                    child_pid = None
                    while time.monotonic() < deadline:
                        if evidence_path.exists():
                            value = json.loads(evidence_path.read_text())
                            if value.get("sampleCount", 0) > 1:
                                child_pid = value["childPid"]
                                break
                        self.assertIsNone(supervisor.poll(), "supervisor exited before signal fixture")
                        time.sleep(0.01)
                    self.assertIsNotNone(child_pid, "child startup timed out")
                    supervisor.send_signal(signum)
                    supervisor.wait(timeout=5)
                    deadline = time.monotonic() + 5
                    while time.monotonic() < deadline:
                        status_file = Path(f"/proc/{child_pid}/status")
                        try:
                            status = status_file.read_text()
                        except FileNotFoundError:
                            break
                        # SIGKILL cannot reap; Linux parent-death signal terminates
                        # the orphan, which is then reaped by its adoptive parent.
                        if any(line.startswith("State:") and "Z" in line for line in status.splitlines()):
                            break
                        time.sleep(0.01)
                    else:
                        self.fail("owned child remained running after supervisor exit")
                    if signum == signal.SIGTERM:
                        saved = json.loads(evidence_path.read_text())
                        self.assertEqual("interrupted", saved["status"])
                        self.assertIsNotNone(saved["exitCode"])
                finally:
                    if supervisor.poll() is None:
                        supervisor.terminate()
                        try:
                            supervisor.wait(timeout=5)
                        except subprocess.TimeoutExpired:
                            supervisor.kill()
                            supervisor.wait()

    def test_real_child_small_fixture_completes_with_resource_evidence(self):
        fixture = fixtures.EverydaySelectionTests(methodName="test_exact_handoff_and_zero_supply_inventory")
        fixture.setUp()
        self.addCleanup(fixture.doCleanups)
        args = self.args(poll_seconds=0.01, min_available_bytes=1, min_disk_free_bytes=1,
                         profile_args=["--pool", str(fixture.pool_path), "--pool-report", str(fixture.pool_report),
                                       "--output-dir", str(self.root / "profile"), "--input-scope", "fixture",
                                       "--measurement-mode", "stages", "--runs", "1", "--max-deck", "20",
                                       "--min-deck", "1", "--thin-deck", "2"])
        result = guard.supervise(args)
        self.assertEqual("child-exited-zero", result["status"],
                         (self.root / "guard/stderr.log").read_text())
        self.assertEqual(0, result["exitCode"])
        self.assertGreater(result["sampleCount"], 0)
        self.assertTrue((self.root / "profile/PROFILE.json").is_file())
        self.assertFalse(result["publicationSafe"])
        self.assertFalse(result["resumable"])


if __name__ == "__main__":
    unittest.main()
