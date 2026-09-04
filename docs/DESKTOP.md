# Desktop

Ikna is an Android app. This file is the plan for making it also a Windows
program you can download as one file and run, what that costs, what it cannot
reproduce, and which stage of the work each part belongs to.

It is written before the port rather than after it, because the first question
-- can the same design survive on a desktop -- has an answer that decides the
shape of everything else.

## The answer

Yes, and not by redrawing anything.

The screens are Compose. Compose is not an Android technology that happens to
run elsewhere; it is a rendering library with an Android backend and a desktop
backend, and the desktop one draws with Skia into a window exactly as the
Android one draws with Skia into an Activity. `Column`, `Modifier.padding`,
`AnimatedContent`, the swipe on a card, the tints in `ui/theme` -- all of it is
the same source file compiled twice. The typography, the spacing scale in
`Metrics.kt`, the lattice in `MemoryLattice.kt` and the wordmark are not
ported, they are shared.

Three things cannot come across, and pretending otherwise would only postpone
the disappointment:

- **The home screen widget.** There is no such surface on Windows. A tray icon
  showing what is due is the nearest equivalent and is a later stage, not a
  translation of `widget/`.
- **Updating itself.** The Android build downloads an APK and hands it to the
  package installer. Windows has no equivalent that is safe to do from an
  unsigned program; the desktop build will check the version and open the
  release page in a browser instead.
- **Speech.** See "Voice" below. The Windows build ships with the section
  visible and marked as not available on PC.

## What the app is actually made of

Measured, not estimated, over `app/src/main/java/dev/ikna` -- 103 Kotlin files,
29,141 lines:

| | files |
| --- | --- |
| no `android.*` or `androidx.*` import at all | 45 |
| Compose imports only | 19 |
| Android APIs beyond Compose | 42 |

So two thirds of the tree is either already portable or portable the moment
Compose is. That includes the whole of `domain/` -- FSRS, the governor, the
session builder, the phonetics respelling -- and it includes every string.

Two facts make this much cheaper than it looks:

- **The interface text is Kotlin, not resources.** All six translations live in
  `ui/text/Strings*.kt` as 568 keys per table. There is no `values-ru/strings.xml`
  to reimplement, and `S.t("dp.014")` works unchanged on a desktop.
- **Two drawables are used, in total.** `R.drawable.ikna_wordmark` in
  `ui/theme/Wordmark.kt` and `R.drawable.ic_notification` in
  `work/ReminderWorker.kt`. Everything else in `res/drawable` is a launcher
  icon or a widget background. The rest of the interface is drawn in code.

Of the 53 test files, three touch an Android type at all, and only for
`Color` and `TweenSpec`. The test suite moves to `commonTest` nearly for free,
which matters: the scheduler tests are the reason a refactor of this size is
survivable.

## The nine places the app touches Android

| What | Where | Difficulty |
| --- | --- | --- |
| Room and `androidx.sqlite` | `data/db`, 5 files | easy -- Room 2.7 runs on JVM desktop with the bundled SQLite driver |
| DataStore preferences | `data/prefs/SettingsStore.kt` | easy -- `datastore-preferences-core` has a JVM target |
| Network | `data/catalog`, `data/update` | free -- it is plain `java.net.HttpURLConnection` |
| zstd-jni, commons-compress | `data/anki` | free -- both publish desktop builds |
| Fonts | `ui/theme/Theme.kt`, `FontStore` | easy -- `FontFamily(Font(file))` exists on desktop |
| File pickers, `Uri`, `DocumentFile` | 6 screens | medium -- becomes a native file dialog |
| Export through `MediaStore` | `data/export/JsonExporter.kt` | medium -- write to the Downloads folder |
| WorkManager and notifications | `work/`, 4 files | medium -- a desktop program only runs while it is open |
| Speech | `audio/`, 9 files | hard -- see below |

Nothing in that list is architectural. The app was written with its logic
separated from its screens, and the screens separated from the platform, and
that is the only reason this is a port rather than a rewrite.

## Voice

The speech runtime is sherpa-onnx, and it enters the build as a local `.aar` in
`app/libs` fetched by `tools/voice/fetch-voice.sh`. An `.aar` is an Android
library; it cannot be put on a desktop classpath.

Whether the same project publishes a plain JVM artefact with Windows native
libraries is **not verified here**, and it will not be claimed until the file
has been found and its contents listed. This project has already been burned
once by a plausible-looking identifier that did not exist -- an Epitran
language map that was invented rather than checked -- and the cost was a red CI
run. The Windows build therefore ships with speech off, the section present and
labelled as not yet available on PC, and finding out is its own stage.

## Shape

```
:shared    commonMain   domain, data, every screen, strings, theme
           androidMain  Context, storage access framework, TTS, WorkManager
           desktopMain  %APPDATA% paths, file dialogs, tray
:app       thin Android: MainActivity, manifest, widget, workers
:desktop   thin Windows: main(), the window, .exe and .msi packaging
```

The seam between them is about ten `expect`/`actual` declarations: where files
live, how the database is built, how preferences are opened, how a bundled
asset is read, how a file is picked, how a link is opened, how something is
spoken, how work is scheduled, how the user is notified, and how a font file is
loaded.

Ten is a number worth defending. Every one of them is a place where the two
builds can drift apart, and the temptation during a port is to add an eleventh
rather than push a decision up into shared code.

## Layout on a wide window

A phone screen is a column. A desktop window is not, and stretching a column
across a 27-inch monitor is not faithfulness, it is neglect.

Above a width threshold the desktop build shows two panes: the deck list on the
left, the current content on the right. Below it, the same single column as the
phone. This is a real difference between the two builds and it is deliberate:
the goal is that the PC app is as comfortable to use as the Android one, not
that it is pixel-identical to it.

Everything inside the panes is the same composables. The split is a layout
decision made once, at the navigation level, not a second set of screens.

## Stages

**Stage 0 -- toolchain. Done.**
Kotlin 2.0.20 to 2.2.20, KSP to 2.2.20-2.0.4, Room 2.6.1 to 2.7.2, and
`android.kotlinOptions` replaced by the `kotlin.compilerOptions` block. The app
is still Android-only and nothing else changed. Compose Multiplatform from 1.8
onwards refuses to configure on a Kotlin older than 2.1, so this had to happen
regardless; doing it alone means that if it breaks, only one thing broke.

See `app/build.gradle.kts` for why each number is the one it is, why the Room
Gradle plugin is not applied, and why Room 2.8 and 3.0 were both declined.

**Stage 1 -- split.**
`:shared`, `:app`, `:desktop`, the `expect`/`actual` boundaries, tests moved to
`commonTest`. Still no Windows artefact. The Android APK must come out of stage
1 byte-comparable in behaviour to the one before it.

**Stage 2 -- the first .exe.**
Decks, catalogue, sessions, transcription, search, statistics, settings, JSON
export and import. No voice, no widget, no reminders.

**Stage 3 -- desktop manners.**
Keyboard shortcuts (space to reveal, 1-4 to grade, arrows to move), window size
and position remembered, tray notifications, Anki import through a native file
dialog, update check that opens the release page.

**Stage 4 -- voice, if the artefact exists.**

## Build

`build.yml` becomes two jobs that run at the same time:

- `android` on `ubuntu-latest`, doing what it does today.
- `windows` on `windows-latest`, producing the portable folder and the
  installer.

The Windows runner is not a preference. `jpackage`, the JDK tool that produces
an `.exe` and an `.msi`, documents its Windows options as "available only when
running on Windows". There is no way to make a Windows installer on the Ubuntu
runner, and any plan that claims otherwise is wrong.

The shell scripts the build already depends on -- `tools/voice/fetch-voice.sh`
and `tools/catalog/fetch-bundled-pack.sh` -- run on the Windows runner under
`shell: bash`, which is Git Bash and is present by default.

`release.yml` gains a third job attaching `Ikna-<version>-windows-x64.zip` and
the `.msi` beside the APK.

One wrinkle worth writing down before it surprises somebody: `jpackage`
insists on a purely numeric version, so `0.10.0 press` becomes `0.10.0` for the
installer. The epoch word stays in the file name and in the app.

## What you download

Two things, because they answer different questions:

- **A portable zip.** `Ikna.exe` and a cut-down Java runtime beside it. Unpack,
  run, delete the folder to uninstall. Nothing to install, no Java needed.
- **An installer.** An `.msi` that puts it in Program Files with a Start menu
  entry.

Both are somewhere around 70-110 MB, most of which is the runtime and Skia.

**The executable will not be signed.** The only key this project has is the
Android keystore in `ikna.keystore`, and it signs APKs; a Windows code signing
certificate is a different thing that is bought from a certificate authority.
So the first run shows SmartScreen's blue "Windows protected your PC" panel,
and getting past it means More info, then Run anyway. That is not a bug and it
will not go away by rebuilding. It is the honest cost of a personal app that
nobody is paying a certificate authority for.

## Linux

Everything above is about Windows, and none of it had to change. This section
is the whole of Linux, and it is packaging rather than a port: no Linux source
set, no second `main()`, no screen drawn twice, not one line of Kotlin. The
nine places the app touches Android are the same nine places, already answered
by `:shared`'s `jvmShared` source set. `:desktop` is a plain Kotlin/JVM module,
`createReleaseDistributable` asks `jpackage` for an application image, and
`jpackage` builds one for whatever machine it is running on. The Ubuntu runner
was already building `:desktop` on every push -- it just threw the folder away.

### What you download

One file: `ikna-<version>-linux-x86_64.AppImage`, somewhere around 100 MB, the
same shrunk jars and cut-down Java runtime that are inside the Windows zip.
Nothing to install, no Java needed, and uninstalling is deleting the file.

```
chmod +x ikna-v0.10.0-press-linux-x86_64.AppImage
./ikna-v0.10.0-press-linux-x86_64.AppImage
```

On Fedora that needs FUSE 2, which Fedora has not shipped by default since 40:

```
sudo dnf install fuse-libs
```

Without it the file says so rather than failing quietly. If installing a
library to run one file is not the trade you want, the runtime can unpack
itself into a temporary folder instead, at the cost of a slower start:

```
./ikna-v0.10.0-press-linux-x86_64.AppImage --appimage-extract-and-run
```

There is one file rather than the Windows two because the second Windows file
answers a question Linux does not ask: an `.msi` exists so the app can live in
Program Files with a Start menu entry, and an AppImage in `~/Downloads` is
already a program you can run and delete. No `.deb` and no `.rpm`, which
`jpackage` will happily produce, because both are installers tied to the
distribution that built them -- a `.deb` built on the Ubuntu runner is a
promise about Fedora that nobody checked. `app-image`, the third `jpackage`
option, is a folder. An AppImage is that folder squashed into one executable
file, which is why it is assembled after Gradle rather than by it.

It is not signed, for the same reason the `.exe` is not. Linux does not put a
blue panel in front of it.

### How it is built

`tools/appimage/build-appimage.sh`, which compiles nothing. It reads the
application image Gradle left in
`desktop/build/compose/binaries/main-release/app`, copies it whole into an
AppDir under `usr/` -- whole, because the `jpackage` launcher finds its runtime
and its jars relative to itself, and splitting them is how an AppImage that
starts on the build machine dies on somebody's desktop -- writes an `AppRun`
that resolves the launcher through `readlink` rather than an absolute path,
adds `tools/appimage/ikna.desktop` and the 512px icon, and hands the folder to
`appimagetool`.

```
gradle :desktop:createReleaseDistributable
bash tools/appimage/build-appimage.sh
```

writes `build/appimage/ikna-x86_64.AppImage`. `--appdir-only` stops before the
packing step, for looking at what is about to be shipped. `appimagetool` is
pinned to release 1.9.1 and fetched into `build/`, or supplied through
`IKNA_APPIMAGETOOL` on a machine that already has it. The pinned release is
tried first, then the rolling `continuous` build, then the renamed legacy
asset in the old AppImageKit repository: upstream moved the tool once already
and a single address is what turns that move into a failed build. The packer
itself needs `mksquashfs`, which recent versions no longer carry inside
themselves, so a local build wants `sudo dnf install squashfs-tools`.

Building it on Fedora yourself works, with one thing worth knowing: Gradle has
to configure `:app` and `:shared` before it can build `:desktop`, so an Android
SDK has to exist even though no APK is being made. That is what `ANDROID_HOME`
or `sdk.dir` in `local.properties` is for.

### Build

`build.yml` has a third job, `linux` on `ubuntu-latest`, beside `android` and
`windows` and needing nothing from either. It fetches the pinned starter deck,
runs the same `createReleaseDistributable` the Windows job runs, packs the
AppImage, uploads it as `ikna-linux-appimage`, and then starts the packaged
file once with `--selftest` -- through the AppImage, not beside it, so what is
tested is the file that gets published. `release.yml` has the matching job,
attaching `ikna-<tag>-linux-x86_64.AppImage` to the same release as the APKs
and the Windows files.

Two details in those jobs are worth the sentence they cost. The runner has no
`libfuse.so.2`, so both jobs set `APPIMAGE_EXTRACT_AND_RUN=1`, which is also
how `appimagetool` -- itself an AppImage -- manages to run at all. And there is
no `setup-android` step, unlike the Windows job: the Ubuntu image carries the
SDK that the `android` job above builds a real APK with, which is a stronger
guarantee than any action could add.

Nothing in the trimmed runtime is compiled on the runner. The launcher, the
JVM, Skia, SQLite and zstd all arrive as prebuilt binaries from Temurin and
from jars, built against a glibc far older than Fedora's, so a build from
Ubuntu is not a build that only runs on Ubuntu.

### Where it keeps things

`~/.ikna`, the branch `iknaHome()` already had for anything that is not
Windows: the database, the settings, the window position, `ikna.lock`, and the
log at `~/.ikna/logs/ikna-desktop.log`. Nothing is written next to the
AppImage, so the file can be moved or deleted without touching a single card.

The omissions are the Windows omissions, unchanged: no voice, no widget, no
reminders, and the update check opens the release page rather than replacing
anything. If the window comes up blank on a machine with an unhappy GL driver,
`SKIKO_RENDER_API=SOFTWARE` in front of the command is the first thing to try.

### The locale the JVM is started in

`AppRun` starts the application in `C.UTF-8` rather than in the locale it
found. The SQLite that Room talks to is compiled into the application and
brings its own copy of the C++ standard library; under a national locale on a
recent glibc -- Fedora 44 with glibc 2.43 is the machine that found this --
that copy's locale facets come up empty and the first statement dies with
SIGSEGV a second after launch, in `sqlite3_step` under
`PackLoader.installBundledPacks`, before a window appears and leaving an
`hs_err_pid*.log` next to the file. `C.UTF-8` is built into glibc and reads no
locale data at all, which is what steps around it.

The interface does not change with it. The locale that was in effect is handed
to the JVM as `user.language` and `user.country`, which is what `Strings.kt`
reads when the language setting is `system`, so a Russian desktop still shows
a Russian interface. `C.UTF-8` and not `C` for the sake of file names: under
`C` the JVM reads paths as ASCII, `sun.jnu.encoding` cannot be argued out of
it from the command line, and a deck sitting in a folder with a national name
becomes unopenable.

`IKNA_KEEP_LOCALE=1` starts with the locale untouched, which is how to check
whether a newer `sqlite-bundled` has made all of this unnecessary, and
`IKNA_NATIVE_LOCALE=<locale>` names a different one. The `linux` job runs the
self test twice, once as the runner comes and once under `ru_RU.UTF-8`, so the
path through `AppRun` is exercised on every build.

### Two C++ runtimes in one process

The Linux build crashed at startup with `SIGSEGV` about a second in, inside
`androidx_sqliteJni`, while `--selftest` -- which opens the same database,
installs the same decks and runs the same queries -- passed on the same
machine. The difference between them is the window.

`sqlite-bundled` ships a prebuilt native library with a whole C++ standard
library compiled into it, and an old one: the symbols in the crash are
`std::string::_Rep` and `std::ctype<wchar_t>`, the copy-on-write string layout
gcc stopped using in 2015. It declares no dependency on `libstdc++` at all --
`readelf -d` lists only `libdl`, `libm`, `libpthread`, `libgcc_s`, `libc` and
the loader -- so on its own it answers every C++ call itself, which is what
happens under `--selftest`.

Drawing a window loads Skia, Skia depends on the system `libstdc++`, and from
then on the process holds two implementations of the same symbols. The linker
may bind a call inside the SQLite library to the system copy, an object built
by one implementation is then destroyed by the other, and the pointer it
follows is garbage: the second crash log wrote into the library's own
read-only code page. Nothing about this is a bug in the application, and
nothing about it is visible on the Ubuntu image CI builds on, whose
`libstdc++` is close enough to the one the library was compiled against.

Two lines fix it, and both are about ordering rather than about SQLite:

- `Main.kt` installs the decks before Compose is touched, so the database is
  opened while the SQLite library is the only C++ runtime in the process.
- `AppRun` exports `LD_BIND_NOW=1`, so the symbols it resolves then are
  resolved for good instead of being looked up again at the first call, after
  Skia has arrived.

`IKNA_LAZY_BIND=1` turns the second one off, which is how to check whether a
newer `sqlite-bundled` has made all of this unnecessary.

### What a Linux machine has to have

An AppImage carries the application, the Java runtime image and every native
library -- but never glibc, which always comes from the distribution. So the
only real requirement is a glibc version, and `tools/appimage/check-portability.sh`
reads it out of the finished build:

```
bash tools/appimage/check-portability.sh
```

It prints the highest `GLIBC`, `GLIBCXX` and `GCC` version any library inside
asks for, and which library asks. The `linux` job runs it on every build and
writes the result into the run summary, so the number for a release can be
copied from there into the release notes. It is a report by default;
`IKNA_GLIBC_LIMIT=2.35` makes it fail when the floor rises above a ceiling.

Nothing in this repository is compiled against the runner's glibc -- jpackage
copies a launcher the JDK was shipped with -- so the floor is decided by
Temurin and Skia and does not move when GitHub changes its image.

Two things that are not glibc and still stop a download from opening:

- The execute bit does not survive a zip file. `chmod +x ikna-x86_64.AppImage`
  first; a double click on a file without it can be routed to the disk image
  writer, which is not what anybody wants.
- The AppImage runtime mounts itself with FUSE. On a machine without it the
  file refuses to start with a message about `libfuse`, and
  `./ikna-x86_64.AppImage --appimage-extract-and-run` works anyway -- as does
  installing `fuse` or `fuse-libs` from the distribution.
