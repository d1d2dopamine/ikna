# 0.12 round 09 — real Everyday preview audit, 2026-10-07

## Scope and inputs

Owner authorized independent auditing and workflows only where needed. Both
repository skills apply. Baseline: complete 579-file round-08 source ZIP,
6,464,746 bytes, SHA-256
`5ef3a36cc9813d060a30755ff1d1679e82b739e712c01237c597810e8b6cfc19`.
Source bytes matched before editing. Accepted window/Hot Reload/application code,
learning identity/storage, signing assets and release versions are preserved.

The attached small review ZIP is 1,793,840 bytes, SHA-256
`22e0f8372d09e70993db4b2aacf787b913c44379a21ec823ae1551ef7eb4fded`.
Read-only public REST metadata identifies successful run
[37458524156](https://github.com/d1d2dopamine/ikna/actions/runs/37458524156), commit
`47a393466e50ac5490ede8bba9173472726a1b9a`, completed 2026-10-06. Review artifact
ID `11416257701`; saved admitted-pool artifact ID `11414433600`, ZIP size
545,984,900 bytes. [INPUTS.json](evidence/everyday-round09/INPUTS.json) records
these observations and the exact report identities. The full pool itself is not
present in this session: its bytes/counts are reported evidence, not an independent
full-input scan here. No full download, workflow dispatch or reacquisition occurs.

## Results

The real Part 7 report records 11,099,490 unique candidates and 648,494 eligible
global exact targets across 110 directed pairs. Part 10 records 934,652 selected
memberships across all decisions, including 62 in two omitted Polish→Korean
levels. Thus 934,590 belong to `publish`/`publish-thin` policy decisions; those
labels do not actually publish material. Eligible membership counts of Parts 7
and 10 need not match: their context/meaning aggregation stages differ. Their
own inventories/summaries and the bound pool identity are consistent.

The independent machine audit covers **every supplied preview row**:

| Measure | Audited value |
|---|---:|
| Preview memberships | 3,280 |
| Unique global targets in preview | 754 |
| Stored contexts | 8,157 |
| Source origins | 8,159 |
| Contexts using a surface different in case from row text | 639 |
| Origins missing one/both contributor names | 693 |

Hash-bound Part 7→10 identities, language/deck inventory, policy totals, provenance,
candidate/target hashes, frequency level and one complete canonical target token
per context pass. UTF-16 spans reconstruct the actual surface in every context.
All ten recorded selection source hashes match this base; system ICU is the
recorded **74.2**. Missing contributor names are informational under the existing
Tatoeba policy, not a newly imposed admission failure. Primary source references
are present in the retained origins.

No remaining pair of stored alternatives violates the existing production
near-duplicate threshold. A stricter ordered-token/character similarity aid flags
89 memberships / 63 unique targets for manual review. It deliberately does not
change the selector: gender, pronoun, modal and article changes can be useful.
The single-hiragana signal identifies 50 memberships but only **five** unique
Japanese targets: `き`, `や`, `え`, `せ`, `わ`.

## Material findings and decisions

[MANUAL-REVIEW.json](evidence/everyday-round09/MANUAL-REVIEW.json) preserves a
deliberate inspection of **27 memberships / 73 contexts**, with exact source
references, attribution, highlights and explanatory notes. Examples span all
eleven learning languages, mostly with Russian meanings. This is preliminary
inspection by Codex, not native-speaker certification or a random quality sample.

| Example | Finding / next decision |
|---|---|
| en→ru `time` | Useful everyday examples; review unusual modern word order in one alternative. |
| ja→ru `き` | Same segment appears in 聞き / 起き / 持ってきて: valid ICU boundary, questionable standalone teaching target. |
| ja→ru `や` | Fragment contexts coexist with a particle context; blanket character rejection would lose a potentially useful use. |
| zh→ru `用` | Counterexample to blanket one-character bans; length alone is not a quality verdict. |
| ru→en `сегодня`, de→ru `nahe` | Alternatives largely change address/pronouns; agree what variety is useful. |
| en→ru `were`, fr→ru `examiner` | Modal/article differences may be educational even when similarity is high. |
| es→ru `escuchando` | Repeated suspicious source spelling `la lluvio`; source spelling and regional register need review. |
| pl→ru `skromny`, `niezły` | Literary name adaptation and colloquial register need explicit content criteria. |
| en→ru `technique`, zh→ru `事` | Technical/government context suitability for Everyday remains a product/content decision. |

The Japanese finding is **not a UTF-16 corruption bug**. ICU word boundaries are
not a morphological or pedagogical validation. The current target chooser accepts
complete ICU spans subject to frequency/length rules, which can admit these short
segments. [Official ICU boundary documentation](https://unicode-org.github.io/icu/userguide/boundaryanalysis/)
explains the boundary/dictionary layer; it does not establish learning suitability.
Do not silently merge exact identities, strip all endings, reject all short CJK
targets, rewrite translations or modify global thresholds from this biased preview.

## Delivered changes

- `selection_audit.py`: bounded ZIP/gzip/strict-JSON reader, admission/hash and
  preview checks, explicit review-only flags and reproducibility evidence.
  Supplied archives are not extracted; output must be a new directory.
- `selection_review.html`: offline viewer template. The generated
  [CARDS.html](evidence/everyday-round09/CARDS.html) embeds all real preview rows,
  highlights per-context surfaces, shows source attribution and manual notes,
  and supports filters, meaning reveal and input-bound reviewer export/import.
  Source/comment text uses DOM text APIs; embedded data cannot close its script.
- Focused Python and Node logic regressions; commands in
  [EVERYDAY-POOL.md](EVERYDAY-POOL.md) and CONTRIBUTING.
- Active plan/entry documentation synchronized to the successful retained run;
  contributor source summary now explicitly retains the MASSIVE exclusion.

No new workflow is necessary for the available artifact: these checks run
independently without CI. Existing acquisition/selection/readiness workflows keep
their separate responsibilities. A universal build/rebuild/evaluate workflow set
can be designed after the missing full-material criteria are agreed.

## Verification and limits

All scoped checks passed; [CHECKS.json](evidence/everyday-round09/CHECKS.json)
records commands and evidence limits. Seven new Python regression groups, the
existing segmentation/identity/ingestion/selection suites, viewer logic, text and
localization checks passed. Repeating the same audit/notes yields byte-identical
JSON, Markdown and HTML. `git diff --check` passes; comparison with the exact
source ZIP confirms only six owning docs changed and eleven explicit files were
added, with application/build/signing/skills source bytes preserved.
Python fixtures cover broken pins, origin/version/identity
violations, boundary errors, duplicate/incomplete inventories, foreign pipeline
paths, unexpected freeze/ICU, case/non-BMP spans, valid short CJK targets and HTML
injection safety. Node executes the generated viewer script with a DOM stub for
navigation/filter/highlight, drafts, storage failure, export/import and refusal
without discarding current decisions. It is not real browser layout evidence.

The available environment has no installed browser executable; no browser or
toolchain was installed. Actual viewer layout/keyboard interaction remains for
owner inspection. No Android/desktop Gradle build or packaged rendering is claimed.
All automated flags remain proposals, all publication/freeze flags remain false,
and no global semantic-quality percentage is estimated. See
[AUDIT.json](evidence/everyday-round09/AUDIT.json) for exact machine counts,
environment and linked input hashes.

Next: owner reviews the visible flagged examples and records decisions; agree
Japanese target suitability and meaningful alternate-context criteria. Obtain a
broader deterministic sample from the already saved exact pool before changing
selection policy. Do not rerun known acquisition/admission just to re-create this
preview. Full material review, World all-document attribution, freeze, final
storage/client acceptance and publication remain open.
