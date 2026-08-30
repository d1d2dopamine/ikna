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
