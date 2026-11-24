# 0.12 visual/UX round 03 — reviewed integration, 2026-10-06

Owner-selected small visual/UX round, with parallel GLM PC testing and separate
Search/Backup work. Source baseline: `ikna-0.12-press-round-02-merged.zip`,
550 files, SHA-256
`fb01fc3f2dd86c0caf66488fa20e07da166bfa6dbf8de697b3aeeab7f88f9c07`.
All baseline source bytes matched the working tree before editing. Both
repository skills apply. The original Codex lane is recorded below; GLM's
round-03 source result is now reviewed and integrated in the final section.
An integrated compile/runtime pass remains open.

## Implemented repairs and evidence

| ID | Trigger and previous source behaviour | Root and change | Verification status |
| --- | --- | --- | --- |
| IKNA-UX-031 | Browse in a pane wider than 640dp; the header and cards could expand to available width despite their declared reading measure | `fillMaxWidth()` fixed incoming width before `widthIn(max = 640.dp)` could cap it. The limit now precedes fill at both sites, retaining centre alignment | Source constraint analysis; real Compose measurement/Hot Reload open |
| IKNA-UX-032 | Settings in a pane wider than 1040dp; jump strip, divider and list could all expand past their intended maximum | The same fill-before-limit order occurred at three sites. All now cap width before `fillMaxWidth()`/`fillMaxSize()`; margins and section order unchanged | Source constraint analysis; wide-window Hot Reload open |
| IKNA-UX-033 | Change interface language or selected font while retaining the active Settings section and viewport width | The centring effect read `spots[activeId]` only inside its coroutine; measured span changes did not change its keys. The active span and measured `row.maxValue` are now observed in composition and passed as keys, so centring follows the new measurement | Source effect-key review; live language/font switch open |

Compose's documented [constraint/modifier order](https://developer.android.com/develop/ui/compose/layouts/constraints-modifiers)
explains the width defect: fill establishes exact incoming bounds, and a later
preferred limit must still respect them. This was source-level confirmation,
not a screenshot reproduction. The caps are existing design measures, not new
width preferences or a redesign.

For a bounded pane of 700/1020/1420dp, the analysed header widths change from
the full pane to 640dp for Browse; Settings remains 700/1020dp and caps the
last case at 1040dp. Browse cards additionally respect the feed's existing
horizontal padding. This arithmetic is a constraint explanation, not an
executed Compose layout test.

The Settings fix keeps the vertical-scroll guard before horizontal animation.
When animations are disabled it still calls `scrollTo`; otherwise it uses the
existing motion duration/easing. The existing Java source-contract assertion
was updated for the new effect inputs; it is not a new runtime test.

## Reviewed without further product changes

- Signal Frame coordinator/pointer lifetime, clipped input bounds, placement
  cleanup, shared phase, theme recolouring and palette tiles. The original
  round-01 repair remains byte-identical; no additional confirmed source defect
  justified a change. The reported Windows lighting sequence remains open.
- Browse prompt/meaning/transcription already allow ordinary text wrapping.
  No new truncation or CJK defect was established without real content/window
  evidence, so typography/content rendering and target highlighting are retained.
- Settings action-row overflow candidates did not justify a broad Row/FlowRow
  rewrite from source review. Preserve the current interface until reproducible
  evidence identifies a specific control.
- Learning/data policy, Browse exposure accounting, schema history, source
  admission, release/build versions, signing and binary assets are untouched.

## Available verification

Six Python commands exited 0 after the product changes:

- `python tools/check_text.py`
- `python tools/check_localization.py` — seven languages, 714 keys each
- `python tools/check_design_parity.py` — 38 source-contract tests
- `python tools/check_palettes.py` — eight contracts
- `python tools/check_hot_reload.py` — launcher/toolchain/profile source contracts
- `python tools/check_android_ci.py` — 157 Android-bound files pass DEX naming

Text checks are repeated after final documentation edits; relative document
file links, `git diff --check`, scoped byte differences and the final source ZIP
manifest/integrity are checked before delivery. These checks establish source
consistency only. No new corpus workflow or remote build was dispatched.

Java cannot load its preinstalled `libjli.so`; `javac`, Gradle and `kotlinc` are
unavailable. No tooling was installed. The updated Java contract, Kotlin/JUnit
suites, shared Android/desktop compilation and actual UI rendering were not run
locally. Hot Reload on the owner's PC is the next desktop development acceptance
path; Android and packaged release validation remain separate.

## Hot Reload handoff

Use the default isolated launcher profile; one Gradle/Hot Reload process at a
time. The final ZIP contains both reviewed lanes; use that common tree for the
next runtime pass. GLM's separate result was compared against the exact common
round-02 baseline and merged by file; its build outputs are not included.

1. Browse: at the minimum window and a wide window, check header/card centring,
   long/CJK content, transcription, source focus/link and wheel/keyboard input.
   A wide window should increase side space rather than the reading measure.
2. Settings: at a pane exceeding 1040dp, verify that jump strip, divider and
   sections share the same centred measure. Reach a later section; switch RU/EN
   or an already available custom font, and check that its active label remains
   visible after remeasurement. Resize; test animations on/off and a fast fling.
3. Original defect: DARK → GREY → DARK and DARK → GREY → GREY under both
   appearance variants and animation settings, including stationary-pointer
   repaint, re-hover, keyboard focus and scrolling. Do not call it closed solely
   because source tests pass.

Correction to the planning prompt: this tree has no separate font-size/scale
preference. Record the existing Windows scale and available selected font;
do not claim an in-app large-font setting was exercised or add one for this
round. Missing long/CJK fixtures or unavailable platform checks remain open.

## Original Codex source and archive scope

Changed existing files:

- `desktop/src/main/kotlin/dev/ikna/desktop/BrowsePane.kt`
- `desktop/src/main/kotlin/dev/ikna/desktop/SettingsPane.kt`
- `shared/src/jvmShared/kotlin/dev/ikna/ui/settings/SettingsChrome.kt`
- `app/src/test/java/dev/ikna/ui/SettingsSourceContracts.java`
- `CHANGELOG.md`
- `docs/DESIGN.md`
- `docs/PLAN-0.12.md`
- `docs/modern_PLAN-0.12.md`
- `docs/DOCUMENTATION.md`

Added: `docs/ROUND-0.12-03.md`. The complete source package uses the 550-file
merged round-02 manifest plus this explicit addition: 551 files, no deletions.
The packager requires exact source/ZIP bytes and ZIP integrity. Both skills,
version metadata, signing/schema/assets and prior Catalogue code/workflows are
retained. At the original Codex delivery, GLM's SearchPane, BackupPane and
HOT-RELOAD files were byte-identical to the common baseline; integration follows.

## Reviewed GLM integration

The returned GLM source ZIP has 551 files: two existing-file edits and the new
`docs/ROUND-0.12-03-GLM.md`, no deletions. Neither edit overlaps the original
Codex changes. The source tree matched the delivered Codex ZIP before merging.

Accepted GLM's `SearchPane.kt` busy guard inside the common `search()` function.
Keyboard Enter/NumPadEnter and the button now use the same gate; the existing
token/request check, query-edit invalidation and success/failure clearing remain
unchanged. Accepted the single remaining path-separator fix in HOT-RELOAD.md.
BackupPane had no established defect and remains byte-identical.

Reviewer corrections:

- Removed the unintended leading UTF-8 BOM from SearchPane.kt.
- Completed the same width-order defect in Search at both input/result columns:
  apply the existing 840dp cap before filling the pane. This preserves the 40dp
  margins and existing 760dp inner field/result measure; no redesign or new
  preference is introduced.
- Labelled GLM's execution claims as external reports. Its header had suggested
  real UI verification, while the body says UI automation was unavailable.
  No pointer/keyboard/visual matrix was run. Removed an unsupported assertion
  that queries completed too quickly despite no recorded interactive timing.
- The evidence directory/logs mentioned by GLM were not supplied. Its warm start,
  timestamps and `:desktop:compileKotlin` success in 31s are reported rather than
  independently verified, and apply to its separate tree, not this integrated
  one. The reported auto-reload failure is recorded as an open finding; watcher
  startup/root cause cannot be established from this attachment. No speculative
  launcher, daemon, dependency or version change was made.

Independent merged-tree verification: all six commands listed above exit 0,
including 38 design and eight palette contracts. Text/localization, Hot Reload
source wiring and DEX names pass. Source/relative-link/diff checks and complete
ZIP verification apply to the integrated tree. The updated Java/Kotlin tests and
integrated compilation remain unavailable locally; no behavioural search test
or actual Hot Reload/Android/packaged-app run is claimed.

Final archive: the complete 551-file Codex source manifest plus the explicit GLM
round-report addition, 552 files, no deletions. Both repository skills and all
previous accepted work are included. There are no build outputs or evidence
logs in the source manifest. Final plans, changelog and documentation map now
record the completed merge and keep runtime/release acceptance open.
