# 0.11.0 press roadmap

Theme: **evidence first**.

0.11 is primarily an architecture and learning-science release. The interface is
not the project to optimize in this cycle unless a learning-engine change requires a
small UI adjustment.

## Goals

### 1. Research contract

- Keep `SCIENCE.md` as the source of truth for evidence labels and claims.
- Classify existing learning mechanisms instead of retroactively calling all of them
  research-proven.
- Require new learning-policy changes to state the outcome, evidence, assumption and
  replay/rollback path.

### 2. Learning-engine boundaries

- Keep scheduling, workload, target choice, context choice, transfer and grading as
  separate responsibilities.
- Keep all learning decisions in shared code so Android and desktop cannot diverge.
- Preserve deterministic replay where learner history depends on a policy decision.

### 3. Ikna Database v1

- Define stable target, occurrence, context and provenance identities.
- Build it as a content project, separate from private learner history.
- Keep Catalogue as the user-facing distribution layer built from this database.
- Use static repository/release assets; no application server is required.

### 4. Conservative contextual diversity

- Group only high-confidence occurrences of the same target.
- Use source-corpus sentences; do not generate filler contexts to reach a quota.
- Accept targets with only one context.
- Version grouping and normalization rules.

### 5. Transfer representation

- Let runtime distinguish a context the learner has seen from one they have never
  seen.
- Record that distinction without automatically changing FSRS grading.
- Do not announce transfer with praise, XP or explanatory popups by default.

### 6. Validation

- Add corpus fixtures for true matches, non-matches and ambiguous cases.
- Add deterministic runtime fixtures before enabling adaptive context selection.
- Compare new policy behaviour against the same histories rather than tuning by
  intuition.

## Deliberately out of scope for the first 0.11 milestone

- LLM calls at runtime;
- generated study sentences;
- semantic embeddings as a mandatory dependency;
- automatic merging of every morphological form;
- a gamified reward system;
- novel-context success changing FSRS intervals before validation;
- a server, account system or telemetry;
- a visual redesign.

## Delivery order

1. Documentation and architecture contract.
2. `ikna-database` schema and offline fixtures.
3. Corpus grouping pipeline.
4. Backward-compatible pack metadata.
5. Local learner-history context fields.
6. Context/Transfer Policy in shared code.
7. Replay and policy tests.
8. Only then evaluate whether transfer observations should influence scheduling.

The release is successful if ikna can represent the difference between "I remembered
this card" and "I retrieved the same target in a real context I had not studied",
without pretending that the second observation already has a scientifically known
weight.
