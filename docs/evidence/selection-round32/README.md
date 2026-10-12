# Round 32 evidence

See [the completed round report](../../ROUND-0.12-32.md) for scope, results and
limitations. This directory preserves the original experimental artifacts as
evidence, without integrating the candidate into production.

- `AGGREGATE.json`: all seven exact output identities, timings, cache counters,
  resource observations, matching-number timing pairs and candidate profile.
- `INDEPENDENT-VERIFICATION.json`: complete payload rehash/byte comparisons,
  Round-31 baseline identity, source/helper bindings and raw-pstats checks.
- `REAL-SEQUENCE.json`: actual process order, command lines, exit status and UTC
  orchestration timestamps. Completion requires all seven processes.
- `SHARED-HOST.json`: known small overlapping test workload and uncertainty.
- Per-run directories: all seven completed manifests and started-scope records;
  candidate-cProfile also retains raw pstats and readable caller/callee output.
- `tooling/`: exact prototype/harness sources, tests, logs, review, manifests,
  serial runner and independent verifier.
- `EVIDENCE-INDEX.json`: included-file hashes, byte totals and omitted payloads.

The original prototype/harness README and pre-run manifests inside `tooling/`
retain their historical implementation-only checkpoint. The completed round
report, aggregate and probe manifests supersede those execution-status statements.
The actual profile bound B1/C1 even though the pre-run guide suggested B3/C3.

The seven 31,461,064-byte complete `EVIDENCE.json` payloads are intentionally not
copied here. Full staging, corpora and selected output likewise remain external.
Their absence does not turn an aggregate into complete-corpus evidence: the
completed probes and verifier explicitly record what was compared and hashed.

Archived scripts use the original external directory layout and retained inputs.
They are reproducibility evidence, not standalone installed tools or permission
to rerun the full workflow, integrate the candidate or publish catalogue assets.
