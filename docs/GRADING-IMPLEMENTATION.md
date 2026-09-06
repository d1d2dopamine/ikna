# Derived grading: implementation and CI hand-off

## What is included

The raw observation stage and the remaining experimental implementation are
included in this source tree. No real-person effectiveness claim is made.

- On-device, undo-aware rolling calibration over at most 200 usable timed swipes.
- 50 prior samples globally and at least 20 at the current presentation level
  before any refinement. The current answer is classified before admission.
- Per-level 25th/75th percentiles. Length normalization is latency divided by the
  square root of prompt Unicode code-point count. This is a conservative,
  versioned heuristic, not a population-trained reading model.
- Absolute 60-second ceiling and a 5x personal normalized-median ceiling after
  warm-up. Missing, zero, invalid, interrupted and non-swipe timings are not
  evidence for confidence. Raw observations remain in the append-only log.
- Original input AGAIN is always AGAIN. Explicit keyboard HARD/EASY retain their
  old meaning. Optional peek or slow recall can yield HARD; ordinary recall GOOD;
  fast recall with known absence of an optional peek can yield EASY.
- Versioned bounded scheduling. Both stability and actual due delay are bounded
  against GOOD from the same before-state; HARD never lengthens and EASY never
  shortens. The maximum relative deviation is 30%. If study-day rounding cannot
  fit a bounded change, GOOD's due date is retained.
- Immutable original input and decision version in every new review. Restore and
  scheduler replay use the logged version, not today's switch or thresholds.
- Original binary outcomes still drive the governor, daily accuracy, component
  learning and undo accounting. A derived HARD is not counted as a failed input.
- Local experimental setting, **off by default**, on Android and desktop, with
  six translations. Settings restore turns the experiment off rather than
  importing another installation's opt-in.
- Three reproducible, explicitly synthetic test logs, a production-Kotlin
  comparison tool, unit/integration tests and GitHub CI gates.

FSRS equations/weights, normal manual-grade scheduling, gesture directions,
thresholds, reveal gate, animations, application identity, signing key and
release versions were not changed. There are no new network calls or uploaded
learning observations. The settings switch is the only new visible control.

## Important required-reveal limitation

The supplied native UI requires revealing the answer before grading. Its raw
`peeked=true` cannot distinguish optional consultation from required verification.
The implementation records `peekSemantics=required_reveal` rather than pretending
the signal means either certainly peeked or certainly not peeked.

In that mode, an ordinary/fast right answer stays GOOD. Clear slow timing may
produce bounded HARD after warm-up. **Native automatic EASY is intentionally
blocked** until a genuinely meaningful optional-peek signal exists. The optional
synthetic scenario exercises all four algorithm outcomes without changing the
native gesture contract. Keyboard EASY remains available on desktop as before.

Do not remove this guard just to make a synthetic grade histogram look complete.
Removing the required reveal or inferring no-peek from a tap would be an
unvalidated UI/safety change, not a missing threshold.

## Data and schema compatibility

Schema 5 -> 6 adds the four nullable raw observations:
`latencyMs`, `swipeVelocityX`, `peeked`, `timingDiscardReason`.

Schema 6 -> 7 adds six nullable context fields:
`inputRating`, `gradingVersion`, `gradingReason`, `presentationLength`,
`inputMethod`, `peekSemantics`.

Both migrations only add columns. Old rows, undo references and ratings are not
rewritten. Versions 3/4/5 exported schemas stay unchanged. Untimed/legacy/Anki
rows retain unknown metadata and do not enter calibration. Version-6 timings
lack reliable presentation length/input context and are retained for inspection,
not guessed into a new calibration cohort. Database downgrade is not supported.

`gradingVersion=1` is recorded only when GOOD is actually refined to HARD/EASY.
A null version retains ordinary scheduling exactly. Unknown or inconsistent
versions are rejected before a restore inserts anything; they are never silently
replayed with a different schedule. The JSONL identity remains `chunkId:level:ts`.

The ring is reconstructed from the latest 200 usable, non-retracted, non-future
rows in the persistent log. It needs no second mutable table, no reset on restart
and no background task. Undo/restore cannot leave a separate cache stale.

`synthetic=true` is a reserved **file-only** flag. The app's restore skips these
rows and a wholly synthetic file causes no replay or database writes. Fixtures
are not bundled in APK/desktop assets. Never remove this flag to import tests
into a real learning profile. Older versions do not know this safeguard: do not
import these fixtures into old app builds either.

### Schema provenance

Schemas 6 and 7 were prepared offline from the previous schema plus the additive
columns. Hashes follow Room's published identity algorithm, checked against all
original schema-3/4/5 hashes. They are not claimed to have been generated by KSP
in this environment. GitHub CI runs the project's pinned Room 2.7.2 compiler and
fails if its output differs; the generated schemas are uploaded for inspection.
Commit that output if CI reports a discrepancy; never disable the schema gate.

Reference algorithm:
https://android.googlesource.com/platform/frameworks/support/+/androidx-room-release/room/room-compiler/src/main/kotlin/androidx/room/vo/

## Synthetic data and comparison

Fixtures live under `tools/grading/fixtures/`:

- `synthetic-optional.jsonl`: meaningful optional-peek protocol; all four outcomes.
- `synthetic-required.jsonl`: the existing mandatory-reveal UI; no automatic EASY.
- `synthetic-noise.jsonl`: interruptions, extreme delays, keyboard answers, missing
  legacy fields and undo rows.

Generation is deterministic (seed 73021). Each fixture contains 1,200 answers and
6 appended retractions. The constant synthetic marker and mostly-null `undoOf`
are intentional; retractions are not observations. There are no duplicate ids or
answer signatures. No fixtures represent any real person's answers.

```sh
python3 tools/grading/generate_synthetic.py          # regenerate fixtures
python3 tools/grading/generate_synthetic.py --check  # verify committed bytes
```

The Kotlin evaluator runs binary and derived histories at identical observed
timestamps. Each predicts recall **before** seeing the next outcome. It scores
paired Brier error and log loss; the last chronological 25% is also reported
separately as held-out outcomes. Calibration remains causal/online and never
uses a future answer. First sightings have no scored prediction. Undo rows and
retracted answers are removed, and duplicate signatures are deduplicated.

This is a prequential observational comparison, not a simulation of what a
person would have done at different due dates. Lower synthetic error is not
required by CI, is not proof of benefit and never enables the feature. Even a
real-log improvement merely warrants human review; there is no automatic rollout.
The normalization heuristic and FSRS HARD/EASY default-parameter mismatch still
need evaluation on genuine per-person data.

Run the exact production Kotlin locally on an explicitly supplied file:

```sh
gradle :desktop:gradingLab \
  -Pgrading.input=tools/grading/fixtures/synthetic-optional.jsonl \
  -Pgrading.output=build/grading/optional.txt

# Real personal log: use on your own machine, NOT as a public CI artifact.
gradle :desktop:gradingLab \
  -Pgrading.input=/absolute/private/path/reviews.jsonl \
  -Pgrading.output=/absolute/private/path/comparison.txt
```

There is no implicit search for personal files, upload or mixed real/synthetic
analysis. Input and output paths must differ. File/row limits prevent an
accidentally unbounded batch. Empty/insufficient histories report no evidence.

## GitHub CI

Use the complete archive, including `.github/`, `tools/grading/`, `shared/`,
`desktop/`, `app/` and committed schemas. Do not upload just the changed Kotlin
files. Existing dependency versions remain pinned: JDK 17, Gradle 8.10.2,
Kotlin 2.2.20, Compose Multiplatform 1.8.2, Room 2.7.2 and Android SDK 35.

`.github/workflows/grading.yml` is reusable, manually dispatchable and runs for
pull requests. Build/release workflows call it and require it before publication
jobs proceed. It:

1. Checks repository text, deterministic fixtures, schema hashes and SQLite
   migrations using Python's standard library.
2. Fetches the same speech runtime/starter pack the project already requires.
3. Compiles both targets and runs Android JVM tests plus desktop grading tests.
4. Runs the production-Kotlin comparison on all three synthetic fixtures and
   validates finite scores, exercised branches and no synthetic rollout approval.
5. Verifies Room's generated schema matches the committed schema.
6. Runs migration and full restore/undo/statistics integration tests on an
   API-35 x86_64 emulator. The new `-Pikna.abi=emulator` is test-only; normal APKs
   remain arm64 and the legacy release remains armeabi-v7a.
7. Uploads `grading-jvm-reports` and `grading-migration-reports`. These contain
   only tests, compiler schemas and explicitly synthetic comparison results.

No GitHub credentials, new server or model download is needed for grading.
CI still needs its normal public Gradle/Android/GitHub dependency access.

### Checks actually run while preparing this archive

- 10 step-one structural/SQLite tests and 8 full-stage structural/SQLite tests.
- Reproducibility and identity checks for all three synthetic fixtures.
- Repository UTF-8/NFC/localization checks, YAML parsing and diff validation.
- Migration SQL against existing populated schema-5 and schema-6 databases,
  preserving every old review value and comparing with a fresh schema 7.
- Clean-original patch application and archive integrity/content comparison.

**Not claimed as run here:** Gradle, KSP, Kotlin/JUnit, emulator tests or the
production-Kotlin metric comparison. The sandbox lacks that toolchain and
cannot download its dependencies. Those are executable CI gates in this archive,
not fabricated green results or precomputed performance claims.

## Safe activation checklist

Wait for all CI jobs to pass. Back up a real installation before upgrading its
database. Then test swipe/reveal/cancel, keyboard/TalkBack, rotation, background,
lock/unlock, undo, same-card repeats and export/restore on a disposable profile.
The switch lives in the existing learning/load settings section and remains off
until explicitly enabled. Turning it off affects future answers only; historical
versioned decisions continue to replay as logged.

Accumulate genuine local logs over weeks before judging effectiveness. No amount
of synthetic data replaces that empirical step. The four-way algorithm is ready
for meaningful optional-peek inputs; the required-reveal safety guard remains.

## Local optimizer follow-on

Schema 8 adds immutable FSRS parameter snapshots to new answers. The native
required-reveal guard is unchanged. Fitting and scheduling share the derived
memory cap; see [FSRS-OPTIMIZER-INTEGRATION.md](FSRS-OPTIMIZER-INTEGRATION.md).
