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

Source review on 2026-10-05, updated with the owner's successful Everyday report
on 2026-10-08, carries the following status forward. Historical
Part 5 supply census is complete; Parts 6 and 8 have resolved **non-admission**
decisions. Those are completed evidence decisions, not failed implementations
to rerun until a source can publish. Preserve dependency order below:

| Candidate work | Existing owner/reference | Implemented / open |
| --- | --- | --- |
| Final Everyday pool | Historical Part 7, [EVERYDAY-POOL.md](EVERYDAY-POOL.md) | Run 37458524156 succeeded; full pinned pool retained by Actions and saved by owner. Round 19 independently reads/hash-checks all 11,099,490 retained candidates and exports exact function-rank metadata; no new acquisition is needed |
| World provenance coverage | Historical Part 9, full-provenance runbook | Native identity and bounded attribution proven; reusable/hash-bound shards and offline merge review implemented; old run failed final completeness gate; exact counters and all-document evidence open |
| Deterministic selection | Historical Part 10 | Run 37674241928 completed selection and full-output automatic contracts; 928,162 included memberships, 328 decks. Failed run 37647495700 is superseded. Semantic certification/publication remain false; no exhaustive owner/manual review prerequisite |
| Final content census/freeze | Historical Part 11 | [Round 15](ROUND-0.12-15.md) fully rereads Everyday selected material, reproduces the quality report and accepts its pinned snapshot for non-publishing Part 12 measurements. Global Catalogue freeze, World/Knowledge and release acceptance remain open |
| Everyday coverage gaps | [Round 12](ROUND-0.12-12.md) | 73 below-1000 decks: 53 already below threshold before context allocation, 20 cross it during allocation; zero budget loss. Round 20 measures pinned Polish/Korean WMT/MKQA trials and NTREX shape; WMT shows potential to lift both omitted levels above 40. Round 21 retains WMT/MKQA as conditional supplements across all eligible decks and adds a [large-source review shortlist](LARGE-CORPUS-CANDIDATES-0.12.md). Content rights/domain gates and production admission/full-matrix yield remain open; allocation losses remain separate |
| Final storage/publication format | Historical Part 12 | [Round 16](ROUND-0.12-16.md) measures every saved Everyday selected row with disk reconstruction: self-contained 237.403 MiB, pair 194.098 MiB, language 182.881 MiB. Pair is a final-pack measurement candidate, language is diagnostic. Round 19 closes exact function-word rank metadata. Final pack/provenance handoff, enriched sizes and actual client acceptance remain open |
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

Round 17 implements the owner-approved narrow visual round: plain Android bottom
bar, palette-aware reading cat instead of falling deck-header paint on both
platforms, and recognisable coloured accents in grey lighting. Offline palette
and source parity checks are recorded in [ROUND-0.12-17.md](ROUND-0.12-17.md).
Desktop/Android compilation and real layout acceptance remain open; this does
not close Catalogue v2 gates or alter card interactions.


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

### Round 10 fix 1 — failed-run recovery

Run `37647495700` failed during Japanese analysis: legacy lower()-uniqueness
conflicted with global NFKC/casefold identity. No complete selection was saved.
Fix 1 defers ambiguous choices in quality v2, keeps unrelated targets and strict
boundary/source gates, validates Japanese analysis first after staging, and emits
progress/context diagnostics. Real-engine and full-output fixture checks pass.
Commit the fix and start a new quality-handoff dispatch on the updated branch;
keep the saved-pool pins. Full-run evidence was pending at fix delivery and is now
superseded by successful run `37674241928`; Part 11/12 remain open.
[ROUND-0.12-10-fix1.md](ROUND-0.12-10-fix1.md) owns details. No new manual round.

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

## Round 11 — classic cards and useful DEV tools

Owner-approved application batch while the corrected Everyday quality workflow
runs. Remove active cloze/production exercises and promotion, preserve the full
classic context -> tap for meaning -> existing side/rating flow. Keep historical
levels for lossless export/restore/undo; filter live queues and normalize only
retired daily-plan keys without changing allowances or learner history.

DEV gains actual feature navigation, a read-only/copyable state snapshot, and a
profile-local switch to apply normal restrictions. Do not expand synthetic
scenarios as a substitute for these tools. Existing seed/reset controls stay.
Local SQL and source/text/localization checks are recorded in
[ROUND-0.12-11.md](ROUND-0.12-11.md); Kotlin/Room/Compose compilation and runtime
acceptance are open. No workflow is added or dispatched. Catalogue source,
source pins, accepted Windows fixes and build versions remain unchanged.

Next Catalogue step remains automatic quality-report review, followed by Part 11
census/content snapshot of the saved selection, then Part 12 storage/client
acceptance. No repeat manual selection or redundant corpus rerun is requested.

Round-11 fix 1: owner CI log confirms compilation/debug APK and shared test tasks;
117/119 desktop tests pass. Correct two new test assertions (Browse pool versus
product blockers; valid review count versus raw append-only journal). Application
behavior is unchanged. Corrected desktop rerun and runtime acceptance remain open;
see the round-11 fix record. Catalogue evidence work is unaffected.

## Round 12 — Everyday handoff evidence and coverage gaps

Owner-supplied run `37674241928` passes the full-output automatic gate. The small
attachment and bound baseline/pool reports were checked locally; large pool and
selected-material bytes were not independently reread. Record 221,653 unique
targets, 928,162 included memberships and 2,094,192 contexts. 257 decks are normal,
71 thin and two omitted (Polish with Korean meanings, middle/advanced). All 110
directions retain at least one level. No semantic certification, freeze or
publication is claimed.

[ROUND-0.12-12.md](ROUND-0.12-12.md) and its retained machine inventory own exact
counts, pins, checks and the shortage diagnosis. Source supply and context-policy
losses are distinct: the 8,000 cap does not cause any below-1000 deck. The omitted
pair has only 94 candidate rows, while reserving distinct contexts reduces its
middle/advanced eligible 46/47 targets to 32/30. Allocation is greedy/bounded;
the report does not prove the maximum achievable without changing the policy.

Owner decision: record replenishment needs now; choose/evaluate an open corpus
later against net gains in these weak directions/levels. This is a planned
investigation, not source admission, a mandatory 1,000-card quota or permission
for synthetic/pivoted filler. Investigate allocation-limited cases before blaming
all loss on the corpus. Preserve inherited MASSIVE/WikiMatrix decisions.

Next: Part 11 census/content snapshot over the saved complete selected file,
then Part 12 storage/client acceptance. Do not rerun acquisition or selection to
obtain counts already present here. Verify retention of this run's selected file;
the owner's saved original raw pool is a different input. New corpus research and
any policy change remain separately scoped; no routine manual-selection round.

## Round 13 — corpus recommendation research

Owner requested concrete source recommendations after round 12. Publisher/author
research is recorded in [CORPUS-CANDIDATES-0.12.md](CORPUS-CANDIDATES-0.12.md).
Measure WMT24++ human social/qualifying speech references first; MKQA's useful
translated questions second. Both expose the eleven-language matrix, but net
new cards are unmeasured and full gap closure is not promised. Verify common
source anchors rather than generating translations or assuming sentence splits.

MKQA data uses CC BY-SA 3.0 under its primary README; its code licence and mirror
metadata must not substitute for the data terms. WMT's card declares Apache-2.0;
redistribution/provenance evidence still belongs to admission. NTREX-128 is a
separate Knowledge candidate, not relabelled Everyday supply. PUD/FLORES-derived
material stays reference-only in this proposal; subtitles/TED/NIKL do not have an
established applicable public-pack grant in the inspected evidence.

Next corpus task is a bounded, pinned incremental-yield measurement in weak
pairs/levels, preserving baseline-rank comparability and honest licence handling.
It is separate from the 20 allocation-limited decks and the saved-material Part
11/12 work. No candidate acquisition, adapter, registry admission, workflow or
publication is performed by this documentation batch. Current source decisions
and the successful Tatoeba snapshot remain unchanged.


## Owner scale/download scenario — recorded, not scheduled

2026-10-08: keep the current 8,000 budget unchanged. The owner proposes later
comparing 10,000–12,000 per deck, with optional user-selected partial download,
visible measured MB and incremental or complete top-up. Alternatives and storage,
identity/history/recovery acceptance are recorded in
[UNSCHEDULED.md](UNSCHEDULED.md#catalogue-deck-size-and-progressive-download--owner-scenario-2026-10-08).
Do not turn this into a selected implementation task or delay the saved-content
census solely to implement it.

The owner expects that preserving quality while broadening supply may need 1–3
additional open corpora; record this as a hypothesis for the existing candidate
measurements, not admission or a source-count quota. The 45 capped Everyday decks
permit at most 90k/180k more memberships at 10k/12k under otherwise unchanged
selection; actual yield is lower or equal. The 1.5m Everyday objective therefore
cannot be met by this cap change alone on the current snapshot. Overall catalogue
scale must distinguish collections, memberships and unique targets.

This batch only records the scenario and synchronizes candidate planning. No code,
cap, asset format, corpus admission, workflow, version or schema changes. Text,
relative-document-link/whitespace checks and exact full ZIP verification are the
scoped checks; no build or long corpus run is needed.

## Round 15 — full Everyday selected-material snapshot

2026-10-08: the owner supplied the full selected archive for `37674241928`.
[ROUND-0.12-15.md](ROUND-0.12-15.md) records full raw/logical hash verification,
the unchanged evaluator's byte-identical quality-report reproduction, and a
separate census of all 928,162 memberships, 221,653 unique targets and 2,094,192
contexts. All 328 included decks and two exclusions reconcile; the round-12
shortage counts remain unchanged. No acquisition or selection was repeated.

Accept and pin this Everyday selected intermediate for non-publishing Part 12
storage/client measurements. The [snapshot passport](evidence/everyday-round15/SNAPSHOT.json)
fixes input, policy, pipeline and environment identities, while the historical
quality report remains unchanged. This closes the Everyday selected-snapshot
checkpoint; it does not close global Catalogue freeze, World/Knowledge evidence,
pack enrichment, app acceptance, semantic certification or release publication.

A bounded Polish/Korean witness exactly reproduces the actual 51 beginner targets
and their context assignments. Primary-only alternatives give 57/60 under changed
context assumptions, not a global recoverable-card forecast or selector repair.
Complete allocation attribution needs the saved raw pool and exact ranks later;
the original raw pool was not supplied locally in this round.

Next: measure lossless layouts against these exact saved rows, then validate
final pack enrichment and reader limits. Preserve all identities, contexts,
offsets and provenance through round trips. Record the one-context profile
(287,645 memberships) and unavailable lemma evidence explicitly. Do not rerun the
legacy sieve, silently discard data for smaller packs or request another long
selection workflow. Cap 8,000, application behavior, source admissions and the
unscheduled scale/download scenario remain unchanged.

## Round 16 — lossless selected storage and reader handoff

The full local experiment now reconstructs all 928,162 memberships, 2,094,192
contexts and 2,094,728 origin occurrences from each actual serialized layout.
[ROUND-0.12-16.md](ROUND-0.12-16.md) owns exact evidence, commands, failed-attempt
limits and the source-audited importer boundary. The reusable tool verifies all
fields, order, source associations, per-deck digests and input pins; no selection
or workflow is repeated. Atomically publish only closed gzip streams; interrupted
or partial outputs cannot constitute successful evidence.

Pair pooling saves 18.241% on the selected intermediate (194.098 versus 237.403
MiB), with maximum cold transfer 3.540 MiB and logical dependency 19.142 MiB.
Treat it as a candidate for final-pack measurement against the self-contained
control. Language pooling saves 22.966% but requires up to 30 decks' dependency
data (15.472 MiB transfer / 66.725 MiB logical); keep it diagnostic. These are
Everyday-only pre-token/credit bytes, not final app downloads or installed sizes.
The provisional 10% savings screen is an experiment heuristic, not a new release
policy. Final format and global Catalogue Part 12 remain open.

Next input: the separately saved admitted pool from `37458524156`, or its exact
input-bound per-language top-function-word export. Selected target ranks do not
contain the excluded top-60 prefix needed by the existing token classifier.
Use original context-key/first-seen/tie order when exporting it, without reranking
the selected subset or repeating selection/acquisition.

Next implementation: materialize exact saved memberships into final pack rows,
derive canonical-token UTF-16 spans, preserve ordered contexts and compatibility
credits, and measure final self-contained/pair candidates including token and
provenance payloads. Then explicitly validate provenance persistence/export and
any required safe Room migration with the real reader. Unknown JSON keys alone
do not preserve versions/contributors through the current importer. Do not invoke
the legacy optional-context trimming helper to make the frozen rows fit.

No runtime format/migration, card behavior, cap, source admission or build-version
change is made here. Original selected material remains separately retained;
reports and reusable tooling are included in the full project ZIP. No new long
workflow or owner-wide manual language review is requested.

## Round 18 — selected cat rendering and DEV profile lifecycle

Owner-requested application repair after round 17. Preserve the first selected
cat asset byte-for-byte and its 48 dp slot; adjust platform downsampling only.
Keep Rare closed, require separate entry/exit/reseed confirmations, and perform
profile changes through a real process restart rather than retaining old DEV
repositories. Make the empty DEV scenario genuinely empty; explain that ordinary
restrictions do not turn DEV off. Queue confirmed reseeds for startup, keep REAL
data isolated and retain failed requests for retry.

[ROUND-0.12-18.md](ROUND-0.12-18.md) records source changes and local checks.
Compilation/JVM suites and Windows/Android runtime acceptance are still open.
Restart the Hot Reload supervisor once before testing this round. Catalogue v2's
round-16 next input/materialization work remains unchanged; no new corpus run,
schema change, version bump or publication is included in this application batch.

## Round 19 — admitted input restored and function-prefix handoff

The owner supplied the saved Part 7 admitted archive in two upload-sized ZIP
wrappers. Reassembly matches its manifest; the complete logical candidate scan
matches the original pool identity, and the input reconciles with the frozen
round-15 selected snapshot. [ROUND-0.12-19.md](ROUND-0.12-19.md) records the
offline audit, premerge/retained-count distinction and exact 60-form-per-language
rank-prefix export. [everyday-round19](evidence/everyday-round19/) retains compact
input-bound evidence so later materialization does not require another raw scan.

The round-16 missing frequency-input checkpoint is closed. The prefix is prepared
input, not authorization to build final packs. On 2026-10-09 the owner explicitly
deferred Catalogue v2 assembly until deficient decks are replenished, current-data
validation is defined and passed, and deck-size/product decisions are agreed.
This supersedes the immediate materialization sequence suggested after round 19.

## Owner sequencing decision — quality, coverage and size before assembly

1. Define the automatic validation contract for current and incoming material:
   identities/counts, source/licence/version/credits, direct translation anchors,
   token/UTF-16 boundaries, duplicate/diversity/content rules and explicit failure
   versus warning thresholds. Existing round-15/19 checks remain evidence; they
   do not establish semantic correctness of every translation. Full-catalogue
   manual review is not an owner requirement; ambiguous cases must be flagged or
   quarantined under agreed rules, not declared correct from workflow success.
2. Maintain the exact shortage matrix by language pair and level. Distinguish
   omitted, empty and thin decks, and source shortage from allocation losses and
   caps. Decide usable coverage targets before admitting replenishment.
3. Run bounded, nonpublishing source/yield experiments from the documented corpus
   shortlist. Verify licence/attribution, human translation/alignment, overlap and
   net contribution to the specific deficient decks. Admit sources only with
   evidence; do not assume one to three sources will close every gap.
4. Replenish deficient decks and validate the resulting candidate/selection
   revision automatically, with a new input passport and before/after counts.
   Preserve the existing accepted snapshot as a control. Do not pad with invented
   text, relax quality gates to meet counts or silently accept unresolved gaps.
   Record remaining shortages for an explicit owner scope decision if the
   measured sources cannot close them.
5. Agree deck budgets and download behaviour: cards, compressed download bytes,
   cold dependencies, installed bytes and temporary import cost. Use existing
   measurements/clearly labelled estimates for this decision. Round 20 implements the owner-approved 12,000 maximum; progressive download
   remains a scenario. Exact final payload sizes remain unmeasured.
6. Only after coverage, validation and size decisions are accepted, resume final
   pack materialization, final size measurement and real-reader validation.
   Assembly and publication remain separate actions.

Until then, do not build Catalogue v2 or materialize final packs. The immediate
work remains the validation contract and corpus replenishment, using the existing
shortage matrix, saved evidence and reusable automatic checks. No new long workflow is requested by this planning
change. Statistics changes await a separate product decision.


## Round 20 — approved 12,000 ceiling and acquired corpus review

Owner approval: 2026-10-09. [ROUND-0.12-20.md](ROUND-0.12-20.md) owns the changes,
pins, executed diagnostics, licence routes, checks and limits. Current v2
selection/census/builder defaults and dispatch workflows use a maximum of 12,000;
smaller explicit budgets remain valid for historical replay. This supersedes the
2026-10-08 cap-deferral scenario. Progressive download remains unscheduled.
No quality/context rule, client byte cap, source admission, app/schema version or
public catalogue changes. Full assembly/publication remain deferred.

Saved all-deck evidence bounds cap-only gain at 161,978 memberships, up to
1,090,140 Everyday memberships, with unchanged sources/ranks/allocation order.
This is not a reselection result or final size forecast. The 73 below-1,000 decks
have no budget loss; their shortage remains a distinct task.

Pinned WMT24++/MKQA/NTREX bytes and primary terms were reviewed. Baseline-frequency
Polish/Korean trials reproduce all six old eligible/selected/collision counts.
Adding WMT social/speech whole segments yields Polish→Korean middle 286 and
advanced 376 versus 32/30, but those trial rows are not admitted final content.
All-query MKQA results are optimistic before domain/usability review; NTREX is
Knowledge-only shape evidence. No entire corpus enters production on counts.

Next: the existing preassembly validation/admission work on a usable WMT subset,
licence/provenance handoff and other weak pairs; retain conditional MKQA question
subset work and separate Knowledge review. Do not recreate the shortage matrix
or repeat known acquisition/selection workflows. Part 11 global freeze, Part 12
final enriched bytes/reader acceptance and Part 17 release gates remain open.

## Round 21 — large-corpus research, no admission

2026-10-09. [ROUND-0.12-21.md](ROUND-0.12-21.md) records this documentation/evidence
round. The owner retains WMT24++ and MKQA as future supplements across all eligible
decks after quality/validity/licence/provenance checks. Prior Polish/Korean results
do not establish matrix-wide yield or admit complete sources.

The [larger-source shortlist](LARGE-CORPUS-CANDIDATES-0.12.md) selects five candidates
for further consideration with explicit pair/collection roles and unresolved gates.
It preserves official API responses rather than treating all-source totals as new
cards. Packaging grants, publisher text grants, source-publication notices and
software/model licences are assessed separately. No full candidate acquisition,
source adapter, registry admission, workflow, app or catalogue asset changes.

Next preassembly work: bounded European explanatory-supply review; original rights
and ancestry feasibility for Korean/news and multiway web material; incremental
target/context/level measurements only once those inputs are suitable. Keep
Knowledge/World supply distinct from Everyday shortages. Continue current-data
validation and licence-honest handoff; do not weaken gates to meet a volume goal.

The 12,000 ceiling is unchanged. Final assembly/publication, mixed-source reader
and byte acceptance, global freeze and release checks remain open. No lengthy
rerun or full-corpus build is needed to review this shortlist.

## Round 22 — next-round selection-engine plan recorded

2026-10-09, Asia/Yekaterinburg. The owner requested a documentation update and a
complete ZIP for commit, then an end to today's work. [ROUND-0.12-22.md](ROUND-0.12-22.md)
records this documentation-only checkpoint. No engine implementation or new
corpus/workflow run occurs here.

[SELECTION-ENGINE-PLAN-0.12.md](SELECTION-ENGINE-PLAN-0.12.md) now owns the proposed
next round: profile stage costs on saved inputs, justify reusable intermediate
data/analysis, separate mandatory checks from content scores, and compare current
rules with a bounded offline classifier experiment. A ready/custom classifier is
not selected or approved. Keeping existing rules remains a valid measured outcome.

This inserts a performance/evaluation decision before expanding costly corpus
experiments. It does not replace replenishment or current-data validation; rights
and provenance feasibility work remains useful independently. Required checks,
frequency/identity contracts, source holds, collection boundaries and the 12,000
ceiling remain intact. No LLM service, generated filler or exhaustive owner review.

Next implementation starts from this complete checkpoint, preserves the accepted
snapshot and distinguishes output-preserving optimization from a changed selection
policy. Report quality gains, good-example losses, coverage, runtime/memory and
repeatability before deciding on integration. Final assembly/publication and
existing global content, licence, byte/reader and release gates remain deferred.

## Round 23 — bounded diagnostic tooling; real profile still open

2026-10-11. [ROUND-0.12-23.md](ROUND-0.12-23.md) adds an offline cProfile driver for
the unchanged admitted-selection path, with repeat-output identity checks and
fail-closed input/output handling. The retained two-row fixture evidence verifies
the instrumentation only. Overlapping call times and process-lifetime RSS do not
close isolated stage timing/peak-memory requirements. No optimization or classifier
integration is justified by these fixtures.

The exact saved admitted pool remains required for representative bounded real-input
profiling; its existing Actions artifact is unexpired but download requires GitHub
authentication. No new upstream acquisition, full selection, app/UI/policy change,
assembly or publication occurs. Continue the selection-engine plan's first step
when input is available; later reuse/classifier decisions remain open.

## Round 24 — full saved-input verification and bounded real profile

2026-10-12, Asia/Yekaterinburg. [ROUND-0.12-24.md](ROUND-0.12-24.md) restores the
exact round-19 archive and completes unchanged validation of all 11,099,490
retained candidates. A deterministic per-pair sample retains 14,012 real rows
across all 110 directions; repeated extraction preserves identical source/pool
bytes and repeated selection preserves complete output identities. Compact
non-publishing diagnostic input and evidence are retained for reuse.

The profiler now has real-input timing evidence, not only fixtures. Context
analysis is its largest measured individual boundary; no production speedup is
inferred. Stage-local memory, isolated allocation and controlled cold/reuse
conditions remain open. Current ICU 76.1 differs from historical 74.2, so this is
not frozen-snapshot replay or acceptance. Japanese pinned-engine checks now pass.
No cache, classifier, selection rule, app/UI, final assembly or publication change.

## Round 25 — pinned ICU and measured selection stages

2026-10-12, Asia/Yekaterinburg. [ROUND-0.12-25.md](ROUND-0.12-25.md) adds optional
stage observers with sampled process RSS, isolates the allocation loop and output,
and pins an isolated official ICU 74.2 runtime without changing system libraries.
Changed pins after initialization fail closed, including cached CJK spans.

Two diagnostic sizes (14,012 / 105,988 saved-source rows), each measured twice in
two fresh processes, produce stable complete output. The larger input also matches
the untouched round-22 selector exactly, apart from expected pipeline fingerprints.
Context analysis is the largest isolated boundary; proceed to a bounded experimental
analysis-cache comparison preserving quality counters and all downstream decisions.
No cache is enabled in production by this checkpoint. OS-cold/full-corpus timing,
classifier labels/model decision, final assembly and publication remain open.

## Round 26 — application JVM, packaged Linux and DEV startup verified

2026-10-12, Asia/Yekaterinburg. [ROUND-0.12-26-APPLICATION.md](ROUND-0.12-26-APPLICATION.md)
records 14 shared and 123 desktop JVM tests passing without skips, successful
Kotlin/Compose/Room compilation and release Linux app-image packaging. The actual
packaged launcher passes selftest and an 11-process REAL/DEV startup harness,
covering all six scenarios, Empty reopening, failed-request retention/recovery
and byte-identical REAL data during DEV use. Application source, visuals, schemas,
signing identity and versions remain unchanged.

These results close the current desktop compilation/JVM and headless packaged
startup evidence gaps. They do not establish GUI/native restart handoff, Windows,
multi-monitor or Android runtime acceptance. Android JVM/APK tasks concretely
stop at missing SDK; isolated SDK setup requires owner acceptance of its linked
license agreement. Queue that decision without blocking independent catalogue
work. No SDK agreement was accepted or marker fabricated.

## Round 27 — bounded analysis reuse

[Round 27](ROUND-0.12-27.md) adds a fail-closed, standalone context-analysis cache
experiment. Nine same-input runs preserve complete output identity; observed fresh
warm-cache median is 17.535 s versus 20.416 s baseline, with slower cold population.
This remains a bounded sample result and an offline prototype, not production
cache activation or full-input acceptance. A reviewed malformed-DEFLATE exception
normalization fix and final-code equivalence refresh are recorded separately.

## Round 28 — classifier feasibility decision

[Round 28](ROUND-0.12-28.md) records classic offline-classifier coverage/rights/runtime
limits and an independently labelled, document-disjoint ID-only MQM evaluation
manifest. Retain existing rules; no model was acquired/run or accuracy measured.
The scored comparison is deferred, with concrete pack/runtime/benchmark gates in
[the feasibility report](CLASSIFIER-FEASIBILITY-0.12.md). Mandatory source checks and
three distinct content questions are now documented explicitly.

## Round 29 — resource-guarded full-input readiness

[Round 29](ROUND-0.12-29.md) prepares explicit full-saved-input status, durable
completed-stage evidence and an owned-process memory/disk guard. Signal cleanup
and SQLite-temp filesystem review findings are fixed; 126 relevant tests pass.
The intended full replay uses historical cap8000, preserving current default12000.
Historical/local Python and zlib differences are corrected explicitly; full replay
has not completed in this readiness checkpoint.

## Round 30 — full saved-input control verified

[Round 30](ROUND-0.12-30.md) completes all 11,099,490 candidates and 110 directions
under historical cap 8000. Independent whole-output checks reproduce both logical
and compressed historical selection hashes, all counts and deck decisions. The
full stage profile identifies pair evidence as the dominant cost; bounded
analysis-cache savings must not be extrapolated to the complete workflow.
No cache is enabled. A bounded exact pair-function profile is the next diagnostic.
The current cap-12000 scenario, OS-cold measurement, semantic classifier scoring,
final assembly and publication remain outside this completed control.

The one-pack [read-only archive audit](CLASSIFIER-ARCHIVE-AUDIT-0.12.md) is complete;
pack contents do not resolve underlying source provenance or empirical quality.
Model execution and classifier adoption remain deferred.

## Round 31 — exact dense-prefix cost localized

[Round 31](ROUND-0.12-31.md) measures the unchanged pair-evidence function on an
exact 32,768-candidate en→de prefix from finalized full staging. Fresh baseline
and cProfile return byte-identical complete evidence. Repeated overlap maxima
and Counter intersections dominate evidence pruning in this diagnostic.
Profiler overhead is not an engine gain; one prefix does not represent all pairs.
The next scoped prototype may reuse exact call-local overlap maxima while
preserving all ranking, source, boundary, duplicate and output decisions.
Production integration still requires differential and real-output evidence.

## Round 32 — bounded exact overlap reuse verified

[Round 32](ROUND-0.12-32.md) verifies the call-local maximum-reuse prototype with
3,177 differential invocations, real multilingual/Japanese regressions and seven
exact-output real-prefix runs. Three fresh original/candidate runs observe median
13.494 s versus 4.658 s on that prefix. LRU recency changes are disclosed and
included in measurement. Normal production source remains unchanged.
An isolated full historical candidate replay is the next acceptance gate and
started separately as Round 33; prefix gains do not establish full-workflow gains.

## Round 33 — full exact candidate accepted

[Round 33](ROUND-0.12-33.md) completes the isolated overlap-reuse replay over all
11,099,490 candidates and 110 directions under historical cap 8000. Independent
whole-output CRC/hash verification proves byte-identical selected material,
counts, decks and quality counters; only the reviewed policy fingerprint differs.
Observed elapsed time is 4288.253 s versus 8335.166 s control (48.55% lower), with
shared-host/cache and one-run uncertainty explicitly retained. No guard stopped.
This closes the full-output/resource gate for the exact call-local optimization.
Normal source is unchanged in this checkpoint; integration and durable
independent-reference regression tests are the next separate stage.

## Round 34 — exact overlap optimization integrated

[Round 34](ROUND-0.12-34.md) integrates only the fully replayed call-local maximum
reuse function and three test-only files. 143 relevant tests pass; explicit
Japanese-engine rerun and a 105,988-row normal-CLI smoke preserve all expected
material identities. No UI, policy semantics, default cap or persistent cache
changes. Original and isolated full candidate evidence plus rollback checkpoint
are retained. The observed full-control elapsed reduction is 48.55%, with
one-run/shared-host/cache limitations.

Performance profiling and this justified exact reuse change are complete within
the reported scope. Empirical classifier scoring/adoption stays deferred on
provenance/rights, labels/runtime and coverage grounds; retain current rules.
Android SDK agreement/setup and real-platform GUI acceptance remain separate
application gates. No final Catalogue assembly, source expansion or publication.

## Round 35 — full-coverage diagnosis and provenance recovery

The owner's current goal is full coverage together with quality, with no corpus
payloads in delivered source ZIPs. [Round35](ROUND-0.12-35.md) recovers the exact
historical Everyday deficit map without another selection run, checks the tiny
current pl-ko direct-link metadata, and adds a nonpublishing metadata-only WMT
anchor-reconstruction path. No count threshold, source admission or selector
policy is relaxed. The old605segment pilot remains conditional; original-text
rights and usable-content checks are unresolved. Corpus collection stays in the
manual Actions path; source packages retain code/config/tests and compact reports.

## Round 36 — targeted allocation diagnostic readiness

[Round 36](ROUND-0.12-36.md) prepares a one-run, saved-pool Actions diagnostic for
20 allocation-limited Everyday cells. It preserves global ranks and every baseline
quality/profile constraint, distinguishes bounded-evidence loss from occupied
contexts, and permits only a conservative strict-three-context additive witness.
No production selector change or real full-input result is claimed at readiness.
The remaining 53 cells' eligible-supply deficit stays separate from allocation.
Corpus payloads remain outside delivered source packages. Source admission and
full-coverage acceptance are unchanged and unresolved where previously stated.

## Round 38: interface reliability

[Round 38](ROUND-0.12-38.md) implements the first owner-approved interface-audit
stage: fail-closed pre-wipe export, visible preparation/session/Browse failures,
retry/cancellation handling and correct propagation of bundled-pack failures.
39 shared and 130 desktop JVM tests pass; the Linux release image and the
11-process disposable-profile headless harness pass. README Round 37 and all
Catalogue policies/content remain unchanged. Android SDK/device and real GUI/
Windows acceptance remain open; this does not close the whole interface audit.

## Round 39 — catalogue outcomes and persistent Undo

2026-10-12: [Round 39](ROUND-0.12-39.md) adds per-deck outcomes, dependent-filter
normalization, atomic/cancellation-aware catalogue installation, separate plan
refresh retry and Undo without an automatic six-second expiry. Format/content,
learning policy and learner history remain unchanged. Shared/desktop evidence
is recorded with the stage; Android and GUI acceptance remain open.

Current Windows report is non-smooth edge resize/maximize and missing minimize
animation. The owner explicitly retired the old white-flash report; historical
round descriptions are retained as dated evidence, not current bug status.

## Round 40 — touch feedback and constrained layouts

2026-10-12: [Round 40](ROUND-0.12-40.md) separates touch press feedback from the
Signal Frame, adds scrollable reading/onboarding states and minimum 48dp targets,
and supplies concise statistics semantics and accurate voice help. Existing
visual identity, authored onboarding copy and learning behavior are retained.
Source/JVM evidence is recorded; actual large-text, gesture and accessibility
acceptance is explicitly open.

## Round 41 — first study and long catalogue lists

2026-10-12: [Round 41](ROUND-0.12-41.md) makes the optional starter pair explicit,
provides direct Catalogue choice, aligns START with session entry, uses keyed
lazy catalogue rows and honors accessibility timeout for brief notices. Final
JVM/Linux evidence is recorded with the round. No Windows runtime fix is claimed;
current resize/maximize lag and absent minimize animation remain open pending
actual build/platform evidence. Android and graphical device acceptance remain
open rather than inferred from source tests.

## Round 42 — bounded Polish/Korean source discovery

2026-10-12: [Round 42](ROUND-0.12-42.md) executes a three-query public metadata
pilot against the exact six weak pl/ko cells. Pair archives and broad repository
language tags remain distinct; known family warnings preserve meaningful new
versions/subsets and unknown evidence stays visible. Three primary-source
follow-ups establish no new sampling-ready human-only source. No catalogue
admission, corpus payload, app/UI change or generated/rewritten card text occurs.
The owner's explicit no-AI-card-text rule also covers examples and translations
prepared for later human review. Replayable sanitized metadata, tests and honest
negative-result limitations accompany the source; no exhaustive-search claim.

## Round 43 — atomic learning writes

2026-10-12: owner asked to continue Ikna/MVS/Vespian while away and explicitly
excluded Catalogue v2 work. [Round 43](ROUND-0.12-43.md) reproduces and fixes
partial answer/Undo writes using one database transaction per operation, preserving
existing learning rules and review history. Fault/cancellation/reopen/retry tests
and full JVM checks pass. Android/Windows device evidence remains open. Statistics
content is a separately requested next application stage.

## Round 44 — meaningful retained-journal statistics

2026-10-12: the owner requested replacement of statistics content.
[Round 44](ROUND-0.12-44.md) shows small 7/30-day factual counts and optional
concrete-card observations, replacing attendance/best-hour/forecast emphasis in
the active renderer. Identity, unknown snapshots, Undo, imports, local-time/DST
bounds and missing content are explicit; no session-time/proficiency inference
or new tracking is added. Loading failures have retry UI rather than false zero
history. Catalogue/discovery remains unchanged; device acceptance remains open.

## Round 45 — atomic journal restore and visible failure

2026-10-12: [Round 45](ROUND-0.12-45.md) reproduces partial restore/replay writes
and false successful desktop bundle reporting, then places journal import and
all derived rebuilding in one cancellation-aware database transaction. Errors
are visible and restore controls recover. This does not promise whole-bundle
rollback across separately imported decks/settings. Platform acceptance remains
open; Catalogue/discovery is unchanged.

## Round 46 — safe backup publication

2026-10-12: [Round 46](ROUND-0.12-46.md) reproduces damage to an existing backup
on mid-write failure and hidden deck-export errors/cancellation. File publication
is a separate reliability stage from Round45 database restore atomicity. Preserve
the old destination until a completed replacement can be published; retain the
existing format and empty-deck behavior, and report save failures accurately.

## Round 47 — visible bundle component failures

2026-10-12: [Round 47](ROUND-0.12-47.md) continues the restore-error work by
preserving operational deck/settings exceptions instead of returning a false
successful bundle result. Deliberate blank/synthetic/compatibility skips and the
existing parser policy remain intact. This does not add whole-bundle rollback.
