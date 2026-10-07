# 0.12 application working plan

Active application lane of [PLAN-0.12.md](PLAN-0.12.md), reviewed against the
supplied source on 2026-10-05. The shipped 0.11 application and its dated test
evidence are the baseline. [modern_PLAN-0.11.md](modern_PLAN-0.11.md) preserves
the earlier inventory; its checkboxes do not prove that this final tree was
built or exercised on a real platform.

Use three separate statuses: **implemented**, **verified** (name the evidence),
and **open**. A source contract establishes source wiring, not runtime behaviour.
Allocation remains approximately 50% application / 50% Catalogue v2 across
batches; this first owner-selected batch concentrates on small defects.

## Source review and remaining work

| Area / inherited track | Implemented in the baseline | Open acceptance or implementation |
| --- | --- | --- |
| Stabilization (A) | One Android/shared/desktop source baseline; signing assets preserved; DEV data/settings separated; product gates bypassed with production verdicts retained | Final cross-platform builds; full DEV entry-path regressions and real smoke pass |
| Developer Sandbox (B) | Six deterministic basic scenarios; persistent DEV marker; isolated reseed/profile settings and developer diagnostics | Real switch/reseed isolation check; backlog, low-accuracy/activity, overheating, forgetting, optimizer-ready and time-of-day scenarios; full gate diagnostics |
| Browse (C) | Vertical feed, complete context/meaning/source blocks, target highlighting, optional transcription, viewport exposure accounting, shared 640dp measure and typography | Long/CJK contexts, transcription wrapping, large fonts, narrow Android layouts; desktop keyboard/wheel/touch and source-link behaviour |
| Desktop (D) | Floating geometry retained; fullscreen restore; non-draggable maximized chrome; off-screen recovery; atomic bounds/native restore/VSync; round-07 first-picture layout/DEV trace; owner reports the artifact fixed | Real mixed-DPI multi-monitor/disconnect and packaged tests; broader transition/input/tray/close matrix beyond the brief owner acceptance |
| Cleanup (E–F) | Initial private-helper cleanup; this round removes the live Android palette-grid duplicate in favour of the existing shared grid | Repeatable caller/manifest/resource/dependency inventory; deprecated API review and packaging validation before further removals |
| Usability (G) | Disabled-action explanations, Rare subgroups, destructive scope, shared Signal Frame, actual keyboard hints and focus treatment | Broader semantic/wording audit and smallest/large-font layouts; palette selection semantics repaired in this round |
| Documentation (H) | Contributor guide, active cycle owner, historical decision/evidence records; current font/appearance/language corrections in this round | Broader documentation contract and volatile-fact audit; do not call the whole track complete |
| AI contributors (I) | Root AGENTS, common skill, separately activated owner skill, links and executable preflight/ZIP manifest checks | Maintain guidance with owning contracts; no pending initial skill split |
| Visual character (J) | Shared phase clock, nested frame ownership, compact dash rhythm, reduced motion, ambient memory strips, angular/rounded variants and requested rounded glyph/progress/seal follow-ups | The reported lighting-switch frame defect is reopened; verify this repair on Windows. Ambient density still needs phone/desktop review |
| Refactors (K) | Existing public facades and ownership retained | LearningRepository and full Settings splits remain deferred pending a separately scoped decision |
| Fast development loop (L) | Pinned Hot Reload launcher, isolated home/DEV profile, external caches/logs, Windows child-compiler bridge, daemon/file watching and visible child diagnostics | Real shared-code reload, timing, deliberate compile/runtime failures and full-ZIP replacement; no tooling upgrade assumed |

Shared-target presentation audit is **cancelled by the owner**, per
[UNSCHEDULED.md](UNSCHEDULED.md), 2026-10-05. Do not turn the old Track G checkbox
back into an obligation. Global target identity/data invariants still apply.
Context/transfer-ready synthetic scenarios wait for the corresponding history
contracts in the Catalogue/learning lane.

Source/report discrepancy: an earlier test report says the deck-row frame was
removed by owner decision, but the accepted snapshot's `IknaDeckRow` still
contains an Outer Signal Frame, also required by its source contracts. This
round retains that supplied behaviour and documents the actual implementation;
the old statement is not treated as proof that the code was removed.

## First batch — small-defect round

Implementation and local evidence: [ROUND-0.12-01.md](ROUND-0.12-01.md).

- [x] Repair Signal Frame arbitration/input lifetime and add direct hover-policy
  regressions, without changing the accepted dash speed/rhythm or focus policy.
- [x] Resolve authored palette previews from the lighting mode; unify Android
  tiles with desktop's shared grid and expose selection on both click targets.
- [x] Make the Bash-dependent CI self-test report a justified skip without Bash.
- [x] Preserve exact raw UTF-8 bytes for plain/gzip census input and test line
  endings against BUILD verification.
- [x] Synchronize active/historical plan ownership, defect statuses and current
  design facts; document the established local memory limits.
- [ ] Compile the final Android and desktop tree and run shared/application JVM
  regressions in a working toolchain; CI now includes the new shared suites.
- [ ] Confirm DARK → GREY → DARK and DARK → GREY → GREY on the owner's Windows
  application, with animation on/off and both appearance variants.
- [ ] Confirm the shared palette grid and selected semantics on real Android.

## Next scoped batches

Round 02 evidence follow-up, 2026-10-06 (Asia/Yekaterinburg): the existing GitHub
build `37366081452`, commit `985ab34`, passed its Android migration job, whose
steps include application/instrumented APK compilation and emulator validation.
The grading JVM job was cancelled because GitHub could not acquire a hosted runner;
Android, Windows and Linux follow-up jobs were skipped. This is partial
remote evidence tied to that commit, not a successful full build or visual smoke.
Details and unchanged acceptance gaps are in [ROUND-0.12-02.md](ROUND-0.12-02.md).

Available source/tool regressions pass again. No additional Kotlin/UI change was
justified by those checks in round 02. The GLM documentation audit from the
round 01 baseline was reviewed and merged with targeted evidence corrections.
Broader documentation status remains open.

Round 03 Codex source checkpoint, 2026-10-06:

- **Implemented:** effective existing 640dp Browse and 1040dp Settings column
  caps; shared jump-strip centring observes the measured active label and scroll
  extent after language/font/layout changes. Existing fling/reduced-motion rules
  are preserved. Reviewed GLM Search busy protection and Hot Reload path repair
  are integrated; Search's existing 840dp caps are effective too. Backup remains
  unchanged. No further Signal Frame/palette change was justified by review.
- **Verified:** six local Python command exits of 0 (text, localization,
  design/palettes, Hot Reload source wiring and Android DEX names), plus source
  diff/manifest checks. These are not Kotlin compilation or runtime measurements.
- **Open:** Windows Hot Reload after these changes, the original lighting-switch
  sequences, long/CJK Browse and source-link interaction, and Android/shared JVM
  validation and an integrated smoke pass. GLM's PC report states that UI access
  was unavailable and auto-reload did not fire; its focused compile/log claims
  are external reports, with logs absent from the supplied ZIP. Source review
  and integration are complete; runtime acceptance is not.
  See [ROUND-0.12-03.md](ROUND-0.12-03.md).

Round 04 source checkpoint, 2026-10-06: the pinned recompiler called a missing
root `gradlew.bat` even though the external Gradle launch opened the app. Added
the distribution bridge, continuous daemon/file-watching override, visible child
diagnostics and native-output handling. Windows fake-process regression fixtures
are present but cannot run on the current Linux host; source checks alone do not
close Track L. The owner will restart once, prove an actual visible reload and
review the integrated visual repairs. See [ROUND-0.12-04.md](ROUND-0.12-04.md).

Round 05 owner/application checkpoint, 2026-10-06: the owner reports the round-04
check completed and authorizes Catalogue work. A new Windows transition-artifact
report keeps Track D open. The parallel GLM lane takes window transitions,
narrow Browse and focus/keyboard return from the common round-04 ZIP; its later
documentation-only output is now reviewed and integrated. Codex changed no application source in that
Catalogue evidence round. No exhaustive native/platform matrix is inferred from
the owner's brief acceptance message.

Round 06, 2026-10-06: implement one bounds write per Windows edge/corner drag,
restore only after native resize acknowledgement and pinned immediate Direct3D
VSync. Reject GLM's unsupported atomic-frame/Windows-limitation conclusions.
Java headless policy/operation checks pass; Kotlin/Compose compilation and fresh
Windows visual/performance acceptance remain open, as do GLM's Browse/focus
interactive checks. Restart the app process for this startup/native change;
Hot Reload of an existing window is insufficient. Details:
[ROUND-0.12-06.md](ROUND-0.12-06.md).

Round 07, 2026-10-06: owner logs prove the prior Direct3D/VSync startup path ran,
but its visual acceptance failed. Prepare the existing child/scene layout before
the first changed Direct3D picture through a public render-delegate wrapper;
retain native-state invalidation and add bounded DEV recording diagnostics.
Seven Java headless groups pass; Kotlin/Compose compilation and actual Windows
presentation remain open. The reported artifact is not declared fixed until
the owner's check. See [ROUND-0.12-07.md](ROUND-0.12-07.md).

Round 08 owner checkpoint, 2026-10-06: the owner reports the round-07 window
artifact fixed. Its acceptance is closed as owner-reported; no full test matrix,
native log or packaged result was supplied. Preserve the accepted window source.
Catalogue work resumes with the admitted Everyday input/selection handoff and
material review. Details: [ROUND-0.12-08.md](ROUND-0.12-08.md).

Round 09, 2026-10-07: real Everyday preview audit and an offline material viewer
are delivered separately from the app. The accepted window fix, runtime, learner
storage and build versions are preserved. No application build or Windows
interactive test is claimed for this Catalogue-only round. Next Catalogue work
uses retained run 37458524156; see [ROUND-0.12-09.md](ROUND-0.12-09.md).

Round 10, 2026-10-07: final bounded selection work and a saved-pool automatic
quality handoff are delivered. No application/build/learner source is changed.
Owner manual review of all languages/cards is not required. The next checkpoint
is the one quality workflow, then complete selected-material census/storage;
see [ROUND-0.12-10.md](ROUND-0.12-10.md).

1. Close this round's build and real-platform evidence, addressing any resulting
   regression before broad feature work.
2. Resume the Catalogue lane's final admitted-source pool/provenance/selection
   evidence in its dependency order; do not rerun rejected admissions as though
   they were unfinished implementations.
3. Select the next application batch from DEV coverage and Browse/desktop edge
   cases above. Keep large structural refactors separately reviewable.

No release/tag/publication approval is implied by a completed local small-fix
round or by the 0.12 planning label. Build versions remain the last shipped
metadata until the dedicated version step.
