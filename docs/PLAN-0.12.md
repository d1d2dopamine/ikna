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
| Final Everyday pool | Historical Part 7, [EVERYDAY-POOL.md](EVERYDAY-POOL.md) | Run 37458524156 succeeded; full pinned pool retained by Actions and saved by owner. Small review artifact audited locally; full-pool bytes/material have not been independently audited in this round |
| World provenance coverage | Historical Part 9, full-provenance runbook | Native identity and bounded attribution proven; reusable/hash-bound shards and offline merge review implemented; old run failed final completeness gate; exact counters and all-document evidence open |
| Deterministic selection | Historical Part 10 | Round-10 Japanese boundary guard, diverse alternate ordering, complete material export and saved-pool workflow implemented/tested locally. One real quality handoff run pending; no exhaustive owner/manual review prerequisite |
| Final content census/freeze | Historical Part 11 | Next after quality handoff: census of complete selected material, exclusions, provenance and reproducible snapshot; no preview-only census or selection rerun |
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

## Round 02 — admitted-source pool and existing evidence

Owner instruction, 2026-10-06: use the existing GitHub workflow results rather
than requesting another long run for a known result. See
[ROUND-0.12-02.md](ROUND-0.12-02.md) for inspected runs and verification limits.
Part 5 and the rejected Parts 6/8 admission decisions remain complete; none was
rerun. The observed full World run passed its 32 shards but failed merge, so
Part 9 stays open pending examination of the existing merge report/logs.

Part 7 has a separate admitted Tatoeba-only pool path. It validates source/version
pins, retains every deduplicated candidate origin, measures all scoped pairs and
saves full reusable inputs with hashes. No full-corpus run or publication is
claimed from the local fixtures. The new manual workflow defaults to exact artifact
reuse, fails on missing input and acquires a fresh export only when explicitly
selected. Existing aggregate reports/short selection previews cannot recreate
discarded raw candidates; recover actual saved candidates before new acquisition.

Application work in this batch rechecks the round 01 contracts and existing build
result. The remote Android migration job passed; the JVM job could not acquire a
hosted runner. Final JVM/platform/UI acceptance stays open. No unrelated UI rewrite
was added to meet a percentage. Catalogue implementation is weighted higher here
to rebalance round 01. The parallel GLM documentation lane was reviewed against
the common round 01 ZIP and integrated with targeted evidence corrections;
see [ROUND-0.12-02-GLM.md](ROUND-0.12-02-GLM.md) and the round 02 integration record.

## Round 03 — visual stability and UX

Owner instruction, 2026-10-06: work on small visual/UX defects while remote CI
is unavailable, using desktop Hot Reload for subsequent runtime review.
Codex's scoped source changes cap Browse header/cards at 640dp and Settings
jump strip/rule/list at 1040dp, and recalculate jump-strip centring after label
remeasurement. Available Python contracts pass; compilation and actual layout
acceptance remain open. See [ROUND-0.12-03.md](ROUND-0.12-03.md).

The round-01 Signal Frame/palette repair is retained without speculative changes;
its Windows lighting-switch acceptance is still open. GLM works independently
from the exact merged round-02 ZIP on Search/Backup, the remaining Hot Reload
path typo and PC evidence. Its round-03 source output is reviewed and integrated:
Search has a common busy guard and effective existing 840dp caps, the path typo
is fixed, and an accidental source BOM was removed. Backup remains unchanged.
GLM reports a focused desktop compile, but the referenced logs were not attached;
no interactive visual checks ran and automatic reload was not observed. This
does not close integrated compilation, Hot Reload or the original lighting bug.
This batch is temporarily weighted toward the application lane; return to the
Catalogue evidence dependency order after the integrated runtime checkpoint.

## Round 04 — unblock the desktop verification loop

Owner instruction, 2026-10-06: address missed Hot Reload updates, then let the
owner check the integrated application before resuming Catalogue v2. Pinned
Hot Reload 1.1.1 source confirms that its Windows child compiler invokes the
absent `gradlew.bat`; the initial external Gradle launch could still open the app.
The bridge now reuses that exact distribution with daemon/file watching, and
child/reload diagnostics are exposed in the session log. Native stderr handling
is isolated from PowerShell's terminating-error policy. See
[ROUND-0.12-04.md](ROUND-0.12-04.md) and [HOT-RELOAD.md](HOT-RELOAD.md).

Source fixes and available checks are distinct from Windows runtime acceptance.
Restart the old session once, prove visible edit/reload and error recovery, then
review the integrated round-03 visual changes. This narrow application-tooling
blocker does not start new Catalogue work or rerun known workflows. Next Catalogue
work examines the existing World merge failure after 32 successful shards and
recovers reusable admitted-source pool inputs. Record input hashes, run/artifact
references, stage counts, failure/stop reasons and decisions during that work;
CONTRIBUTING now owns the minimal documentation rule for every change.

## Round 05 — Catalogue evidence recovery and parallel desktop work

Owner checkpoint, 2026-10-06: round 04 was checked and continuation to Catalogue
v2 authorized. This is owner-reported acceptance; no native test counts or complete
platform matrix were supplied. The owner separately reports artifacts during
minimize/maximize/resize/fullscreen transitions. GLM's parallel round uses the
exact 556-file round-04 base on window behaviour, narrow Browse and keyboard/focus
return. Catalogue-lane delivery kept application source unchanged. GLM's result
has now arrived and was reviewed against the common base: it changes no app code.
Reviewed observations and corrected conclusions are integrated in round 06.

Read-only API evidence identifies the old World failure as the **final scan
gate**, not the merge algorithm: merge, attribution and normalization passed.
No retained artifacts or `gv-` cache metadata are listed; anonymous log retrieval
is blocked. Exact failure counters stay unknown. [ROUND-0.12-05.md](ROUND-0.12-05.md)
records observations, implemented recovery/retention and local fixture checks.

Tooling now retains complete native/shard/cache inputs, binds new shard reports
to the alignment hash, supports offline artifact reuse for merge review, and
reports all affected shards/documents. Full-scan/licence/provenance gates remain
strict. This Catalogue-heavy evidence batch performs no corpus acquisition,
workflow dispatch, publication or application build. Next: recover any locally
saved original World report/input/log before selecting outstanding fetch work,
then review the full admitted Everyday input/material evidence.

## Round 06 — window corrections and GLM integration

The owner reproduced the window artifacts after trying GLM's unchanged source.
GLM's bounds snapshots do not close visual acceptance or prove a Windows
limitation. [ROUND-0.12-06.md](ROUND-0.12-06.md) records pinned upstream research,
reviewed evidence and the implementation: one bounds write per Windows edge
drag, restore only after native resize acknowledgement, and VSync for the pinned
immediate Direct3D presentation path. The title bar and application design stay
the same. All round-05 Catalogue changes are retained; no corpus run is needed.

Headless Java policy/operation checks and source checks are local evidence.
Kotlin/Gradle compilation, fresh-process Windows rendering/performance, mixed-DPI
and focus/Browse interactive checks remain open. Do not mark the window defect
visually closed or promote it to an unavoidable OS limitation. Startup changes
require a new app process, not only Hot Reload of an existing window.

## Round 07 — prepare layout before the resized picture

Owner follow-up: round-06 artifacts persist despite three fresh-process logs
confirming immediate VSync and Direct3D. That attempt did not pass visual
acceptance; unapplied source is not supported as its explanation.
[ROUND-0.12-07.md](ROUND-0.12-07.md) records the pinned immediate-render/layout
ordering, the public delegate fix and bounded DEV diagnostics. The child Canvas
and scene constraints are now prepared before a changed Direct3D picture draws,
without relayout of stable frames. Catalogue round-05/06 source is unchanged.

Seven Java headless regression groups pass. Kotlin/Gradle compilation and actual
Windows presentation remain open. Perform one fresh-window short check, using
the new startup marker and trace if the artifact persists. Do not close the
defect from fixture results or request another corpus workflow for this task.

## Round 08 — admitted Everyday selection and owner acceptance

Owner reports the round-07 window defect fixed on 2026-10-06. Close that reported
artifact's acceptance; broader mixed-DPI/multi-monitor and packaged checks remain
open. No window source is changed in this Catalogue round.

Public API metadata lists no retained final admitted Everyday artifact and no
artifacts for the old Everyday/World runs. These observations do not cover
separately saved local inputs. Do not rerun known admission/census results.
The next missing dependency is the full Tatoeba-only input and its material review.

Round 08 adds a hash-bound Part 7 → Part 10 review, complete pair/level inventory
and bounded selected samples, and corrects confirmed input overwrite, UTF-8 repair
and gzip reproducibility defects. The existing admitted-pool workflow saves its
full input before optional selection. Local regressions and executed offline
workflow blocks pass; full corpus/material evidence is still absent. Next commit
this ZIP and run the one admitted-pool workflow using saved full inputs if
available, otherwise its explicit `fresh-tatoeba` mode. Keep all language defaults
and `run_selection=true`. [EVERYDAY-POOL.md](EVERYDAY-POOL.md) owns exact instructions;
[ROUND-0.12-08.md](ROUND-0.12-08.md) records evidence and remaining gates. World
stays open without another scan; recover saved reports/logs before choosing work.

## Round 09 — saved Everyday material audit

Run `37458524156` completed successfully on 2026-10-06. Its exact small review
ZIP is available; the owner saved the full pool. Round-08's request to acquire/run
the pool is fulfilled and is not the next action. The run reports 11,099,490 unique
candidates, 110 directed pairs and 934,652 selected memberships across all
decisions (including 62 in two omitted decks); counts do not prove quality.

Round 09 independently checks all 3,280 preview memberships: 754 unique targets,
8,157 contexts and 8,159 origins. Hash links, scopes, target/candidate identities,
complete token boundaries and UTF-16 reconstruction pass using the recorded ICU
74.2. Full pool bytes are absent from this session and are not recalculated.
The preview is the first ten ranked targets per included deck, not a population
sample. Five distinct single-hiragana Japanese targets and 63 distinct targets
with similar alternatives are review signals, not automatic exclusions.

An offline viewer shows the actual material, source attribution, 27 preliminary
manual findings and hash-bound reviewer export/import. No new workflow or corpus
run is needed for this evidence. [ROUND-0.12-09.md](ROUND-0.12-09.md) and
[EVERYDAY-POOL.md](EVERYDAY-POOL.md) own checks and reuse instructions.

This original proposal for owner card review was superseded on 2026-10-07:
language-by-language owner review is not practical, and round 10 is the last
bounded manual-selection round. Continue through automated checks/later stages.
World, Knowledge expansion, full material review, freeze, final format and
publication remain separately gated.

## Round 10 — final bounded selection work, automated handoff

Owner decision: preserve high quality, finish this selection correction, then use
workflows and subsequent stages. Do not require the solo owner to assess unknown
languages, inspect every card or repeat manual-selection rounds without a new
demonstrated blocker. Present useful examples in text; the viewer is optional.

Explicit `boundary-diversity-v2` defers Japanese ICU targets whose source boundaries
cut analyzer units, preserving complete short words/compounds and exact identities.
Alternative selection prefers different written-token content after source/align
evidence; the existing duplicate threshold stays unchanged. Local real-engine and
contract fixtures pass. The old-preview impact is not a population defect rate.

Next: commit the ZIP and run **catalogue v2 everyday quality handoff** once with
prefilled inputs for run `37458524156`. It reuses the pool, does one new selection,
saves complete included memberships before evaluation and checks the full output.
The old selector, acquisition and admission are not rerun. Full-report count
deltas are complete; exact old identity/context deltas cover only its retained
preview because the old complete selection was not saved.

After the gate passes, proceed to Part 11 census/content snapshot over that saved
selection, then Part 12 storage/client acceptance. Freeze records exact identities,
the accepted quality profile and semantic limitations; automatic checks do not
prove every translation correct. World provenance/publication stay separately
gated. [ROUND-0.12-10.md](ROUND-0.12-10.md) and
[EVERYDAY-POOL.md](EVERYDAY-POOL.md) own evidence/run instructions.

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
