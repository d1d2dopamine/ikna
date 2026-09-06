# Local FSRS fitting: integration contract

## Runtime and user control

`LocalOptimizer` connects the existing pure estimator to a shared Android/desktop
settings panel. It runs only after an explicit request on `Dispatchers.Default`,
not on the main thread, answer path or startup. This is an app-lifetime coroutine
worker, not a new OS WorkManager task. Navigation does not cancel it; explicit
cancellation does. Process death leaves the last committed result intact.

The latest attempt, accepted candidate and explicitly applied result are separate.
A new candidate never auto-activates or replaces the active model. Full accepted
or rejected fits have a 30-day cooldown; too-few-data checks may be retried as more
history accumulates. There is no hidden automatic monthly computation.

Off restores defaults immediately, including when a disk write fails; a storage
error is shown and must be retried before restart on a read-only/full disk.
Switching does not mutate cards, due dates, history, statistics or the daily plan.
A short runtime lock makes reset and activation atomic, while answering only reads
an atomic parameter reference. Startup loads a validated applied result before
learning is exposed. Desired retention always remains current configuration policy.

## Evidence and fitting data

A versioned private DataStore record retains verdict, both held-out losses, source
and scored counts, time range, calculation time, fingerprint and accepted weights.
It is not part of the portable settings snapshot; settings restore disables local
weights. Full reset clears storage and cancels work. Generation guards prevent a
late worker or activation from reviving a reset result.

A bounded SQLite snapshot selects up to 20,000 newest valid, non-future,
non-retracted answers. Keyboard and untimed answers count: gesture timing is not
an FSRS input. Suppressed/broken chunks are excluded. Sorting/deduplication are
deterministic and levels remain separate memories. The source fingerprint is
checked again before saving; changed history requests a retry.

Original thresholds, defaults, bounds, coordinate descent, regularisation and
70/30 held-out gate remain. No new effectiveness benchmark is claimed. The pure
estimator now accepts optional recorded grading context and uses the same shared
memory-state cap as live version-1 derived HARD/EASY. Legacy/manual grades keep
the old mathematical path. Original input supplies recall labels. Numerical loops
check cancellation; training replay stops before the held-out cutoff.

## Parameter snapshots and migration

Schema 7 -> 8 adds exactly one nullable TEXT field: `reviews.fsrsParameters`.
No old review or card is rewritten. Every new answer captures one immutable
scheduler, encodes/validates its 21 weights and retention before writing the card,
and exports that versioned FSRS snapshot in JSONL. Restore validates snapshots
before inserting anything and replays each with its own model, not the currently
active fit. Undo retains its existing before-state mechanism. Legacy rows without
a snapshot use the base standard model. Matching study-day/timezone policy is
required for identical due-date rounding, as before.

Unknown versions, malformed/nonfinite/out-of-bounds weights and invalid accepted
states are rejected. Corrupt local preferences fall back to defaults; corrupt
imported parameter snapshots fail before insertion. Synthetic import protection
from the grading work is preserved. Application and dependency versions are
unchanged; database downgrade is not supported.

Schema 8 was prepared offline using the Room identity algorithm verified against
schemas 3–7. It is not claimed to be KSP output produced here. CI regenerates it
with the pinned compiler and fails on differences. Do not disable that gate.

## Validation

The existing reusable CI runs the original FSRS tests plus `LocalOptimizerTest`
on desktop and Android JVM targets. Device tests validate migration and real-Room
restore of mixed legacy/default/fitted/disabled histories, undo remapping,
statistics, duplicate imports and invalid-snapshot rejection. Controller tests
cover explicit activation, policy retention, restart, refusal, cooldown, no silent
replacement, cancellation/coalescing, reset, storage failure and history changes.
Mock accepted verdicts exercise wiring only, never empirical benefit.

```sh
python3 tools/check_text.py
python3 tools/check_grading.py
python3 tools/grading/check_full.py
python3 tools/check_optimizer.py
python3 tools/grading/generate_synthetic.py --check
gradle --no-daemon :desktop:test :app:testReleaseUnitTest :app:assembleDebug
gradle --no-daemon :app:connectedDebugAndroidTest -Pikna.abi=emulator
```

Actually executed for this hand-off: 27 Python/SQLite checks, text/localization,
fixture reproducibility, schema comparisons, YAML parsing, unchanged app-version
checks, clean-baseline patch application and archive-content/integrity verification.
Not executed here: Gradle/Kotlin/KSP, JUnit, emulator or a new effectiveness benchmark.
The sandbox could not fetch compiler dependencies; CI must be green before treating
this source archive as a tested release.

The grading lab still compares binary/derived arms under common default weights;
it is not an audit of a changing sequence of personal-fit policies. The original
prototype figures in FSRS-OPTIMIZER.md remain historical results, not new findings.
