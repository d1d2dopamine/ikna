#!/usr/bin/env python3
"""Offline source and process-boundary fixtures; no Gradle, adb or emulator run."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


def check_contract(workflow, wrapper):
    for token in ('script: bash tools/ci/run-android-tests.sh', 'timeout-minutes: 40',
                  'api-level: 35', 'arch: x86_64', 'if: always()', 'ci-logs/',
                  'app/build/outputs/androidTest-results/'):
        if token not in workflow:
            raise ValueError(f'Missing runtime evidence gate: {token}')
    for token in ('timeout --signal=TERM --kill-after=30s 900s bash tools/ci/run-gradle.sh',
                  ':app:connectedDebugAndroidTest', '-Pikna.abi=emulator || code=$?',
                  'logcat -v threadtime', 'ulimit -f 32768', 'android-logcat-status.txt', 'trap cleanup EXIT', 'exit "$code"'):
        if token not in wrapper:
            raise ValueError(f'Missing timeout/log/status contract: {token}')
    if 'continue-on-error' in workflow or 'instrumentationRunnerArguments.class' in wrapper:
        raise ValueError('Do not ignore failures or silently narrow the existing all-class suite')


class AndroidRuntimeCiTest(unittest.TestCase):
    def setUp(self):
        self.workflow = (ROOT / '.github/workflows/grading.yml').read_text()
        self.wrapper = (ROOT / 'tools/ci/run-android-tests.sh').read_text()

    def test_live_contract(self):
        check_contract(self.workflow, self.wrapper)

    def test_gate_mutations_are_rejected(self):
        for before, after in [('900s', '0s'), ('--kill-after=30s', ''),
                              ('|| code=$?', '|| true'), ('exit "$code"', 'exit 0'),
                              ('logcat -v threadtime', 'echo no-logcat')]:
            with self.subTest(before=before), self.assertRaises(ValueError):
                check_contract(self.workflow, self.wrapper.replace(before, after))
        with self.assertRaises(ValueError):
            check_contract(self.workflow.replace('if: always()', 'if: success()'), self.wrapper)

    @unittest.skipUnless(shutil.which('bash') and shutil.which('timeout'), 'Requires bash and GNU timeout')
    def test_real_timeout_and_exit_status_with_stub_gradle_and_adb(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / 'tools/ci').mkdir(parents=True)
            # Shorten the deadline only in the isolated test copy. Production
            # remains the literal 900-second external process bound above.
            (root / 'tools/ci/run-android-tests.sh').write_text(self.wrapper.replace('900s', '1s'))
            shutil.copyfile(ROOT / 'tools/ci/run-gradle.sh', root / 'tools/ci/run-gradle.sh')
            bin_dir = root / 'bin'
            bin_dir.mkdir()
            for name, text in {
                'gradle': '#!/bin/bash\necho fixture-gradle-start\nif [ "$CASE" = hang ]; then exec sleep 20; fi\nexit "$STATUS"\n',
                'adb': '#!/bin/bash\nif [ "$CASE" = no-adb ]; then echo unavailable; exit 9; fi\necho fixture-logcat\nexec sleep 20\n',
            }.items():
                path = bin_dir / name
                path.write_text(text)
                path.chmod(0o700)
            for case, status, expected in [('pass', 0, 0), ('failure', 7, 7), ('hang', 0, 124), ('no-adb', 7, 7)]:
                env = dict(os.environ, PATH=f"{bin_dir}{os.pathsep}{os.environ['PATH']}", CASE=case, STATUS=str(status))
                result = subprocess.run(['bash', 'tools/ci/run-android-tests.sh'], cwd=root,
                                        env=env, capture_output=True, text=True, timeout=8)
                self.assertEqual(result.returncode, expected, result.stderr)
                self.assertIn('fixture-gradle-start', (root / 'ci-logs/android-connected-tests.log').read_text())
                self.assertIn(str(expected), (root / 'ci-logs/android-exit-status.txt').read_text())
                self.assertTrue((root / 'ci-logs/android-logcat.log').exists())
                self.assertTrue((root / 'ci-logs/android-logcat-status.txt').exists())


if __name__ == '__main__':
    unittest.main()
