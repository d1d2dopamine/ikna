# Round 43: atomic answer and Undo persistence

2026-10-12. Cumulative on Round 42. This stage changes application reliability
only; the discovery tool and all Catalogue v2 work are unchanged.

## Reproduced defect

LearningRepository serialized answer and Undo with a Mutex but committed their
DAO writes separately. An answer could update the schedule before its immutable
review was saved. Undo could restore a card before appending its retraction;
subsequent Undo could then reject the state mismatch. Retrying an error did not
necessarily restore consistency. Derived components, daily statistics and earned
plan entries also crossed independent commit boundaries.

A disposable actual-Room regression failed all four tests against the original
code. Close/reopen snapshots confirmed persisted partial writes, rather than
merely an exception in an in-memory mock.

## Fix

Both operations now retain the existing Mutex and execute the complete unchanged
helper body inside one database transaction. The mandatory database is injected
at the Android and desktop construction sites; all DAOs belong to that database.
Caller context is captured before entering Room and checked before commit because
Room 2.7.2 uses its own database job inside the transaction. Failure or originating
coroutine cancellation rolls back all these DB writes together.

This does not change Scheduler, Governor, grading, review formats, schema, or the
existing Undo policy that retains component exposure observations. External
settings and in-memory state are not falsely included in the rollback guarantee;
callbacks on these paths only read settings. No user data was opened or erased.

## Verification

Evidence: [learning-atomicity-round43](evidence/learning-atomicity-round43/).

- Before fix: four focused tests failed on persisted partial writes.
- After fix: all four pass, covering 24 fault/cancellation scenarios across seven
  answer and five Undo write boundaries, including final-write cancellation.
- Each scenario verifies rollback after database reopen, a successful retry with
  exactly one answer/retraction, then another reopen.
- Full shared/desktop tests pass; exact totals are in tests.json.
- Text/NFC, 43 source-design and five classic SQL checks pass.
- Independent source review found no blocker; unchanged helper bodies preserve
  successful-path behavior and the established append-only Undo contract.

Android compilation/device behavior and Windows runtime remain unverified. No
Android SDK agreement was accepted. No speculative window/cosmetic patch is part
of this stage. The full cumulative archive retains Round 42 and earlier files,
README, signing identity and schemas, with the same corpus/sample omissions.
