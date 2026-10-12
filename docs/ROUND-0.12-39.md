# Round 39: catalogue outcomes and time to undo

2026-10-12. Second approved interface stage, cumulative on Round 38.

## Changes

- Download/import results stay beside their deck instead of a distant footer.
  Results for one deck survive another download. A polite live region exposes
  the result to accessibility services; actual reader behavior needs testing.
- Dependent language, topic and level choices are normalized in order. Valid
  choices and typed search remain. Android and desktop share the same options,
  ordering and legacy pair-tier fallback.
- Licence details precede the download/re-download action. Installed status is
  still visible on collapsed rows, whose action explicitly opens details.
- Catalogue content, memberships, contexts and metadata import in one Room
  transaction. Write failures, a cancelled originating operation and an empty
  import roll back together. File-import semantics and catalogue format/content
  are unchanged. Blocking network reads can still wait for their timeout;
  leaving the screen is not a promise of instant cancellation.
- Room 2.7.2 runs writer work in its database coroutine context. The import
  captures its caller context before entering Room and checks that context
  before committing. Checking Room's context would miss screen cancellation.
- A committed installation remains successful if refreshing the study plan
  fails. The row explains the partial outcome and retries only the plan refresh,
  without downloading or importing again. A failed installed-list read retains
  known installed state. One new message is supplied in all seven languages.
- Undo no longer disappears after six seconds on either platform. Existing
  dismissal, replacement by subsequent actions and append-only retraction remain.
  This is not a permanent undo history or persistence across application restarts.

No scheduler, grading, learner-history format, schema, version or release changes.
No real user data was touched.

## Verification

Evidence: [usability-round39](evidence/usability-round39/).

The full shared/desktop build uses project Kotlin 2.2.20 and the verified isolated
JDK 17 / Gradle 8.10.2 toolchain. All 47 shared and 135 desktop tests pass with zero failures, errors or skips;
counts and successful logs are retained in the linked evidence. New tests cover dependent filter normalization (including 400
combinations), plan-refresh failure/retry/cancellation, actual Room rollback after
five write stages, cancellation at each stage, successful reimport, failed
replacement and empty/malformed imports. The existing append-only Undo integration
test now waits one simulated minute before undoing.

The first cancellation fixture deliberately waited inside Room and exposed the
separate database Job: cancelling the caller could not release that fixture.
The test was stopped, the captured-context check added, and the fixture changed
to cancel the caller then release the transaction explicitly. This verifies that
completed writes still roll back when the originating operation is cancelled.

Text/localization, 43 source-design and nine palette checks pass. Source checks
are not visual or screen-reader tests. Android compilation/device tests remain
unavailable without the separately pending SDK agreement. Windows and GUI
behavior remain unverified.

## Current Windows report

The owner corrected the report on 2026-10-12: the earlier white flash is outdated
and is no longer a current bug. Current Windows 11 concerns are laggy/non-smooth
edge resize and maximize, plus missing minimize animation compared with GitHub
Desktop. The exact affected build/commit is still needed. This stage does not
modify native window behavior or claim a Windows runtime fix.

## Acceptance still needed

- On actual Android and desktop: interrupt a download/import by leaving, return,
  retry, and verify no partial installed deck or misleading success.
- Trigger download/import and plan-refresh failures separately; verify nearby
  results and that plan retry does not download again.
- Change learning/meaning language, topic and level in different orders; selected
  options must stay valid and deliberate search must remain.
- Pause more than six seconds after an answer, undo, then verify restored card
  state and append-only history. Check dismissal and a subsequent answer too.
- Test keyboard/screen-reader traversal of details, download and result messages.

The full source delivery preserves README, signing material, assets, schema and
all earlier compact evidence; existing corpus/sample omissions remain. Build
outputs, downloaded tools and disposable databases are excluded.
