# 0.12 round 06 — Windows window corrections and integration, 2026-10-06

## Scope and review

The owner reproduced window artifacts after trying GLM's output, including the
content growing into one corner before the remaining area catches up. GitHub
Desktop behaved normally on the same machine. That comparison does not identify
our renderer's exact fault, but it disproves the blanket claim that smooth window
motion is impossible on Windows. No visual redesign is authorized.

Codex source baseline is the complete 560-file round-05 Catalogue ZIP, SHA-256
`d3d5bb3b723d03ff0d88f8528faf72893c7398bf280536b369c0bfcd76a60ac6`;
every source byte matched before editing. GLM used the earlier 556-file round-04
ZIP. Its source ZIP (`9312ddb746ee598973e4cf36c9768eb9fc26548b2fde64c6c3c1cf5f39102591`)
changes only DESKTOP and adds its report. There is **no app fix to merge**.
The reviewed report is retained, but the unsupported DESKTOP paragraph is not.
The latest Catalogue scripts/workflows/evidence are preserved byte-for-byte.

The evidence ZIP contains two AWT text logs, no images or frame recordings.
Probe 2 gives final bounds at move/resize callbacks 135–153 ms apart for several
transitions. This cannot prove atomic native operations, exclude geometry races,
or show displayed frames. Probe 1's drift cannot be attributed to a person from
another run's stationary pointer. GLM's build result is reported externally;
the delivered ZIP contains no build log/XML for independent verification.

## Research and implementation

Primary source retrieval and input hashes are in
[windows-window-round06.json](evidence/windows-window-round06.json). Pinned
Compose 1.8.2 uses Skiko **0.9.4.2**, not the newest renderer.

| Confirmed source behaviour | Correction in this round |
| --- | --- |
| Compose 1.8.2 `UndecoratedWindowResizer.resize` calls `setLocation` and then `setSize`; left/top/corner drags expose a moved rectangle before resizing it. | Disable its edge zones and supply the same eight invisible zones/cursors. Derive each drag step from initial screen-space bounds and issue one `setBounds`, preserving minimum size, opposite edges and negative coordinates. Only requested/native Floating, non-minimized peers can resize. |
| `WindowState.placement` is the desired state. Compose's updater writes size, position, then placement; native listeners update state later. Our placement effect could therefore correct geometry before native restore. | Consume the saved restore request only from a native `componentResized` callback when requested and native placement are both Floating. Match means no write; otherwise use one native bounds write. Clear the request before correcting, cancel it on a newer non-floating request and remove the listener on disposal. |
| Pinned Skiko `SkiaLayer.setBounds` already immediately redraws Direct3D on resize. `Direct3DRedrawer.redrawImmediately` uses `windowsWaitForVsyncOnRedrawImmediately`, whose default is false; normal asynchronous frames use VSync. | Enable that existing immediate-presentation VSync property before window creation on Windows, preserving an explicit property override. No renderer selection, generic VSync, animation preference or other-platform default changes. Log the effective property and selected render API. |

The first two defects are established from the exact code path. Their contribution
to the owner's observed frames, and the benefit of the VSync change, still require
Windows visual acceptance. **The complete reported artifact is not declared
fixed from source or headless tests.** Immediate VSync may add display-refresh
waiting to the AWT render path; check drag responsiveness on the owner's machine.

JetBrains [Skiko PR 1243](https://github.com/JetBrains/skiko/pull/1243), merged
2026-08-06, implements native synchronous live resize, with geometry/render
coordination, child-surface bounds and composition timing. Compose
[PR 3299](https://github.com/JetBrains/compose-multiplatform-core/pull/3299)
integrates it in a much newer stack. Its new
`skiko.rendering.windows.direct3DSynchronousLiveResize` property is absent from
our pinned Skiko, so setting it here would be a no-op. That implementation also
targets native interactive resize; it does not establish that all programmatic
maximize/fullscreen/minimize artifacts disappear. A blind Skiko binary override
or untested whole-stack upgrade is not included.

Another tempting flag, `compose.swing.render.on.graphics`, explicitly prevents
transitional panel rendering issues, but the pinned `ComposeWindowPanel` hardcodes
`useSwingGraphics=false`. It cannot fix this application's `Window` by merely
setting the property. No such ineffective flag, paint loop, artificial delay,
frame-style toggle or software-renderer fallback is added.

## Checks actually run by Codex

The installed JDK 17 compiler module works after supplying its own library path;
no compiler/Gradle/Git was installed. Compile the three Java helpers and both
standalone check classes with `java com.sun.tools.javac.Main`, then run headless:

- `WindowBoundsEditsChecks`: **6 groups passed**, covering all 8 edges/corners,
  minimum/anchor behaviour, negative positions, one native bounds write, no-op
  duplicate bounds, requested/native acknowledgement, minimized/stale events,
  one-shot/cancelled restore, copied saved inputs, unknown position and Windows
  pacing overrides/platform isolation. JUnit delegates to the same assertions.
- `TitleBarClicksChecks`: **6 existing groups passed**.
- `tools/check_design_parity.py`: **38 source contracts passed**.
- `tools/check_localization.py`: **7 languages × 714 keys passed**.
- `tools/check_palettes.py`: **8 palette contracts passed**.
- `tools/check_text.py`: **505 text files and 7 localization tables passed**.
- `git diff --check`: passed. Source review against round 05 confirms that all
  Catalogue scripts/workflows, Room schemas, both skills and signing bytes are
  unchanged. Only the scoped window implementation/tests and owning records differ.

The source packager requires exact ZIP/source manifest, byte and integrity
verification. Java headless checks exercise geometry
and operation ordering, not real Windows presentation. Gradle/Kotlin/Compose
compilation, GUI interaction and packaged desktop checks could not run here:
no Gradle/Kotlin toolchain or Windows display is available. GLM's old reported
test run predates these changes and cannot validate this implementation.

## Fresh-process Windows acceptance (still open)

Close the app and its existing Hot Reload session, replace sources, then start
`dev-hot-reload.cmd` again. Merely reloading composables cannot rerun `main()` or
replace native startup setup. No release build or long Catalogue workflow is
needed. The app log should identify `window immediate-vsync=true` and its actual
render API unless explicitly overridden.

Check all four edges/corners, especially left/top, minimum size and returning the
pointer to its initial position. Then maximize/restore via button and double
click, minimize/restore via taskbar, Alt+Tab, F11 from Floating and Maximized,
rapid reversals and close/reopen with saved floating size. Confirm no stepped
content, new lag, lost pointer/focus or drifting restore. Repeat at 125% scaling;
mixed-DPI/multi-monitor checks remain separate open evidence. Browse and keyboard
return checks from GLM remain unverified; no speculative fixes were added there.

## Delivery

One complete repository ZIP combines these window changes, the corrected GLM
report and every accepted Catalogue round-05 change. No source deletions,
dependency/version/schema changes, workflow dispatch, corpus download or public
publication. Both repository skills and tracked binary/signing assets remain.

Explicit additions to the 560-file source manifest (568 files total):
`WindowBoundsEdits.java`, `WindowsFramePacing.java` in desktop main Java;
`WindowResizeOverlay.kt` in desktop main Kotlin;
`WindowBoundsEditsChecks.java` in desktop test Java;
`WindowBoundsEditsTest.kt` in desktop test Kotlin; the reviewed GLM round-05
report, this round-06 report and `docs/evidence/windows-window-round06.json`.

## Owner follow-up, 2026-10-06

The owner reports artifacts unchanged. Three supplied fresh-process startup
records show effective `immediate-vsync=true renderApi=DIRECT3D`. Startup source
was executing; this round did not pass visual acceptance. This does not invalidate
the scoped headless checks above, which never established displayed smoothness.
The next pinned-source layout investigation and correction are recorded in
[ROUND-0.12-07.md](ROUND-0.12-07.md); the complete window defect remains open.
