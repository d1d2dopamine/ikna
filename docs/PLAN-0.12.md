# 0.12.0 press working plan

## Cycle decision

Owner decision, 2026-10-05: the application release **0.11.0 press has shipped**.
The next working cycle is **0.12.0 press**, with approximately **50% of planned
effort on Catalogue v2 and 50% on the overall application plan**.

This document owns the active cycle, work allocation and new status decisions.
The 0.11 plans remain historical checkpoints. An unchecked 0.11 item is a
candidate for review, not automatically a 0.12 commitment or proof that the
application release is still pending.

Treat the 50/50 split as an allocation of effort across work batches, including
verification and evidence review, not a count of tickets or changed files.
Keep each change coherent; do not add unrelated Catalogue/UI work to satisfy
the ratio in a single patch. Record any temporary imbalance caused by a blocker
and rebalance the following batches.

Changing the planning cycle does not itself change application build versions,
create a release tag or publish Catalogue assets. Use `docs/VERSIONS.md` and the
release workflow for a dedicated version/release step.

## Starting evidence

Use the supplied source snapshot as the initial baseline. Read actual code and
current tests before carrying status forward. Preserve dated evidence in:

- [PLAN-0.11.md](PLAN-0.11.md) and [ROADMAP-0.11.md](ROADMAP-0.11.md);
- [modern_PLAN-0.11.md](modern_PLAN-0.11.md);
- [PARTS-6-9-DECISION-RECORD.md](PARTS-6-9-DECISION-RECORD.md);
- [PART-9-FULL-PROVENANCE.md](PART-9-FULL-PROVENANCE.md);
- [ai-audits.md](ai-audits.md), [test-cli.md](test-cli.md) and [rele.md](rele.md).

The snapshot already labels the application 0.11.0 press. Application release
status is distinct from Catalogue v2 publication and evidence gates. Do not infer
final Catalogue acceptance from the application release.

## Catalogue v2 lane — 50%

Source review on 2026-10-05 carries the following status forward. Historical
Part 5 supply census is complete; Parts 6 and 8 have resolved **non-admission**
decisions. Those are completed evidence decisions, not failed implementations
to rerun until a source can publish. Preserve dependency order below:

| Candidate work | Existing owner/reference | Implemented / open |
| --- | --- | --- |
| Final Everyday pool | Historical Part 7 | Preview tooling exists; final admitted Tatoeba-only pool and coverage evidence open |
| World provenance coverage | Historical Part 9, full-provenance runbook | Native identity and bounded attribution proven; sharded full-scan tooling exists; complete all-document evidence open |
| Deterministic selection | Historical Part 10 | Disk-backed non-publishing experiment exists; real selected-material review and final production policy open |
| Final content census/freeze | Historical Part 11 | Census/readiness tooling exists; final reviewed samples, provenance, coverage and pinned inputs open |
| Final storage/publication format | Historical Part 12 | Earlier lossless experiments exist; final frozen-content measurement and client acceptance open |
| Target relations and context evidence | Historical Parts 13–14 | Existing exact identity is retained; richer relations and replayable context history open |
| Context/Transfer policy | Historical Parts 15–16 | Conservative experiments remain open and depend on history representation |
| Publication validation | Historical Part 17 | Final dry run and separately authorized publication open after all gates |

Retain the recorded admission decisions: MASSIVE remains experimental and is not
admitted to production; all-direct-pair WikiMatrix expansion remains unadmitted.
Tatoeba Everyday and the already approved WikiMatrix scope are the conservative
baseline. Admit World only through verified record-level attribution. Reopening
these decisions requires a separately reviewed evidence decision, not larger
counts, looser score floors or an old runbook's default experiment settings.

Read [CORPORA-0.11.md](CORPORA-0.11.md), [CATALOGUE-V2.md](CATALOGUE-V2.md),
[SOURCES.md](SOURCES.md) and the decision record as inherited contracts until a
scoped task explicitly updates their ownership or policy.

## Application lane — 50%

Use [modern_PLAN-0.12.md](modern_PLAN-0.12.md) for the current source-reviewed
inventory; [modern_PLAN-0.11.md](modern_PLAN-0.11.md) is historical evidence.
Select remaining work in this order:

1. Application and Developer Sandbox correctness; meaningful regressions
   and real Android/packaged desktop verification.
2. Browse reading behaviour, narrow/large-font layouts, accessibility and desktop
   window/focus/input correctness, including real multi-monitor evidence.
3. Proven code/dependency/resource cleanup and usability improvements.
4. Documentation ownership, contributor support and fast development-loop checks.
5. Low-risk visual follow-ups only when required by selected work.

Keep broad LearningRepository/Settings splits deferred until separately reviewed;
the old phrase “after 0.11” is not automatic approval to perform them now.
Keep `UNSCHEDULED.md` ideas optional. Respect its 2026-10-05 owner decision to
drop the shared-target presentation audit when triaging the older Track G list.

Preparation completed in this batch:

- [x] Establish this active 0.12 plan and the owner-selected 50/50 allocation.
- [x] Add root AGENTS and the common `ikna-development` skill.
- [x] Separate owner workflow/full-ZIP delivery into `ikna-owner-workflow`.
- [x] Review inherited source status in both lanes and select the owner's first
  scoped batch: visual/tooling bugs and plan synchronization.
- [x] Record tasks and available acceptance evidence in
  [ROUND-0.12-01.md](ROUND-0.12-01.md).

## Current batch and next checkpoint

Round 01 implements pointer-lifetime/bounds arbitration for Signal Frame, shared
lighting-aware palette tiles and selected semantics, IKNA-T-002/003 tooling fixes
and status/design documentation synchronization. Python source/tool checks pass;
new shared JVM regressions and final target builds still require a working
toolchain. The reported Windows lighting-switch sequence and real Android
palette accessibility remain open acceptance checks, not closed UI evidence.

This batch is deliberately weighted toward the application stabilization lane,
with the census-byte repair in Catalogue tooling. Rebalance subsequent scoped
batches toward the Catalogue pool/provenance/selection evidence above; no exact
percentage is claimed from file counts or an unmeasured effort estimate.

## Release blockers

Define final 0.12 scope and task-specific acceptance checks during detailed plan
review. Until then, do not treat this initial checkpoint as release approval.

- Preserve learner history, shared-target identity, migration/schema compatibility,
  export/restore and Developer Sandbox isolation.
- Require relevant checks and final Android/desktop build evidence for selected
  application scope; source checks alone are insufficient.
- For Catalogue publication, require reviewed admitted-source inputs, complete
  required provenance, final selection/content freeze, accepted lossless storage,
  client limits and an exact non-publishing final run.
- Require context history before Context/Transfer claims or experiments depending
  on seen/unseen observations; do not silently change FSRS weighting.
- Preserve index-last ordering and keep application/Catalogue publication separate.

Record a blocker in the lane that owns it. Do not use a Catalogue publication
blocker to reinterpret the shipped 0.11 application as unreleased.
