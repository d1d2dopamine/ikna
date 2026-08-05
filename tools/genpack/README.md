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

## Languages

`--lang` selects a profile:

| Profile | Lemmas | Function words |
| --- | --- | --- |
| `en` | suffix rules plus an irregular-verb table | `FUNCTION_WORDS` |
| anything else (`pl`) | surface form, lowercased | `PL_FUNCTION_WORDS` for `pl` |

Polish gets no suffix stripping on purpose. Its morphology needs a real analyser (Morfeusz), and a
guessed lemma is worse than no lemma: it merges unrelated words in the component layer, so credit
for an answer leaks to words that were never involved. The tokeniser itself is Unicode-aware — the
old `[A-Za-z]` pattern quietly ate every diacritic and turned `śniadaniem` into `niadaniem`.

## Shipping a second deck

Pass `--inactive` so the pack is installed but switched off. Two active decks interleave two
languages inside one session; the user turns the new one on in *Колоды* when they want it. The
flag only affects a first install — a deck the user has already switched keeps their choice when a
newer version of the pack is installed.
