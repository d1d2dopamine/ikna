# Round57 — review duration safety

Status: completed local stage; full regressions and build smoke passed.
Baseline: sealed Round56, SHA-256
`103240a40a36c73b7a6913f564db7de02fbf66f688a7dbf02a6d37c7d36f91d4`.

## Bounded hypothesis

Desktop currently computes active duration from wall-clock subtraction and passes
it to the repository without Android's 0..120000ms clamp. A backward clock change
can therefore produce a negative event duration and negative active-time effects.
Journal synchronization explicitly rejects negative duration. Reproduce actual
recording and import/transfer behavior before changing production code.

## Intended fix and acceptance

Use monotonic elapsed time for UI active-duration measurement and the existing
Android per-card bound at the shared new-answer recording boundary. Event wall
clock timestamps, existing grading-latency signals, FSRS and optional purpose
remain unchanged. Do not silently rewrite historical immutable events.

Test negative/zero/sub-millisecond/exact-limit/over-limit/extreme values, normal
and optional answers, Undo/replay, goal accounting, exported/synchronized payloads
and UI wiring. Preserve legitimate old long-duration records during supported
restore. Record the compatibility consequence of capping future desktop per-card
intervals at the existing Android 120-second ceiling.

Stop after the evidenced defect is fixed and the finite regression/build gates
pass. This is not clock-causality repair, background timing measurement or a
promise that wall-clock timestamps on different devices agree.

## Captured baseline (2026-10-17)

On byte-identical Round56 production (300 compared source files), a synthetic
backward wall-clock interval of -5000ms was persisted as review.durationMs=-5000
and daily activeMs=-5000. Journal merge rejected this record. Manual JSONL
restore accepted it and recreated negative active time. The expected-safe
regression failed with expected 0, actual -5000; this is a deliberate red test,
not a passing verification result.

The narrow import correction rejects negative durations during whole-input
validation, before restore preparation or any mutation. Nonnegative historical
values, including values over 120000ms, remain unchanged. Checked daily active
sum arithmetic must roll back an overflowing new write rather than wrap; this
is a safety rejection, not saturation or silent historical repair.

Existing invalid immutable rows are not rewritten by this round. A transfer
containing such a row can still fail explicitly. Monotonic UI timing does not
claim pause/background exclusion or repair event timestamp causality.

## Focused verification

133 cases passed, zero failures/errors/skips: shared44 (passed in run1, unchanged
and up-to-date in run2), desktop86 and Android JVM3 executed in run2. This includes
nine real Room duration tests and four helper/source-contract tests. Final source
has 464 compile-input entries, aggregate SHA-256
`562bf26edb75e23e9f8eb913e97d24b6f224cd83a0c1d55a055e1f08570ebd19`.

Run1 retained two new fixture failures: inconsistent scheduler day boundary and
invalid learned historical FSRS pre-state. Only fixture inputs were corrected;
production and exact assertions were preserved. Full-suite results are recorded below.

## Full regression result

1,191 cases: 1,189 passed, two pre-existing non-mouse pointer skips, zero failures
or errors. Shared desktop151, desktop328 (326 passed), shared Android JVM144,
Android app JVM568. All 464 compile inputs are unchanged before/after the run.
The full run includes the separate-process crash/restart, consent, incremental,
optional-accounting, restore and history-integrity regressions.

Raw evidence is retained separately; compact source/XML/log hashes and counts
are packaged in `docs/evidence/review-duration-round57/`. Synthetic regression
fixtures contain no real corpus or learner history. Source schema14 and wire4
are unchanged. No catalogue or application-content changes are made here.

## Build smoke and compatibility

`:app:assembleDebug` and `:desktop:createDistributable` passed. APK v2 signature
matches the baseline identity. The packaged Linux launcher passed its headless
selftest in a fresh isolated home; read-only database inspection confirmed
schema14, quick_check=ok and the unchanged nullable purpose column. Zero packaged
decks/cards are expected for corpus-free source, not a bundled-content test.
Five existing static checks passed. No physical Android/Windows acceptance,
background/lifecycle timing accuracy, internet routing or power-loss claim.

Round56 remains the source rollback checkpoint. Round57 adds no schema/wire
migration. It intentionally does not provide a repair flow for already-invalid
immutable durations; those require a separate explicit recovery decision.
