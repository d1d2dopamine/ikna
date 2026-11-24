# Round 24 — saved pool restored and bounded real-input profile

2026-10-12, Asia/Yekaterinburg (2026-10-11 UTC). Continue the selection-engine
plan from round 23 using the owner's saved artifact. All source material belongs
to ikna. No application/UI, learner data, selector semantics, source admissions,
release metadata or public assets change.

## Full source verification

The supplied archive is exactly 545,984,900 bytes, SHA-256
`0f02c2acfbab21d1e90c3d88f1dda0db61ca789f9507b05a483ea7f2a20884e3`, matching
round 19 and the existing Actions artifact. All five ZIP members pass CRC; paths
are checked before extraction. The retained original is not bundled into this ZIP.

The unchanged `everyday_selection.validate_pool` then reads all **11,099,490**
retained candidates, checking schema, identities, every source origin, exact
language scope, source version/registry, raw/logical hashes and counts. All checks
pass. This is complete input validation, not full selection or semantic translation
certification. [FULL-VALIDATION.json](evidence/selection-round24/FULL-VALIDATION.json)
records 138.007 seconds and an 18,477,056-byte process-lifetime peak RSS on this host.
The host also ran unrelated work; this is an observed duration, not a service SLA.

## Deterministic bounded diagnostic

New `selection_diagnostic_pool.py` streams the exact hash-bound saved pool and
retains up to 128 lowest SHA-256 candidate-ID scores independently per directed
pair. The versioned score prefix is `pair-bottom-sha256-v1`. Ties prefer the earlier
source row; retained raw rows return to original source order. No source sentence
or translation is generated, corrected or replaced.

The original pool builder revalidates all retained records and origins. The
result contains **14,012 candidates across all 110 directions**. Both Polish/Korean
directions retain all 94 available rows. Source bytes and provenance remain
intact. Independent caps prevent large English directions from hiding smaller
pairs, but this is **not a population-weighted sample**, nor is defect/domain/
length representativeness established. Frequency ranks are rebuilt on the bounded
input; they do not reproduce the full accepted frequency space.

The diagnostic command fails on raw/logical/count mismatches, unadmitted parent
status, changed registry or sampler, and existing output directories. It preserves
the parent raw/logical/report pins and sampler hash. Completion evidence is written
atomically. The separate completed full validator is the evidence for all-row
admission; the sampler's compact parsing pass alone does not prove every origin.

Reproduce the sample from the already saved artifact:

```bash
python3 tools/catalog/selection_diagnostic_pool.py \
  --pool /path/to/pool/everyday-candidates.jsonl.gz \
  --pool-report /path/to/reports/EVERYDAY-POOL.json \
  --output-dir /path/to/new-bounded-pool --per-pair 128
```

The compact diagnostic gzip and its report are retained alongside this round's
evidence for direct repeat profiling. They are explicitly non-publishing test
inputs and must never replace the accepted production snapshot.

## Measured selection, unchanged rules

Two sequential current-environment runs of the existing boundary-diversity-v2
selection path produce identical canonical reports/previews and identical complete
selected gzip/logical hashes. [PROFILE.json](evidence/selection-round24/PROFILE.json)
owns precise pins, settings, environment, counts and call-boundary measurements.

| Observed boundary | First run, seconds | Same-process repeat, seconds |
| --- | ---: | ---: |
| admitted selection call, total | 4.814 | 4.676 |
| pool verification | 0.458 | 0.461 |
| staging | 0.553 | 0.571 |
| rank construction | 0.377 | 0.211 |
| context analysis | 1.463 | 1.394 |
| pair evidence | 0.404 | 0.454 |
| target ordering | 0.095 | 0.096 |
| context choice | 0.745 | 0.776 |
| selected-row serialization | 0.416 | 0.428 |

Cumulative boundaries can overlap; do not sum the table as disjoint phases.
Context choice includes calls during evidence pruning and final allocation.
The isolated allocation loop and per-stage memory remain unmeasured. The complete
selection call includes streamed selected output but excludes the diagnostic
report/preview writes afterward. cProfile adds overhead. Process-lifetime peak
RSS is 243,425,280 / 261,664,768 bytes; the second value includes retained process
state, not an independent cold-process memory comparison. Staging is 16,150,528
bytes, selected gzip 2,080,693 bytes. No OS page-cache flush or cold-disk claim.

The sample's 330 level decisions and 15,601 selected-by-policy target memberships
are **diagnostic output only**. They are not an updated 12,000-budget full-catalogue
census. The frozen 928,162 production memberships remain unchanged.

## Runtime pins and acceptance limits

Python is 3.12.14. The exact existing SudachiPy 0.6.10 / SudachiDict-core 20250825
wheels were installed into a separate ikna environment using the committed hash
lock; the dictionary's runtime hash matches the policy. All 13 quality tests now
pass with `--require-japanese-engine`, without the earlier skipped analyzer case.
No runtime dependency is added to the app.

System ICU is **76.1**, whereas the historical saved input reports **74.2**. Thus
this is a pinned current-environment diagnostic, not a historical selection replay.
Do not use it to certify unchanged old CJK boundaries or production acceptance.
Any historical replay requires the historical ICU execution contract.

## Reuse decision and remaining classifier work

Context analysis is the largest measured individual boundary on this bounded
input. Repeated staging remains roughly 0.55 seconds, and repeated input checks
remain around 0.46 seconds. These observations justify examining analysis/staging
reuse, but they do not demonstrate a full-corpus speedup or the total corpus's
bottleneck. Existing within-run rank/context reuse remains in place.

**No new cache is integrated.** A future reusable analysis shard must bind the
candidate content/registry/schema, exact traversal/rank digest, functionTop,
segmentation ICU version, Japanese engine/dictionary hash, boundary/quarantine
policy and context-selection dependencies. Complete/integrity markers must be
atomic; interruption or a changed dependent pin must reject reuse. Selected
choices must never be reused under different ranks or function-word settings.
Compare IDs, memberships, ordered contexts/origins, levels and canonical decisions
before accepting any performance-only change. Measuring cache validation/recovery
cost is part of the experiment, not optional overhead to hide.

No classifier comparison is claimed. These candidates provide source provenance,
not independently labelled translation/domain/usefulness truth. The two known
quarantined Spanish spellings are regression witnesses, not an eleven-language
holdout. Before choosing a model, separate the three scoring questions, source/
near-duplicate split and language coverage; pin model/data licences and weights;
measure missed defects and false rejection on independent evidence. Current rules
remain unchanged while that evidence is missing. More retained rows alone would
not establish improved quality.

The plan's first step now has complete input verification and a reproducible
bounded real-input profile. It remains **partial** for stage-local memory,
isolated allocation, controlled cold/reuse conditions and historical-environment
replay. These gaps are explicit; no cache/model selection, final assembly,
production admission or publication gate is closed by this round.

## Checks

Eight new sampler tests cover exact top-k byte selection, repeatability, original
order, interleaved pair caps, raw/logical corruption, invalid limits, existing
outputs and admission/registry refusal, including a registry change during build.
Profiler and existing selection regressions are rerun after final source changes.
Detailed checks and sampler-repeat identity are in
[CHECKS.json](evidence/selection-round24/CHECKS.json).
No Android/desktop build or interactive application verification is claimed.
