#!/usr/bin/env python3
"""Early DEX-name and CI regression checks; standard library only.

Quoted Kotlin method/field names can compile on the JVM but fail D8 at minSdk 29.
Use conservative ASCII JVM identifiers in Android-bound sources. This lexical
style check is not a Kotlin compiler: assembleDebugAndroidTest is the real gate.
Pure src/test and desktop-only sources are intentionally outside this check.
"""
from pathlib import Path
import os
import re
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SAFE_NAME = re.compile(r"[A-Za-z_$][A-Za-z0-9_$]*\Z")
ANDROID_SETS = {"main", "androidMain", "commonMain", "jvmShared", "androidTest", "androidInstrumentedTest"}


def quoted_identifiers(source):
    """Yield code identifiers, ignoring comments and string/character literals."""
    i = 0
    while i < len(source):
        if source.startswith("//", i):
            end = source.find("\n", i + 2)
            i = len(source) if end < 0 else end + 1
        elif source.startswith("/*", i):
            depth = 1
            i += 2
            while i < len(source) and depth:
                if source.startswith("/*", i):
                    depth += 1
                    i += 2
                elif source.startswith("*/", i):
                    depth -= 1
                    i += 2
                else:
                    i += 1
        elif source.startswith('"""', i):
            end = source.find('"""', i + 3)
            i = len(source) if end < 0 else end + 3
        elif source[i] in ('"', "'"):
            quote = source[i]
            i += 1
            while i < len(source):
                if source[i] == "\\":
                    i += 2
                elif source[i] == quote:
                    i += 1
                    break
                else:
                    i += 1
        elif source[i] == "`":
            end = source.find("`", i + 1)
            if end < 0:
                yield source.count("\n", 0, i) + 1, "<unterminated identifier>"
                break
            yield source.count("\n", 0, i) + 1, source[i + 1:end]
            i = end + 1
        else:
            i += 1


def unsafe_identifiers(source):
    return [(line, name) for line, name in quoted_identifiers(source) if not SAFE_NAME.fullmatch(name)]


def android_sources(root=ROOT):
    result = []
    for module in ("app", "shared"):
        for source_set in sorted(ANDROID_SETS):
            result.extend((root / module / "src" / source_set).rglob("*.kt"))
    return sorted(result)


def check_repository():
    files = android_sources()
    if not files:
        print("No Android Kotlin sources found; check repository layout.", file=sys.stderr)
        return 1
    failures = []
    for path in files:
        for line, name in unsafe_identifiers(path.read_text(encoding="utf-8")):
            failures.append(f"{path.relative_to(ROOT)}:{line}: use a DEX-safe ASCII identifier instead of {name!r}")
    if failures:
        print("\n".join(failures), file=sys.stderr)
        return 1
    print(f"DEX-safe Kotlin identifier check passed: {len(files)} Android-bound files; JVM-only tests excluded.")
    return 0


class AndroidCiChecks(unittest.TestCase):
    def test_rejects_original_error_and_other_unsafe_quoted_identifiers(self):
        for name in ("an upgrade from version 1 keeps the answers and gains the new tables", "governor's inputs", "with-hyphen", "two\twords", ""):
            self.assertEqual([(1, name)], unsafe_identifiers("@Test fun `" + name + "`() {}"))
        self.assertTrue(unsafe_identifiers("val `bad field` = 1"))
        self.assertTrue(unsafe_identifiers("fun `unterminated"))

    def test_accepts_normal_methods_and_quoted_keywords(self):
        self.assertEqual([], unsafe_identifiers("fun upgradeFromVersion1() {}\nfun `when`() {}\nval `valid_name` = 0"))

    def test_ignores_comments_raw_strings_and_escaped_literals(self):
        text = '\n'.join([
            '// fun `not a method`() {}',
            '/* outer /* nested `not a field` */ `also ignored` */',
            'val prose = "escaped \\" `not a name`"',
            'val example = """fun `example only`() {}"""',
            "val quote = '\\''",
            'fun actualMethod() {}',
        ])
        self.assertEqual([], unsafe_identifiers(text))

    def test_reports_source_line_after_comments(self):
        self.assertEqual([(3, "two words")], unsafe_identifiers("// one\n/* two */\nfun `two words`() {}"))

    def test_android_scope_does_not_reject_legal_jvm_test_names(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            included = ["app/src/androidTest/kotlin/Test.kt", "app/src/main/kotlin/Main.kt", "shared/src/jvmShared/kotlin/Core.kt", "shared/src/androidMain/kotlin/Platform.kt"]
            excluded = ["app/src/test/kotlin/Test.kt", "desktop/src/main/kotlin/Main.kt", "desktop/src/test/kotlin/Test.kt"]
            for name in included + excluded:
                p = root / name
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text("fun method() {}")
            self.assertEqual(set(included), {p.relative_to(root).as_posix() for p in android_sources(root)})

    def test_project_names_are_safe_and_all_device_tests_remain_enabled(self):
        files = android_sources()
        self.assertTrue(files)
        for path in files:
            self.assertEqual([], unsafe_identifiers(path.read_text()), str(path))
        source = (ROOT / "app/src/androidTest/java/dev/ikna/data/db/MigrationTest.kt").read_text()
        self.assertEqual(8, len(re.findall(r"@Test\b", source)))
        self.assertNotIn("@Ignore", source)
        self.assertIn("fun version7PreservesGradingHistoryAndAddsParameterSnapshots()", source)

    def test_ci_builds_test_apk_before_emulator_and_retains_failure_logs(self):
        source = (ROOT / ".github/workflows/grading.yml").read_text()
        self.assertLess(source.index(":app:assembleDebugAndroidTest"), source.index("reactivecircus/android-emulator-runner@v2"))
        for token in ("check_android_ci.py --self-test", "check_android_ci.py", "cache-read-only: true", "ci-logs/", "app/build/outputs/androidTest-results/", ":app:connectedDebugAndroidTest", "if: always()"):
            self.assertIn(token, source)
        self.assertNotIn("continue-on-error:", source)
        self.assertNotIn("if-no-files-found: ignore", source)

    def test_logging_wrapper_preserves_gradle_exit_status_and_arguments(self):
        # Fake executable verifies shell plumbing, NOT a real Gradle build.
        wrapper = ROOT / "tools/ci/run-gradle.sh"
        with tempfile.TemporaryDirectory() as folder:
            directory = Path(folder)
            fake = directory / "gradle"
            fake.write_text('#!/bin/sh\nprintf "%s\\n" "$*"\necho diagnostic-output\necho diagnostic-error >&2\nexit "$FAKE_GRADLE_EXIT"\n')
            fake.chmod(0o700)
            for code in (0, 37):
                log = directory / "logs with spaces" / f"build-{code}.log"
                env = dict(os.environ, PATH=str(directory) + os.pathsep + os.environ.get("PATH", ""), FAKE_GRADLE_EXIT=str(code))
                result = subprocess.run(["bash", str(wrapper), str(log), ":app:assembleDebugAndroidTest", "-Pikna.abi=emulator"], env=env, capture_output=True, text=True, timeout=10)
                self.assertEqual(code, result.returncode, result.stdout + result.stderr)
                saved = log.read_text()
                for token in ("diagnostic-output", "diagnostic-error", "--stacktrace", ":app:assembleDebugAndroidTest", "-Pikna.abi=emulator"):
                    self.assertIn(token, saved)

    def test_no_version_bump_or_min_sdk_workaround_and_updated_actions(self):
        build = (ROOT / "app/build.gradle.kts").read_text()
        for token in ('val appVersionName = "0.10.0 press"', "val appVersionCode = 200100000", "minSdk = 29"):
            self.assertIn(token, build)
        grading = (ROOT / ".github/workflows/grading.yml").read_text()
        self.assertIn('gradle-version: "8.10.2"', grading)
        self.assertIn("gradle/actions/setup-gradle@v5", grading)
        self.assertIn("actions/upload-artifact@v6", grading)
        for path in (ROOT / ".github/workflows").glob("*.yml"):
            text = path.read_text()
            self.assertNotIn("gradle/actions/setup-gradle@v4", text)
            self.assertNotIn("actions/upload-artifact@v4", text)


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        unittest.main(argv=[sys.argv[0]], verbosity=2)
    elif len(sys.argv) == 1:
        sys.exit(check_repository())
    else:
        sys.exit("Usage: python3 tools/check_android_ci.py [--self-test]")
