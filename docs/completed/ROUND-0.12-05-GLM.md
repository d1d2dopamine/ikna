# 0.12 desktop window round 05 (GLM) — 2026-10-06

Owner round on window-transition artefacts plus small desktop UX checks, run
with `ikna-development` + `ikna-owner-workflow`. Base:
`ikna-0.12-press-round-04-hot-reload.zip` — SHA-256
`98299ea0c1ab3cda7bb1be9e0651a8769890b3c60f00ccb98211349cd730aed1` verified with
`certutil` before unpacking; 556 files confirmed after unpacking. No Catalogue,
workflow, plan, CHANGELOG, CONTRIBUTING, skill, dependency, version, Room or
Hot Reload launcher/bridge file was touched.

**Files changed:** `docs/DESKTOP.md` (one added paragraph in the window-state
contract section) and this report. **No production code changed**: after the
investigation below, `desktop/src/main/kotlin/dev/ikna/desktop/Main.kt` is
byte-identical to the base archive (verified against the base ZIP), and no other
source file differs.

## Review and integration — Codex, 2026-10-06

The owner reproduced the artifacts after trying this output through Hot Reload.
The delivered source archive has **no application changes**, only DESKTOP and
this report. Codex compared it with the exact round-04 source manifest, then
integrated this reviewed report into the newer round-05 Catalogue baseline.
The speculative DESKTOP conclusion was rejected; the owning window contract
now describes the round-06 implementation. See [ROUND-0.12-06.md](ROUND-0.12-06.md).

The supplied evidence ZIP contains two UTF-8 text logs, no rendered frames or
screenshots. Probe 2 reports the final bounds at each callback; moved/resized
notifications for maximize/restore/fullscreen exit are about 135–153 ms apart.
This does **not** prove an atomic native transition, exclude an app-side race,
or establish what DWM/Skia presented between callbacks. Probe 1 has no pointer
correlation; the stationary pointer in a separate probe cannot prove its drift
was human dragging. That causal attribution is withdrawn.

GLM's reported build/check results below are external-agent claims. The build
log/XML results are absent from the evidence ZIP, so Codex did not independently
verify that Gradle run. The log header reports Java 17.0.20.1, whereas the report
also describes a JBR 21 Hot Reload environment: the probes do not establish the
same JVM/process path as the owner's visual check. Source-only Browse/focus
review is retained; neither task's interactive acceptance is closed.

## Session environment (actually observed)

Windows 11, single monitor 1920×1080 at **125 % scaling** (AWT reports the
screen as 1536×864 logical units; 1 unit = 1 dp for this window), build JDK
17.0.20 (Microsoft), app JetBrains Runtime 21, Compose Multiplatform **1.8.2**,
Gradle 8.10.2 (the pinned distribution already bootstrapped by the Hot Reload
launcher). **Computer Use (mouse/keyboard/screen automation) is unavailable in
this agent session** — the runtime rejects binding with "Computer Use is
unavailable for this node_repl session". All interactive UI verification is
therefore blocked and is reported as such; nothing interactive is claimed.

## Task 1 — window transition artefacts (reviewed observations)

GLM temporarily instrumented AWT move/resize/state callbacks and drove the
app's placement/minimize paths in separate DEV processes, removing the probes
before packaging. Delivered Main.kt is byte-identical to round 04.

Probe 2 reports initial/restored bounds `[48,48,1180,800]`, maximized/fullscreen
bounds `[0,0,1536,864]`, scale 1.25 and a stationary pointer. Minimize/restore
report state 0 → 1 → 0. These are callback snapshots of a single-monitor run,
not visual correctness evidence. Probe 1 contains many position-only callbacks;
their origin remains unverified. Evidence hashes and observations are preserved
in [windows-window-round06.json](evidence/windows-window-round06.json).

The original assertion that Windows necessarily stretches a previous frame
because undecorated windows cannot animate is **not established**. Native bounds
logs do not identify displayed frames. The owner's continued reproduction keeps
the issue open. Pinned upstream source review and scoped application fixes are
recorded in the round-06 report; no production fix was delivered by GLM.

## Task 2 — Browse in the minimal window

Source review of [`BrowsePane.kt`](../desktop/src/main/kotlin/dev/ikna/desktop/BrowsePane.kt)
against the current contracts: the 640 dp reading measure is intact (top-bar
column and every card are capped at 640 dp), exposure accounting still flows
through `browseMeaningfullyVisibleIndices` + `recordBrowse` only, the source
link is rendered only when a source id exists and opens the canonical Tatoeba
URL, and the pane keeps the shared empty/loading states. **No clipping, dead
control or input conflict is identifiable in source, and nothing could be
reproduced without screen access — the file is unchanged.** Long-context and
CJK rendering in the minimal 1000×660 window, transcription wrapping and
wheel/keyboard scrolling remain **not verified** (blocked); no CJK/long fixture
was exercised, and per the round rules nothing was generated.

## Task 3 — focus and keyboard after returning to the app

Source review only (runtime focus checks blocked):

- Window-level keys (`handleWindowKey`) consume only F11, F1 and Ctrl+1…4/`,` —
  they cannot intercept Space, A/D or Z, which stay inside the session screen;
  `SearchPane`'s `onPreviewKeyEvent` consumes only `Enter`/`NumPadEnter`, and
  the busy guard from round 03 is present in the merged base
  (`if (busy || localSearchTerms(query) == null) return`), so typing in Search
  stays local.
- Title-bar buttons are `clickable` (Compose focusable), draw their focus
  border from `collectIsFocusedAsState`, and are named with localized
  `contentDescription`s (`pc.017`–`pc.020`), which `check_localization.py`
  confirms exist consistently in all seven tables.
- The source review did not identify explicit focus stealing in the
  minimize/restore or file-picker paths; this is not an interactive guarantee:
  `BackupPane`'s Swing dialogs are modal and its cancel paths return before any
  state changes.

No focus/semantics failure was reproduced, so no code changed. Not verifiable
here and left open: actual focus return after minimize/restore and alt-tab,
Enter/Space activation of the plain `clickable` buttons on desktop, and
Space/A/D after returning — these need an interactive session.

## Checks reported by GLM (not independently verified from delivered logs)

- `python tools/check_text.py` — 496 text files, 7 localisation tables OK.
- `python tools/check_localization.py` — 7 languages, 714 keys each.
- `python tools/check_design_parity.py` — 38 source contracts OK.
- `:desktop:test` (bounded: `--no-daemon --max-workers=1 -Xmx768m`,
  pinned Gradle 8.10.2 distribution) — **BUILD SUCCESSFUL in 46 s**, exit 0;
  includes the window tests `WindowChromeRegressionTest`,
  `WindowPlacementMemoryTest`, `WindowBoundsTest` for the investigated area.
- `certutil -hashfile … SHA-256` on the base archive before unpacking.

## Not run / blocked

- Any interactive UI matrix (all three tasks): Computer Use unavailable —
  no mouse/keyboard/screen access; no screenshots exist and none are claimed.
- Full builds, installers, other JVM suites: not run (single bounded test task;
  the owner's own Hot Reload session was running throughout and was left
  untouched).

## Process notes

- The launcher was started first as instructed, but the machine already runs
  the **owner's own Hot Reload session** (Gradle session from 03:03, its app
  holding the single-instance lock of the shared DEV profile since 03:05). Per
  the "foreign JVMs are untouchable" rule that session was left running; my
  launcher was stopped (own PIDs only) and the two probe runs used the same
  pinned distribution with a separate temporary `IKNA_HOME_OVERRIDE` DEV
  sandbox instead. Both probe logs are delivered in the separate evidence ZIP
  (`probe-1-transition-log-0349.txt` with unexplained position-change trains,
  `probe-2-transition-log-0356.txt` clean), which is explicitly not part of the
  source archive.
- All temporary instrumentation was removed; `Main.kt` verified byte-identical
  to the base ZIP before packaging.

## Packaging

Full source ZIP built with `skills/ikna-owner-workflow/scripts/package_repo.py`
using the round-04 archive as the authoritative manifest and `--add
docs/ROUND-0.12-05-GLM.md`: 557 files (556 source-manifest + 1 new), exact
manifest/byte/integrity verification, `ikna.keystore` and both skills
preserved. Only `docs/DESKTOP.md` differs from the baseline among existing
files. Codex has compared this ZIP against the common round-04 base
and integrated the reviewed report with the Catalogue round; nothing here closes the 0.12 window
matrix or the overall round.
