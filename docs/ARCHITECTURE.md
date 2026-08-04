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

**Item layer** — `cards`, scheduled by FSRS-4.5. One chunk produces up to three cards, one per
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
   (`Documents/Ikna/`) via MediaStore, so it survives uninstall and device changes.

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
