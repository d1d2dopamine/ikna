# Round 50 — short pointer gesture reveals the card

Base: cumulative Round49, archive SHA256
`3f626954b41d0dc3b5bd22d796bb1ded4f951729a67dcf03fdfd79d97baca7af`.

## Reproduced failure

The owner reported intermittent tap-to-reveal failure, possibly conflicting with
answer swipes. `SwipeableCard` consumes a recognized horizontal drag, cancelling
`ChunkCard`'s clickable. A short drag can exceed Compose's pointer slop while
remaining below the additional16px `PEEK_TRAVEL`. The old release handler then
returned the card without revealing it or grading it. An ordinary stationary tap
already worked in the regression fixture.

The unchanged Round49 production code failed six actual Compose pointer cases:
short left/right movement and a short recognized drag released just outside the
card, for both mouse and injected touch. Twenty other cases passed; two
mouse-only cases were skipped for the touch parameter. The clean baseline and
fixed logs/XML are in `docs/evidence/card-reveal-round50/`.

## Minimal change

`shared/src/jvmShared/kotlin/dev/ikna/ui/session/CardStack.kt` now reveals on normal
completion of a horizontal gesture that began on the front and has not already
revealed. A per-gesture marker prevents duplicate reveal callbacks before a
recomposition. The latched start-of-gesture grading gate remains unchanged, so
the same gesture cannot reveal and grade. Continuous16px peek, grading distance
and velocity thresholds, accessibility actions, click semantics and vertical
scroll handling remain unchanged. Cancel has no release fallback.

A recognized drag may finish outside the card, as before for longer front pulls;
the new short-drag fallback follows that drag contract. A press that begins
outside the card does not become a reveal by releasing inside. Secondary mouse
input remains ignored by the pinned detector. Long front pulls may have already
revealed before cancellation; cancellation never adds a reveal or a grade.

## Verification

`CardRevealPointerIntegrationTest` uses the real `SwipeableCard` + `ChunkCard`,
Compose1.8.2 hit testing, clickable, scroll and horizontal detector in a Linux
raster/offscreen scene. It does not replace the gesture with a mathematical stub.
Fixed expanded result:30 passed,0 failures,2 intentional parameter skips.
The four additional lifecycle instances were added after the clean baseline: key
replacement during a short drag and a deliberately non-recomposing reveal callback,
under mouse and touch. They also pass without revealing or grading a replacement
card, or issuing a duplicate callback. The test-only
`ui-test-desktop:1.8.2` dependency is not added to the application runtime.

Coverage includes stationary tap, sub-slop jitter, left/right dead-zone movement,
repeated move callbacks, separate later grading, competing-consumer cancellation,
touch scrolling, secondary mouse rejection, and inside/outside boundaries.
Pinned desktop input injection implements `cancel()` as a no-op, so cancellation
is tested by a competing Initial-pass consumer instead. Mouse button-drag does
not scroll the reading viewport in this pinned Compose version; that pre-existing
behavior is recorded rather than silently changed.

Reproduction environment: Temurin17.0.20.1+1, Gradle8.10.2, bounded one-worker
Gradle run; writable isolated Android config and Skiko cache. No Android SDK was
required. Example after setting the project's documented toolchain:

    gradle --no-daemon --max-workers=1 :desktop:test --tests dev.ikna.desktop.CardRevealPointerIntegrationTest
    gradle --no-daemon --max-workers=1 :shared:desktopTest :desktop:test

In a restricted home, set ANDROID_USER_HOME and `-Dskiko.data.path` to writable
isolated directories. Logs preserve actual task outcomes separately from this
example. Injected touch on the JVM is not an Android-device result. Animations
are disabled in this fixture; real Windows/Android interaction and animated
transition acceptance remain unverified.

Full JVM regression:69 shared tests passed;206 desktop tests passed,2 intentional
pointer parameter skips,0 failures. The final expanded run reused the unchanged
shared result (UP-TO-DATE) and reran the desktop suite. Source checks:4 small-viewport
tests,5 classic-card checks,778 keys across7 locales, and UTF-8/NFC validation pass.

## Daily completion: diagnosed, policy unchanged in Round50

Later checkpoint: [Round58](ROUND-0.12-58.md) corrects the local post-completion
Android repeat tail. Its separate concurrent-sync limitation remains explicit.
The following describes the historical Round50 finding.

The required plan counts distinct answered card keys, including AGAIN. Successful
recall is not required. Android separately appends each AGAIN and a first-contact
second pass to the current in-memory queue; this can continue after N/N, and
continual AGAIN can keep that queue alive. Desktop currently lacks that same
repetition queue. Developer Mode may also deliberately reopen scheduled material.
A clickable zero-due deck alone does not prove extra required work is admitted:
normal reopening should show completion.

Relevant owners: `LearningRepository.buildSession`/`planCompleted`,
`ReviewDao.answeredKeysSince`, `SessionViewModel.rate`, desktop `SessionPane`, and
`docs/LEARNING-ENGINE.md`. Platform, Developer Mode and same-session versus reopen
context remain unconfirmed. This stage does not resolve the policy inconsistency
or silently alter daily obligations/repetition.

## Windows minimization: still open

`tools/diagnose-window-minimize.ps1` and its Russian instructions are an optional
read-only diagnostic: selected Ikna PID, HWND styles and existing system animation
flag. No window/source behavior, OS setting or execution policy is changed. The
script has not been executed on Windows; source checks are not runtime proof.
There is no animation fix in this stage. The older white-flash report is obsolete.
