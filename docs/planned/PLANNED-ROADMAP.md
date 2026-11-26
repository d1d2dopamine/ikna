# Planned Roadmap (Open Non-Obsolete Tracks)

This document tracks the open, non-obsolete product and engineering initiatives
that remain after the current UI, sync, local profile, and CI-speedup work.
Items listed here are planned future tracks rather than blocking release gates
for the current cycle unless explicitly promoted into an active cycle plan.

## 1. Major Refactor & Oversized Files

Two core Kotlin files have grown well past comfortable maintenance boundaries
and are scheduled to be split cleanly as part of a dedicated major refactor
cycle (see [REFACTOR.md](REFACTOR.md) and the full scientific/engineering audit
in [ENGINE-CORE-REFACTOR-AUDIT.md](ENGINE-CORE-REFACTOR-AUDIT.md)):

- `shared/src/jvmShared/kotlin/dev/ikna/data/repo/LearningRepository.kt`
  (~1,941 lines) — split into focused responsibilities (`DailyPlanService`,
  `ReviewAnswerService`, `BrowseService`, `StudyTelemetryService`, plus a
  unified `JournalReplayEngine`).
- `app/src/main/java/dev/ikna/ui/settings/SettingsScreen.kt` (~1,600+ lines) —
  extract individual settings sections into self-contained composables sharing
  the common `SettingsChrome` and panel primitives with Desktop.

Goal of the major refactor: eliminate accumulated edge-case bugs, simplify
cross-platform UI parity between Android and Desktop, and establish clean,
narrow module boundaries before any native-core work.

## 2. Deferred Rust Compute Core (`ikna-core`)

Tracked in [RUST-CORE-PLAN.md](RUST-CORE-PLAN.md).

- **Scope:** Pure-compute engine only (FSRS-5 / optimizer, grading, due-queue
  scoring, delta-sync hashing/merging, bulk deck/Anki parsing). Room/SQLite,
  Compose UI, Android/Desktop OS integrations, and TLS sockets stay in Kotlin.
- **Timing:** Explicitly deferred until the Major Refactor above is complete so
  that FFI boundaries are narrow, stable, and do not slow down day-to-day
  releases.

## 3. Voice / TTS Progression

Tracked in [VOICE.md](VOICE.md).

- Move Android local speech/TTS out of beta once voice-pack diagnostics,
  fallback messaging, and per-language speech rate polish are verified.
- Evaluate optional Desktop offline TTS support (keeping strict zero-cloud,
  user-supplied-model guarantees from [PRIVACY.md](../PRIVACY.md)).

## 4. Catalogue v2 Replenishment & Freeze

Tracked in [CATALOGUE-V2.md](CATALOGUE-V2.md), [CORPUS-CANDIDATES-0.12.md](CORPUS-CANDIDATES-0.12.md),
and [ROUND-0.12-19.md](ROUND-0.12-19.md).

- Continue staged Everyday/Knowledge corpus replenishment and census verification
  until all target language pairs pass the readiness gates.
- Freeze and ship the final Catalogue v2 pack once runtime import, installed
  size, and publication checks are signed off.
