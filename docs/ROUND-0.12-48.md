# Round 48: Android build validation after SDK authorization

2026-10-12. Cumulative on Round 47. The owner authorized installation and
acceptance of the Android SDK in the cloud build environment. The SDK agreement
was accepted through sdkmanager: https://developer.android.com/studio/terms .
No user computer is involved.

## Reproduced build defect and fix

Both owner-provided CI logs show the same module-visibility error: debug/release
compilation and the instrumentation-APK job fail at JsonExporter.kt:87. Exact
original logs and SHA-256 provenance are preserved beside the local reproduction.

The first local Android build passed 69 shared Android JVM tests, then failed at
`:app:compileReleaseKotlin`: Android JsonExporter calls writeExportDocument from
`:shared`, but that function was internal to the shared module. Shared-module
unit tests could call it, so their success did not validate this boundary.

The fix makes that existing helper public. Serialization, storage-error handling
and pre-wipe behavior are unchanged. An app-module regression calls the shared
boundary and checks successful writing/closing and an unavailable output stream.
Actual Android compilation is the primary regression check for module visibility.

The first post-fix app run exposed one stale source-contract assertion among 565
unit tests: it required the old fixed 34dp deck information row. The already
approved accessibility code uses a minimum 48dp information row, growing text
layout and a preserved 52dp mark. The test now enforces those existing constraints
and rejects a return to fixed-height clipping; production deck UI is unchanged.
The failure log/XML are retained rather than hidden or counted as a clean run.

## Reproducible prerequisites

- Existing JDK17 and Gradle8.10.2; Android platform35 revision2;
  Build Tools34.0.0 for this project's AGP8.6.1.
- Official command-line tools22.0, archive build15859902, SHA-256 checked against
  Google's published checksum. Platform-tools37.0.1 are installed.
- Build Tools35.0.0 are also installed for the separately validated Vespian build;
  this does not upgrade Ikna's project configuration.
- Existing Sherpa1.10.46 AAR downloaded from its official release, ZIP integrity
  checked and local SHA-256 recorded. The repository has no upstream checksum
  pin for that dependency; do not describe a local digest as an upstream match.
- Existing starter asset fetched in strict verification mode. Its pinned size,
  SHA-256 and shape pass. No pin, content, catalogue tool or publication changes.
- Official emulator37.2.12 and API35 AOSP x86_64 image revision2 installed for a
  feasibility check. Hardware acceleration is unavailable: no /dev/kvm and the
  emulator's acceleration check reports missing vmx/svm.

See `evidence/android-round48/` for provenance and actual test/build outcomes.
SDKs, downloaded AARs, corpus payloads, generated APKs, caches and machine-local
configuration are not added to the source ZIP.

## Signing and validation limits

Validation uses `-Pikna.unsigned=true` rather than the tracked release key.
Release APKs from that path are unsigned; debug APKs use the ordinary debug
identity, not the established update identity. They are not presented as a safe
update over an installed learner app. The tracked signing file is unchanged.

Building an instrumentation APK does not mean its tests executed. Real-device
voice, downloads, workers, accessibility and font/window acceptance remain
separate from compilation and JVM tests. Windows resize/minimize behavior is
unaffected by this Android stage.

## Completed verification checkpoint

Both Android debug and release Kotlin compilation pass. **565 app release JVM
tests and 69 shared Android JVM tests pass**, zero failures/errors/skips.
The x86_64 debug application APK and its instrumentation APK assemble successfully.
The two supplied CI failure paths are therefore covered by a local successful
repeat. Independent source review found no behavior change in the helper.

At this checkpoint, release R8/lint and instrumentation execution remain pending;
no Android-device test success is inferred from test-APK assembly.
