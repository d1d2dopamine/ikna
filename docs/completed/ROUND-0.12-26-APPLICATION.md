# Round 26 — application build and Developer Sandbox evidence

2026-10-12, Asia/Yekaterinburg (executed 2026-10-11 UTC). Application-only
verification of the supplied 0.12 working tree. No application implementation,
visual design, learning policy, dependency version, Room schema, signing asset or
release version changed. Catalogue profiling is recorded separately in round 25.

## Results

- **Shared JVM: 14/14 passed**, including 3 DeveloperBootstrap cases, 8 Signal
  Frame hover cases, 2 palette-preview cases and the reading-cat assertion.
- **Desktop JVM: 123/123 passed**, including all 9 real-Room Developer Sandbox
  integration cases and 3 restart-command cases. Both suites have zero failures,
  errors or skipped tests. Kotlin/Compose and Room-generated desktop code compile.
- **Linux release application image built successfully** through
  `:desktop:createReleaseDistributable`, including ProGuard and jlink/jpackage.
  The image contains 176 files, 168,423,683 logical bytes. It is an application
  directory, not the separately wrapped AppImage or a Windows installer.
- The **actual packaged launcher** passes its existing `--selftest` in a new,
  isolated directory: bundled deck installation, native SQLite/Room, a daily
  session, DataStore/protobuf preference writes and `sun.misc.Unsafe` loading.
- An additional **11-process packaged startup harness passes**: REAL startup;
  all six existing DEV scenarios; Empty reopened without another request; an
  invalid request rejected and retained; correction to Empty; return to REAL.
  Empty has zero packs/chunks/cards/reviews. Every DEV run leaves the REAL
  database and settings byte-identical. A corrupt pending request leaves DEV
  inventory unchanged and returns failure rather than silently selecting Empty.
  Successful requests are consumed. This exercises real packaged startup,
  not the UI confirmation dialogs or platform restart handoff.

The exact suite counters, three focused JUnit XML reports, successful build logs,
toolchain/package hashes and harness are in
[`evidence/application-round26/`](evidence/application-round26/).
The packaged harness is an evidence aid, not a new application feature or a
replacement for the normal Gradle tests. It refuses a pre-existing sandbox.

## Reproduction and environment

The starting environment had only an OpenJDK 21 **runtime**, no JDK compiler,
Gradle distribution or Android SDK. Official Gradle 8.10.2 and Temurin JDK
17.0.20.1+1 were downloaded into a separate toolchain directory and checked
against the publishers' SHA-256 values before use:

- Gradle: `31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26`
- JDK: `3808d1d15e3ec6bd5b84057fb5d84c33d8a1536a258146bcea2e603fc726e08e`

[`toolchain.json`](evidence/application-round26/toolchain.json) records official
source URLs and the exact verified identities. No system installation/settings
change was made. Set `JAVA_HOME` to the extracted JDK, `GRADLE_USER_HOME` to a
separate writable cache, and `ANDROID_USER_HOME` / `XDG_CONFIG_HOME` to separate
writable tool directories. If a proxy is required, read its host/port from the
current execution's `HTTPS_PROXY`; do not reuse another execution's port. The
existing system Java trust store was selected, without modifying trust anchors.

Executed from the repository root, with the pinned Gradle on PATH:

```bash
bash tools/catalog/fetch-bundled-pack.sh
gradle --no-daemon --max-workers=1 --no-parallel \
  '-Dorg.gradle.jvmargs=-Xmx768m -Dfile.encoding=UTF-8 -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts' \
  -Dkotlin.daemon.jvm.options=-Xmx512m \
  :shared:desktopTest :desktop:test --console=plain --stacktrace
gradle --no-daemon --max-workers=1 --no-parallel \
  '-Dorg.gradle.jvmargs=-Xmx768m -Dfile.encoding=UTF-8 -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts' \
  -Dkotlin.daemon.jvm.options=-Xmx512m \
  :desktop:createReleaseDistributable --console=plain --stacktrace
IKNA_SELFTEST_HOME=<new-disposable-directory> \
  desktop/build/compose/binaries/main-release/app/Ikna/bin/Ikna --selftest
python3 docs/evidence/application-round26/check_packaged_profiles.py \
  desktop/build/compose/binaries/main-release/app/Ikna/bin/Ikna \
  <another-new-disposable-directory> <output-json>
```

One Gradle session ran at a time. Available host memory remained above 5 GiB
during the observed build checks. Successful test/build durations were 3m18s and
1m22s respectively, including their remaining first-use work; these are not
benchmark comparisons. The first attempts failed because an execution-local
proxy port was reused, then because Android tooling attempted a read-only home.
Both environment problems were corrected before the reported successful run.

The starter-deck fetch used the existing verify-only pin, without updating it:
1,196,978 bytes, SHA-256
`70a84018057208aae631ce00f16b4c1d7be009660a9b75fee39dfa167530f8f7`,
1,435 cards / 1,424 transcriptions. This ignored build prerequisite is not new
Catalogue material and is excluded from the source-delivery diff.

Kotlin reports existing expect/actual beta, clipboard/painter deprecation and
pointer-parameter-name warnings. ProGuard reports duplicate-resource/class and
reflection/descriptor notes. They did not fail the build; successful packaging
alone does not prove every reflective or graphical path works.

## Other checks

- Existing JDK17 standalone desktop checks: 6 bounds/restore/pacing groups,
  6 titlebar/platform groups and 7 layout/trace groups passed. These model
  operations; they do not measure a real Windows/GPU frame.
- Design contracts: 42 passed. Classic DAO SQLite fixtures: 5 passed.
- Palettes: 9 passed; all 24 preview combinations agree.
- Localization: 7 languages, 735 keys each. UTF-8/NFC checker passed.
- Android CI checker and all 10 self-test cases passed; Hot Reload source wiring
  passed. All 6 Windows Hot Reload executable tests were **skipped** on Linux.
- SHA-256 comparison before/after build confirms every pre-existing file under
  app/shared/desktop and the root build configuration is unchanged, including
  committed schema bytes. No git metadata was supplied, so no git diff result
  is claimed. Toolchain/caches/generated outputs remain outside source delivery.

## Open acceptance and the next blocker

Android SDK packages and license acceptance are absent. The bounded offline
attempt `gradle --offline --no-daemon --max-workers=1 --no-parallel
-Dorg.gradle.jvmargs=-Xmx768m -Dkotlin.daemon.jvm.options=-Xmx512m
:shared:testDebugUnitTest :app:testReleaseUnitTest :app:assembleDebug` failed
before executing tests, with `SDK location not found` while resolving shared
Android test dependencies. The retained blocker log records the actual failure;
Android JVM/APK checks remain open. The current compileSdk is 35; AGP 8.6 uses Build Tools 34.0.0 by
default ([official compatibility table](https://developer.android.com/build/releases/agp-8-6-0-release-notes)).
The official [SDK setup guide](https://developer.android.com/tools/sdkmanager#accept-licenses)
requires accepting the relevant package licenses. Installing/using the SDK is
subject to the [Android SDK License Agreement](https://developer.android.com/studio/terms).
No SDK license was accepted, SDK installed or license marker fabricated in this
round. Next: owner-approved isolated SDK setup, or an already licensed build
runner, then shared Android JVM tests, app JVM tests/APK and actual migration
instrumentation. The Android speech runtime prerequisite remains unfetched.

This Linux execution has no DISPLAY/WAYLAND display and no Xvfb. Consequently
no graphical smoke pass, native profile restart handoff, visual cat/palette
acceptance, keyboard/focus/large-font Browse check, multi-monitor/DPI test,
Windows package or Android runtime result is claimed. The packaged headless
checks narrow the gap but do not close these acceptance items. Existing visual
appearance is preserved; no speculative repair was justified by this baseline.
