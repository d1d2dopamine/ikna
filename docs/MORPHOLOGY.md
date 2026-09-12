# Catalogue morphology

Catalogue v2 enriches token metadata offline. The app does not ship a morphological
model and does not contact a morphology service while somebody studies.

Morphology rule version 1 has one bias: **unknown is better than a false lemma**.
The output may therefore be less complete than a general NLP tagger. That is
intentional because later target grouping can amplify one wrong lemma into many
wrong relationships.

## Sources

Two open morphology projects are supported by the Part 3 pipeline:

- **UniMorph** provides inflectional paradigms: dictionary lemma, written form and
  a UniMorph feature bundle. Dataset licences are checked per language repository.
- **Universal Dependencies (UD)** provides corpus annotations in CoNLL-U, including
  lemma, universal POS and morphological FEATS. UD is mixed-licence, so each
  selected treebank is audited independently.

There is no blanket "UniMorph licence" or "UD licence" in the build. A production
manifest must pin every input by source version and SHA-256 and name its actual
licence. The current gate accepts CC0, CC BY and CC BY-SA variants that permit
redistribution; NC, ND and unknown licences fail before indexing.

The audit policy is machine-readable in
`tools/catalog/sources/catalogue-v2-morphology-sources.json`.

## Resolution policy v1

The rule is `ud-exact-context-then-unanimous-form`.

1. If an allowed UD treebank contains the exact catalogue sentence and every
   matching annotation agrees, its lemma, UPOS and FEATS can be used for that
   occurrence.
2. Otherwise the token is resolved by surface form only when the available
   morphology evidence is unambiguous.
3. UniMorph contributes a lemma only when all rows for that written form agree on
   one lemma.
4. Form-level UD evidence contributes a lemma only when all selected observations
   agree. UPOS is emitted only when those compatible observations agree on UPOS;
   FEATS is emitted only when the whole canonical FEATS bundle agrees.
5. If UniMorph exposes several possible lemmas, a form-level UD sample is not
   allowed to erase that ambiguity. An exact-context UD observation may resolve
   it because it describes that particular use.
6. A pre-existing non-identity lemma is preserved unless exact-context UD evidence
   describes the occurrence directly.
7. Otherwise the token keeps its identity lemma and records `lemmaSource=identity`.

UniMorph feature bundles are deliberately not converted into CoNLL-U FEATS in
rule v1. The two feature systems are related but not interchangeable field by
field. Keeping them separate avoids manufactured precision.

## Card fields

The existing fields stay valid:

```text
surface
lemma
pos
isContent
```

Enrichment may add:

```text
upos
feats
lemmaSource
```

`pos` and `isContent` are not overwritten. They are part of the existing component
memory contract, while `upos` and `feats` describe linguistic analysis.

`lemmaSource` in rule v1 is one of the effective evidence classes such as
`unimorph`, `ud`, `unimorph+ud`, `identity` or `legacy`. Full dataset provenance is
stored once in the catalogue index rather than repeated on millions of tokens.

## Reproducibility

A morphology manifest records:

- rule version;
- dataset id and source family;
- learning-language code;
- pinned source version;
- source URL;
- licence and attribution;
- each local input file and SHA-256.

`tools/catalog/enrich_morphology.py build-index` verifies the manifest and builds a
SQLite lookup database. The database is build material, not a catalogue asset.
`enrich` then writes enriched JSONL plus a report naming the exact datasets used.

Rule version 1 does **not** change `targetId`. Target identity remains the exact
NFKC + case-fold method from Catalogue v2 Part 1. If a later release uses morphology
to merge targets, the target-identity version must change separately and that
policy must be validated before publication.
