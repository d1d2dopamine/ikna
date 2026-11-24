# 🧭 Unscheduled ideas

This file is deliberately **not a roadmap**.

Items here have no target release, no target date and no implied commitment. They
may be implemented later, rewritten completely, or rejected without a deprecation
process. Moving an idea here means only that it was worth recording; it does not
mean that 0.11 or any later release owes the feature.

## 🛟 Local-history resilience

The project already has explicit export/restore paths. A separate future idea is
to add automatic local safety snapshots before operations that can replace or
remove a large amount of learner data, such as a restore, destructive import or
future database maintenance operation.

This should only be pursued if it solves a demonstrated failure mode. It must not
turn into cloud backup, an account system or telemetry.

## 🔎 Local diagnostics

A small local-only diagnostics surface could expose facts useful while debugging
an installation, for example:

- database/schema version;
- installed deck and target counts;
- review-row count;
- last successful planning/scheduler pass;
- active Catalogue generation;
- local database size.

Nothing in this idea requires sending diagnostics anywhere. It is optional and
may be rejected if ordinary logs and exports remain sufficient.

## ⚙️ Settings audit

At some future checkpoint, review settings for controls whose original ownership
has moved into Governor, Scheduler or automatic grading policy. Remove or rewrite
only controls that are demonstrably stale; do not delete user choice merely to
make the settings screen smaller.

## 🌱 First-run explanation

If real users are confused by the small initial batch, consider a minimal
explanation that it is an adaptive starting point rather than a daily quota.
Avoid multi-page onboarding unless evidence shows it is needed.

Implemented: the first-run session message states it directly.

## 📦 Catalogue-download states

The client could distinguish download, verification, decompression and import
failures more explicitly when installing Catalogue assets. This is useful only if
current error messages prove insufficient in real use.

Partially implemented: the import phase wording sets the expectation for large
decks. Hash verification of downloaded assets remains open.

## 🧩 Widget compatibility matrix

Continue testing the Android widget on the smallest declared size, large system
font scales and several launcher implementations. Treat launcher-specific layout
work as maintenance, not as a redesign project.

Dropped by owner decision (2026-10-05): the shared-target presentation audit
was taken out of this list without being run.

## 🚫 What this file is not

This file must not be used to smuggle work into a release. In particular:

- an item here does not block 0.11;
- an item here does not gain priority merely by being old;
- an item here may be deleted if later evidence says it is unnecessary;
- scheduled Context/Transfer Policy work remains in the 0.11 roadmap rather than
  being duplicated here.


## Catalogue deck size and progressive download — owner scenario, 2026-10-08

The 2026-10-08 proposal was a scenario. On 2026-10-09 the owner approved a
12,000-target maximum; [round 20](ROUND-0.12-20.md) implements the v2 defaults
and ceiling. Progressive/partial download remains unscheduled. Historical
8,000-target evidence stays unchanged. Compare incremental target usefulness,
context diversity, source mix and actual bytes under the same quality gates.
Larger decks are expected to cost more storage; neither compressed nor installed
size is assumed proportional to card count. No thresholds are relaxed to reach a
catalogue headline total.

Product idea: before download, let the user choose how much of a deck to install
and see the corresponding MB estimate. After partial installation, offer a small
additional download or the entire remaining deck. Distinguish total selected
size, additional download bytes and installed storage. A smaller choice should
show a smaller transfer when the packaging genuinely permits it; do not display
invented linear estimates or fetch the full asset invisibly for a partial choice.
Preserve the existing study-card interaction and scheduling logic.

Compare these routes before choosing:

- Keep a full downloadable pack but import only a selected target count. Simplest
  content layout, but it saves local content rather than network transfer; it does
  not fulfil the reduced-download-MB goal unless another transfer format is used.
- Publish a few deterministic size tiers. Show measured tier bytes; assess duplicated
  publication assets and whether upgrading redownloads existing material.
- Publish deterministic ranked segments with a static manifest. Download a useful
  starting subset, append segments later or complete the deck. Measure cold-start,
  metadata, compression and recovery costs before accepting the extra complexity.

For a genuine incremental route, extend the same deck's membership idempotently:
keep stable global target identities, contexts/provenance/licences and learner
history; avoid duplicate memories, review resets or charging already present
segments again. Bind segments to one content generation, validate hashes/counts,
resume interrupted transfers and never silently mix generations. Changing the
installed size must not change FSRS rules, grades or the classic-card flow.
Any reader/index/storage change requires its own reviewed design and validation.
The source remains static catalogue assets; no server or runtime generation.

Scale context from round 12: 928,162 Everyday memberships, 45 decks at the cap.
Under the otherwise unchanged selection, raising only those decks to 10,000 can
add at most 90,000 memberships; 12,000 at most 180,000 (upper totals 1,018,162 and
1,108,162). Round 20 tightens the 12,000 upper total to **1,090,140** using each
deck's actually reported unallocated candidates (161,978 maximum additions).
These are ceilings, not yield forecasts: context collisions and
available useful targets can reduce gains. Thus this cap change alone cannot
produce 1.5 million Everyday memberships from that snapshot. Other collections
are not included in this comparison; memberships are not unique global targets.

Owner hypothesis: sustaining the earlier quality profile while broadening supply
and seeking roughly 1–1.5 million memberships will likely require **1–3 additional
open corpora**. Treat that count as a planning hypothesis, not a mandatory quota,
measured necessity or admission decision. Choose sources by net useful additions
in weak directions/levels and diversity; more corpora do not by themselves ensure
quality. The shortlist and proposed measurement live in
[CORPUS-CANDIDATES-0.12.md](CORPUS-CANDIDATES-0.12.md); coverage evidence is in
[ROUND-0.12-12.md](ROUND-0.12-12.md). Preserve current admission decisions.

Acceptance before implementation: compare 8k/10k/12k selection yields and quality,
measure compressed/installed and incremental bytes on actual selected content,
choose a clear optional UX, and validate partial/full import, upgrade, interrupted
transfer, generation changes, shared-target deduplication and history retention.
This recording batch changes documents only; no selection, workflow or app build
is requested. Documentation checks and complete ZIP verification cover this edit.
