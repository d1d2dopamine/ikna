# Local FSRS fitting: integration contract

## Runtime and automatic policy

`LocalOptimizer` connects the pure estimator to both Android and desktop. Fitting
never runs on the answer path: one application-lifetime monitor coalesces history
changes, waits for a 20-second quiet gap, and periodically rechecks eligibility.
CPU work runs on `Dispatchers.Default`; answer-time parameter reads are atomic.

The policy adds no learner-facing model controls. Derived grading is enabled when
its own warm-up/evidence gates are ready. FSRS fitting follows the stored verdict:
`TOO_FEW_ANSWERS` may be rechecked after 24 hours; a completed accepted or rejected
fit keeps the 30-day cooldown. A one-hour runtime backoff prevents repeated work
when the monitor wakes frequently.

A candidate never activates merely because it exists. Before activation its source
fingerprint must still match current history. A newly completed fit is applied only
when accepted, current and valid; rejected, stale, cancelled or corrupt results stay
out. Startup loads only a validated previously applied result. Reset immediately
restores defaults, increments the generation guard and cancels in-flight work.

Activation never mutates cards, due dates, history, statistics or the daily plan.
Desired retention remains product policy rather than an estimated parameter.

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
