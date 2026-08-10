# Changelog

Every released APK is built by GitHub Actions from the tag it is attached to.
Versions are `MAJOR.MINOR.PATCH`; the app also shows a build number, which is the
CI run that produced the file.

This project keeps one rule above all others: **the review log is never rewritten
and never dropped.** Any change that would touch it is listed here explicitly.

## 0.6.1

0.6.0 was never published: the first build of it failed to compile, and the fixes
below were found while getting it to a state worth releasing. Everything 0.6.0 was
going to contain is in this version, so there is one release note rather than two.

### Added

- **Six palettes, and a palette is not a theme.** The app used to have a dark
  scheme and a light scheme, which is what an app has when nobody decided what it
  looks like: near-black plus a blue accent is the default of every framework
  written in the last ten years. Colour is now one choice — Уголь, Библиотека,
  Чернила, Слива, Ноль, Нейтральная — and lighting is another. Each palette
  exists in both lightings and keeps its hue in both: the light version is tinted
  paper, not white with the colour drained out, which is what "light theme" has
  usually meant here and elsewhere.
- **Уголь is the default**, replacing the grey-and-blue pair. Warm near-black,
  bone-coloured ink, an ember accent. The launch window, the splash field and the
  home-screen widget were changed with it, so the first frame after tapping the
  icon is already the app's colour instead of a grey flash.
- **The palette is chosen by looking at it.** Settings shows the six as six tiles,
  each painted in the palette it offers and lit the way the app is lit right now:
  its own background, the wordmark in its own ink, a bar of accent beside a bar of
  muted. A row of names would have required switching to each one to find out what
  it was.
- **"Как в системе" lighting.** Dark, light, follow the phone, or your own four
  colours. Following the phone is a mode rather than a fifth palette, so it costs
  nothing to combine with any of the six. The wording is the same one the language
  section already uses for the same promise.
- **The warning colour belongs to the palette.** The single red used for "this
  cannot be undone" measured 3.5:1 on every dark background this app has ever
  shipped — the least readable colour in the product, on the one button with no
  way back. Each lighting now has its own, and when the palette's accent is itself
  a warm red the warning stops using colour at all and leans on the word and the
  frame, because a red warning next to a red everything is not a warning. Held to
  4.5:1 by a test, like every other pair.

### Changed

- **Reading aloud is marked beta and starts switched off.** The feature works, but
  how good it sounds is decided by whichever speech engine is installed on the
  phone, and nothing in this app can inspect that. A bad voice is worse than
  silence, so it is now offered rather than assumed — and never speaks before
  being asked to.

### Fixed

- **A deck could be switched on and still refuse to open.** Two separate causes,
  one symptom. The plan for today is cached, and turning a deck on did not throw
  that cache away, so the deck was active, its cards were due, and the number in
  front of them stayed zero until the next day rolled over. On top of that, both
  the day's block and each deck row were only clickable when they owed something:
  a deck with nothing due today, or one whose count had not been recomputed yet,
  was a dead row that swallowed the tap and said nothing. The plan is now
  invalidated the moment a deck is toggled, and both are always openable — an
  empty session says it is empty, which is an answer, unlike silence.
- **Nothing ever explained how to answer a card.** The first-run screens covered
  what the app teaches, what happens if you disappear and how little counts as a
  day — and then handed over a card without mentioning that it is swiped left for
  "не знаю" and right for "знаю". The words are printed at the bottom corners of
  a card, but they are only understood after the first answer has already been
  given, which is exactly one answer too late. There is now a fourth screen that
  draws the card and its two answers, using the same two strings and the same two
  colours the session screen uses. It is the last thing shown before the first
  card, and "ПРОПУСТИТЬ" lands on it rather than past it.
- **The technical screen is no longer in the app people install.** It was reachable
  from Settings → Advanced in every build, and it carried fifteen strings in three
  languages. It now exists only in the debug source set, with an empty stand-in
  compiled into release builds — R8 is switched off in this project, so a hidden
  button would still have shipped the screen. Nothing was lost with it: exporting
  the review log is in Settings → Д А Н Н Ы Е and exports the settings too,
  rebuilding the word layer is in Advanced, and the one question the governor log
  answers for a user — why nothing new arrived today — is already answered in a
  sentence on the session screen.
- **The database schema history is checked, and CI keeps it in the repository.**
  Room writes the schema of every version into `app/schemas`, and those files are
  the only way to tell an upgrade that preserves four months of answers from one
  that drops them. They had never been committed; CI merely uploaded them as an
  artifact. A build regenerates the current one, so no test can catch the absence
  — git can, and now does: the run fails if anything under `app/schemas` is
  untracked or changed. A unit test checks the part git cannot, that the schema on
  disk agrees with the migration meant to produce it and that every version step
  has a migration at all.
- **The launcher icon was still the old colour.** Its field was `#110F0F`, a colder
  near-black inherited from the original artwork, while the launch window, the
  splash and the widget had all moved to Уголь's `#17100C`. On a home screen the
  icon, the splash and the app's first frame are read as one surface, and this one
  was visibly grey next to the other two.
- **The restore path did not compile.** `rebuildFromReviews`, rewritten in 0.5.0 to
  batch its queries, wrapped them in a `Sequence` — which stores its lambda for
  later, cannot be inlined, and therefore cannot contain a suspending call. The
  chain is now an ordinary list chain, which is inline and keeps the coroutine
  context. Same queries, same batches, same result; it just builds them now
  instead of later. This was the only compile error in the tree and it was in the
  file that rebuilds the word layer after a restore.

## 0.5.0

### Changed

- **Two answers instead of four.** A card is thrown left when the chunk is not
  known and right when it is; up and down no longer mean anything. The vertical
  pair asked *how well* the answer went, which is a second decision stacked on
  the first one, and it collided with the hand: a thumb travelling right also
  travels up, the axis was chosen by whichever displacement was larger, and "I
  knew it" regularly landed as "that was easy" with different scheduling and no
  way to tell. Both grades stay in the data model and every review ever recorded
  stays readable — the interface simply stopped asking a question it could not
  ask well.
- **One card, one look.** The variant that marked a card returning from the
  amnesty pool is gone. It kept score of an absence on the material the user was
  in the middle of remembering, which is the one place this app had promised not
  to.
- **The card can be answered with TalkBack on.** Both verbs are also exposed as
  accessibility actions; an interface whose only verb is a swipe has none.

### Fixed

- **The words explaining the two sides slid off the screen.** They were drawn
  inside the card, and the card is the object that travels: pulling it to the
  right carried the label that explains the right side off the right edge,
  exactly when it was being looked for. The label under the thumb was the first
  thing to disappear. Both words now belong to the screen instead of the card,
  in the bottom corners, and stay put while the card slides underneath them.
  They fade out for good once the movement has been used enough times.
- **The gesture stopped working halfway through a drag.** The touch area moved
  with the card, so a long drag left the region that was listening to it.
- **A card whose back had not been seen could not be pulled.** Dragging one now
  turns it over — the same act as tapping, done with the hand that is already
  moving — and that gesture can never grade it: the answer needs a second,
  deliberate throw.
- **The line that says how to turn a card over was computed and never drawn.**
  The first card of a first session explained nothing at all.
- **New material arrived once a week instead of every day.** The rule meant to
  hold new chunks back after a *skipped* day compared "days since the last
  session" against one — but the plan for a day is built before that day's first
  answer exists, so studying yesterday evening and opening the app this morning
  already counted as a gap. The gate fired on every ordinary day of use, the
  allowance was zero, and the only new material that ever got through was the
  safety valve's single chunk a week. A skipped day is now a day with no answers
  in it, which is two calendar days.
- **The day's new chunks could be crowded out of the session.** The plan
  introduced new cards and then filled the same session from the due queue,
  which on a busy day returned a full session before a single new card was
  reached: they were introduced, counted, and not shown. New cards are now
  reserved a place first, and the rest of the session is built around them.
- **Coming back after a break lasted one session.** `returnModeDays` was
  declared, documented and never read, so the softer capacity ended the moment
  one session happened. It now covers the days after a return as well.
- **An idle-time adjustment quietly rewrote card history.** Skipping days
  shifted every schedule forward and falsified `lastReviewAt`, which is an input
  to the scheduler, so the interval after a break was computed from a review
  that never happened. Removed; the amnesty pool already handles absences, and
  handles them without inventing data.
- **The export could write a file it could not read back.** Lines were built by
  string concatenation, so an imported deck whose ids contain a quote, a
  backslash or a newline produced a log that no longer parsed — discovered, at
  the earliest, on the day someone needed it. Export and restore now share one
  serialised format, tested for both.
- **Restoring on a phone that had already been used mixed two sets of row ids.**
  Ids from the file were inserted verbatim, so they collided with existing rows
  and could attach an old retraction to an unrelated answer. Rows are inserted
  unnumbered and the undo trail is re-pointed at the ids assigned here; a file
  imported twice is now a no-op.
- **The word layer was rebuilt wrongly after a restore.** It replayed the raw
  log, counting retractions — stored with rating 0 — as failures, so undoing an
  answer damaged the words it contained instead of reverting them.
- **Cloud backup is off.** Android was uploading `ikna.db` — every phrase
  studied, every rating, every timestamp — to the user's Google account, which
  contradicts PRIVACY.md. Direct transfer to a new phone still works.
- **Accuracy was stored as a rounded average of averages**, drifting a little
  further from the truth with every answer. Correct answers are counted as an
  integer and the percentage is derived.
- **"Plan completed" was recorded for any day above the one-card minimum**, which
  is what feeds the acceleration rule, so the ceiling could rise off days that
  were nowhere near the plan. It now compares against the plan actually built.
- **The evening reminder drifted later every day.** A periodic worker repeats 24
  hours after the previous run, and Doze delays runs, so the time slipped. Each
  run now re-aims the next one at the chosen clock time.
- **The widget showed yesterday's number until the app was opened**, on a widget
  whose purpose is to be read instead of opening the app. The nightly job now
  refreshes it.
- **The nightly plan ran at whatever time the app was installed.** It is now
  anchored just after the study day rolls over.
- Restore no longer runs one database write per answer; a long history replays
  in batches instead of tens of thousands of round trips.

### Added

- Tests for the two gates above: an ordinary morning is not a skipped day, a
  real skipped day still has to be warmed up, and return mode outlives the
  session that ended the gap. Plus a round trip of the export format through an
  id full of quotes and newlines.
- CI keeps the Room schemas as an artifact, so a migration can be checked
  against the version it migrates from.

### Added — earlier in this cycle

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
