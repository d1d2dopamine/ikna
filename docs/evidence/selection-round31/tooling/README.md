# Exact dense-pair prefix probe

Standalone tooling only. **No real measurement has been run.** The repository
and active full-control staging must remain untouched. Do not run this until the
full control and its independent comparison have both succeeded.

The probe measures the original `selection_experiment.pair_evidence` on the first
32,768 Everyday en -> de candidates in its existing `ORDER BY c.id` traversal.
It consumes full-pool `context_analysis` choices/ranks/levels as stored. No
validation workflow, restaging, rank construction, analysis-cache integration,
model, source acquisition, final allocation or application work is performed.

## Entry contract

`--control-dir` identifies round30. These exact finalized paths are required:

- `CONTROL-COMPARISON.json`
- `guard/RESOURCE.json`
- `full-control/PROFILE.json`
- `full-control/run-01/selection.json`
- `full-control/run-01/selection.sqlite3`

The comparison must have status `complete-independent-control-comparison` and
true `historicalLogicalIdentityMatches`, `reportedIntegrityMatches` and
`fullSelectedGzipIntegrityRead`. It must bind:

- `profileFileSha256`: final PROFILE.json bytes
- `selectionReportFileSha256`: final selection.json bytes
- `stagingSha256`: the complete finalized SQLite file bytes
- `stagingSizeBytes`: its positive integer byte size

The independent selected logical hash must match the historical control
`7149ca35ebc44b0833434505a94d187e11194a2eca7d4f45b885b061fb267cd4`.
The guard must show `child-exited-zero` and exit code 0. The profile must be a
completed full-saved-input, stages-mode, one-run, boundary-diversity-v2 control
with cap 8000. Report, profile, staging size, canonical report hash and admitted
input pins must agree. The complete unique input count is 11,099,490. Morphology
must be disabled. These gates reject an active or failed control before opening
SQLite. There is no bypass/fixture/force option in the CLI.

Producer catalogue source paths and hashes, registry, quarantine, Python,
platform, SQLite, zlib, explicit ICU pin and all actually mapped ICU library bytes
are verified. The inherited Japanese analyzer/dictionary versions and dictionary
hash are checked without instantiating an analyzer or recomputing choices. Source,
runtime and producer evidence are checked again afterward. The helper SHA is
recorded separately; it is not added to or substituted for producer source pins.
The comparison and guard JSON byte hashes are also retained as completion-gate
bindings. Metadata/baseline snapshots precede parsing and are rechecked afterward
and at completion, so recorded hashes identify the evidence actually validated.

Full staging bytes are hashed before **and** after each invocation, outside the
pair timing. File identity/size must stay fixed, and WAL/SHM/journal sidecars are
refused. SQLite opens with `mode=ro`, `query_only=ON`, zero lock timeout and a read
transaction. The bounded independent ID query must match every joined prefix
row, preventing a missing analysis row from silently shifting the sample.
Actual SQLite connection PRAGMAs are recorded and must match across baseline and
profiled invocations. The producer's large staging-connection cache/mmap PRAGMAs
are not silently copied: its pair-evidence path opens a separate fresh connection.

## Runnable commands after approval of completed evidence

Use the same process-local ICU exports as the full control. In particular,
`IKNA_ICU_LIBRARY` must be the absolute pinned library path,
`IKNA_ICU_VERSION` must be `74.2`, and `LD_LIBRARY_PATH` must select its companions.
The CLI verifies those loaded bytes against the completed producer profile.

```bash
ROOT=/workspace/scratch/8105054a3211
PY="$ROOT/ikna-venv/bin/python"
TOOL="$ROOT/ikna-results/round31-probe-tooling/dense_pair_probe.py"

# Invocation 1: new fresh Python process; output directory must not exist.
"$PY" -B "$TOOL" \
  --repo "$ROOT/ikna-work" \
  --control-dir "$ROOT/ikna-results/round30" \
  --output-dir "$ROOT/ikna-results/round31-probe-tooling/baseline-01" \
  --mode baseline

# Invocation 2: only after baseline exit 0 and completed PROBE.json.
"$PY" -B "$TOOL" \
  --repo "$ROOT/ikna-work" \
  --control-dir "$ROOT/ikna-results/round30" \
  --output-dir "$ROOT/ikna-results/round31-probe-tooling/cprofile-01" \
  --mode cprofile \
  --reference-baseline "$ROOT/ikna-results/round31-probe-tooling/baseline-01/PROBE.json"
```

Do not run them concurrently with each other or the producer. Each invocation
hard-limits virtual address space to at most 2 GiB and total process CPU to at
most 300 seconds (soft CPU interrupt at 299 seconds). An inherited lower ceiling
is retained. Limits cover hashing/imports/output too, not just the measured call.
A preflight requires at least 2 GiB host available memory and 4 GiB free disk.
Temporary paths are a new owned `tmp` inside the output directory, on the same
filesystem. Existing output directories or evidence files are never overwritten.

The CPU ceiling is **not a wall-time ceiling**, and the preflight snapshot is
**not continuous host/cgroup/RSS monitoring**. Hard address-space bounds are not
an exclusive RSS allocation. Hidden cgroup limits may remain unknown. No change
to the full-control guard is made. A hard kill may leave only STARTED.json and
partial artifacts; absence of valid completed PROBE.json means incomplete.

## Outputs and interpretation

- `STARTED.json`: durable incomplete scope/lineage evidence
- `EVIDENCE.json`: streamed canonical complete returned target list and counters;
  preserves all list order, contexts, identities, ranks, original text and origins
- `PROBE.json`: written only after all final integrity/equality checks; contains
  full input/source/helper/runtime bindings, exact seven-value prefix-row and ID
  hashes, pair wall/CPU clocks, lifetime peak RSS, cache info and counts
- `PAIR.pstats`, `PROFILE-TOP.txt`: profiled invocation only; top self/cumulative
  costs and relevant callers/callees; hashes are recorded in PROBE.json
- `FAILED.json`: best-effort failure information; never a successful result

The measured adapter checks the original SQL/parameters and adds only
parameterized `LIMIT 32768`; it returns the ordinary SQLite cursor. Row hashing
runs before and after, outside timing. A completed profiled result additionally
requires byte/hash identity of the entire returned evidence with the separately
verified baseline, plus identical producer/helper/settings/runtime/prefix inputs.
All output JSON is streamed through a canonical encoder; no extra full serialized
copy of the target graph is retained. Pstats creation and serialization are also
outside the measured pair call.

Read `choose_contexts` call count first: those calls are evidence pruning here,
not allocation. If pruning is absent, this prefix did not exercise the suspected
mechanism. A larger probe is a separate decision, not an automatic fallback.
Examine overlap/Counter work, written-token cache misses, duplicate normalization,
quality-key hashing, JSON/origin checks and enclosing self time. Source inspection
suggests questions, not measured bottlenecks or guaranteed optimizations.

Cumulative cProfile times overlap; never sum them. Instrumentation changes
timings. The baseline/profile ratio describes profiler overhead, not an engine
speedup. Fresh caches and an early 32,768-row prefix do not reproduce the producer's
inherited cache or late-pair density. Mandatory full-file hashes and prefix checks
warm filesystem caches. Whole-process high-water RSS is not pair-exclusive memory.
SQLite fetch costs may appear in enclosing self time. None of this certifies
semantic quality, complete-pair output, all 110 directions or a production cache.

Before any future optimization, retain all provenance/identity/quarantine/rank/
boundary/order/diversity gates, require appropriate fixtures and exact complete
output equality. Full integration additionally needs full-control output and
end-to-end resource evidence. Do not rerun the full workflow merely to test this
diagnostic's design. Hashes provide local integrity, not authentication of an
untrusted producer or staging file.

## Tiny tests

```bash
cd /workspace/scratch/8105054a3211/ikna-results/round31-probe-tooling
/workspace/scratch/8105054a3211/ikna-venv/bin/python -B test_dense_pair_probe.py
```

Tests use only generated 2-42-row SQLite fixtures under this tooling directory.
They exercise the original function's 37-context pruning boundary, exact return
identity, pstats loadability, read-only access, missing analysis, mutated features,
producer/source/output gates and resource-limit installation. The in-process
baseline/profile integration mocks producer runtime/source verification and clears
the tiny fixture cache between calls. It is harness evidence, not fresh-process
real-input/runtime or dense-corpus performance evidence.
