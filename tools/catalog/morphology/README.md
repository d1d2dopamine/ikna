# Catalogue v2 morphology

Part 3 enriches catalogue tokens offline. It does not add a morphology engine to
Android or desktop and it does not change `targetId` in morphology rule version 1.

The pipeline uses two evidence sources with different jobs:

- **UniMorph** supplies form-to-lemma evidence. A surface form is accepted only
  when all UniMorph rows for that form agree on one lemma.
- **Universal Dependencies** supplies lemma, UPOS and canonical CoNLL-U FEATS.
  An exact sentence match may resolve a form in context. Otherwise a form-level
  value is emitted only when the selected UD data are unanimous. Syncretic or
  conflicting forms stay unresolved.

UniMorph feature bundles are not relabelled as UD FEATS. The two schemas are not
identical, so v1 keeps the UniMorph bundle inside the build index for audit and
uses UD when a public card needs `upos` or `feats`.

## Pinned inputs

Every real build uses a manifest with a source version, licence, attribution and
SHA-256 for every input file. UniMorph licences vary by language dataset and UD
licences vary by treebank, so neither project has one blanket licence that ikna
can safely assume. NC, ND and unknown licences are rejected by the build gate.

The source-family audit is in
`../sources/catalogue-v2-morphology-sources.json`. The fixture manifest in
`../fixtures/morphology/manifest.json` uses synthetic CC0 test data only.

## Commands

Build a disk-backed morphology index:

```bash
python3 tools/catalog/enrich_morphology.py build-index \
  --manifest work/morphology-manifest.json \
  --db work/morphology.sqlite3
```

Enrich a deck without changing card ids, target ids, offsets, coarse `pos` or
`isContent`:

```bash
python3 tools/catalog/enrich_morphology.py enrich \
  --db work/morphology.sqlite3 \
  --lang es \
  --input work/es-ru-knowledge-beginner.jsonl \
  --output work/es-ru-knowledge-beginner.morph.jsonl \
  --report work/es-ru-knowledge-beginner.morph.json
```

Rule v1 deliberately prefers missing morphology to a guessed lemma. The order is:

1. one unambiguous exact UD sentence annotation;
2. unanimous form-level evidence;
3. the existing lemma if it was already non-identity;
4. identity lemma.

If UniMorph proves that a form has several lemmas, a form-level UD observation is
not allowed to erase that ambiguity. Only an exact UD context may resolve it.
