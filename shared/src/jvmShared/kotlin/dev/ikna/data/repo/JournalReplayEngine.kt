package dev.ikna.data.repo

import dev.ikna.data.db.CardEntity
import dev.ikna.data.db.DailyStatEntity
import dev.ikna.data.db.ReviewEntity
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.time.DayBoundary

/**
 * Pure, deterministic replay engine shared across backup restore, paired sync,
 * and FSRS scheduler migrations.
 *
 * The append-only `reviews` journal is the single source of truth; card schedules
 * and daily statistics are derived projections recomputed through this engine.
 */
object JournalReplayEngine {

    data class FullReplayResult(
        val cards: LinkedHashMap<String, CardEntity>,
        val days: LinkedHashMap<String, DailyStatEntity>,
        val replayedAnswers: Int
    )

    /**
     * Replays an ordered list of non-retracted [answers] into card states and
     * daily statistics. When [sync] is true, selects the least-advanced pre-state
     * baseline across [syncOriginals] (protecting against clock rollbacks) and
     * preserves existing amnesty/local introduction counters.
     */
    fun replayFullHistory(
        answers: List<ReviewEntity>,
        scheduler: Scheduler,
        dayStartHour: Int,
        sync: Boolean = false,
        syncOriginals: List<ReviewEntity> = emptyList(),
        syncIdentities: Map<Long, String> = emptyMap(),
        localCards: Map<String, CardEntity> = emptyMap(),
        localDays: Map<String, DailyStatEntity> = emptyMap(),
        onStep: () -> Unit = {}
    ): FullReplayResult {
        val boundary = DayBoundary(dayStartHour)
        val cards = LinkedHashMap<String, CardEntity>()
        val days = LinkedHashMap<String, DailyStatEntity>()
        val seen = HashSet<String>()

        if (sync) {
            val originals = syncOriginals
                .filter { it.undoOf == null }
                .groupBy { it.chunkId + ":" + it.level }
            val baselineOrder = compareBy<ReviewEntity> { it.prevReps ?: 0 }
                .thenBy { if (it.prevIsNew == true) 0 else 1 }
                .thenBy { it.ts }
                .thenBy { syncIdentities[it.id] }
            for ((key, history) in originals) {
                val r = history.minWith(baselineOrder)
                cards[key] = CardEntity(
                    chunkId = r.chunkId,
                    level = r.level,
                    stability = r.prevStability ?: r.stabilityBefore,
                    difficulty = r.prevDifficulty ?: r.difficultyBefore,
                    dueAt = r.prevDueAt ?: r.ts,
                    lastReviewAt = r.prevLastReviewAt,
                    introducedAt = history.minOf { it.ts },
                    reps = r.prevReps ?: 0,
                    lapses = r.prevLapses ?: 0,
                    inAmnesty = localCards[key]?.inAmnesty ?: false,
                    isNew = r.prevIsNew ?: true
                )
            }
        }

        for (r in answers) {
            onStep()
            val key = r.chunkId + ":" + r.level
            val card = cards[key] ?: CardEntity(
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
            val firstTime = if (sync) seen.add(key) else !cards.containsKey(key)
            cards[key] = scheduler.applyRecordedReview(card, r).card.let { rebuilt ->
                if (sync) {
                    require(
                        rebuilt.stability.isFinite() &&
                            rebuilt.difficulty.isFinite() &&
                            rebuilt.dueAt >= 0 &&
                            rebuilt.reps >= 0 &&
                            rebuilt.lapses >= 0
                    ) { "Invalid replay state" }
                    rebuilt.copy(inAmnesty = localCards[key]?.inAmnesty ?: false)
                } else {
                    rebuilt
                }
            }

            val day = boundary.key(r.ts)
            val stat = days[day] ?: DailyStatEntity(day, 0, 0, 0L, 1.0, false)
            val done = Math.addExact(stat.reviewsDone, 1)
            val correct = Math.addExact(stat.correctCount, if (r.outcomeRating >= 3) 1 else 0)
            days[day] = stat.copy(
                reviewsDone = done,
                correctCount = correct,
                newIntroduced = Math.addExact(stat.newIntroduced, if (firstTime && r.purpose == null) 1 else 0),
                activeMs = Math.addExact(stat.activeMs, r.durationMs),
                accuracy = correct.toDouble() / done,
                planCompleted = if (sync) localDays[day]?.planCompleted ?: false else false
            )
        }

        if (sync) {
            for ((day, old) in localDays) {
                val rebuilt = days[day]
                days[day] = if (rebuilt == null) {
                    old.copy(reviewsDone = 0, correctCount = 0, activeMs = 0, accuracy = 1.0)
                } else {
                    rebuilt.copy(newIntroduced = maxOf(rebuilt.newIntroduced, old.newIntroduced))
                }
            }
        }

        return FullReplayResult(cards = cards, days = days, replayedAnswers = answers.size)
    }

    /**
     * Replays only currently existing cards (preserving untouched/new cards and original
     * `introducedAt`), used by one-time FSRS scheduler migrations.
     */
    fun replayExistingCards(
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
                introducedAt = current.introducedAt,
                reps = first.prevReps ?: 0,
                lapses = first.prevLapses ?: 0,
                inAmnesty = false,
                isNew = first.prevIsNew ?: true
            )

            for (answer in history) {
                card = scheduler.applyRecordedReview(card, answer).card
                replayedAnswers++
            }
            rebuilt += card
            replayedCards++
        }

        return SchedulerReplay(rebuilt, replayedCards, replayedAnswers)
    }
}
