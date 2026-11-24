# Round 41: first study choice and bounded catalogue rendering

2026-10-12. Fourth approved interface stage, cumulative on Round 40.

## Changes

- The final introduction slide identifies the optional bundled example: beginner
  English with Russian meanings. It explicitly allows builds without that asset
  and offers a direct route to the existing Catalogue for another language pair.
  All four authored slides remain; no questionnaire or availability promise is
  added. This choice does not silently alter installed decks' active settings.
- START enters the whole-day session on both platforms. Android keeps Home below
  that first destination, so Back does not reopen the introduction. The Catalogue
  choice uses the same preparation-before-completion sequence and preserves its
  destination when preparation needs retry. Ordinary returning navigation stays.
- Desktop keeps the introduction composed until its successful callback applies
  the destination. A settings update during completion must not cancel the
  callback or briefly open an unintended default session.
- Both catalogue screens render rows lazily with stable deck-ID keys. Intro,
  filters, loading/error/empty and footer content are separate items in one
  bounded viewport. Desktop retains its existing title/back frame, gutters and
  reading width. Download jobs and per-deck results remain screen-owned, so
  scrolling a row out of view does not cancel its installation.
- Brief informational notices use the platform accessibility manager's reading
  timeout, never a shorter time than the ordinary duration. Text and dismiss
  control flags are included; a polite live region exposes the message. The
  dismiss callback stays current through recomposition. Actionable load errors
  remain explicit persistent retry states from Round 38.

No catalogue format/content, learner history, scheduling, schema or release
change. No real user data was touched.

## Verification

Evidence: [usability-round41](evidence/usability-round41/).

Final shared/desktop compilation passes: 61 shared and 136 desktop tests, zero
failures/errors/skips. Linux release packaging and the bounded 11-process
packaged-profile harness also pass. Successful results are recorded there. New regressions
cover desktop first-destination state cleanup, missing optional starter data and
accessibility timeout behavior. Android route unit tests are supplied but cannot
run without the separately pending SDK agreement. Four first-study and four
lazy-catalogue source guards run alongside the existing design, viewport, palette
and localization checks.

The packaged harness exercises disposable isolated profiles, not graphical
interaction. Lazy-list performance, first-launch transitions, Android routing,
large-font layout, gestures and screen-reader behavior still need actual-device
acceptance. No Windows build or runtime test was performed in this Linux cloud.

## Current Windows acceptance request

Current owner report: Windows 11 resizing by edge drag and maximizing both feel
very laggy/non-smooth; minimizing lacks the expected animation compared with
GitHub Desktop. The previous white-flash report is outdated and is excluded.
The affected build/commit is still needed; a large recording is not required.

Use the existing Developer Mode `logs/window-surface.log` and fresh-process
startup markers described in [DESKTOP.md](DESKTOP.md). They measure geometry and
picture preparation, not frames actually displayed by GPU/DWM. Keep resize
latency and minimize/native-window animation as separate observations:

1. Record exact application build, Windows edition/build, display scale and
   renderer/startup markers. Start a fresh process; Hot Reload cannot validate
   native startup setup.
2. Compare several edge/corner drags with maximize/restore, then minimize and
   restore. Note whether only content or the whole window lags. Compare a small
   and a populated screen without changing data or renderer settings.
3. Repeat against the reference app under the same OS animation settings. A
   comparison establishes behavior, not the cause or which API must change.
4. Check other supported Windows versions only after the actual supported
   minimum/runtime floor is established. This source does not justify claiming
   universal support or a Windows 11 fix.

Existing atomic-bounds, restore acknowledgement, VSync and surface-layout work is
preserved. No speculative repetition or replacement of those patches is included.

## Delivery and remaining acceptance

This is a cumulative full source package, including all Round 38–40 fixes and
evidence, unchanged README presentation, signing identity, assets and committed
schemas. Existing corpus/sample omissions remain. Build output, toolchains and
scratch databases stay out of the ZIP.

Implementation is ready for the device checklist, not a completed visual/runtime
audit. Android build/device evidence, graphical accessibility/large-text/gesture
checks and the current Windows behavior remain open. The summary in
[INTERFACE-CHANGES-ru.txt](INTERFACE-CHANGES-ru.txt) lists the user-visible scope.
