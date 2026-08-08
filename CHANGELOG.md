# Changelog

Every released APK is built by GitHub Actions from the tag it is attached to.
Versions are `MAJOR.MINOR.PATCH`; the app also shows a build number, which is the
CI run that produced the file.

This project keeps one rule above all others: **the review log is never rewritten
and never dropped.** Any change that would touch it is listed here explicitly.

## Unreleased

### Added

- **Home screen widget.** One number — how much is left today — and a tap that
  opens the cards directly, skipping the deck list.
- **The reminder opens the session.** Tapping the daily notification lands on a
  card instead of the deck list.
- **System language support.** Android's per-app language picker now lists
  Russian, English and Polish. The in-app switch still wins when it is set to a
  specific language.
- **Screen reader names.** The hand-drawn marks (back, settings, statistics, add
  a deck, speak) had no names at all, so TalkBack skipped them silently. They are
  named now, in all three languages.
- **Tagged releases.** Pushing a `v*` tag builds a signed release APK and
  attaches it to the GitHub release automatically. The version inside the app is
  taken from the tag.

- **Speech speed and pitch.** Two steppers next to the voice picker, 50 to 150
  per cent, where 100 is whatever the phone itself is set to. Cards already
  spoken are spoken again at the new values.
- **The chosen font is used everywhere.** It reached the headings and the body
  text but not the small caps labels or the counters, which stayed monospaced, so
  a custom `.ttf` only ever half applied.

### Changed

- **Controls moved to the bottom of the screen.** The way out, the settings, the
  statistics and "add a deck" used to live in a bar across the top, which on a
  phone held in one hand is the only part of the screen a thumb cannot reach
  without regripping the device. Same marks, same order, bottom edge.
- **The statistics screen stopped explaining itself out loud.** Every figure had
  its paragraph printed underneath it permanently — nine figures, thirty-odd
  sentences of small grey prose. Each block now carries a "?" and hands the
  sentence over when it is asked for. Nothing was deleted; it is one tap away.
- **One number under each deck instead of three.** The row used to read
  "introduced 34 of 121, known 12" under a bar that already draws two of those
  numbers. It now shows how far through the deck you are, as a percentage.
- **Level names say what is being asked.** "вставить" described what the app does
  to the sentence rather than what you are being asked for, and "сказать" did not
  say out loud.
- **Cards leave the screen differently depending on the answer.** All four exits
  used to be one animation with the sign flipped. A card you kept is now flicked
  away light and lifts slightly; a card you lost drags and sinks, because it is
  going back into the pile. The scheduling is untouched — this is the answer
  restated in motion.

- **Both built-in decks rewritten in spoken language.** The chunks were
  textbook sentences — correct and lifeless. English went from 70 to 121 chunks
  (`no way`, `for real`, `my bad`, `hang in there`), Polish from 110 to 121
  (`no dobra`, `daj spokoj`, `jakos to bedzie`, `bez cisnienia`), both with a
  carrier sentence and a Russian gloss for every phrase. The packs are at version
  2. **Your review log is untouched:** the cards you have already met keep their
  schedule.

### Fixed

- **A font you picked was never actually applied.** Installing one reported "could
  not open the file" even when the file was perfectly good, and the setting stayed
  on the previous font. The success case was being turned into an error one line
  after it happened.

## 0.2.x

### Added

- Statistics screen: retention, minutes spent, median answer time, best hour of
  the day, and the words that keep slipping.
- Interface in Russian, English and Polish.
- Local speech: an installed engine reads a chunk aloud. Nothing is sent
  anywhere, and no voice model ships inside the app.
- Custom appearance: light, dark or four hand-picked colours, plus your own
  `.ttf` or `.otf` font.
- Backup and restore: the review log and the settings are written to
  `Documents/ikna/`, and either file can be read back in.
- Polish deck (`pl-ru-core`, 110 chunks), shipped switched off.
- Deck screen as the home screen; sessions open out of a deck.
- Swipe to answer: left for *not yet*, right for *got it*, up and down for the
  two in between. The buttons are gone.

### Changed

- The day starts at 04:00, not at midnight, so a session at 01:00 belongs to the
  evening it actually happened in.
- The visual language: flat right angles, hand-drawn marks, no Material
  components.

### Fixed

- A review log file picked in *Restore* could be mistaken for a settings file and
  silently reset the theme, the colours and the font.
- The session counter could grow while cards were being answered.

## 0.1.x

First working builds: FSRS-4.5 scheduling, the load governor, chunk packs, the
append-only review log, and the weekly export.
