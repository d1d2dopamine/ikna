# Changelog

Every released APK is built by GitHub Actions from the tag it is attached to.
Versions are `MAJOR.MINOR.PATCH`; the app also shows a build number, which is the
CI run that produced the file.

This project keeps one rule above all others: **the review log is never rewritten
and never dropped.** Any change that would touch it is listed here explicitly.

From `0.1.0 proof` onward a version is a number **and an epoch word**, and tags
replace the space with a dash: `0.1.1 press` is tagged `v0.1.1-press`. What the
words mean and what a number promises inside an epoch is written down once, in
[`docs/VERSIONS.md`](docs/VERSIONS.md).

## 0.6.0 press

The same history, scheduled by the current memory model.

### FSRS-6

- The item scheduler moves from FSRS-4.5's 17 parameters to the official
  FSRS-6 default set of 21. The trainable forgetting-curve shape, the damped
  difficulty update and the FSRS-6 short-term stability formula are implemented
  directly in Kotlin.
- A repeat under 24 hours uses the short-term model. This matters here more than
  it does in a conventional deck: a card answered `AGAIN` deliberately returns
  to the same Ikna session, so same-day learning is the ordinary path.
- Desired retention, the one-day interval floor, the 04:00 study-day boundary,
  the governor and the one-way component prior do not change.

### The migration

- On the first launch, every card that still exists is replayed through FSRS-6
  from the append-only review log. `stability`, `difficulty` and `dueAt` are
  derived again; decks, chunks, reviews and statistics are not rewritten.
- Reviews belonging to a deleted deck stay in the log and remain part of the
  statistics, but do not resurrect the deleted cards. Introduced cards with no
  answer are preserved as-is; their first answer replaces the prior with a real
  FSRS-6 observation.
- The card replay and daily-plan invalidation are one Room transaction. The
  migration marker is written only after it commits; a killed process safely
  repeats the same deterministic replay on the next launch instead of leaving
  a database split between two schedulers.
- Screens and the nightly worker share one launch gate. Nothing can build a
  plan or answer a card while the derived card table is being replaced. A failed
  replay deletes nothing and offers a retry.
- Restoring an exported review log now derives its cards through FSRS-6 too.

### Verification

- Golden vectors cover all four long-term ratings and same-day ratings against
  py-fsrs 6.3.2 with the official defaults.
- Migration tests pin down idempotence, preservation of unanswered cards and
  the rule that old reviews never recreate a deleted deck.

## 0.5.0 press

Decks you did not have to write.

Until now a deck came from one of two places: the two that ship with the app, or a
model you pasted a prompt into. The second one works, and it is still the way to
get a deck about exactly what you want — but it asks somebody to go somewhere else,
ask a machine for text, and trust text that no one can check. A model that invents
a sentence cannot be looked up. That is the hole this release fills.

The plus screen now has a **catalogue**: finished decks cut out of open corpora on
the build server, listed with their licence, downloaded with a progress bar, and
imported through the same path a pasted deck already went through. Every card in
them names the sentence it came from, by number, on a public site.

### The catalogue screen

- One small index is fetched when the screen opens. Everything after that — the
  language pair, the topic, the level, the search box — is filtered on the phone,
  so trying ten combinations still costs one request.
- A row says the title, the pair, the card count and the size in megabytes. Tapping
  it unfolds the licence, who is credited, and which corpora the deck was cut out
  of, **before** the download button.
- The download draws the same band and the same percentage as the update download
  in `0.4.0 press`, because it is the same widget and the same arithmetic.
- The list not arriving is a sentence, not a spinner that never stops: the screen
  says the list did not come, offers to retry, and offers the catalogue page in a
  browser. Nothing else in the app is affected.

### What a deck brings with it

- **The licence travels with the deck.** It is in the index, on the screen before
  the download, and appended to every card's meaning as a `— Tatoeba #12345` line
  — the same source mark a hand-written deck's fourth column produces, so it
  survives an export and a share.
- **A catalogue deck cannot overwrite yours.** Its identifier starts with
  `catalog-`; a deck you imported yourself starts with `user-`. Downloading the
  same deck twice replaces it instead of leaving two copies.
- **The phrase was cut out of its sentence, not written beside it.** The pipeline
  stores the offsets it cut at, so the one fault a pasted deck sometimes has — a
  phrase that is not in its sentence — cannot exist in a catalogue deck, in any
  language.

### Eight languages taught, ten understood

The matrix is deliberately lopsided. A phrase is cut on word boundaries, so the
language being **learned** has to be one written with spaces: English, Russian,
Polish, Spanish, French, German, Italian, Portuguese. A translation is shown whole
and never cut, so the language the **meanings** are in can also be Chinese or
Japanese.

How well a pair is served is measured by the pipeline and published with the
catalogue — `full`, `thin`, or absent — rather than promised in a README. A deck's
size is a result of what the sieve kept, not a number anybody chose.

### The pipeline

- `tools/catalog/build_catalog.py` reads the Tatoeba exports, keeps only direct
  translations, picks the rarest word in an otherwise ordinary sentence as the
  card's phrase, cuts it out with offsets, and writes decks in the format the
  importer already reads.
- `tools/catalog/make_sample.py` writes a hundred and twenty invented sentences in
  Tatoeba's shape, so the whole pipeline runs end to end in about a second.
- `.github/workflows/catalog.yml` runs that sample first and checks that every
  card's phrase sits exactly at its stored offsets, then builds the real catalogue
  and attaches it to one release called `catalog`. That release is explicitly not
  marked as the latest one, so the update check still finds versions and not decks.
- Wiktionary is read at build time only, to tell the forms of one word apart. None
  of its text goes into a deck, so a deck carries Tatoeba's licence alone.

### The plus screen, rearranged

- The catalogue is the first thing the screen offers now, and the model way is
  folded behind one button underneath it. Adding cards is no longer a prompt, a
  paste and a hope that nothing came back malformed — that path still exists,
  unchanged, for the deck the catalogue does not have.
- The language of the deck moved down to where a deck actually arrives, so a file
  somebody sent you can still say which language it is in.
- A catalogue row whose deck is already installed says **ALREADY DOWNLOADED** and
  offers a small *download again* instead of the download button. The list of what
  is installed is read from the deck table every time the screen opens, so a deck
  deleted on the decks screen becomes downloadable again.
- The updater now recognises the actual `-legacy32.apk` name produced by CI. A
  64-bit phone always chooses the ordinary APK regardless of the order in which
  GitHub returns the two release assets; `-32bit.apk` remains understood for old
  releases.
- Chip rows wrap by measured width instead of four to a row. Four was a guess about
  how wide a word is, and it cut the last letters off «продвинутый» inside its own
  border.
- A deck name is cut to 40 characters on the way into the database, on a word
  boundary where there is one, and the deck list draws it on one line. A catalogue
  name three lines long used to push the progress bar down out of its row and leave
  every row in the list a different height.
- A deck named in the bundled manifest whose file is not in the build is skipped
  instead of stopping the other decks from installing.
- The Polish deck (`pl-ru-core`) is no longer shipped inside the APK: 121 chunks
  against the catalogue's Polish decks is not worth carrying for everybody. Nothing
  is removed from a phone that already installed it.

### What did not change

No account, no identifier, no statistics. The second network request exists only
when somebody taps a deck, and it is a GET for a static file, the same shape as the
update check. The review log is untouched, the database schema is unchanged, and
nothing in the session, the governor or the grading was modified by this release.

## 0.4.0 press

A pasted deck arrives whole, and the app can now update itself.

Two things, and they are both about the same failure: something was wrong, it had
already been fixed, and the person holding the phone had no way to find that out.
`0.3.0 press` shipped a paste bug that was fixed the same evening and the only
reason anybody knew is that they asked. So this release fixes the paste path, and
then fixes the reason it took a day to reach anyone.

This is the first version that opens a socket, and that is a change to what the
app promised rather than a feature added to it. Every claim about the network in
the README and in `docs/` has been rewritten to match; `docs/UPDATES.md` is the
whole of it.

### The update window

- Says what is available, in the order the questions arrive: the installed version
  and the offered one either side of an arrow, the download size, then what
  changed.
- The notes are the release's own text, fetched with it -- the build being offered
  knows what is in it and the installed one does not. Badges, rules and link
  syntax are stripped, and the text scrolls inside a fixed box so a long release
  cannot push the buttons off the screen.
- Two small words, `skip` and `update`. Skip silences that one version and the
  next release asks again; tapping outside behaves like skip without remembering
  it.
- `Update` downloads the file here, in the window that offered it, and hands it to
  the system installer. What that looks like is below.
- A phone with no 64-bit ABI is offered the 32-bit file. The speech runtime is
  native code, and the wrong APK installs and then fails at the first sentence.

### Settings -> Updates

- The same check on demand, for the case the window was waved away and the mind
  changed an hour later. Here the skipped version is ignored: pressing the button
  is the change of mind.
- Shows the installed version, the skipped one if there is one, and a link to the
  releases page for when the check cannot work at all.
- One switch turns the whole thing off, and off it opens no socket at all.

### What is sent

- Nothing. One GET with no body, at most once a day while the app is open, or when
  the button is pressed. No account, no identifier, no statistics, no card, no
  answer. The version is in the user agent so a broken release can be recognised.
- The review log is untouched by all of this: still append-only, still exported by
  hand to `Documents/ikna/`, still with `allowBackup` off.
- Short timeouts, a capped reply, and every failure returning nothing. A check that
  failed is not an event and says nothing.

### Downloading it

- Pressing `update` no longer sends anybody to the browser. The two words are
  replaced, in the same window, by a band that fills, the percentage beside it and
  the megabytes under it -- `18.9 / 40.2 MB` -- because a percentage of an unknown
  size tells somebody on a metered connection nothing.
- The browser was the deliberate choice until now, and the argument for it left out
  the person on the other end: an app that leaves, a file that lands in a folder
  they then have to find, and a notification that looks like every other one they
  ignore. The update was available and nobody installed it.
- When the file is whole the system installer opens by itself and puts the new
  version **over** the old one. Every release is signed with the same committed
  key, so the review log, the decks and the settings are all still there
  afterwards; that is what `docs/KEYSTORE.md` has always been for.
- The percentage cannot reach 100 before the last byte is written, so "100%" and
  "the installer is opening" are one moment rather than two seconds apart.
- A download that stopped short is thrown away rather than handed on. A truncated
  APK with the right name is refused by the installer, and that reads as "the
  update is broken" instead of "the network dropped".
- The file goes into the app's own cache, under `updates/`, one at a time, cleared
  before the next attempt. Nothing is resumed and nothing accumulates.
- While bytes are arriving a tap outside the window does nothing: a download
  cancelled by a misplaced finger at eighty percent is the worst thing this window
  could do. `Cancel` is there, and cancelling closes the socket rather than only
  the display.
- Android asks, per app and on a settings screen, before anything may be
  installed from this source. That screen is opened at the moment it is needed --
  file already downloaded, window saying what it is for -- and never on first
  launch. Granting it and pressing `install` again costs no second download.
- A failure says which way it failed and keeps both ways out within reach:
  `retry`, and `in the browser`. The app fetching the file is a convenience and is
  not allowed to become the only way to get it.
- The same download runs from **Settings -> Updates**, with the same band and the
  same percentage. Two ways in, one thing happening.

### Pasting a whole deck

- **Paste from clipboard**, the button under the field. A hundred kilobytes of
  table handed to a phone keyboard came back cut short and with its line breaks
  flattened into one line; the importer then read the whole deck as row one with
  six hundred columns and refused all of it -- "line 1 has not got three
  fields", about a paste that was, field for field, correct. This button reads
  the clipboard directly, so nothing passes through a keyboard at all.
- A paste that **did** come through the keyboard is now rescued: on create, if the
  text in the field is one line carrying a deck's worth of separators while the
  clipboard still holds the same deck with its line breaks intact, the clipboard
  is what gets imported. Silently -- the person did nothing wrong and has nothing
  to fix.
- Where that cannot be done, the importer **puts the line breaks back itself**: a
  line holding six or more fields is read as the rows it plainly is, three or four
  columns wide. Twelve fields and twenty-four divide by both widths, and there the
  count is not allowed to decide: the wrong width shifts every field one place
  along and turns a whole deck false without refusing a single line. The deck's own
  rule decides instead -- a phrase appears inside its own sentence -- and the width
  that holds on more rows wins.
- That rescue now works **line by line**, which is the whole of the fix. A keyboard
  does not flatten a text evenly: it keeps a break here and there and glues
  everything between them, so three hundred rows arrive as a handful of enormous
  lines rather than as one. Only a text of a single line was rescued before, so
  that far commoner case imported the one line that happened to hold three fields
  and refused the rest -- "1 card added" out of three hundred.
- A line is only split when the result reads like cards, on at least two rows and
  on at least half of them. A line with a stray bar in it stays one line and is
  reported as the mistake it is, rather than being cut into halves of sentences and
  imported as meanings.
- And where even that fails, the message says **which** thing went wrong: the line
  breaks were lost, press paste from clipboard. Not "line 1 has not got three
  fields" printed under three hundred rows that were all correct. It is said about
  the line it happened to, whether the text arrived as one line or as four.
- The pasted text is **folded away**. In place of the wall of text: one line saying
  how many lines and how many characters arrived, and **SHOW THE TEXT** beside it.
  Opened, it shows the first forty rows -- enough to see that the columns line up
  -- and says how many more there are. That is a fixed cost: opening the preview on
  ten thousand rows costs what opening it on forty costs.
- Nothing large is held in an editable field any more. Laying a megabyte of text
  out on the main thread, on every keystroke, is what made this screen take a
  minute to scroll to its own create button.
- The paste ceiling was a quarter of the file ceiling: four megabytes from a file,
  four hundred thousand characters from a paste. A ten-thousand-row deck is about
  a megabyte and a half, so a large paste was cut in silence. Both ends now hold a
  whole deck, and a paste that still does not fit **says it was cut** instead of
  installing a deck missing its last two hundred cards.

### The wrong-card action

- It is written as an order now: **MARK AS WRONG**, upper case, the way this app
  writes every other action. It read "this card is wrong" in the same muted grey
  as the captions around it, in the bar under every card that had been turned
  over -- so it looked like a verdict the app had passed on the card, on card
  after card, and the reasonable conclusion was that every card in the deck had
  been marked. Nothing was ever marked by it without a tap; Settings -> Data
  counts what actually was, and puts it all back with one button.

### The deck row

- The three dots moved to the **left** of the switch. They had been put on the
  right, which took the outer edge the switch has held since the first version --
  the one control on that row people already knew how to find, moved -- and made
  the pair look unintended.
- They are also the same height as the switch now. At 44dp against its 32dp they
  had made every row in the list twelve points taller, which pushed the progress
  bar and the percentage under it out of proportion.

### Notes

- No database migration. Room stays at version 3. The review log, the cards and the
  schedule are untouched; everything here happens before a deck becomes cards,
  after it already is, or outside the database entirely.
- New permissions: `INTERNET`, and `REQUEST_INSTALL_PACKAGES` for handing the
  downloaded file to the installer. The manifest says why, at length, next to each
  of them. Neither is reached with the update check switched off.
- The file provider now covers a second cache directory, `updates/`. Still not
  exported, still granting per intent, still nowhere near the review log.
- Version code 200040000.

## 0.3.0 press

What you can learn here, and what happens when a deck is wrong about it.

The app was built for languages, and its core turned out not to care -- a card is a
unit, a sentence that carries it and what it means, which is a definition in
neuroscience as well as a phrase in Polish. Four things were language-shaped;
all four now belong to the deck. And because a deck can be written by a model in
half a minute, the other half of this release is about not trusting it blindly.

### Subject decks

- The add-deck screen asks **what is being learned** -- a language or a subject --
  before anything else, and everything after that follows from the answer.
- A subject deck gets its own prompt (`assets/prompt/subject_prompt.txt`): four
  columns, no images, no hedging, order as curriculum, and a rule against padding.
- A subject deck stops at the **second** level. The third level asks the person to
  say the phrase out loud from its meaning, which is a question about a language; a
  definition has no pronunciation to produce, and the day's budget is no longer
  spent on a question that cannot be answered.
- New cards from a subject deck arrive **in the order the file was written**, not
  by frequency: line 40 can be meaningless before line 39. When a language deck and
  a subject deck are both active, new material is split between them instead of
  coming from whichever sorted first.
- A subject deck claims no language and is not read aloud. A voice guessing at
  Latin anatomy is worse than silence.

### Not trusting a generated deck

- A deck line may carry a **fourth column**: where the card came from. Optional,
  60 characters, and the only defence an app with no network can offer -- it cannot
  check a claim, but it can hand you somewhere to look. Four columns used to fail
  the whole line.
- Import now **counts what it accepted but doubts** and says so next to the number
  of cards added: definitions that repeat their own term, hedged wordings
  ("probably", "I think"), the same meaning under two different terms, and numbers
  of three digits or more. Nothing is refused; the deck installs in full.
- New in the session: **this card is wrong**. One tap under a revealed card. It
  leaves the rotation, and -- this is the point -- **nothing is written to the
  review log**: no lapse, no dent in the accuracy the governor reads, no week
  without new material because of a card the deck got wrong. If the card was
  introduced today, the day's new-material room is given back.
- Before this, the only way to say "this card is nonsense" was to answer *forgot*,
  which told the scheduler to show the nonsense more often and told the governor the
  learner was struggling. That was the single worst-behaved thing left in the app.
- Marked cards are **not deleted**. Settings -> Data says how many there are and
  puts them all back with one button; the day is then planned again with them in it.
### Notes

- No database migration: the schema stays at version 3. The list of marked cards is
  a preference, and a card's source travels at the end of its meaning. Both are
  compromises, both are written down in `docs/DECKS.md`, and both get a column of
  their own in the release that migrates the database.
- The review log is untouched, as always -- and this release exists partly to keep
  a bad deck from writing to it.

## 0.2.2 press

The day itself. Three things in settings behaved as if nobody used them, deck
names could not be changed at all, and the governor -- the part of this app that
is supposed to be the reason it works -- was quietly making decisions from
numbers it could not see.

### The day can be earned

The governor rules once, before the day's first answer. On a day whose queue
already fills the capacity it correctly finds no room for new material, and then
the user answers everything faster than the forecast expected -- and the app has
nothing unfamiliar left to show. Finishing changed nothing, which is the exact
treadmill this app was written against.

New material is now earned inside the session: every four reviews past the day's
obligation open one more chunk. Four, because that is what a chunk costs over the
following week, so it is paid for rather than borrowed from tomorrow. Gates are
not overridden -- a pile, a quiet week, poor accuracy, a return, an overheated day
still mean nothing new, however much work is done.

And there is now a hard daily ceiling on new material, a quarter of the day's
capacity, separate from the headroom arithmetic. Headroom answers "is there room
today"; this answers "should there be anything left for tomorrow". It is never
zero: a day with nothing unfamiliar in it is the day this becomes a chore.

### A day that ran hot is not repeated

Everything in the governor protected the queue from growing too fast. Nothing
protected the user from being spent, and one heroic evening is how the next four
days get skipped.

A day counts as overheated when it was far above the median day **and** either
its accuracy fell away from the usual or its plan was abandoned -- three
agreements, because any one alone is an ordinary day. A big, accurate, finished
day is a good day and is left alone. When it fires, new material stops and the
following day shrinks to three quarters of the norm. Reviews still arrive, and
the session says why.

### The norm survives a restart

The daily target was held in a field that only a live settings collector ever
filled in. A plan built by the nightly worker, or by the first screen of a freshly
started process, could therefore use the 40 from `governor.json` while the user's
own norm said 15 -- a day a third larger than the one that was asked for, with
nothing on any screen that could explain it. The stored value is now read at the
moment the plan is built.

### The log keeps the reason

The safety valve wrote its own name over the gate it was overriding, so the one
event most worth investigating -- something is wrong, and a chunk is being handed
out anyway -- recorded nothing about itself. It now logs both
(`SAFETY_VALVE/LOW_ACCURACY`). The accelerator's accuracy threshold moved into
the config too; it was a literal two lines from the three configured numbers that
mean the same thing, so tuning could not reach it.

### Renaming a deck

Possible at last, and where it should have been from the start. Deck settings
used to open by tapping the deck's title, which is not a place anyone looks for
settings -- and it took the title's place as the way into a session. There is now
a three-dot button beside the switch: settings there, the whole row opens the
session, and the title is a field you can edit.

### Settings that behave

The palette no longer changes when a tap lands in the empty space beside a tile;
only the tile itself and its caption are clickable. The row of section names at
the top follows the scroll and keeps the current section centred, so it is a
position indicator rather than a static list. The paragraph under the palettes,
the captions under four switches, the section subtitles that restated their own
headings and the leftover sentence about breaks are gone: explanations are kept
where an action cannot be undone, and nowhere else.

## 0.2.1 press

Adding a voice model that is large. Every part of that path was built for the
other kind -- a dozen small files that land in a second -- and the release people
actually want, Kokoro, is one file of a few hundred megabytes. It did not fail.
It sat there saying `1`, for as long as anyone was willing to watch, and there
was no way to tell from the outside that anything was happening at all.

### Progress that moves

The line under the button counted finished files. For a Piper voice that is a
count from one to twelve; for a Kokoro release there is one file to finish, so
the number was `1` from the first second until the last and then the model
appeared, minutes later, if the user had not given up.

It now counts bytes of the file that was picked, reported from inside the writing
of a single file, and shows a percentage. Two lines under it answer the two
questions that always followed: bzip2 is unpacked by the phone's own processor,
which is why this takes minutes, and leaving the screen is allowed.

### An install that outlives the screen

The copy used to run in the voice screen's own coroutine scope, which ends when
the screen does. A back press in the middle of a ten-minute unpacking cancelled
the work, deleted everything already written, and said nothing about either --
the user came back to a list with no new model in it and no reason given.

Installing is now owned by the app rather than by the screen, and the screen only
watches it. Come back later and the percentage is still climbing; come back after
it finished and the result is still there waiting to be read. It is deliberately
not a foreground service, so the system may still take the process while the app
is in the background -- which is why the screen asks not to put it away, instead
of promising something it cannot keep.

### A megabyte at a time

Both import paths copied in the standard library's default eight-kilobyte pieces.
Against three hundred megabytes that is forty thousand round trips through the
document provider and as many writes, and it is a large part of the wait that was
being reported as a freeze. Both now move a megabyte at a time.

### The screen stays awake while it copies

A long job with no touches in it lets the phone dim and lock, and a locked phone
is one whose process the system is free to reclaim. It did, halfway, leaving
nothing behind. The screen is now held awake for the length of a copy and
released the moment it ends.

### Unchanged

The review log, as always. Nothing here reads it, writes it, or migrates it, and
no database version moved. A model that is already installed is untouched: this
release changes how one arrives, not what one is.

## 0.2.0 press

Speech again, from the other end: what it does by itself, and how much of it was
ever worth showing. One regression of my own making is fixed here, and three
controls are gone for good.

### Cards read themselves again

A phrase met for the first time is supposed to speak the moment it appears. From
`0.1.1 press` it did not: the mark had to be pressed by hand, every time.

The cause was the cache key. Rendered audio is filed under the voice that made
it, and that name used to include the speed and the pitch this app applied to the
phone's engine. Both are read from storage, and storage answers a moment after a
session has already opened -- so the first card was rendered under one name and,
an instant later, looked for under another. Pressing the mark always worked,
because by then the numbers had arrived.

The key now names only who would actually say the words: a model with its voice
number and its own speed, or a constant standing in for the phone. Nothing in it
can change while a card is on screen.

Two smaller things went with it. A card only speaks by itself if it is still the
card in front when the audio is ready, so sound never arrives over the next
phrase. And a language that answered "not ready" once is asked again: that answer
usually came from a model still loading, and remembering it silenced the language
for the rest of the session with a working voice sitting right there.

### Speak every time, or only at first contact

A new switch. Reading itself belongs to the meeting that needs it -- the first
one -- and that stays the default. Whoever learns by ear turns this on and every
card speaks when it appears.

### Speed, pitch and a voice per language are gone

Three controls over an engine this app did not write, replaced by one switch:
**the phone's voice**, on or off.

- **Pitch** did nothing on most engines. It took the number and ignored it.
- **Speed** belongs to whoever set that engine up, in the phone's own speech
  settings, for every app that uses it. A model of your own keeps its speed,
  per model, on the voice screen -- where a Piper voice and a Kokoro voice can
  disagree about what 100% means.
- **A voice per language** was a list of engine voice names, phone by phone,
  that nobody could tell apart before hearing them.

The switch that remains means something: off, a language no model of yours covers
has no voice at all, and the speaker mark is not drawn for it rather than drawn
and dead. On, the phone reads exactly as the phone was set up to read.

A settings file written by an older build still carries the three old values.
They are read and dropped -- nothing to migrate them into -- and everything else
in the file is restored as before. No settings are lost by updating.
### The prompt comes out already answered

The prompt ended with six blank lines to fill in by hand -- which language is
being learned, which language the meanings should be in, how many cards, the
topic, the level. They were filled in inside a chat window, on a phone, before
anything had been learned, and a prompt sent with those lines still blank came
back as a deck in the wrong language, or as a question instead of a deck.

The add-deck screen now asks those questions itself, in chips: the language of
the meanings (the app's own language to begin with), how many cards, the level,
and one short line for the topic. **Copy prompt** hands over the text with the
answers already in it, and the two prompt buttons moved down, below the
questions they now carry answers to.

A question left alone stays blank rather than filled in with "none": a model
reads "none" as an instruction and a blank line as silence.

### Cards can be added to a deck that already exists

There was no way to do this at all. Every import made a deck, so a course
arriving in portions became five decks with the same name, and nothing in the
app could put them back together.

A deck's own screen now has **Add cards**: that button, then the file. Two taps,
no name to type and no choice to make. The deck keeps its name and its language
-- the name of the file a second portion happened to arrive in is not a reason to
rename a deck somebody is halfway through.

Underneath, three things had to stop depending on the file:

- **Card ids continue** from what the deck already holds instead of restarting
  at one, so a second portion cannot overwrite the first card by card.
- **The deck's count** is what the deck holds now, not what the last file
  brought.
- **Rows already in the deck are skipped.** Portions overlap, and a duplicate
  card is a card whose history is split in two.

### A deck may hold ten thousand rows

Was five thousand. Not a target and not a suggestion: the governor still lets in
a few new cards a day and cannot be hurried by a bigger deck. The old cap only
punished the opposite habit -- bringing a whole book once and being fed from it
for a year. A deck is storage; the day is what is rationed.

A file that large imports in seconds. Pasting it into the field is another
matter, and the field still has its own limit: bring it as a file.

## 0.1.1 press

Speech, and the six things about it that were either wrong or in the way. One of
them was a crash, and it is why this release exists.

### Choosing a Kokoro voice could kill the app

Kokoro addresses its voices by number, and the screen let the number climb with
no end: "+" was always enabled, and the store clamped at zero and at nothing
else. Past the last voice a number is not a wrong voice -- sherpa-onnx validates
the index down in C++ and calls `exit`, so there is no Kotlin frame anywhere to
catch and nothing in a log to find afterwards. The app was simply gone.

The number of voices is now asked of the loaded net itself, written into the
model's manifest, and it is the only thing that bounds the buttons. A number
nothing has confirmed is never handed to the runtime: until a net has answered,
voice 0 is offered, the one voice every model has. A number left behind by an
older version is pulled back into range the first time the model loads.

Two smaller things in the same file were the other half of it. The net in memory
was keyed by the model's cache id, which carries the voice number -- so every tap
on "+" freed a hundred megabytes of model and read it straight back in, for a
number that is an argument to one rendering and not a property of the net at all.
And loading did not share a lock with rendering, so that reload could free a net
that a rendering already in flight was still reading from: a second crash, with
the same absence of a stack trace. The net is now keyed by the file it came from,
and a load can no longer happen underneath a rendering.

### The first word of a session no longer waits

Warm-up existed to pay for the slow part -- reading a net in -- while the first
card was still being read. It did not: it asked whether any model was installed,
which is a folder listing, answered in a millisecond and loaded nothing. The cost
moved to the first card instead, where it was audible as a couple of seconds of
silence. Warm-up now loads the model, and a model that fails to load falls
through to starting the phone's engine rather than leaving that cold too.

### Speed belongs to a model, not to all of them

There was one speed and one pitch for every voice installed. Each model now
carries its own speed, in its own manifest, next to its language, and the cached
audio is keyed by it -- so moving a model's speed re-renders instead of playing
the old pace back from disk.

Pitch stayed in Settings, and that is not an oversight: a neural voice has one
pitch, its own, and the runtime has no parameter for it. The pair in Settings is
now labelled as what it always was -- the phone's own voice, where both numbers
do something.

### A model's language is only asked about when it is not known

Every model had four language chips under it, including the ones whose language
the app had already read off their own name. That is an invitation to overrule a
correct answer, and the price of overruling it is a deck that goes silent. A
detected language is now a line of text; the chips appear for a release that
names no language at all -- every multi-language one, Kokoro included, where the
question is real -- and behind one tap for the rest, since a folder can always be
renamed by hand.

### Two things removed from Settings

"Перерывы" was a heading, a rule and an empty room: a section that promised
settings and had none to make, because a break is measured rather than
configured. The sentence that explained it moved into "Нагрузка", beside the
number it is about. The section is gone.

The language section listed every interface language as a full-width chip, one
per row. Four is a section; a couple more is a wall. Chips now sit two to a row,
and past six languages the rest go behind one tap -- so adding a language no
longer adds a row to the screen.

---

## 0.1.0 press

The first build of the press epoch. Nothing new was added: this release is every
way the app could lose, delay or misreport work, closed, plus the tests that had
to exist before any of them could be called closed. Two of the fixes reach into
the scheduler and the governor, which is why they are described at length rather
than listed.

### The migrations are tested against a real old database

The schema history in `app/schemas` starts at version 3. Versions 1 and 2 were
never committed while they were current, which meant the two migrations every
existing install still has to run had never been executed against anything but a
developer's own phone. `fallbackToDestructiveMigration` is banned here, so a
wrong migration does not silently wipe the review log -- it makes the app refuse
to open at all, on a phone, after the update is installed. That is the one
failure this project cannot ship.

The old schemas were not faked into `app/schemas`: those files are Room's output,
and a hand-written one carries a hand-written identity hash that nobody can
check. Both migrations only ever add, so version 1 and version 2 were reversed
out of the committed version 3 exactly, and they now live as SQL in
`MigrationTest`, which upgrades a real version 1 and a real version 2 database
with rows in them and checks that the answers, the undo trail and the derived
`correctCount` all come out right. It runs on a device: `connectedDebugAndroidTest`.

A second test runs on every build without a device. It compares the schema of
version 1 plus everything the migrations add against the committed schema, column
for column, so a field added to an entity without a migration fails the build
instead of failing a user's install.

### "Ещё немного" no longer brings back a card you just answered

Adding cards to the day read the plan, appended to that copy, and wrote the whole
row back -- without holding the write lock the rest of the repository holds. The
button sits on the session screen, so there is always an answer in flight: the
answer wrote the plan, this copy overwrote it a moment later with a version read
before it, and the card that had just been answered reappeared with the counter
back up.

### One action at a time on the session screen

Swipe, undo and "ещё немного" each ran on their own coroutine. The database was
never corrupted -- the repository serialises its writes -- but the read that
decides what to write happened before the lock was taken, so undo pressed while
an answer was still being written retracted the answer before it. The three
actions now queue behind one another. Nothing slow is held: the six-second undo
window, speech and prefetching stay where they were.

### The progress band counts questions, not answers

It counted answers given. A card rated "again" comes back in the same session and
so does a first contact, so the band ran ahead of the work and a session of ten
could read ten out of ten with three still to come. It now counts distinct
questions of the session that are done, which is the number the label always
claimed to be.

### A streak is days, not rows

The clean-day streak the load governor uses to decide whether the daily ceiling
may rise was counted by walking the last thirty rows of `daily_stats`. A day with
no session writes no row, so the walk stepped over an absence and joined two
streaks together: a week off could still read as five clean days and buy a heavier
plan on the evening the user came back. And the first row is today, whose plan is
unfinished until it is finished, so every streak collapsed to zero each morning.
It now walks calendar days, a gap ends the streak, and today is allowed to be in
progress.

### A restored backup does not arrive as an avalanche

Replaying the log wrote every card visible, because the log records when an answer
happened and never records what was hidden at the time. A file two weeks old
therefore landed as a queue with two weeks of overdue cards in it -- exactly the
pile the amnesty pool exists to prevent, on the first screen after a restore. The
amnesty rule is now applied once at the end of the replay, before anything reads
the cards.

### The amnesty threshold is in the config

It was a `2` written next to the line that decides what a returning user sees.
Every load-bearing number in this app lives in `governor.json`; this one now does
too, as `amnestyAfterDays`. The value is unchanged.

### An interval of one day now means the next day, not the next night

A due time was the moment of the answer plus the interval: answer at 23:00 with a
one-day interval and the card came back at 23:00 the following night. Anybody who
studies in the evening met that card the day after that -- the interval FSRS had
chosen was stretched by up to a full day, on every review, compounding across a
card's whole history, always in the direction of forgetting. The daily plan is
keyed by a study day that begins at 04:00, so a due time now lands on the start of
the day the interval falls in.

This can only ever bring a card forward, never push it back, so nothing is hidden
for longer than the scheduler intended, and it cannot move a card into the past
because intervals are floored at one full day. The floor is now documented as
load-bearing rather than looking like a rounding convenience: a card being learned
is re-asked inside the same session anyway. Nothing in the review log changes; the
log stores what happened, and what happened is unaffected.

### A promotion is new material, and the governor gets to say when

When an item reaches three weeks of stability, the next way of asking it opens --
recognition to cloze to production. That new level is a card the user has never
been asked, written `isNew`, due tomorrow. It was created inside the answer path
without consulting the day's new-material budget and without being counted
anywhere. So on a day the governor had allowed nothing new -- after midnight, low
accuracy, backlog over the limit, the first days back after a break -- a long
session could still mint a pile of level-1 cards for tomorrow, and
`daily_stats.newIntroduced` never saw them, so the measured norm and the safety
valve both read the day as lighter than it had been and sized tomorrow from a day
that had not happened.

The rule now lives on its own in `LevelPromotion`, takes the day's remaining
budget as an argument, and a promotion is counted like the introduction it is.
Undo gives the budget back with the card it removes. Nothing is lost by waiting:
an item at three weeks of stability is still there tomorrow, and if it lapses in
the meantime it was not ready to be promoted.

### A chunk's score no longer depends on which chunks it was batched with

The selector's frequency term was `1 - rank / maxRank`, where `maxRank` was the
largest rank in the day's candidate batch -- and the batch is whatever the
frequency query happened to return. The same chunk therefore scored differently on
different days for reasons that had nothing to do with it: batched with rare words
its frequency bonus sat near 1, batched with common ones it collapsed towards 0,
and the component layer -- the one thing that makes this app more than a word list
-- was being outvoted by an accident of pagination. Frequency is now measured
against an absolute scale (`frequencyHalfRank`, 2000), so the score says something
about the chunk. Common phrases still come first; i+1 still comes before
frequency.

## 0.5.0 proof

### More than one voice

The voice screen held one model. Adding a second destroyed the first, which for
anybody learning two languages meant copying sixty megabytes every time they
switched -- and there is no reason a phone with room for ten should be asked to
keep one.

Models are now a list. Each row has a switch, the language it reads, and its own
delete button. Switching a model off keeps its files: "this voice is worse than my
phone's" no longer costs a re-install to undo.

One model per language speaks at a time. Switching one on switches off whichever
held that language before, because two models with an equal claim to a deck is a
coin toss, and a coin toss is not something an app should hide from you.

Only the model in use is held in memory, loaded when a card in its language comes
up and dropped when another language needs the room. Ten installed models cost
storage and nothing else.

### The archive unpacks itself

Every model on the sherpa-onnx page is a `.tar.bz2`. Android opens neither half of
that, so the instructions read "install ZArchiver, extract twice, come back" --
three apps deep into a language app that had not shown a card yet, and where most
attempts ended.

The app now takes the archive. Download it, press **Add a .tar.bz2 archive**, pick
the file; it is unpacked, checked, and installed, counting files as it goes. An
already-unpacked folder is still accepted, and is still the faster route when one
is already open.

The picker opens for any file rather than for bzip2 specifically. Phones disagree
about what a `.tar.bz2` is called and several answer nothing at all -- which is
exactly how a file picker greys out the file it was opened to choose. The contents
are what get checked.

Two refusals were added, both said in words on screen: a picked file that is not
an archive, and not enough free space -- an unpacked model is about three times
the download, and that is checked before unpacking rather than discovered on the
last file of sixty.

Nothing is trusted about the names inside an archive. An entry called
`../../databases/ikna.db` would, unpacked naively, overwrite the review log; every
path is resolved and anything landing outside the destination is skipped. The
archive is unpacked to one side and moved into place only once it turns out to be
a model, so a wrong pick or a dead battery leaves the installed models alone.

### Kokoro's voices are reachable

A Kokoro file holds around a hundred voices addressed by number and by nothing
else -- they have no names to list. The app always used the first. Kokoro rows now
carry that number, and the test button is how it is judged.

### The review log

Untouched. No migration, no schema change. Voice models live in the app's own
files directory and are not part of the database or of a backup.

## 0.4.0 proof

### The cards speak

A model could be added, proved out loud on the voice screen, and every card would
still be silent. Three separate reasons, all of them in the app:

- Readiness was decided by asking the phone's own engine, and nothing else. A
  phone whose engine is missing, disabled or slow to start answered no -- and the
  answer hid the speaker mark and skipped every prefetch, with a working model
  sitting right there. A model that loads is now an answer on its own.
- A phrase met for the first time is meant to read itself out. It asked for audio
  before anything had rendered it, so it played nothing, and the file it had just
  finished writing sat unplayed. Rendered first, played second, and only if the
  same card is still on screen.
- Readiness was one global yes or no, while a model speaks one language. A deck
  in another language showed a mark that did nothing when pressed. It is now
  asked per card language, remembered per language, and the mark appears only
  when something can actually say the words.

### The voice screen says who reads what

**Who reads which deck** lists every deck language and who would read it: the
model, the phone's voice, or nobody. The master switch moved onto this screen,
beside the test button that does not obey it -- the two facts that used to be one
screen apart and never connected.

### The per-language voice pickers are gone

Settings offered, for each deck language, a list reading `default`, `RU .
ORDINARY`, `RU . GOOD`. Those were the phone engine's own voice names, they said
nothing about who would read a card, and with a model installed they changed
nothing anybody could hear. Removed. Speed and pitch stay where they were.

Saved settings files are unchanged and still restore: the stored voice map is
kept in the format, simply no longer chosen by hand.

### 114 MB, and why it now is not

The speech engine is native code and 0.3.0 packed it for four architectures in
one file. A phone could run one of them. The release now carries
`ikna-<tag>.apk` for arm64 -- every phone sold since roughly 2017 -- and
`ikna-<tag>-legacy32.apk` for older 32-bit ones. Around 40 MB each instead of
114, with no feature removed. Emulator architectures are not built.

### Documentation

`docs/VOICE.md` says that model downloads are `.tar.bz2`, that Android's own file
manager will not open one, and how to unpack it on the phone. The app still
accepts folders only; unpacking inside the app is not built yet.

## 0.3.0 proof

Still the proof epoch. The largest one so far, and all of it is the same kind of
work: places where the app knew something useful and did not say it, or asked for
something it could have worked out itself.

### A new deck is asked which language it is in

Every imported deck used to arrive with no language at all, which meant no voice:
the engine has nothing to read with until it knows what it is reading. The one
screen that could fix that was the deck's own page, behind a deck nobody had
opened yet -- so a new deck was silent, and nothing on it said why.

The question is now asked where the deck is made, as a row of taps under the
prompt, and it defaults to *no voice*: no language claimed, nothing promised.
The answer is used for the voice and for nothing else, and it can still be
changed later on the deck's page, which is where it always was.

### The minutes stay on the screen

A session has always been able to say what it will cost -- "24 cards, ~6 min"
above the first card -- and almost never did. Two reasons, both fixed here.

The measurement threw away nearly every answer it was given. Anything faster
than 0.8s or slower than a minute was treated as a mis-swipe or a phone put
face-down, and a real answer is often both: a phrase recognised on sight goes
faster than that, a phrase being thought about takes longer. The bounds are now
0.4s to two minutes, which still drops the accidents and keeps the session, and
four measured answers are enough where eight were required.

The figure is also written down now. It used to be recomputed from recent history
every time, so a short evening pushed the history below the threshold and the
minutes vanished for no reason the reader could see. The last real measurement is
kept and used until a better one exists.

And it is shown where the decision is actually made: on the deck list, beside
what each deck owes today -- "today 24 · ~6 min". The question at that moment is
not how many cards there are, it is whether this fits in the time there is.
Nothing is shown until something has been measured; a guess that turns out to be
a lie would not be trusted twice.

### One download instead of two

Every release carried two APKs: the app, and the same app with the speech engine
inside. That split made sense while the engine came with a 300 MB model welded
to it. Once the model moved out to the file picker, the second file was ten
megabytes of runtime and a question -- "which one do I want?" -- asked of
somebody who came here to learn a language.

It was also broken. The script that downloads the runtime had a corrupted
address in it, so the second APK failed to build on every release, quietly,
because that step was allowed to fail. The release page kept a download button
pointing at a file that was never produced: a 404 with a green tick beside it.

So there is one file now, about 31 MB, with the engine inside and switched off
until it is turned on. The address is fixed, the runtime is fetched before
anything is compiled in both workflows, and a failed download now fails the run
instead of losing half the release.

### Deck squares carry letters, not emoji

The square could be given one of twenty-four emoji. On this app's flat
single-colour surfaces a full-colour emoji, drawn by the phone in its own style
with its own shading, looked like a sticker on a blueprint -- and it could not be
fixed by choosing better emoji, because the problem was that they are pictures.

The square now takes **one or two characters of your own**: initials, a language
pair, a number. Left empty it keeps working out its own letters, exactly as
before, so there is nothing to undo. The letters take the square's own colours,
which the emoji never could, so a deck that owes you cards still inverts
properly. The eight colours are unchanged.

### The translation arrives as one translation

Two habits of the models that write these decks, both repaired at import:

- **The punctuation of the phrase is copied onto its translation.** "how are
  you?" was coming back without the question mark, "watch out!" without the
  exclamation mark -- and the mark is half of what those phrases mean. A mark
  the phrase has not got is removed too.
- **A list of wordings becomes the first wording.** "tired, worn out", "tired /
  worn out", "tired (informal)": three answers on the back of a card turn every
  recall into multiple choice, because the person grades themselves and one of
  the three always matches what they thought. Brackets, slashes and semicolons
  go without question; a comma only counts as a list when the phrase itself has
  no comma and every piece is a word or two.

A translation that is simply long, or that contains a comma because the phrase
does, is left exactly as written -- an import step that edits correct lines
would be worse than none. The prompt behind the plus button now asks for both
things directly, and one of its own examples, which offered two wordings, has
been corrected.

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

This is stored beside the theme, not inside the deck. A mark is how one person
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
