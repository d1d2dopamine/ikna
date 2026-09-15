# 0.11 corpus policy

This document fixes the corpus scope for the remainder of the 0.11 cycle and
keeps corpus acquisition separate from target selection and learner policy.

The short version is:

```text
human parallel material
    -> source adapter
    -> normalized sentence pair
    -> quality / provenance sieve
    -> target extraction
    -> Catalogue v2 target + one or more real contexts
```

A corpus does **not** need to ship ready-made flash cards. It needs to supply
real text, a trustworthy aligned meaning, stable provenance and a licence that
allows ikna to redistribute the retained material. Catalogue v2 then decides
which target inside that material is useful to study.

## Terms used in the plan

- **Source row / sentence pair**: one learned-language utterance or sentence and
  its aligned meaning-language text. This is raw material, not a study card.
- **Target**: the exact learned-language item ikna chooses to study, for example
  `umbrella` inside `I forgot my umbrella at home.`
- **Context**: the real source sentence in which that target occurred.
- **Target/deck membership**: one target present in one downloadable deck. This
  is what the current reports call a card in a deck.
- **Alternative context**: another real source occurrence for the same target;
  it does not create another FSRS learner memory.

## Fixed source scope for 0.11

The source list below is deliberately small. 0.11 will measure these sources
properly rather than continuously adding corpora whenever a thin deck appears.

### Everyday

#### Tatoeba - production source, keep

Role: broad everyday/general parallel sentences.

Status: already approved, implemented and used by the current scale build.

The existing weekly export, stable sentence ids and contributor-aware provenance
remain the baseline. Tatoeba is not replaced by another corpus.

#### MASSIVE 1.1 - experimental production candidate

Role: fill Everyday coverage holes with a multiway parallel dataset: the original
English SLURP seed plus human-localized, human-reviewed utterances in the other
supported MASSIVE locales, especially for direct pairs that are thin in Tatoeba.

Why it is useful:

- MASSIVE 1.1 contains more than one million utterances across 52 languages; the localized languages are roughly 19.5k rows each, while `en-US` is the smaller original SLURP seed;
- all eleven languages currently supported by ikna are represented;
- the material is parallel, so the same underlying utterance can be paired
  directly between two supported locales without inventing a translation pivot;
- localized source rows include human judgments for naturalness, spelling, target-language identity and intent fit;
- the dataset is published under CC BY 4.0.

Evidence checked for this decision:

- https://github.com/alexa/massive
- https://huggingface.co/datasets/AmazonScience/massive

Important limitation: MASSIVE is voice-assistant material. It is human language,
but its domain mix is narrower than Tatoeba. Therefore MASSIVE must **supplement**
Everyday rather than dominate it.

MASSIVE is implemented as an experimental source, but it is not automatically
publishable. Its registry status is `candidate`; the normal Catalogue v2 builder
refuses candidate sources. The completed 0.11 review found that the first gate
missed an important distinction: MASSIVE is an NLU localization corpus, so a
slot value may deliberately change to a different local person, place, artist or
service while the intent remains correct. That is useful upstream behavior but
is unsafe as a literal bilingual learning context.

The experimental adapter now requires at least two positive votes for naturalness,
spelling, target-language identity, intent **and slot correctness**. It also
requires `annot_utt` and `slot_method` to agree and rejects rows containing any
upstream `slot_method = localization`. The original `en-US` SLURP seed remains
unjudged by design and skips only this localization-specific gate. These stricter
rules are retained for future evidence runs, but the 0.11 admission decision is
conservative: **MASSIVE is not admitted; Tatoeba remains the production Everyday
source for the Part 11 freeze.** See `PARTS-6-9-DECISION-RECORD.md`.

### Knowledge

#### WikiMatrix v1 - production source, expand usage

Role: Wikipedia-derived neutral/explanatory material.

Status: already approved and implemented. The current scale build uses a bounded
English-hub subset; that is a runner/build choice, not the final definition of
Knowledge.

The next catalogue work must first inventory direct WikiMatrix files among the
supported eleven languages and measure their usable rows. A real direct pair may
be used in both directions. ikna must **not** manufacture a `ko -> pl` meaning by
pivoting through English.

WikiMatrix reports 135 million mined parallel sentences across 1,620 language
pairs. The alignment score remains provenance/quality evidence. Part 8 keeps the
decision in `tools/catalog/sources/catalogue-v2-wikimatrix-quality.json`: every
physical pair can remain under review, be accepted with its own minimum score, or
be rejected entirely. The first 55-pair diagnostic showed that `1.04` is too
permissive for some pairs. The next review pool therefore uses explicit provisional
floors of `1.10`, `1.11`, or `1.12` according to the observed pair quality band.
All 55 rules still say `review`; the floors are filters for human review, not a
claim that a pair is production-safe. The second retained-sample review still
found obvious semantic mismatches above those floors, including mismatches above
1.20. Therefore the all-direct-pair expansion is **not admitted for 0.11**. The
already approved production WikiMatrix scope remains; score-only retuning is not
accepted as a substitute for an independent semantic-quality signal.

Evidence:

- https://github.com/facebookresearch/LASER/tree/main/tasks/WikiMatrix
- https://dl.fbaipublicfiles.com/laser/WikiMatrix/v1/

### World

#### Global Voices - production source after attribution completion

Role: journalism, society and culture.

Status: already approved by the source registry, but final publication remains
blocked until every retained segment can carry the canonical article URL and
credited contributors required by the existing Catalogue v2 gate.

The source is not allowed to fall back to corpus-level attribution just to make
World non-empty. Part 9 therefore separates native aligned text from article
attribution: XCES document names **and sentence ids** are resolved directly against
the native OPUS XML archives, rather than assuming that XCES link N equals Moses
line N. `globalvoices_manifest.py` now constructs the single date/slug URL candidate
encoded by an OPUS document id and verifies it against the live Global Voices
page. A document is emitted only when the fetched/resolved URL remains on
`globalvoices.org`, a canonical Global Voices article URL is recovered, and at
least one credited contributor is present in page metadata/markup. Missing or
unverifiable documents are reported and their aligned rows are excluded; URLs or
names are never inferred from sentence text.

Evidence:

- https://globalvoices.org
- https://wiki.creativecommons.org/wiki/Global_Voices_Online
- https://opus.nlpl.eu/datasets/GlobalVoices

### Quality reference, not a production source

#### FLORES-200

Role: small professionally translated multilingual reference set used to sanity
check alignment/segmentation and to calibrate manual expectations for multilingual
quality.

FLORES-200 contains 3,001 sentences translated from 842 web articles and is
published under CC BY-SA 4.0. It is intentionally **not** used to inflate final
Catalogue v2 deck counts in 0.11.

Evidence:

- https://github.com/facebookresearch/flores/blob/main/flores200/README.md
- https://github.com/facebookresearch/flores/blob/main/README.md

Keeping this reference set outside normal deck supply makes it more useful as an
independent quality check.

## Source admission gate

A production corpus may enter final 0.11 Catalogue v2 assets only when all of the
following are true:

1. **Redistribution is clear.** The text licence and required attribution are
   explicit enough for ikna's static public assets.
2. **Provenance is stable.** Every retained context has a source family and a
   stable record/source reference; sources that require record-level credit keep it.
3. **Alignment is real.** A meaning is a direct source alignment or a true
   multiway-parallel record. ikna does not create hidden machine-translation pivots.
4. **The source is reproducible.** Version/date/download identity is pinned well
   enough to rebuild the same catalogue generation.
5. **The normal sieve still applies.** A large corpus is not permission to relax
   target length, token, level, duplicate or malformed-text rules.
6. **Manual samples look like useful study material.** Technical validity alone
   is not enough.

A new source outside the fixed 0.11 list must wait for a later release unless a
currently approved source becomes unavailable or a genuine blocker makes the
planned catalogue impossible.

## Selection principles after sources are admitted

More source rows do not imply bigger decks automatically.

- Thin decks should first be diagnosed: source shortage, quality-sieve loss or a
  build cap are different problems.
- Rich decks should be ranked and selected, not filled until an arbitrary stop is
  reached.
- `8,000` remains a safety/selection budget while Part 10 measures a better final
  policy; it is not a quality target that every deck must reach.
- Multiple good contexts for one target are valuable; they do not become extra
  learner memories.
- No collection gets synthetic filler sentences to make its numbers look even.
- Source balance matters: MASSIVE cannot crowd out broad Tatoeba language merely
  because its parallel matrix is convenient.

## What is deliberately deferred

0.11 does not keep searching for more corpora once the fixed sources above have
been measured. DGT-TM, ParaCrawl-family data, subtitles and other possible sources
may be revisited later under the same licence/provenance/quality gate, but they are
not required to finish this release.
