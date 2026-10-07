# Documentation map

What each document owns, where the current plans live, and what must be
re-checked rather than trusted. Maintained contracts and runbooks use English;
dated historical records retain their original language, including Russian.
The entry documents are linked from the README in both interface languages. When a
document is added, renamed or its ownership moves, update this map in the same
change.

## Entry points

- [../README.md](../README.md) — product overview, download links, build
  pointers; mirrors in Russian in the second half. Owns the release download
  file names and the interface-language list.
- [../CONTRIBUTING.md](../CONTRIBUTING.md) — contributor entry point. Owns the
  check commands, the build/toolchain pins summary and the active-plan marker
  (`ikna-active-plan`). The linked plan, not this map, decides the cycle.
- [../AGENTS.md](../AGENTS.md) — AI-contributor entry point and repository
  invariants; links the two source-controlled skills.
- [../PRIVACY.md](../PRIVACY.md) — network behaviour and data promises.
- [../CHANGELOG.md](../CHANGELOG.md) — release history.

## Active cycle (0.12)

- [ROUND-0.12-10.md](ROUND-0.12-10.md) — last bounded selection correction,
  Japanese boundary evidence, diverse alternate ordering, one saved-pool workflow
  and full selection handoff. Owner review of all languages/cards is not required.
- [ROUND-0.12-09.md](ROUND-0.12-09.md) — saved real Everyday preview audit,
  preliminary material findings and offline card review. Input-bound machine
  report, manual notes and viewer: [everyday-round09](evidence/everyday-round09/).
- [ROUND-0.12-08.md](ROUND-0.12-08.md) — admitted Everyday selection, input
  preservation/reproducible preview fixes, next single workflow and owner window
  acceptance. [everyday-round08.json](evidence/everyday-round08.json) records the
  public artifact checkpoint and local verification limits.
- [ROUND-0.12-07.md](ROUND-0.12-07.md) — owner confirms round-06 artifacts
  persist; pinned first-picture layout correction and bounded DEV trace; owner
  subsequently reports it fixed. Input/source hashes: [windows-window-round07.json](evidence/windows-window-round07.json).
- [ROUND-0.12-06.md](ROUND-0.12-06.md) — window bounds/restore/frame-pacing
  repairs, pinned upstream research and integration with round-05 Catalogue.
- [ROUND-0.12-05-GLM.md](ROUND-0.12-05-GLM.md) — reviewed GLM observations;
  unsupported causal conclusions corrected, interactive checks still open.
- [PLAN-0.12.md](PLAN-0.12.md) — the active working plan. Owns cycle
  decisions, the 50/50 allocation and current status decisions. Linked from
  CONTRIBUTING; the planning cycle is independent of the version in build files.
- [modern_PLAN-0.12.md](modern_PLAN-0.12.md) — the 0.12 application-lane
  inventory (source-reviewed task list).
- [ROUND-0.12-01.md](ROUND-0.12-01.md) — round 01 record: repairs, evidence,
  remaining platform smoke.
- [EVERYDAY-POOL.md](EVERYDAY-POOL.md) — admitted Tatoeba-only Part 7 pool
  runbook: exact input pins, full pool retention and explicit artifact reuse.
- [ROUND-0.12-02.md](ROUND-0.12-02.md) — Catalogue tooling/application
  evidence round 02 record and reviewed GLM integration.
- [ROUND-0.12-02-GLM.md](ROUND-0.12-02-GLM.md) — documentation/audit round 02
  record (this round's changes, checks and left-over findings).
- [ROUND-0.12-03.md](ROUND-0.12-03.md) — visual/UX changes, reviewed Codex/GLM
  integration, source verification and remaining runtime acceptance.
- [ROUND-0.12-03-GLM.md](ROUND-0.12-03-GLM.md) — GLM's original source work and
  reported PC attempts, with reviewer corrections and evidence limitations.
- [ROUND-0.12-04.md](ROUND-0.12-04.md) — Windows Hot Reload recompiler diagnosis,
  pinned source evidence, tooling repair and remaining native/runtime acceptance.
- [ROUND-0.12-05.md](ROUND-0.12-05.md) — existing World run diagnosis, reusable
  evidence/offline merge repair, scoped tests and the parallel GLM handoff.
- [evidence/world-provenance-run-35009216395.json](evidence/world-provenance-run-35009216395.json)
  — read-only API checkpoint with exact job/step outcomes, missing data and
  retrieved-source hashes; not a substitute for the unavailable full run report.

## Contracts — each document owns its area

| Document | Owns |
| --- | --- |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Layers, learning-engine seams, data-safety rules, restore/replay, Developer isolation. |
| [DESIGN.md](DESIGN.md) | Product behaviour and the visual language: palettes (twelve, two lightings), motion, typography. |
| [LEARNING-ENGINE.md](LEARNING-ENGINE.md) | Scheduler/Governor/Target-Context-Transfer/Grading responsibility split. |
| [SCIENCE.md](SCIENCE.md) | Evidence labels and what may be claimed as research. |
| [GOVERNOR.md](GOVERNOR.md) | Load control: numbers, gates, what is not user-configurable. |
| [GRADING.md](GRADING.md) · [GRADING-IMPLEMENTATION.md](GRADING-IMPLEMENTATION.md) | Grading contract and its CI/implementation hand-off. |
| [FSRS-OPTIMIZER.md](FSRS-OPTIMIZER.md) · [FSRS-OPTIMIZER-INTEGRATION.md](FSRS-OPTIMIZER-INTEGRATION.md) | Local FSRS fitting: science and integration contract. |
| [CATALOGUE-V2.md](CATALOGUE-V2.md) | Catalogue v2 identity, contexts, pack format, readiness gates. Owns the meaning of the generated `CATALOGUE-V2-READINESS.md`/`.json` (produced by the census workflow from `tools/catalog/readiness_audit.py`; not source files). |
| [SOURCES.md](SOURCES.md) | Source registration, licences, provenance requirements. |
| [CORPORA-0.11.md](CORPORA-0.11.md) | The fixed 0.11 corpus scope and admission rules; inherited contract until a scoped task updates it. |
| [MORPHOLOGY.md](MORPHOLOGY.md) | Offline build enrichment rules; unresolved-token preference. |
| [PHONETICS.md](PHONETICS.md) | IPA/transcription pipeline contracts. |
| [DECKS.md](DECKS.md) | User-made deck format and the AI-prompt contract. |
| [ANKI.md](ANKI.md) | Anki bridge: what imports, what is rejected, size limits. |
| [DESKTOP.md](DESKTOP.md) | Desktop platform contracts: packaging, installer, AppImage, window state, platform omissions. Its port-time measurements are labelled historical. |
| [HOT-RELOAD.md](HOT-RELOAD.md) | The fast desktop UI loop: `dev-hot-reload.cmd`, isolated developer profile, toolchain pins. |
| [UPDATES.md](UPDATES.md) | Release checks and update behaviour. |
| [VERSIONS.md](VERSIONS.md) | Version/epoch scheme, tag rules, where versions live. |
| [KEYSTORE.md](KEYSTORE.md) | Signing identity: what exists, what never leaves source control. |
| [DEVELOPER-MODE.md](DEVELOPER-MODE.md) | Developer Sandbox isolation contract. |
| [THIRD-PARTY-FONTS.md](THIRD-PARTY-FONTS.md) | Bundled/importable font licensing. |
| [VOICE.md](VOICE.md) | Speech runtime: models the user supplies, what the app never downloads. |
| [UNSCHEDULED.md](UNSCHEDULED.md) | Optional ideas, explicitly not commitments. |
| [REFACTOR.md](REFACTOR.md) | The planned split of two oversized files. |

## Historical evidence (do not carry status forward from these)

- [PLAN-0.11.md](PLAN-0.11.md), [ROADMAP-0.11.md](ROADMAP-0.11.md),
  [modern_PLAN-0.11.md](modern_PLAN-0.11.md) — the shipped 0.11 cycle.
- [PARTS-5-10-RUNBOOK.md](PARTS-5-10-RUNBOOK.md),
  [PARTS-6-9-DECISION-RECORD.md](PARTS-6-9-DECISION-RECORD.md),
  [PART-9-FULL-PROVENANCE.md](PART-9-FULL-PROVENANCE.md) — 0.11 corpus
  evidence run and admission decisions (inherited decision records).
- [test-cli.md](test-cli.md) — dated CLI test report (includes the
  memory-constraint evidence CONTRIBUTING cites).
- [ai-audits.md](ai-audits.md), [rele.md](rele.md) — dated read-only audits
  and research notes.

## Facts that require regular re-verification

Never trust these from memory or from another document; read the owner:

- Current application version and tag agreement — [VERSIONS.md](VERSIONS.md)
  against `app/build.gradle.kts` (`appVersionName`), `desktop/build.gradle.kts`
  and the release workflow.
- Release asset file names — README download table against
  `release.yml`/`build.yml` outputs.
- Check commands and toolchain pins — CONTRIBUTING against the scripts under
  `tools/` and `.github/workflows/`.
- Interface-language list and translation-table size — README/DESKTOP against
  `shared/src/jvmShared/kotlin/dev/ikna/ui/text/Strings*.kt`.
- Active plan status — [PLAN-0.12.md](PLAN-0.12.md) and
  [modern_PLAN-0.12.md](modern_PLAN-0.12.md) against the actual tree; round
  records state what was actually verified.
- DESKTOP.md counts labelled "at port time" — historical by intent; do not
  refresh them as if they were current.
- This map itself — whenever documents are added, renamed or re-owned.

- [Round 10 fix 1](ROUND-0.12-10-fix1.md): canonical occurrence failure and corrected automatic handoff.
