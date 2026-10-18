# Round59 — safe open-session invalidation after synchronization

Status: completed bounded stage; focused/full suites and build smoke passed.
Baseline: sealed Round58 SHA256
`e9e09b9050ca217fb605855671ef9c50341f6d2febd3d68376c3d78e8bd5af4c`.

## Confirmed gap and bounded design

Changed journal merges clear the daily plan transactionally. In the Round58 baseline, Android and Desktop
have no session-refresh observer behind their empty onMerged callbacks. An open
session can therefore retain an obsolete queue. Blindly calling buildSession
from a notification would create a plan and can admit/re-pick work, losing the
original explicit-extra metadata. This stage must not do that.

Keep existing plan clearing. Publish only a nonblocking postcommit change signal;
no repository calls while notifying. On both platforms, immediately invalidate stale presentation
and disable new input, then serialize a read-only existing-plan snapshot. Refresh
only a safe existing plan; a missing/replaced plan gives an explicit Continue
state, without automatically creating or admitting work. Further optional hard
practice never resumes automatically.

An already accepted answer can legitimately commit before the notification is
observed. Preserve it: repository writes reread current state under their guard.
The signal is presentation invalidation, not event authorization. Never cancel an
in-flight answer merely to refresh its screen, and prevent obsolete async result
or error callbacks from restoring an older presentation.

Recheck on foreground/reentry because a postcommit callback can be missed through
cancellation/process failure. A read-only observation stamp avoids discarding an
unchanged optional batch on every resume. This is not arbitrary database tamper
detection or a new synchronization protocol.

## Acceptance and stopping point

Real Room snapshot tests: changed merge/missing plan, rebuilt plan, durable normal
versus optional progress, Undo, no-op, and no writes/admission during inspection.
Actual testable generation coordination: barriers around in-flight work, newer
signals, cancellation/disposal and obsolete publication. Controller notification
must follow successful commit and must not fire for duplicate/rolled-back pages.
Platform source wiring and compilation are distinct from physical ViewModel,
lifecycle, touch, TalkBack and background/network acceptance.

Stop after this finite safety stage is verified. Do not redesign durable daily
plan witnesses, remove atomic plan invalidation, add external infrastructure or
claim seamless continuation where an explicit reload is required.

## Captured baseline

Unchanged Round58 production: a real Room merge imported one answer, cleared the
plan and changed durable answer count to1. The captured open-session queue still
held three cards including the remotely answered target and factual count0.
The repository witness passed; two Android source-routing contracts failed as
expected. Desktop's empty callback and deck/reload-only session loading were
independently preserved as source evidence. This is not runtime ViewModel/UI
execution. Logs, XML and unchanged-source hashes are retained.

## Focused verification

Run1 passed 146 cases without failures, errors or skips: 33 shared, 98 desktop,
and 15 Android local-JVM cases. All 473 captured compile inputs remained identical.
Coverage combines real Room/TLS integration, pure coordination/policy tests and
platform source-wiring contracts; it does not execute a physical UI lifecycle.
Five static checks passed. Full suites passed 1,239 cases with two existing skips and zero failures/errors (1,241 total). Android APK build/signature and packaged Linux selftest passed. Physical Android/Windows lifecycle acceptance remains open.
