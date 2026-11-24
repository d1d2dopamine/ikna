# 0.12 round 07 — first resized picture, 2026-10-06

## Baseline and owner evidence

The source baseline is the complete 568-file round-06 ZIP, SHA-256
`8c94e1f867f62263fc8d76713ea2766c37bb27686860eadef33772561325a47b`.
All source bytes matched before this round. Catalogue round-05 work and the
round-06 bounds/restore corrections remain in this baseline.

The owner reports that the window artifacts persisted. Supplied startup logs
show three new processes on 2026-10-06, each with
`window immediate-vsync=true renderApi=DIRECT3D` (04:53:40, 05:12:00, 05:47:43,
owner-local time). The previous startup code was executing; failure to apply
Hot Reload is not supported as the explanation for this result. Round 06 did
not pass the owner's visual acceptance. Logs contain no presented frames and
cannot identify the remaining fault alone.

## Pinned-source finding and correction

Primary source URLs, byte hashes and normalized owner log observations are in
[windows-window-round07.json](evidence/windows-window-round07.json).
Compose 1.8.2 uses Skiko 0.9.4.2. Its resize path is:

1. `ComposeWindowPanel.setBounds` changes the container, then
   `ComposeContainer.setBounds` changes the SkiaLayer size.
2. Showing Direct3D `SkiaLayer.setBounds` immediately records/draws a picture
   for the new pixel size. It does not call `doLayout` first.
3. The layer's later `doLayout` updates the child Canvas bounds, including
   Skiko's existing fractional-DPI adjustment. Compose's subclass then calls
   `mediator.onComponentSizeChanged`, updating the scene constraints.
4. The original mediator render callback draws the scene with its current
   constraints; it does not derive new constraints from its width/height arguments.

This permits a new-size picture with old scene/child geometry. It fits the
reported corner-first content growth, but its contribution to the actual
displayed artifact remains a hypothesis until the owner's Windows check.

Install one public `SkiaLayer.renderDelegate` wrapper for the Windows custom
frame. Before delegating a Direct3D picture whose size, scale or native-window
revision changed, call the layer's existing `doLayout`. Dynamic dispatch reaches
Compose's subclass, updating child geometry and scene constraints before that
same picture draws. Delegate the original canvas, dimensions and timestamp once
after successful preparation. Stable animation frames do not force layout.
Native resize/state callbacks invalidate the gate, including same-size return
from minimize. Preparation failure leaves the gate pending; invalidation during
preparation or drawing is retained for the following frame.

Skiko already guards immediate redraw during its render callback by scheduling
a later redraw. The wrapper adds no reflection, renderer switch, private scene
access, frame-style toggle, geometry write, timer, sleep or perpetual repaint.
Fallback renderers delegate unchanged. Disposal removes both native listeners
and restores the original delegate only if this wrapper still owns the slot.
The existing immediate VSync setting is retained to isolate the layout change.
This is not a backport of newer Skiko native live-resize/DWM hooks.

## Bounded DEV diagnostic

The startup marker is `window surface-sync=before-picture-v1` with the actual
renderer. If the main layer/delegate cannot be found, log `surface-sync=unavailable`
and leave the ordinary window path functional.

Developer Mode writes `logs/window-surface.log` under the existing profile home.
It captures native resize/state snapshots, resized picture dimensions/scale,
child bounds before/after preparation, and the latest Compose root size reported
by `onSizeChanged` before/after the original render. This observer stores plain
fields and causes no recomposition. The first two subsequent pictures are also
sampled. AWT bounds are logical units; picture/root measurements are pixels.
Root values are the last reported layout measurements, not a GPU readback.
`recordUs` measures callback work, not GPU presentation or displayed latency.

At most 512 data records of 1024 characters each are accepted, followed by one
explicit limit marker. A header and at most one previous file are retained;
each new trace rotates the current file to `window-surface.log.previous`.
Disk I/O runs on a task-owned daemon writer, not the AWT/render thread. Closing
does not wait on the UI thread; abrupt JVM termination may lose a queued tail.
Writer failure is reported once and disables tracing without throwing into the
render callback. Normal mode writes no surface trace. No learner content is logged.

These records describe picture recording and native event snapshots, **not
GPU Present, DWM animation or displayed frames**. They cannot prove smoothness.

## Verification

- Installed JDK 17 compiled both new Java helpers and their standalone checks;
  `WindowFrameLayoutChecks`: **7 groups passed**. Covers first-picture ordering
  with stale geometry, steady frames/scale changes, same-size native return,
  invalidation during callbacks, failure/retry/zero size, recursion guard,
  bounded trace/rotation/shutdown and writer failure isolation. These fixtures
  model the pinned path; they do not execute Compose or a Windows GPU.
- Design source contracts: **38 passed**; palette contracts: **8 passed**;
  localization: **7 languages × 714 keys passed**. Text and final archive/diff
  verification are recorded in the evidence file after execution.
- Kotlin/Gradle compilation, actual Windows interaction and GPU presentation
  cannot run here: no Gradle/Kotlin toolchain or Windows display is available.
  The JUnit entry points are supplied, but their Gradle execution is not claimed.

## Short Windows acceptance, still open

Close the existing app and Hot Reload session, replace the full source tree, then
start `dev-hot-reload.cmd` once. This installs the native delegate wrapper in a
fresh window. Check the new `surface-sync=before-picture-v1` marker, then a few
maximize/restore, left/top resize, F11 and minimize/restore transitions. No release
package or long Catalogue workflow is required.

If the defect persists, preserve the current `window-surface.log` and a short
screen recording of one transition before restarting again. The Hot Reload
default trace path is
`%LOCALAPPDATA%\Ikna\dev-hot-reload\profile\logs\window-surface.log`.
Matching child/root sizes would narrow the remaining investigation toward
native surface/presentation timing; it would not establish an OS limitation.
Mixed-DPI/multi-monitor and packaged-release acceptance remain separate open work.

## Delivery scope

One full ZIP, no source deletions. The 568-file source manifest gains seven files
(575 total): `WindowFrameLayout.java`, `WindowFrameTrace.java`,
`WindowSurfaceSynchronization.kt`, `WindowFrameLayoutChecks.java`,
`WindowFrameLayoutTest.kt`, this record and its JSON evidence. Main installs the
wrapper and root observer; owning desktop/plan/changelog/map records are updated.
Catalogue sources, workflows, scripts, Room schemas, dependency/build versions,
both skills and signing assets remain byte-for-byte unchanged from round 06.
The reported window defect remains open until real Windows acceptance.

## Owner acceptance, 2026-10-06

After delivery the owner reports: "пофикшено". The reported artifact's Windows
acceptance is closed on this owner report. No exhaustive transition counts,
mixed-DPI/multi-monitor evidence or packaged-release matrix were supplied; those
remain open. The next Catalogue round preserves all window source unchanged.
