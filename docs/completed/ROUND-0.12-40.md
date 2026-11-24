# Round 40: touch feedback, reading space and accessible summaries

2026-10-12. Third approved interface stage, cumulative on Round 39.

## Changes

- Pressing a control on a touch screen no longer activates the decorative Signal
  Frame. Existing button/chip/toggle pressed fills remain; borderless controls
  receive a restrained local wash in the existing mark color. This wash has no
  new animation or gesture handler. Pointer hover, keyboard focus, selected
  navigation, nested hover ownership and reduced-motion behavior remain.
- Onboarding, study-card, finished and load-failure content can scroll when it
  exceeds the available height. A shared bounded column retains ordinary
  weighted spacing; the onboarding scroll position resets between slides.
  The authored onboarding text is unchanged.
- Card dragging recognizes horizontal gestures, leaving vertical scrolling to
  the reading viewport. The existing reveal-before-rating contract remains.
- Deck rows can grow for text and their Browse/details controls have 48dp hit
  targets. The deck mark remains 52dp. Shared icon/text/wide controls use at least
  48dp targets; labels can wrap. A toggle's artwork remains 32dp high within a
  48dp target. No new icon, palette or typography scheme is introduced.
- Statistics canvases receive concise localized descriptions of their existing
  data. Activity is summarized as active/total days; the hourly summary uses the
  repository's qualified best hour, preserving the small-sample distinction;
  the expanded forecast exposes an ordered list of scheduled counts. Missing
  data is not described as a zero score. Statistics labels can grow vertically.
- Voice help now says that speech runs on device and this screen imports an
  archive/folder rather than downloading a model. Catalogue and update network
  use are stated separately. The matching voice documentation is corrected.

No learning policy, schema, history, catalogue content/format or release changes.

## Verification

Evidence: [usability-round40](evidence/usability-round40/).

The final shared/desktop compilation succeeds: 58 shared and 135 desktop tests
pass with zero failures, errors or skips; counts are retained in that directory. New tests cover seven signal-feedback cases and four statistics
text/data/localization cases. Four viewport source guards check scroll hosts,
horizontal gesture handling, minimum targets and flexible rows. The existing
43 design checks were updated to the new explicit contracts, preserving hover
ownership, palette and focus checks. Nine palette checks and all seven locale
and UTF-8/NFC checks pass.

These are JVM and source tests. No actual Android/Windows GUI, font-scale,
pointer, keyboard or screen-reader run took place. The Android SDK agreement is
still pending. In particular, the viewport helper and gesture arbitration need
real-device acceptance; compilation alone cannot prove their feel or layout.

## Device acceptance checklist

- At ordinary and 200% text, short/narrow windows and phone landscape: read every
  onboarding slide, scroll to all actions, complete/retry preparation, read a long
  card and reach completion/retry controls without clipped text.
- Scroll a long card vertically without grading it; deliberately swipe left/right
  before and after reveal; check grading, drag cancellation and keyboard actions.
- Tap/hold/release/cancel on buttons, rows, toggles and palettes. Press feedback
  remains visible without a perimeter flash. Test an attached mouse and keyboard
  on Android as well as desktop; hover/focus/selection remain discoverable.
- Test every deck action near its edges: no neighboring action or row opening.
  Recheck at large text and with keyboard/Switch Access.
- Read statistics with a screen reader: concise summary, meaningful forecast
  values, and distinct empty/zero/small-sample states; no decorative-cell barrage.
- Recheck light/dark/custom palettes, angular/rounded controls and reduced motion.

Windows 11 non-smooth resize/maximize and absent minimize animation remain a
separate runtime investigation. The earlier white-flash report was retired by
its owner. No native-window fix is claimed here.

## Delivery

The full cumulative source package preserves README, signing identity, assets,
committed schemas and all previous compact evidence. The same corpus/sample
omissions remain; build outputs, runtime images, toolchains and disposable test
data are excluded. This stage does not claim all remaining audit items complete.
