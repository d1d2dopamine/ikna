# Round58 — required completion versus automatic Android repeats

Status: completed bounded local stage; full regressions and build smoke passed.
Baseline: sealed Round57, SHA256
`00279f3fd40936dbc880f7d8262b3936c6a094fe8e406cd66b7f6bed301185a8`.

## Scope and acceptance

The owner's completed-main-lesson contract requires further hard review to be an
explicit choice. Android currently appends AGAIN and first-contact repeats in
memory, while the persisted required plan counts distinct normal answered keys.
Round50 documented this inconsistency. A filtered deck reaching N/N is not proof
that the global required plan is complete.

First establish actual repository completion/restart/Undo behavior using Room and
a failing source-routing contract for the unchanged Android implementation. This
is not a live Android ViewModel or physical-device reproduction: the existing
local JVM setup lacks that runtime harness. If confirmed, wire a minimal tested
queue-boundary decision to durable global completion, without marking unanswered
required keys complete or changing grading/FSRS. Keep explicit extra work and
finite optional-hard batches distinct.

Acceptance: all-AGAIN and mixed answers, one/small required plan, filtered-deck
incompletion, first-contact repeats, Undo reopening and restart, no extra goal or
new-card budget, and no stale async completion overwriting a later action.
Preserve failing evidence; stop after the bounded correction and checks. Do not
infer physical gesture/lifecycle acceptance from source contracts.

## Baseline evidence (2026-10-17)

Unchanged Round57 production: 301 compared files identical. A real Room fixture
with one required target and four retained AGAIN answers has planCompleted=true.
The bounded trace of the unchanged source-guarded Android queue expression still
has one pending repeat; a rebuilt repository session has zero. The expected-safe
queue assertion fails (expected0, actual1). A separate filtered-deck case confirms
that completing one deck does not finish another deck's required key. Android's
structural test also fails because no durable global completion gate is wired.
This proves repository state plus copied, source-checked queue policy, not a live
Android ViewModel or physical interaction.

The narrow fix uses an existing-plan read only: missing plan=false, existing empty
required prefix=true, otherwise actual distinct required-key coverage. Optional
hard review keeps its separate nonempty-prefix availability condition. After a
committed ordinary answer, discard only already-answered transient repeats when
global completion is true; preserve every unanswered planned/explicit-extra key.

## Explicit concurrency limit and next stage

This stage filters transient copies tracked as answered by the current local
session. It does not refresh an already-open Android session after concurrent
synchronization. Android's current onMerged callback is empty; changed merge
invalidates the persisted plan, so a missing plan is conservatively incomplete.
If another action rebuilds the plan while this UI remains open, remote-answered
keys can still remain in its stale queue. Do not interpret the local fix as a
complete cross-device session-refresh solution. A separate bounded Round59 must
cover an immutable completion/pending-key snapshot and lifecycle-safe refresh,
in-flight answer ordering, optional batch invalidation and no new-budget credit.

## Focused verification

67 cases passed, zero failures/errors/skips: shared15, desktop39, Android JVM13.
Includes seven real Room completion cases and eight Android pure-helper/source
contracts. Source467 compile inputs remained unchanged, aggregate SHA256
`cb41b2dfd10d8e867dd217c8ef73dfbb8a5cee49812213867efb99f2aa871a20`.
The intended red baseline is retained; the first post-fix focused run passed.

## Full regression result

1,206 cases: 1,204 passed, two pre-existing pointer skips, zero failures/errors.
Shared desktop151, desktop335 (333 passed), shared Android JVM144, app JVM576.
All467 compile inputs match the focused revision before/after the full run.
The separate-process crash tests, optional-accounting, duration, migration,
restore and sync suites remain green. These counts include repeated shared
coverage on two JVM targets; they are not unique scenario counts.

## Build and delivery

Android debug APK and Linux distributable built successfully. APK v2 signature
matches the baseline identity; packaged Linux headless selftest passed in a fresh
isolated home. All467 compile inputs remain identical through builds. Five static
checks passed. Schema14 and wire4 are unchanged. Physical Android/Windows and
concurrent open-session refresh are not established by these results.

Compact evidence: `docs/evidence/completion-boundary-round58/`. Round57 remains
the verified rollback source. Round59 is the separately bounded follow-up for
safe invalidation after committed sync, with no automatic new plan admission.
