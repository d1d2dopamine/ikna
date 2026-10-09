# Round 17 — reading cat and grey lighting

Owner-authorized visual round, 2026-10-08. Baseline:
`ikna-0.12-press-round-16-storage-measurement.zip`; all 627 source members matched
the workspace byte-for-byte before editing.

## Changes and reasons

- Android bottom bars no longer draw ambient grain. The shared bottom bar defaults
  to plain; desktop pane chrome explicitly retains its existing texture.
- Android and desktop deck headers replace the falling pixel paint with one
  shared, static reading-cat illustration. It owns a 48 dp square beside a weighted,
  single-line title; ellipsis prevents long titles from reaching the cat. Today
  remains in the next layout block. No pointer target, animation or spoken label
  is added for the decoration. Unused falling-paint code is removed; Home grain,
  empty-state lattices and desktop window strips retain their existing roles.
- Grey lighting previously authored almost-neutral accents for every palette;
  several accents followed the background hue instead of the dark accent hue.
  This made changing palettes look ineffective even though theme selection worked.
  Eleven coloured grey accents now keep their palette hue with moderate saturation.
  Zero remains neutral. Backgrounds, ink and muted text retain their authored values;
  existing shared control contrast logic derives states from the new accents.
  The existing danger guard also correctly steps aside for warm grey accents.
- Updated the owning design contract, active plan, documentation map and generated
  palette study. Card interactions, scheduler, catalogue data, database schema and
  application versions are outside this change.

## Artwork provenance and recolouring

Owner-supplied reference: `わちふぃーるど  iPhone_Androidスマホ壁紙(960×854)-1.jpg`,
actually 375×334 pixels. This is a clean illustration derived from that reference,
not a pixel-identical extraction or a claim of original character ownership.
Created with the built-in imagegen tool, no fallback. Selected output is a
1254×1254 RGBA PNG, 982,279 bytes, saved identically to:

- `shared/src/androidMain/res/drawable-nodpi/ikna_reading_cat.png`
- `shared/src/desktopMain/resources/drawable/ikna_reading_cat.png`

SHA-256: `80ac42002e3ec5692cf417c83d07bc8f463001b0cab54e3f26cf6fcbef300131`.

Final generation prompt:

> Create a transparent-background UI illustration derived from the supplied reading cat. Preserve its recognisable large ears, downward-looking eyes, tabby stripes, seated front-facing pose, paws holding the open book and feet. Remove the sofa, wall and ALL background. Clean confident hand-drawn contours, charming original character, not generic icon. Entire cat visible, no clipping, centered tightly in square canvas with about 5% transparent margin. IMPORTANT limited two-colour line-art asset for programmatic recolouring: every cat contour, stripe, face and book outline is pure BLACK; book interior panels are pure RED (#FF0000). All other regions, including cat body interiors and space around it, are TRANSPARENT, not white. No shading, gradients, shadows, gray tones, lettering or watermark. Smooth antialiased edges. Book red beneath black contours. Small UI asset legible at 48 pixels height.

The generated artwork includes white interiors despite the prompt. Those are
explicitly mapped to the current theme background, black contours to ink and the
red book to accent, using one shared Compose color matrix. Alpha is unchanged.
There is no runtime image processing, added dependency or network request.
Matrix semantics were checked against the official
[Compose ColorMatrix reference](https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/ColorMatrix).

## Verification actually performed

- `python3 skills/ikna-development/scripts/preflight.py .`: versions unchanged,
  Room 10, active 0.12 plan; this is not a build.
- `python3 tools/check_palettes.py`: 9 tests passed across all 24 authored
  palette/lighting combinations. Existing text 4.5:1, boundaries 3:1 and quiet-fill
  limits remain in force. New check guards hue identity, saturation and distinct
  grey accents; numerical palette checks do not certify appearance on a device.
- `python3 tools/check_design_parity.py`: 39 source contract tests passed;
  includes both header call sites, plain phone bar and identical RGBA cat resources.
- `python3 tools/make_palette_preview.py --check`: source-derived study current.
- `python3 tools/check_localization.py`: 7 languages × 732 keys passed.
- `python3 tools/check_text.py` and `git diff --check`: passed.
- Inspected the generated cat. Decoded PNG verifies transparent corners, opaque
  artwork and antialiased alpha. Offline arithmetic extracted the actual source
  matrix and verified 25 palettes (24 authored + one custom light palette), three
  artwork roles and four alpha values: all passed.
- Added `ReadingCatTest` for the actual Kotlin matrix and updated Android palette
  regressions. These Kotlin tests were **not executed**: local Java fails to load
  `libjli.so`. No Kotlin compilation, Android build or desktop runtime claim is made.

## Remaining acceptance

Use the existing desktop Hot Reload path, restarting its supervisor once after
updating this archive so the new classpath PNG is loaded. Check the header and
Today at narrow/wide widths, dark/grey and custom lighting; switch grey palettes
and confirm the book, selected controls and Today accents change together.
Android still needs a real build/device pass: plain bottom bars on pushed routes,
header at narrow width and larger font scales, and theme switching. Resource and
source checks cannot replace that pass. Catalogue v2's separate final-pack gates
remain open as recorded in Round 16; no corpus workflow was rerun for this UI round.
