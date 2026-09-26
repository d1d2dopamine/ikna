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
pinned Gradle 8.10.2 into `%LOCALAPPDATA%\\Ikna\\dev-hot-reload\\tools` if it is
not already there, Gradle resolves dependencies, and Compose Hot Reload may
provision its JetBrains Runtime. Those downloads are reused by later sessions.
A Java/JDK installation still has to exist to start Gradle itself; JDK 17 is the
repository build baseline.

## Data safety

By default the launcher sets `IKNA_HOME_OVERRIDE` to an isolated folder under
`%LOCALAPPDATA%\\Ikna\\dev-hot-reload\\profile` and selects the Developer Mode
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
files and the desktop entry point to return before starting a new hot-run
process. The `.git` directory is not managed by this tooling.

If the active Gradle continuous build survives the replacement, it observes the
new files normally. If it exits because the build scripts temporarily vanished,
the launcher waits for the source tree to become complete again and then offers
a restart instead of losing the error output.

## Errors and logs

Compose Hot Reload 1.1.1 provides its own development feedback while the app is
running. Gradle/compiler output is also mirrored to timestamped files under:

```text
%LOCALAPPDATA%\\Ikna\\dev-hot-reload\\logs
```

The launcher prints the exact log path for every hot-run process. Desktop runtime
crashes also continue to use Ikna's existing crash logger; in the isolated hot
profile that file is:

```text
%LOCALAPPDATA%\\Ikna\\dev-hot-reload\\profile\\logs\\ikna-desktop.log
```

A compilation failure during continuous mode is not a reason to commit or build
a release: fix/replace the source and let the watcher try again. A process-level
failure keeps the terminal and log path visible so the failure is not reduced to
an application window disappearing. The root `dev-hot-reload.cmd` additionally
starts Windows PowerShell with `-NoExit`: even if the supervisor itself hits an
early setup/script error before its normal restart prompt, the double-clicked
terminal stays open at the PowerShell prompt with the error still visible. Close
the window (or type `exit` after the supervisor has ended) when you are done.

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
