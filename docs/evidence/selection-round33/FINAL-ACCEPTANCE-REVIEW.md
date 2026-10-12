# Independent finalized Round 33 review

Reviewed 2026-10-12 UTC after successful guard exit and completed strict candidate/control comparison. No blocking evidence gap found for the narrowly scoped output-preserving overlap-reuse integration, subject to retaining this checkpoint and applying the already-reviewed identical policy and durable regression tests.

## Checks performed now

- Inspected finalized `CANDIDATE-CONTROL-COMPARISON.json`, guard/profile/report bindings, complete report differences, stage timing/CPU/RSS comparison and limitations.
- Independently rehashed all 10 bound small evidence/helper files and rechecked 237 original/isolated source and prototype pins.
- Normal source remains original; only the isolated `selection_policy.py` differs. Its chooser matches the reviewed prototype, and the rest of the policy AST matches original.
- Selected gzip size was rechecked. The completed helper already independently traversed the whole gzip, checked its CRC, and verified compressed/logical hashes and counts. This review did not repeat that large read, inspect SQLite, rerun selection or run another full evaluator.
- Confirmed the Round 34 proposal records exactly the fully replayed policy SHA-256: `72783a30ada3995c9cc4e32f96a4fc5ff53940756ceb5895c9530ecd9af92edc`. Its separate durable-regression review remains the owner of those 17-test results; they were not rerun here.

## Outcome

The strict comparison reports exactly one complete-report difference: `/selectionPipelineSha256/selection_policy.py`, with the specifically reviewed original and candidate hashes. No other code-hash, environment, count, deck, quality-counter or output-byte difference was allowed.

- Compressed selected SHA-256: `a2a5e4bb1e4079e88c8986443c95761795d5fe66ec14ee46a1f83e099e95df64`
- Compressed bytes: 255,938,165
- Logical selected SHA-256: `7149ca35ebc44b0833434505a94d187e11194a2eca7d4f45b885b061fb267cd4`
- Logical bytes: 1,512,254,677
- Memberships: 928,162
- Complete counts, deck inventory and quality counters equal both Round 30 and the historical material.

Observed selection elapsed time decreased from 8,335.166 seconds to 4,288.253 seconds (48.55% less). Pair-evidence elapsed decreased from 7,187.008 to 3,169.583 seconds; its process CPU decreased from 6,853.630 to 2,840.084 seconds. This concentration and CPU change support the intended computation reduction; it is not merely a headline wall-clock comparison.

Candidate process-lifetime peak RSS was 1,664,880,640 bytes versus control 1,666,994,176 bytes. Candidate minimum monitored disk free space was 4,117,577,728 bytes, above its explicitly authorized 3 GiB stop floor. No resource stop occurred.

## Scope and caveats

One full original run and one full candidate run were measured on a shared host with uncontrolled OS/cache conditions. This is an observed comparison, not a calibrated repeated-run or other-corpus speed guarantee. The historical cap 8,000 was measured; this is not a full cap-12,000 benchmark. Guard disk floors differed, and cgroup visibility remained unknown. Sampled stage RSS is whole-process residency, not isolated allocations.

Byte-identical selected material retains the applicability of the completed Round 30 structural audit. Neither equivalence nor faster execution establishes semantic-quality gain, new content/source admission, content freeze or publication permission. No source or producer files were edited during this review.
