# Independent prototype and harness review

Read-only source review completed 2026-10-12 UTC. No blocking finding for the reviewed bounded prefix experiment. This review does not approve production integration or establish full-workflow benefit.

## Candidate

Reviewed original `selection_policy.choose_contexts`, its callers, `overlap_reuse.py`, differential tests and natural LRU-eviction fixture. Incremental maxima preserve the original left-to-right maximum for deterministic context overlap and append-only selected contexts. Object identity indexes memoization only; original dictionary equality/removal, repeated references, rejection-key counting, source passes and stable tie order remain intact. Contexts and helper functions must remain unchanged during each call.

Independently ran pinned Python/ICU 74.2 unit discovery: 15 tests passed in 2.692 seconds. Coverage includes identity/equality distinctions, repeated references, rejection traversal, thresholds, Unicode, synthetic pair/allocation equality, and fresh-process natural eviction beyond the 8,192-entry token cache. LRU hit counts/recency can differ and must remain part of measured candidate costs.

## New standalone harness

Compared `dense_pair_candidate_probe.py` against frozen Round31 `dense_pair_probe.py`. Producer metadata, source/runtime fingerprints, immutable staging identity, read-only SQLite and exact-prefix gates remain intact. Baseline binds candidate bytes without executing them. All modes bind the new helper and candidate separately. Candidate loading verifies original dependency aliases and absence of cache priming. Only `selection.choose_contexts` is substituted during the measured call, with restoration in `finally`.

Success requires complete returned evidence bytes equal the same-helper baseline. Candidate-cProfile additionally binds the completed unprofiled candidate; its overhead ratio uses that candidate, not the original baseline. Failure/mismatch cannot produce a completed probe.

Independently ran pinned Python/ICU 74.2 unit discovery: 31 harness tests passed in 0.179 seconds, including actual tiny fixture execution through all three modes and exception restoration. No real-corpus probe was initiated by this reviewer. Real-probe outcomes must be documented separately from these review/test results.
