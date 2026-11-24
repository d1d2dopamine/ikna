# Derived grading: implementation and CI hand-off

## Shipping policy

The native card contract still requires reveal before the final answer. No
optional reveal path, confidence button or extra grade choice was added.

Current timing cohort: `required_reveal_verified_v2`.

- Timing runs from card layout to the start of the final answer action.
- A front-card reveal pull is not mistaken for an answer.
- The revealed answer must remain visible for at least 250 ms.
- Reveal and answer modality must match.
- Swipe and keyboard windows are separate; keyboard animation has no fake pointer
  velocity.
- Only verified known (`inputRating=GOOD`) current-cohort answers calibrate speed.
- At most 200 recent undo-aware rows are loaded for one modality.
- Refinement needs 50 modality samples and 20 at the current level.
- Length normalization is `latency / sqrt(promptCodePoints)`.
- Below the personal 10th percentile is a candidate for EASY; above the 75th is
  HARD. Equality and flat windows stay GOOD.
- EASY additionally requires at least three prior successful reviews of that
  card (`reps - lapses >= 3`).
- Missing, interrupted, mixed, unverified, non-finite or out-of-range evidence
  falls back to the binary input.

`gradingVersion=1` continues to identify the unchanged bounded scheduling
transform, not the timing cohort. New classifier semantics are isolated by the
`peekSemantics` protocol string. Historical rows remain replayable and are not
silently admitted to the new calibration window.

## Scheduling and outcome compatibility

A derived HARD/EASY is calculated from an original GOOD input. The stored
`inputRating` remains GOOD, so governor accuracy, daily statistics and component
learning treat it as a successful answer. Replay uses the recorded grade, FSRS
parameter snapshot and version; it never recomputes a past decision.

The version-1 transform calculates GOOD and the derived grade from the same
before-state. Stability and actual due delay stay within 70–130% of GOOD. HARD
cannot lengthen and EASY cannot shorten. Study-day rounding falls back to GOOD's
due date when no bounded alternative exists.

No schema change is required. Schema 6 already stores raw timing observations;
schema 7 stores original input, grading reason/version, presentation length,
input method and protocol. Schema 8 stores immutable FSRS parameters, and schema
9 adds Browse exposure state. Existing additive migrations remain unchanged.

## Deterministic synthetic scenarios

Fixtures under `tools/grading/fixtures/` are explicitly synthetic and rejected by
app restore:

- `synthetic-verified.jsonl`: repeated current-protocol cards; exercises bounded
  HARD and mature EASY;
- `synthetic-immature.jsonl`: at most three sightings per card; verifies that fast
  immature answers cannot become EASY;
- `synthetic-noise.jsonl`: interruptions, invalid/missing rows, keyboard noise and
  undo rows.

All use seed 73021. They validate causal window mechanics and report formatting,
not real-person benefit.

```sh
python3 tools/grading/generate_synthetic.py
python3 tools/grading/generate_synthetic.py --check
```

Run the production Kotlin evaluator explicitly:

```sh
gradle :desktop:gradingLab \
  -Pgrading.input=tools/grading/fixtures/synthetic-verified.jsonl \
  -Pgrading.output=build/grading/verified.txt
```

A personal export should only be evaluated locally and never uploaded as a CI
artifact.

## Release checks

The reusable `.github/workflows/grading.yml` performs:

1. UTF-8/NFC, localization, deterministic fixture and SQLite/schema checks.
2. Desktop and Android JVM tests plus debug APK assembly.
3. Production-Kotlin replay of verified, immature and noise fixtures.
4. Room generated-schema comparison.
5. Real migration/restore/undo tests on an API-35 x86_64 emulator.

Pinned release toolchain remains JDK 17, Gradle 8.10.2, Kotlin 2.2.20, Compose
Multiplatform 1.8.2, Room 2.7.2 and Android SDK 35.

Useful local static checks:

```sh
python3 tools/check_text.py
python3 tools/check_localization.py
python3 tools/check_grading.py
python3 tools/grading/check_full.py
python3 tools/check_optimizer.py
python3 tools/check_design_parity.py
python3 tools/check_palettes.py
python3 tools/grading/generate_synthetic.py --check
```

Final compile gate:

```sh
bash tools/ci/run-gradle.sh ci-logs/jvm-build.log --continue \
  :desktop:test :app:testReleaseUnitTest :app:assembleDebug
```

The local sandbox used to prepare the archive has no Gradle executable, so static
and real-SQLite checks can be run here but the packaged GitHub workflow remains
the authoritative Kotlin/Android compilation gate.

## Manual smoke test

On a disposable profile, check:

- tap reveal, front pull reveal and cancelled pull;
- mouse/touch answer after at least 250 ms;
- Space reveal and one-tap A/D answer;
- mixed keyboard/mouse interaction falling back safely;
- focus loss, background, lock/unlock and timeout;
- same-card repeats, undo, export and restore;
- first 50 answers remaining GOOD while each modality warms independently;
- fast immature cards staying GOOD;
- mature fast, ordinary and slow responses producing bounded EASY/GOOD/HARD.

No timing, answer or grading data leaves the device.
