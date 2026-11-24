# Round 46: preserve the previous backup until publication

2026-10-12. Cumulative on Round 45. This stage addresses desktop file-output
reliability, separate from the preceding database restore transaction.

## Reproduced defects

The previous bundle writer opened the destination directly before serialization
finished. A later ZIP error could replace the prior backup with an incomplete
file. Deck-export exceptions, including cancellation, were caught and silently
skipped, allowing an apparent successful backup with missing content.

Four focused baseline tests exercised the production serialization path: three
failed (mid-ZIP error, export error and cancellation); the successful empty-history
and blank-deck case passed. Test fixtures use disposable local files only.

## Safety boundary

A completed file is first written beside its destination, then published with an
atomic move. The existing destination is preserved if writing or cancellation
fails before publication. Unsupported atomic replacement must fail safely rather
than fall back to deleting the old file. This does not promise power-loss
durability, an all-files phone-export transaction, or rollback after publication.

The bundle format and deliberate omission of empty decks stay unchanged. A failed
deck read aborts the backup instead of producing a false complete-success result.
Developer exports retain their existing synthetic-data marking and restrictions.

The backup pane clears stale success at the next attempt, resets busy in finally
and preserves cancellation. An export failure gets a saving-specific explanation
in seven locales; it is no longer mislabeled as an unreadable file or no data.

## Verification

All 18 focused tests pass: 16 new tests plus two existing bundle-restore tests.
Coverage includes mid-stream/ZIP/export failures, cancellation, failed initial
save and publication, cleanup, repeated replacement, blank/empty content, phone
second-file failure and the public developer export path. Independent source and
test review found no blocking defect. No provider that rejects ATOMIC_MOVE was
available for a real filesystem test; that behavior is bounded by the no-fallback
source contract rather than claimed as observed. Linux automated
checks do not establish native Windows replacement behavior or screen-reader/GUI
acceptance. No Android SDK installation, Catalogue work, schema change or Windows
resize patch is included.

Full verification passes **69 shared + 169 desktop tests**, zero errors/failures/
skips, and Linux release packaging. Static checks pass: 43 design contracts,
778 keys in seven locales, UTF-8/NFC text validation.
The packaged headless harness passes 11 fresh processes covering developer
scenarios, empty reopen, failure/recovery and real/developer isolation.
