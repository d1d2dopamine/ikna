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

The asset name is the deck id plus `.jsonl`. The collection is intentionally part
of the local deck identity too. A v2 `Everyday` deck is not imported over an
already installed v1 deck: the rebuild changes card membership and positional
card ids, so reusing the old pack id could attach existing review history to
different content. The old deck therefore keeps its history and can be disabled
independently after the learner installs its v2 replacement.

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
- `contextCount`: primary plus alternative natural contexts retained by the deck.

`chunkCount` remains the compatibility field name, but in v2 it counts **learning
target memberships**, not every retained context. A deck with 3,000 target rows and
7,400 natural contexts therefore reports `chunkCount: 3000` and
`contextCount: 7400`.

The existing human-readable `licence`, `attribution` and `sources` fields stay in
place. Older clients therefore continue to show provenance before download even
though they do not understand the new structured registries.

A v2 publication must never contain a source family without a resolved licence
and attribution policy. Planned sources may be documented in this repository,
but only active, audited sources belong in a published `index.json`.

## Ingestion boundary

Part 2 adds an offline source-normalization layer before deck selection. Source
adapters do not emit learner cards and do not decide levels or targets. They turn
local corpus dumps into one small candidate contract:

```text
source dump
    -> source adapter
    -> normalized candidate pair
    -> exact deduplication within a collection
    -> later quality sieve / target extraction / deck build
```

A normalized candidate contains the learned-language segment, its aligned meaning,
the collection it belongs to and one or more source origins. Exact duplicates may
merge inside one collection, but every origin is retained. The same text in two
different collections remains two candidates because the learner-facing collection
choice is intentional.

The machine-readable source registry is
`tools/catalog/sources/catalogue-v2-sources.json`. It is the only place an adapter
gets a source licence, attribution rule, collection and publication requirements.
Adapters cannot invent a licence from a dump filename. The registry currently
audits Tatoeba, WikiMatrix and Global Voices, rejects unknown policy fields and
allowlists the content licences accepted by this pipeline.

Tatoeba ingestion prefers the weekly `sentences_detailed.csv` export so contributor
names can be retained with stable sentence ids. Production ingestion also requires
an explicit export version/date instead of recording the moving word `weekly`. The
smaller `sentences.csv` remains parseable for fixtures and compatibility work.

Global Voices has an extra gate: an aligned text pair alone is not publishable. A
record must also retain a canonical article URL and credited contributors. The
adapter therefore requires an attribution sidecar and fails on the first missing
record. This keeps a convenient OPUS text download from silently stripping the
information required for attribution.

WikiMatrix keeps its alignment score. The adapter understands the upstream v1
`score<TAB>sentence<TAB>sentence` TSV directly (including `.gz`) and can also read
an already split aligned pair. Upstream TSVs store columns in filename order, so
the adapter infers that order from `WikiMatrix.xx-yy.tsv.gz` and safely swaps the
segments when the requested learning direction is reversed. A renamed TSV must
declare its physical column languages explicitly. Score thresholds are an
ingestion/build parameter, not part of the public card schema; the final threshold
is intentionally left to the later quality-sieve stage.

Catalogue-scale exact deduplication is disk-backed through SQLite rather than a
Python set of millions of full records. Duplicate candidates inside one collection
merge their provenance origins; candidates in different collections remain separate.

The reference CLI is `tools/catalog/ingest_sources.py`; command examples and the
intermediate contract are documented in `tools/catalog/ingest/README.md`. The CLI
performs no network requests. Downloading large corpora remains a workflow
responsibility so a failed source download cannot be confused with a parsing or
licence decision.

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

- `targetId`: stable opaque catalogue-global identity of the exact learning target
  under the declared target-identity algorithm. The same exact target keeps this
  id across collection, level and meaning-language decks.
- `contextId`: stable source reference for the learned-language sentence.
- `meaningId`: stable source reference for the meaning-language sentence or
  aligned segment when one exists.
- `sourceFamily`: source-family id matching the index registry.
- `contexts`: zero or more additional natural source contexts for the same target.

One JSONL line is therefore one **target membership in a deck**, not one source
context. The v1-compatible `context`/`translation` fields hold the primary context;
additional contexts live in `contexts[]` and do not create extra scheduling rows.
Older readers ignore `contexts[]` and still receive one usable primary card.

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
without evidence. The identity is catalogue-global: deck id, collection, level and
meaning language are deliberately absent from the hash input. A deck is therefore
a membership/view over targets, not the owner of target identity.

An exact written form is not automatically a proven semantic sense. A polysemous
form such as `bank` can still need sense separation later. Part 4.1 deliberately
uses this exact `targetId` as the shared learner-memory key across installed v2
decks, because duplicate Beginner/Middle/collection memberships must not create
parallel FSRS histories for the same exact target. This is an explicit temporary
product policy, not a claim that spelling proves sense identity. Part 5 may split
ambiguous forms when corpus evidence supports a safer sense distinction; such a
change must be versioned rather than silently changing old ids.

The index records an identity version and method. A later morphology-aware method
must increment the identity version instead of silently changing the meaning of
existing ids.

`targetId` is opaque to the application. The reference implementation uses a
short SHA-256 derived id so a million-scale catalogue does not need to repeat a long
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

Part 3 implements morphology as an offline, pinned enrichment step. It does not
change `targetId` and does not put a morphology model in the app. The public token
contract remains:

```text
surface
lemma
pos
isContent
upos          # optional
feats         # optional canonical CoNLL-U FEATS
lemmaSource   # optional evidence class
```

Rule version 1 uses UniMorph for conservative form-to-lemma evidence and Universal
Dependencies for lemma/UPOS/FEATS evidence. An exact, unambiguous UD sentence match
may resolve a token in context. Outside an exact context, a written form is enriched
only when the selected datasets agree. Known ambiguity or source disagreement falls
back to the existing/identity lemma. `pos` and `isContent` are never rewritten.

UniMorph feature bundles are not silently converted to UD FEATS. The raw UniMorph
bundle can stay in the local build index for audit, while public `feats` comes from
UD in rule v1. This keeps two different annotation systems from being presented as
if they were identical.

Morphology inputs are pinned by source version and SHA-256. UniMorph licences vary
by language dataset and UD licences vary by treebank, so every production dataset
has its own licence and attribution record. NC, ND and unaudited inputs are rejected
before indexing. The catalogue index can publish one `morphology` block containing
the rule version, policy and dataset registry; individual decks can name the dataset
ids they used through `morphologySources`. Full strings are not repeated per token.

The reference pipeline is `tools/catalog/enrich_morphology.py`; the full contract is
in [`MORPHOLOGY.md`](MORPHOLOGY.md).

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

Parts 1 through 3 specify the format, ingestion boundary and offline morphology
enrichment. They do not publish v2 assets yet.

The eventual rollout is atomic from the index's point of view:

1. Build and validate all v2 assets.
2. Upload deck assets to the existing `catalog` release.
3. Publish `index.json` last.
4. Rename the release title to `Catalogue v2` as part of the v2 publish.
5. Repin the bundled starter deck and run application CI.

The existing publisher already sends `index.json` last. That rule remains.

## What v2 does not decide yet

This specification deliberately does not define:

- the final WikiMatrix alignment-score threshold or other corpus-specific quality cutoffs;
- how Global Voices record-attribution sidecars are extracted from the upstream distribution at scale;
- when a learner receives a new context;
- whether a novel-context result changes FSRS or grading;
- semantic embeddings or LLM-generated relationships.

Those are later parts of the 0.11 work. The schema makes them possible without
pretending they have already been solved.

## Machine-readable contract

The repository contains JSON Schemas under `tools/catalog/schema/`, v2 pack/index
fixtures under `tools/catalog/fixtures/v2/`, source-ingestion fixtures under
`tools/catalog/fixtures/ingest/`, morphology fixtures under
`tools/catalog/fixtures/morphology/`, and audited content/morphology source
registries under `tools/catalog/sources/`. The Python contract tests check
the examples, source gates, deduplication and compatibility invariants without
adding a runtime or CI dependency on a JSON Schema package.

## Full rebuild and census

Part 4 adds `tools/catalog/build_catalogue_v2.py` and the manual
`catalogue v2 build` workflow. The legacy `catalog.yml` publisher remains in place
as the known-good v1 path until a v2 artifact has been reviewed. The v2 workflow
defaults to `publish=false`.

The first large build uses two automatically publishable collections:

- **Everyday**: one streaming pass over Tatoeba sentences and reciprocal direct
  translation links; stable Tatoeba sentence ids become `contextId`/`meaningId`.
- **Knowledge**: bounded WikiMatrix v1 hub pairs. One downloaded TSV is read once
  and emits both learning directions. The default margin threshold is 1.04 and the
  workflow caps retained aligned rows per hub pair so the build has a deterministic
  disk/time ceiling.

`World` is represented in the v2 collection registry but the automatic rebuild does
not emit Global Voices decks yet. Part 2's record-attribution gate remains binding:
article URL and contributor data must survive ingestion before a World deck can be
published.

Part 4.1 keeps the mature v1 selection rules explicit and testable. Catalogue v2
uses the same language set, length limits, function-word cut, level boundaries, ICU
segmentation and UTF-16 offset rules through `tools/catalog/catalogue_core.py`; a
parity test compares those primitives against the v1 builder so a v2 refactor cannot
quietly change the old sieve. Phonetics still uses the existing
`tools/phonetics/g2p.py` pipeline when enabled.

The v2 builder no longer turns every retained natural context into another deck
row. It selects up to the configured number of **unique targets** per level, keeps
one primary natural context on that row, and can attach additional distinct source
contexts in `contexts[]` (three total contexts per target by default). The same
`targetId` is reused across decks whenever the learning language and exact
normalized target are the same.

The build emits `BUILD.json` and `BUILD.md` with separate counts for unique exact
targets, target-deck memberships, retained natural contexts and unique source
contexts. The workflow then runs `catalogue meta-info` over the finished deck files.
It does not generate the large all-groups JSONL unless someone explicitly asks the
standalone census workflow for it.

GitHub release constraints are treated as build constraints: index/deck size caps
remain enforced and the builder refuses an asset count close to the release limit.
The initial WikiMatrix hub plan is intentionally narrower than every possible
language pair for the same reason.
