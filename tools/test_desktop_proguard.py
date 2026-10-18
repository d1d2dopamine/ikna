#!/usr/bin/env python3
"""Offline desktop shrinker regression; never downloads dependencies.

Always checks the warning boundary. With --java-home, --dependencies, --compose-plugin and
--proguard-classpath, compiles a library-only Java fixture, reproduces the old
warning failure, shrinks/runs it with the production rules, and checks that new
required missing references still fail. This is not an application/installer build.
"""
from pathlib import Path
import argparse
import os
import re
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RULES = ROOT / 'desktop/compose-desktop.pro'
OPTIONAL_OWNERS = {
    'okhttp3.internal.platform.' + name for name in (
        'Android10Platform', 'AndroidPlatform', 'BouncyCastlePlatform',
        'ConscryptPlatform', 'ConscryptPlatform$Companion',
        'ConscryptPlatform$DisabledHostnameVerifier', 'OpenJSSEPlatform',
        'android.Android10SocketAdapter', 'android.Android10SocketAdapter$Companion',
        'android.AndroidCertificateChainCleaner', 'android.AndroidCertificateChainCleaner$Companion',
        'android.AndroidLog', 'android.BouncyCastleSocketAdapter',
        'android.BouncyCastleSocketAdapter$Companion$factory$1',
        'android.ConscryptSocketAdapter', 'android.ConscryptSocketAdapter$Companion$factory$1',
    )
} | {
    'com.google.crypto.tink.util.KeysDownloader',
    'com.google.crypto.tink.util.KeysDownloader$Builder',
    'com.google.crypto.tink.hybrid.internal.XWingHpkeConscryptEncrypt',
    'com.google.crypto.tink.hybrid.internal.XWingHpkeConscryptDecrypt',
}
EXISTING_WARNINGS = {
    'okio.**', 'kotlinx.serialization.**', 'androidx.room.**', 'androidx.sqlite.**',
    'androidx.datastore.preferences.protobuf.**', 'sun.misc.**', 'androidx.datastore.**',
    'kotlinx.coroutines.**', 'com.github.luben.zstd.**',
}
# This isolated fixture follows the pinned production libraries, not ProGuard's
# own tool classpath. It does not include optional HTTP/native TLS dependencies.
DEPENDENCIES = (
    'tink-1.23.0.jar', 'okhttp-tls-4.12.0.jar', 'okhttp-4.12.0.jar',
    'okio-jvm-3.6.0.jar', 'kotlin-stdlib-2.2.20.jar', 'annotations-26.0.1.jar',
    'gson-2.13.2.jar', 'protobuf-java-4.33.6.jar', 'jsr305-3.0.2.jar',
    'error_prone_annotations-2.41.0.jar',
)
MISSING = re.compile(r"Warning: ([^:]+): can't find (?:referenced class|superclass or interface) (\S+)")


def active_lines(text):
    return [line.split('#', 1)[0].strip() for line in text.splitlines()
            if line.split('#', 1)[0].strip()]


class WarningBoundary(unittest.TestCase):
    def test_only_reviewed_owner_exceptions_are_added(self):
        lines = active_lines(RULES.read_text())
        warnings = {line.split(None, 1)[1] for line in lines if line.startswith('-dontwarn ')}
        self.assertEqual(EXISTING_WARNINGS | OPTIONAL_OWNERS, warnings)
        self.assertNotIn('-dontwarn', lines)
        self.assertFalse(any(line.startswith(('-ignorewarnings', '-dontshrink')) for line in lines))
        self.assertTrue(all('*' not in owner for owner in OPTIONAL_OWNERS))

    def test_fixture_tracks_the_pinned_dependencies(self):
        build = (ROOT / 'shared/build.gradle.kts').read_text()
        self.assertIn('com.squareup.okhttp3:okhttp-tls:4.12.0', build)
        self.assertIn('com.google.crypto.tink:tink:1.23.0', build)
        self.assertIn('compose-desktop.pro', (ROOT / 'desktop/build.gradle.kts').read_text())
        lan = (ROOT / 'shared/src/jvmShared/kotlin/dev/ikna/sync/LanSyncSecurity.kt').read_text()
        envelope = (ROOT / 'shared/src/jvmShared/kotlin/dev/ikna/sync/remote/EncryptedRemoteEnvelope.kt').read_text()
        self.assertIn('SSLContext.getInstance("TLS")', lan)
        self.assertIn('HeldCertificate', lan)
        self.assertIn('AeadConfig.register()', envelope)
        self.assertIn('it.typeUrl == KEY_TYPE', envelope)
        self.assertIn('google.crypto.tink.XChaCha20Poly1305Key', envelope)


def invoke(command, log, expected=0):
    result = subprocess.run([str(x) for x in command], stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, text=True, timeout=180)
    log.write_text(result.stdout)
    if (expected == 0 and result.returncode != 0) or (expected != 0 and result.returncode == 0):
        raise AssertionError(f'Unexpected exit {result.returncode}; see {log}\n{result.stdout[-5000:]}')
    return result.stdout


def write_jar(classes, destination, exclude=()):
    with zipfile.ZipFile(destination, 'w') as archive:
        for path in sorted(classes.rglob('*.class')):
            name = path.relative_to(classes).as_posix()
            if name not in exclude:
                archive.write(path, name)


def quoted(path):
    return '"' + str(path.resolve()).replace('\\', '/') + '"'


def integration(args, output):
    output.mkdir(parents=True, exist_ok=True)
    java = args.java_home / 'bin' / ('java.exe' if os.name == 'nt' else 'java')
    javac = args.java_home / 'bin' / ('javac.exe' if os.name == 'nt' else 'javac')
    dependencies = [args.dependencies / name for name in DEPENDENCIES]
    for path in [java, javac, *dependencies]:
        if not path.is_file():
            raise FileNotFoundError(path)
    modules = sorted((args.java_home / 'jmods').glob('*.jmod'))
    if not modules:
        raise ValueError('A full JDK with jmods is required')
    defaults = output / 'default-compose-desktop-rules.pro'
    with zipfile.ZipFile(args.compose_plugin) as plugin:
        defaults.write_bytes(plugin.read('default-compose-desktop-rules.pro'))
    classes = output / 'classes'
    classes.mkdir(exist_ok=True)
    fixture = ROOT / 'tools/proguard/ReleaseDependencySmoke.java'
    invoke([javac, '--release', '17', '-cp', os.pathsep.join(map(str, dependencies)),
            '-d', classes, fixture], output / 'compile.log')
    smoke = output / 'smoke.jar'
    write_jar(classes, smoke)
    invoke([java, '-cp', os.pathsep.join(map(str, [smoke, *dependencies])),
            'dev.ikna.buildcheck.ReleaseDependencySmoke'], output / 'unshrunk.log')

    baseline = output / 'baseline-rules.pro'
    baseline.write_text('\n'.join(line for line in RULES.read_text().splitlines()
                                  if line.strip() not in {'-dontwarn ' + owner for owner in OPTIONAL_OWNERS}) + '\n')

    def shrink(name, fixture_jar, rules, expected=0):
        destination = output / f'{name}.jar'
        config = output / f'{name}.pro'
        config.write_text('\n'.join([
            *['-injars ' + quoted(path) + '(!META-INF/versions/**,!module-info.class)'
              for path in [fixture_jar, *dependencies]],
            *['-libraryjars ' + quoted(path) + '(!**.jar;!module-info.class)' for path in modules],
            '-outjars ' + quoted(destination), '-include ' + quoted(defaults),
            '-include ' + quoted(rules),
            '-keep class dev.ikna.buildcheck.ReleaseDependencySmoke { public static void main(java.lang.String[]); }',
            '-keep class dev.ikna.buildcheck.MissingDependency { *; }',
            '-keep class okhttp3.internal.platform.RequiredNewPlatform { *; }',
            # ProguardSettings in Compose 1.8.2 defaults obfuscate to false.
            '-dontobfuscate',
        ]) + '\n')
        log = invoke([java, '-Xmx512m', '-cp', args.proguard_classpath,
                      'proguard.ProGuard', '@' + str(config)], output / f'{name}.log', expected)
        return destination, log

    _, before = shrink('baseline', smoke, baseline, expected=1)
    missing = MISSING.findall(before)
    if len(missing) != 136 or {owner for owner, _ in missing} != OPTIONAL_OWNERS:
        raise AssertionError('Baseline differs from the supplied 136-reference failure; inspect baseline.log')
    shrunk, after = shrink('fixed', smoke, RULES)
    if MISSING.search(after):
        raise AssertionError('Fixed shrink has unresolved classes')
    invoke([java, '-cp', shrunk, 'dev.ikna.buildcheck.ReleaseDependencySmoke'], output / 'shrunk.log')

    # Compile against a stub, then deliberately omit it from the shrink input.
    # Same missing type as the approved optional path: target-wide suppressions
    # would hide these errors. Check both a new app and a new library owner.
    for label, owner in [('app', 'dev.ikna.buildcheck.MissingDependency'),
                         ('new-platform', 'okhttp3.internal.platform.RequiredNewPlatform')]:
        folder = output / label
        folder.mkdir(exist_ok=True)
        stub = folder / 'HpkeSuite.java'
        stub.write_text('package org.conscrypt; public class HpkeSuite { public static void required() {} }\n')
        source = folder / (owner.rsplit('.', 1)[1] + '.java')
        package, name = owner.rsplit('.', 1)
        source.write_text(f'package {package}; public class {name} {{ public static void required() {{ org.conscrypt.HpkeSuite.required(); }} }}\n')
        compiled = folder / 'classes'
        compiled.mkdir(exist_ok=True)
        invoke([javac, '--release', '17', '-d', compiled, stub, source], folder / 'compile.log')
        negative = folder / 'negative.jar'
        write_jar(compiled, negative, exclude=('org/conscrypt/HpkeSuite.class',))
        _, failed = shrink(label, negative, RULES, expected=1)
        if (owner, 'org.conscrypt.HpkeSuite') not in MISSING.findall(failed):
            raise AssertionError('Required missing reference did not remain visible: ' + owner)
    print('PASS: baseline reproduces 136 refs; fixed shrink and crypto/TLS smoke; two required-reference failures')
    print('Evidence:', output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path)
    parser.add_argument('--dependencies', type=Path)
    parser.add_argument('--proguard-classpath')
    parser.add_argument('--compose-plugin', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(WarningBoundary))
    if not result.wasSuccessful():
        return 1
    supplied = [args.java_home, args.dependencies, args.proguard_classpath, args.compose_plugin]
    if any(supplied) and not all(supplied):
        parser.error('Supply --java-home, --dependencies, --proguard-classpath and --compose-plugin together')
    if all(supplied):
        if args.output:
            integration(args, args.output.resolve())
        else:
            with tempfile.TemporaryDirectory(prefix='ikna-proguard-') as folder:
                integration(args, Path(folder))
    else:
        print('Static boundary checks only; actual ProGuard/runtime verification not requested.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
