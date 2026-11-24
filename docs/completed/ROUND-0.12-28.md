# Round 28 — offline classifier feasibility and independent evaluation contract

2026-10-12, Asia/Yekaterinburg. Read the complete findings and primary-source
links in [CLASSIFIER-FEASIBILITY-0.12.md](CLASSIFIER-FEASIBILITY-0.12.md).

The decision is to retain existing rules for now. This checkpoint separates
mandatory provenance/identity/boundary checks from three content questions:
translation correspondence, Everyday suitability and target-in-context usefulness.
It does not pretend that rule or model agreement establishes semantic correctness.

Classic Bicleaner has official relevant packs for eight of 55 unordered pairs,
at most 16 of 110 learner directions. Japanese, Korean and Chinese are uncovered.
One en-de pack alone is 523,179,851 compressed bytes. Individual archive notices,
dictionary/training-source inventory and a fully locked, tested CPU runtime have
not been established; no pack was acquired or executed. Lack of a published digest
is recorded, not replaced by an invented checksum. Ready all-direction coverage is
not inferred from a multilingual toolkit's source-code licence.

A separate, reproducible 300-pair ID/hash-only evaluation manifest uses independent
published MQM labels: 100 calibration and 200 held-out pairs, balanced by observed
major accuracy errors versus explicit no-error labels. Calibration and held-out
source talks are disjoint, and all selected source hashes are unique. Every group
has one rater; these are observed labels, not consensus or guaranteed correctness.
The TED/MT domain, unknown model-training overlap, and original-text rights caveat
remain explicit. No benchmark sentence text is redistributed or admitted to ikna.
This manifest is unscored and provides no measured classifier accuracy.

Verification reran the manifest builder against the pinned publisher file and
obtained identical bytes. All retained evidence hashes, 300 unique source hashes
and document split disjointness passed. `evidence/classifier-round28/VERIFICATION.json`
records these checks. No application or runtime dependency changed in this round.

The empirical comparison remains deferred pending exact pack audit, reproducible
runtime startup and benchmark-use clarification. The report gives a minimal
conditional en-de trial and its acceptance limits, rather than expanding to a new
custom classifier or silently changing admissions. Full saved-input resource
profiling can proceed independently under the existing performance plan.
