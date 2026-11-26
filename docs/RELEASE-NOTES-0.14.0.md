# ikna 0.14.0 press

This release fixes critical bugs and scientific errors in Ikna's learning engine and core scheduler, alongside a redesigned local Profile and updated repository documentation.

## Learning engine and core fixes

- **No new cards earned from failed reviews:** `earnNewIfDue()` now counts only recalled ordinary answers (`GOOD` / `EASY`, excluding optional hard sessions) instead of total review presses. Repeated `AGAIN` lapses can no longer reward the learner with newly introduced cards.
- **Late-night 00:00 to 03:59 rollover fix:** `Scheduler.dueAt()` now enforces a 4-hour minimum gap before snapping a 1-day interval to the next study day via DST-safe `DayBoundary.nextDayStart()`. Studying at 03:30 no longer schedules a 1-day card 30 minutes later at 04:00.
- **Post-reset history isolation:** `historyBoundary` (`afterLocalId`) is now enforced across live governor signals, daily plan completion, median answer time, and `LocalOptimizer`. Archived pre-reset reviews stay in the append-only journal without leaking into a fresh profile.
- **Atomic plan and suppression writes:** `ensureDailyPlanLocked()` and `markWrongLocked()` now commit multi-table writes inside single Room transactions. `remainingByDeck()` filters suppressed chunks consistently, and `SessionBuilder` excludes suppressed chunks before SQL `LIMIT` so hidden cards never consume daily capacity.
- **Clean overheating baseline:** `GovernorSignalCollector.isOverheated()` excludes the candidate day itself from its own median volume and average accuracy baseline.

## Scientific scheduling upgrades

- **Deterministic FSRS-6 interval fuzzing:** Intervals of 2.5 days or longer now use FSRS-6 continuous piecewise-linear fuzzing seeded deterministically from `(chunkId, level, reps, lapses)`. This spreads review cohorts across neighbouring days to prevent review waves while keeping `HARD <= GOOD <= EASY` monotonic and preserving exact live-vs-replay equivalence.
- **Warm-up and interleaved sessions:** `SessionBuilder` opens sessions with up to two familiar review cards when available, then weaves newly introduced cards evenly across the remaining reviews instead of front-loading all new cards in one block.
- **Spacing-aware, language-isolated component memory:** `ComponentRepository` and `ChunkSelector` isolate homographs across languages (for example, English `gift` vs German `Gift`) and scale lexical stability growth by real elapsed time between exposures so massed same-session repetitions cannot inflate stability to hundreds of days.
- **Cleaner local FSRS optimizer:** `FsrsOptimizer` retains complete whole-card histories under `MAX_ANSWERS` instead of truncating mature cards mid-life, keeps unscored short-term weights (`w[17..19]`) pinned to published FSRS-6 defaults, and adds coupled-pair diagonal search probes.
- **Contextual diversity for mature cards:** `ContextPolicy` keeps the initial acquisition sentence fixed while a card is young (`stability < 21` days) and deterministically rotates across distinct Catalogue v2 `chunk_contexts` once mature.
- **Honest late-night rule documentation:** Removed the inverted sleep-consolidation claim from code comments and `docs/contracts/GOVERNOR.md`, reframing the late-night cutoff accurately as a sleep-hygiene and fatigue policy.
- **Audit and regression tests:** Added full engineering and scientific audit documentation (`docs/planned/ENGINE-CORE-REFACTOR-AUDIT.md`), extracted `GovernorSignalCollector`, and added unit and Room integration tests (`EngineRefactorAuditTest`, `EngineCoreAuditIntegrationTest`).

## Profile and documentation

- **Full local Profile:** Added a dedicated Profile icon, customizable cover banner hero with overlapping circular avatar, identity meta-line, clean `Profile` / `Analytics` tabs, and live learner insights (languages, recently practiced phrases, study rhythm, and milestones).
- **Updated README:** Rewrote `README.md` for everyday users and split the Russian guide into `README.ru.md`.

## Downloads

| Platform | File |
| --- | --- |
| Android 10+ (arm64) | `ikna-android-arm64.apk` |
| Android 10+ (32-bit ARM) | `ikna-android-legacy32.apk` |
| Windows x64 (Installer) | `ikna-windows-x64-setup.exe` |
| Windows x64 (Portable) | `ikna-windows-x64-portable.zip` |
| Linux x86_64 | `ikna-linux-x86_64.AppImage` |
