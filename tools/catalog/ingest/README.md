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
