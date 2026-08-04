# Pack generator

Content is data, not code. This script turns a flat TSV of chunks into the
JSONL pack the app ships in `app/src/main/assets/packs/`.

Everything expensive happens here, offline, once:

- tokenisation
- lemmatisation and part-of-speech tagging
- content-word vs function-word classification
- target span resolution
- frequency ranking
- duplicate and sanity checks

The phone never does morphology. That is the whole reason the component layer
is cheap enough to ship.

## Usage

```sh
python3 tools/genpack/generate_pack.py \
  --seed tools/genpack/seed_chunks.tsv \
  --out app/src/main/assets/packs \
  --pack-id en-ru-core \
  --version 1
```

## Seed format

Tab separated, three columns, no header:

```
phrase <TAB> carrier sentence containing the phrase <TAB> translation of the phrase
```

The carrier sentence must contain the phrase verbatim; the generator resolves
the character span and refuses rows where it cannot.

## Growing the pack

This is where an LLM belongs: generate thousands of rows in this TSV in one
batch, validate with this script, commit the artefact. It is explicitly not
something the app does at runtime, and not something the user ever does.
