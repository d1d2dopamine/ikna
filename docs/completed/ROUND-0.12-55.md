# Round55 — independent-process sync interruption

Status: completed local process-crash verification stage, 2026-10-17.
Baseline: sealed Round54. Production behavior is not changed merely to make a
fault-injection test pass.

## Fixed scope

Two independent JVM children each own a separate Room database, settings/pairing
state and real DeviceSyncController with pinned TLS on loopback. Bounded local
control messages are test instrumentation; invitations and credentials must not
appear in retained logs or deliverables.

Two deterministic fault points use existing integration seams:

1. Park the receiver inside replay's CardDao write before the page transaction
   commits, then forcibly terminate that child.
2. Park the receiver's onMerged callback after page commit but before the protocol
   can return its acknowledgement, then forcibly terminate that child.

Terminate the sender as well. Restart both paused, inspect durable rows and
checkpoints, then resume. Fixed fixture: two bootstrap legacy events (answer and
Undo), 257 padded A answers, one explicit cross-page Undo and 17 B answers: 277
unique final events. Padding is a technical synthetic fixture, not study content.

## Acceptance

- Precommit interruption leaves receiver history and checkpoint unchanged.
- Postcommit interruption preserves imported history and matching checkpoint
  despite the missing acknowledgement.
- Fresh processes retain approved operation, stream identity and history boundary.
- Within a fixed 60-second convergence budget, both copies contain the exact
  expected canonical IDs, payloads, Undo edges and derived learning state.
- Both incoming checkpoints equal the other source's final cursor/prefix.
- Subsequent caught-up exchange changes neither history nor derived state and
  invokes no replay callback.
- Bound phase waits, child heaps, control output and cleanup. Always terminate
  and wait for fixture processes on failure; retain failures and source hashes.

Stop when these two fault cases and no-op checks pass. A failure must be diagnosed,
not hidden by widening the deadline or weakening identity/checkpoint checks.

This is process-crash evidence on Linux, not filesystem power-loss durability,
physical Android/Windows acceptance, internet reachability or mobile background
support. No external service or account is configured.

## Fixed execution inputs and retained failures

The scenario captures the parent JVM timezone once and passes that same explicit
zone to both child generations and the reference. Exact dueAt equality is tested
under equal device-local scheduling inputs; production timezone semantics are
unchanged. Each child uses -Xmx256m and ActiveProcessorCount=1 (a JVM concurrency
hint, not OS CPU affinity). Deadlines: 180 seconds per test; 30 seconds for initial
preview/consent, 15 seconds for proposal/RPC/no-op, 45 seconds for the fault barrier,
60 seconds for convergence and 10 seconds per child reap.

Run1 stopped at a fixture type-inference compile error; no tests or children ran.
Run2 reached forced termination, restart and final union, then exact dueAt oracle
comparison failed because children used UTC while the parent used its host zone.
The fix aligns fixture inputs without changing production or weakening the
assertion. Both runs and source hashes are retained outside the source package.

Focused run3: two passes, zero failures/errors/skips, 22.69 seconds total.
Precommit 11.839 seconds; postcommit 10.851 seconds. Actual shared zone
Europe/Tallinn. All 454 captured source/build inputs remained unchanged;
test-owned distinct-process and child cleanup assertions passed. The external
observer captured no child samples, so it provides no independent RSS/CPU or
process-count evidence.

Final desktop regression: 299 cases, 297 passes, two unchanged pointer-test skips,
zero failures/errors. The crash cases passed again in 12.167 and 11.649 seconds.
All 454 inputs remained unchanged. Production/schema bytes match sealed Round54;
only two test fixtures and the desktop test-classpath hook differ among compile
inputs. Android/shared suites and standard builds were not rerun in this test-only
stage; their unchanged-production baseline is Round54, not a new Round55 pass.
Evidence and hashes: docs/evidence/sync-round55/.
