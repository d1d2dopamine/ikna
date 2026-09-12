# Catalogue v2

Catalogue v2 is the content contract planned for ikna 0.11.0. It extends the
existing catalogue into a structured public content layer for the learning engine.

The release channel does not move:

- Git tag: `catalog`
- GitHub release title after the v2 rollout: `Catalogue v2`
- Index URL: `releases/download/catalog/index.json`

The catalogue remains static files published by GitHub Releases. No server,
account, telemetry endpoint or learner upload is introduced.

## Why v2 exists

The current catalogue already contains enough information to study from: target
text, a real source sentence, a direct meaning, frequency rank, token data,
pronunciation where available and provenance. The catalogue census also shows
that many exact targets already occur in several distinct source contexts.

0.11 needs the catalogue to preserve those relationships explicitly so the
learning engine can later distinguish a target from one particular card and one
particular sentence. v2 adds structure around the data that already exists and
makes room for additional open corpora and real morphology.

## Three dimensions

Every downloadable deck belongs to three independent dimensions:

1. **Pair** - language being learned and language used for meanings.
2. **Collection** - the kind of source material the learner asked for.
3. **Level** - beginner, middle or advanced.

The initial collections are:

| id | display name | initial source family | intended material |
| --- | --- | --- | --- |
| `everyday` | Everyday | Tatoeba | short, general and everyday language |
| `knowledge` | Knowledge | WikiMatrix / Wikipedia | neutral and explanatory language |
| `world` | World | Global Voices | journalism, society and culture |

A collection is a product category, not a permanent alias for one corpus. A
future source can join a collection without inventing a fourth user-facing
category. Source provenance remains separate and explicit.

## File names and deck ids

New v2 deck ids include the collection so different collections cannot collide:

```text
en-ru-everyday-beginner
en-ru-knowledge-beginner
en-ru-world-beginner
```

The asset name is the deck id plus `.jsonl`.

The old v1 naming scheme remains readable. The v2 rollout may replace the old
assets in the same `catalog` release only after the application and the bundled
starter-deck pin have been updated together.

## Index contract

`index.json` keeps every field older ikna builds already understand. v2 adds
collection and source registries plus explicit catalogue metadata.

The compatibility fields remain:

- `version`
- `builtAt`
- `segmentation`
- `decks`
- `pairs`

For v2, `version` is `2`. `catalogueVersion` is also `2`; it names the public
catalogue generation independently of future small schema additions.

New top-level fields:

- `catalogueVersion`: public catalogue generation.
- `collections`: user-facing collection definitions.
- `sourceFamilies`: provenance and licensing definitions for active sources.
- `targetIdentity`: versioned rules used to create `targetId` values.

A deck adds:

- `collection`: collection id.
- `sourceFamily`: source-family id for this deck.

The existing human-readable `licence`, `attribution` and `sources` fields stay in
place. Older clients therefore continue to show provenance before download even
though they do not understand the new structured registries.

A v2 publication must never contain a source family without a resolved licence
and attribution policy. Planned sources may be documented in this repository,
but only active, audited sources belong in a published `index.json`.

## Card contract

A v2 JSONL line is still a `PackChunk`. All v1 fields remain valid and keep their
meaning:

```text
id
text
context
translation
targetStart
targetEnd
freqRank
tokens
ipa
ipaContext
```

v2 can add these fields:

- `targetId`: stable opaque identity of the learning target under the declared
  target-identity algorithm.
- `contextId`: stable source reference for the learned-language sentence.
- `meaningId`: stable source reference for the meaning-language sentence or
  aligned segment when one exists.
- `sourceFamily`: source-family id matching the index registry.

The old source credit remains appended to `translation` during the compatibility
period. Structured provenance does not remove information an older build relies
on.

### Target identity

`targetId` is produced offline. The app never invents or merges target identities.

The first v2 identity method is intentionally conservative:

```text
NFKC(target text) + Unicode case-folding + learning language
```

No morphology or semantic similarity is inferred by this first method. Exact
surface groups are already useful and are much safer than merging related forms
without evidence.

The index records an identity version and method. A later morphology-aware method
must increment the identity version instead of silently changing the meaning of
existing ids.

`targetId` is opaque to the application. The reference implementation uses a
short SHA-256 derived id so two million cards do not need to repeat a long
canonical key.

### Context identity

`contextId` identifies the source occurrence, not merely the sentence text.
Different source records that happen to contain the same text remain distinguishable
for provenance and deduplication.

Each source adapter defines how its stable reference is formed. Examples include a
Tatoeba sentence id or a stable article/segment reference. If a source cannot
provide a stable public id, the adapter must document the deterministic derived
reference it uses.

## Morphology

The existing token fields remain:

```text
surface
lemma
pos
isContent
```

v2 may enrich a token with:

- `upos`: Universal Dependencies universal POS tag when known.
- `feats`: morphology in canonical CoNLL-U `FEATS` form, for example
  `Mood=Ind|Number=Plur|Person=1|Tense=Pres|VerbForm=Fin`.
- `lemmaSource`: provenance for the lemma/morphology decision, such as
  `unimorph`, `ud`, `wiktextract` or `identity`.

No fabricated probability is required. When morphology cannot be resolved
reliably, the existing identity lemma is allowed and `lemmaSource` records that
fact. Unknown is preferable to a false merge.

The coarse `pos` and `isContent` fields are retained because existing releases and
the component-memory code already consume them. `upos` and `feats` are enrichment,
not a replacement in the v2 migration.

## Provenance and licences

Provenance is split into two levels:

1. The index states what a source family is, its homepage, licence and required
   attribution.
2. Each card carries enough source identity to trace its context and meaning back
   to that source family.

The card does not repeat full licence prose. Repeating long legal strings across
millions of JSONL lines wastes space and makes corrections harder. The deck index
is the authoritative licence registry; the card keeps stable source references
and the compatibility credit line.

A deck is initially restricted to one source family. This keeps its licence and
attribution understandable before download. If a later collection combines
sources inside one asset, the schema must be extended deliberately rather than
hiding a mixed licence behind one string.

## Backward compatibility

Compatibility is a hard requirement for the v2 rollout.

Existing readers use Kotlin serialization with `ignoreUnknownKeys = true` for both
the catalogue index and pack JSONL. Therefore new optional fields do not make an
older app reject the catalogue.

During the transition:

- all v1 required deck fields remain present;
- all v1 required card fields remain present;
- `licence`, `attribution` and `sources` remain populated;
- the source credit remains inside `translation`;
- no individual deck may exceed the current 24 MiB app download cap;
- `index.json` must remain below the current 2 MiB app cap;
- the bundled starter pack is repinned only when v2 is actually published.

Older builds will not have a collection filter. For that reason v2 titles must be
unambiguous even when all collections are shown together.

## Publication rules

Part 1 specifies the format only. It does not publish v2 assets yet.

The eventual rollout is atomic from the index's point of view:

1. Build and validate all v2 assets.
2. Upload deck assets to the existing `catalog` release.
3. Publish `index.json` last.
4. Rename the release title to `Catalogue v2` as part of the v2 publish.
5. Repin the bundled starter deck and run application CI.

The existing publisher already sends `index.json` last. That rule remains.

## What v2 does not decide yet

This specification deliberately does not define:

- how WikiMatrix or Global Voices are filtered;
- the final licences/attribution strings for sources not yet audited;
- which morphology source wins when several analyses disagree;
- when a learner receives a new context;
- whether a novel-context result changes FSRS or grading;
- semantic embeddings or LLM-generated relationships.

Those are later parts of the 0.11 work. The schema makes them possible without
pretending they have already been solved.

## Machine-readable contract

The repository contains JSON Schemas and fixtures under
`tools/catalog/schema/` and `tools/catalog/fixtures/v2/`. The Python contract test
checks the examples and the compatibility invariants without adding a runtime or
CI dependency on a JSON Schema package.
