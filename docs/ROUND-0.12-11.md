# Round 11 — classic cards and useful DEV tools

Date: 2026-10-07. Application batch authorized by the owner while the corrected
Everyday quality workflow is running. Baseline: complete
`ikna-0.12-press-round-10-fix1.zip`, 601 files,
SHA-256 `0a2cfa1fbf8558701d52047b64938c13c6180e8af92e57aceba541eaa128bee2`.

## Card scope

The sole active presentation is the existing classic one: full context/chunk,
tap to reveal translation/meaning, then the existing side/rating choice. Classic
highlighting, phonetics, source link, first-contact repetition, gesture handling,
grading and FSRS answer/undo path remain. No voice-input exercise is introduced.

Remove active gap/cloze and reverse/production prompts, exercise ladders and
promotion. Delete `LevelPromotion.kt` and its obsolete `LevelPromotionTest.kt`.
Stored levels 1/2 and their review rows are retained for history, export/restore,
replay and historical undo. No database schema migration or history rewrite.
Live queues, due/amnesty/forecast/leeches/active-card counts use level 0. Existing
plans drop retired keys while preserving required/extra boundaries, allowances,
capacity and creation time. Historical deck progress still counts unique targets
actually answered. Anki cloze source parsing remains an import operation.

## DEV scope

A shared panel in Settings -> Rare, available only in DEV on Android and desktop:

- select an installed deck and open the actual cards, Browse, statistics,
  Catalogue or search screen;
- refresh/copy diagnostics: current Governor preview, saved plan reason,
  remaining queue, due/backlog, history and retained-mode counts, override state;
- apply ordinary product restrictions or disable them to test feature access.

The switch is isolated to DEV, read at each operation, and absent from learner
settings exports. REAL never obtains an override. Read-only inspection holds the
repository write lock for a consistent snapshot; it creates no plan, credits or
answers and does not change load settings. The Governor preview uses the stored
load target consistently for activity and capacity; saved-plan decisions can
differ. Opening Browse explicitly checks the ordinary feature path and can create
a plan/settle credits; blockers/errors remain visible. With override enabled and
an empty pending queue, cards can reopen existing scheduled classic cards without
adding a new daily obligation. Rating them records ordinary answers inside DEV.
Missing content/schedules stay ordinary empty states.

Existing seed/reset/profile controls remain. No new synthetic data, scenarios,
window changes, Catalogue source/workflow changes or dependency/version upgrades.

## Verification

| Executed local check | Result | Evidence boundary |
| --- | --- | --- |
| `python3 tools/check_classic_cards.py` | 4 tests pass | Actual current DAO SQL on committed schema 10, mixed classic/retired schedules; not Room code generation |
| `python3 tools/check_design_parity.py` | 39 tests pass | Source/UI route and profile/inspection contracts; not rendered interaction |
| `python3 tools/check_grading.py` | 10 tests pass | Existing grading/schema/undo/export structural and SQLite checks |
| `python3 tools/check_optimizer.py` | 9 tests pass | Existing optimizer/source contracts |
| `python3 tools/check_text.py` | Pass | UTF-8/NFC text and locale table hygiene |
| `python3 tools/check_localization.py` | Pass | 7 languages, 732 keys each, tokens/fallback/selectors |
| `git diff --check` and exact baseline review | Pass | Current changes compared to supplied ZIP; older accepted dirty checkout changes retained |

Updated classic presentation/target/import-shape and DEV access unit tests; added
`ClassicPlanTest`. Five real Room/repository regressions were added to
`DeveloperSandboxIntegrationTest`: policy toggle, side-effect-free inspection,
retired plan/materialization, classic answer/undo without promotion, forced access
without changing the daily obligation. These Kotlin tests **were not executed**:
Gradle/Kotlin compiler and the Android/Compose build toolchain are unavailable
locally. Source and SQLite checks do not establish successful compilation,
KSP generation, Android runtime behavior or packaged Windows acceptance.

Machine-readable evidence: [application-round11.json](evidence/application-round11.json).
The full delivery ZIP is manifest/hash/integrity verified against the input source
plus explicitly declared additions and the two requested deletions. Build metadata
remains 0.11.0 press, Room 10; this is work for the 0.12 planning cycle.

## Short owner check

After replacing the source, fully stop the existing hot-run process and restart
with the usual launcher once. This round changes repository/DAO code and controller
state; reloading only a currently open card is insufficient acceptance evidence.

1. Open a classic session: full text -> tap -> meaning -> the existing answer
   gesture/controls. A learned card must never switch to a gap/reverse exercise.
2. In DEV -> Settings -> Rare select an installed deck, try the real feature
   buttons, and refresh/copy diagnostics. Refresh alone must not create a plan.
3. Turn ordinary restrictions on: Browse should explain its actual blockers when
   present. Turn off: product blockers can be bypassed while technical failures
   remain errors. Return to REAL through the existing restart control; DEV tools
   and overrides must be absent and normal learner history retained.

No additional long workflow is needed for this application round. Review the
already running Catalogue quality report when it finishes, then continue saved
selected-material census/content freeze and storage/client acceptance.
