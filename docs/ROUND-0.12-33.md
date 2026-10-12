# Round 33 - full isolated candidate preserves exact output

2026-10-12, Asia/Yekaterinburg. The overlap-reuse candidate from
[Round 32](ROUND-0.12-32.md) completed the full saved-input historical-cap-8000
selection in an isolated source copy. Strict independent comparison confirms
complete compressed and logical selected-output identity with both
[Round 30](ROUND-0.12-30.md) and the retained historical material. Measured
selection elapsed time was **4,288.253 seconds versus 8,335.166 seconds** for the
original control, an observed 48.55% reduction. This checkpoint precedes normal
source integration; it does not change the current default cap or publish assets.

## Isolated implementation and acceptance gates

Only `tools/catalog/selection_policy.py` changed in the isolated tree. The
chooser AST equals the reviewed call-local overlap-reuse prototype, and all
remaining policy AST equals the original. The candidate extends exact overlap
maxima only when selected contexts are appended; it does not alter ordering,
identity, source-diversity passes, duplicate rejection or allocation policy.
Original source, inputs and completed control evidence were retained.

The accepted policy fingerprints are exact:

- Original: `6100810b5bd7eca23b063f90dd0c1e81cb3a7f55ba3aeab76f02be84720fb3ae`.
- Isolated candidate: `72783a30ada3995c9cc4e32f96a4fc5ff53940756ceb5895c9530ecd9af92edc`.

The full run retained complete source/provenance validation, fresh staging,
full-pool ranks and context analysis, all directed pairs, final allocation and
selected-output verification. No persistent analysis cache was enabled. This was
a full end-to-end candidate measurement after the bounded diagnostic established
exact prefix equality; it was not a rerun to recover already known source counts.

The comparison first required successful guard exits, complete one-run stage
profiles and bound canonical reports. It then verified source/data inventories,
runtime/input/settings contracts and complete report differences. The **only**
permitted report difference was the specifically reviewed `selection_policy.py`
fingerprint. No broad exemption for code metadata, environment, counters, quality
fields or selected bytes was used. All 237 original/isolated source and prototype
pins passed, and the normal catalogue source remained unchanged at this checkpoint.

## Complete output identity

All 11,099,490 unique candidates, 5,290,947 source contexts and 110 directed pairs
were processed. The comparison streamed the complete candidate gzip, including
CRC/trailer validation, and independently computed compressed/logical hashes,
byte sizes and membership-line count. These match the candidate report, the
completed original control and the retained historical selected identity:

- Included memberships: **928,162**.
- Selected contexts: **2,094,192**; origin occurrences: **2,094,728**.
- Included decks: **328**, comprising 257 normal and 71 thin; two decks omitted.
- Compressed size: **255,938,165 bytes**; logical size: **1,512,254,677 bytes**.
- Compressed SHA-256:
  `a2a5e4bb1e4079e88c8986443c95761795d5fe66ec14ee46a1f83e099e95df64`.
- Logical SHA-256:
  `7149ca35ebc44b0833434505a94d187e11194a2eca7d4f45b885b061fb267cd4`.

Complete stage counts, summary, ordered deck inventory, quality counters and all
other report fields match the original control. Its pre-omission selected-target
summary remains 928,224, distinct from the 928,162 serialized included memberships.
Hash/byte identity covers full selected content, not only counts or preview rows.

The candidate selected material is therefore the same content already examined
by the completed Round-30 structural/provenance/boundary audit. That audit's
findings remain applicable to these identical bytes. The full structural evaluator
was **not rerun** for this candidate, and its self-baseline checks are not
independent semantic-quality or historical-preview improvement evidence.

## Full measured costs

Both profiles use one first-process run, historical cap 8000, boundary-diversity-v2
and the same pinned environment: Python 3.12.14, SQLite 3.53.1, zlib 1.3.2 and
ICU 74.2 with mapped binary hashes. This is not a full cap-12000 test. Candidate
launch-to-exit UTC interval was 2026-10-12T00:36:02.356657Z through
2026-10-12T01:47:32.973164Z. The measured selection window excludes some launcher,
setup and integrity work, so it is not identical to that interval.

| Measurement | Original control | Isolated candidate |
| --- | ---: | ---: |
| Complete selection wall seconds | 8,335.166 | 4,288.253 |
| Pair evidence wall seconds, 110 calls | 7,187.008 | 3,169.583 |
| Pair evidence process CPU seconds | 6,853.630 | 2,840.084 |
| Allocation wall seconds, 330 calls | 249.734 | 228.186 |
| Context analysis wall seconds, 11 calls | 239.194 | 242.068 |
| Staging wall seconds | 342.062 | 329.984 |
| Ranks wall seconds, 11 calls | 125.799 | 126.827 |
| Process lifetime high-water RSS, bytes | 1,666,994,176 | 1,664,880,640 |

The observed whole-selection candidate/control elapsed ratio is 0.5145.
Pair-evidence wall time is 55.90% lower and its process CPU is 58.56% lower in
these two runs. This concentration and CPU decrease support the intended
reduction in repeated computation. Other stage measurements and all stage call
counts are retained in the complete comparison. The small RSS difference is not
treated as an established memory improvement.

This is one full control and one full candidate invocation, not a randomized
repeat-run distribution or calibrated forecast for another corpus or machine.
OS/filesystem caches were uncontrolled; token-cache hit counts and recency can
differ because the candidate makes fewer pure overlap calls. Stage clocks include
the same diagnostic instrumentation and durable-boundary overhead. Process CPU
includes other threads; sampled stage RSS is whole-process residency and a lower
bound, not exclusive stage allocation.

## Resource outcome and shared host

The candidate guard completed with exit code zero; no resource stop occurred.
Memory ceilings remained 4 GiB sampled RSS and 5 GiB address space, with a 2 GiB
available-memory floor. The one-second guard sampled peak RSS at 1,664,983,040
bytes and minimum available memory at 5,818,888,192 bytes. The separate process
high-water measurement is shown above; these are distinct measurements.

The explicitly authorized candidate disk floor was **3 GiB**, compared with
4 GiB for the original control. Preflight retained all original evidence and
reserved 100 MiB for compact diagnostics; no user or control file was deleted.
Minimum monitored free disk was **4,117,577,728 bytes**, approximately 3.835 GiB.
This establishes completion under the authorized 3 GiB floor, not under the
original 4 GiB disk floor. Temporary files remained on the monitored filesystem.
Sampling can miss brief excursions, and hidden cgroup limits remained unknown.

The host was shared. Recorded approximate overlaps were compact report ZIP
compression/transfer around 00:38-00:39 UTC and small conditional-integration
fixture/review work around 00:59-01:02 UTC. Those activities were not separately
resource-sampled; their exact overlap with measured subwindows is unknown.
Unobserved background load is possible. `SHARED-HOST.json` preserves the supplied
activity record, so this result is not described as an exclusive-host benchmark.

## Verification, retained evidence and integration boundary

Before the full run, the isolated source passed the retained **126-test**
selection/segmentation suite in 2.240 seconds. The strict comparison helper's
**10 tests** passed in 0.004 seconds, including incomplete guards, source/AST
drift, unexpected report differences, malformed JSON, gzip corruption and
non-overwriting output. These are recorded tests, not newly repeated during this
documentation step.

The finalized technical review independently rehashed 10 bound small
evidence/helper files and rechecked the 237 source/prototype pins. It confirmed
the sole policy difference and the exact fully replayed policy hash proposed for
subsequent integration. It did not repeat the full gzip traversal, inspect SQLite,
rerun selection or rerun the structural evaluator. No blocking evidence gap was
found for the narrowly scoped output-preserving change, subject to retaining this
checkpoint and its reviewed regression requirements.

[selection-round33](evidence/selection-round33/) contains the complete profile,
selection report, guard outcome/logs, strict comparison, preparation/preflight,
command and UTC endpoints, shared-host record, exact candidate policy and patch,
comparison helper/tests/README, test logs and final technical review. A compact
index binds every copied file. The 9,586,688,000-byte SQLite file, raw candidate
corpus, selected gzip and preview payload are excluded from the source delivery;
their relevant identities remain in the reports.

`PREPARATION.json` intentionally retains its earlier `prepared-not-executed`
status. Completed profile/guard/comparison artifacts and this report establish
the later outcome without rewriting that history. Archived scripts expect the
original external results layout and retained inputs; they are evidence, not
instructions to launch another full run from this compact package.

The comparison retains `productionIntegrationApproved:false` because it is a
non-integrating diagnostic tool. **Round 33 is the saved pre-integration
checkpoint.** Normal selector integration and durable regressions belong to a
separate decision/checkpoint. No model/classifier gain, new source admission,
content freeze, publication approval, app visual change or cap-12000 result is
claimed here.
