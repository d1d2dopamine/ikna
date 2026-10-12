# Conditional overlap integration: independent reference review

Reviewed 2026-10-12 UTC. Result: no material issues found within the requested proposal/reference scope. This is a conditional integration review, not full-candidate replay acceptance.

## Verified independently

- Proposed `selection_policy.choose_contexts` has exactly the reviewed Round32 `overlap_reuse.py` function source and AST. Chooser SHA-256: `f4f1b0a51b8645153b3f4fd08a55caf2d152d1d96add7238cbecd8e4c66d0d2a`.
- Replacing the chooser body with a sentinel makes the proposed and normal production modules byte-identical. All imports, deterministic helpers, quarantine handling, target selection and other production content are unchanged.
- The frozen fixture chooser has exactly the pre-optimization production chooser source and AST, SHA-256 `54f69e0c45fa4426d4a8dc563bd5b6055d5fd0a8d061a2c6f0f072e50c2e098c`. Its recorded complete original module SHA-256 matches the current unchanged control source: `6100810b5bd7eca23b063f90dd0c1e81cb3a7f55ba3aeab76f02be84720fb3ae`.
- The reference is loaded as a separate module and owns its function globals/code. Its chooser is the old algorithm, not an alias of the candidate. Sharing the three unchanged deterministic helpers is intentional and asserted.
- All four proposed file byte counts and hashes agree with `PROVENANCE.json`; the conditional patch was independently reconstructed and matched exactly. All four candidate-test-repo proposal copies match. Its 117 other non-bytecode files are byte-identical to normal source.
- There is no new production import or runtime dependency on fixtures, proposal/results folders, prototype code or docs. The tests and fresh-process probe use in-repository code and test-local fixture paths.

## Tests and coverage

Executed only the new standard-unittest discovery suite in the separate `round34-integration-proposal/candidate-test-repo`, using `/workspace/scratch/8105054a3211/ikna-venv/bin/python -B -m unittest discover -s tools/catalog -p test_selection_overlap_reuse.py -v`, with bytecode disabled and the explicitly pinned ICU 74.2 library/version/runtime path. Result: 17 tests passed in 2.752 seconds.

Coverage includes returned values and original-object identity/order; deliberate identity/order/counter mutants; equal dictionaries, repeated references and duplicate IDs/text; rejection-key deduplication; exact near-duplicate call order and thresholds; stable ties and both source-diversity passes; missing-language placement and malformed inputs; exhaustive small lists and 1,500 seeded random fixtures; all 11 supported languages and real CJK ICU; threshold boundaries; reduced overlap calls; O(N) call-local state with no cross-call reuse; cold and repeatedly used subprocesses with natural traffic around the 8,192-entry LRU capacity; and synthetic in-memory pair pruning, quarantine counters and allocation equality.

The multilingual test now requires a real ICU engine/version rather than hard-coding 74.2 in the portable regression file. This review execution independently pinned and enforced ICU 74.2 through the existing segmentation loader environment contract.

## Boundaries

Read `build_proposal.py` without executing it. No proposal files, normal source or active Round33 candidate source were edited. No corpus replay, download, UI operation or source integration was performed. Full-candidate semantic/performance acceptance remains the separate Round33 gate.
