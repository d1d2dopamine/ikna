package dev.ikna.data.repo

import dev.ikna.data.db.inTransaction
import dev.ikna.data.db.CardDao
import dev.ikna.data.db.CardEntity
import dev.ikna.data.db.IknaDatabase
import dev.ikna.data.db.PlanDao
import dev.ikna.data.db.ReviewDao
import dev.ikna.data.db.ReviewEntity
import dev.ikna.data.prefs.SettingsStore
import dev.ikna.domain.fsrs.DAY_MS
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.GovernorConfig

const val CURRENT_SCHEDULER_VERSION = 6

/** What the launch gate needs to draw while the one-time replay runs. */
sealed interface SchedulerMigrationState {
    data object Running : SchedulerMigrationState
    data class Ready(val migratedCards: Int) : SchedulerMigrationState
    data class Failed(val reason: String) : SchedulerMigrationState
}

data class SchedulerMigrationResult(val migratedCards: Int, val replayedAnswers: Int)

/**
 * Moves card state from FSRS-4.5 to FSRS-6 without touching content or history.
 *
 * The `reviews` table is the source of truth and stays byte-for-byte append-only.
 * Only `cards` and `daily_plan` are changed: each card that still exists is
 * replayed through FSRS-6, and the old plan is thrown away because its due set
 * was selected from FSRS-4.5 dates.
 *
 * The replay and the marker are intentionally separate commits. Card changes
 * happen in one Room transaction; the DataStore marker is written afterwards.
 * If the process dies in between, the replay runs a second time from the same
 * immutable log and produces the same card table. It can never leave half the
 * cards on one algorithm and half on the other.
 */
class SchedulerMigration(
    private val db: IknaDatabase,
    private val cardDao: CardDao,
    private val reviewDao: ReviewDao,
    private val planDao: PlanDao,
    private val settings: SettingsStore,
    private val scheduler: Scheduler,
    private val config: GovernorConfig
) {

    suspend fun runIfNeeded(now: Long = System.currentTimeMillis()): SchedulerMigrationResult {
        if (settings.schedulerVersion() >= CURRENT_SCHEDULER_VERSION) {
            return SchedulerMigrationResult(0, 0)
        }

        var result = SchedulerMigrationResult(0, 0)
        db.inTransaction {
            val current = cardDao.all()
            val answers = reviewDao.allAnswers()
            val rebuilt = replayCardsForFsrs6(current, answers, scheduler)

            // No cards on a new installation: there is nothing to migrate, but
            // clearing a stale plan is still the only honest first FSRS-6 plan.
            cardDao.clear()
            rebuilt.cards.chunked(WRITE_BATCH).forEach { cardDao.upsertAll(it) }
            planDao.clear()

            // Replay writes cards visible. Apply the same absence rule as a
            // restore before any deck or worker can read the new table.
            cardDao.moveOverdueToAmnesty(now - config.amnestyAfterDays * DAY_MS)
            result = SchedulerMigrationResult(
                migratedCards = rebuilt.replayedCards,
                replayedAnswers = rebuilt.replayedAnswers
            )
        }

        // Written only after the transaction above committed. A failed write is
        // safe: the next launch repeats an idempotent replay.
        settings.setSchedulerVersion(CURRENT_SCHEDULER_VERSION)
        return result
    }

    private companion object {
        const val WRITE_BATCH = 500
    }
}

data class SchedulerReplay(
    val cards: List<CardEntity>,
    val replayedCards: Int,
    val replayedAnswers: Int
)

/**
 * Pure half of [SchedulerMigration], kept outside Room so histories can be
 * tested as ordinary JVM values.
 *
 * Only cards that exist now are returned. Reviews from a deck deleted months
 * ago remain in the append-only log for statistics, but a scheduler migration
 * must not resurrect that deck. A card with no surviving answer is preserved:
 * it is still new, and [Scheduler.apply] replaces its prior with the first real
 * FSRS-6 observation anyway.
 */
fun replayCardsForFsrs6(
    currentCards: List<CardEntity>,
    answers: List<ReviewEntity>,
    scheduler: Scheduler
): SchedulerReplay {
    val currentKeys = currentCards.asSequence().map { it.key }.toHashSet()
    val byCard = answers.asSequence()
        .filter { it.chunkId + ":" + it.level in currentKeys }
        .groupBy { it.chunkId + ":" + it.level }

    var replayedCards = 0
    var replayedAnswers = 0
    val rebuilt = ArrayList<CardEntity>(currentCards.size)

    for (current in currentCards) {
        val history = byCard[current.key].orEmpty()
            .sortedWith(compareBy<ReviewEntity> { it.ts }.thenBy { it.id })
        if (history.isEmpty()) {
            rebuilt += current
            continue
        }

        val first = history.first()
        var card = CardEntity(
            chunkId = current.chunkId,
            level = current.level,
            stability = first.prevStability ?: first.stabilityBefore,
            difficulty = first.prevDifficulty ?: first.difficultyBefore,
            dueAt = first.prevDueAt ?: first.ts,
            lastReviewAt = first.prevLastReviewAt,
            // Introduction is content history, not scheduler output. Keep its
            // real time instead of replacing it with the first answer time.
            introducedAt = current.introducedAt,
            reps = first.prevReps ?: 0,
            lapses = first.prevLapses ?: 0,
            inAmnesty = false,
            // Rows from schema v1 have no snapshot; the first logged answer for
            // a question was necessarily its first answer in that schema.
            isNew = first.prevIsNew ?: true
        )

        for (answer in history) {
            card = scheduler.apply(card, Rating.of(answer.rating), answer.ts).card
            replayedAnswers++
        }
        rebuilt += card
        replayedCards++
    }

    return SchedulerReplay(rebuilt, replayedCards, replayedAnswers)
}
