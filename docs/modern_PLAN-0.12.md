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
| Desktop (D) | Floating geometry retained; fullscreen restore; non-draggable maximized chrome; off-screen recovery with valid negative coordinates | Real mixed-DPI multi-monitor/disconnect tests; minimize/restore/fullscreen transitions, minimum window size, input/tray/close ordering |
| Cleanup (E–F) | Initial private-helper cleanup; this round removes the live Android palette-grid duplicate in favour of the existing shared grid | Repeatable caller/manifest/resource/dependency inventory; deprecated API review and packaging validation before further removals |
| Usability (G) | Disabled-action explanations, Rare subgroups, destructive scope, shared Signal Frame, actual keyboard hints and focus treatment | Broader semantic/wording audit and smallest/large-font layouts; palette selection semantics repaired in this round |
| Documentation (H) | Contributor guide, active cycle owner, historical decision/evidence records; current font/appearance/language corrections in this round | Broader documentation contract and volatile-fact audit; do not call the whole track complete |
| AI contributors (I) | Root AGENTS, common skill, separately activated owner skill, links and executable preflight/ZIP manifest checks | Maintain guidance with owning contracts; no pending initial skill split |
| Visual character (J) | Shared phase clock, nested frame ownership, compact dash rhythm, reduced motion, ambient memory strips, angular/rounded variants and requested rounded glyph/progress/seal follow-ups | The reported lighting-switch frame defect is reopened; verify this repair on Windows. Ambient density still needs phone/desktop review |
| Refactors (K) | Existing public facades and ownership retained | LearningRepository and full Settings splits remain deferred pending a separately scoped decision |
| Fast development loop (L) | Pinned Hot Reload launcher, isolated home/DEV profile, external caches/logs and restart/error recovery wiring | Real shared-code reload, timing, deliberate compile/runtime failures and full-ZIP replacement; no tooling upgrade assumed |

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
