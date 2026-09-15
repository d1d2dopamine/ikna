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
| `everyday` | Everyday | Tatoeba; MASSIVE only if it passes the 0.11 admission gate | short, general and everyday language |
| `knowledge` | Knowledge | WikiMatrix / Wikipedia | neutral and explanatory language |
| `world` | World | Global Voices | journalism, society and culture |

A collection is a product category, not a permanent alias for one corpus. A
future source can join a collection without inventing a fourth user-facing
category. Source provenance remains separate and explicit. The fixed 0.11 source
scope and admission rules are documented in [`CORPORA-0.11.md`](CORPORA-0.11.md);
adding a source there does not make it publishable until its adapter, provenance
and quality gate have passed.

## File names and deck ids

New v2 deck ids include the collection so different collections cannot collide:

```text
en-ru-everyday-beginner
en-ru-knowledge-beginner
en-ru-world-beginner
```

The asset name is the deck id plus `.jsonl.gz`. The payload is still the same
newline-delimited JSON contract after decompression; gzip is only a lossless
storage/transfer layer. The collection is intentionally part
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

## Part 5 supply census

The supply census is deliberately separate from a catalogue build. It uses the same normalized candidates, language frequency ranks and phrase sieve, but writes **reports only**. For each `collection / learning language / meaning language` pair it records:

- normalized candidate rows before exact-candidate deduplication;
- unique source contexts seen by the target sieve;
- every unique exact target eligible before `max_deck`;
- the targets the current capped first pass would select;
- current `min_deck` publication effect;
- sentence/translation-length rejection, no-usable-target rows, duplicate-target rows and cap-blocked rows.

WikiMatrix availability is measured separately from target supply. A missing direct upstream file is reported as such. If the workflow stops at its configured high-score row limit, that pair is marked `source-scan-lower-bound`; the count is evidence that **at least** that much material exists, not evidence of corpus scarcity. The workflow is `.github/workflows/catalogue-v2-supply-census.yml`; the report implementation is `tools/catalog/supply_census.py`. It has no publication step.

## Parts 8-10 content experiments

Part 8 removes the English-hub assumption from Knowledge evidence. The manual
`catalogue v2 knowledge experiment` workflow probes every direct WikiMatrix pair
among the requested languages, keeps the upstream alignment score, applies the
explicit pair policy in `catalogue-v2-wikimatrix-quality.json`, and then runs the
Part 10 selection policy over the retained preview. A pair can be rejected even
when its upstream file exists. No missing pair is manufactured through an English
pivot.

Part 9 keeps World fail-closed. `globalvoices_native.py` resolves XCES document
and sentence ids against the native OPUS XML archives. `globalvoices_manifest.py`
uses the date/slug encoded by a document id only to propose one Global Voices page,
then verifies the live page, canonical URL and contributor metadata. The resulting
manifest is consumed by `globalvoices_attribution.py`, which reports unresolved
document ids and filters aligned text so the existing adapter sees only fully
attributed rows. No URL or author is guessed from sentence text.

Part 10 is deliberately non-publishing until the full evidence run is reviewed.
`selection_experiment.py` stages candidates on disk, builds frequency ranks from
deduplicated natural contexts, measures all eligible exact targets before selection,
and then chooses targets deterministically. Frequency usefulness is primary; target
context evidence and independent source support break ties. When a morphology DB
is supplied, a confidently resolved lemma is used only to diversify the first pass
so several inflected surfaces of one lemma do not crowd out other useful targets.
The exact `targetId` is never merged or rewritten.

Alternate contexts are selected separately and near-duplicate sentences are
suppressed with an inspectable token-overlap rule. The 8,000 value is a safety
budget, not a fill target. Decks below `minDeck` are omitted with an explicit
reason, genuinely small decks above that threshold are reported as `publish-thin`,
and no source material is weakened or fabricated merely to reach a count.

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
product policy, not a claim that spelling proves sense identity. Part 13 may add
richer morphology/sense relations when corpus evidence supports them; any actual
identity split must be versioned rather than silently changing old ids.

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

That schema compatibility does **not** mean pre-0.11 clients can install the new
compressed assets: older `catalogDeckUrl` code only accepts `.jsonl`. Publication
therefore remains gated on shipping the 0.11 reader first (or atomically with the
catalogue rollout). The old public v1 catalogue stays untouched until then.

During the transition:

- all v1 required deck fields remain present;
- all v1 required card fields remain present;
- `licence`, `attribution` and `sources` remain populated;
- the source credit remains inside `translation`;
- no individual deck may exceed the current 24 MiB **decompressed** app import cap; if a rich multi-context deck would cross that ceiling, the builder keeps every selected learning target and its primary context, then removes only optional alternative contexts (largest byte contributions first) until the logical JSONL fits;
- v2 deck assets are then gzip-compressed deterministically (`mtime=0`). `sizeBytes` is the actual network size and `uncompressedSizeBytes` records the separately enforced import size;
- compressed decks carry three preview rows in `index.json`, so preview never needs to download or partially decode a gzip stream;
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

Before publication the census is run directly over the freshly built directory,
including `.jsonl.gz` assets, and is cross-checked against that build's
`BUILD.json`. A mismatch is fatal; a report from the old public `catalog` release
cannot accidentally pass as evidence for the new build.
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

## Part 4.3 storage experiment

The measured 8,000-target scale build made the physical-storage question
separate from the content model: self-contained deck assets total 3,108.4 MiB as
raw JSONL and 387.1 MiB as deterministic gzip. Gzip is therefore already doing
its job; another compression wrapper cannot remove content repeated across deck
assets.

`tools/catalog/storage_experiment.py` tests that question against an **existing**
Catalogue v2 build. It does not ingest corpora, rerun target selection, mutate the
input build or publish a new index. Every membership is split into a target-local
shell plus ordered context and meaning payloads, then reconstructed immediately;
the experiment aborts if any field other than the deterministic deck-local `id`
changes. `BUILD.json` membership/context totals are checked again when available.

Two layouts are measured:

- `pair`: context/meaning pools are shared only by decks with the same learning
  and meaning languages;
- `language`: pools are shared by every meaning language for one learning
  language. This is diagnostic only because its cold-install dependency is much
  larger.

The experiment reports total compressed bytes, exact payload reuse, and both
cold- and warm-deck costs. The current acceptance gate for considering pair
pooling is deliberately concrete: at most 220 MiB total and at most 24 MiB for
the largest cold one-deck dependency. Passing those numbers still does not make
the prototype a public format; client/index work would be a separate reviewed
change. Missing the gate means the self-contained gzip layout stays in place and
0.11 returns to the main plan instead of accumulating storage architecture.

The manual `catalogue v2 storage experiment` workflow can download the latest
successful `catalogue-v2` Actions artifact, or a specified run id, and uploads
only the Markdown/JSON measurement report. This keeps the experiment cheap and
avoids another corpus build.
## Part 4.4 / 4.5 inventory and readiness

The final Catalogue-foundation checkpoint makes the build inspectable without
requiring knowledge of internal names such as `targetDeckMemberships`.
`tools/catalog/meta_info.py` now begins with human-facing counts for **cards in all
decks**, **unique learning targets**, retained contexts and deck count. It records
the last entry in `index.json`, full per-deck cards/contexts/bytes, deck-size
distribution, cap-bound/thin decks, and collection/level/language/pair/source
breakdowns. The technical membership terminology remains in the same report so
the two meanings cannot be confused.

A deterministic sample (SHA-256 priority, up to 20 cards per deck by default) can
be emitted during the same scan. Samples preserve target/context/source fields
needed for manual content review without changing the catalogue or selecting new
material.

`tools/catalog/readiness_audit.py` consumes the machine-readable census and emits
`CATALOGUE-V2-READINESS.md`/`.json`. Its statuses have deliberately narrow meaning:

- `FAIL` -- structural/reproducibility blocker such as broken JSON, duplicate card
  ids, bad offsets, missing required fields/provenance, index/file mismatches or a
  deck over the 24 MiB decompressed client cap;
- `WARN` -- review evidence such as thin decks, long-text candidates or a deck near
  the import cap;
- `PASS` -- no configured blocker/warning remains.

The manual `catalogue meta-info` workflow defaults to the latest successful
`catalogue-v2` Actions artifact and can instead inspect the public `catalog`
release. A reviewed v2 publication also uploads `BUILD.json` and `BUILD.md` before
`index.json`, so a later read-only release census can cross-check the exact build.
Part 4.5 is the final planned Part 4 checkpoint; absent a real blocker, work moves
to Part 5 rather than adding more pre-policy catalogue machinery.

