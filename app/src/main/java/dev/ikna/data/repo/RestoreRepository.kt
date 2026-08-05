package dev.ikna.data.repo

import dev.ikna.data.db.CardDao
import dev.ikna.data.db.CardEntity
import dev.ikna.data.db.DailyStatEntity
import dev.ikna.data.db.PlanDao
import dev.ikna.data.db.ReviewDao
import dev.ikna.data.db.ReviewEntity
import dev.ikna.data.db.StatsDao
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.GovernorConfig
import dev.ikna.domain.time.DayBoundary
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class ReviewRecord(
    val id: Long = 0,
    val chunkId: String,
    val level: Int = 0,
    val ts: Long,
    val rating: Int,
    val elapsedDays: Double = 0.0,
    val stabilityBefore: Double = 0.0,
    val stabilityAfter: Double = 0.0,
    val difficultyBefore: Double = 5.0,
    val difficultyAfter: Double = 5.0,
    val durationMs: Long = 0L,
    val wasAmnesty: Boolean = false,
    val prevStability: Double? = null,
    val prevDifficulty: Double? = null,
    val prevDueAt: Long? = null,
    val prevLastReviewAt: Long? = null,
    val prevReps: Int? = null,
    val prevLapses: Int? = null,
    val prevIsNew: Boolean? = null,
    val prevInAmnesty: Boolean? = null,
    val undoOf: Long? = null
)

data class RestoreResult(val imported: Int, val skipped: Int, val replayed: Int)

/**
 * Restore from an exported review log.
 *
 * This is the reason the log is append-only. Card schedules, the word layer and
 * every statistic are derived values: given the answers back, they can be
 * recomputed exactly by replaying them through the same scheduler. So a restore
 * is not "copying a backup over the app", it is replaying a history — which
 * also means a future change to the algorithm can re-derive everything from the
 * same file.
 */
class RestoreRepository(
    private val cardDao: CardDao,
    private val reviewDao: ReviewDao,
    private val statsDao: StatsDao,
    private val planDao: PlanDao,
    private val components: ComponentRepository,
    private val scheduler: Scheduler,
    private val config: GovernorConfig
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun restoreFromJsonl(text: String): RestoreResult {
        val known = reviewDao.signatures().toHashSet()
        val batch = ArrayList<ReviewEntity>()
        var skipped = 0

        for (line in text.lineSequence()) {
            if (line.isBlank()) continue
            val rec = runCatching { json.decodeFromString<ReviewRecord>(line) }.getOrNull()
            if (rec == null) {
                skipped++
                continue
            }
            val signature = rec.chunkId + ":" + rec.level + ":" + rec.ts
            if (!known.add(signature)) {
                skipped++
                continue
            }
            batch += ReviewEntity(
                id = rec.id,
                chunkId = rec.chunkId,
                level = rec.level,
                ts = rec.ts,
                rating = rec.rating,
                elapsedDays = rec.elapsedDays,
                stabilityBefore = rec.stabilityBefore,
                stabilityAfter = rec.stabilityAfter,
                difficultyBefore = rec.difficultyBefore,
                difficultyAfter = rec.difficultyAfter,
                durationMs = rec.durationMs,
                wasAmnesty = rec.wasAmnesty,
                prevStability = rec.prevStability,
                prevDifficulty = rec.prevDifficulty,
                prevDueAt = rec.prevDueAt,
                prevLastReviewAt = rec.prevLastReviewAt,
                prevReps = rec.prevReps,
                prevLapses = rec.prevLapses,
                prevIsNew = rec.prevIsNew,
                prevInAmnesty = rec.prevInAmnesty,
                undoOf = rec.undoOf
            )
        }

        if (batch.isNotEmpty()) reviewDao.insertAll(batch)
        val replayed = replayFromLog()
        return RestoreResult(imported = batch.size, skipped = skipped, replayed = replayed)
    }

    /**
     * Rebuilds every derived table by replaying the log through the scheduler.
     * Retracted answers are already filtered out by the DAO, so an undo made
     * months ago stays undone.
     */
    suspend fun replayFromLog(): Int {
        cardDao.clear()
        statsDao.clear()
        planDao.clear()

        val answers = reviewDao.allAnswers()
        val seen = HashSet<String>()
        val days = LinkedHashMap<String, DailyStatEntity>()

        for (r in answers) {
            val existing = cardDao.card(r.chunkId, r.level)
            val card = existing ?: CardEntity(
                chunkId = r.chunkId,
                level = r.level,
                stability = r.prevStability ?: r.stabilityBefore,
                difficulty = r.prevDifficulty ?: r.difficultyBefore,
                dueAt = r.ts,
                lastReviewAt = r.prevLastReviewAt,
                introducedAt = r.ts,
                reps = r.prevReps ?: 0,
                lapses = r.prevLapses ?: 0,
                inAmnesty = false,
                isNew = r.prevIsNew ?: true
            )
            cardDao.upsert(scheduler.apply(card, ratingOf(r.rating), r.ts).card)

            // Same boundary as the live counters, or a restore would rebuild
            // stats that disagree with the app that wrote them.
            val day = DayBoundary(config.dayStartHour).key(r.ts)
            val stat = days[day] ?: DailyStatEntity(day, 0, 0, 0L, 1.0, false)
            val done = stat.reviewsDone + 1
            val correct = stat.accuracy * stat.reviewsDone + if (r.rating >= 3) 1.0 else 0.0
            val firstTime = seen.add(r.chunkId + ":" + r.level)
            days[day] = stat.copy(
                reviewsDone = done,
                newIntroduced = stat.newIntroduced + if (firstTime) 1 else 0,
                activeMs = stat.activeMs + r.durationMs,
                accuracy = correct / done,
                planCompleted = done >= config.dailyMinimumCards
            )
        }

        for (stat in days.values) statsDao.upsert(stat)
        components.rebuildFromReviews()
        return answers.size
    }

    private fun ratingOf(value: Int): Rating = when (value) {
        1 -> Rating.AGAIN
        2 -> Rating.HARD
        3 -> Rating.GOOD
        else -> Rating.EASY
    }
}
