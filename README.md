# Ikna

Anki, but reversed. The system feeds you; you never feed the system.

Ikna is an offline Android SRS for **chunks** (short phrases in context), built around one
idea that no existing flashcard app implements: a **Load Governor** that decides *whether you
are allowed new material today*, based on a forecast of your upcoming review load, your
backlog, your recent accuracy and whether you skipped days.

There is no "add card" button. There never will be.

## Core design decisions

| Decision | Value |
| --- | --- |
| Unit of learning | chunk = phrase + carrier sentence + translation + `target_span` |
| Presentation levels | 0 recognition, 1 cloze, 2 production |
| Scheduler | FSRS-4.5 (17 params), local optimisation later |
| Second memory layer | component-level (lemma) state, one-directional influence |
| New-material control | `LoadGovernor` (forecast-aware valve) |
| Debt handling | amnesty pool, 20% of each session, never a visible backlog number |
| Streaks | none. Metric is *days with a session in the last 30* |
| Daily minimum | 1 card |
| Audio | not in MVP (`audio_ref` reserved in schema) |
| Content | pre-baked packs, generated offline in `tools/genpack` |

## Why the signing key is fixed

Gradle generates a throwaway `debug.keystore` on every clean machine. In GitHub Actions that
means **every build has a different signature**, so a new APK cannot be installed over the old
one and you lose your entire `reviews` history. Ikna signs *both* debug and release with one
fixed keystore committed to this repository as `ikna.keystore`. Nothing to generate, no
repository secrets to configure. The trade-off is written down in `docs/KEYSTORE.md`.

The review log is the only irreplaceable asset in this app. Packs can be re-downloaded, FSRS
parameters can be recomputed, four months of answers cannot.

## Build

Push to `main` (or run the workflow manually) and download the `ikna-apk` artifact.
No Gradle wrapper jar is committed; CI provisions Gradle itself.

## Layout

```
app/src/main/java/dev/ikna/
  data/db        Room entities, DAOs, migrations
  data/pack      chunk pack models + loader
  data/repo      repositories
  data/export    weekly JSON dump of reviews
  domain/fsrs    FSRS-4.5 + scheduler
  domain/governor GovernorConfig, LoadGovernor, ChunkSelector
  domain/session  session assembly
  work           WorkManager jobs
  ui             Compose UI
tools/genpack    offline pack generator (Python)
docs             architecture, governor spec, keystore setup
```

## Docs

- `docs/ARCHITECTURE.md`
- `docs/GOVERNOR.md`
- `docs/KEYSTORE.md`

## When a day starts

Not at midnight. `dayStartHour = 4` in `governor.json`, and every day key, daily counter,
activity mark and "nothing new tonight" rule is measured from there.

Delayed sleep phase comes with the territory: the session at 01:00 is the *evening* session. With
a midnight boundary it was filed under a day that had not begun yet, so the activity map grew a
hole for a day that was actually worked, the measured norm dropped, and the load governor throttled
new material because of a break that never happened. Four in the morning is late enough to catch
almost every real night session and early enough that nobody works through it by accident.

## Decks

Two packs ship in `app/src/main/assets/packs`:

| Deck | Chunks | Shipped |
| --- | --- | --- |
| `en-ru-core` — English core chunks | 70 | on |
| `pl-ru-core` — Polish core chunks | 110 | off |

A second language ships **off** on purpose: two active decks interleave two languages inside one
session, and the switch lives in *Колоды*. Everything else about a Polish chunk is identical to an
English one — same three levels, same FSRS state, same governor, same component layer — because a
chunk is content and none of the machinery knows what language it is looking at.

Both packs are built offline by the same generator, from a three-column TSV
(`phrase`, `carrier sentence`, `translation`):

```
python3 tools/genpack/generate_pack.py \
  --seed tools/genpack/seed_chunks_pl.tsv \
  --out app/src/main/assets/packs \
  --pack-id pl-ru-core --lang pl --title "Polish core chunks" --inactive --strict
```

Polish is tokenised but deliberately **not** lemmatised: Polish inflection needs a real
morphological analyser, and guessed lemmas are worse than none, because a wrong lemma merges
unrelated words in the component layer and hands out credit nobody earned. Surface forms aggregate
less, but they never lie.

## License

Ikna is free software, licensed under the **GNU General Public License, version 3 or (at your
option) any later version**. The full text is in [LICENSE](LICENSE).

This licence covers **the whole repository**: every file, every commit, every branch and every
release — the ones published before this notice was added as well as every future one. No
per-file headers and no per-release notices are needed; this section and the `LICENSE` file are
the entire statement, and nothing has to be re-stated when a new version ships.

    Copyright (C) 2026 the Ikna authors

    This program is free software: you can redistribute it and/or modify it under the terms of
    the GNU General Public License as published by the Free Software Foundation, either version 3
    of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along with this program.
    If not, see <https://www.gnu.org/licenses/>.

For tools that ask: `SPDX-License-Identifier: GPL-3.0-or-later`.
