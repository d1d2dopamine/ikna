# Round 27 — fail-closed context-analysis reuse experiment

2026-10-12, Asia/Yekaterinburg. This implements the measured next experiment from
round 25. The normal selector and application still use no persistent analysis
cache. No visual changes, source admissions, model scores or publication occur.

## Implementation

`selection_analysis_cache.py` persists per-language exact analysis rows and the
returned quality counters. Keys bind exact original context text and ordered keys,
full frequency ranks, function settings, canonical policy, parent pool/report and
registry hashes, analyzer/runtime identities (including all three mapped ICU
libraries for CJK), dictionary evidence and implementation dependencies. Choices
are never reused under changed ranks. Changed inputs create a different key.

The immutable gzip payload is linked first and the checksummed manifest last.
Incomplete or corrupt entries fail closed; they are neither silently repaired nor
overwritten. Full payload/schema/count/context checks occur in temporary SQLite
before touching staging. Restore is transactional even when the original staging
connection used journal_mode=OFF: journal mode is temporarily changed, rollback is
verified, and its original mode restored. An uncommitted OFF connection is refused.
The cache directory must be outside the source tree and cannot contain staging.
Checksums establish local integrity, not authentication of an untrusted cache.

`selection_cache_experiment.py` is a standalone, explicitly experimental wrapper.
A scoped hook replaces context analysis only and restores itself on success/error.
Every invocation still validates the entire supplied source pool and provenance,
rebuilds staging/ranks and recomputes pair evidence, allocation and output. Cache
hit diagnostics never enter canonical selection output. There is no production
selector flag that enables this cache.

## Real measurements

The unchanged round-25 deterministic 105,988-row diagnostic spans 110 directions
and 100,499 contexts. It uses sample ranks, not full-pool ranks. Its optional input
is delivered separately to keep the code checkpoint below the owner's 20 MB cap.
See `evidence/selection-round25/INPUT-DELIVERY.json` for restoration and hashes.

The initial nine measured runs use the same pinned ICU 74.2 / Python 3.12.14 /
Sudachi environment as round 25. Baseline fresh-process median was **20.416 s**
(four runs, range 20.195–20.963); fresh warm-cache median was **17.535 s**
(three runs, range 17.520–17.558), an observed **14.1% lower elapsed time**.
First cache population cost **24.720 s**; its same-process second call cost
**19.378 s**. Thus cold population is slower, and same-process behavior is not the
same as starting with a populated disk cache. Cache artifacts total **9,960,806
bytes** in 22 files. Fresh warm process high-water RSS was 294.6–298.4 MB versus
365.6–386.5 MB for these fresh baselines; this is process memory, not allocations
attributable solely to the cache.

All nine canonical reports, preview identities, compressed selected bytes and
logical selected rows match exactly. Selection timing includes cache key creation,
integrity validation and row import/write costs. Full profiles and cache event
records accompany `evidence/selection-round27/SUMMARY.json`.

These are small, nonrandomized repetitions, without statistical confidence bounds.
OS page cache was not flushed or controlled. “Cold” means an empty analysis cache;
“fresh” means a fresh Python process. Neither means OS-cold input. These results
are no guarantee about full-corpus runtime, cache benefit or production readiness.

## Review and verification

An independent review found no decision/integrity bypass, and reproduced exact
identity across all nine runs. It found one error-contract gap: malformed DEFLATE
could raise raw `zlib.error` rather than `CacheIntegrityError`. The rows remained
unchanged, but the exception normalization was corrected and regression-tested.
The nine-run profiles retain their exact pre-correction source pins; a separate
final-code baseline/cold/warm equivalence refresh is retained, rather than changing
historical pins. This correction only changes malformed-input error handling.

111 relevant tests pass under the pinned runtime: cache, experiment, profiler,
stage observer, deterministic diagnostic derivation, segmentation, admitted
selection, quality and audit tests. Corrupt bytes, altered counters, foreign/missing
rows, incomplete publication, failed SQL restore, changed dependencies and failed
mandatory source checks are covered. `TESTS-FINAL.log` records the actual result.

## Decision and next boundary

Keep this as a measured offline prototype. Warm reuse is promising on this fixed
sample, while first-use cost and invalidation are real. Do not silently integrate
it or infer a full-workflow speedup. Next measure the unchanged selector on the
complete saved input under explicit resource bounds, preserving partial evidence
if it cannot finish. Independent content/classifier feasibility is a separate
round; structural pass counts remain distinct from semantic correctness.

Final-code refresh: baseline20.312s; empty-cache24.348s; same-process hit19.202s; fresh-process hit17.587s. All four complete output identities match. See FINAL-REFRESH.json.
