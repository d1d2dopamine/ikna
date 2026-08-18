# Architecture

## Layers

```
UI (Compose)
  |
SessionViewModel  <- SessionBuilder
  |                     |
  |                LoadGovernor + ChunkSelector
  |                     |
repositories -> Room (append-only reviews + derived tables)
                  ^
            PackLoader (assets/packs/*.jsonl)
```

## Two memory layers

**Item layer** — `cards`, scheduled by FSRS-6. One chunk produces up to three cards, one per
presentation level (recognition, cloze, production).

**Component layer** — `components`, keyed by `(lemma, pos)`. Every answer updates the
components of the answered chunk with weights: the token inside `target_span` gets 1.0, other
content tokens 0.25, function words 0.0.

Information flows **one way**. An answer updates the card state and the component states
independently. Component state influences card state only once, at the moment a card is
created, as a prior for initial stability and difficulty. Never after. Two-way updates diverge
and cannot be debugged.

`components` is a *derived* table. It can be dropped and rebuilt from `reviews` at any time,
which is what makes it safe to change the component model later.

## Data safety rules

1. `reviews` is append-only. Migrations may add columns, never rewrite rows.
2. `exportSchema = true`; schemas are committed under `app/schemas`.
3. `fallbackToDestructiveMigration()` is forbidden. It is the single line that silently
   deletes a user history on schema change.
4. Derived tables (`components`, `daily_stats`, `governor_log`) may be dropped by migrations
   and rebuilt.
5. `ExportWorker` writes a weekly JSON dump of `reviews` to shared storage
   (`Documents/ikna/`) via MediaStore, so it survives uninstall and device changes.
   One line per review, serialised by `data/export/ReviewRecord` — the single
   definition of that file format, shared by the exporter and the restore. Two
   hand-written copies of the same format is one too many: a field added on one
   side and forgotten on the other is data lost silently.
6. Nothing rewrites a card's history to compensate for time passing. An earlier
   version shifted every schedule forward after an absence and moved
   `lastReviewAt` with it — but `lastReviewAt` is an input to FSRS, so the next
   interval was computed from a review that never happened. Absences are handled
   by the amnesty pool instead, which changes what is *shown* and never what was
   recorded.
7. The database is excluded from Android's cloud backup (`allowBackup="false"`,
   plus an explicit exclusion in `res/xml/data_extraction_rules.xml`). It is a
   record of everything the user has studied, and PRIVACY.md says it stays on the
   phone. Device-to-device transfer is still allowed.

## Restore is a replay, not a copy

`RestoreRepository` reads the exported log, inserts the answers, and then rebuilds
`cards`, `components` and `daily_stats` by feeding every answer back through the
same scheduler. Two details matter:

- **Row ids in the file are not row ids here.** They belong to the database that
  wrote them, and a restore usually targets a different one. They are dropped on
  insert and SQLite numbers the rows; the `undoOf` trail is then re-pointed at the
  new ids, in a second pass after every answer has one. Importing ids verbatim
  either collides with existing rows or attaches an old retraction to an unrelated
  answer.
- **Identity is `chunkId:level:ts`, not the id.** That is what makes importing the
  same file twice a no-op, and what lets a file be merged into a database that has
  been used since the export.

`planCompleted` is not restored. A day's plan is not an answer, so it is not in
the log; guessing it would hand the accelerator a run of clean days that nobody
studied. It restarts.

## Packs

Content is data, not code. A pack is a JSONL file plus a manifest, generated offline by
`tools/genpack/generate_pack.py`. Lemmas and part-of-speech tags are baked in at generation
time, so the phone never does morphology. `audio_ref` exists in the schema but is null in the
MVP.

A pack can be replaced without touching the database: chunks are upserted by stable id, and
card state is keyed by chunk id.

## No user settings

The governor is configured by `assets/governor.json`, deserialised into `GovernorConfig`. It is
not exposed in the UI. This is deliberate: a settings screen turns the app into a tuning toy
and the tuning is more interesting than the studying.
