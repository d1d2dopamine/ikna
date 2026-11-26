package dev.ikna.data.repo

import dev.ikna.data.db.CardDao
import dev.ikna.data.db.CardEntity
import dev.ikna.data.db.ChunkDao
import dev.ikna.data.db.DailyStatEntity
import dev.ikna.data.db.StatsDao
import dev.ikna.domain.fsrs.ComponentPrior
import dev.ikna.domain.fsrs.DAY_MS
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.ChunkSelector
import dev.ikna.domain.governor.CleanStreak
import dev.ikna.domain.governor.GovernorConfig
import dev.ikna.domain.governor.GovernorDecision
import dev.ikna.domain.governor.GovernorReason
import dev.ikna.domain.governor.GovernorSignalCollector
import dev.ikna.domain.time.DayBoundary
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Handles new-chunk introduction, daily introduction accounting, and governor
 * historical window helpers (overheat, return gap, clean streak, night guard).
 */
internal class ChunkIntroductionService(
    private val cardDao: CardDao,
    private val chunkDao: ChunkDao,
    private val statsDao: StatsDao,
    private val components: ComponentRepository,
    private val scheduler: Scheduler,
    private val selector: ChunkSelector,
    private val boundary: DayBoundary,
    private val configProvider: () -> GovernorConfig,
    private val suppressedNow: suspend () -> Set<String>
) {
    private val config: GovernorConfig get() = configProvider()

    suspend fun overheating(now: Long): Boolean =
        GovernorSignalCollector.isOverheated(
            recentActiveDays = statsDao.lastDays(config.activityWindowDays * 2),
            todayKey = boundary.key(now),
            overheatRatio = config.overheatRatio,
            overheatAccuracyDrop = config.overheatAccuracyDrop
        )

    suspend fun daysSinceReturn(now: Long, scanDays: Int): Int? =
        GovernorSignalCollector.daysSinceReturn(
            recentDaysNewestFirst = statsDao.lastDays(scanDays),
            todayKey = boundary.key(now),
            returnModeGapDays = config.returnModeGapDays
        )

    suspend fun daysSinceStart(now: Long): Int {
        val first = statsDao.firstDay() ?: return 0
        return runCatching {
            ChronoUnit.DAYS.between(LocalDate.parse(first), LocalDate.parse(boundary.key(now))).toInt()
        }.getOrDefault(0)
    }

    fun withNightRule(decision: GovernorDecision, now: Long): GovernorDecision {
        if (decision.allowedNew <= 0 || decision.reason == GovernorReason.FIRST_RUN) {
            return decision
        }
        if (!boundary.isNight(now, config.nightCutoffHour)) return decision
        return decision.copy(allowedNew = 0, reason = GovernorReason.LATE_NIGHT)
    }

    suspend fun countCleanDays(now: Long, scanDays: Int): Int {
        val finished = statsDao.lastDays(scanDays)
            .associate { it.day to it.planCompleted }
        val days = (0 until scanDays).map { d -> boundary.key(now - d * DAY_MS) }
        return CleanStreak.count(days, finished)
    }

    suspend fun introduce(
        count: Int,
        now: Long,
        packId: String? = null,
        hidden: Set<String>? = null
    ): List<CardEntity> {
        val suppressed = hidden ?: suppressedNow()
        val candidates = (
            if (packId == null) chunkDao.unintroducedByFrequency(count * 12)
            else chunkDao.unintroducedByFrequencyFor(packId, count * 12)
            ).filterNot { it.id in suppressed }
        if (candidates.isEmpty()) return emptyList()

        val tokens = chunkDao.tokensFor(candidates.map { it.id }).groupBy { it.chunkId }
        val comps = components.componentsForChunks(candidates, tokens)

        val subject = candidates.filter { it.lang == NO_LANG }.sortedBy { it.freqRank }
        val language = candidates.filterNot { it.lang == NO_LANG }
        val share = if (subject.isEmpty() || language.isEmpty()) count else (count + 1) / 2
        val fromSubject = subject.take(share.coerceAtMost(count))

        val subjectPrior = ComponentPrior(
            knownRatio = 0.0,
            unknownContentTokens = 1,
            weakLemmas = emptyList()
        )
        val chosen = selector.select(language, tokens, comps, now, count - fromSubject.size)
        val cards = fromSubject.map { chunk ->
            scheduler.introduce(chunk.id, level = 0, componentPrior = subjectPrior, now = now)
        } + chosen.map { sc ->
            scheduler.introduce(sc.chunk.id, level = 0, componentPrior = sc.prior, now = now)
        }
        cardDao.upsertAll(cards)
        return cards
    }

    suspend fun uncountIntroduced(ts: Long) {
        val day = boundary.key(ts)
        val stat = statsDao.day(day) ?: return
        statsDao.upsert(stat.copy(newIntroduced = (stat.newIntroduced - 1).coerceAtLeast(0)))
    }

    suspend fun countIntroduced(now: Long, added: Int) {
        if (added <= 0) return
        val day = boundary.key(now)
        val stat = statsDao.day(day)
        statsDao.upsert(
            (stat ?: DailyStatEntity(day, 0, 0, 0L, 1.0, false))
                .copy(newIntroduced = (stat?.newIntroduced ?: 0) + added)
        )
    }
}
