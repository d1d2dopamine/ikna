package dev.ikna.data.repo

import dev.ikna.data.db.CardDao
import dev.ikna.data.db.ChunkDao
import dev.ikna.data.db.ReviewDao
import dev.ikna.data.db.ReviewEntity
import dev.ikna.data.db.StatsDao
import dev.ikna.domain.fsrs.DAY_MS
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.time.DayBoundary
import java.time.Instant
import java.time.ZoneId

/**
 * Read-only study telemetry helpers extracted from [LearningRepository]
 * (activity maps, due forecasts, median pace, hourly accuracy slices, and leeches).
 */
internal class LearningStatsService(
    private val cardDao: CardDao,
    private val chunkDao: ChunkDao,
    private val reviewDao: ReviewDao,
    private val statsDao: StatsDao,
    private val boundary: DayBoundary,
    private val activeHistoryCutoff: suspend () -> Long
) {
    suspend fun activityMap(days: Int, now: Long): List<Boolean> {
        val active = statsDao.lastDays(days)
            .filter { it.reviewsDone > 0 }
            .map { it.day }
            .toSet()
        return (0 until days).map { d -> boundary.key(now - d * DAY_MS) in active }
    }

    suspend fun forecast(days: Int, now: Long): List<Int> =
        (1..days).map { d -> cardDao.dueBetween(now + (d - 1) * DAY_MS, now + d * DAY_MS) }

    suspend fun medianAnswerMs(
        sampleLimit: Int,
        minSamples: Int,
        minMs: Long,
        maxMs: Long
    ): Long? {
        val cutoff = activeHistoryCutoff()
        val samples = reviewDao.recentDurationsAfter(cutoff, sampleLimit)
            .filter { it in minMs..maxMs }
        if (samples.size < minSamples) return null
        val sorted = samples.sorted()
        return sorted[sorted.size / 2]
    }

    fun hourlySlices(reviews: List<ReviewEntity>, zone: ZoneId = ZoneId.systemDefault()): List<HourSlice> {
        val buckets = HashMap<Int, IntArray>()
        reviews.forEach { review ->
            val hour = Instant.ofEpochMilli(review.ts).atZone(zone).hour
            val slot = buckets.getOrPut(hour) { IntArray(2) }
            slot[0]++
            if (review.rating >= Rating.HARD.value) slot[1]++
        }
        return buckets.entries
            .sortedBy { it.key }
            .map { (hour, slot) ->
                HourSlice(hour = hour, answers = slot[0], accuracy = slot[1].toDouble() / slot[0])
            }
    }

    suspend fun leeches(minLapses: Int, limit: Int): List<LeechItem> {
        val leechCards = cardDao.leeches(minLapses, limit)
        val chunks = chunkDao.chunks(leechCards.map { it.chunkId }.distinct()).associateBy { it.id }
        return leechCards
            .mapNotNull { card ->
                val chunk = chunks[card.chunkId] ?: return@mapNotNull null
                LeechItem(
                    text = chunk.text,
                    translation = chunk.translation,
                    lapses = card.lapses
                )
            }
            .distinctBy { it.text }
    }
}
