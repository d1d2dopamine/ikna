# Round 25 — isolated selection stages, sampled RSS and pinned ICU 74.2

2026-10-12, Asia/Yekaterinburg. This continues the saved-input performance work;
application appearance, learning policy, source admissions and public assets are
unchanged. The application lane proceeds separately through existing build/DEV
acceptance work. No MVS source, dependencies, data or results are used here.

## Implemented boundaries

`StageRecorder` is an offline diagnostic observer. Optional observer arguments in
`everyday_selection.build_report` and `selection_experiment.build_report` wrap the
existing operations without feeding measurements into their decisions:

- complete input/provenance verification;
- segmentation/dictionary setup and candidate staging;
- ranks and context analysis per learning language;
- evidence per directed pair;
- the actual target-ordering/context-allocation loop per level;
- selected-row output/finalization and post-selection input verification;
- diagnostic JSON/preview writing, measured separately from selection elapsed.

These integration windows are disjoint. The allocation window excludes pruning
inside pair evidence and excludes output. Timers also record process CPU time;
loop bookkeeping outside the windows remains part of total selection elapsed.
The default selector still uses no-op contexts and keeps every existing choice.

RSS is observed from Linux `/proc/self/statm` at stage boundaries and on a daemon
sampler requested every 10 ms. It describes the whole process while a stage is
active, **not memory allocated exclusively by that stage**. The sampled maximum
is a lower bound: scheduling and Python's GIL can miss a shorter peak. The separate
OS lifetime high-water mark is retained too. Missing RSS is null/unavailable,
never a fabricated zero. Nested-recorder semantics are explicit, and exceptions
unwind/join the sampler. Records and metadata are bounded.

Run the stage mode with `--measurement-mode stages`; cProfile remains available
as a separate mode. It is invalid to call the lower overhead stage mode a speedup
over the earlier cProfile timings.

## ICU version recovered without changing the system

The official Unicode ICU 74.2 Ubuntu22.04 x86_64 archive was downloaded and its
published SHA-512 verified, then extracted into an isolated toolchain directory.
[ICU-74.2.json](evidence/selection-round25/ICU-74.2.json) retains exact URL, bytes,
checksum source and verification scope. Its detached signature was not verified;
no system install or `ldconfig` operation was performed.

New process-local environment pins select a library explicitly and require its
reported version. Invalid paths/versions fail closed instead of silently falling
back to system ICU. Changing the pin after engine initialization is also refused,
including on cached CJK spans. A review caught that cached-span corner case in the
initial patch; it was fixed and regression-tested before the retained final runs.
The profiler records hashes of actually mapped ICU main/companion/data libraries.

```bash
export LD_LIBRARY_PATH=/path/to/icu-74.2/runtime/icu/usr/local/lib
export IKNA_ICU_LIBRARY="$LD_LIBRARY_PATH/libicui18n.so.74.2"
export IKNA_ICU_VERSION=74.2
python3 tools/catalog/selection_profile.py \
  --pool docs/evidence/selection-round25/diagnostic-pool-1024.jsonl.gz \
  --pool-report docs/evidence/selection-round25/EVERYDAY-POOL-1024.json \
  --output-dir /path/to/new-stage-profile --input-scope bounded-saved-input \
  --measurement-mode stages --runs 2
```

ICU 74.2 and the locked Sudachi versions/dictionary match their historical
version fields. Correction recorded during round 29 preflight: the local runtime
is Python 3.12.14 / zlib 1.3.2, whereas the retained Part 10 report records Python
3.12.15 / zlib 1.3. The earlier wording incorrectly implied a matching Python
version. The original historical ICU binary hashes were not supplied either;
exact historical runtime reproduction is not asserted. Logical selected-output
identity is therefore checked separately from compressed-byte identity. Current pinned runtime identities are
retained for future exact comparisons. No runtime library enters the app.

## Real bounded measurements

The existing 128-per-pair diagnostic has 14,012 candidates. A separately pinned
1,024-per-pair diagnostic, derived from the same complete saved pool with the same
bottom-hash/original-order rule, has **105,988 candidates**, **100,499 distinct
contexts**, and all **110 directed pairs**. It remains a pair-stratified, sample-
ranked diagnostic, not a production census or defect-labelled evaluation set.
Its compact input and original builder report are retained for direct reuse.

Each input runs twice in each of two fresh Python processes: eight measured
selections in total. Within each input, all four canonical reports/previews and
complete selected gzip/logical hashes agree. Fresh process resets interpreter and
in-process caches; filesystem/OS caches are deliberately left untouched. No cold-
disk claim, privileged cache flushing or independent full-pool timing forecast.

| Input | First run in fresh processes | Repeated run in the same processes |
| --- | --- | --- |
| 14,012 candidates | 2.458 / 2.361 s | 2.320 / 2.260 s |
| 105,988 candidates | 20.443 / 20.582 s | 21.464 / 21.537 s |

The larger repeated runs are slightly slower: warm process state alone does not
establish a useful cache or a guaranteed speedup. A bounded one-worker application
build/toolchain task could also affect wall timings. CPU clocks and the host
snapshot are retained; the earlier eight-worker MVS benchmark had finished.

For the first larger run, explicit windows measured:

| Stage | Elapsed seconds |
| --- | ---: |
| verification | 1.396 |
| segmentation/dictionary setup | 0.160 |
| staging | 2.374 |
| ranks | 1.459 |
| context analysis | 4.947 |
| pair evidence | 2.665 |
| allocation | 3.945 |
| selected-row output | 3.216 |
| selected-output finalization | 0.015 |
| post-selection verification | 0.007 |
| diagnostic JSON/preview output, outside selection total | 0.106 |

Staging occupies 120,438,784 bytes; complete selected gzip is 19,359,539 bytes.
Each profile includes sampled stage RSS and the independent process high-water
mark. These measured bounded sizes and memory values must not be multiplied into
a promised full-corpus resource bound.

## Original implementation equivalence

The untouched round-22 catalogue tools were extracted from the owner's supplied
source archive into a separate read-only baseline directory. Under the same pinned
ICU 74.2, their unchanged selector processed the larger 105,988-row diagnostic.
Its full selected gzip and preview exactly match the observer-enabled code.
Reports match after removing only the expected `selectionPipelineSha256` field;
all decisions, quality counters, identities, ordered contexts and source origins
remain compared. [ORIGINAL-EQUIVALENCE-LARGE.json](evidence/selection-round25/ORIGINAL-EQUIVALENCE-LARGE.json)
retains both pipeline fingerprints and the complete comparison scope. This is
actual real-input equivalence, not merely two runs of the modified code.

## Decision and next step

Context analysis is the largest isolated window on both diagnostic sizes: roughly
one quarter of selection time on the larger input. Allocation and serialization
also materially contribute. Therefore the next bounded experiment is **reusable
context analysis only**, with unchanged validation, fresh staging/ranks, evidence,
allocation and output. It is not yet a production cache or a promised speedup.
A successful prototype must preserve rejection counters as well as cached choices:
preloading the current analysis table then calling its count shortcut would return
an empty counter and silently change report evidence.

The experiment must bind original context text/order, ranks, function settings,
registry/input scope, schema/code, ICU/data and Japanese policy/dictionary pins;
reject incomplete/corrupt/foreign entries; prove cold/miss/hit complete-output
identity; and measure end-to-end import/write/validation cost and disk/RSS. Selected
choices, allocation and provenance decisions are not cache candidates in this step.

The bounded profiling checkpoint now has explicit stage windows, sampled memory,
fresh-process versus reused-process evidence, and original-selector equivalence
under the recovered ICU version. OS-cold disk cost and full-corpus selector timing
are still unmeasured. They are not silently replaced by these small-run results.
A full rerun is not launched merely to recover already known selection counts.
Classifier evaluation still lacks independent multilingual labels and a pinned,
rights-reviewed candidate; current rules and all publication/content gates remain.
