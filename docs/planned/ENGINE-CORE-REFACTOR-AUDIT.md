# Ikna Learning Engine & Core Refactor Audit

**Date:** 2026-11-26  
**Scope:** `domain/fsrs`, `domain/governor`, `domain/grading`, `domain/session`, `data/repo/LearningRepository.kt`, `data/repo/ComponentRepository.kt`, `data/repo/LocalOptimizer.kt`, `data/repo/RestoreRepository.kt`, `data/repo/SchedulerMigration.kt`, `data/db/Daos.kt`  
**Status:** Audit complete; ready for architectural review prior to implementation  
**Related:** [LEARNING-ENGINE.md](../contracts/LEARNING-ENGINE.md) · [SCIENCE.md](../contracts/SCIENCE.md) · [GOVERNOR.md](../contracts/GOVERNOR.md) · [GRADING.md](../contracts/GRADING.md) · [FSRS-OPTIMIZER.md](../contracts/FSRS-OPTIMIZER.md) · [REFACTOR.md](REFACTOR.md)

---

## Executive Summary

Ikna's core learning architecture has strong foundational strengths:

1. **Core FSRS-6 differential equations are mathematically faithful.** Line-by-line verification of `Fsrs.kt` confirms that all 21 default weights, power-law forgetting curve (`decay = -w[20]`), damped/mean-reverting difficulty update, short-term same-day stability (`w[17..19]`), recall growth, and post-lapse stability match the official FSRS-6 specification.
2. **Append-only review history and honesty-first science framing.** `docs/contracts/SCIENCE.md` explicitly distinguishes *Established*, *Supported*, *Experimental*, and *Product policy*, refuses gamified dopamine narratives, and keeps `Browse` passive exposures strictly isolated from FSRS scheduling.

However, a deep audit of the actual Kotlin implementation (~4,600 lines across domain + repositories) uncovered **8 concrete critical/high engineering bugs**, **5 scientific / anti-pseudoscience defects**, and a **1,941-line monolithic `LearningRepository.kt`** (50% larger than when `REFACTOR.md` first flagged it) that creates transactional hazards and subtle state drift.

---

## Part 1 — Critical & High-Severity Core Engine Bugs

### Bug 1 [CRITICAL] — `AGAIN` ("Don't know") marks a card completed for the day on reload and awards `planCompleted`, `cleanDays`, and Browse credits

- **Where:** `Daos.kt:569-573` (`answeredKeysSince`), `LearningRepository.kt:910, 925, 1687-1693`
- **Mechanism:**
  ```sql
  SELECT DISTINCT chunkId || ':' || level FROM reviews
  WHERE ts >= :from AND purpose IS NULL AND NOT_RETRACTED
  ```
  1. When a learner rates a card `AGAIN` (`rating = 1`), `answerLocked()` inserts the row and schedules `card.dueAt` at least 1 day forward (`Fsrs.intervalDays` floor). The UI re-queues the card in memory for same-session retry.
  2. If the learner switches tabs, rotates the screen, closes the app, or syncs before recalling that card, `buildSession()` filters out every key returned by `answeredKeysSince(startOfDay(now))`. Because `answeredKeysSince` does **not** filter by `rating >= 2` (or require the latest answer of the day to be recalled), the failed card vanishes from today's queue.
  3. Worse, `planCompleted()` uses the exact same `answeredKeysSince()` set. A learner can swipe left (`AGAIN`) on every card in today's plan, close the app, and Ikna will mark `planCompleted = true`, increment `cleanDays` (driving the governor accelerator), unlock *Optional Hard Review*, and credit *Browse* points — for failing every card.
- **Proposed Fix:**
  - Define two distinct queries:
    - `recalledKeysSince(from)` — cards whose **latest** non-retracted ordinary answer today has `rating >= 2` (used for pending-queue removal, `planCompleted`, Browse credit eligibility, and Optional Hard gate).
    - `attemptedKeysSince(from)` — factual count of cards touched today (used only for display telemetry where appropriate).
  - Persist or derive pending same-day retry state from the journal so a failed card always returns until actually recalled (or explicitly skipped/abandoned).

---

### Bug 2 [CRITICAL] — Failing cards repeatedly triggers `earnNewIfDue()` and hands out NEW cards as a reward for failures

- **Where:** `LearningRepository.kt:1493-1522` (`earnNewIfDue`), `LearningRepository.kt:1638-1652` (`bumpDailyStat`)
- **Mechanism:**
  - `bumpDailyStat()` increments `daily_stats.reviewsDone` on **every** answer, including `AGAIN`.
  - `earnNewIfDue()` calculates `beyond = admissionReviewsDone(stat.reviewsDone, now) - obligation` and awards `beyond / 4` brand-new cards whenever `beyond > 0`.
  - Example: today's obligation is `10` cards. The learner misses cards 4 times (`AGAIN`) before finishing those 10 cards. `reviewsDone` reaches `14`, so `beyond = 4`, `earnedNew(4) = 1` — **the governor introduces a brand-new chunk specifically because the learner failed 4 reviews!**
- **Proposed Fix:**
  - Compute `beyond` strictly from **unique recalled cards beyond the required prefix** (e.g., `max(0, uniqueRecalledToday - requiredCount)`), never raw `reviewsDone` inflated by `AGAIN` retries.
  - Require the entire required prefix to be recalled (`requiredComplete == true`) before `earnNewIfDue` can open any earned new card.

---

### Bug 3 [CRITICAL] — `Scheduler.dueAt()` schedules cards due 30 minutes later when answered between 00:00 and 03:59 AM

- **Where:** `Scheduler.kt:164-168` (`dueAt`)
- **Mechanism:**
  ```kotlin
  val target = now + (intervalDays * DAY_MS).roundToLong()
  val dayStart = boundary?.startOfDay(target) ?: return target
  return max(dayStart, now)
  ```
  - Suppose study day starts at `04:00` (`dayStartHour = 4`).
  - A learner studies at **Tuesday 03:30** (which belongs to **Monday's** study day) and receives `intervalDays = 1.0` (e.g., `AGAIN` or short `HARD`).
  - `target = Wednesday 03:30`. `boundary.startOfDay(Wednesday 03:30)` is **Tuesday 04:00** — which is **30 minutes from `now`**!
  - Because `Tuesday 04:00 > Tuesday 03:30`, `max(dayStart, now)` returns **Tuesday 04:00**. Half an hour later the card is already due again as a "next-day" review, collapsing a 24-hour spacing interval into 30 minutes.
  - Across ordinary hours, `startOfDay(now + intervalDays * DAY_MS)` also systematically truncates up to `23h59m` off every interval depending on time of day (e.g. an answer at `03:50` vs `04:10` shifts due dates by a full day).
- **Proposed Fix:**
  - Anchor day-boundary scheduling to the **study day of `now`**, not wall-clock `now + interval`:
    ```kotlin
    val days = intervalDays.roundToLong().coerceAtLeast(1L)
    val nextStudyDate = boundary.date(now).plusDays(days)
    return nextStudyDate.atTime(dayStartHour, 0).atZone(zone).toInstant().toEpochMilli()
    ```
  - This guarantees that an interval of `1` day always lands on the **next** study day (`date(now) + 1`), DST-safely, with zero 30-minute rollover collapse.

---

### Bug 4 [CRITICAL] — `Scheduler.introduce()` computes a component prior that `Scheduler.apply()` immediately throws away, while creating ghost future-due "new" cards

- **Where:** `Scheduler.kt:51-58` (`apply`), `Scheduler.kt:112-143` (`introduce`), `LearningRepository.kt:332-352`
- **Mechanism:**
  1. `introduce()` seeds `stability = baseS * (1 + knownRatio * 2)` and `difficulty = D0 - knownRatio * 1.5`. When `knownRatio >= 0.75`, it also sets `dueAt = dueAt(now, interval)` (several days in the future) while keeping `isNew = true`.
  2. When the learner answers that card for the first time (`card.isNew == true`), `Scheduler.apply()` runs `Fsrs.initial(rating, params)` — **completely discarding the component-seeded stability and difficulty**.
  3. Meanwhile, if the plan is invalidated (`invalidatePlan()`) before that card is answered, the card stays in `cards` with `isNew = true` and `dueAt` 5–7 days in the future: excluded from `unintroducedByFrequency`, excluded from today's `dueCards`, yet counted in `hasScheduledCards`!
- **Scientific flaw as well:** Knowing individual words (`keep`, `eye`, `on`) does not prove recognition of a multi-word idiom (`keep an eye on`) without at least one real retrieval test (Roediger & Karpicke; Dunlosky et al. 2013).
- **Proposed Fix:**
  - Always schedule freshly introduced cards with `dueAt = now` so every newly introduced chunk receives an honest first-contact exposure in today's session.
  - Keep `ChunkSelector`'s component layer strictly as a **candidate ranking signal** (`i+1` selection) — never skip first-contact testing and never write dead stability/difficulty values that `apply()` immediately overwrites. If a component prior is ever tested experimentally in the future, it must be versioned, applied cleanly during the first-answer transition, and validated on held-out log-loss.

---

### Bug 5 [HIGH] — Post-reset `historyBoundaryDao` (`afterLocalId`) is ignored by `RiarManage`, `collectSignals`, `answeredKeysSince`, and `LocalOptimizer`

- **Where:** `LearningRepository.kt:517-545, 910, 1744-1762`, `Daos.kt:522-574`, `LocalOptimizer.kt:72-74`
- **Mechanism:**
  - `resetProgressWithBoundary()` preserves the append-only `reviews` table and records `HistoryBoundaryEntity(afterLocalId = lastId)`.
  - However, `reviewDao.recent()`, `reviewDao.lastReviewTs()`, `reviewDao.total()`, `reviewDao.answeredKeysSince()`, `reviewDao.recentDurations()`, and `reviewDao.since()` have **no `id > :afterLocalId` filter**.
  - Immediately after a user resets progress:
    - `totalReviews > 0`, so `FIRST_RUN` never fires;
    - `lastReviewTs()` returns a pre-reset timestamp, potentially forcing `RETURN_MODE` or `POST_SKIP_WARMUP` on a brand-new reset;
    - `answeredKeysSince()` still contains pre-reset answers from today, hiding newly introduced cards;
    - `accuracyRecent` and `FsrsOptimizer` still consume pre-reset history.
- **Proposed Fix:**
  - Introduce a single active-cutoff helper (`activeHistoryCutoff()`) read consistently by **every** live query in `DailyPlanService`, `ReviewAnswerService`, `StudyTelemetryService`, and `LocalOptimizer`.

---

### Bug 6 [HIGH] — Missing FSRS-6 Interval Fuzzing creates synchronized cohort avalanches

- **Where:** `Fsrs.kt:89-96`, `Scheduler.kt:60-65`
- **Mechanism:**
  - All 6 new cards introduced on Day 1 and answered `GOOD` receive the identical `intervalDays = 2.3065`, snap to the identical study day, and then step forward together (`Day 3 -> Day 10 -> Day 30 -> Day 90`) in lockstep waves.
  - After 3–4 weeks of steady study, these synchronized cohorts collide on the same days, spiking `dueToday` above `capacity`, triggering `NO_HEADROOM` / `BACKLOG_LIMIT` oscillation even when average daily load is sustainable.
- **Proposed Fix:**
  - Add **deterministic, seeded bounded fuzzing** (standard FSRS / Anki fuzz range: `0%` for `interval < 2.5d`, `±15%` for `2.5–7d`, `±10%` for `7–20d`, `±5%` for `>20d`, minimum `1` day) seeded deterministically from `(chunkId, level, reviewTs)` (or least-loaded day load-balancing within the fuzz window during live scheduling, recording the chosen `dueAt` or deterministic seed so replay stays 100% reproducible!).

---

### Bug 7 [HIGH] — `FsrsOptimizer` mid-history truncation corrupts state replay, mixes incompatible materials, and struggles on coupled FSRS-6 valleys

- **Where:** `FsrsOptimizer.kt:216-228, 346-380`
- **Mechanisms:**
  1. **Mid-card history slicing (`histories()`):** Keeping the newest `20,000` raw answers slices in the middle of mature cards' timelines. `logLoss()` then treats the first surviving review of a 2-year-old card as `previous == null` and calls `Fsrs.initial(...)`, corrupting retrievability predictions and biasing fitted weights toward unrealistically high initial stabilities (`w[0..3]`).
  2. **Perturbing unscored short-term weights (`w[17..19]`):** Same-day repeats (`elapsedDays < 1.0`) are never scored in `logLoss()`, yet coordinate descent still probes `w[17..19]`, adding noise without direct supervision.
  3. **Axis-aligned coordinate descent (`MAX_PASSES = 6`):** FSRS-6 weights (`w[8], w[9], w[10]`, `w[4..7]`, `w[20]`) lie along strongly correlated curved valleys; coordinate descent cannot traverse diagonal valleys efficiently and frequently hits parameter bounds (already observed in `docs/contracts/FSRS-OPTIMIZER.md`).
  4. **Single global parameter vector across heterogeneous materials:** Peer-reviewed memory modeling (Sense et al., 2016, `doi:10.1111/tops.12183`; Sense et al., 2018, `doi:10.3389/feduc.2018.00112`) demonstrates that individual forgetting rates are stable over time **within a domain** but differ substantially **across materials** (e.g., Chinese hanzi vs English idioms vs Medical terminology).
- **Proposed Fix:**
  - Truncate by **whole card histories** (newest active cards up to 20k reviews) or preserve pre-cutoff `prevStability`/`prevDifficulty` snapshots from the first retained review instead of re-initializing mature cards via `Fsrs.initial()`.
  - Freeze `w[17..19]` at defaults unless enough same-day observations exist and a dedicated short-term loss is enabled.
  - Upgrade optimization from 6-pass axis-aligned coordinate descent to **analytical backpropagation-through-time (BPTT) with Adam / L-BFGS + L2 prior to `DEFAULT_W`** (deterministic, ~20–50× faster and far better conditioned on coupled parameters), while keeping the strict 30% temporal held-out gate (`logLoss` + Brier score calibration check).
  - Roadmap: allow per-language or per-deck-category parameter fitting once a category crosses `MIN_SCORED_ANSWERS` (falling back smoothly to global/default weights via hierarchical shrinkage).

---

### Bug 8 [HIGH / MEDIUM] — Non-transactional `ensureDailyPlanLocked` / `markWrongLocked`, `remainingByDeck` suppression mismatch, and multilingual `ComponentKey` collision

- **Where:** `LearningRepository.kt:232-367, 1010-1025, 1319-1357`, `ComponentRepository.kt:34, 71-84`, `ChunkSelector.kt:107`
- **Mechanisms:**
  1. **`ensureDailyPlanLocked()` and `markWrongLocked()` are NOT wrapped in `db.inTransaction`:** A process crash or cancellation between `introduce()` (`cardDao.upsertAll`), `governorDao.insert`, `countIntroduced`, and `storePlan()` leaves orphan introduced cards and spent daily budgets with no stored plan.
  2. **`remainingByDeck()` omits `suppressedNow()` filter** (`LearningRepository.kt:1010-1015`), causing deck badges to disagree with `todayStudySummary()` and `buildSession()` whenever a card is marked wrong.
  3. **Suppressed chunks pollute governor inputs & steal capacity slots:** `dueCount()`, `amnestyCount()`, `dueBetween()`, and `SessionBuilder.pickForDay()` count and fetch suppressed chunks *before* SQL `LIMIT`, only filtering them afterward in Kotlin — silently shrinking the session below `capacity`.
  4. **Multilingual `ComponentKey(lemma, pos)` has NO `lang` field:** Identical lemmas across languages (`gift` EN/DE, `die` EN/DE, `pan` EN/ES/PL, `bar`, `no`, `was`) collide in the same `components` table row.
  5. **Massing inflation in `ComponentRepository.estimateStability()`:** Multiplies `stabilityEst` by `1.35..1.80` on **every** exposure regardless of `elapsedDays` — 10 exposures inside 5 minutes inflate stability to 350+ days, contradicting the spacing effect (Cepeda et al., 2006). Live `undoLast` also never reverts component updates.
  6. **`overheating()` biases its own baseline:** `usual = days.map { it.accuracy }.average()` (`LearningRepository.kt:580`) includes `last` (the candidate overheated day itself) in the baseline average, weakening detection on small windows.

---

## Part 2 — Scientific Rigor & Anti-Pseudoscience Audit

Every learning rule in Ikna was checked against peer-reviewed cognitive psychology and spaced-repetition literature retrieved via OpenAlex (`literature-search-openalex`) and arXiv (`literature-search-arxiv`).

| Ikna Mechanism | Current Code / Claim | Peer-Reviewed Evidence Verdict | Required Action |
| --- | --- | --- | --- |
| **FSRS-6 core math** (`Fsrs.kt`) | 21-param DSR model, power-law forgetting curve | **Supported / State-of-the-Art.** Outperforms SM-2/Leitner on large review corpora; power-law forgetting matches multi-timescale retention better than exponential curves (Ye et al., 2022, `doi:10.1145/3534678.3539081`; Cepeda et al., 2006; Sense et al., 2026 `doi:10.1038/s41539-026-00447-1`). | Keep FSRS-6 equations; fix `dueAt` rollover bug and add bounded deterministic fuzzing. |
| **Retrieval practice & passive Browse isolation** (`BrowsePolicy.kt`) | Passive reading never advances FSRS | **Established.** Active retrieval robustly outperforms restudying (Roediger & Karpicke; Dunlosky et al., 2013, `doi:10.1177/1529100612453266`; Donoghue & Hattie, 2021, `doi:10.3389/feduc.2021.581216`). | **Keep strictly.** Browse must remain read-only for FSRS. |
| **Late-night cutoff rationale** (`LearningRepository.kt:627`) | Claims *"overnight consolidation is weaker"* for late-night encoding | **SCIENTIFICALLY INVERTED.** Declarative encoding shortly *before* sleep consistently **improves** consolidation vs daytime wakefulness due to reduced interference and slow-wave sleep replay (Rasch & Born, 2013, `doi:10.1152/physrev.00032.2012`; Payne et al., 2012, `doi:10.1371/journal.pone.0033079`; Feld & Diekelmann, 2015, `doi:10.3389/fpsyg.2015.00622`). Late-night study hurts only via **sleep deprivation / circadian fatigue**, not weak sleep consolidation. | Remove the false consolidation claim from code/docs. Reframe `nightCutoffHour` strictly as an optional **behavioral sleep-hygiene / fatigue product policy** (configurable or quietly toggleable). |
| **Derived Grading via post-reveal total latency** (`DerivedGrading.kt`) | Measures total `shown -> final swipe` AFTER mandatory reveal as memory strength | **CONFOUNDED / PSEUDOSCIENCE RISK.** Latency-based adaptive scheduling research (Sense et al., 2016, `doi:10.1111/tops.12183`; *Optimizing Fact-Learning with a Response-Latency-Based Adaptive System*, 2022, `doi:10.31234/osf.io/chpgv`) relies on **pre-feedback retrieval latency**. Measuring *post-reveal* time confounds recall fluency with reading speed, sentence verification, and TTS audio listening duration — penalizing attentive learners as `HARD`. | Split `ReviewSignalTracker` into **pre-reveal `retrievalLatencyMs`** (`shown -> reveal`) vs **`verificationMs`** (`reveal -> answer`). Discard timing when TTS auto-plays. Keep derived grading **strictly shadow-evaluated** until real-user held-out Brier/log-loss improves over binary FSRS. |
| **Front-loading all new chunks first** (`SessionBuilder.kt:96`) | `reserved + interleave(due + amnesty)` blocks all new items at index `0..k-1` | **SUBOPTIMAL.** Blocking novel items increases proactive/retroactive interference and misses within-session expanding retrieval benefits (Storm, Bjork & Storm, 2010, `doi:10.3758/mc.38.2.244`). For ADHD task initiation, starting with 1–2 familiar warm-up reviews reduces entry friction. | Interleave new chunks evenly among review cards (e.g., 1–2 familiar warm-ups first, then 1 new card every 3–5 reviews) so failed new cards naturally get spaced within-session re-tests. |
| **Component stability growth without spacing** (`ComponentRepository.kt:71`) | Multiplies stability `×1.35..1.8` on every exposure regardless of elapsed time | **PSEUDOSCIENTIFIC.** Ignores the spacing effect (Cepeda et al., 2006, `doi:10.1037/0033-2909.132.3.354`), granting 1-year word stability from 5 minutes of massed repetition, and mixes lemmas across languages. | Add `lang` to `ComponentKey`, gate component stability growth by elapsed spacing (`>= 1 day` or true DSR update), and keep components strictly as a ranking signal (never skipping first-contact testing). |
| **Context diversity & transfer** (`LEARNING-ENGINE.md`) | Documented as `ContextPolicy` / `TransferPolicy`, not yet active in runtime | **Supported (with nuance).** Contextual diversity improves L2 vocabulary learning (Chen, Miller & Ke, 2026, `doi:10.1017/S0142716426100691`; *Studies in Second Language Acquisition*, 2026, `doi:10.1017/s0272263126101818`; Pan & Rickard, 2018, `doi:10.1037/bul0000151`), especially after initial acquisition stabilizes. | Implement conservative `ContextPolicy`: fixed primary context during acquisition (`reps < 3` or `stability < 7d`), verified secondary context from `chunk_contexts` once stable; log `contextId` + `isNovelContext` without modifying FSRS grades. |
| **Governor arbitrary constants** (`GovernorConfig.kt`) | 26 fixed magic constants (`settlingDays = 60`, `costPerNew = 4.0`, etc.) | **Product policy (over-parameterized).** Fixed 60-day lock and static `costPerNew = 4.0` ignore actual retention/review load dynamics and punish fast/experienced learners. | Derive `costPerNew` dynamically from observed 7-day post-introduction review cost (bounded `2.0..7.0`), replace rigid `settlingDays = 60` with an evidence-based stability/accuracy ramp (e.g. 7–14 active days with stable accuracy), and keep all governor rules explicitly labeled as **Product Policy**, never "science". |

---

## Part 3 — Target Core Architecture (Decomposing the 1,941-Line Monolith)

`LearningRepository.kt` currently mixes 7 unrelated responsibilities across 1,941 lines. Refactoring it into focused services (while keeping `LearningRepository` as a thin compatibility facade) follows the invariants in [REFACTOR.md](REFACTOR.md):

```text
                    +-----------------------------+
                    |   LearningRepository        |  (thin facade; owns ONE writeLock: Mutex)
                    +--------------+--------------+
                                   |
        +--------------------------+--------------------------+--------------------------+
        v                          v                          v                          v
+---------------+        +-------------------+      +-------------------+      +-------------------+
| DailyPlan     |        | ReviewAnswer      |      | BrowseService     |      | StudyTelemetry    |
| Service       |        | Service           |      |                   |      | Service           |
| (writeLock +  |        | (writeLock +      |      | (writeLock for    |      | (LOCK-FREE        |
|  1 DB tx)     |        |  1 DB tx)         |      |  exposures)       |      |  read-only)       |
+-------+-------+        +---------+---------+      +-------------------+      +-------------------+
        |                          |
        +------------+-------------+
                     v
        +-----------------------------+
        |   JournalReplayEngine       |  (SINGLE source of truth for Restore,
        |   (pure deterministic)      |   SchedulerMigration, Sync & Sync-Undo)
        +-----------------------------+
                     |
        +------------+----------------------------------------+
        v                        v                            v
  domain/fsrs             domain/governor              domain/grading
  - Fsrs (FSRS-6)         - RiarManage                 - DerivedGrading (pre-reveal RT)
  - Scheduler (+fuzz)     - ChunkSelector (+lang)      - GradingEvaluator (shadow gate)
  - FsrsOptimizer (BPTT)  - SessionBuilder (interleave)
```

### Non-Negotiable Architectural Invariants
1. **One `writeLock` Mutex, injected into `DailyPlanService`, `ReviewAnswerService`, and `BrowseService`.** `StudyTelemetryService` takes **no lock** so UI/widget reads never stall an answer swipe.
2. **Every multi-table mutation runs inside one `db.inTransaction { ... }`** — including `ensureDailyPlanLocked` and `markWrongLocked`.
3. **One `activeHistoryCutoff()` everywhere** — post-reset archived reviews never leak into governor signals, `answeredKeysSince`, `LocalOptimizer`, or stats.
4. **One `JournalReplayEngine`** — live answering, full backup restore, incremental sync replay, scheduler migration, and boundary undo all execute the exact same pure Kotlin state-transition function, guaranteeing zero drift between live state and replayed state.
5. **Pure domain modules stay zero-dependency Kotlin** — enabling fast deterministic JVM simulation tests (100,000-review Monte Carlo stress tests for cohort clumping, accuracy collapse, return-from-absence, and optimizer calibration) and keeping a clean seam for any future optional Rust core (`ikna-core`).

---

## Part 4 — Phased Execution Roadmap

### Phase 1 — Critical Correctness & State-Integrity Fixes (Highest Priority, Zero Schema Changes)
1. Fix `answeredKeysSince` vs `recalledKeysSince` so `AGAIN` never disappears on reload or awards `planCompleted` / Browse credits.
2. Fix `earnNewIfDue` so repeated `AGAIN` answers never earn new cards.
3. Fix `Scheduler.dueAt()` 00:00–03:59 AM 30-minute rollover bug by anchoring intervals to `boundary.date(now).plusDays(days)`.
4. Fix `Scheduler.introduce()` ghost future-due `isNew` cards and dead component-prior overwrite.
5. Wire `activeHistoryCutoff()` (`historyBoundaryDao`) into all live `ReviewDao` queries and `LocalOptimizer`.
6. Wrap `ensureDailyPlanLocked()` and `markWrongLocked()` in `db.inTransaction`, fix `remainingByDeck()` suppression filter, exclude suppressed chunks before SQL `LIMIT`, and exclude `last` from its own `overheating()` baseline.

### Phase 2 — Scientific Rigor & Scheduling Quality
1. Add **deterministic bounded fuzzing** to FSRS-6 scheduling to eliminate cohort review avalanches.
2. Interleave new introductions with review cards in `SessionBuilder` (with 1–2 familiar warm-up reviews first).
3. Upgrade `ReviewSignalTracker` & `DerivedGrading` to measure **pre-reveal retrieval latency** (`shown -> reveal`), discard TTS-confounded samples, and keep derived grading gated on real-user held-out Brier/log-loss improvement.
4. Fix `ComponentRepository`: include `lang` in `ComponentKey`, require elapsed spacing before boosting component stability, and keep live undo consistent.
5. Correct the late-night rule documentation/rationale (remove inverted sleep-consolidation claim; treat strictly as optional fatigue/sleep-hygiene product policy).
6. Upgrade `FsrsOptimizer`: preserve whole-card histories (no mid-card truncation), freeze unscored `w[17..19]`, and replace 6-pass coordinate descent with analytical BPTT/Adam + L2 prior (with future per-language/material specialization once enough reviews accumulate).
7. Implement conservative `ContextPolicy` for Catalogue v2 `chunk_contexts` (fixed context during acquisition, secondary context after stability threshold, logged without altering FSRS grades).

### Phase 3 — Monolith Split & Unified Replay Engine
1. Pure move-only commit splitting `LearningRepository.kt` into `DailyPlanService`, `ReviewAnswerService`, `BrowseService`, and `StudyTelemetryService` behind the existing `LearningRepository` facade.
2. Extract unified `JournalReplayEngine` shared across `RestoreRepository`, `SchedulerMigration`, `IncrementalJournalRepository`, and sync-boundary undo.
3. Add deterministic multi-month simulation tests (verifying zero cohort spikes, zero negative/30-min intervals, reset isolation, and live-vs-replay bit-for-bit equivalence).

---

## References (Verified via OpenAlex & arXiv Literature Search)

> **API & License Notice:** Bibliographic metadata was retrieved using the OpenAlex (`https://help.openalex.org/`) and arXiv (`https://info.arxiv.org/help/api/index.html`) APIs. Always check the license of individual papers before reusing figures or text.

1. Ye J, Su J, Cao Y. *A Stochastic Shortest Path Algorithm for Optimizing Spaced Repetition Scheduling.* Proceedings of the 28th ACM SIGKDD Conference on Knowledge Discovery and Data Mining (KDD). 2022. https://doi.org/10.1145/3534678.3539081
2. Cepeda NJ, Pashler H, Vul E, Wixted JT, Rohrer D. *Distributed practice in verbal recall tasks: A review and quantitative synthesis.* Psychological Bulletin. 2006;132(3):354-380. https://doi.org/10.1037/0033-2909.132.3.354
3. Cepeda NJ, Vul E, Rohrer D, Wixted JT, Pashler H / Mozer MC et al. *Optimizing Distributed Practice: Theoretical Analysis and Practical Implications.* Experimental Psychology. 2009;56(4):236-246. https://doi.org/10.1027/1618-3169.56.4.236
4. Sense F, Behrens F, Meijer RR, van Rijn H. *An Individual's Rate of Forgetting Is Stable Over Time but Differs Across Materials.* Topics in Cognitive Science. 2016;8(1):305-321. https://doi.org/10.1111/tops.12183
5. Sense F, Meijer RR, van Rijn H. *Exploration of the Rate of Forgetting as a Domain-Specific Individual Differences Measure.* Frontiers in Education. 2018;3:112. https://doi.org/10.3389/feduc.2018.00112
6. *Optimizing Fact-Learning with a Response-Latency-Based Adaptive System.* OSF Preprints. 2022. https://doi.org/10.31234/osf.io/chpgv
7. *Explaining forgetting at different timescales requires a time-variant forgetting function.* npj Science of Learning. 2026. https://doi.org/10.1038/s41539-026-00447-1
8. Storm BC, Bjork RA, Storm JC. *Optimizing retrieval as a learning event: When and why expanding retrieval practice enhances long-term retention.* Memory & Cognition. 2010;38(2):244-253. https://doi.org/10.3758/mc.38.2.244
9. Rasch B, Born J. *About Sleep's Role in Memory.* Physiological Reviews. 2013;93(2):681-766. https://doi.org/10.1152/physrev.00032.2012
10. Payne JD, Tucker MA, Ellenbogen JM, Wamsley EJ, Walker MP, Schacter DL, Stickgold R. *Memory for Semantically Related and Unrelated Declarative Information: The Benefit of Sleep, the Cost of Wake.* PLOS ONE. 2012;7(3):e33079. https://doi.org/10.1371/journal.pone.0033079
11. Feld GB, Diekelmann S. *Sleep smart—optimizing sleep for declarative learning and memory.* Frontiers in Psychology. 2015;6:622. https://doi.org/10.3389/fpsyg.2015.00622
12. Dunlosky J, Rawson KA, Marsh EJ, Nathan MJ, Willingham DT. *Improving Students' Learning With Effective Learning Techniques.* Psychological Science in the Public Interest. 2013;14(1):4-58. https://doi.org/10.1177/1529100612453266
13. Donoghue GM, Hattie JAC. *A Meta-Analysis of Ten Learning Techniques.* Frontiers in Education. 2021;6:581216. https://doi.org/10.3389/feduc.2021.581216
14. Pan SC, Rickard TC. *Transfer of test-enhanced learning: Meta-analytic review and synthesis.* Psychological Bulletin. 2018;144(7):710-756. https://doi.org/10.1037/bul0000151
15. Chen S, Miller RT, Ke S. *The effect of contextual diversity on L1 and L2 word learning: A systematic review and meta-analysis.* Applied Psycholinguistics. 2026;47:e26. https://doi.org/10.1017/S0142716426100691
16. *The effects of contextual diversity on incidental vocabulary learning.* Studies in Second Language Acquisition. 2026. https://doi.org/10.1017/s0272263126101818
17. *The spacing effect stands up to big data.* Behavior Research Methods. 2019. https://doi.org/10.3758/s13428-018-1184-7
18. *Interpretable Memory Models for Spaced Repetition.* arXiv. 2026. https://doi.org/10.48550/arxiv.2610.10548
