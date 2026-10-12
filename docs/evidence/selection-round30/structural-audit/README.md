# Round 30 full selected-material structural audit

**Baseline: current-self-baseline. This is not a historical-preview comparison or a semantic-quality improvement experiment.**

The unchanged `tools/catalog/selection_evaluate.py` checked the complete Round 30 historical-policy control output. Its baseline consists of byte-for-byte copies of the current `selection.json`, current `preview.jsonl.gz`, and original admitted `EVERYDAY-POOL.json`. `SCOPE.json` binds those copies and their source paths with SHA-256. The current selection report SHA-256 is `3f116d0cfcd9bfd9c2b819d11a2a0953085f6a75fe5193314d4a9bf6bf753009`.

## Result

- Full-material structural, identity, provenance, boundary, duplicate-policy and count/hash contracts passed.
- 928,162 memberships; 221,653 unique global targets.
- 2,094,192 contexts; 2,094,728 origin occurrences.
- All 3,280 current preview memberships were retained with unchanged context IDs.
- No deck-count differences or lost pairs, as expected in a self-comparison.
- Evaluator exit code 0; guarded elapsed time 151.14 seconds.
- Sampled child peak RSS 301,072,384 bytes (about 287 MiB).

`results/QUALITY.json` and `results/QUALITY.md` are untouched evaluator outputs. Their generic “baseline/old preview”, “quality gate”, and delta fields refer to this current-self-baseline here. They do not establish semantic accuracy, improvement over historical selection, content freeze or publication approval. The separate parent `CONTROL-COMPARISON.json` owns historical full-output equality evidence.

The run used the pinned project virtual environment and ICU 74.2. It retained a 2 GiB RSS threshold, 3 GiB address-space ceiling, 2 GiB available-memory floor and 4 GiB disk floor. All temporary paths were isolated on the monitored filesystem. No threshold was crossed. Cgroup visibility was unavailable; sampled limits can overshoot between polls. `RESOURCE.json` records measurements, and `run_audit.py`/`COMMAND.json` record execution. Neither source files nor finalized producer artifacts were modified.
