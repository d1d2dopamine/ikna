# 0.12 Hot Reload round 04 — 2026-10-06

Owner-selected tooling unblock before the integrated visual smoke and return to
Catalogue v2. Baseline: `ikna-0.12-press-round-03-merged.zip`, 552 files, SHA-256
`0c80a2d744ade070a847a1237d4ed0560a18c419f0566468842205a017288edb`.
Preserve both reviewed round-03 lanes and all earlier accepted source work.
No new UI, Catalogue experiment, build/workflow dispatch or toolchain upgrade.

## Diagnosis and evidence

The owner confirms that saved changes sometimes left the running window unchanged.
GLM's round-03 report describes an open app but no automatic rebuild; the cited
PC logs were not attached. This round establishes defects in the supplied source,
not a complete reconstruction of every past PC session.

The launcher ran external Gradle 8.10.2 directly, so it could open the app despite
the absence of a repository wrapper. **The child recompiler uses another entry
point.** The exact pinned Compose Hot Reload `v1.1.1` source, commit
`c7a2bd6c9529f3c5cda9d15ee9ab60cc51cb5b56`, shows:

- [GradleRecompiler.kt](https://github.com/JetBrains/compose-hot-reload/blob/v1.1.1/hot-reload-devtools/src/main/kotlin/org/jetbrains/compose/devtools/gradle/GradleRecompiler.kt),
  lines 86–109: run `cmd /c gradlew.bat` in `buildRoot`, pass the reload task and
  `-t` for auto mode. The 552-file input has no `gradlew.bat`. A separate external
  initial launch does not supply that entry point to the child.
- The same file, lines 162–170: blocking Gradle launch selects a child invocation
  with `--no-daemon`. The [Gradle 8.10.2 continuous-build manual](https://docs.gradle.org/8.10.2/userguide/continuous_builds.html)
  states that continuous builds require file watching and do not work with
  `--no-daemon`. Enabling the daemon only on the parent does not override that
  child argument. The bridge therefore appends `--daemon --watch-fs`.
- [Gradle's 8.10.2 command-line parser](https://github.com/gradle/gradle/blob/v8.10.2/platforms/core-runtime/cli/src/main/java/org/gradle/cli/CommandLineParser.java)
  documents last-option selection for mutually exclusive options; its completion
  path removes previous group members. The bridge preserves all plugin task,
  orchestration, Java-home and continuous arguments before those final flags.
- [properties.yaml](https://github.com/JetBrains/compose-hot-reload/blob/v1.1.1/properties.yaml)
  defaults `compose.reload.logStdout` to false. [Agent logging](https://github.com/JetBrains/compose-hot-reload/blob/v1.1.1/hot-reload-agent/src/main/kotlin/org/jetbrains/compose/reload/agent/logging.kt),
  lines 96–114, prints incoming logs to stdout only when it is enabled. Tee'ing
  the initial Gradle process did not by itself expose child/reload diagnostics.
  The supported Gradle properties now enable stdout and Debug-level events.

The pinned source tree and the listed files were fetched read-only from official
GitHub/raw endpoints; no checkout, dependency or tool installation was performed.
SHA-256 of the inspected upstream bytes:

| Source | SHA-256 |
| --- | --- |
| `v1.1.1/GradleRecompiler.kt` | `bd37d4dfa28c7e55a2f0ec3f6d5c9a138b69be2551feb97b50769deda3696d53` |
| `v1.1.1/agent/logging.kt` | `c4124584b34d25d1faaf4dde3ca7a0d9fd4f58efc944bf7eb80cf510aaf5a515` |
| `v1.1.1/properties.yaml` | `3fc99de05f697484a1411cff15f61e1005a71482a1163b66425f7983f6c297ec` |
| `v8.10.2/CommandLineParser.java` | `149b8f5007ecaf049655c4119662cea23a151292d9e2bd4490bc13f08332bf00` |

## Repairs

- `gradlew.bat` bridges the recompiler's required root command to the supervisor's
  existing checked distribution via inherited `IKNA_HOT_RELOAD_GRADLE_EXE`.
  Missing setup/path fails explicitly with exit 2; Gradle's exit code is retained.
  No wrapper jar or independent Gradle download is added. Ordinary CI/build
  commands and global `gradle.properties` are unchanged.
- The supervisor sets that environment variable after Gradle preparation and
  includes the bridge in its source-tree readiness check. The initial run also
  enables daemon/file watching. Existing isolated profile/mode retention stays.
- `tools/hot-reload-process.ps1` merges native stderr inside `cmd.exe` before
  PowerShell's stop policy, appends both output streams to the session log, and
  returns the native exit code. Compiler stderr must not abort the supervisor as
  a PowerShell ErrorRecord. Quoted executable paths remain supported.
- Logs enable supported Hot Reload stdout/Debug properties and retain session
  start/root/distribution/bridge/profile and top-level exit information. An open
  app or a successful source check is explicitly insufficient reload evidence.
- CONTRIBUTING now requires minimal change records and records acquired Catalogue
  evidence during work: input hashes, run/artifact references, parameters, stage
  counts, stop/failure reasons, reusable data, decisions and open gates.

The supervisor does not synthesize an additional polling/rebuild loop, run
manual reload tasks concurrently with auto mode or claim to restart a failed
child independently. Source/build-script replacement limits are explicit in the
runbook. Unsupported changes still need a restart.

## Verification and acceptance

Local source checks: `python tools/check_hot_reload.py`, `python tools/check_text.py`,
`python tools/check_localization.py`, `python tools/check_design_parity.py`,
`python tools/check_palettes.py` and `python tools/check_android_ci.py` exit 0.
These establish source consistency only; unchanged UI is retained from round 03.

`python tools/test_hot_reload.py` discovers five meaningful Windows native-process
fixtures: missing setup, missing Gradle, spaced/non-ASCII executable paths and
argument preservation, nonzero exit propagation, and stdout/stderr logging under
PowerShell's stop policy. **All five are skipped on this Linux host.** They do
not validate the actual Gradle watcher, and no native test is reported as passed.
PowerShell, a usable JDK, Gradle and a desktop runtime are unavailable here.
No tooling was installed and no full build or corpus run was requested.

Before delivery, review scoped byte differences against the 552-file baseline,
relative documentation links, `git diff --check` and full source ZIP manifest,
bytes and integrity. Final package: 556 files, no deletions; explicit additions
are `gradlew.bat`, `tools/hot-reload-process.ps1`, `tools/test_hot_reload.py` and
this report. Both skills, signing/schema/binary assets and build versions remain.

Windows acceptance remains open: restart the old session once, follow
[HOT-RELOAD.md](HOT-RELOAD.md)'s visible-label/save/restore probe and compiler-error
recovery check, and retain edited path/save/update times plus the actual log.
Then check the integrated visual matrix in [ROUND-0.12-03.md](ROUND-0.12-03.md).
Do not close the original lighting-switch defect solely on static checks.
After that checkpoint, return to existing World merge evidence and reusable
admitted-source pool inputs; do not rerun known long workflows speculatively.
