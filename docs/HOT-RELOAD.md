# Desktop Hot Reload

This is the fast UI-development path for Ikna. It runs the real JVM desktop
application with JetBrains Compose Hot Reload so edits in shared Compose UI can
be recompiled into the already-running window instead of producing a release
installer/APK for every visual check.

It is development tooling only. It does not replace Android/Desktop CI, release
packaging, migration tests or final smoke testing.

## Windows: normal use

1. Keep the ordinary Git/GitHub Desktop repository folder. No second checkout is
   required.
2. Double-click `dev-hot-reload.cmd` in the repository root.
3. Leave the terminal window open while testing.
4. Edit/save source files, or replace the repository contents with a new complete
   source ZIP as usual. A successful change is rebuilt and reloaded into the
   running desktop window automatically.
5. Stop the session with `Ctrl+C` or by closing the terminal.

No commit is required for a reload. Git can stay dirty until the tested version
is ready to commit.

The first launch is intentionally slower: the launcher downloads the repository's
pinned Gradle 8.10.2 into `%LOCALAPPDATA%\Ikna\dev-hot-reload\tools` if it is
not already there, Gradle resolves dependencies, and Compose Hot Reload may
provision its JetBrains Runtime. Those downloads are reused by later sessions.
A Java/JDK installation still has to exist to start Gradle itself; JDK 17 is the
repository build baseline.

The root `gradlew.bat` is a Windows Hot Reload bridge, not a general Gradle
wrapper. The supervisor sets `IKNA_HOT_RELOAD_GRADLE_EXE` to its existing pinned
distribution; the app's recompiler inherits it and uses the same executable.
Calling the bridge outside that session fails with a setup message rather than
choosing another Gradle installation. Ordinary build/CI commands stay unchanged.
Both the initial run and the continuous child build enable the daemon and file
watching. Multiple JVMs inside this one Hot Reload session are expected; do not
start a second build alongside it on a memory-constrained machine.

After installing the round-04 ZIP, stop the old Hot Reload session and launch
`dev-hot-reload.cmd` again once. Replacing the launcher on disk cannot change
the supervisor/app processes that are already running.

## Data safety

By default the launcher sets `IKNA_HOME_OVERRIDE` to an isolated folder under
`%LOCALAPPDATA%\Ikna\dev-hot-reload\profile` and selects the Developer Mode
profile there on the first run. After that the profile file belongs to the
app's own Developer Mode switch, so a "return to normal mode" choice made in
Settings survives relaunches. Hot-reload experiments therefore never open the
normal desktop learner database or normal preferences.

If real desktop data is deliberately needed for a check, run:

```text
dev-hot-reload.cmd -UseRealData
```

Do not use that option for destructive/synthetic testing.

## Replacing the full source tree

The launcher is designed around the existing ZIP workflow. If the visible
repository files temporarily disappear while the folder contents are replaced,
the supervisor waits for `settings.gradle.kts`, the root/desktop/shared build
files, `gradlew.bat` and the desktop entry point to return before starting a new hot-run
process. The `.git` directory is not managed by this tooling.

An active continuous build may survive a source-only replacement. A replacement
that changes build scripts/tooling needs a restart. If the top-level hot-run
process exits while required files are absent, the supervisor waits and restarts
after their return; otherwise it offers Enter to restart. A failed child watcher
can leave the app window open: check its logs and restart the session in that
case. The supervisor does not claim to repair an independently stopped watcher.

## Errors and logs

Compose Hot Reload 1.1.1 provides its own development feedback while the app is
running. The launcher explicitly enables `compose.reload.logStdout=true` and
`compose.reload.logLevel=Debug`, exposing child compiler/watcher and reload-agent
messages instead of recording only the initial launch banner. Native stderr is
merged inside `cmd.exe` before reaching Windows PowerShell, so a compiler's stderr
does not become a terminating PowerShell ErrorRecord. Output is mirrored to
timestamped files under:

```text
%LOCALAPPDATA%\Ikna\dev-hot-reload\logs
```

The launcher prints the exact log path for every hot-run process. The session log
also records the source root, exact Gradle/bridge paths, profile, start time and
top-level exit code. `latest.log.path` in the same directory points to the latest
session log. Desktop runtime
crashes also continue to use Ikna's existing crash logger; in the isolated hot
profile that file is:

```text
%LOCALAPPDATA%\Ikna\dev-hot-reload\profile\logs\ikna-desktop.log
```

A compilation failure during continuous mode is not a reason to commit or build
a release: fix/replace the source and let the watcher try again. A process-level
failure keeps the terminal and log path visible so the failure is not reduced to
an application window disappearing. The root `dev-hot-reload.cmd` additionally
starts Windows PowerShell with `-NoExit`: even if the supervisor itself hits an
early setup/script error before its normal restart prompt, the double-clicked
terminal stays open at the PowerShell prompt with the error still visible. Close
the window (or type `exit` after the supervisor has ended) when you are done.

## Proving an actual reload

An open app window or `python tools/check_hot_reload.py` passing proves neither
a live watcher nor a successful UI update. The source checker explicitly says so.
The root cause/evidence for the missing Windows recompiler bridge is recorded in
[ROUND-0.12-04.md](ROUND-0.12-04.md), including pinned upstream sources.

For the first check after restarting:

1. Wait for the app and the child build's `Waiting for changes` output. If the
   app opens but that output is absent, inspect the latest log for `Recompiler`,
   `gradlew.bat`, `FAILURE` or an exit/error before judging a UI repair.
2. Temporarily change a visible desktop Compose label, save it, and keep the same
   window open. Confirm a new compiler/build event, Hot Reload feedback and the
   changed label in that window. Record the edited file, save time, update time
   and log path. Restore the label and confirm a second update.
3. For failure recovery, temporarily introduce a Kotlin syntax error in that
   file. The compiler should report it while the app keeps its previous UI;
   restore valid code and confirm another build and UI update. Restore all probe
   edits before committing. No release build or corpus workflow is needed.

A build succeeding alone is not proof of visible reload. Build-script, dependency
or launcher edits require a session restart. Preserve failed-run evidence instead
of relabelling an unchanged window as a product regression.

For optional tooling regression checks on Windows, `python tools/test_hot_reload.py`
runs fake native Gradle fixtures only: quoted/non-ASCII paths, missing setup,
argument/exit preservation and merged stderr under PowerShell's stop policy.
It does not compile the app. On other hosts it reports explicit skips, not runtime
acceptance. Real watcher/reload validation remains the three-step check above.

## What Hot Reload is for

Best candidates:

- shared Compose layout, spacing and typography;
- theme/palette work;
- Signal Frame and other motion/interaction tuning;
- Settings, Decks, Statistics and Browse UI;
- desktop navigation and ordinary Compose state handling.

Expect a restart or normal build for changes such as Gradle/plugin/dependency
configuration, generated Room/KSP code, database migrations, native libraries,
packaging/installer code and Android-only integration. Final correctness still
comes from the normal repository checks and target builds.

## Toolchain decision

Ikna intentionally pins Compose Hot Reload `1.1.1` for the current 0.11 toolchain:
Kotlin `2.2.20`, Compose Multiplatform `1.8.2` and JVM target `17`. Hot Reload
`1.2.0-alpha02` and newer require Compose Multiplatform `1.10.0+`, so adopting
those versions belongs to a separate toolchain modernization rather than this
fast-loop change.
