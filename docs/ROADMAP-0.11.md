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

Status: source adapters and licence/provenance gates implemented. The Part 4
workflow now exercises them at catalogue scale; the public `catalog` release is
still unchanged until a reviewed build is explicitly published.

- Tatoeba remains the source family for `Everyday`; the adapter accepts the detailed export when contributor metadata is wanted, while the large rebuild may use the smaller sentence export and stable sentence ids to stay within runner memory. Real runs record the weekly export date.
- WikiMatrix is registered for `Knowledge` under CC BY-SA 4.0. Its alignment score
  can be retained for the later quality sieve, and TSV column direction is handled
  explicitly so a reversed learning pair cannot be mislabeled.
- Global Voices is registered for `World` under CC BY 3.0, but publication requires
  record-level contributor credit and a canonical article URL; plain aligned text
  is rejected.
- All three adapters normalize local dumps into the same candidate JSONL contract
  before target extraction or level selection.
- Exact duplicates merge only inside the same collection, and every source origin
  survives the merge.
- The source registry is an allowlist: an unknown, unaudited or disallowed licence
  fails before ingestion.
- No new corpus is published by this part. The current `catalog` release remains
  the existing Tatoeba catalogue until the later rebuild/publish step.

### Part 3 - Morphology enrichment

Status: offline enrichment pipeline and contracts implemented. Part 4 adds an
audited UD 2.18 production set and generates a SHA-256-pinned manifest for each
full rebuild; UniMorph remains supported but is not required by the first v2 run.

- UniMorph supplies conservative form-to-lemma evidence; ambiguous paradigms remain unresolved.
- Universal Dependencies supplies contextual/form evidence for lemma, UPOS and canonical CoNLL-U FEATS.
- Every production morphology input must be version-pinned, SHA-256 pinned and pass a per-dataset/treebank licence gate.
- Existing `lemma`, `pos` and `isContent` remain compatible; `pos` and `isContent` are never rewritten by enrichment.
- Rule v1 prefers identity morphology to source disagreement and does not convert UniMorph features into UD FEATS.
- Morphology rule version 1 does not alter `targetId`; a later morphology-aware target identity must increment its own identity version.

### Part 4 - Catalogue expansion and census

Status: experimental full-build pipeline and GitHub Actions workflow implemented;
the first full CI census is intentionally pending review before publication.

- `catalogue v2 build` keeps `publish=false` by default and leaves the current
  release untouched while a complete build is inspected as an Actions artifact.
- `Everyday` is rebuilt from the current Tatoeba weekly export in one matrix pass.
- `Knowledge` is expanded from bounded WikiMatrix v1 hub pairs. The default English
  hub is deliberately small enough for a hosted runner and the GitHub release asset
  limit, while still adding both learning directions.
- `World` remains empty in the automatic build until Global Voices article URL and
  contributor attribution can be supplied for every retained segment.
- The source-independent v2 sieve can retain up to three natural source contexts
  per exact target and pair instead of forcing one written target to one card.
- The build tracks card count, exact targets and unique source contexts separately.
  At least one million useful cards is the acceptance target; 1.5-2 million remains
  a scale goal only if the census shows the quality sieve supports it.
- An audited permissive UD 2.18 treebank set can enrich the selected cards. The
  generated manifest records file SHA-256 values for the exact run.
- `catalogue meta-info` runs against the finished v2 assets before any publication.
- An explicit reviewed publish still uses the existing `catalog` tag, uploads deck
  assets first and `index.json` last, and changes the release title to `Catalogue v2`.

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
