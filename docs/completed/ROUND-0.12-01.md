# 0.12 small-defect round 01 — 2026-10-05

Owner-selected source batch using `ikna-development` and
`ikna-owner-workflow`, based on the complete `ikna-0.12-press-skills.zip`
snapshot. Active owners: [PLAN-0.12.md](PLAN-0.12.md) and
[modern_PLAN-0.12.md](modern_PLAN-0.12.md).

## Repairs and diagnosis

| Defect | Cause / change | Acceptance status |
| --- | --- | --- |
| Signal Frame disappears after a lighting switch; press still draws it | The old global arbiter selected the smallest historical hovered ID without checking the current pointer or clipped input bounds. A stale small candidate suppresses unrelated controls; press bypasses that owner check. Hover ownership now follows an attached pointer modifier, clears on cancel/detach, recovers on Move and excludes candidates outside the actual pointer. Painting bounds remain separate from clipped input bounds; palette changes keep the shared phase clock | Code repair and eight shared hover/lifetime tests added; final JVM and real Windows confirmation open |
| GREY preview tiles show DARK colours | Every authored grey background is still dark under `isLight`; the old callers therefore always selected dark variants. Previews now resolve DARK/GREY/SYSTEM explicitly; custom colours keep the existing brightness fallback | Shared lighting-matrix tests added; static parity/palette checks pass; runtime build/device checks open |
| Android tiles drift from desktop appearance/focus/semantics | Android still called a private old grid, bypassing the shared shape, Signal Frame and focus implementation. It now calls `IknaPaletteTiles` with the phone's default three columns; desktop retains four | One implementation confirmed by source contract; real Android check open |
| Palette radio targets omit selected state | Tile and caption advertised radio roles without selected semantics. Both now expose selected state within a selectable group; their existing click zones remain explicit. Each target has its own interaction/frame so caption hover does not depend on the tile's input bounds | Source contract passes; screen-reader verification open |
| IKNA-T-002: Windows self-test crashes without Bash | Shell-wrapper test invoked `bash` unconditionally. It now locates Bash and skips only that shell test with a reason when absent; DEX/source checks still execute | Linux wrapper test and simulated no-Bash skip regression pass |
| IKNA-T-003: CRLF census disagrees with BUILD | Text reading translated CRLF into LF before raw-byte accounting. Plain/gzip readers now retain original line endings; fixtures write explicit LF | Plain/gzip × LF/CRLF/CR with non-ASCII UTF-8 and exact BUILD totals pass |
| Plan/design drift | Initial skill work, current implementation and platform acceptance were conflated with old checkboxes. Active 0.12 inventory separates these states; historical evidence is retained. Language registry, retired system font, rounded glyph/progress/seal descriptions and local memory guidance are corrected | Documentation/source checks pass |

The stale-hover failure path is identifiable from source and specified in the
new policy regressions, whose JVM execution remains pending. This environment
cannot reproduce the owner's packaged Windows event sequence, so the precise trigger linking palette recomposition
to the reported failure is **not experimentally confirmed here**. Do not close
the real-platform bug solely from a source check or label this archive a tested
release build.

The repair follows Compose's pointer-node lifetime and coordinate contracts:
[PointerInputModifierNode](https://developer.android.com/reference/kotlin/androidx/compose/ui/node/PointerInputModifierNode)
and [GlobalPositionAwareModifierNode](https://developer.android.com/reference/kotlin/androidx/compose/ui/node/GlobalPositionAwareModifierNode).
The chosen event handling does not consume gestures or emit extra interactions.

## Local verification

Environment: Linux, Python 3.12.14 with existing PyICU. Gradle and a Kotlin
compiler are unavailable; the existing Java executable fails to load
`libjli.so`. No additional toolchain was installed. Consequently Kotlin tests,
Android/desktop compilation and packaged/device UI checks are **not run**.
New shared JVM test sources are wired into both `:shared:desktopTest` and
`:shared:testDebugUnitTest`, and both tasks/reports are included in grading CI.

Focused results already obtained:

- `tools/check_design_parity.py`: 38 source contracts pass.
- `tools/check_palettes.py`: 8 checks pass.
- `tools/check_android_ci.py --self-test`: 10 tests pass, including the explicit
  simulated no-Bash branch; shell verification also executes normally on Linux.
- `tools/catalog/test_meta_info.py`: 10 tests pass; the new test covers six
  independently written byte fixtures and verifies their BUILD totals.

Final available checks all exited 0 (29 commands):

- 13 repository commands: text, localization, synthetic fixture reproducibility,
  grading, full grading, optimizer, design parity, palettes, NSIS, palette preview,
  Android CI self-test, DEX names and Hot Reload source contracts.
- All 16 `tools/catalog/test_*.py` scripts: segmentation, v2 contract, ingestion,
  morphology, builder, v1/v2 parity, meta-info, readiness, storage, supply census,
  sharded census, WikiMatrix stream status, Everyday, Knowledge, selection and
  Global Voices attribution.
- The new byte regression was also run against the accepted old reader: both
  plain/gzip CRLF cases fail with the original normalized-byte undercount. All
  six cases pass with the repaired reader.
- Changed/new Markdown links resolve. `git diff --check` passes. Build metadata,
  signing-file bytes and complete schema history match the accepted snapshot.
- Complete source ZIP packaging verifies 543 files: all 539 accepted source paths
  plus the two current-round documents and two shared test files. Manifest
  agreement, SHA-256 byte agreement and ZIP integrity checks pass; both project
  skills and the signing/schema/binary assets are included.

These results are source/tool evidence. The new Kotlin suites and real-platform
smoke remain unexecuted here, as stated above.

## Required platform smoke

Use an isolated developer profile; keep one Gradle/hot-run session at a time
under CONTRIBUTING's memory guidance.

1. On Windows Settings, repeat DARK → GREY → DARK and DARK → GREY → GREY. Move
   between a small toggle/icon, a larger chip/button, palette tile/caption and
   navigation controls; the frame must follow hover before and after a click.
2. Repeat with angular/rounded appearance and animations enabled/disabled.
   With motion disabled, hover remains visible and static. Press freezes phase;
   release restores it. Keyboard focus still uses bordered controls' own border.
3. Scroll hovered controls out of view, move outside the window and return;
   no stale frame may suppress another control or paint over unrelated content.
4. On Android check GREY tile colours, rounded clipping/focus and selection
   announcements for tile/caption; blank space beside captions stays inactive.
   Verify SYSTEM against both phone light/dark settings and custom fallback.
5. Run the final shared/application JVM suites and Android/desktop builds. Record
   exact tree/toolchain/results here; retain failures instead of relabelling
   static checks as runtime evidence.
