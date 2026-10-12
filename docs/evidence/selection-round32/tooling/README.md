# Exact call-local overlap reuse prototype

Experimental, non-publishing, outside `ikna-work`. No production implementation,
UI, selected snapshot, source admission, cap or classifier policy was changed.
This directory contains generated test fixtures only; it does not contain new
catalogue material. No real-prefix or full-pool candidate run has been executed.

## Result and evidence

`overlap_reuse.py` provides the same `choose_contexts` signature as the frozen
`tools/catalog/selection_policy.py`. It imports the original quality key,
near-duplicate test and overlap calculation. The sole algorithmic change is
retaining an exact maximum over a growing prefix of the selected contexts.

- 15 tests passed in 2.713 seconds under Python 3.12.14 and pinned ICU 74.2.
- 3,177 direct differential invocations compare every returned field, selected
  object identity and order, rejection count, input preservation, and the exact
  near-duplicate comparison trace. These include 1,500 seeded random cases.
- Coverage includes both source passes, source/alignment precedence, true ranking
  ties and permutations, aliased objects, equal copies, repeated IDs/text,
  duplicate rejection-key counting, empty families, empty/nonword text, zero and
  negative limits, limits 1/2/3/12 and above supply, missing language and ordinary
  invalid-input errors, threshold boundaries, and all 11 supported languages.
- A 97-row generated SQLite fixture preserves the complete `pair_evidence`
  target graph and all counters, including sentence length, translation length,
  no usable target and exact quarantine rejection. It triggers repeated evidence
  pruning and checks target allocation through the existing replenishment helper.
  It is not a production ingestion/source-admission or end-to-end build test.
- In a 37-object, limit-12 fixture, original overlap calls are 2,314 and candidate
  calls are 378, with identical results (12 chosen, 0 rejected). This is a proven
  call reduction, not a measured runtime speedup.
- Fresh-process natural-cache traffic produces identical output hashes across
  five selection phases, while token-cache counters diverge. No private cache
  clearing, replacement or direct mutation is performed by candidate or tests.
- All 159 files under `ikna-work/tools/catalog` match the pre-test hash manifest.
- The untouched upstream `test_selection_quality.py --require-japanese-engine`
  also passes all 13 tests in each of two separate processes: original policy,
  then candidate substituted in memory before importing the test module. This
  additionally exercises its real pinned Japanese analyzer cases and synthetic
  selection path. The two logs are retained as `UPSTREAM-QUALITY-BASELINE.log`
  and `UPSTREAM-QUALITY-CANDIDATE.log`; fixture timings are not performance evidence.

`TESTS.log` records the actual suite result. `TEST-EVIDENCE.json` records counts,
source/candidate hashes, fixture identity, ICU/runtime and cache observations.
`SOURCE-BEFORE.json` and `SOURCE-UNCHANGED.json` bind the frozen source check.

## Exact algorithm and scope

For each candidate object identity `c`, store `(k, maximum)`, where `maximum` is
the left-to-right maximum of `context_overlap(c, selected[j])` for `0 <= j < k`.
`selected` is append-only. When ranking revisits `c`, evaluate only the newly
appended suffix `selected[k:]` and extend the maximum in the same order. If no
context was appended, reuse that maximum unchanged. A first value is assigned
directly; later values replace it only when strictly greater. No rounding,
approximate score, quantization or short-circuit threshold is introduced.

For immutable ordinary input dictionaries/text during a call and the unchanged
deterministic functions/runtime, this invariant makes every ranking key equal
to the original key. Inductively both implementations choose the same candidate,
make the same duplicate decision and update the same selected list/families/key
set. Both source passes, stable sorted/min order, dictionary equality membership,
`remaining.remove` behavior, unique rejection-key counting and the original
post-ranking limit check stay in place. The sometimes surprising missing-language
exception after a ranking is also retained.

The map is keyed by Python object identity, not reference IDs or text. Aliases
share only their computed maximum; distinct equal dictionaries retain independent
state. All objects remain held by `ordered`, so identities cannot be recycled
during the call. The policy still uses dictionary equality for selection/removal.
Every returned context is the same original dictionary, never a copied/rebuilt
record. Arbitrary fields, origins and target evidence remain intact.

If there are U unique candidate objects and K chosen contexts, each (object,
selected-position) overlap is evaluated at most once, bounded by U*K overlap
calls. At most U `(count, maximum)` entries are stored: O(N) additional call-local
state, rather than an O(N*K) pair matrix. The state never survives a function
return and never supplies choices to another target or later invocation.

This does not promise identical behavior for concurrently mutated inputs,
stateful monkeypatched overlap functions, externally altered cached Counters,
side-effecting custom dict/number subclasses, changing segmentation configuration
during the call, or transient failures intentionally tied to a repeated call.
Those are outside the immutable deterministic catalogue contract. Ordinary
validation failures in the fixtures remain unchanged. The existing overlap
function returns finite scores in [0, 1]; the candidate does not redefine it.

## Existing global cache behavior differs

The existing `_written_counts` LRU (8192 entries) and downstream CJK span cache
are left intact. Fewer calls mean different hit/miss totals and recency, which
can change subsequent runtime and token recomputation. Cache contents/statistics,
cProfile counts and timings are deliberately not promised to be bit-identical.
They are diagnostics, separate from the returned rejection/pair/deck counters.

`cache_state_probe.py` starts each implementation in its own fresh process. Five
37-object selections are separated by 8,170 or 8,300 unique-text public overlap
calls, forcing natural LRU eviction. All five complete selected result objects
share output SHA-256
`4c71217cd3dec4471407d8357240225ba8e76eb2cd5b98977ce8a0aadb08e61d`.
At the end, original/candidate misses are 33,102 / 33,124 and hits are
55,918 / 36,536. Thus the candidate can cause slightly more token misses after
different recency even while eliminating most overlap calculations. End-to-end
measurement must include that consequence, not infer speed solely from call count.

The ordinary differential suite deliberately runs original then candidate with
natural inherited caches; its cache deltas are not a cold-cache cost comparison.
The separate two-process fixture establishes the fresh/reused-state behavior.
These generated fixtures do not establish full-workflow performance or universal
equivalence on every corpus input.

## Run the tests

No dependency install, repository edit, source download, catalogue write or
production staging access is needed. Use the previously pinned offline runtime:

```bash
ROOT=/workspace/scratch/8105054a3211
ICU="$ROOT/ikna-toolchain/icu-74.2/runtime/icu/usr/local/lib"
IKNA_ICU_LIBRARY="$ICU/libicui18n.so.74.2" \
IKNA_ICU_VERSION=74.2 LD_LIBRARY_PATH="$ICU" \
  "$ROOT/ikna-venv/bin/python" -B \
  "$ROOT/ikna-results/round32-overlap-prototype/test_overlap_reuse.py"
```

The test driver writes its evidence JSON inside this prototype directory. Direct
unittest discovery can be used for an independent check without overwriting it.
`IKNA_TEST_REPO` can explicitly point the tests at another reviewed source copy;
the evidence records that copy's policy SHA. The tests require actual ICU 74.2,
not a fake CJK tokenizer or silently skipped engine test.

## Next real-prefix comparison design, not yet executed

1. Extend the standalone round-31 probe outside the repository. Retain all current
   completion, successful-control, source/runtime/dictionary/quarantine, full
   staging before/after hash, read-only SQLite, exact prefix row/ID hash and
   bounded-resource gates. Keep the candidate module's hash separate from the
   unchanged producer source bindings. No force/fixture bypass in the real CLI.
2. Use the same exact first 32,768 en-to-de rows and inherited full-pool analysis,
   ranks and evidence limit 12. Do not restage or recompute sample ranks. The
   original pair SQL and adapter must stay intact. Do not expand the prefix just
   because the experiment is fast, and do not run the full pool.
3. In the fresh candidate process, after verifying the untouched source files
   and imported function identities, substitute only
   `selection_experiment.choose_contexts` with this candidate. Restore the alias
   after the one pair call. Keep every other producer function/source unchanged.
   Do not overwrite `selection_policy.choose_contexts`, alter token caches, skip
   quarantine or mutate selected records. Record the substitution explicitly.
4. Serialize the complete returned target list and Counter with the existing
   canonical, order-preserving writer. Require exact byte/hash/size equality to
   the independently completed round-31 baseline `EVIDENCE.json`, not only counts
   or selected IDs. That baseline hash is
   `f551785d003b9a8f0793bedd687829f0a3364ebf47cab80f83d2a1f052ee12bd`.
   Bind baseline PROBE/evidence hashes and recheck files afterward. Any mismatch
   stops the experiment with a failure record, never an accepted speed result.
5. Initially run an unprofiled candidate under the same 2 GiB address-space,
   300-second process-CPU and host-headroom checks. Report pair wall/process CPU,
   lifetime RSS, cache counters and complete identity. Gate a separate profiled
   run on success; count context_overlap calls and inspect both token-cache misses
   and candidate own overhead. Cumulative cProfile times overlap and profiler
   overhead is not a speedup denominator.
6. If identity holds, compare unprofiled measurements using fresh processes in
   alternating original/candidate order, sequentially on the same guarded host.
   Record competing load and filesystem-cache conditions. The stored baseline's
   13.273 seconds is one historical observation, not a stable distribution or
   guaranteed speed ratio. Timing may justify a bounded follow-on decision only.

The current source gates, accepted snapshot, cap 8000 historical control versus
current cap 12000 distinction, 110-direction/full-selection evidence requirement,
UI preservation and production/publication deferral all remain unchanged.
