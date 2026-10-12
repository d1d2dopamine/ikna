# Same-helper overlap-reuse experiment harness

Implementation/fixture checkpoint only. **No real invocation has been started by
this harness task.** Await independent review before measurements. This new helper
is `dense_pair_candidate_probe.py`; the frozen Round31 helper and all its original
files remain byte-for-byte unchanged.

The candidate worker owns `overlap_reuse.py`, `test_overlap_reuse.py`, README.md,
TESTS.log and TEST-EVIDENCE.json. This separate guide owns only the harness flow.
The candidate README's proposed Round31 comparison is historical planning. The
actual experiment creates a **new unchanged-function baseline using this same
helper**, with both helper and candidate hashes pinned in every mode. Do not relax
the helper-identity check to reuse a Round31 baseline manifest. The first new
baseline's complete evidence can independently be checked against the frozen
Round31 SHA-256 `f551785d003b9a8f0793bedd687829f0a3364ebf47cab80f83d2a1f052ee12bd`.

## Preserved gates and exact scope

The producer completion/guard/historical-comparison gates, all source/registry/
quarantine/runtime/ICU/dictionary checks, full immutable staging hashes before
and after, metadata snapshots, read-only SQLite transaction, independent ordered
candidate IDs and seven-value row hash, resource ceilings and exclusive output
creation are carried forward from the reviewed Round31 helper.

The only input is the same first 32,768 Everyday en -> de candidates in original
`ORDER BY c.id` order, with stored full-pool choices/ranks. The original
`selection_experiment.pair_evidence` remains the entry point, and the only SQL
change is parameterized LIMIT. No validation workflow, restaging, sample-derived
ranks, new analyzer features, policy admission, full workflow, source edit or
application change is invoked.

Every mode reads, hashes and parses the candidate source. Baseline never imports
or executes it. Candidate modes load the exact verified bytes, avoiding stale pyc
fallback; verify module path, declared chooser/function code path and signature;
and require the three imported helpers to be the original policy functions.
Import must not replace policy dependencies or prime the written-token or
quarantine-rule caches. Candidate global module reuse is refused: CLI invocations
must be fresh processes.

During the measured call only, candidate modes replace
`selection_experiment.choose_contexts`. A finally block restores the original
alias on success, exceptions, KeyboardInterrupt and SystemExit, then verifies
original policy dependency identities. The actual `selection_policy` functions,
cache definitions and source files are never patched or cleared by the harness.
Pure cache hit/recency behavior naturally differs when fewer overlap calls occur.
The candidate's maxima are call-local, not a persistent cache.

The measured window includes the small invocation/restoration wrapper as well as
pair evidence. Candidate imports, source/input checks, reference checks and
serialization remain outside it. Baseline uses that same wrapper with no alias
replacement. Fresh written-token and quarantine caches are asserted before each
measured call; the normal pinned segmentation setup remains outside the window.

## Modes and reference contracts

- `baseline`: original chooser; forbids references and does not import candidate.
- `candidate`: requires a completed unchanged baseline from this helper and
  candidate hash. All returned evidence must match exactly before completion.
  Reports `observedCandidateOverBaselineElapsedRatio` for those two invocations.
- `candidate-cprofile`: requires both that unchanged baseline and a completed
  unprofiled candidate that binds this same baseline. Complete returned evidence
  must match both. Reports `profileOverCandidateElapsedRatio` against the
  unprofiled candidate, explicitly instrumentation overhead rather than gain.

References must agree on source/producer bindings, helper SHA, candidate path/SHA,
settings, runtime, SQLite configuration and exact row stream. Their manifests and
complete payloads are snapshotted before validation and checked for stability
again. Candidate source bytes are also rechecked at completion. A mismatch or
error leaves no successful PROBE.json and restores the selector alias.

## Commands after review

Use sequential fresh invocations and new output directories. Do not run these
while other resource-intensive work is active. The exact process-local runtime is:

```bash
ROOT=/workspace/scratch/8105054a3211
DIR="$ROOT/ikna-results/round32-overlap-prototype"
PY="$ROOT/ikna-venv/bin/python"
ICU="$ROOT/ikna-toolchain/icu-74.2/runtime/icu/usr/local/lib"
export IKNA_ICU_LIBRARY="$ICU/libicui18n.so.74.2"
export IKNA_ICU_VERSION=74.2
export LD_LIBRARY_PATH="$ICU${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"

"$PY" -B "$DIR/dense_pair_candidate_probe.py" \
  --repo "$ROOT/ikna-work" --control-dir "$ROOT/ikna-results/round30" \
  --candidate-module "$DIR/overlap_reuse.py" \
  --output-dir "$DIR/baseline-01" --mode baseline

# Independently check baseline-01's complete EVIDENCE identity against Round31.
# Then run the candidate using the completed same-helper baseline.
"$PY" -B "$DIR/dense_pair_candidate_probe.py" \
  --repo "$ROOT/ikna-work" --control-dir "$ROOT/ikna-results/round30" \
  --candidate-module "$DIR/overlap_reuse.py" \
  --output-dir "$DIR/candidate-01" --mode candidate \
  --reference-baseline "$DIR/baseline-01/PROBE.json"

# Profiling is a separate observation and additionally needs its unprofiled peer.
"$PY" -B "$DIR/dense_pair_candidate_probe.py" \
  --repo "$ROOT/ikna-work" --control-dir "$ROOT/ikna-results/round30" \
  --candidate-module "$DIR/overlap_reuse.py" \
  --output-dir "$DIR/candidate-cprofile-01" --mode candidate-cprofile \
  --reference-baseline "$DIR/baseline-01/PROBE.json" \
  --reference-candidate "$DIR/candidate-01/PROBE.json"
```

The authorized measurement design uses three serial baseline/candidate pairs,
alternating their order after the first valid baseline exists, then one profiled
candidate. Suggested order: B1, C1, C2, B2, B3, C3, C-profile. C2 may use B1 as its
already completed identity reference; once B2 exists, the external timing summary
must explicitly pair C2 with B2 rather than silently treating its in-manifest
C2/B1 ratio as C2/B2. For C-profile, use the matching completed B3 and C3 references.
Every baseline and candidate must independently match complete returned evidence.
All seven invocations use identical helper/candidate pins and fresh processes;
do not clear OS caches. No batch runner is implemented or started here.

## Resources, outputs and interpretation

Hard ceilings remain 2 GiB virtual address space and 300 process-CPU seconds
(soft CPU interruption at 299), including preflight and serialization. Preflight
requires 2 GiB host available memory and 4 GiB free disk; the owned temporary
directory is inside each new output. These are not continuous headroom monitoring
or a wall-time ceiling. Hidden cgroup limits may be unknown. Incomplete/failed
evidence is never reusable.

Outputs remain STARTED.json, complete streamed EVIDENCE.json and successful
PROBE.json, with FAILED.json on best-effort error cleanup. Only candidate-cprofile
adds PAIR.pstats and PROFILE-TOP.txt. The canonical return includes every target,
context, original text, ID, origin and rejection counter in original list order.
Success status is `complete-dense-pair-overlap-experiment`; old Round31 status is
not accepted as a reference.

Unprofiled ratios are observations for this prefix and host. Aggregate repeated
observations transparently; do not infer full-workflow gain, all-language benefit,
production readiness or semantic quality. cProfile cumulative times overlap and
must not be added. Profiler overhead uses the same candidate implementation on
both sides. Mandatory hashes warm OS caches; fresh process state differs from
full-run inherited state. Reduced pure LRU lookups alter recency and can affect
later timing, so a prefix result cannot establish end-to-end cache behavior.

## Tiny harness tests

```bash
cd /workspace/scratch/8105054a3211/ikna-results/round32-overlap-prototype
/workspace/scratch/8105054a3211/ikna-venv/bin/python -B test_dense_pair_candidate_probe.py
```

31 tiny tests pass in HARNESS-TESTS.log. They retain prior producer/row/read-only/
output/resource checks and add three-mode equality, baseline non-execution,
candidate hash/shape/dependency/import-cache gates, reference-mode/hash gates,
alias restoration on normal/exceptional exits, and failed/mismatching candidate
refusal. Fixture integrations mock producer source/runtime checks and reset only
their tiny in-process fixture caches between invocations. They are not real-input
performance or fresh-process runtime evidence. Candidate algorithm tests and
their analysis remain in the separately owned prototype files.
