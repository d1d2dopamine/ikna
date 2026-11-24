# Round 38: safe erase and visible preparation failures

2026-10-12. First approved reliability stage of the interface audit, cumulative
on Round 37. Existing appearance, learning policy, Catalogue data and README
presentation are preserved.

## Changes

- Android erase now requires the review export (unless history is empty) and
  settings export to succeed. A MediaStore null document/stream or write/close
  failure is an error, not an empty journal. A failed backup leaves the database
  untouched and presents a retryable explanation. Automatic exports also receive
  the real failure instead of silently succeeding.
- Desktop erase requires all deck texts to be written and read back first.
  Every attempt uses a separate directory below `export`; ordinal filenames
  preserve decks with colliding titles. This is still a deck-text copy, not a
  review-history backup. The existing warning to export history separately stays.
- Backup and erase failures have distinct messages; cancellation propagates and
  is checked before destruction. The erase action is disabled while running.
  No erase-without-backup bypass was added.
- Both introductions commit `onboardingDone` only after preparation succeeds.
  Failures remain on the screen with a retry action. The desktop startup shell
  also distinguishes failed preparation from readiness.
- Bundled pack installation skips only intentionally absent optional files.
  Parse, read, database and cancellation failures now reach callers. Previously
  a catch-all inside the loader could defeat the screen's failure handling.
- Session and Browse loads distinguish failure from a genuine empty result.
  Failed/loading states cannot expose stale cards or false completion; retries
  reload saved state and deduplicate pending Android requests. Optional speech
  failures are contained without swallowing cancellation.
- Eight new message keys are present in all seven interface languages.

No database schema, scheduling, grading, target identity, review-history format,
version or publication change is included. No live learner data was erased.

## Verification

Evidence: [reliability-round38](evidence/reliability-round38/).

- Official portable Temurin 17.0.20.1+1 and Gradle 8.10.2 archives were verified
  against their official SHA-256 files. Toolchain and dependency cache are outside
  the source package. Builds used one Gradle worker and bounded heaps.
- `:shared:desktopTest :desktop:test`: successful compilation and 39 shared plus
  130 desktop tests; zero failures, errors or skips. Of these, 32 new behavioral
  tests cover backup ordering/storage failures, disposable deck files, the actual
  bundled loader, preparation commit order, retry/cancellation and stale-card
  suppression. The Android export guard is pure shared JVM code and is exercised
  by the shared suite; this does not test a real Android MediaStore provider.
- `:desktop:createReleaseDistributable`: Linux application image built, including
  ProGuard and runtime packaging.
- Existing bounded package harness: 11 packaged headless processes pass across
  six DEV scenarios, empty reopen, failed request/recovery and disposable REAL
  profile isolation/return. The harness refuses a pre-existing test home.
- Text/NFC, localization and 42 source-design checks pass.
- Independent source review identified and rechecked the bundled-loader
  catch-all defect before final verification.

Initial build setup encountered a stale proxy address and a read-only default
Android preferences directory. The isolated build used the active cloud proxy,
existing OS certificate trust store and a writable private preferences directory.
No certificate verification was disabled and no Android SDK was installed.
One execution session ended before desktop testing; the resumed command completed
successfully. Successful logs are retained with the verification evidence.

## Remaining checks

Android compilation/device behavior, GUI layout, screen-reader behavior and
Windows rendering were not tested. The Android SDK agreement remains pending.
Headless Linux tests do not establish Windows or Android acceptance. The reported
Windows 11 white flash/content lag and missing minimize animation remain open;
no speculative window patch is part of this stage.

Next interface stages address catalogue status/filtering and cancellation,
Undo visibility, touch feedback and constrained/large-text layouts. This stage
does not claim that the entire interface audit is implemented.

## Delivery

The full source ZIP preserves Round 37 README/assets/signing material and previous
compact evidence. It excludes the same corpus/sample payloads, build outputs,
downloaded toolchains, caches, runtime image and disposable databases. The refreshed
manifest records the immediate Round 37 archive as its source. The archive is
verified by member hashes and ZIP CRC and remains below 20 MB.
