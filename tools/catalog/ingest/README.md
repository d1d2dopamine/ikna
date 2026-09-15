# Catalogue v2 ingestion

This directory is the source-specific edge of Catalogue v2. It converts local
corpus dumps into one normalized candidate JSONL format. It does not download
corpora, choose chunks, assign levels or publish release assets.

The source registry is `../sources/catalogue-v2-sources.json`. Licence,
attribution and publication requirements come from that registry; adapters do not
supply their own legal metadata.

## Candidate boundary

Every adapter emits the same shape:

```text
collection
learning language
meaning language
context text
meaning text
one or more provenance origins
```

Candidate ids are deterministic exact-content ids. Exact duplicates merge only
inside one collection, and all distinct origins survive the merge. The merge
command uses SQLite so a full-catalogue run does not need to keep every candidate
in RAM.

## Tatoeba

The adapter supports both `sentences_detailed.csv` and the smaller
`sentences.csv`, together with `links.csv`. The detailed export retains
contributor names when present. The full Catalogue v2 workflow deliberately uses
the smaller sentence export so the hosted runner does not need to hold the much
larger detailed dump; stable Tatoeba sentence ids and corpus-level attribution
remain intact. Every production run pins an explicit weekly-export version/date.

For one direction:

```bash
python3 tools/catalog/ingest_sources.py tatoeba \
  --dump-dir corpus/tatoeba \
  --learn en --meaning es \
  --source-version 2026-09-05 \
  --out work/tatoeba-en-es.jsonl.gz
```

For a catalogue rebuild, use the matrix command. It loads the requested sentence
languages once, streams `links.csv` once and emits every requested directed pair.
Tatoeba exports reciprocal links, so the adapter consumes one canonical
orientation instead of materializing a second in-memory copy of the link graph.

```bash
python3 tools/catalog/ingest_sources.py tatoeba-matrix \
  --dump-dir corpus/tatoeba \
  --learn en,ru,es,fr,de \
  --meanings en,ru,es,fr,de \
  --source-version 2026-09-05 \
  --out work/tatoeba.jsonl.gz
```

## MASSIVE 1.1

MASSIVE is an experimental `Everyday` source for Parts 6-7. The official dump
contains one JSONL file per locale and uses a shared SLURP-derived `id` across
localizations. ikna aligns requested locales only by that id; it never pivots or
generates a translation.

Before normalization, each localized row passes a conservative human-judgment
and slot-equivalence gate. By default at least two judgments must rate it natural
(grammar 3-4), correctly spelled, target-language-only, consistent with the intent,
and correct for its slot annotations. `annot_utt` must agree with `slot_method`,
and any upstream slot explicitly marked `localization` is rejected because its
entity/value may intentionally differ across locales. MASSIVE intentionally
provides no localization judgments or slot methods for the original `en-US` SLURP
seed, so those English seed rows skip only this source-specific localization gate.
The ordinary Catalogue target sieve still runs for every language.

```bash
python3 tools/catalog/ingest_sources.py massive-matrix \
  --dump-dir corpus/massive \
  --learn en,ru,es,fr,de,it,pt,zh,ja,ko,pl \
  --meanings en,ru,es,fr,de,it,pt,zh,ja,ko,pl \
  --source-version 1.1-<archive-hash-prefix> \
  --out work/massive.jsonl.gz
```

MASSIVE remains `publication.status = candidate` until manual Part 6 review.
Candidate sources may be normalized for experiments, but the production builder
refuses to publish them.

## WikiMatrix

The adapter reads the upstream scored `WikiMatrix.xx-yy.tsv.gz` directly. The
physical column order is taken from the upstream filename, so requesting the
reverse learning direction swaps the two sentence columns instead of silently
mislabeling them. For a renamed/local TSV, pass `--tsv-langs` explicitly.

```bash
python3 tools/catalog/ingest_sources.py wikimatrix \
  --tsv corpus/WikiMatrix.en-fr.tsv.gz \
  --learn fr --meaning en \
  --min-score 1.04 \
  --max-rows 120000 \
  --out work/wikimatrix-fr-en.jsonl.gz
```

For a rebuild where both directions are wanted, `wikimatrix-pair` reads the
scored file once and emits both directions:

```bash
python3 tools/catalog/ingest_sources.py wikimatrix-pair \
  --tsv corpus/WikiMatrix.en-fr.tsv.gz \
  --first en --second fr \
  --min-score 1.04 \
  --max-rows 120000 \
  --source-version v1 \
  --out work/wikimatrix-en-fr.jsonl.gz
```

Upstream WikiMatrix files are score-sorted, so ingestion stops once the score
falls below the selected threshold. The score is preserved as provenance
metadata. `--max-rows` bounds accepted source rows for an experimental rebuild;
it is not a claim that the same cap is optimal for every pair.

## Global Voices

Aligned text by itself is not accepted for publication. A line-number keyed JSONL
sidecar must provide a canonical HTTPS article URL and a non-empty contributor
list for every retained pair:

```json
{"line":1,"articleUrl":"https://globalvoices.org/...","contributors":["Author","Translator"]}
```

```bash
python3 tools/catalog/ingest_sources.py globalvoices \
  --learn-file corpus/globalvoices/en.txt \
  --meaning-file corpus/globalvoices/es.txt \
  --attribution-file corpus/globalvoices/en-es.attribution.jsonl \
  --learn en --meaning es \
  --out work/globalvoices-en-es.jsonl
```

Part 2 defines and tests this gate. Building the attribution sidecar from the
upstream distribution/articles at catalogue scale is intentionally left to the
later acquisition/rebuild stage rather than faking attribution from corpus-level
metadata.

## Compressed candidates and merge

Candidate readers and writers accept both `.jsonl` and `.jsonl.gz`. Production
rebuilds use gzip for intermediate candidate streams so millions of rows do not
consume unnecessary runner disk space.

```bash
python3 tools/catalog/ingest_sources.py merge \
  work/a.jsonl.gz work/b.jsonl.gz \
  --out work/merged.jsonl.gz
```

For a long run, `--db work/dedupe.sqlite3` keeps the SQLite work file instead of
using a temporary database.

Run the contracts with:

```bash
python3 tools/catalog/test_ingestion.py
```

## Global Voices attribution recovery

OPUS aligned text alone is insufficient for the `world` collection because it can
lose article/contributor metadata. Part 9 therefore uses two explicit steps before
`ingest_sources.py globalvoices`:

1. `globalvoices_native.py` resolves XCES document/sentence ids directly against
   the native OPUS XML archives.
2. `globalvoices_manifest.py` reads the document ids, constructs only the
   date/slug URL encoded by each id, then fetches and verifies the live Global
   Voices page. It emits a manifest row only when the resolved/canonical URL is a
   real Global Voices HTTPS URL and credited contributor metadata is present.
3. `globalvoices_attribution.py` joins that verified manifest back to original
   aligned rows and filters out every unresolved document.

An explicit pre-audited manifest may still be supplied to the workflow. The tools
never derive authors or article URLs from sentence text, search results or fuzzy
title matching.
