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

Catalogue v2 prefers `sentences_detailed.csv` plus `links.csv`. The detailed
export lets the adapter retain contributor names when present. It can parse the
smaller `sentences.csv` as a compatibility fallback, but production ingestion
must pin an explicit weekly-export version/date.

```bash
python3 tools/catalog/ingest_sources.py tatoeba \
  --dump-dir corpus/tatoeba \
  --learn en --meaning es \
  --source-version 2026-09-05 \
  --out work/tatoeba-en-es.jsonl
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
  --out work/wikimatrix-fr-en.jsonl
```

The score is preserved as provenance metadata. The final threshold belongs to the
later quality-sieve stage and is not fixed by the ingestion contract.

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

## Merge

```bash
python3 tools/catalog/ingest_sources.py merge \
  work/a.jsonl work/b.jsonl \
  --out work/merged.jsonl
```

For a long run, `--db work/dedupe.sqlite3` keeps the SQLite work file instead of
using a temporary database.

Run the contracts with:

```bash
python3 tools/catalog/test_ingestion.py
```
