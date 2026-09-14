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

#### MASSIVE 1.1 - planned production candidate

Role: fill Everyday coverage holes with professionally localized, parallel
utterances, especially direct pairs that are thin in Tatoeba.

Why it is useful:

- MASSIVE 1.1 contains more than one million utterances across 52 languages;
- it contains 19,521 utterances per language;
- all eleven languages currently supported by ikna are represented;
- the material is parallel, so the same underlying utterance can be paired
  directly between two supported locales without inventing a translation pivot;
- Amazon describes the dataset as localized by professional translators;
- the dataset is published under CC BY 4.0.

Evidence checked for this decision:

- https://www.amazon.science/code-and-datasets/massive
- https://www.amazon.science/blog/amazon-releases-51-language-dataset-for-language-understanding
- https://huggingface.co/datasets/AmazonScience/massive
- https://github.com/alexa/massive

Important limitation: MASSIVE is voice-assistant material. It is human language,
but its domain mix is narrower than Tatoeba. Therefore MASSIVE must **supplement**
Everyday rather than dominate it.

MASSIVE is admitted to the 0.11 implementation queue, but it is not automatically
publishable. Before its rows can enter final Catalogue v2 assets, Part 6 must
measure how many new targets and useful contexts survive the normal sieve and a
manual review sample must confirm that the resulting cards are natural and useful.
If the quality gate fails, 0.11 keeps Tatoeba-only Everyday rather than lowering
quality to fill counts.

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
pairs. The alignment score remains provenance/quality evidence and its final
threshold must be chosen from measured samples rather than copied blindly across
all pairs.

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
World non-empty.

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
