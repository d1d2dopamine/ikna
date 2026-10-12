#!/usr/bin/env python3
"""Synthetic/mock-only resource guard tests; never run a corpus or selector."""
from contextlib import redirect_stdout
import io
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
from types import ModuleType, SimpleNamespace
import unittest
from unittest.mock import patch

if sys.platform.startswith("linux"):
    import resource

import allocation_diagnostic_guard as guard
import selection_profile_guard as original


class FakeChild:
    pid = 23456

    def __init__(self, returncode=None, stubborn=False):
        self.returncode = returncode
        self.stubborn = stubborn
        self.terminated = self.killed = self.reaped = False

    def poll(self):
        return self.returncode

    def terminate(self):
        self.terminated = True
        if not self.stubborn:
            self.returncode = -signal.SIGTERM

    def kill(self):
        self.killed = True
        self.returncode = -signal.SIGKILL

    def wait(self, timeout=None):
        if self.returncode is None:
            raise subprocess.TimeoutExpired("owned synthetic child", timeout)
        self.reaped = True
        return self.returncode


@unittest.skipUnless(sys.platform.startswith("linux"), "Linux resource supervisor")
class AllocationGuardTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="ikna-allocation-guard-fixture-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.inputs = {}
        for name in guard.INPUT_OPTIONS:
            path = self.root / (name + ".fixture")
            path.write_bytes(b"SYNTHETIC INPUT ONLY")
            self.inputs[name] = path

    def args(self, **updates):
        child = [part for name, value in self.inputs.items() for part in ("--" + name, str(value))]
        child += ["--scratch", str(self.root / "scratch"), "--out", str(self.root / "output")]
        result = guard.parser().parse_args(["--evidence-dir", str(self.root / "guard"), "--", *child])
        for key, value in updates.items():
            setattr(result, key, value)
        return result

    @staticmethod
    def sample(**updates):
        result = {"rssBytes": 100, "virtualBytes": 1000, "availableBytes": 8 * guard.GIB,
                  "hostAvailableBytes": 8 * guard.GIB, "diskFreeBytes": 20 * guard.GIB,
                  "cgroupVisibility": "unavailable", "cgroupHeadroomBytes": None,
                  "monotonicSeconds": 1.0}
        result.update(updates)
        return result

    def saved(self, directory="guard"):
        return json.loads((self.root / directory / "RESOURCE.json").read_text(encoding="utf-8"))

    def test_defaults_and_helpers_remain_shared(self):
        args = self.args()
        self.assertEqual((args.max_rss_bytes, args.address_space_bytes, args.min_available_bytes,
                          args.min_disk_free_bytes, args.max_wall_seconds),
                         (4 * guard.GIB, 5 * guard.GIB, 2 * guard.GIB, 3 * guard.GIB, 5400))
        for name in ("resource_sample", "violation", "terminate_owned", "parent_death_guard", "atomic_json"):
            self.assertIs(getattr(guard, name), getattr(original, name))

    def test_preflight_refuses_all_sampled_limits_without_spawning(self):
        for index, (updates, reason) in enumerate((({"rssBytes": 4 * guard.GIB}, "rss-limit"),
                                                  ({"availableBytes": 1}, "available-memory-headroom"),
                                                  ({"diskFreeBytes": 1}, "disk-headroom"))):
            directory = "guard-" + str(index)
            with patch.object(guard, "resource_sample", return_value=self.sample(**updates)), \
                    patch.object(guard.subprocess, "Popen") as spawn:
                result = guard.supervise(self.args(evidence_dir=str(self.root / directory)))
            spawn.assert_not_called()
            self.assertEqual(result["status"], "refused-preflight")
            self.assertEqual(self.saved(directory)["stopReason"], reason)
            self.assertEqual(result["sampleCount"], 0)
            self.assertIsNone(result["sampledPeakRssBytes"])
            self.assertFalse((self.root / "scratch").exists())
            self.assertFalse((self.root / "output").exists())

    def test_unavailable_preflight_fails_closed_and_restores_signal(self):
        before = signal.getsignal(signal.SIGTERM)
        with patch.object(guard, "resource_sample", side_effect=OSError("SYNTHETIC PRIVATE ERROR")), \
                patch.object(guard.subprocess, "Popen") as spawn:
            with self.assertRaises(OSError):
                guard.supervise(self.args())
        spawn.assert_not_called()
        self.assertEqual(self.saved()["status"], "guard-failed")
        self.assertEqual(self.saved()["failure"], {"type": "OSError"})
        self.assertNotIn("SYNTHETIC PRIVATE ERROR", json.dumps(self.saved()))
        self.assertEqual(signal.getsignal(signal.SIGTERM), before)

    def test_rss_stop_fixed_routing_temp_filesystem_and_owned_reaping(self):
        child = FakeChild()
        with patch.object(guard, "resource_sample", side_effect=[self.sample(), self.sample(rssBytes=4 * guard.GIB)]), \
                patch.object(guard.subprocess, "Popen", return_value=child) as spawn:
            result = guard.supervise(self.args())
        self.assertEqual((result["status"], result["stopReason"]), ("resource-stop", "rss-limit"))
        self.assertTrue(child.terminated and child.reaped)
        self.assertEqual(self.saved()["exitCode"], -signal.SIGTERM)
        command = spawn.call_args.args[0]
        self.assertEqual(command[:2], [sys.executable, str(Path(guard.__file__).resolve())])
        self.assertEqual(command[2:7], ["--child-address-space", str(5 * guard.GIB), "--parent-pid", str(os.getpid()), "--"])
        self.assertEqual(command[7:], result["allocationArgs"])
        self.assertNotIn("shell", spawn.call_args.kwargs)
        for name in ("TMPDIR", "TMP", "TEMP", "SQLITE_TMPDIR"):
            self.assertEqual(spawn.call_args.kwargs["env"][name], str(self.root / "guard/tmp"))
        self.assertTrue((self.root / "guard/tmp").is_dir())

    def test_wall_timeout_stops_and_reaps_only_owned_child(self):
        child = FakeChild()
        with patch.object(guard, "resource_sample", return_value=self.sample()), \
                patch.object(guard.subprocess, "Popen", return_value=child), \
                patch.object(guard.time, "monotonic", side_effect=[0, 1, 5401, 5402]):
            result = guard.supervise(self.args())
        self.assertEqual((result["status"], result["stopReason"]), ("resource-stop", "wall-time-limit"))
        self.assertTrue(child.terminated and child.reaped)
        self.assertEqual(self.saved()["elapsedSeconds"], 5401)

    def test_child_success_failure_and_sample_extrema(self):
        for exit_code in (0, 7):
            child = FakeChild()
            samples = [self.sample(), self.sample(rssBytes=1000, availableBytes=7 * guard.GIB),
                       self.sample(rssBytes=2000, availableBytes=6 * guard.GIB, diskFreeBytes=10 * guard.GIB)]
            sleeps = []
            def finish_after_two_samples(seconds):
                sleeps.append(seconds)
                if len(sleeps) == 2:
                    child.returncode = exit_code
            directory = "guard-" + str(exit_code)
            with patch.object(guard, "resource_sample", side_effect=samples), \
                    patch.object(guard.subprocess, "Popen", return_value=child), \
                    patch.object(guard.time, "sleep", side_effect=finish_after_two_samples):
                result = guard.supervise(self.args(evidence_dir=str(self.root / directory)))
            self.assertEqual(result["status"], "child-exited-zero" if exit_code == 0 else "child-failed")
            self.assertEqual(result["exitCode"], exit_code)
            self.assertEqual((result["sampleCount"], result["sampledPeakRssBytes"]), (2, 2000))
            self.assertEqual(result["minimumAvailableBytes"], 6 * guard.GIB)
            self.assertEqual(result["minimumDiskFreeBytes"], 10 * guard.GIB)
            self.assertTrue(child.reaped)
            self.assertFalse(child.terminated)
            self.assertFalse(result["resumable"])
            self.assertFalse(result["publicationSafe"])

    def test_fast_child_exit_does_not_invent_a_sample(self):
        child = FakeChild(returncode=0)
        with patch.object(guard, "resource_sample", return_value=self.sample()), \
                patch.object(guard.subprocess, "Popen", return_value=child):
            result = guard.supervise(self.args())
        self.assertEqual(result["status"], "child-exited-zero")
        self.assertEqual(result["sampleCount"], 0)
        self.assertIsNone(result["sampledPeakRssBytes"])

    def test_monitor_fault_keyboard_interrupt_and_sigterm_reap_child(self):
        for index, error in enumerate((OSError("SYNTHETIC ERROR"), KeyboardInterrupt(), guard.GuardTerminated())):
            child = FakeChild()
            directory = "guard-" + str(index)
            before = signal.getsignal(signal.SIGTERM)
            with patch.object(guard, "resource_sample", side_effect=[self.sample(), error]), \
                    patch.object(guard.subprocess, "Popen", return_value=child):
                with self.assertRaises(type(error)):
                    guard.supervise(self.args(evidence_dir=str(self.root / directory)))
            self.assertTrue(child.terminated and child.reaped)
            self.assertEqual(self.saved(directory)["exitCode"], -signal.SIGTERM)
            self.assertEqual(self.saved(directory)["status"], "guard-failed" if index == 0 else "interrupted")
            self.assertEqual(signal.getsignal(signal.SIGTERM), before)

    def test_proc_exit_race_settles_without_false_guard_failure(self):
        child = FakeChild()
        def exiting_sample(pid, directory):
            if pid == os.getpid():
                return self.sample()
            child.returncode = 0
            raise OSError("synthetic /proc race")
        with patch.object(guard, "resource_sample", side_effect=exiting_sample), \
                patch.object(guard.subprocess, "Popen", return_value=child):
            result = guard.supervise(self.args())
        self.assertEqual(result["status"], "child-exited-zero")
        self.assertTrue(child.reaped)

    def test_spawn_failure_records_evidence_without_unowned_cleanup(self):
        with patch.object(guard, "resource_sample", return_value=self.sample()), \
                patch.object(guard.subprocess, "Popen", side_effect=OSError("synthetic spawn failure")), \
                patch.object(guard, "terminate_owned") as terminate:
            with self.assertRaises(OSError):
                guard.supervise(self.args())
        terminate.assert_not_called()
        self.assertEqual(self.saved()["status"], "guard-failed")
        self.assertIsNone(self.saved()["childPid"])

    def test_stubborn_child_killed_and_partial_state_preserved(self):
        child = FakeChild(stubborn=True)
        def spawn(*args, **kwargs):
            (self.root / "scratch").mkdir()
            (self.root / "scratch/partial.sqlite").write_bytes(b"SYNTHETIC PARTIAL")
            (self.root / "output").mkdir()
            (self.root / "output/STATE.json").write_text('{"fixture":true}')
            return child
        with patch.object(guard, "resource_sample", side_effect=[self.sample(), self.sample(diskFreeBytes=1)]), \
                patch.object(guard.subprocess, "Popen", side_effect=spawn):
            result = guard.supervise(self.args())
        self.assertTrue(child.terminated and child.killed and child.reaped)
        self.assertEqual(result["stopReason"], "disk-headroom")
        self.assertEqual((self.root / "scratch/partial.sqlite").read_bytes(), b"SYNTHETIC PARTIAL")
        self.assertTrue((self.root / "output/STATE.json").is_file())
        self.assertEqual(result["partialState"], {"scratchDirectoryExists": True, "outputDirectoryExists": True})
        self.assertTrue(result["partialStatePreserved"])
        self.assertFalse(result["resumable"])

    def test_bad_limits_refuse_before_evidence_creation(self):
        for updates in ({"poll_seconds": float("nan")}, {"poll_seconds": 0}, {"poll_seconds": 11},
                        {"max_wall_seconds": float("inf")}, {"max_wall_seconds": 0},
                        {"max_rss_bytes": 0}, {"max_rss_bytes": True},
                        {"address_space_bytes": guard.GIB}, {"min_available_bytes": -1}, {"min_disk_free_bytes": 0}):
            with self.subTest(updates=updates), self.assertRaises(ValueError):
                guard.supervise(self.args(**updates))
        with patch.object(resource, "getrlimit", return_value=(guard.GIB, guard.GIB)):
            with self.assertRaisesRegex(ValueError, "existing hard"):
                guard.supervise(self.args())
        self.assertFalse((self.root / "guard").exists())

    def test_invalid_child_routing_and_implicit_separator_refuse(self):
        valid = self.args().allocation_args[1:]
        for invalid in ([], ["/bin/sh", "-c", "echo", "anything"], valid + ["--force", "true"],
                        valid + ["--out", str(self.root / "another")], valid[:-1],
                        ["--pool=" + str(self.inputs["pool"])] + valid[2:]):
            with self.subTest(arguments=invalid), self.assertRaises(ValueError):
                guard.child_routes(invalid)
        with self.assertRaisesRegex(ValueError, "separate"):
            guard.validate(self.args(allocation_args=valid))

    def test_default_registry_is_fixed_and_all_paths_are_absolute(self):
        args = self.args()
        index = args.allocation_args.index("--registry")
        del args.allocation_args[index:index + 2]
        _, routes, forwarded = guard.validate(args)
        self.assertEqual(routes["registry"], guard.HERE / "sources/catalogue-v2-sources.json")
        self.assertEqual(len(forwarded), 12)
        self.assertTrue(all(Path(value).is_absolute() for value in forwarded[1::2]))

    def test_new_disjoint_paths_and_input_aliases_refuse(self):
        for target in (self.root / "guard", self.root / "guard/nested", self.root / "scratch",
                       self.inputs["pool"], self.inputs["pool"] / "nested"):
            args = self.args()
            args.allocation_args[-1] = str(target)
            with self.subTest(target=target), self.assertRaises(ValueError):
                guard.validate(args)
        (self.root / "output").mkdir()
        with self.assertRaisesRegex(ValueError, "must be new"):
            guard.validate(self.args())
        (self.root / "output").rmdir()
        self.inputs["registry"].unlink()
        self.inputs["registry"].hardlink_to(self.inputs["pool"])
        with self.assertRaisesRegex(ValueError, "alias"):
            guard.validate(self.args())

    def test_symlink_destinations_and_missing_inputs_refuse(self):
        (self.root / "output").symlink_to(self.root / "missing")
        with self.assertRaisesRegex(ValueError, "must be new"):
            guard.validate(self.args())
        (self.root / "output").unlink()
        (self.root / "alias").symlink_to(self.root / "guard", target_is_directory=True)
        args = self.args()
        args.allocation_args[-1] = str(self.root / "alias/nested")
        with self.assertRaisesRegex(ValueError, "disjoint"):
            guard.validate(args)
        self.inputs["pool"].unlink()
        with self.assertRaisesRegex(ValueError, "existing regular files"):
            guard.validate(self.args())

    def test_cross_filesystem_output_refuses(self):
        parents = [SimpleNamespace(is_dir=lambda: True, stat=lambda device=device: SimpleNamespace(st_dev=device))
                   for device in (1, 1, 2)]
        with patch.object(guard, "existing_parent", side_effect=parents):
            with self.assertRaisesRegex(ValueError, "monitored filesystem"):
                guard.validate(self.args())
        self.assertFalse((self.root / "guard").exists())

    def test_child_wrapper_installs_parent_death_and_hard_limit_before_fixed_main(self):
        calls = []
        module = ModuleType("allocation_diagnostic")
        module.main = lambda argv: calls.append(("main", argv)) or 17
        child_args = self.args().allocation_args[1:]
        with patch.dict(sys.modules, {"allocation_diagnostic": module}), \
                patch.object(guard, "parent_death_guard", side_effect=lambda pid: calls.append(("parent", pid))), \
                patch.object(resource, "setrlimit", side_effect=lambda kind, limits: calls.append(("limit", kind, limits))):
            status = guard.main(["--child-address-space", str(5 * guard.GIB), "--parent-pid", "123", "--", *child_args])
        self.assertEqual(status, 17)
        self.assertEqual(calls, [("parent", 123), ("limit", resource.RLIMIT_AS, (5 * guard.GIB, 5 * guard.GIB)),
                                 ("main", child_args)])

    def test_bad_child_wrapper_never_imports_or_runs_diagnostic(self):
        module = ModuleType("allocation_diagnostic")
        with patch.dict(sys.modules, {"allocation_diagnostic": module}), \
                patch.object(guard, "parent_death_guard") as parent, patch.object(resource, "setrlimit") as limit:
            for argv in (["--child-address-space"], ["--child-address-space", "0", "--parent-pid", "123", "--"],
                         ["--child-address-space", "1", "--parent-pid", "123", "--", "/bin/sh", "-c"]):
                with self.assertRaises((ValueError, IndexError)):
                    guard.main(argv)
        parent.assert_not_called()
        limit.assert_not_called()

    def test_main_exit_code_reflects_child_result(self):
        for state, code in (("child-exited-zero", 0), ("child-failed", 1), ("resource-stop", 1), ("refused-preflight", 1)):
            with patch.object(guard, "supervise", return_value={"status": state, "exitCode": None, "stopReason": None}), \
                    redirect_stdout(io.StringIO()):
                self.assertEqual(guard.main(["--evidence-dir", "unused", "--"]), code)


if __name__ == "__main__":
    unittest.main()
