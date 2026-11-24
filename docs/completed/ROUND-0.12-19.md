# Round 19 — saved admitted pool and exact function-rank prefix

2026-10-09, Asia/Yekaterinburg. Owner supplied the saved Part 7 archive in two
ordinary ZIP wrappers and `manifest.txt`, since the original exceeds the upload
limit. This is an offline input audit and rank-prefix export, not a new corpus
acquisition, selection or application change. Source baseline is the complete
round-18 project ZIP; its accepted application changes remain intact.

## Input and verification scope

The ordered binary members reconstitute
`catalogue-v2-everyday-admitted-pool.zip`, exactly 545,984,900 bytes, SHA-256
`0f02c2acfbab21d1e90c3d88f1dda0db61ca789f9507b05a483ea7f2a20884e3`.
Both match the owner-supplied manifest. The original ZIP's five members are read
through EOF with CRC checks, and the pool's gzip integrity is checked during the
complete logical scan.

This is the original admitted pool from run `37458524156`, source version
`2026-10-03-2d4eee105639ba24`. All ten admitted-input fields reconcile with the
already accepted [round-15 selected snapshot](evidence/everyday-round15/SNAPSHOT.json).
No replacement of that snapshot or its historical reports is made.

| Material | Bytes | SHA-256 |
| --- | ---: | --- |
| Pool gzip | 545,718,435 | `57b90b01d5dcb0725743998dccaf307a100e174059a853e01f5f31fc13dd6170` |
| Logical JSONL | 5,320,249,214 | `05ccbb5b5217dc050cc5a5e0e1cfca3223657529afa6b3890c9340999d571ab8` |
| Original Part 7 report | 267,859 | `41c7e176d516e6ad89d70954b116d18062baa48857f66ee276bdcfd32dd0438f` |

The complete scan parses and validates every retained candidate identity, scope,
origin family/version, stable source reference and current source-policy gate.
Logical size/hash, row count, unique primary-context count and language-pair
coverage are checked. Candidate-ID uniqueness is inherited from the exact
hash-bound admitted report, not independently re-indexed in this export. Corpus
coverage remains **provided input only**, not a claim of complete upstream Tatoeba.
Neither source admission nor successful parsing certifies all translations.

## The two pools and their counts

- Part 7's original scoped input contained 11,115,426 candidate rows.
- Merging removed 15,936 duplicate candidates, retaining 11,099,490 records.
- Frequency evidence uses 5,296,490 unique primary-origin context keys across
  11 learning languages and 110 directed pairs.
- The separately accepted selected file remains 928,162 deck memberships,
  221,653 unique targets and 2,094,192 retained contexts. It was previously
  supplied and audited; it is not this archive and is not regenerated here.

`pairs.inputCandidates` in the Part 7 report counts **premerge** rows. The first
local attempt incorrectly compared those with retained rows and stopped after a
complete identity/context scan. That was an exporter guard error, not damaged
input. The corrected check reconciles pair scope, each nonnegative removal count,
the premerge total and the exact 15,936 merged duplicates. A regression covers
this distinction; no failed attempt is labelled a completed prefix export.

## Reusable handoff

`tools/catalog/pool_function_words.py` exports an input-bound
[FUNCTION-WORDS.json](evidence/everyday-round19/FUNCTION-WORDS.json). It uses the
existing builder's `build_ranks`, not a reranking of selected targets. Its slim
temporary index uses the identical primary-origin context key, first-seen text,
rowid traversal, lowercase token forms and Counter tie order. Multiple translations
and alternate merged origins do not multiply frequency observations.

Python 3.12.14, ICU 74.2, all eight pool pipeline hashes and the source registry
match the original report. Different environments, changed registry/code, corrupt
hashes, inconsistent counts or reuse of an existing output directory fail closed.
Only completed compact evidence is retained; the large pool and temporary index
are not bundled in the application or project ZIP.

Each language contributes the first 60 frequency-ranked forms used by the
existing FUNC/WORD classifier. This is a deterministic rank prefix, **not a
linguistic POS dictionary**. The length rule still applies; no lemma model,
segmentation policy, source admission or learning behaviour changes. Tokens outside
the prefix receive the same classification as under the original full ranks.

The recovered prefix is 55,723 bytes, with 660 ranked entries. Supply is strongly
uneven: English has 1,158,070 distinct primary contexts, Korean 15,336 and Chinese
81,217. These are available source contexts across all supported meaning languages,
not per-deck selected-card counts or promises of usable translations for every pair.
The disparity is consistent with the previously diagnosed supply shortages.

The form `tom` ranks fourth in English/German and third in Italian, and therefore
falls inside the existing FUNC prefix despite being a proper name in these uses.
This exposes a corpus-frequency heuristic, not a recovered grammatical POS tag.
Final materialization must reproduce the frozen classifier; changing this rule
would require a separately versioned/evaluated policy decision rather than a
silent token or selection rewrite. No new manual selection is introduced.

The completed evidence retains exact input/pipeline/environment pins, raw versus
retained counts, origin metadata coverage and per-language context/ranked-form
counts. [AUDIT.json](evidence/everyday-round19/AUDIT.json) binds the split delivery
and the prefix to the frozen selected snapshot. The original
[EVERYDAY-POOL.json](evidence/everyday-round19/EVERYDAY-POOL.json) remains unchanged.

Reproduce the export locally, without a workflow or upstream download:

```bash
python3 tools/catalog/pool_function_words.py \
  --archive /path/to/catalogue-v2-everyday-admitted-pool.zip \
  --expect-archive-sha256 0f02c2acfbab21d1e90c3d88f1dda0db61ca789f9507b05a483ea7f2a20884e3 \
  --expect-report-sha256 41c7e176d516e6ad89d70954b116d18062baa48857f66ee276bdcfd32dd0438f \
  --output-dir /path/to/new-prefix-output
```

## Checks and next step

Seven new regressions cover parity with full builder staging/ranks, first-seen
ties, translation/alternate-origin deduplication, distinct context references,
CJK segmentation, premerge counts, complete archive export and refusal paths.
Existing builder contracts and 11 admitted-pool tests pass. Text, documentation
links, source diff and complete ZIP manifest/byte/CRC checks are also performed.
No JVM/Android build, semantic manual review or GitHub workflow is run.

The round-16 missing frequency-input checkpoint is closed. The initial handoff
suggested final materialization next; the owner superseded that sequence on
2026-10-09. **Catalogue assembly and final pack materialization are deferred**
until deficient decks are replenished, the agreed automatic validation gates
pass, and deck-size/product decisions are made. The current sequence lives in
[PLAN-0.12.md](PLAN-0.12.md#owner-sequencing-decision--quality-coverage-and-size-before-assembly).
This export remains prepared input for the later authorized assembly stage.

The full selected archive was supplied and audited in round 15; this round reads
the admitted archive only. The selected material must be available again at
materialization time if transient workspace copies have expired. Its exact input
identity stays in the snapshot. Do not substitute a preview or rerun selection.
Cap 8,000, application behaviour, source admission, publication and global
Catalogue freeze remain unchanged. Statistics redesign remains a separate,
unapproved product discussion.
