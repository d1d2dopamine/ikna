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

### Part 4 / 4.1 - Catalogue expansion, grouped contexts and census

Status: experimental full-build pipeline and GitHub Actions workflow implemented;
Part 4.1 corrected target/context ownership before the first reviewed rebuild. The
public `catalog` release remains unchanged.

- `catalogue v2 build` keeps `publish=false` by default and leaves the current
  release untouched while a complete build is inspected as an Actions artifact.
- `Everyday` is rebuilt from the current Tatoeba weekly export in one matrix pass.
- `Knowledge` is expanded from bounded WikiMatrix v1 hub pairs. The default English
  hub is deliberately small enough for a hosted runner and the GitHub release asset
  limit, while still adding both learning directions.
- `World` remains empty in the automatic build until Global Voices article URL and
  contributor attribution can be supplied for every retained segment.
- One deck JSONL row is one target membership. Up to three natural source contexts
  can belong to that row; extra contexts are not independent scheduling cards.
- `targetId` is catalogue-global for an exact target in one learning language and
  stays stable across collection, level and meaning-language decks.
- The v2 extraction core is parity-tested against the mature v1 segmentation,
  length/function-word sieve, level boundaries, token classification and UTF-16
  offsets. The existing phonetics pipeline remains the v2 phonetics implementation.
- The build tracks unique exact targets, target-deck memberships, retained natural
  contexts and unique source contexts separately. Scale goals are evaluated against
  those separate measures instead of inflating a card count with alternative contexts.
- An audited permissive UD 2.18 treebank set can enrich the selected target contexts. The
  generated manifest records file SHA-256 values for the exact run.
- `catalogue meta-info` runs against the finished v2 assets before any publication.
- An explicit reviewed publish still uses the existing `catalog` tag, uploads deck
  assets first and `index.json` last, and changes the release title to `Catalogue v2`.

### Part 5 - Richer target/context relations

The exact global target/context structure now exists in Catalogue v2. Part 5 adds
only relationships that require more evidence than exact written identity.

- Start from the existing exact NFKC + case-fold target groups.
- Use source-corpus sentences; never generate filler contexts to reach a quota.
- Keep one-context targets valid.
- Add morphology-aware grouping only where its precision is demonstrated.
- Keep occurrence and context provenance inspectable.

### Part 6 - Learning-engine integration

Part 4.1 already added the minimum learner-database bridge required for shared
exact targets: schema v10 has explicit deck/target membership and source-context
tables, and Catalogue v2 imports use `targetId` as one shared FSRS key. Part 6 is
therefore about policy, not re-creating that storage layer.

- Keep scheduling, workload, target choice, context choice, transfer and grading as
  separate responsibilities.
- Choose which stored context represents a shared target in a session; do not
  silently rotate it on every review.
- Let learner history distinguish seen and unseen contexts before transfer policy
  can use that distinction.
- Add any sense-splitting policy for ambiguous exact forms only with evidence and
  versioned identity/migration rules.
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
