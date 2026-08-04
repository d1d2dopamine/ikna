package dev.ikna.data.repo

import dev.ikna.data.db.CardDao
import dev.ikna.data.db.ChunkDao
import dev.ikna.data.db.DailyStatEntity
import dev.ikna.data.db.GovernorDao
import dev.ikna.data.db.GovernorLogEntity
import dev.ikna.data.db.ReviewDao
import dev.ikna.data.db.ReviewEntity
import dev.ikna.data.db.StatsDao
import dev.ikna.domain.fsrs.DAY_MS
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.ChunkSelector
import dev.ikna.domain.governor.GovernorConfig
import dev.ikna.domain.governor.GovernorDecision
import dev.ikna.domain.governor.GovernorSignals
import dev.ikna.domain.governor.LoadGovernor
import dev.ikna.domain.session.SessionBuilder
import dev.ikna.domain.session.SessionCard
import dev.ikna.domain.session.SessionPlan
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

class LearningRepository(
    private val cardDao: CardDao,
    private val chunkDao: ChunkDao,
    private val reviewDao: ReviewDao,
    private val statsDao: StatsDao,
    private val governorDao: GovernorDao,
    private val components: ComponentRepository,
    private val scheduler: Scheduler,
    private val selector: ChunkSelector,
    private val config: GovernorConfig
) {

    private val governor = LoadGovernor(config)
    private val sessionBuilder = SessionBuilder(cardDao, chunkDao, config)
    private val dayFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun dayKey(ts: Long): String =
        dayFormat.format(Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()))

    // ---- daily plan -------------------------------------------------------

    /**
     * Runs once per day (and on app open). Moves overdue cards into the amnesty
     * pool, asks the governor whether new material is allowed, and introduces
     * whatever it permits.
     */
    suspend fun runDailyPlan(now: Long = System.currentTimeMillis()): GovernorDecision {
        // Overdue cards leave the visible queue. They are not forgiven, only
        // hidden: FSRS still recomputes their stability from real elapsed time.
        cardDao.moveOverdueToAmnesty(now - 2 * DAY_MS)

        val signals = collectSignals(now)
        val decision = governor.decide(signals)

        governorDao.insert(
            GovernorLogEntity(
                ts = now,
                day = dayKey(now),
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

        if (decision.allowedNew > 0) introduce(decision.allowedNew, now)
        return decision
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
            daysSinceLastSession = daysSince,
            reviewsDoneToday = today?.reviewsDone ?: 0,
            cleanDays = cleanDays,
            newIntroducedLastWeek = statsDao.newIntroducedSince(weekAgo) ?: 0,
            totalReviews = reviewDao.total()
        )
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

    suspend fun buildSession(now: Long = System.currentTimeMillis()): SessionPlan {
        val signals = collectSignals(now)
        val decision = governor.decide(signals)
        return sessionBuilder.build(decision, now)
    }

    suspend fun answer(sessionCard: SessionCard, rating: Rating, durationMs: Long, now: Long) {
        val result = scheduler.apply(sessionCard.card, rating, now)
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
                wasAmnesty = sessionCard.fromAmnesty
            )
        )

        components.recordAnswer(sessionCard.chunk.id, rating.value, now)

        // Promote to the next presentation level once the item is solid, which
        // gives novelty without growing the queue.
        sessionBuilder.nextLevelFor(result.card)?.let { nextLevel ->
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

    private fun startOfDay(ts: Long): Long =
        Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault())
            .toLocalDate().atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()

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
}
