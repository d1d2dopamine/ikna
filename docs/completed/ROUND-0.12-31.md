# Round 31 — exact dense-pair prefix profile

2026-10-12, Asia/Yekaterinburg. After the successful
[full historical-cap-8000 control](ROUND-0.12-30.md), two separate fresh-process
invocations measured the unchanged evidence function on a fixed 32,768-row
Everyday en -> de prefix. Their complete returned evidence is byte-identical.
The instrumented profile identifies repeated diversity-overlap work as the
dominant cost **within this prefix**. No optimization or production cache is
integrated in this round.

## Scope and completed identity gates

The standalone helper opens only completed full-control staging read-only, with
`query_only=ON`. It calls the original `pair_evidence` implementation through an
adapter that accepts its exact SQL and adds only a parameterized `LIMIT 32768`.
Candidate traversal remains `ORDER BY c.id`. Full-pool analysis choices, ranks,
levels, source text and origin evidence are consumed verbatim; no sample-derived
ranks or new analysis are substituted. Morphology remains off, evidence-context
limit is 12, and boundary-diversity-v2 remains active. The inherited 8000 cap is
recorded for control lineage; final deck allocation is outside this function.

Both invocations passed the completed-profile/guard/independent-comparison gates,
source/registry/quarantine/runtime pins, exact prefix IDs and seven-value row
hashes. The complete 9,586,688,000-byte staging file was hashed before and after
each invocation, outside pair timing; its identity, size and bytes stayed fixed.
Baseline/profile settings, runtime, SQLite configuration and helper hashes agree.
The original pipeline source was not changed.

- Prefix: 32,768 candidates, 111,407 eligible target occurrences and 11,496
  returned exact targets. Rejections: 254 sentence length, 134 translation length
  and 188 no usable target. Other absent Counter keys are not invented as observed
  categories.
- Ordered candidate-ID SHA-256:
  `1015a074bfb80e5f60624feac2b50ec6f3e685610d6d598df42c3ab9be139c21`.
- Exact seven-value row-stream SHA-256:
  `30c05cfa2166b7427a619eb8a02039eced5e00ee444cbd85848c8d92a472e71a`.
- Complete returned-evidence SHA-256 for both invocations:
  `f551785d003b9a8f0793bedd687829f0a3364ebf47cab80f83d2a1f052ee12bd`.
  Each canonical JSON payload is 31,461,064 bytes.

Equality covers the complete ordered target list and every retained context,
origin, original text, ID, rank, level, evidence count and rejection counter.
It is not a comparison of only target sets or previews. The profiled invocation
verified the completed baseline manifest and payload. This documentation review
also independently streamed and rehashed both payloads, checked the retained
tooling manifest and verified raw pstats against the recorded function metrics.
It did not rerun the selector or reread the full staging file.

## Measured costs

The pinned runtime is Python 3.12.14, SQLite 3.53.1, zlib 1.3.2 and ICU 74.2;
mapped ICU binaries and inherited Japanese dictionary evidence are recorded in
the profiles. The English prefix does not recalculate Japanese features.

| Measurement | Baseline | cProfile |
| --- | ---: | ---: |
| Pair wall seconds | 13.273 | 34.841 |
| Pair process CPU seconds | 13.250 | 34.838 |
| Total invocation wall seconds | 41.875 | 57.255 |
| Final process lifetime high-water RSS, bytes | 110,022,656 | 110,075,904 |

The observed profiled/baseline pair-time ratio is **2.625**. This describes the
instrumented run's overhead and shared-host variation, not a selector speedup or
a correction factor for other runs. Total invocation time additionally includes
integrity hashing, imports, prefix checks and serialization. RSS is whole-process
lifetime high-water memory, not allocation attributable exclusively to the pair.

The raw profile records 117,027,811 function calls. Relevant call boundaries:

| Function | Calls | Self seconds | Cumulative seconds |
| --- | ---: | ---: | ---: |
| `pair_evidence` | 1 | 0.856 | 34.840 |
| `choose_contexts` | 2,286 | 0.155 | 33.280 |
| `context_overlap` | 5,289,804 | 6.369 | 27.206 |
| `Counter.__and__` | 5,289,804 | 10.458 | 16.108 |
| `near_duplicate` | 150,876 | 0.240 | 1.448 |
| `_written_counts` cache misses | 36,680 | 0.028 | 0.934 |
| `context_quality_key` | 169,164 | 0.336 | 0.880 |

These cumulative times overlap and **must not be summed**. All 2,286
`choose_contexts` calls here come from bounded evidence pruning inside
`pair_evidence`; final context allocation is not in this profile. Callers/callees
show the repeated diversity `min`/`max` evaluations reaching `context_overlap`,
which constructs Counter intersections and repeatedly totals their values.

The existing 8,192-entry written-token cache starts empty in each process and
ends with identical counters: 10,542,928 hits and 36,680 misses. Thus the observed
overlap workload remains substantial despite extensive reuse of token counts.
The profile supports investigating this repeated computation first; it does not
prove the achievable benefit of a particular rewrite. SQLite fetch work may
appear in enclosing self time, and cProfile can affect relative function costs.

## Resource and measurement limits

Each probe used a hard 2 GiB virtual-address-space ceiling and a 300-second
process-CPU ceiling, with soft CPU interruption at 299 seconds. Both completed.
Preflight host available memory exceeded 7.3 billion bytes and free disk exceeded
14.2 billion bytes. Temporary files stayed in each new owned output directory.
The probe records preflight headroom and process high-water RSS; it does not
continuously supervise host/cgroup headroom or impose a wall-time ceiling.

The host was not exclusive. The unchanged selected-material structural audit
started after the baseline began and overlapped the profiled invocation. Its
completed resource record shows 151.138 seconds elapsed and 301,072,384 bytes
sampled peak RSS. Audit results are a separate checkpoint; their self-baseline
comparisons do not establish semantic or historical-preview quality. The overlap
sequence was reported with the execution results; no precise probe/audit UTC
overlap interval was captured by this helper. See the compact shared-host record.

There is one baseline and one profiled run, no randomized repetitions or
confidence interval. OS caches were uncontrolled and were warmed by required
preflight reads. Fresh process state does not recreate the full selector's
inherited cache or later dense-pair state. A candidate-ID prefix is not an
independent quality-labelled sample and does not represent every language pair.
No full-workflow speedup, final allocation equivalence, classifier quality,
semantic translation accuracy, publication approval or application/UI acceptance
is claimed.

## Evidence, review and next boundary

[selection-round31](evidence/selection-round31/) retains both completed
`PROBE.json` files, raw pstats (18,156 bytes), caller/callee text, invocation logs,
the exact historical standalone helper/tests and their review/test evidence.
Its compact index records copied-file hashes and the omitted large payloads.
The two 31 MB returned-evidence payloads, full staging and source corpus remain
outside the source delivery; their identities are retained.

The archived helper SHA-256 is
`3dfe1efe4bce32ff4f791a6c41b3dff7fb89ab7bc7d67083b405196cdb02e0fa`.
Its original README, REVIEW and TOOLING-MANIFEST intentionally retain the pre-run
checkpoint, including `realMeasurementExecuted=false`. They are copied without
rewriting history; this report and the completed profiles supersede that execution
status. The archived commands/tests expect the original external results layout
and retained large inputs. They are evidence copies, not newly installed
`tools/catalog` commands or a self-contained corpus package.

Before measurement, 22 tiny synthetic tests passed and independent review
reproduced the result. Metadata-binding and pre-parse snapshot gaps found during
that review were fixed before the retained helper was hashed. These fixture
checks are separate from the subsequently completed real-input equality gates.
The repository UTF-8/NFC and localization-key text check also passes after adding
this compact evidence; no application build or visual test is implied.

The next step is a separate bounded, exact-output-preserving prototype targeting
the measured repeated overlap work. Keep mandatory provenance, quarantine,
identity, rank/tie order, boundary, diversity, rejection-count and output gates.
Any eventual integration needs appropriate multilingual/adversarial checks and
complete control-output and end-to-end resource evidence. No optimization code,
cache admission, policy change, change of planned scope, source admission or visual change is
part of Round 31.
