# Round 18 — first cat and developer profile lifecycle

Owner-authorized application round, 2026-10-08. Authoritative baseline:
`ikna-0.12-press-round-17-cat-gray-ui(1).zip`, CRC-verified with 632 source files.
The prior transient workspace was unavailable; implementation resumed from this
reattached archive. Both repository skills govern the full-ZIP delivery.

## Findings and resulting behaviour

- The chosen cat was already 1254×1254, but default low-quality sampling reduced
  it to a 48 dp slot. Desktop now uses Medium filtering for strong downscaling.
  Android additionally requests native mipmap levels: non-None Compose filter
  qualities alone map to the same native filtered paint there. The hint is
  renderer-dependent, so improved legibility needs device inspection.
  Both original PNGs retain SHA-256
  `80ac42002e3ec5692cf417c83d07bc8f463001b0cab54e3f26cf6fcbef300131`.
  No new drawing, background edit, enlarged slot or recolouring change is included.
- Rare previously opened automatically in DEV. It now starts closed on both
  targets. Return to normal remains visible above the fold so leaving DEV does
  not require finding the tools. Entry, exit and reseeding use shared, separate
  confirmation dialogs; cancel changes no bootstrap state.
- A profile choice only changes the next process's database. Existing containers
  retain their original immutable profile, which explained persistent DEV decks
  and markers when a restart was missing. Desktop restart callbacks are now
  required; a packaged/JVM successor waits for the old PID before claiming the
  lock/opening Room. Hot Reload requests its supervisor to restart after the
  child exits, without Enter; normal closes retain the existing prompt.
- Android's previous start-MainActivity-then-exit sequence could construct the
  old Application graph before process death. An unexported activity in a separate
  handoff process now waits for that death before relaunching MainActivity. Its
  Application skips dependency graph/background-work setup. The existing full
  wipe also uses this handoff. Failed startup/restart paths remain visible rather
  than being presented as a cosmetic mode switch.
- Confirmed scenarios are queued outside both settings stores and applied only
  during DEV startup before ordinary UI/repository work. Successful seeding clears
  the request; failure preserves it. Desktop refuses to open a partly reseeded
  sandbox after a startup failure. Overlapping developer clicks are disabled
  during restart/operations; launch failure restores the previous profile choice.
- Empty previously installed synthetic content, and desktop could add bundled
  packs afterwards. It now contains no decks/cards/history. Other scenario
  fixtures and actual learning-card behaviour remain unchanged.
- The ordinary-restrictions switch now states that it does not exit DEV. Reseed
  warns that DEV settings are reset as well. All seven interface locales include
  the shared confirmation/status text. Android no longer requests the real
  notification permission for a reminder setting inside DEV.

No learner-history merge, production reset, destructive migration, Catalogue
selection change or build-version bump is included. Room remains version 10 and
the stored application version remains 0.11.0-press.

## Verification

Local checks actually run:

- Contributor preflight: current version/schema/active-plan sanity report.
- Design source contracts: 42 passed. Palette contracts: 9 passed; all 24
  source-derived palette preview combinations agree. Classic-card DAO checks:
  5 passed; these are SQLite/source fixtures, not application runtime tests.
- Localization: seven tables with 735 keys each; repository UTF-8/NFC check.
- Hot Reload source wiring check. The six Windows native fixtures explicitly skip
  on this Linux host; this is not Windows runtime evidence.
- Baseline byte/path diff and exact manifest/CRC/byte verification when packaging.
  Both cat PNGs, signing identity, version files, schemas and existing skills are
  preserved from the input archive.

Added executable JVM regressions cover repeated bootstrap replacements and
failed writes, queued scenario persistence/corruption, restart argument handling,
and empty DEV installation preserving REAL data/settings. They were **not run**:
the available Java executable fails loading `libjli.so`; Gradle/Kotlin and a
Windows/Android runtime are unavailable. No tooling installation or long corpus
workflow was requested. Source checks cannot certify compilation, native process
handoff, hot watcher behaviour or visual legibility.

## Target acceptance still required

Stop the old session and launch `dev-hot-reload.cmd` once after replacing sources.
Check: Rare initially closed; cancel entry leaves REAL untouched; confirm entry
starts DEV; exit button is visible with Rare closed; cancel exit retains DEV;
confirm exit starts REAL and removes DEV decks/marker from the ordinary UI.
Changing restrictions inside DEV must keep DEV active. Confirm Empty, then reopen
the deck list and check it stays empty; other scenarios require confirmation and
restart. Verify normal close still offers Enter rather than restarting forever.

Inspect the original cat at normal DPI and a scaled display in dark/grey palettes:
same pose, book, slot and palette mapping; reduced aliasing is the acceptance goal.
Repeat entry/exit/reseed on Android and packaged Windows; these paths cannot be
accepted from desktop Hot Reload alone. No full Catalogue rebuild is needed.

## Primary references for the rendering decision

- [BitmapPainter](https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/painter/BitmapPainter): explicit filter quality, default Low.
- [Compose native Android paint mapping](https://android.googlesource.com/platform/frameworks/support/+/1e41492dedf72667309443c4929b4436e9b96c41/compose/ui/ui-graphics/src/androidMain/kotlin/androidx/compose/ui/graphics/AndroidPaint.android.kt): non-None qualities use native filtering.
- [Android Bitmap](https://developer.android.com/reference/android/graphics/Bitmap#setHasMipMap(boolean)): downscale mipmap hint, renderer-dependent.
