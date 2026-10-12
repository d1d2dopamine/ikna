# Round 23 — selection diagnostics prepared; saved-input measurement open

2026-10-11 UTC (2026-10-12, Asia/Yekaterinburg). Baseline: the supplied complete round-22 source
archive. The owner authorizes continued work on the existing plan and requests
preservation of the application appearance. This batch changes offline diagnostic
tooling only. No app, learner database, selector policy, corpus admission, build
version, public asset or workflow changes.

## Implemented

`tools/catalog/selection_profile.py` executes the existing admitted-pool selection
path under Python's standard-library cProfile. It does not replace validation,
staging, ranks, context analysis or selection with alternate implementations.

- Original raw/logical hash, schema, provenance, language and completeness checks
  still run before selection. Inputs are hashed again after all diagnostic runs.
- First and repeated runs get separate staging databases. There is no persisted
  validated-staging or choice cache, and no speedup claim.
- Canonical report/preview and full selected gzip/logical hashes must agree across
  repeats. Variable timings stay outside the deterministic selection report.
- Inputs, source registry, pipeline source, quarantine, Python/SQLite/platform,
  segmentation, counts, staging/output bytes and execution settings are recorded.
- Output directory must be new. Failure, corruption, changed output or interruption
  cannot produce a completed PROFILE.json or overwrite earlier evidence. A failed
  directory is retained for diagnosis; retry uses another directory.
- Default input guard is 64 MiB of compressed/source-file bytes, with at most five
  runs. It is not a bound on decompressed size, memory or time. The full saved pool
  is deliberately refused at the default limit. Do not raise that limit as a
  substitute for the planned representative bounded diagnostic scope.

Example for an already prepared, independently hash-bound bounded saved input:

```bash
python3 tools/catalog/selection_profile.py \
  --pool /path/to/bounded-everyday-candidates.jsonl.gz \
  --pool-report /path/to/BOUND-EVERYDAY-POOL.json \
  --output-dir /path/to/new-profile-directory \
  --input-scope bounded-saved-input --runs 2
```

A raw prefix of the original pool cannot reuse the original whole-pool report.
A future diagnostic subset must retain its derivation and parent input pins and
must not be described as the accepted complete production snapshot.

## Evidence and limits

[FIXTURE-PROFILE.json](evidence/selection-round23/FIXTURE-PROFILE.json) records two
runs of the existing two-candidate English-to-Spanish ingestion fixture. Full
selected bytes and canonical reports agree. These tiny test inputs establish
instrumentation and repeatability contracts only. They cannot identify the real
corpus bottleneck, justify caching, forecast a full run or evaluate a classifier.
The host was shared with unrelated work, so fixture timings are not a performance
comparison even between the two local runs.

Measurements are deliberately labelled:

- cProfile cumulative call times overlap and must not be summed as disjoint stages;
- context choice is called both during pair-evidence pruning and allocation;
  the allocation loop has not been isolated;
- peak RSS is the process-lifetime high-water mark, not stage-local peak memory;
- the first process run is not a verified cold OS-cache run;
- repeated runs can benefit from interpreter/filesystem state, while all input
  checks and staging still execute;
- timing includes profiling overhead; report/preview serialization outside the
  selection call is not included in its elapsed measurement.

Therefore the plan's complete stage-by-stage memory, isolated allocation/output,
controlled cold/reused-input measurements and representative saved-input profile
remain **open**. This round prepares instrumentation; it does not close step 1
of the selection-engine plan and does not advance to optimizing from fixture data.

## Missing saved input

The large source pool is intentionally absent from project ZIPs. Round 19 pins
`catalogue-v2-everyday-admitted-pool.zip`, 545,984,900 bytes, SHA-256
`0f02c2acfbab21d1e90c3d88f1dda0db61ca789f9507b05a483ea7f2a20884e3`.
Its `everyday-candidates.jsonl.gz` is the needed input; the original
`EVERYDAY-POOL.json` already exists in `docs/evidence/everyday-round19/`.

A read-only official GitHub API check on 2026-10-11 confirms the exact artifact
`11414433600` in run [37458524156](https://github.com/d1d2dopamine/ikna/actions/runs/37458524156)
exists, is unexpired and has the matching size/digest. Its archive endpoint returns
HTTP 401 without an authenticated session. The owner previously retained
`ikna-pool-part-01.zip`, `ikna-pool-part-02.zip` and `manifest.txt`; supplying those
is an alternative to authenticated download of the existing artifact. No upstream
reacquisition or replacement pool is needed.

## Checks

- Baseline admitted-selection suite: 17 tests passed before editing.
- New profiler regressions: ten tests passed. Cover uninstrumented equality,
  repeated complete output, existing-directory preservation, byte guard, corrupt
  input, interrupted selection, divergent output, post-run input/pipeline mutation, legacy policy and single-run status.
- Existing admitted-selection suite: 17 tests passed after editing.
- Selection policy contracts, selection audit (seven tests), deck-budget contracts
  and repository text checks passed.
- Quality suite: 13 tests ran, one real Japanese analyzer case explicitly skipped
  because the pinned analyzer is unavailable. This is not Japanese runtime evidence.
- No Android/desktop compilation or interactive UI verification performed. No
  application source changed; real-platform application acceptance remains open.

## Next

Receive and verify the saved pool, derive a bounded representative diagnostic
scope with its parent pins, complete the missing measurement boundaries/memory,
and profile before choosing reuse. Keep classifier comparison/labels/rights and
production integration open until their own evidence gates pass. Final Catalogue
assembly/publication and the existing content/coverage/size decisions stay deferred.

This batch is temporarily Catalogue-only because the immediately scoped work is
its diagnostic preparation. Rebalance toward the application's existing correctness
and real-platform checks when the required toolchain/platform is available;
do not compensate with an unrelated visual redesign.
