# 0.12 round 10 — final selection correction and automatic handoff

Owner decision, 2026-10-07: this is the last bounded manual-selection round.
High quality remains required, but a solo owner need not assess unknown languages
or every catalogue card. Subsequent work uses workflows and later plan stages;
examples are presented in text. This supersedes round-09's proposed owner-review
prerequisite. Both repository skills apply.

Baseline: complete 590-file round-09 ZIP, 7,539,237 bytes, SHA-256
`ddec04ac64c5ec925b6d72d9d2a3f04b638551c968cf8e862c5f897bdabe1532`.
All source bytes matched before editing. Accepted app/window/Hot Reload fixes,
versions, Room schema, learner history, signing assets and repository skills are
preserved. No Android/desktop source or build dependency changes occur.

## Changed selection behaviour

Explicit `boundary-diversity-v2` adds three scoped changes:

1. A Japanese target occurrence is deferred when its ICU start/end cuts a unit
   from pinned SudachiPy 0.6.10 / SudachiDict-core 20250825, Split Mode A. Unknown
   cuts are separately labelled. Targets spanning complete units remain eligible;
   written surfaces, global IDs and original frequency ranks are not rewritten.
   The original source pool is retained. No Chinese/Korean one-character ban,
   inferred lemma, machine translation or synthetic filler is introduced.
2. Primary context/source/alignment priority stays unchanged. Alternatives prefer
   lower written-token overlap before minor compactness ties. The existing
   duplicate threshold stays .85. Similar grammatical variants can still be
   retained when there is no more varied eligible context; similarity is not a
   new semantic rejection rule. Evidence buffers use the same ordering.
3. Two observed Spanish source cases containing `la lluvio` are deferred using
   exact sentence reference, export version and text SHA-256. The quarantine
   applies in both context and meaning roles. Originals remain in the pool;
   corrected/future text and regional forms such as `Decime` are unaffected.
   This is a bounded source exclusion, not silent spelling correction or a
   general grammar judgement. The versioned manifest is
   `tools/catalog/sources/selection-quarantine.json`.

Actual pinned-engine checks defer `き` in `起き` and `行` in `行った`, retaining
complete `木`, `日本語` and particle `や` in their tested contexts. Reapplying the
guard to the **old biased preview** checked 754 Japanese context presentations
and deferred 203 known morpheme cuts. These include repeated memberships across
meaning languages; this is not a unique-defect count, a corpus defect percentage
or a full reselection. Exact evidence is in
[everyday-round10.json](evidence/everyday-round10.json).

The compatible pre-0.7/V0 wheels are hash-pinned; the dictionary binary SHA is
`d28ffc33b196e5c2ca731e8147fd1ef47d35ba79928ef9f0871962859d70ac23`.
Official [analyzer](https://github.com/WorksApplications/sudachi.rs) and
[dictionary](https://github.com/WorksApplications/SudachiDict) document Apache-2.0
and version compatibility. Local checks loaded verified wheel contents from a
temporary test directory; no system/project toolchain was installed. Model files
are not included in the project/app ZIP. This is boundary evidence, not a proof
of every lexical or semantic choice; MORPHOLOGY owns its separation from rule v1.

## Complete material and one next workflow

`--selected-output` writes complete included-decision memberships to deterministic
gzip JSONL, independently of preview limits. Raw/logical hashes, counts and
per-deck identity are recorded. Input/report/registry/output aliases are rejected;
failed admitted selection does not replace a previously saved full handoff.
Legacy CLI defaults remain available; the new workflow explicitly selects v2.

**catalogue v2 everyday quality handoff** reuses both exact artifacts from run
`37458524156`, validates pins, selects once and uploads the complete handoff before
evaluation. No fresh/latest fallback, admission rebuild or old-selector rerun
exists. The evaluator checks every selected membership/context with disk-backed
uniqueness, source/target/candidate/boundary/UTF-16 contracts and the Japanese guard.
Full-report count deltas compare every deck. Exact old identity/context deltas are
only baseline-preview evidence because the old complete selection was not saved.
Losing a previously included language pair stops automatic progression.

Save `catalogue-v2-everyday-selected-material` for Part 11/12; return the small
`catalogue-v2-everyday-quality-report`. Exact prefilled inputs and reuse commands
are in [EVERYDAY-POOL.md](EVERYDAY-POOL.md). Small reports are retained on failure;
the complete handoff is saved before evaluation so checks can be rerun without
reselection. The workflow has not been dispatched in this round.

## Verification and progression

Local checks include eleven new quality regressions with the actual pinned analyzer,
seventeen admitted-selection tests, existing segmentation/identity/ingestion/
selection/preview suites, text/localization checks, deterministic full output,
complete-output versus limited-preview behaviour and preservation on failure.
An admitted fixture was executed through both selection and evaluation **CLIs**;
the automatic gate passed. Workflow YAML/Python blocks compile, and checkpoint
blocks accept valid fixture files/pins and reject changed pins or moving run IDs.
Final executed checks and source fingerprints are recorded in the evidence file.

This is local/preview evidence. The 11-million-candidate pool and complete new
selection are absent here; coverage losses, full runtime/cost and material counts
remain pending the one real workflow. No app build, final storage/client acceptance,
global catalogue freeze or publication is claimed. The automatic gate checks
source/structural/boundary/coverage contracts; it does not certify every translation.

After a passing real report, proceed to **Part 11 Everyday census/content snapshot**
over its saved complete material, then **Part 12 storage/client validation**, not
another routine manual-selection round. Freeze records the accepted quality
profile, exclusions, limitations and exact input/policy identities. Do not resieve
the source pool through the historical builder and reintroduce deferred targets.
World all-document attribution and final publication remain separately gated.
