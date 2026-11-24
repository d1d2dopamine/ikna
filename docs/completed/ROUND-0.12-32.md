# Round 32 - exact overlap reuse on the real prefix

2026-10-12, Asia/Yekaterinburg. A standalone performance candidate preserves the
complete returned evidence for the fixed 32,768-row Everyday en -> de prefix from
[Round 31](ROUND-0.12-31.md). Three fresh-process original/candidate comparisons
observe lower pair time: median **13.494 seconds original versus 4.658 seconds
candidate**, an observed ratio of 2.897. This is bounded-prefix evidence, not a
full-workflow speedup or production integration decision. The original catalogue
source, UI, policies and accepted selected snapshot remain unchanged.

## Candidate and exact behavior boundary

The external `overlap_reuse.py` retains one exact running maximum for each
candidate object over an append-only prefix of the selected contexts. Ranking
evaluates overlaps only against newly selected contexts instead of recomputing
the same earlier overlaps. The additional map is O(N) call-local state; it is
discarded after each chooser invocation. No global pair cache, approximate score,
model, source admission or persistent choice reuse is introduced.

Both source-diversity passes, quality/alignment precedence, stable ordering,
dictionary equality/removal, duplicate-reference behavior, rejection-key counting
and the original post-ranking limit check remain intact. Every selected context
is the same original dictionary with all fields and origins preserved. Exactness
assumes immutable ordinary context data and unchanged deterministic helpers/runtime
during each call; concurrent mutation or stateful replacement functions are not
part of the catalogue contract. The induction argument and implementation diff
are retained with the prototype evidence.

The existing written-token and CJK span caches are not replaced or cleared by
the candidate or real harness. Reduced calls change LRU hit counts and recency;
later token misses and runtime can differ. These diagnostics are distinguished
from returned rejection/pair/deck counters, which must remain exact.

## Tests, review and real execution gates

Before real measurement, 15 prototype tests passed with 3,177 direct differential
invocations, including 1,500 seeded random cases. They compare complete values,
selected object identity/order, unchanged inputs and exact near-duplicate call
traces. Coverage includes limits, both source passes, ranking ties, repeated IDs,
aliased/equal dictionaries, rejection-key deduplication, all 11 supported languages
and real ICU 74.2. A 97-row synthetic SQLite fixture preserves the entire pair
target graph/counters and checks target allocation. Two fresh processes also
preserve selected results through natural eviction beyond the 8,192-entry LRU,
while demonstrating different subsequent cache misses.

The untouched upstream quality suite passed 13/13 with the original chooser and
13/13 with the candidate substituted in a separate test process, including the
required pinned Japanese analyzer. The standalone harness passed 31 tiny tests
covering identity gates, baseline non-execution of the candidate, fresh caches,
dependency verification, reference binding, exact output comparison, read-only
access and alias restoration after failures. Independent review reproduced the
15 and 31 test results without blocking findings. These are fixture/regression
checks, separate from real-prefix measurements or full-selection acceptance.

All seven real processes used the same new helper and candidate hashes. Baseline
modes hashed the candidate without importing or executing it. Candidate modes
verified its function signature and original helper aliases, then substituted
only `selection_experiment.choose_contexts` for the measured call. A `finally`
block restored that alias. The original policy functions/source were not patched.

The harness retains the successful [Round 30](ROUND-0.12-30.md) completion,
guard and independent historical-comparison gates; producer source, registry,
quarantine, Python/SQLite/zlib/ICU/dictionary bindings; read-only SQLite transaction;
and exact ordered candidate-ID and seven-value row hashes. It consumes stored
full-pool choices/ranks/levels verbatim. The unchanged pair SQL has only a
parameterized `LIMIT 32768`; no restaging, sample ranks or recomputed analysis.
Morphology remains off and the evidence-context limit is 12. Historical cap 8000
is lineage metadata here; final deck allocation is outside this function.

Every invocation hashed the complete 9,586,688,000-byte staging file before and
after its pair measurement. Source/helper/reference evidence was rechecked, and
all processes completed with the original chooser restored. Completion was not
declared until `REAL-SEQUENCE.json` recorded `complete-seven-serial-probes` with
seven exit-zero processes.

## Complete output equivalence

Each process returned 11,496 exact targets from 32,768 candidates and 111,407
eligible target occurrences. Pair rejections remain 254 sentence length,
134 translation length and 188 no usable target. Absent Counter keys are not
invented as measured categories.

All seven complete canonical returned-evidence files are 31,461,064 bytes with
SHA-256:

`f551785d003b9a8f0793bedd687829f0a3364ebf47cab80f83d2a1f052ee12bd`

The exact row-stream and ordered-ID hashes also match Round 31:

- Seven-value rows: `30c05cfa2166b7427a619eb8a02039eced5e00ee444cbd85848c8d92a472e71a`.
- Ordered IDs: `1015a074bfb80e5f60624feac2b50ec6f3e685610d6d598df42c3ab9be139c21`.

After the completed sequence, an independent verifier streamed and rehashed all
seven full payloads, byte-compared each to the new original baseline and checked
that baseline byte-for-byte against Round 31. It rechecked source/helper and
reference bindings and validated the reported focused metrics against raw pstats.
It did not rerun selection or reread the full staging database. Equality includes
list order, every target/context/origin/text/identity/rank/level/evidence count
and all returned counters, not just aggregate counts or a preview.

## Repeated unprofiled measurements

The serial order was B1, C1, C2, B2, B3, C3, then candidate-cProfile. Each was a
fresh process under the same pinned runtime: Python 3.12.14, SQLite 3.53.1,
zlib 1.3.2 and ICU 74.2. Exact mapped library bytes are recorded in every manifest.
The English prefix consumes inherited features and does not run a Japanese
analyzer during pair evidence.

| Process | Pair wall seconds | Pair CPU seconds | Total invocation seconds | Final lifetime RSS bytes |
| --- | ---: | ---: | ---: | ---: |
| B1 original | 13.297 | 13.282 | 36.555 | 110,325,760 |
| C1 candidate | 4.658 | 4.659 | 26.405 | 110,387,200 |
| C2 candidate | 4.621 | 4.617 | 25.992 | 110,428,160 |
| B2 original | 13.532 | 13.521 | 35.252 | 110,272,512 |
| B3 original | 13.494 | 13.481 | 35.366 | 110,211,072 |
| C3 candidate | 4.889 | 4.890 | 25.526 | 110,395,392 |
| Candidate-cProfile | 11.472 | 11.467 | 32.376 | 110,796,800 |

Pair windows include the small common invocation/restoration wrapper; imports,
integrity hashes, prefix checks and output serialization are outside them. Total
invocation measurements include that additional work. Lifetime high-water RSS
is whole-process memory, not an allocation attributed exclusively to the pair.

Pairing matching numbered unprofiled runs yields observed original/candidate
wall ratios 2.855, 2.928 and 2.760; the ratio of their medians is 2.897. This is
three observations per implementation, not a randomized trial or confidence
interval. Candidate manifests all use completed B1 as their identity reference;
the external aggregate pairs C2 with B2 for timing rather than silently reusing
its in-manifest C2/B1 ratio.

## Profile and cache consequences

The candidate profile records 32,728,716 total calls. Its actual references are
B1 and C1; the historical harness guide suggested B3/C3, but no measured reference
is rewritten to match that suggestion. The profiled/unprofiled C1 pair-time ratio
is 2.463, describing instrumentation overhead and host variation, not speedup.

| Candidate function | Calls | Self seconds | Cumulative seconds |
| --- | ---: | ---: | ---: |
| `pair_evidence` | 1 | 0.716 | 11.472 |
| `choose_contexts` | 2,286 | 0.134 | 10.160 |
| Call-local `maximum_overlap` | 864,108 | 0.683 | 6.717 |
| Original `context_overlap` | 864,108 | 1.191 | 5.775 |
| `Counter.__and__` | 864,108 | 1.944 | 2.928 |
| `near_duplicate` | 150,876 | 0.228 | 1.386 |
| `_written_counts` misses | 36,692 | 0.028 | 0.979 |
| `context_quality_key` | 169,164 | 0.320 | 0.817 |

Cumulative values overlap and must not be summed. Compared with the unchanged
Round-31 profile, context-overlap calls fall from 5,289,804 to 864,108, a measured
83.66% reduction. Chooser, near-duplicate and quality-key call counts remain
unchanged. These chooser calls are evidence pruning, not final allocation.

All original processes end with 10,542,928 written-token hits and 36,680 misses;
all candidate processes end with 1,691,524 hits and 36,692 misses. The 12 extra
misses are retained in the measured cost and demonstrate changed LRU recency.
Both versions start with empty written-token and quarantine caches; the quarantine
cache ends with the same 32,191 hits and one miss. No cache state is adjusted to
make the performance comparison look better.

## Resources, shared host and remaining limits

Orchestration UTC interval: 2026-10-12T00:30:27.432866Z through
2026-10-12T00:34:05.560740Z. Per-process commands and UTC endpoints are retained.
Each process had a hard 2 GiB address-space ceiling and 300-second CPU ceiling,
with soft CPU interruption at 299 seconds. Recorded preflight host available
memory never fell below 7,380,430,848 bytes and free disk never below
14,013,456,384 bytes. These are preflight snapshots, not continuous headroom/RSS
supervision or a wall-time ceiling; hidden cgroup limits may remain unknown.

The host was not exclusive. A small serial 126-test selection/segmentation suite
on an isolated source copy started at approximately 00:32:53 UTC and finished
before 00:33:03 UTC, during B3's invocation. Its reported suite timer was 2.240
seconds; exact overlap with B3's measured pair subwindow was not recorded. The
coordinator reported no other known concurrent heavy job, but background and
whole-host load were not continuously measured. See the shared-host evidence.

Required preflight reads warm filesystem caches; there is no OS-cold claim.
Fresh-process token caches do not recreate the full selector's inherited state
or later dense pairs. One ID-prefix/direction does not establish benefit or
equivalence across all languages, complete pair evidence, final allocation or the
full workflow. No semantic translation/classifier quality, final pack acceptance,
publication permission or application/UI verification is implied.

The next evidence boundary is an isolated full candidate replay, only under the
separate conditional authorization and existing resource/output gates. The separate Round 33 replay started at 00:36 UTC after independent source-copy
review and a capacity preflight; its result is pending and is not evidence in this
completed prefix checkpoint. Production integration remains deferred until
complete-control output identity and end-to-end resource/cost evidence exist.

## Retained compact evidence

[selection-round32](evidence/selection-round32/) contains the candidate and new
harness sources/tests, original pre-run README/manifests, independent review,
test logs, all seven completed PROBE/STARTED records, candidate pstats/caller
output, exact serial runner/UTC records, independent verifier and aggregate.
The inventory hashes each included artifact and records the omitted payloads.
The seven complete 31 MB evidence files, full staging and source corpus remain
outside the source delivery; none was discarded or mistaken for a compact report.

Archived pre-run README/manifests intentionally retain their original statements
that no real run had occurred. This completed report and the seven probes
supersede that execution status without rewriting history. Archived tools expect
the original external results layout and retained large files; they are evidence
copies, not installed catalogue commands or a self-contained corpus package.

Candidate SHA-256:
`36efac2461e102d1c0ae66282c5ea46facff6c1dcdd6b8bb376828e6e0525e1e`.
Harness SHA-256:
`9c77cb25f6ca25eb5c62fd97751cf7b24e7840e264ef4cfa8872a2621f429975`.
All 159 original catalogue files remain byte-identical to the pre-prototype
manifest. No production implementation, cap, source admission, UI or learner-state file
is changed. Active plans receive checkpoint notes and remaining acceptance gates.
The repository UTF-8/NFC and localization-table text check passes after adding
this evidence. Archived manifests, included-file hashes and local report links
were checked; no application build or visual test is implied.

Documentation clarification: Round 31 appended checkpoint notes to active plans;
its original “no plan edit” wording meant no change of planned scope. The current
copy makes that distinction explicit.
