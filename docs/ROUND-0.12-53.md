# Round53: sync profile resource preflight

Status: completed bounded resource-safety stage. Full JVM/unit suites and standard
Android/Linux build smoke passed. Physical-device acceptance remains unverified.

## Scope

Keep Round52 profile consent, identity, history and recovery semantics. Existing
limits remain 4 MiB encoded profile and 50,000 rows per table. Before constructing
entities, transaction-local bounded counts reject row overflow and conservative
raw UTF-8 text-byte sums reject provable byte overflow. Exact JSON encoding remains
the final authority: JSON keys and escaping can still cause rejection after a
bounded load. This is not streaming or pagination, and not every oversized encoded
profile is rejected before materialization.

Active-history reads use the persisted cutoff in SQL instead of loading archived
reviews then filtering. SQL MAX replaces loading history to find the last row.
The automatic journal transport also enters the bounded profile snapshot path,
closing the race where an import between completion validation and journal export
could otherwise bypass preflight. Generic manual export/restore limits are not
changed. Cancellation must propagate across Room transaction boundaries.

A typed size error maps to a localized actionable message. No history is truncated
or deleted to make a profile fit; a profile exceeding current transfer capacity
needs a later bounded-transfer implementation. Pair trust is retained after a
recoverable size error; no raw exception or content is displayed.

## Evidence and recovery

The execution workspace was lost before the first Round53 JVM run. The owner
reattached the sealed Round52 archive (SHA-256
`f0ead45f5a592b914d3241ade8b27a27fed8e433847a827aba73af43269eb0e2`).
Round53 edits were reconstructed in an isolated copy. Earlier source inspection
and reconstruction checks are not substituted for actual tests.

Executed acceptance includes real SQLite row/UTF-8/cumulative-size boundary cases, active
history and legacy undo, cancellation, existing profile/journal/protocol tests,
size-error retry over authenticated sockets, and bounded synthetic capacity
comparison against unchanged Round52. Final evidence and limits follow below.

## Remaining scope

No cloud service is required or configured. Each device retains its own data;
updates can travel only when a communication path is available. Foreground LAN
and real-device limitations from Round52 remain. A later incremental exchange
must bind durable per-peer checkpoints to event identity, content consent and
history generation, survive retries without duplicate effects, and invalidate
correctly on reset/restore/adoption. Incremental network transfer alone does not
prove bounded replay CPU or memory.

## First executed checkpoint

2026-10-17 20:58 UTC: focused JVM run passed 74 tests with zero failures, errors
or skips: resource safety 10, profile transfer 14, journal 18, consent 19, TLS 12,
error mapping 1. Shared/desktop production and test code compiled. Room schema
remains 12. These are Linux/JVM checks; full suites, build smoke and capacity
comparison are still pending. The initial configuration failure concerned an
unwritable Android tool-state directory and was resolved by an isolated writable
ANDROID_USER_HOME; no assertion or test was skipped to obtain the result.

## Full suite checkpoint

The four complete JVM/unit targets passed: 1,066 passes, two existing desktop
pointer-test skips, zero failures/errors (1,068 executions). Shared desktop 117;
desktop 274 passes + two skips; shared Android 110; app Android 565. All 435
compile-input hashes stayed unchanged. See evidence/sync-round53/TEST-RESULTS.json.
Standard build smoke remains pending at this checkpoint.

## Final build and capacity checkpoint

Standard app:assembleDebug and desktop:createDistributable succeeded. APK v2
signature verifies with the baseline certificate. The packaged Linux launcher
--selftest exited 0 under a fresh isolated home; zero decks/cards reflect omitted
corpus assets and do not prove starter-deck operation. See evidence/sync-round53/BUILD-SMOKE.json.

All six finite capacity observations passed with identical paired input/result
hashes. Fixtures cover 512 targets/2,048 events, 2,048 targets/2,048 events at
4 MiB minus 64 KiB, and 128 targets/4,096 events. The same-row profile at one byte
over the encoded cap rejects. Peak whole-process RSS was 164–286 MiB across
both revisions. Near-cap duplicate commit was slower in the candidate sample;
no speedup or statistical non-regression bound is claimed. Each case/revision has
one observation, warmed partly by fixture setup, on shared Linux infrastructure.
Full details: evidence/sync-round53/PROFILE-CAPACITY.json.
