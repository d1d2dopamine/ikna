# Round 45: all-or-nothing journal restore

2026-10-12. Cumulative on Round 44. This stage addresses reproducible persistence
and restore-error handling defects within the authorized reliability work.

## Reproduced defects

A restore inserted answer and retraction batches before rebuilding card schedules,
daily statistics, plans and component state. A storage failure or cancellation
could leave imported history or partly rebuilt state committed. Standalone replay
had the same partial-clear/write problem. Disposable Room tests reproduce both
failure and cancellation, close the database and inspect it after reopening.

Desktop bundle restore also caught a journal failure and returned a successful
bundle result. An actual temporary ZIP with an unsupported journal version
reproduces that false success; an optional empty journal remains supported.

## Changes

Journal parsing/validation happens before the writer transaction. Existing-record
lookup, additive answer/retraction import, ID remapping and the entire derived
rebuild now share one database transaction. Standalone replay has its own atomic
entry point. History format, replay mathematics, target identity and schema stay
unchanged. No network/file reads are added inside this transaction.

Cancellation checks retain the originating coroutine context because Room can
execute its connection block under a different job. Checks occur during bounded
work and before commit. A failed or cancelled operation rolls back database
writes; successful retry can import the same file without duplicating history.

Desktop bundle restore propagates journal errors instead of reporting success.
Both restore screens reset their busy state in finally and preserve cancellation.
Android reports restore status beside its restore control; failure/status text
uses a polite accessibility live region. Seven locales receive a concise error
message. A file that cannot be opened is no longer treated as an empty journal.

## Boundaries

This is atomicity of the review journal and its database-derived state. A desktop
bundle contains separately imported decks and settings: a later journal failure
does not imply that those earlier deck imports were undone. The error message
does not claim that nothing changed. No whole-bundle transaction, power-loss
backup durability, Android/Windows runtime acceptance or resize fix is claimed.

The existing treatment of skipped malformed/synthetic lines is preserved.
Developer fixtures and temporary databases are used for regression tests; no
learner data is opened or removed. Catalogue/discovery and release metadata are
unchanged.

## Verification

See `evidence/restore-atomicity-round45/` for baseline and regression evidence.
The original backend failed four of six baseline tests; the old desktop bundle
path failed one of two tests. The final focused suite passes 11 tests, including
62 fault/cancellation checkpoints, durable reopen and idempotent retry, concurrent
signature deduplication and a locally constructed APKG nested-transaction case.

Both Anki importers retain an originating-caller checkpoint before their outer
commit and preserve CancellationException. Cancellation during nested replay can
be noticed only when control returns to the outer checkpoint; this bounds the
rollback claim, not a promise of immediate interruption for every imported file.
Standalone developer component rebuilding remains outside this atomicity claim.

Independent source review found no blocking regression. Android was not built
because SDK installation/terms remain unapproved; Windows/Android visual and
screen-reader acceptance have not run.

The complete JVM suite passes: **69 shared + 153 desktop tests**, zero failures,
errors or skips. Linux release packaging succeeds. Static checks pass: 43 design
contracts, 5 classic-card contracts, 4 small-viewport contracts, 777 localization
keys in seven locales, and UTF-8/NFC text validation. These are not real-device
UI or screen-reader checks. The committed Android grading/restore test constructor
is updated for the mandatory database dependency, but instrumentation did not run.

The packaged Linux headless harness passes 11 fresh processes (six developer
scenarios, empty reopen, failed request/recovery and real/developer isolation).
