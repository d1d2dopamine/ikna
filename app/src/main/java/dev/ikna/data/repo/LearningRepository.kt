package dev.ikna.data.repo

import dev.ikna.data.db.CardDao
import dev.ikna.data.db.ChunkDao
import dev.ikna.data.db.DailyPlanEntity
import dev.ikna.data.db.DailyStatEntity
import dev.ikna.data.db.GovernorDao
import dev.ikna.data.db.GovernorLogEntity
import dev.ikna.data.db.PlanDao
import dev.ikna.data.db.ReviewDao
import dev.ikna.data.db.ReviewEntity
import dev.ikna.data.db.StatsDao
import dev.ikna.domain.fsrs.DAY_MS
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.ChunkSelector
import dev.ikna.domain.governor.GovernorConfig
import dev.ikna.domain.governor.GovernorDecision
import dev.ikna.domain.governor.GovernorReason
import dev.ikna.domain.governor.GovernorSignals
import dev.ikna.domain.governor.LoadGovernor
import dev.ikna.domain.session.SessionBuilder
import dev.ikna.domain.session.SessionCard
import dev.ikna.domain.session.SessionPlan
import dev.ikna.domain.time.DayBoundary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LearningRepository(
    private val cardDao: CardDao,
    private val chunkDao: ChunkDao,
    private val reviewDao: ReviewDao,
    private val statsDao: StatsDao,
    private val governorDao: GovernorDao,
    private val planDao: PlanDao,
    private val components: ComponentRepository,
    private val scheduler: Scheduler,
    private val selector: ChunkSelector,
    private val baseConfig: GovernorConfig
) {

    /** Set from the load switch in settings: calm / normal / dense. */
    @Volatile var dailyTargetOverride: Int? = null

    /**
     * When true, the size of a normal day is measured instead of chosen: see
     * [autoTarget]. On by default, because asking the user to predict their own
     * capacity is asking them to make a decision, and the decision is the
     * expensive part — they will either guess high and drown, or never answer.
     */
    @Volatile var autoLoad: Boolean = true

    /**
     * One writer at a time.
     *
     * Answering is a read-modify-write: read today's counter, add one,
     * write it back. Two fast swipes overlapped, both read the same value,
     * and the second write erased the first — a card answered but not
     * counted. That number is not cosmetic: it feeds the measured norm and
     * the load governor, so a lost answer quietly shrinks tomorrow. Swiping
     * fast is not misuse here, it is the normal speed of a good session.
     * These writes are milliseconds long, so serialising them costs nothing
     * the user can feel.
     */
    private val writeLock = Mutex()

    private val config: GovernorConfig
        get() = baseConfig.copy(
            targetDailyReviews = dailyTargetOverride ?: baseConfig.targetDailyReviews
        )

    // Cheap objects, rebuilt per call so a settings change takes effect at once.
    private fun governor() = LoadGovernor(config)
    private fun builder() = SessionBuilder(cardDao, chunkDao, config)

    private val dayFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // Rebuilt per call so a config change takes effect immediately.
    private val boundary: DayBoundary
        get() = DayBoundary(config.dayStartHour)

    /**
     * Which day a moment belongs to. The night belongs to the evening that
     * produced it: see [DayBoundary].
     */
    fun dayKey(ts: Long): String = boundary.key(ts)

    fun dailyMinimum(): Int = config.dailyMinimumCards

    // ---- daily plan -------------------------------------------------------

    /**
     * The plan for today, computed at most once per calendar day.
     *
     * This idempotence is the fix for the counter bug. Previously the plan was
     * recomputed on every entry to the session screen: answering cards lowered
     * the due count, the governor saw free headroom, spent it on new chunks,
     * and the number at the top of the screen went up while the user worked.
     * Now the day's questions are decided once and stored; the only way the set
     * grows is [addExtra], which the user triggers on purpose.
     */
    suspend fun ensureDailyPlan(now: Long = System.currentTimeMillis()): DailyPlanEntity =
        writeLock.withLock { ensureDailyPlanLocked(now) }

    private suspend fun ensureDailyPlanLocked(now: Long): DailyPlanEntity {
        val day = dayKey(now)
        val existing = planDao.plan(day)

        // A stored plan is authoritative: today's questions are decided once.
        if (existing != null) return existing

        // Every break is inferred here, and only here.
        absorbIdleTime(now)

        // The measured norm is refreshed once a day, right here, so today's
        // capacity reflects the last two weeks of real behaviour.
        if (autoLoad) dailyTargetOverride = autoTarget()

        // Overdue cards leave the visible queue. They are not forgiven, only
        // hidden: FSRS still recomputes their stability from real elapsed time.
        cardDao.moveOverdueToAmnesty(now - 2 * DAY_MS)

        val signals = collectSignals(now)
        val decision = withNightRule(governor().decide(signals), now)

        governorDao.insert(
            GovernorLogEntity(
                ts = now,
                day = day,
                dueToday = signals.dueToday,
                forecastAvg3d = signals.forecastAvg3d,
                backlog = signals.backlog,
                accuracyRecent = signals.accuracyRecent,
                daysSinceLastSession = signals.daysSinceLastSession,
                reviewsDoneToday = signals.reviewsDoneToday,
                capacity = decision.capacity,
                headroom = decision.headroom,
                allowedNew = decision.allowedNew,
                reason = decision.reason.name
            )
        )

        // New chunks are introduced exactly once per day, here and nowhere else.
        if (decision.allowedNew > 0) introduce(decision.allowedNew, now)

        val alreadyAnswered = reviewDao.answeredKeysSince(startOfDay(now)).toSet()
        val ids = builder().pickForDay(decision, now)
            .map { it.key }
            .filterNot { it in alreadyAnswered }

        return storePlan(
            day = day,
            ids = ids,
            capacity = decision.capacity,
            allowedNew = decision.allowedNew,
            amnestyQuota = decision.amnestyQuota,
            reason = decision.reason,
            now = now
        )
    }

    /** Kept for the nightly worker, which only needs the side effects. */
    suspend fun runDailyPlan(now: Long = System.currentTimeMillis()): DailyPlanEntity =
        ensureDailyPlan(now)

    private suspend fun storePlan(
        day: String,
        ids: List<String>,
        capacity: Int,
        allowedNew: Int,
        amnestyQuota: Int,
        reason: GovernorReason,
        now: Long
    ): DailyPlanEntity {
        val plan = DailyPlanEntity(
            day = day,
            plannedIds = ids.joinToString(","),
            plannedTotal = ids.size,
            capacity = capacity,
            allowedNew = allowedNew,
            amnestyQuota = amnestyQuota,
            reason = reason.name,
            extraRequested = 0,
            createdAt = now
        )
        planDao.upsert(plan)
        planDao.clearOtherThan(day)
        return plan
    }

    /**
     * Time nobody spent here does not count.
     *
     * There is no vacation switch in this app on purpose. A switch has to be
     * flipped in advance by someone who can predict their own week, which is the
     * one thing this app assumes its user cannot do — and a switch they forget to
     * flip produces exactly the pile it was supposed to prevent. So every
     * completed day is graded instead: a day with nothing done pushes every
     * schedule forward by a full day, a day with a third of the norm pushes
     * nothing, and a half-hearted day pushes a fraction of a day. Debt cannot
     * accumulate while nobody is looking, and the front of the queue always lands
     * on the day the user comes back — not behind it.
     *
     * The anchor is the last stored plan row, so days are accounted for exactly
     * once, whether the nightly worker kept building plans through the absence or
     * the phone was off the entire time.
     */
    private suspend fun absorbIdleTime(now: Long) {
        val anchor = planDao.latest() ?: return
        val today = LocalDate.parse(dayKey(now))
        val anchorDay = runCatching { LocalDate.parse(anchor.day) }.getOrNull() ?: return

        var day = maxOf(anchorDay, today.minusDays(MAX_CREDIT_DAYS))
        if (!day.isBefore(today)) return

        val expected = expectedForCredit()
        var shiftMs = 0L
        while (day.isBefore(today)) {
            val done = statsDao.day(day.format(dayFormat))?.reviewsDone ?: 0
            val used = (done / expected).coerceIn(0.0, 1.0)
            shiftMs += ((1.0 - used) * DAY_MS).toLong()
            day = day.plusDays(1)
        }
        if (shiftMs > 0L) cardDao.shiftSchedules(shiftMs)
    }

    /**
     * How much work counts as a day fully used. Deliberately generous: a third
     * of the norm buys the whole day, because the goal is a queue that stays
     * small, not a quota that has to be met.
     */
    private suspend fun expectedForCredit(): Double =
        max(
            config.dailyMinimumCards.toDouble(),
            currentDailyTarget() * config.idleCreditRatio
        )

    /**
     * The share of the last week that was actually used, 0..1.
     *
     * This is what replaces "did the user skip yesterday": one missed day out of
     * seven barely moves it, three quiet days halve it, and a week away drives it
     * to zero. The governor reads it and stops handing out new chunks long before
     * the queue turns into a pile. A brand new account reads as fully active, so
     * a first day is never punished for having no history.
     */
    suspend fun activityRatio(now: Long = System.currentTimeMillis()): Double {
        val window = config.activityWindowDays
        val all = statsDao.lastDays(window * 3)
        if (all.isEmpty()) return 1.0

        val cutoff = dayKey(now - (window - 1) * DAY_MS)
        val inWindow = all.filter { it.day >= cutoff }

        val today = LocalDate.parse(dayKey(now))
        val firstEver = runCatching { LocalDate.parse(all.last().day) }.getOrNull()
        val span = if (firstEver == null) window
        else (ChronoUnit.DAYS.between(firstEver, today).toInt() + 1).coerceIn(1, window)

        val expected = expectedForCredit()
        val used = inWindow.sumOf { (it.reviewsDone / expected).coerceIn(0.0, 1.0) }
        // The norm is weekly, not daily. Dividing by the whole window made an
        // entirely ordinary week — four full days, three off — read as 0.57,
        // one quiet evening away from the gate that stops new chunks. Four or
        // five used days now count as a week that went fine.
        val need = minOf(span.toDouble(), config.activeDaysPerWeek).coerceAtLeast(1.0)
        return (used / need).coerceIn(0.0, 1.0)
    }

    /**
     * The measured size of a normal day.
     *
     * Median of cards actually answered on active days over the last two weeks,
     * plus a little headroom so a good stretch can still grow the load. Median
     * rather than mean because one heroic evening should not become the new
     * standard. Fewer than three active days means there is nothing to measure
     * yet, so it starts deliberately small.
     */
    suspend fun autoTarget(): Int {
        val done = statsDao.lastDays(AUTO_WINDOW_DAYS)
            .map { it.reviewsDone }
            .filter { it > 0 }
            .sorted()
        if (done.size < 3) return AUTO_COLD_START
        val median = done[done.size / 2]
        return (median * AUTO_HEADROOM).toInt().coerceIn(AUTO_MIN, AUTO_MAX)
    }

    /** What today's plan is aiming at. Shown on the progress screen. */
    suspend fun currentDailyTarget(): Int =
        if (autoLoad) autoTarget() else (dailyTargetOverride ?: baseConfig.targetDailyReviews)

    /**
     * Whether the norm above is measured or still a placeholder.
     *
     * [AUTO_COLD_START] is a guess for the first days, and showing a guess as a
     * measurement is how an app teaches the user to distrust all of its numbers.
     * The screens ask this before printing a figure.
     */
    suspend fun normIsMeasured(): Boolean =
        !autoLoad || statsDao.lastDays(AUTO_WINDOW_DAYS).count { it.reviewsDone > 0 } >= 3

    /**
     * Which of the last [days] days had a session, most recent first.
     *
     * A map, not a chain: the progress screen draws these as separate marks so a
     * gap is a gap and not a broken streak.
     */
    suspend fun activityMap(days: Int = 30, now: Long = System.currentTimeMillis()): List<Boolean> {
        val active = statsDao.lastDays(days)
            .filter { it.reviewsDone > 0 }
            .map { it.day }
            .toSet()
        return (0 until days).map { d -> dayKey(now - d * DAY_MS) in active }
    }

    suspend fun collectSignals(now: Long): GovernorSignals {
        val dueToday = cardDao.dueCount(now)
        val backlog = cardDao.amnestyCount()

        var forecastTotal = 0
        for (d in 1..config.forecastHorizonDays) {
            forecastTotal += cardDao.dueBetween(now + (d - 1) * DAY_MS, now + d * DAY_MS)
        }
        val forecastAvg = forecastTotal.toDouble() / max(1, config.forecastHorizonDays)

        val recent = reviewDao.recent(config.recentWindowSize)
        val accuracy = if (recent.isEmpty()) 1.0
        else recent.count { it.rating >= 3 }.toDouble() / recent.size

        val lastTs = reviewDao.lastReviewTs()
        val daysSince = if (lastTs == null) 0
        else ((startOfDay(now) - startOfDay(lastTs)) / DAY_MS).toInt()

        val today = statsDao.day(dayKey(now))
        val cleanDays = countCleanDays()
        val weekAgo = dayKey(now - 7 * DAY_MS)

        return GovernorSignals(
            dueToday = dueToday,
            forecastAvg3d = forecastAvg,
            backlog = backlog,
            accuracyRecent = accuracy,
            activityRatio = activityRatio(now),
            daysSinceLastSession = daysSince,
            daysSinceStart = daysSinceStart(now),
            reviewsDoneToday = today?.reviewsDone ?: 0,
            cleanDays = cleanDays,
            newIntroducedLastWeek = statsDao.newIntroducedSince(weekAgo) ?: 0,
            totalReviews = reviewDao.total()
        )
    }

    /**
     * Days since the first day with any activity. Zero on a fresh install, so a
     * new account is inside the settling window by definition.
     */
    private suspend fun daysSinceStart(now: Long): Int {
        val first = statsDao.firstDay() ?: return 0
        return runCatching {
            ChronoUnit.DAYS.between(LocalDate.parse(first), LocalDate.parse(dayKey(now))).toInt()
        }.getOrDefault(0)
    }

    /**
     * Nothing new between [GovernorConfig.nightCutoffHour] and the hour the
     * day rolls over.
     *
     * A chunk met once at midnight gets the worst possible first contact:
     * overnight consolidation is weaker here than it is for most people, so that
     * single late pass is largely wasted and the chunk comes back tomorrow as
     * something unfamiliar that already carries a schedule. Reviews are
     * untouched — only the introduction waits for the morning. A first ever
     * session is exempt: an empty first screen is worse than anything this rule
     * protects against.
     */
    private fun withNightRule(decision: GovernorDecision, now: Long): GovernorDecision {
        if (decision.allowedNew <= 0 || decision.reason == GovernorReason.FIRST_RUN) {
            return decision
        }
        if (!boundary.isNight(now, config.nightCutoffHour)) return decision
        return decision.copy(allowedNew = 0, reason = GovernorReason.LATE_NIGHT)
    }

    private suspend fun countCleanDays(): Int {
        var n = 0
        for (stat in statsDao.lastDays(30)) {
            if (stat.planCompleted) n++ else break
        }
        return n
    }

    private suspend fun introduce(count: Int, now: Long) {
        val candidates = chunkDao.uintroducedByFrequency(count * 12)
        if (candidates.isEmpty()) return

        val tokens = chunkDao.tokensFor(candidates.map { it.id }).groupBy { it.chunkId }
        val lemmas = tokens.values.flatten().map { it.lemma }
        val comps = components.componentsFor(lemmas)

        val chosen = selector.select(candidates, tokens, comps, now, count)
        val cards = chosen.map { sc ->
            scheduler.introduce(sc.chunk.id, level = 0, componentPrior = sc.prior, now = now)
        }
        cardDao.upsertAll(cards)

        val day = dayKey(now)
        val stat = statsDao.day(day)
        statsDao.upsert(
            (stat ?: DailyStatEntity(day, 0, 0, 0L, 1.0, false))
                .copy(newIntroduced = (stat?.newIntroduced ?: 0) + cards.size)
        )
    }

    // ---- session ----------------------------------------------------------

    /**
     * Today's plan minus everything already answered today. Rebuilding this
     * after a rotation, a tab switch or a process death yields exactly the same
     * queue and the same count, which is what makes the counter trustworthy.
     */
    suspend fun buildSession(
        deckId: String? = null,
        now: Long = System.currentTimeMillis()
    ): SessionPlan {
        val plan = ensureDailyPlan(now)
        val answered = reviewDao.answeredKeysSince(startOfDay(now)).toSet()

        // The whole plan is materialised here, not just its pending part, because
        // a deck session has to know how much of its own share is already done —
        // and the deck of a card is only reachable through its chunk. The plan is
        // a few dozen keys at most, so this costs one query and no joins.
        val all = builder().materialize(plan.ids)
        val scope = if (deckId == null) all else all.filter { it.chunk.packId == deckId }
        val pending = scope.filterNot { it.card.key in answered }

        val storedReason = runCatching { GovernorReason.valueOf(plan.reason) }
            .getOrDefault(GovernorReason.OK)

        // A finished deck and a quiet day produce exactly the same empty plan,
        // and the governor cannot tell them apart: it rules on how much new
        // material is allowed, never on how much exists. So the one layer that
        // can count does it here. Without this the end of a deck was reported as
        // "everything is repeated, the next ones are not due yet" — a promise of
        // cards that are never coming.
        val reason = if (pending.isEmpty() && untouchedIn(deckId) == 0) {
            GovernorReason.PACK_EXHAUSTED
        } else {
            storedReason
        }

        return SessionPlan(
            cards = pending,
            plannedTotal = plan.plannedTotal,
            answeredToday = statsDao.day(plan.day)?.reviewsDone ?: 0,
            reason = reason,
            nextDueAt = cardDao.nextDueAt(now),
            deckId = deckId,
            deckTitle = deckId?.let { id -> chunkDao.pack(id)?.title ?: id },
            sessionTotal = scope.size,
            sessionDone = scope.size - pending.size
        )
    }

    /**
     * Chunks never met, in one deck or across every active deck.
     *
     * Zero does not mean the deck is over for good — its cards keep coming back
     * for review — it means there is nothing left in it to meet for a first time.
     */
    private suspend fun untouchedIn(deckId: String?): Int =
        if (deckId == null) chunkDao.untouchedCount() else chunkDao.untouchedCountFor(deckId)

    /**
     * What each deck still owes today.
     *
     * Read by the deck list, which is the first screen. A deck showing zero is
     * not a deck that failed — the day's capacity is one number for the whole
     * app and the plan spends it where the schedule points, so a quiet deck today
     * is simply a deck whose cards are not due today.
     */
    suspend fun remainingByDeck(now: Long = System.currentTimeMillis()): Map<String, Int> {
        val plan = ensureDailyPlan(now)
        val answered = reviewDao.answeredKeysSince(startOfDay(now)).toSet()
        return builder().materialize(plan.ids)
            .filterNot { it.card.key in answered }
            .groupingBy { it.chunk.packId }
            .eachCount()
    }

    /**
     * "Ещё немного". Adds cards that are already due to today's plan and
     * nothing else: no new chunks, so a good day today never inflates tomorrow.
     * Returns how many were actually added.
     */
    suspend fun addExtra(
        count: Int = 5,
        deckId: String? = null,
        now: Long = System.currentTimeMillis()
    ): Int {
        val plan = ensureDailyPlan(now)
        val answered = reviewDao.answeredKeysSince(startOfDay(now))
        val exclude = (plan.ids + answered).distinct()

        // Inside a deck session the extra cards have to come from that deck. The
        // card table has no deck column, so we over-ask and filter by chunk
        // rather than teaching the DAO to join. Without this the button appeared
        // to do nothing whenever the extra cards happened to be from elsewhere.
        val candidates = builder().pickExtra(exclude, if (deckId == null) count else count * 10, now)
        val extra = if (deckId == null) candidates else {
            val packOf = chunkDao.chunks(candidates.map { it.chunkId }.distinct())
                .associate { it.id to it.packId }
            candidates.filter { packOf[it.chunkId] == deckId }.take(count)
        }
        if (extra.isEmpty()) return 0

        planDao.upsert(
            plan.copy(
                plannedIds = (plan.ids + extra.map { it.key }).joinToString(","),
                plannedTotal = plan.plannedTotal + extra.size,
                extraRequested = plan.extraRequested + extra.size
            )
        )
        return extra.size
    }

    suspend fun answer(sessionCard: SessionCard, rating: Rating, durationMs: Long, now: Long) =
        writeLock.withLock { answerLocked(sessionCard, rating, durationMs, now) }

    private suspend fun answerLocked(
        sessionCard: SessionCard,
        rating: Rating,
        durationMs: Long,
        now: Long
    ) {
        // Read the row instead of trusting the copy the UI holds: a card that
        // was rated "again" earlier in the same session is shown again from an
        // in-memory copy, and that copy is stale.
        val before = cardDao.card(sessionCard.chunk.id, sessionCard.level.value) ?: sessionCard.card
        val result = scheduler.apply(before, rating, now)
        cardDao.upsert(result.card)

        reviewDao.insert(
            ReviewEntity(
                chunkId = sessionCard.chunk.id,
                level = sessionCard.level.value,
                ts = now,
                rating = rating.value,
                elapsedDays = result.elapsedDays,
                stabilityBefore = result.before.stability,
                stabilityAfter = result.after.stability,
                difficultyBefore = result.before.difficulty,
                difficultyAfter = result.after.difficulty,
                durationMs = durationMs,
                wasAmnesty = sessionCard.fromAmnesty,
                // Snapshot for undo. Restoring a saved state is the only honest
                // way back: FSRS is not invertible.
                prevStability = before.stability,
                prevDifficulty = before.difficulty,
                prevDueAt = before.dueAt,
                prevLastReviewAt = before.lastReviewAt,
                prevReps = before.reps,
                prevLapses = before.lapses,
                prevIsNew = before.isNew,
                prevInAmnesty = before.inAmnesty
            )
        )

        components.recordAnswer(sessionCard.chunk.id, rating.value, now)

        // Promote to the next presentation level once the item is solid, which
        // gives novelty without growing the queue.
        builder().nextLevelFor(result.card)?.let { nextLevel ->
            if (cardDao.card(result.card.chunkId, nextLevel) == null) {
                cardDao.upsert(
                    result.card.copy(
                        level = nextLevel,
                        stability = result.card.stability * 0.4,
                        dueAt = now + DAY_MS,
                        reps = 0,
                        lapses = 0,
                        isNew = true,
                        lastReviewAt = null,
                        introducedAt = now
                    )
                )
            }
        }

        bumpDailyStat(now, rating, durationMs)
    }

    /**
     * Takes back the most recent answer.
     *
     * The review log stays append-only: the retraction is an inserted row that
     * points at the answer it cancels, and every query that reads "real
     * answers" filters those out. The card is restored from the snapshot taken
     * when the answer was recorded.
     *
     * The word layer keeps its exposure on purpose. It is a smoothed estimate
     * where one extra observation is noise, and Settings can rebuild it exactly
     * from the log at any time.
     *
     * Returns the key of the restored card, or null if there was nothing that
     * could be undone (answers recorded before this version carry no snapshot).
     */
    suspend fun undoLast(now: Long = System.currentTimeMillis()): String? =
        writeLock.withLock { undoLastLocked(now) }

    private suspend fun undoLastLocked(now: Long): String? {
        val last = reviewDao.lastAnswer() ?: return null
        val stability = last.prevStability ?: return null
        val difficulty = last.prevDifficulty ?: return null
        val dueAt = last.prevDueAt ?: return null
        val reps = last.prevReps ?: return null
        val lapses = last.prevLapses ?: return null
        val wasNew = last.prevIsNew ?: return null

        val card = cardDao.card(last.chunkId, last.level) ?: return null
        cardDao.upsert(
            card.copy(
                stability = stability,
                difficulty = difficulty,
                dueAt = dueAt,
                lastReviewAt = last.prevLastReviewAt,
                reps = reps,
                lapses = lapses,
                isNew = wasNew,
                inAmnesty = last.prevInAmnesty ?: card.inAmnesty
            )
        )

        // If that answer promoted the chunk to a new level, take the promotion
        // back too, but only while the new level is still untouched.
        val promotedLevel = last.level + 1
        cardDao.card(last.chunkId, promotedLevel)?.let { promoted ->
            if (promoted.isNew && promoted.reps == 0 && promoted.introducedAt == last.ts) {
                cardDao.delete(last.chunkId, promotedLevel)
            }
        }

        reviewDao.insert(
            ReviewEntity(
                chunkId = last.chunkId,
                level = last.level,
                ts = now,
                rating = 0,
                elapsedDays = 0.0,
                stabilityBefore = last.stabilityAfter,
                stabilityAfter = last.stabilityBefore,
                difficultyBefore = last.difficultyAfter,
                difficultyAfter = last.difficultyBefore,
                durationMs = 0L,
                wasAmnesty = last.wasAmnesty,
                undoOf = last.id
            )
        )

        unbumpDailyStat(last)
        return last.chunkId + ":" + last.level
    }

    private suspend fun bumpDailyStat(now: Long, rating: Rating, durationMs: Long) {
        val day = dayKey(now)
        val prev = statsDao.day(day) ?: DailyStatEntity(day, 0, 0, 0L, 1.0, false)
        val done = prev.reviewsDone + 1
        val correct = prev.accuracy * prev.reviewsDone + if (rating.value >= 3) 1.0 else 0.0
        statsDao.upsert(
            prev.copy(
                reviewsDone = done,
                activeMs = prev.activeMs + durationMs,
                accuracy = correct / done,
                planCompleted = done >= config.dailyMinimumCards
            )
        )
    }

    private suspend fun unbumpDailyStat(review: ReviewEntity) {
        val day = dayKey(review.ts)
        val prev = statsDao.day(day) ?: return
        val done = (prev.reviewsDone - 1).coerceAtLeast(0)
        val correct = (prev.accuracy * prev.reviewsDone - if (review.rating >= 3) 1.0 else 0.0)
            .coerceAtLeast(0.0)
        statsDao.upsert(
            prev.copy(
                reviewsDone = done,
                activeMs = (prev.activeMs - review.durationMs).coerceAtLeast(0L),
                accuracy = if (done == 0) 1.0 else (correct / done).coerceIn(0.0, 1.0),
                planCompleted = done >= config.dailyMinimumCards
            )
        )
    }

    private fun startOfDay(ts: Long): Long = boundary.startOfDay(ts)

    // ---- maintenance ------------------------------------------------------

    /**
     * Danger zone. Clears card schedules and every derived table, then lets the
     * next plan start from scratch. The review log is NOT touched: it is the
     * one thing in this database that cannot be regenerated, so "start over"
     * means forgetting the schedule, not the history.
     */
    suspend fun resetProgress() {
        cardDao.clear()
        components.clearAll()
        statsDao.clear()
        planDao.clear()
    }

    /** Drops today's plan so the next session rebuilds it. Used after imports. */
    suspend fun invalidatePlan() = planDao.clear()

    // ---- stats ------------------------------------------------------------

    fun last30Flow() = statsDao.last30Flow()

    /**
     * The only streak-like metric in the app: days with a session out of the
     * last 30. A single missed day cannot break it, so there is nothing to feel
     * guilty about and nothing to reset.
     */
    suspend fun activeDaysLast30(): Int = statsDao.lastDays(30).count { it.reviewsDone > 0 }

    suspend fun governorLog(limit: Int = 30) = governorDao.recent(limit)

    suspend fun forecast(days: Int, now: Long = System.currentTimeMillis()): List<Int> =
        (1..days).map { d -> cardDao.dueBetween(now + (d - 1) * DAY_MS, now + d * DAY_MS) }

    suspend fun answeredToday(now: Long = System.currentTimeMillis()): Int =
        statsDao.day(dayKey(now))?.reviewsDone ?: 0

    /**
     * How long one answer actually takes, so the queue can be shown in
     * minutes instead of card counts.
     *
     * Median rather than mean, and mis-swipes and put-the-phone-down pauses
     * are dropped, so one interrupted evening cannot inflate the estimate.
     * Null until there is enough history: an invented number is worse than
     * no number, because the whole point is that the estimate can be
     * trusted.
     */
    suspend fun medianAnswerMs(): Long? {
        val samples = reviewDao.recentDurations(DURATION_SAMPLE)
            .filter { it in MIN_SANE_ANSWER_MS..MAX_SANE_ANSWER_MS }
        if (samples.size < DURATION_MIN_SAMPLES) return null
        val sorted = samples.sorted()
        return sorted[sorted.size / 2]
    }

    /**
     * The four measurements added to the statistics screen, in one read.
     *
     * Deliberately computed here rather than in SQL: the day starts at 04:00,
     * the hour of an answer depends on the phone's timezone, and a review has to
     * be told apart from a first contact by its snapshot. All three are things
     * SQLite would have to be taught and Kotlin already knows.
     */
    suspend fun statsDigest(now: Long = System.currentTimeMillis()): StatsDigest {
        val answers = reviewDao.since(now - STATS_WINDOW_DAYS * DAY_MS)

        // Only real reviews count towards retention. A first contact cannot be
        // "forgotten", so counting it would quietly inflate every figure here.
        // Rows written before the undo schema carry no snapshot and are skipped.
        val reviews = answers.filter { it.prevIsNew == false }
        val recalled = reviews.count { it.rating >= Rating.HARD.value }
        val retention =
            if (reviews.size >= RETENTION_MIN_SAMPLE) recalled.toDouble() / reviews.size else null

        val zone = ZoneId.systemDefault()
        val buckets = HashMap<Int, IntArray>()
        reviews.forEach { review ->
            val hour = Instant.ofEpochMilli(review.ts).atZone(zone).hour
            val slot = buckets.getOrPut(hour) { IntArray(2) }
            slot[0]++
            if (review.rating >= Rating.HARD.value) slot[1]++
        }
        val hours = buckets.entries
            .sortedBy { it.key }
            .map { (hour, slot) ->
                HourSlice(hour = hour, answers = slot[0], accuracy = slot[1].toDouble() / slot[0])
            }
        val bestHour = hours
            .filter { it.answers >= HOUR_MIN_SAMPLE }
            .maxWithOrNull(compareBy<HourSlice> { it.accuracy }.thenBy { it.answers })
            ?.hour

        // Minutes come from the daily counters rather than from the log: they
        // already exclude the gaps where the phone was put down mid-session.
        val todayMs = statsDao.day(dayKey(now))?.activeMs ?: 0L
        val weekMs = statsDao.lastDays(7).sumOf { it.activeMs }

        val leechCards = cardDao.leeches(LEECH_MIN_LAPSES, LEECH_LIMIT)
        val chunks = chunkDao.chunks(leechCards.map { it.chunkId }.distinct()).associateBy { it.id }
        val leeches = leechCards
            .mapNotNull { card ->
                val chunk = chunks[card.chunkId] ?: return@mapNotNull null
                LeechItem(
                    text = chunk.text,
                    translation = chunk.translation,
                    lapses = card.lapses
                )
            }
            // The same phrase can be a leech at two levels. It is one phrase to
            // the person reading the list.
            .distinctBy { it.text }

        return StatsDigest(
            retention = retention,
            retentionSample = reviews.size,
            minutesToday = (todayMs / 60_000L).toInt(),
            minutesLast7 = (weekMs / 60_000L).toInt(),
            medianSeconds = medianAnswerMs()?.let { ((it + 500L) / 1000L).toInt() },
            hours = hours,
            bestHour = bestHour,
            leeches = leeches
        )
    }

    private companion object {
        /** Upper bound on how far back an absence is repaid, in days. */
        const val MAX_CREDIT_DAYS = 120L
        const val AUTO_WINDOW_DAYS = 14
        const val AUTO_COLD_START = 25
        const val AUTO_MIN = 12
        const val AUTO_MAX = 80
        const val AUTO_HEADROOM = 1.15

        /** Sample size and sanity bounds for the time estimate. */
        const val DURATION_SAMPLE = 100
        const val DURATION_MIN_SAMPLES = 8
        const val MIN_SANE_ANSWER_MS = 800L
        const val MAX_SANE_ANSWER_MS = 60_000L

        /**
         * Sizes for the statistics screen.
         *
         * The two sample floors are the difference between a measurement and a
         * rumour: twenty reviews before a retention figure is shown at all, and
         * twelve answers inside one hour before that hour may compete for
         * "best". Four forgettings is where a phrase stops being hard and starts
         * being broken.
         */
        const val STATS_WINDOW_DAYS = 30L
        const val RETENTION_MIN_SAMPLE = 20
        const val HOUR_MIN_SAMPLE = 12
        const val LEECH_MIN_LAPSES = 4
        const val LEECH_LIMIT = 8
    }
}
