# Documentation map

Clean directory layout for Ikna's documentation. Maintained contracts and
runbooks use English; dated historical records retain their original language.

## Folder layout

- **`docs/` (root)** — entry points, active cycle plan, release notes, version
  registry, and CI-pinned guides.
- **`docs/contracts/`** — active technical contracts & architecture specs that
  govern current code.
- **`docs/planned/`** — open, non-obsolete future tracks, deferred plans, and
  ongoing corpus/classifier research.
- **`docs/completed/`** — completed 0.11 & 0.12 round logs (`ROUND-0.12-*`),
  shipped 0.11 plans, and historical audits/evidence notes (archive; do not
  carry status forward from these).
- **`docs/evidence/`** — machine-generated census/selection/verification artifacts.
- **`docs/assets/`** — brand and installer artwork.

---

## Entry points (repo root & `docs/`)

- [../README.md](../README.md) — product overview, download table, build pointers (EN + RU).
- [../CONTRIBUTING.md](../CONTRIBUTING.md) — contributor check commands and active-plan marker.
- [../AGENTS.md](../AGENTS.md) — AI-contributor entry point and repository invariants.
- [../PRIVACY.md](../PRIVACY.md) — network behaviour, Android permissions (including optional `CAMERA`), and data promises.
- [../CHANGELOG.md](../CHANGELOG.md) — release history.
- [ARCHITECTURE.md](ARCHITECTURE.md) — layers, learning-engine seams, data-safety rules, Developer isolation.
- [DESIGN.md](DESIGN.md) — product behaviour and visual language (12 palettes, motion, typography).
- [PLAN-0.12.md](PLAN-0.12.md) & [modern_PLAN-0.12.md](modern_PLAN-0.12.md) — active 0.12 working cycle.
- [RELEASE-NOTES-0.14.0.md](RELEASE-NOTES-0.14.0.md) · [RELEASE-NOTES-0.13.0.md](RELEASE-NOTES-0.13.0.md) · [RELEASE-NOTES-0.12.0.md](RELEASE-NOTES-0.12.0.md) — GitHub release notes for `v0.14.0-press`, `v0.13.0-press` and `v0.12.0-press`.
- [VERSIONS.md](VERSIONS.md) — version/epoch scheme and shipped release registry (`0.10`–`0.14`).
- [HOT-RELOAD.md](HOT-RELOAD.md) — desktop hot-reload toolchain pins.
- [IKNA-MCP.md](IKNA-MCP.md) — local MCP deck-import guide.

---

## Planned / open tracks (`docs/planned/`)

| Document | Owns |
| --- | --- |
| [planned/PLANNED-ROADMAP.md](planned/PLANNED-ROADMAP.md) | Consolidated open post-0.12 roadmap: major refactor, deferred Rust core, Voice/TTS, Catalogue v2. |
| [planned/ENGINE-CORE-REFACTOR-AUDIT.md](planned/ENGINE-CORE-REFACTOR-AUDIT.md) | Deep engineering & scientific audit of FSRS-6, optimizer, `riar-manage`, grading, component memory, and `LearningRepository` refactor plan. |
| [planned/REFACTOR.md](planned/REFACTOR.md) | Planned split of oversized `LearningRepository.kt` and `SettingsScreen.kt`. |
| [planned/RUST-CORE-PLAN.md](planned/RUST-CORE-PLAN.md) | Deferred optional Rust compute core (`ikna-core`), scheduled after the major refactor. |
| [planned/UNSCHEDULED.md](planned/UNSCHEDULED.md) | Optional ideas, explicitly not commitments. |
| [planned/REMOTE-SYNC-DESIGN.md](planned/REMOTE-SYNC-DESIGN.md) | Optional future encrypted remote sync architecture. |
| [planned/CORPUS-CANDIDATES-0.12.md](planned/CORPUS-CANDIDATES-0.12.md) · [planned/LARGE-CORPUS-CANDIDATES-0.12.md](planned/LARGE-CORPUS-CANDIDATES-0.12.md) | Catalogue v2 corpus replenishment candidates and licence review. |
| [planned/EVERYDAY-POOL.md](planned/EVERYDAY-POOL.md) · [planned/SELECTION-ENGINE-PLAN-0.12.md](planned/SELECTION-ENGINE-PLAN-0.12.md) | Catalogue v2 pool runbook and selection engine plan. |
| [planned/CLASSIFIER-FEASIBILITY-0.12.md](planned/CLASSIFIER-FEASIBILITY-0.12.md) · [planned/CLASSIFIER-ARCHIVE-AUDIT-0.12.md](planned/CLASSIFIER-ARCHIVE-AUDIT-0.12.md) | Classifier research and archive audit. |

---

## Active technical contracts (`docs/contracts/`)

| Document | Owns |
| --- | --- |
| [contracts/LEARNING-ENGINE.md](contracts/LEARNING-ENGINE.md) | Scheduler / Governor / Target-Context-Transfer / Grading split. |
| [contracts/SCIENCE.md](contracts/SCIENCE.md) | Evidence labels and research claims policy. |
| [contracts/GOVERNOR.md](contracts/GOVERNOR.md) | `riar-manage` load control numbers and gates. |
| [contracts/GRADING.md](contracts/GRADING.md) · [contracts/GRADING-IMPLEMENTATION.md](contracts/GRADING-IMPLEMENTATION.md) | Derived grading contract and CI hand-off. |
| [contracts/FSRS-OPTIMIZER.md](contracts/FSRS-OPTIMIZER.md) · [contracts/FSRS-OPTIMIZER-INTEGRATION.md](contracts/FSRS-OPTIMIZER-INTEGRATION.md) | Local FSRS fitting science and integration. |
| [contracts/SYNC-0.12.md](contracts/SYNC-0.12.md) · [contracts/SYNC-PROFILES-0.12.md](contracts/SYNC-PROFILES-0.12.md) · [contracts/SYNC-INCREMENTAL-0.12.md](contracts/SYNC-INCREMENTAL-0.12.md) | Local paired LAN sync, profile adoption, and incremental journal contracts. |
| [contracts/CATALOGUE-V2.md](contracts/CATALOGUE-V2.md) · [contracts/SOURCES.md](contracts/SOURCES.md) · [contracts/CORPORA-0.11.md](contracts/CORPORA-0.11.md) | Catalogue v2 identity, pack format, source licensing, and corpus admission rules. |
| [contracts/MORPHOLOGY.md](contracts/MORPHOLOGY.md) · [contracts/PHONETICS.md](contracts/PHONETICS.md) | Offline morphology enrichment and IPA/phonetics pipeline. |
| [contracts/DECKS.md](contracts/DECKS.md) · [contracts/ANKI.md](contracts/ANKI.md) | User-made deck format, AI prompt contract, and Anki `.apkg` bridge. |
| [contracts/DESKTOP.md](contracts/DESKTOP.md) · [contracts/UPDATES.md](contracts/UPDATES.md) · [contracts/KEYSTORE.md](contracts/KEYSTORE.md) | Desktop packaging, update checks, and signing identity. |
| [contracts/DEVELOPER-MODE.md](contracts/DEVELOPER-MODE.md) · [contracts/VOICE.md](contracts/VOICE.md) · [contracts/THIRD-PARTY-FONTS.md](contracts/THIRD-PARTY-FONTS.md) | Developer Sandbox isolation, offline speech runtime, and font licensing. |

---

## Completed / archived (`docs/completed/`)

Contains all finished 0.12 application/catalogue round records (`ROUND-0.12-01`
through `ROUND-0.12-64`), the shipped 0.11 cycle (`PLAN-0.11.md`,
`ROADMAP-0.11.md`, `modern_PLAN-0.11.md`), 0.11 corpus decision records
(`PARTS-5-10-RUNBOOK.md`, `PARTS-6-9-DECISION-RECORD.md`,
`PART-9-FULL-PROVENANCE.md`), and dated historical audits (`ai-audits.md`,
`rele.md`, `test-cli.md`).
