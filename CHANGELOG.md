# Changelog

Every released APK is built by GitHub Actions from the tag it is attached to.
Versions are `MAJOR.MINOR.PATCH`; the app also shows a build number, which is the
CI run that produced the file.

This project keeps one rule above all others: **the review log is never rewritten
and never dropped.** Any change that would touch it is listed here explicitly.

From `0.1.0 proof` onward a version is a number **and an epoch word**, written in
lower case like the app's own name. The number counts releases inside the epoch;
the word says which epoch the build belongs to. Both words come from printing:
`proof` is the copy that is read back and corrected before the press runs, and
`press` is the run itself — the next era, which opens only when `proof` has
nothing left to correct. Tags replace the space with a dash, so `0.1.0 proof` is
tagged `v0.1.0-proof`.

## 0.2.0 proof

Still the proof epoch: the app is being proven, not polished. This one is about
the two places where it was asking more of the reader than it meant to.

### The phrase is marked inside the sentence

A recognition card shows a whole sentence and asks about one phrase in it. The
phrase was stored with its exact position from the very first import, and no
screen ever drew it, so the card was quietly asking two questions: which words
are being tested, and do you know them. Only the second one was ever meant to
be asked, and only the second one belongs in the schedule -- a card missed
because the eye was on the wrong word was recorded as a phrase forgotten, and
every interval after that was computed from it.

The phrase now carries a rule under it, in the colour of the palette, on the
front of a recognition card and on the back of a production one. Not bold --
bold changes the shape of the word being learned. Not a highlight -- a block of
colour in the middle of a line is harder to read, not easier. The sentence
around it stays untranslated, which was never the bug.

### Decks can be told apart

A deck can be given one of twenty-four icons and one of eight colours, on its
own page under *Appearance*. The square on the list takes them, and keeps doing
everything it already did: the fill still means the deck is on, the fading still
means it is off, the dot still means it owes cards.

This is stored beside the theme, not inside the deck. An icon is how one person
tells their own decks apart; a deck sent to somebody else arrives as cards and
nothing else. It also means no database migration for a decoration, and a deck
file that stays a list of phrases.

### The bottom bar can be moved to the other hand

Two switches in *Settings -> Appearance*:

- **The ikna mark** -- the one place the app says its own name. It earns its
  room on the first day and stops earning it later; switched off, the row goes
  to the controls.
- **For the left hand** -- the bar is mirrored. The plus takes the corner the
  left thumb rests in and the two everyday marks stay together at the other
  end. It is the same layout read the other way, not a second one to maintain.

What was asked for and deliberately not built: dragging the marks into cells
like a game's controls. There are three of them. A saved arrangement would also
have to survive every button added after it, and it would break on each one.

## 0.1.0 proof

The same app as 0.6.1, renumbered, plus everything done while this proof was
being brought to a state worth releasing. Nothing about the scheduler, the
governor, the packs or the database changed here.

### Added

- **The neural voice now reads with a model you add, and no model ships inside.**
  The `-voice` APK went from 466 MB at full precision, then 150 MB quantised, to
  about 31 MB: the sherpa-onnx runtime and nothing else. All those megabytes had
  bought exactly one language, and not one that any deck in this app is written
  in. A Kokoro or Piper folder is now added from the file picker, in whatever
  language is actually being learned. The app still holds no internet permission
  and downloads nothing.
- **A screen that says who is speaking.** Settings -> Voice names the engine
  reading the cards right now, proves it out loud with a test button, and shows
  the model's name, type, size and language. Before this, a 450 MB install and a
  21 MB install looked and behaved identically, which is how a voice that never
  worked went unnoticed.
- **Every refusal is a sentence.** A picked folder is judged before a byte is
  copied, and each of the six ways it can be wrong -- not a model, one level too
  high, several models at once, no `tokens.txt`, no `voices.bin`, nothing to
  pronounce with -- says so in those words instead of failing silently.
- **Adding, replacing and removing a model**, with a running count of files while
  it copies. The copy lands in a staging folder and is swapped in only once it is
  whole, so a dead battery cannot leave half a model looking installed.

### Fixed

- **Decks you made yourself are read aloud.** The importer stores them with the
  language `custom`, because a file of phrases does not declare one. The engine
  was asked whether it spoke `custom`, answered no, and every deck anybody
  actually created fell through to the phone's voice -- or to silence, where the
  phone had no voice for that language. An undeclared language is now answered
  with the model that is installed.
- **A model that is too slow for the phone gives way to the phone's own voice**
  after three long renders, rather than freezing the card each time.

### Changed

- **Versions are now an epoch plus a number.** `appVersionName` is
  `0.1.0 proof`. The old `0.x` line counted the app being assembled piece by
  piece; the `proof` epoch counts a finished app being brought to a releasable state, and
  continuing from `0.6.1` would have implied that the two scales are comparable.
  They are not, so the number restarts and the word makes the reset explicit.
- **`appVersionCode` does not restart.** It is now `100010000` — an epoch offset
  of `100000000` plus the version. Android refuses to install an APK whose version
  code is lower than the installed one, and the only irreplaceable thing in this
  app is the review log inside that installation. The version *you read* reset;
  the version *Android compares* never has and never will.
- **The release workflow slugifies the tag.** A git tag cannot contain a space, so
  the tag check now compares the pushed tag against `appVersionName` with the
  space replaced by a dash: `0.1.0 proof` -> `v0.1.0-proof`. A mismatch still
  aborts the release before anything is built or published.
- **One bilingual README.** `README.md` now carries English on top and a full
  Russian copy below it, reachable through a language switch at the top of each
  half. It has badges, a download link to the first release APK, and a section
  explaining what proof and press mean. `README.ru.md` is gone: two files drifted
  apart, one file cannot.

### Added

- **A screen behind the plus, and a prompt that writes the deck for you.** The
  plus used to open the system file browser with no filter on it, so the first
  thing anyone saw after deciding to add a deck was their camera roll — and the
  only format the app accepted was one JSON object per line with character
  offsets in it, which nobody writes by hand. Adding a deck was the hardest thing
  in an app built for people who lose the thread during setup. Now the plus opens
  a screen with a four-line explanation, a **Copy the prompt** button, a **Save
  the prompt as a file** button, a field to paste an answer into, and a file
  picker as the last option rather than the only one. The prompt is three
  kilobytes of English addressed to a model: it states the format, the rules and
  the failure modes, and leaves the language and the topic to the person sending
  it. The routine goes to the machine; the choice stays with the human.
- **A deck format a person can read.** Three columns separated by a bar — phrase,
  a sentence containing that phrase, translation. Token splitting, character
  offsets and frequency order are worked out on import, so the file carries only
  what a person could plausibly write. `.jsonl` packs still import; which of the
  two shapes a text is in is decided by reading it, not by its file name.
- **An import report that says what went wrong.** Bad lines are skipped and
  counted as before, but the first one is now named with its line number and the
  reason — not three columns, empty field, phrase missing from the sentence, too
  long, duplicate. "0 imported" with no explanation was a dead end.

- **The logo, in the app, wearing the palette.** The wordmark now sits at the
  left end of the bottom row on the deck screen, which until now was the only
  product in the world that never said its own name anywhere inside itself. The
  letters are the artwork's own pixels, resampled and tinted, never traced into
  paths — the same rule the launcher icon has always been under. The square over
  the i is not part of the bitmap: it is erased from the asset and drawn by the
  app in the accent colour, so the mark answers whichever of the nine palettes
  is on. It is not pressable, and the asset is checked before release for the
  two things that would fail silently — a stale aspect ratio, and a dot left in
  the bitmap under the one the app draws.

- **Three more palettes, nine in total.** Роза — a wine-dark field and a rose
  that reads as a highlighter rather than a ribbon; its accent sits at hue 333
  because the danger guard starts at 34 and an accent that trips it has to hand
  its warning colour back to the ink. Иней — the only scheme in the set with
  nothing warm in it anywhere, where every other one answers a cool background
  with a warm accent. Фосфор — a phosphor tube, and the one that breaks the
  pattern on purpose: the ink itself is the colour, so the whole surface is a
  single hue instead of a neutral with one coloured thing on it. All three are
  authored in both lightings and all three pass the 4.5:1 test on ink, muted,
  accent and danger, in both. The picker needed no change: it lays the palettes
  out three to a row, and nine is three rows.
- **`docs/GRADING.md`.** A design note, not a feature. It records why the card
  has one swipe axis instead of four, what the two-grade UI costs in scheduling
  resolution, and the decided replacement: derive `HARD`/`GOOD`/`EASY` from
  answer latency and the existing peek signal rather than asking the user for a
  self-assessment. It also fixes the constraints in advance — per-device
  calibration with no server and no internet permission, percentile thresholds
  over a rolling window, `GOOD` as the fallback whenever a signal is missing, a
  bounded effect on intervals, and log replay as the test that decides whether
  any of it ships. Nothing in it is implemented in this release.

### Fixed

- **The release build failed on a test that could not compile.** `WordmarkTest`
  read the wordmark PNG through `javax.imageio`, which exists in an ordinary JVM
  and not in an Android unit test: those compile against `android.jar`, which
  shadows the JDK. The test now reads the PNG header itself — signature, width,
  height — and checks the accent square's rectangle against the constants the
  composable draws with. Less is verified than before; all of it compiles.
- **The file picker offered files that were not decks.** It asked for `*/*`, so
  it listed photos, music and video, and picking a video read the whole file into
  a string and took the process down with it. It now asks for text, JSON and
  unnamed binary only, and refuses anything over four megabytes before opening it.
- **The empty deck list pointed at the wrong corner of the screen.** It had said
  the plus was at the top since the day the bar moved to the bottom.

- **Six broken characters in the Russian strings.** `StringsRu.kt` carried six
  U+FFFD replacement characters, left over from an encoding round-trip, inside two
  visible strings: `set.064` and `dbg.006`. Both now read correctly
  (`Спрятано не потому...` and `Слой слов пересчитан`). The whole repository was then
  scanned for replacement characters, mojibake, BOMs, CRLF line endings,
  non-breaking and zero-width spaces and stray control characters; nothing else
  was found. The typographic dashes and quotes throughout the sources are
  intentional and were left alone.

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

- **The introduction card is gone; there are two kinds of card left.** A chunk met
  for the first time used to be shown rather than asked: the phrase, its meaning
  and its sentence at once, with a tap to move on. It sounded right and worked
  badly. Every new chunk arrived twice — once as a reading, then, seconds later,
  as a question whose answer was still on the screen behind it — and the app filed
  that second pass as a genuine first success and pushed the chunk days into the
  future. Now every card is a question with the answer one tap away, and a first
  miss costs nothing. New chunks still arrive at the front of the day and still
  come back once more inside the same session, except that the second pass is now
  an actual recall instead of a second reading. Expect first answers on new
  material to be mostly "don't know" and first intervals to be short: that is the
  schedule being honest, not a regression.

- **Reading aloud is marked beta and starts switched off.** The feature works, but
  how good it sounds is decided by whichever speech engine is installed on the
  phone, and nothing in this app can inspect that. A bad voice is worse than
  silence, so it is now offered rather than assumed — and never speaks before
  being asked to.

### Fixed

- **New chunks alternated between a reading and a question without end.** The
  builder kept introductions apart by putting two of them between every three
  repetitions, and quietly did nothing when there were no repetitions to space
  them with — which is exactly the state of a fresh install, where every card is
  new. The result was introduction, question, introduction, question for the whole
  first session, and for every session after it until reviews began to arrive.
  Removing the introduction card removes the loop with it.

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
