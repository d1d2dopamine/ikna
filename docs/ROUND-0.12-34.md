# Round 34 — exact overlap reuse integrated

2026-10-12, Asia/Yekaterinburg. The normal offline selector now uses the exact
call-local overlap reuse accepted by the full [Round 33](ROUND-0.12-33.md) replay.
Only `selection_policy.choose_contexts` changes in production code. Three test-only
files preserve an independent pre-optimization reference and durable regressions.
No application/UI, learner-state, schema, signing identity, source admission,
selection thresholds, default cap or persistent analysis cache changed.

## Acceptance before integration

The complete historical cap-8000 run covers 11,099,490 candidates and 110 directed
pairs. Candidate and original selected gzip bytes, logical bytes, 928,162 included
memberships, 2,094,192 contexts, 2,094,728 origins, all deck decisions and quality
counters match exactly. The complete report differs only at the reviewed policy
fingerprint. That candidate file is copied byte-for-byte into normal source:
`72783a30ada3995c9cc4e32f96a4fc5ff53940756ceb5895c9530ecd9af92edc`.

The observed complete selection elapsed time is 8,335.166 → 4,288.253 seconds,
48.55% lower for these two runs. Pair evidence wall time is 7,187.008 → 3,169.583
seconds, with corresponding process CPU reduction (6,853.630 → 2,840.084 seconds).
Peak process memory remains about 1.66 GB. These are one original and one candidate
full invocation under shared-host/uncontrolled cache conditions, not a guaranteed
speedup on every input or machine. The bounded 105,988-row smoke below is an
identity check, not a claim that all smaller workloads become faster.

The new map stores one exact maximum and evaluated-prefix length per candidate
object within one call. Both source passes, ranking/tie order, dictionary equality,
original context object identity, duplicate handling and rejection counts remain.
Selected contexts are append-only; each candidate/selected-position overlap is
computed at most once. Additional state is O(N), never a persistent cache.
Existing token LRU recency/hit counts can change; that cost is included in the
measured candidate. No approximation or content-score policy is introduced.

## Durable tests and post-integration checks

- 143 relevant tests pass on the actual normal source: 126 existing tests plus
  17 new overlap/reference regressions. This includes 3,177 differential cases,
  1,500 seeded random fixtures, all 11 languages with real CJK segmentation,
  aliases/equal dictionaries, stable ties, source passes, rejection traces,
  object identity, natural LRU churn and synthetic pair/allocation behavior.
- The exact old chooser is retained in a test-only fixture with source/function
  hashes. It never aliases the production chooser. Deliberate identity/order/
  rejection-count mutants prove the differential assertions can detect changes.
- The 13-case quality suite also passes when rerun with the explicit required
  Japanese-engine flag; these cases overlap the 143-test total, not 13 extra
  unique tests. No missing-engine skip is presented as success.
- Independent final review verifies the accepted policy bytes, original-reference
  identity, intended three test additions, all 360 archived app/shared/desktop
  files and the signing keystore unchanged, default cap 12,000, and no persistent
  cache activation. Its independent 17-test rerun also passes.

A guarded normal CLI run processes all 105,988 rows of the earlier deterministic
saved-input diagnostic at the unchanged default cap 12,000. It reproduces the
historical bounded selected compressed/logical identities, preview identity,
complete stage counts and summary. Independent full gzip CRC/hash reading checks
121,673 serialized memberships and 128,746,924 logical bytes:

- Compressed SHA-256: `2efae253c630285c7b7de5f9de5e076117c39981775cea181e6a7ef51bdc16f6`.
- Logical SHA-256: `7ab05cd6a7a03221cf818176c9671b99f33646fe7f98a173fa716bbb1c7d3d2e`.
- Measured selection time 21.281 seconds; sampled guard peak RSS 313,241,600 bytes.
- Guard: 768 MiB sampled RSS, 2 GiB address space, 2 GiB available memory and
  3 GiB free disk. Corrected run exits zero with no threshold crossed.

The first smoke invocation omitted the required `--input-scope` flag and was
rejected by argparse before selection (exit 2). Its failure record is retained.
The corrected explicit `bounded-saved-input` invocation uses new output/evidence
directories; no implementation change or guard bypass was needed.

## Reproduction, rollback and scope

[selection-round34](evidence/selection-round34/) contains integration provenance,
actual test logs, independent-reference review, both smoke invocation outcomes,
new source/runtime/input bindings and the exact selected-material comparison.
The conditional proposal evidence deliberately retains its pre-acceptance status;
`INTEGRATION.json` records the subsequent accepted/verified integration.

The full replay was executed in an isolated copy before normal integration.
Only the identical production function plus test files was then installed; the
normal-source smoke verifies the current pipeline including new test fingerprints.
There was no second full replay after copying identical production bytes.
Round 33's full source ZIP remains the rollback checkpoint, SHA-256
`e92926478049d2d9b9b571c7f6464a1baf49cd3d697c85f8c7dc5210e18e6e48`.

Old diagnostics deliberately bind their original complete source manifests.
To rerun them, restore the matching historical checkpoint and retained input
layout; do not weaken their source checks to accept this newer source. The new
fixture files legitimately change the broad diagnostic pipeline manifest. Test
fixtures are not imported by production and contain no new catalogue material.

Large corpus, staging databases, returned prefix payloads, full selected output,
model archive and ignored app/build assets are not included in the source ZIP.
Their identities and provenance are retained in the corresponding reports.
The optional larger bounded diagnostic input remains separately delivered as
recorded by Round 25; none of the user's original evidence was deleted.

## Plan status and remaining decisions

The measured performance/reuse change is complete for this historical control.
Full default-cap-12000 selection and controlled OS-cold measurements are not
claimed; no global host-cache clearing was performed. First/fresh/reused-process
and cache-population conditions are reported separately in earlier rounds.

Mandatory provenance/identity/boundary gates and separate content questions are
documented. Retain current rules: classifier inference/accuracy comparison is
still deferred by the pack provenance/rights and benchmark-use/runtime gaps in
[the feasibility assessment](CLASSIFIER-FEASIBILITY-0.12.md). No semantic quality
gain, model execution, new source admission, content freeze or publication occurs.

Application verification remains as [Round 26](ROUND-0.12-26-APPLICATION.md):
137 shared/desktop tests and headless packaged startup checks passed with unchanged
application source. Android requires the unresolved SDK agreement/setup decision;
GUI/Windows/multi-monitor/native restart acceptance remains unverified. These
limits are not repaired by offline selector performance work.
