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
