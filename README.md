<p align="center">
  <img src="docs/logo.png" alt="ikna" width="420">
</p>

<p align="center">
  Anki, but reversed. The system feeds you; you never feed the system.
</p>

---

ikna is an offline Android app for learning languages in **chunks** — short
phrases inside a carrier sentence — built around one idea that no other flashcard
app implements: a **Load Governor** that decides *whether you are allowed new
material today*, from a forecast of your upcoming review load, your backlog, your
recent accuracy and the days you missed.

There is no "add card" button. There never will be.

It is built for ADHD, which is not a slogan here but a list of constraints that
rejected features: no streaks, no guilt, no growing counter, no queue number, no
choice where a choice can be avoided, and a day that starts at four in the
morning because that is when the previous one actually ends.

The name is written in lower case, always. A capital I is a bare vertical bar in
most sans-serif faces and is read as a lower case L, so "Ikna" invites being read
as "lkna". The logo, the label under the icon and this file all agree on that.

## What it looks like

A card, thrown. Answering is a gesture, not a row of buttons: throw the card away
from you when you knew it, towards you when you did not, and the direction is the
answer. There is a deck list with one number on it, a session, and a statistics
screen that counts days with a session rather than days in a row.

There is no video here yet. A screenshot is a poor description of an app whose
whole answer mechanism is a movement, and a screen recording of a swipe looks
like a stutter, so the honest options are to build it properly or to say nothing.
Build it and see it; it takes one `gradle assembleDebug`.

## Core design decisions

| Decision | Value |
| --- | --- |
| Unit of learning | chunk = phrase + carrier sentence + translation + `target_span` |
| Presentation levels | 0 recognition, 1 cloze, 2 production |
| Scheduler | FSRS-4.5 (17 parameters), local optimisation later |
| Second memory layer | component-level (lemma) state, one-directional influence |
| New-material control | `LoadGovernor` — a forecast-aware valve |
| Debt handling | amnesty pool, 20% of each session, never a visible backlog number |
| Streaks | none. The metric is *days with a session in the last 30* |
| Daily minimum | 1 card |
| Answering | one axis: left *not known*, right *known*. Nothing else is an answer |
| Audio | the phone's own speech engine, offline, beta, off by default. No voice ships in the APK |
| Colour | six palettes, each in two lightings; Уголь by default. Every pair passes 4.5:1 |
| Interface languages | Russian, English, Polish |
| Content | pre-baked packs, generated offline in `tools/genpack` |
| Network | none. The app has no internet permission |

## How a session works

The day's plan is decided once and persisted. It can only shrink as you answer,
and it grows only when you ask for more — the counter at the top of the screen is
not allowed to go up while you work, because watching the finish line move away
is how a session ends early.

A card is answered by throwing it: left for *not yet*, right for *got it*, up and
down for the two ratings in between. There are no rating buttons. Tapping the
card turns it over, and everything else — the estimate, the deck name, the speak
mark — lives in the thin row above, never on the card itself.

Undo is an inserted row, not an edit: the log is append-only, and taking an
answer back is recorded as a retraction of it.

## When a day starts

Not at midnight. `dayStartHour = 4` in `governor.json`, and every day key, daily
counter, activity mark and "nothing new tonight" rule is measured from there.

Delayed sleep phase comes with the territory. The session at 01:00 is the
*evening* session; with a midnight boundary it was filed under a day that had not
begun yet, so the activity map grew a hole for a day that was actually worked, the
measured norm dropped, and the governor throttled new material because of a break
that never happened. Four in the morning is late enough to catch almost every
real night session and early enough that nobody works through it by accident.

## Decks

Two packs ship in `app/src/main/assets/packs`:

| Deck | Chunks | Shipped |
| --- | --- | --- |
| `en-ru-core` — English core chunks | 121 | on |
| `pl-ru-core` — Polish core chunks | 121 | off |

A second language ships **off** on purpose: two active decks interleave two
languages inside one session, and the switch lives on the deck screen. Everything
else about a Polish chunk is identical to an English one — same three levels, same
FSRS state, same governor, same component layer — because a chunk is content and
none of the machinery knows what language it is looking at.

Both packs are built offline by the same generator, from a three-column TSV
(`phrase`, `carrier sentence`, `translation`):

```
python3 tools/genpack/generate_pack.py \
  --seed tools/genpack/seed_chunks_pl.tsv \
  --out app/src/main/assets/packs \
  --pack-id pl-ru-core --lang pl --title "Polish core chunks" --inactive --strict
```

Polish is tokenised but deliberately **not** lemmatised: Polish inflection needs a
real morphological analyser, and guessed lemmas are worse than none, because a
wrong lemma merges unrelated words in the component layer and hands out credit
nobody earned. Surface forms aggregate less, but they never lie.

Your own pack can be imported from the deck screen.

## Your answers are the only backup that matters

Once a week, and on demand, two files are written to `Documents/ikna/`:

- `ikna-reviews-YYYY-MM-DD.jsonl` — the append-only review log.
- `ikna-settings-YYYY-MM-DD.json` — theme, colours, font, language, reminder.

Deliberately outside the app sandbox, so they survive an uninstall, a factory
reset and a new phone. *Restore* takes either file and works out which one it is
from its contents.

Restoring the log does not copy a database over the app — it **replays the
history**. Every answer is fed back through the same scheduler, and the cards, the
word layer and every statistic are recomputed from it. That is why the log may
only ever gain rows: given the answers, everything else is derivable, including by
a future version with a different algorithm.

## Appearance

Flat right angles, hand-drawn marks, no Material components anywhere — not a
style preference but a requirement, since a Material button ignores the theme's
shape scheme and rounds itself back at every opportunity.

Colour is two choices, not one. **Which palette** — Уголь (warm near-black and
ember, the default), Библиотека (dark green and brass), Чернила (navy and
coral), Слива (aubergine and mint), Ноль (pure black and white, nothing else) or
Нейтральная — and **how it is lit**: dark, light, as the phone is set, or four
colours picked by hand (background, ink, muted, accent).

A palette is not a theme: the same one exists in both lightings and keeps its hue
in both, so the light version is tinted paper rather than white with the colour
drained out. The six are chosen from tiles painted in themselves rather than from a
list of names. Every pair of colours in every palette — including the warning red,
which each lighting defines for itself and which steps aside entirely when the
palette's accent is already a warm red — is held to 4.5:1 by a unit test, and the
hand-picked scheme gets the same check live, refusing combinations that cannot be
read. Any `.ttf` or
`.otf` on the phone can be used, and it is applied to the entire interface —
headings, body, section marks, button captions and counters alike. The file is
validated before it is accepted, so a broken font cannot leave the app unreadable.

## Getting to a card

- **The widget.** One number on the home screen and a tap that opens the cards
  directly.
- **The reminder.** One notification a day, at your hour, and only if the day's
  minimum is still unmet. It also opens the cards directly. It never mentions a
  streak, a queue size or a number of missed days.

Both skip the deck list on purpose: tapping either one is already an answer to
"shall I study now", and asking again is where the intention is lost.

## Accessibility

Every mark in this app is drawn on a canvas, and a drawn shape has no text for a
screen reader to find — so each one is given a name, in all three languages, and
the switches are real toggles that announce their state. The system's per-app
language picker (Android 13+) lists the three languages through
`res/xml/locales_config.xml`.

## Privacy

No servers, no accounts, no analytics, no internet permission. The details, and
what is stored where, are in [PRIVACY.md](PRIVACY.md).

## Install

From the [releases page](https://github.com/d1d2dopamine/ikna/releases): download
the APK and open it. Android will ask once whether to allow installing from this
source.

There is no app store listing. Every release is signed with the key committed to
this repository, so each new APK installs over the previous one and your answers
survive the update.

## Why the signing key is fixed

Gradle generates a throwaway `debug.keystore` on every clean machine. In GitHub
Actions that means **every build has a different signature**, so a new APK cannot
be installed over the old one and you lose your entire `reviews` history. ikna
signs *both* debug and release with one fixed keystore committed to this
repository as `ikna.keystore`. Nothing to generate, no repository secrets to
configure. The trade-off is written down in `docs/KEYSTORE.md`.

The review log is the only irreplaceable asset in this app. Packs can be
re-downloaded, FSRS parameters can be recomputed, four months of answers cannot.

## Build

Push to `main`, or run the `build` workflow by hand, and download the `ikna-apk`
artifact. No Gradle wrapper jar is committed; CI provisions Gradle itself. Every
build carries the version written in `app/build.gradle.kts` — there is exactly
one place where a version number exists.

Building without the committed key is one flag, and produces an unsigned APK:

```
./gradlew assembleRelease -Pikna.unsigned=true
```

## Release

Bump the two version lines at the top of `app/build.gradle.kts`, then tag the
commit with the same number:

```
val appVersionName = "0.3.0"
val appVersionCode = 30000        // major * 100000 + minor * 10000 + patch * 100
```

```
git tag v0.3.0
git push origin v0.3.0
```

The `release` workflow refuses to continue if the tag and `appVersionName`
disagree, then runs the tests, builds a signed release APK, names it after the
tag and attaches it to the GitHub release with generated notes. The About line in
the app and the file on the release page therefore cannot drift apart.

The version lives in the build file rather than being derived from the tag or the
CI run number, so that a clone of this repository builds the same version number
on any machine, with no tag and no CI at all.

## Layout

```
app/src/main/java/dev/ikna/
  data/db         Room entities, DAOs, migrations
  data/pack       chunk pack models + loader
  data/repo       repositories, restore-by-replay
  data/export     weekly dump of the review log and the settings
  data/prefs      settings and font storage
  domain/fsrs     FSRS-4.5 + scheduler
  domain/governor GovernorConfig, LoadGovernor, ChunkSelector
  domain/session  session assembly
  domain/time     the 04:00 day boundary
  audio           the phone's speech engine, wrapped
  work            WorkManager jobs: daily plan, export, reminder
  widget          the home screen widget
  ui              Compose UI (onboarding, decks, session, stats, settings, theme, text)
app/src/debug/java/dev/ikna/ui/debug     the technical screen: governor log,
                                         plan rebuild. Debug builds only.
app/src/release/java/dev/ikna/ui/debug   its empty stand-in, so the screen is
                                         absent from a release rather than
                                         merely unreachable in one.
app/schemas       the database schema of every version, committed on purpose:
                  without the old one, a migration cannot be checked
tools/genpack     offline pack generator (Python)
docs              architecture, governor spec, keystore setup
```

## Docs

- `docs/ARCHITECTURE.md`
- `docs/GOVERNOR.md`
- `docs/KEYSTORE.md`
- [`CHANGELOG.md`](CHANGELOG.md)
- [`PRIVACY.md`](PRIVACY.md)

## License

ikna is free software, licensed under the **GNU General Public License, version 3
or (at your option) any later version**. The full text is in [LICENSE](LICENSE).

This licence covers **the whole repository**: every file, every commit, every
branch and every release — the ones published before this notice was added as well
as every future one. No per-file headers and no per-release notices are needed;
this section and the `LICENSE` file are the entire statement, and nothing has to
be re-stated when a new version ships.

    Copyright (C) 2026 the ikna authors

    This program is free software: you can redistribute it and/or modify it under the terms of
    the GNU General Public License as published by the Free Software Foundation, either version 3
    of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along with this program.
    If not, see <https://www.gnu.org/licenses/>.

For tools that ask: `SPDX-License-Identifier: GPL-3.0-or-later`.
