# 0.11.0 press roadmap

Theme: **evidence first**.

Operational checkpoint: [`PLAN-0.11.md`](PLAN-0.11.md). Corpus/source scope:
[`CORPORA-0.11.md`](CORPORA-0.11.md). Unscheduled, easily rejectable ideas are
kept separately in [`UNSCHEDULED.md`](UNSCHEDULED.md) and are not release
commitments.

0.11 is primarily an architecture, content-data and learning-science release.
The interface is not the project to optimize in this cycle unless a
learning-engine change requires a small UI adjustment.

The original roadmap grouped too much work into Parts 5-8. The current full
Catalogue v2 census exposed both very thin decks and decks truncated by the
8,000-target cap, so the remaining work is now split into smaller integer parts.
This is deliberate: ordinary findings should be absorbed by the planned owner
part, not create a chain of `Part 5.1`, `5.2`, etc.

## Work parts

### Part 1 - Catalogue v2 contract

Status: complete; specified in [`CATALOGUE-V2.md`](CATALOGUE-V2.md).

- Keep the existing GitHub release tag `catalog`.
- Rename that release to `Catalogue v2` only when v2 assets are actually published.
- Add the `Everyday`, `Knowledge` and `World` collection dimension.
- Preserve v1 index/card fields so older builds can still read v2 files.
- Define structured source provenance, target/context identity and morphology fields.
- Keep the current 2 MiB index cap and 24 MiB per-deck cap during migration.

### Part 2 - corpus ingestion boundary

Status: complete for Tatoeba, WikiMatrix and the Global Voices attribution gate.

- Source adapters normalize local dumps into one candidate contract before target extraction or level selection.
- Exact duplicates merge only inside the same collection, and every source origin survives the merge.
- The source registry is an allowlist: an unknown, unaudited or disallowed licence fails before ingestion.
- Tatoeba `Everyday`, WikiMatrix `Knowledge` and Global Voices `World` remain the base source families.
- No corpus adapter emits learner cards directly; adapters emit real aligned source material.

### Part 3 - morphology enrichment

Status: complete as an offline evidence pipeline.

- UniMorph supplies conservative form-to-lemma evidence where admitted.
- Universal Dependencies supplies contextual/form evidence for lemma, UPOS and canonical CoNLL-U FEATS.
- Production morphology inputs are version/SHA-256 pinned and licence-gated.
- Existing `lemma`, `pos` and `isContent` remain compatible; morphology does not silently rewrite target identity.

### Part 4 - scale build, grouped contexts and foundation audit

Status: complete as the structural Catalogue v2 foundation.

The verified scale build contains **443,366 unique exact targets**,
**1,187,151 target/deck memberships**, **2,613,071 natural contexts** and
**1,533,669 unique source contexts** across **383 decks / 109 language pairs**.
Self-contained deterministic gzip assets occupy **387.1 MiB** and represent
**3,108.4 MiB** raw JSONL.

Part 4.1-4.5 historically corrected target/context ownership, measured storage,
made the census concrete and added deterministic manual-review/readiness tooling.
The final readiness result is **WARN** with no structural blockers. The material
warnings are now inputs to Parts 5-12:

- 72 decks below 1,000 cards, including 13 below 100;
- 69 decks at the configured 8,000-target cap;
- 31 decks at or above 90% of the raw client cap;
- many exact targets still have only one distinct source context.

Part 4 is not extended again for ordinary cleanup.

### Part 5 - existing-source supply census

Status: tooling implemented; full 11-language census run pending.

The manual `catalogue v2 supply census` workflow enumerates every direct WikiMatrix pair, records unavailable files, and reports bounded source scans explicitly as lower bounds rather than calling them thin. `tools/catalog/supply_census.py` measures all unique sieve-eligible exact targets before `max_deck` separately from the targets chosen by the current capped first pass.

Purpose: establish what the current corpora can supply **before** final deck caps.

- Census Tatoeba across the supported language matrix.
- Inventory and census every available direct WikiMatrix pair among the eleven supported languages.
- Record source rows, quality/provenance survivors, eligible targets before cap and selected targets after the current sieve.
- Attribute thinness to actual causes: no direct source pair, quality rejection, duplicate/no-new-target, level/sieve rejection or artificial cap.
- Produce reports only; do not publish.

Exit condition: every current thin/capped deck has a measured explanation.

### Part 6 - source admission and MASSIVE measurement

Status: 0.11 decision complete — MASSIVE is **not admitted** after review exposed localized slot values that can change bilingual meaning. Tatoeba remains the production Everyday source. MASSIVE stays an experimental `candidate`; the stricter slot-aware gate is kept for future re-evaluation.

Purpose: freeze the 0.11 source set before rebuilding content.

The policy source is [`CORPORA-0.11.md`](CORPORA-0.11.md).

- Tatoeba remains the broad `Everyday` baseline.
- MASSIVE 1.1 is the only new production candidate in 0.11. It provides one shared multilingual utterance set across all eleven supported languages: the original English SLURP seed plus human-localized, human-reviewed rows for the localized languages. Its voice-assistant domain is intentionally narrower than Tatoeba.
- Measure MASSIVE through the normal sieve rather than counting its raw source rows as cards. Localized rows use the human-review vote gate; the original unjudged `en-US` seed still goes through the normal ikna sieve and manual samples.
- Review deterministic samples across languages and previously thin pairs.
- Admit MASSIVE only if it adds useful targets/contexts without weakening quality.
- FLORES-200 remains reference/QA material and does not inflate production deck counts.
- Freeze the source set at the end of the part; later thinness is not permission to add another corpus ad hoc.

### Part 7 - final Everyday candidate pool

Status: preview-pool tooling implemented; final source composition waits on the Part 6 manual admission decision. Exact cross-source duplicates retain both provenance origins.

Purpose: build the human-source Everyday material from the admitted sources.

- Rebuild Tatoeba plus MASSIVE only if MASSIVE passed Part 6.
- Preserve source-family and record provenance.
- Deduplicate exact content without discarding distinct origins.
- Preserve useful alternative natural contexts.
- Report source balance and coverage before selection.

Exit condition: a reproducible Everyday candidate pool exists independently of final deck-size policy.

### Part 8 - direct-pair Knowledge rebuild

Status: 0.11 expansion decision complete — the all-direct-pair rebuild is **not admitted** because retained samples still contain semantic mismatches above the provisional pair floors. Existing approved WikiMatrix production scope remains; all expansion rules stay `review` and fail closed.

Purpose: remove the English-hub convenience restriction from the Knowledge evidence base.

- Acquire available direct WikiMatrix pairs among the supported eleven languages.
- Use real direct source alignments in both directions where appropriate; never manufacture missing pairs by machine-translation pivot.
- Retain alignment scores and choose thresholds from measured quality samples.
- Allow a noisy upstream pair to be rejected explicitly.
- Produce before-cap/after-sieve candidate counts by pair and level.

### Part 9 - World attribution build

Status: native XCES mapping is proven (752,051/752,052 diagnostic rows resolved), and the bounded live gate produced 20,881 fully attributed en-es rows from 300 verified documents. The remaining step is the sharded/resumable all-document provenance scan before Part 11.

Purpose: make Global Voices publishable without weakening the existing provenance contract.

- Recover canonical article URLs and credited contributors for every retained aligned segment.
- Keep the hard record-attribution gate.
- Publish no synthetic filler when a World pair is naturally thin or absent.

### Part 10 - deterministic catalogue selection policy

Status: non-publishing deterministic selection engine implemented and connected to Everyday/Knowledge experiments; real-data review pending before it becomes the Part 11 production policy.

Purpose: turn the available material into intentionally selected decks.

- Replace source-order / first-until-8,000 behaviour as the final product rule.
- Keep 8,000 as an experimental safety budget while ranking is validated, not a target every deck should reach.
- Balance frequency usefulness, clean context, context/source diversity and near-duplicate rejection.
- Use morphology as evidence without changing exact target identity.
- Define an explicit policy for genuinely small decks: publish thin but useful material, omit it with an explicit reason, or alter product grouping only when semantics remain honest. Never pad with weak data.
- Keep deterministic tie-breaking.

### Part 11 - final content census and freeze

Purpose: decide whether the final Catalogue v2 content is ready to freeze.

- Run the complete admitted-source build with the final selection policy.
- Produce full census, per-deck inventory and deterministic samples.
- Audit integrity, provenance, source balance, pair/level coverage, morphology and target/context shape.
- Inspect the smallest and highest-risk pairs as well as broad samples across every collection/language.
- Freeze exact source/build inputs only after review.

### Part 12 - final storage/package decision

Purpose: optimize the frozen content, not an obsolete intermediate build.

- Re-run the lossless storage experiment on Part 11 assets.
- Compare self-contained gzip with measured pooling/layout alternatives.
- Accept a more complex layout only when the byte/cold-deck benefit justifies it and recovery/integrity remain simple enough.
- Never remove contexts, translations or provenance merely to meet storage goals.
- Implement the chosen app reader/publication format after the measurement decision.

### Part 13 - richer target relations

Purpose: add evidence-backed relations beyond exact spelling identity.

- Start from exact NFKC + case-fold target groups.
- Add morphology-aware relations only where precision is demonstrated.
- A relation is not an automatic learner-memory merge.
- Keep occurrence/morphology evidence inspectable.

### Part 14 - context evidence history

Purpose: make context choice observable and replayable before adaptive policy exists.

- Record the stable context shown when context choice matters.
- Record enough policy/version information for deterministic replay.
- Define seen/unseen from history, not a transient flag.
- Keep Browse exposure semantics explicit.
- Preserve one learner memory for a shared exact target.

### Part 15 - Context Policy

Purpose: choose ordinary stored contexts deliberately.

- Do not rotate context on every review.
- Keep deterministic choice for the same history.
- Prevent meaning-language/source leakage across deck memberships.
- Keep ordinary context choice separate from transfer testing.

### Part 16 - Transfer Policy and contextual-diversity experiment

Purpose: test retrieval in an unseen real context only when the history can represent it.

- Use mature targets and conservative eligibility.
- Compare policy variants against identical deterministic histories.
- Do not let novel-context success change FSRS intervals before a documented policy is validated.
- Do not add gamified rewards around transfer.

### Part 17 - validation and publication

Purpose: prove the exact release configuration before touching the public catalogue.

- Keep `SCIENCE.md` evidence labels aligned with claims.
- Run Android and desktop CI.
- Run Catalogue contracts, ingestion, morphology, parity, census and migration tests.
- Verify deterministic replay/policy fixtures.
- Inspect real final samples by collection/level/language pair.
- Exercise the exact final build with `publish=false`.
- Publish assets/metadata first and `index.json` last.
- Rename the fixed-tag release to `Catalogue v2` only after the publication is complete.

## Deliberately out of scope for 0.11

- runtime LLM calls;
- generated study sentences;
- machine-translated/pivoted filler for missing catalogue pairs;
- semantic embeddings as a mandatory dependency;
- automatic merging of every morphological form;
- a gamified reward system;
- novel-context success changing FSRS intervals before validation;
- a server, account system or telemetry;
- a visual redesign;
- open-ended addition of more corpora after the Part 6 source freeze.

The release is successful if ikna can build useful study targets from auditable
human-source material, distinguish a target from the contexts in which it was
observed, preserve one learner memory across decks, and measure transfer to an
unseen real context without pretending that the transfer observation already has
a scientifically known scheduler weight.
