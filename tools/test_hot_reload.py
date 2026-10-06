#!/usr/bin/env python3
"""Windows native-launch regression fixtures; no Gradle, Java or downloads.

Runs the actual bridge/output helper against a temporary fake Gradle executable.
Non-Windows hosts explicitly skip these runtime checks.
"""
from __future__ import annotations

import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


@unittest.skipUnless(os.name == "nt", "requires Windows cmd.exe; no runtime proof on this host")
class NativeHotReloadTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="ikna hot reload ")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "project with spaces"
        self.root.mkdir()
        self.bridge = self.root / "gradlew.bat"
        shutil.copyfile(ROOT / "gradlew.bat", self.bridge)
        # A non-ASCII, spaced external distribution path must survive both layers.
        self.gradle = Path(self.temp.name) / "tools тест with spaces" / "gradle.bat"
        self.gradle.parent.mkdir()
        self.gradle.write_text(
            "@echo off\n"
            "echo STDOUT-marker\n"
            "echo ARGS=%*\n"
            "echo STDERR-marker 1>&2\n"
            "exit /b %IKNA_TEST_EXIT%\n", encoding="ascii",
        )
        self.env = os.environ.copy()
        self.env["IKNA_HOT_RELOAD_GRADLE_EXE"] = str(self.gradle)
        self.env["IKNA_TEST_EXIT"] = "0"
        self.cmd = self.env.get("ComSpec", "cmd.exe")

    def run_bridge(self, args: str = ":desktop:reload") -> subprocess.CompletedProcess:
        # Pass cmd.exe its own quote syntax, without list2cmdline re-escaping it.
        command = f'"{self.cmd}" /d /s /c ""{self.bridge}" {args}"'
        return subprocess.run(command, cwd=self.root, env=self.env,
                              capture_output=True, timeout=20)

    def test_missing_bridge_environment_fails_without_launching_gradle(self) -> None:
        del self.env["IKNA_HOT_RELOAD_GRADLE_EXE"]
        result = self.run_bridge()
        self.assertEqual(result.returncode, 2)
        self.assertIn(b"start dev-hot-reload.cmd first", result.stderr)
        self.assertNotIn(b"STDOUT-marker", result.stdout)

    def test_missing_gradle_fails_without_launching(self) -> None:
        self.env["IKNA_HOT_RELOAD_GRADLE_EXE"] = str(self.root / "missing.bat")
        result = self.run_bridge()
        self.assertEqual(result.returncode, 2)
        self.assertIn(b"does not exist", result.stderr)

    def test_recompiler_arguments_preserved_and_watch_flags_last(self) -> None:
        result = self.run_bridge(':desktop:reload -t "-Dprobe=C:\\a path" --no-daemon')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(b':desktop:reload -t "-Dprobe=C:\\a path" --no-daemon --daemon --watch-fs', result.stdout)
        self.assertIn(b"STDERR-marker", result.stderr)

    def test_gradle_failure_exit_code_preserved(self) -> None:
        self.env["IKNA_TEST_EXIT"] = "37"
        result = self.run_bridge()
        self.assertEqual(result.returncode, 37)
        self.assertIn(b"STDOUT-marker", result.stdout)
        self.assertIn(b"STDERR-marker", result.stderr)

    def test_powershell_stop_policy_keeps_both_streams_and_failure_code(self) -> None:
        powershell = shutil.which("powershell.exe") or shutil.which("pwsh")
        if powershell is None:
            self.skipTest("Windows PowerShell/PowerShell is unavailable")
        log = self.root / "session with spaces.log"
        self.env["IKNA_TEST_EXIT"] = "37"
        self.env["IKNA_TEST_HELPER"] = str(ROOT / "tools/hot-reload-process.ps1")
        self.env["IKNA_TEST_LOG"] = str(log)
        script = (
            "$ErrorActionPreference = 'Stop'; "
            ". $env:IKNA_TEST_HELPER; "
            "Set-Content -Path $env:IKNA_TEST_LOG -Value 'HEADER-marker'; "
            "$code = Invoke-IknaHotGradle -GradleExe $env:IKNA_HOT_RELOAD_GRADLE_EXE "
            "-Log $env:IKNA_TEST_LOG; exit $code"
        )
        result = subprocess.run([powershell, "-NoProfile", "-ExecutionPolicy", "Bypass",
                                 "-Command", script], cwd=self.root, env=self.env,
                                capture_output=True, timeout=20)
        self.assertEqual(result.returncode, 37, result.stderr)
        data = log.read_bytes()
        contents = data.decode("utf-16" if data.startswith((b"\xff\xfe", b"\xfe\xff")) else "utf-8-sig")
        for marker in ("HEADER-marker", "STDOUT-marker", "STDERR-marker", ":desktop:hotRun --auto"):
            self.assertIn(marker, contents)
        self.assertIn("-Pcompose.reload.logStdout=true", contents)
        self.assertIn("-Pcompose.reload.logLevel=Debug", contents)


if __name__ == "__main__":
    unittest.main(verbosity=2)
