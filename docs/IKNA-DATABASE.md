# Ikna Database

**Ikna Database** is the proposed public content corpus behind future catalogue
builds. It is not telemetry and it is not the learner's local database.

There is already a Kotlin class named `IknaDatabase` in the app. That class is the
local Room database containing cards, reviews, settings-derived state and installed
content. Until it is renamed, documentation should call it the **local learner
database** when the distinction matters.

## Why it exists

The current catalogue is a distribution format: it offers ready-made packs. That is
enough while one catalogue card is treated as one independent chunk.

Context-aware learning needs a different layer. The system has to know when two
source occurrences belong to the same learning target, which contexts a learner has
seen, and which real source sentences remain available as unseen contexts.

That relationship data belongs upstream of a downloadable deck.

```text
Tatoeba / other permitted corpora
              |
              v
        Ikna Database
  targets + occurrences + provenance
              |
              v
       catalogue builder
              |
              v
      downloadable packs
              |
              v
       ikna application
```

Catalogue remains the user-facing way to discover and download material. Ikna
Database is the content model the catalogue can be built from.

## Core objects

### Learning target

The knowledge ikna intends to train.

```text
target_id
language
canonical_text
target_kind
normalization_version
```

`canonical_text` is not permission to collapse every morphological or semantic
variant into one target. A target relationship must be produced by a versioned rule
that can be tested on fixtures.

### Occurrence

One real occurrence of a target in source material.

```text
occurrence_id
target_id
source_id
sentence_id
surface_start
surface_end
surface_text
```

Offsets point into the exact stored source sentence. They must use one documented
coordinate system; the current app uses UTF-16 offsets to match Kotlin/Java strings.

### Context

What the learner can be shown around an occurrence.

```text
context_id
occurrence_id
sentence
translation
translation_language
ipa / optional phonetic fields
quality metadata
```

A target may have one context or many. One is valid. The database must never invent
extra contexts only so every target reaches a quota.

### Source and licence

Every context keeps enough provenance to answer where it came from and under what
terms it may be redistributed.

```text
source_id
corpus
source_record_id
public_url
licence
attribution
```

Provenance is part of the data model, not release-page prose added afterwards.

## Contextual diversity v1

The first grouping rule should prefer precision over coverage.

A safe first pass can group occurrences only when the pipeline has high confidence
that they express the same target under the same normalization rule. Exact or
conservatively normalized matches are useful even if they cover only part of the
catalogue.

0.11 should not require:

- semantic embeddings to force every phrase into a family;
- an LLM call at runtime;
- generated replacement sentences;
- automatic merging of every inflected form;
- a promise that every target has several contexts.

More sophisticated morphology and sense disambiguation can be added later as new
versioned pipeline stages.

## Distribution without a server

Ikna Database does not need an application server. A practical layout is a separate
repository containing schema, build code, source manifests, tests and small fixtures.
Generated database/catalogue artefacts can be published as static GitHub Release
assets.

```text
ikna-database/
  schema/
  pipeline/
  sources/
  fixtures/
  tests/

GitHub Release assets:
  manifest.json
  target/context shards or catalogue packs
```

The application continues to download only the static files the learner asks for.
Nothing about this architecture requires uploading review history, identifiers,
cards or statistics.

## Stable identity

A target or occurrence id must not depend on its row number in one generated file.
Rebuilding the database should preserve identity when the underlying linguistic
object has not changed.

If a grouping rule changes enough to alter identity, that change needs an explicit
migration/alias strategy. Otherwise a corpus rebuild can accidentally make known
content appear new.

## Relationship to learner history

The local learner database owns facts about the learner:

```text
what target/context was shown
when it was shown
whether that context had been seen before
what answer was given
which policy version selected it
```

Ikna Database owns facts about content:

```text
what the target is
which real contexts contain it
where those contexts came from
how they may be redistributed
```

Keeping the boundary strict means a new corpus build can be published without
moving private learner data anywhere.

## First milestone

Before any adaptive context selection ships, the corpus pipeline should be able to
prove this relationship on fixtures:

```text
one target
  -> original context A
  -> alternative context B
  -> alternative context C
```

and also prove the negative cases:

```text
similar text, different meaning -> separate targets
uncertain relationship          -> separate targets
one available context           -> valid target with no alternative
```

Only after those invariants hold should runtime Context Policy start consuming the
new metadata.
