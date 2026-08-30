package dev.ikna.data.repo

import dev.ikna.data.db.CardDao
import dev.ikna.data.db.CardEntity
import dev.ikna.data.db.DailyStatEntity
import dev.ikna.data.db.PlanDao
import dev.ikna.data.db.ReviewDao
import dev.ikna.data.db.ReviewEntity
import dev.ikna.data.db.StatsDao
import dev.ikna.data.export.ReviewRecord
import dev.ikna.domain.fsrs.DAY_MS
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.GovernorConfig
import dev.ikna.domain.time.DayBoundary

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
 *
 * The one thing in the file that must NOT be taken literally is the row id. Ids
 * belong to the database that issued them, and a restore is normally into a
 * different one: a new phone, or an install that has been used for a week
 * before the file arrived. Importing them verbatim either collides with a row
 * that already exists or, worse, quietly attaches an old retraction to whatever
 * unrelated answer happens to hold that number now. Rows are therefore inserted
 * unnumbered, and the undo trail is re-pointed through the ids SQLite hands
 * back.
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

    suspend fun restoreFromJsonl(text: String): RestoreResult {
        var skipped = 0
        val records = ArrayList<ReviewRecord>()
        for (line in text.lineSequence()) {
            if (line.isBlank()) continue
            val rec = runCatching {
                ReviewRecord.json.decodeFromString(ReviewRecord.serializer(), line)
            }.getOrNull()
            if (rec == null) {
                skipped++
                continue
            }
            records += rec
        }

        // Signature -> the id that answer has *here*. Seeded with what is
        // already stored, so importing the same file twice is a no-op.
        val known = HashMap<String, Long>()
        for (k in reviewDao.keys()) known[k.signature] = k.id

        // Id in the file -> id here. This is what makes `undoOf` survive.
        val remap = HashMap<Long, Long>()

        val sorted = records.sortedBy { it.ts }

        // Pass one: the answers. They have to land before the retractions,
        // because a retraction is only meaningful once the row it points at
        // exists and has a number.
        val staged = HashSet<String>()
        val answers = ArrayList<Pair<ReviewRecord, ReviewEntity>>()
        for (rec in sorted) {
            if (rec.undoOf != null) continue
            val existing = known[rec.signature]
            if (existing != null) {
                // Already here. Still worth remembering, because a retraction
                // later in the same file may point at it.
                if (rec.id != 0L) remap[rec.id] = existing
                skipped++
                continue
            }
            if (!staged.add(rec.signature)) {
                skipped++
                continue
            }
            answers += rec to rec.toEntity()
        }
        var imported = insertRecords(answers, known, remap)

        // Pass two: the retractions, each re-pointed at the local id of the
        // answer it undoes. One whose target cannot be resolved is dropped
        // rather than imported dangling: a retraction of nothing would sit in
        // the log forever and hide a row that no longer exists.
        val stagedUndo = HashSet<String>()
        val retractions = ArrayList<Pair<ReviewRecord, ReviewEntity>>()
        for (rec in sorted) {
            val target = rec.undoOf ?: continue
            if (known.containsKey(rec.signature) || !stagedUndo.add(rec.signature)) {
                skipped++
                continue
            }
            val here = remap[target]
            if (here == null) {
                skipped++
                continue
            }
            retractions += rec to rec.toEntity(undoOf = here)
        }
        imported += insertRecords(retractions, known, remap)

        val replayed = replayFromLog()
        return RestoreResult(imported = imported, skipped = skipped, replayed = replayed)
    }

    /**
     * Inserts a batch and records the ids SQLite assigned, in order.
     *
     * Batched because a per-row insert is a transaction per row, and a log with
     * four months in it makes that the slowest thing the app ever does.
     */
    private suspend fun insertRecords(
        pending: List<Pair<ReviewRecord, ReviewEntity>>,
        known: MutableMap<String, Long>,
        remap: MutableMap<Long, Long>
    ): Int {
        var imported = 0
        for (part in pending.chunked(INSERT_BATCH)) {
            val ids = reviewDao.insertAll(part.map { it.second })
            for ((i, pair) in part.withIndex()) {
                val newId = ids.getOrNull(i) ?: continue
                if (newId <= 0L) continue
                val rec = pair.first
                known[rec.signature] = newId
                if (rec.id != 0L) remap[rec.id] = newId
                imported++
            }
        }
        return imported
    }

    /**
     * Rebuilds every derived table by replaying the log through the scheduler.
     * Retracted answers are already filtered out by the DAO, so an undo made
     * months ago stays undone.
     *
     * Everything is accumulated in memory and written once at the end. The old
     * version read and wrote one card per answer, which on a real log is two
     * SQLite round trips per row for a table small enough to hold whole — and
     * this runs on the main path of every restore.
     */
    suspend fun replayFromLog(): Int {
        cardDao.clear()
        statsDao.clear()
        planDao.clear()

        val answers = reviewDao.allAnswers()
        val boundary = DayBoundary(config.dayStartHour)
        val cards = LinkedHashMap<String, CardEntity>()
        val days = LinkedHashMap<String, DailyStatEntity>()

        for (r in answers) {
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
            val firstTime = !cards.containsKey(key)
            cards[key] = scheduler.apply(card, ratingOf(r.rating), r.ts).card

            // Same boundary as the live counters, or a restore would rebuild
            // stats that disagree with the app that wrote them.
            val day = boundary.key(r.ts)
            val stat = days[day] ?: DailyStatEntity(day, 0, 0, 0L, 1.0, false)
            val done = stat.reviewsDone + 1
            val correct = stat.correctCount + if (r.rating >= 3) 1 else 0
            days[day] = stat.copy(
                reviewsDone = done,
                correctCount = correct,
                newIntroduced = stat.newIntroduced + if (firstTime) 1 else 0,
                activeMs = stat.activeMs + r.durationMs,
                accuracy = correct.toDouble() / done,
                // Deliberately false, and deliberately not guessed.
                //
                // planCompleted means "the plan the governor built that day was
                // finished", and the plan is nowhere in the log — it is not an
                // answer, so it is not exported. Setting it from a card count
                // would invent five clean days out of a restore and hand the
                // user a larger daily target on their first evening back. The
                // accelerator restarts instead, and earns its way up again.
                planCompleted = false
            )
        }

        cards.values.chunked(WRITE_BATCH).forEach { cardDao.upsertAll(it) }
        days.values.chunked(WRITE_BATCH).forEach { statsDao.upsertAll(it) }

        // Every card above was written visible, because the log records when an
        // answer happened and never records what was hidden at the time. A file
        // that is two weeks old therefore lands as a queue with two weeks of
        // overdue cards in it at once — the avalanche the amnesty pool exists to
        // prevent, arriving on the first screen after a restore.
        //
        // The daily plan applies the same rule and would eventually apply it
        // here too, but "eventually" is whichever screen opens first, and the
        // deck list and the statistics screen both read cards. So the restored
        // database is made consistent here, once, before anything reads it.
        cardDao.moveOverdueToAmnesty(
            System.currentTimeMillis() - config.amnestyAfterDays * DAY_MS
        )

        components.rebuildFromReviews()
        return answers.size
    }

    private fun ratingOf(value: Int): Rating = when (value) {
        1 -> Rating.AGAIN
        2 -> Rating.HARD
        3 -> Rating.GOOD
        else -> Rating.EASY
    }

    private companion object {
        const val INSERT_BATCH = 500
        const val WRITE_BATCH = 500
    }
}
