# 0.11.0 press roadmap

Theme: **evidence first**.

0.11 is primarily an architecture, content-data and learning-science release. The
interface is not the project to optimize in this cycle unless a learning-engine
change requires a small UI adjustment.

## Work parts

### Part 1 - Catalogue v2 contract

Status: specified in [`CATALOGUE-V2.md`](CATALOGUE-V2.md).

- Keep the existing GitHub release tag `catalog`.
- Rename that release to `Catalogue v2` only when v2 assets are actually published.
- Add the `Everyday`, `Knowledge` and `World` collection dimension.
- Preserve v1 index/card fields so older builds can still read v2 files.
- Define structured source provenance, target/context identity and morphology fields.
- Keep the current 2 MiB index cap and 24 MiB per-deck cap during migration.

### Part 2 - New corpus ingestion

- Keep Tatoeba as the initial source for `Everyday`.
- Add audited adapters for WikiMatrix/Wikipedia and Global Voices after their exact
  distribution, attribution and licence requirements are recorded.
- Normalize every source into one internal candidate representation before deck
  selection.
- Deduplicate across sources without erasing provenance.
- Reject source records whose provenance or licence cannot be represented safely.

### Part 3 - Morphology enrichment

- Replace identity-only lemmas where a reliable open morphology source can resolve
  them.
- Preserve the existing `lemma`, `pos` and `isContent` contract.
- Add `upos`, canonical CoNLL-U `feats` and `lemmaSource` as enrichment.
- Prefer unresolved/identity morphology to a false merge.
- Version every rule that can change target identity.

### Part 4 - Catalogue expansion and census

- Rebuild the catalogue from the audited sources.
- Target at least one million useful cards, with 1.5-2 million as a scale goal only
  if the quality sieve supports it.
- Track unique source contexts separately from JSONL card count.
- Run `catalogue meta-info` against the complete build before publication.
- Publish deck assets first and `index.json` last to the existing `catalog` tag.

### Part 5 - Target/context relations

- Start with exact NFKC + case-fold target groups.
- Use source-corpus sentences; never generate filler contexts to reach a quota.
- Keep one-context targets valid.
- Add morphology-aware grouping only where its precision is demonstrated.
- Keep occurrence and context provenance inspectable.

### Part 6 - Learning-engine integration

- Keep scheduling, workload, target choice, context choice, transfer and grading as
  separate responsibilities.
- Add runtime support for catalogue `targetId` and `contextId` without changing
  scheduling semantics first.
- Let learner history distinguish seen and unseen contexts.
- Preserve deterministic replay where a policy decision affects history.

### Part 7 - Conservative contextual-diversity experiment

- Introduce Context/Transfer Policy only after the data model and history can
  represent the decision.
- Do not rotate contexts on every review.
- Do not announce transfer with praise, XP or explanatory popups by default.
- Do not let novel-context success change FSRS grading before a documented and
  validated policy exists.

### Part 8 - Validation

- Keep `SCIENCE.md` as the source of truth for evidence labels and claims.
- Add corpus fixtures for true matches, non-matches and ambiguous cases.
- Add deterministic runtime fixtures before adaptive context selection is enabled.
- Compare policy changes against the same histories rather than tuning by intuition.

## Deliberately out of scope for the first 0.11 milestones

- LLM calls at runtime;
- generated study sentences;
- semantic embeddings as a mandatory dependency;
- automatic merging of every morphological form;
- a gamified reward system;
- novel-context success changing FSRS intervals before validation;
- a server, account system or telemetry;
- a visual redesign.

The release is successful if ikna can represent the difference between "I
remembered this card" and "I retrieved the same target in a real context I had not
studied", without pretending that the second observation already has a
scientifically known weight.
