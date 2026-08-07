# Changelog

Every released APK is built by GitHub Actions from the tag it is attached to.
Versions are `MAJOR.MINOR.PATCH` and live in `app/build.gradle.kts`; the release
workflow refuses to publish if the tag says something else.

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
  attaches it to the GitHub release automatically.
- **A store listing that lives in the repository.** Title, descriptions and
  per-version changelogs in Russian, English and Polish under `fastlane/`, plus a
  512 px icon rendered from the app's own launcher vector, so the picture in a
  listing cannot drift away from the icon on the phone. `docs/PUBLISHING.md`
  explains how to submit the app to IzzyOnDroid or F-Droid.

### Changed

- **The version is declared in the build file, not derived from CI.** It used to
  come from the GitHub run number, which does not exist anywhere else — a build
  made on another machine called itself version 1 and could not be published. Two
  lines at the top of `app/build.gradle.kts` are now the only place a version
  number exists, and the release workflow checks the tag against them.
- **Unsigned builds are possible.** `-Pikna.unsigned=true` skips the committed
  keystore, which store repositories that build from source require.

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
  `Documents/Ikna/`, and either file can be read back in.
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
