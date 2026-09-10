package dev.ikna.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.ikna.data.export.ReviewRecord
import dev.ikna.data.repo.ComponentRepository
import dev.ikna.data.repo.RestoreRepository
import dev.ikna.data.repo.outcomeRating
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.GovernorConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end JSONL -> real Room -> replay -> undo/statistics regression gate. */
@RunWith(AndroidJUnit4::class)
class GradingRestoreTest {
    @Test fun derivedLogRestoresExactlyAndSyntheticImportDoesNothing() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "grading-restore-test.db"
        context.deleteDatabase(name)
        val db = buildIknaDatabase(Room.databaseBuilder<IknaDatabase>(
            context, context.getDatabasePath(name).absolutePath
        ))
        try {
            val scheduler = Scheduler(dayStartHour = 4)
            val restore = RestoreRepository(
                db.cardDao(), db.reviewDao(), db.statsDao(), db.planDao(),
                ComponentRepository(db.componentDao(), db.chunkDao(), db.reviewDao()),
                scheduler, GovernorConfig()
            )
            val start = System.currentTimeMillis() - 12 * 3_600_000L
            var live = CardEntity("one", 0, 1.0, 5.0, start, null, start, isNew = true)
            var beforeLast = live
            val records = ArrayList<ReviewRecord>()
            repeat(30) { i ->
                val before = live
                beforeLast = before
                val now = start + i * 60_000L
                val input = if (i % 7 == 0) Rating.AGAIN else Rating.GOOD
                val grade = if (input == Rating.AGAIN) input else when (i % 5) {
                    1 -> Rating.HARD
                    2 -> Rating.EASY
                    else -> input
                }
                val version = if (grade != input) 1 else null
                val weights = dev.ikna.domain.fsrs.FsrsParams.DEFAULT_W.mapIndexed { index, weight ->
                    if (i in 15..22 && index == 17) weight * 0.8 else weight
                }
                val liveScheduler = Scheduler(dev.ikna.domain.fsrs.FsrsParams(weights), dayStartHour = 4)
                val result = if (version == 1) liveScheduler.applyDerivedV1(before, grade, now)
                             else liveScheduler.apply(before, grade, now)
                live = result.card
                records += ReviewRecord.of(ReviewEntity(
                    id = 1_000L + i, chunkId = "one", level = 0, ts = now, rating = grade.value,
                    inputRating = input.value, gradingVersion = version,
                    fsrsParameters = if (i < 10) null else dev.ikna.domain.fsrs.FsrsSnapshotCodec.encode(liveScheduler.currentParameters()),
                    gradingReason = if (version == 1) "test" else "disabled",
                    presentationLength = 25, inputMethod = "swipe", peekSemantics = "required_reveal_verified_v2",
                    latencyMs = 1_000L + i, swipeVelocityX = 950f, peeked = false,
                    elapsedDays = result.elapsedDays,
                    stabilityBefore = before.stability, stabilityAfter = result.after.stability,
                    difficultyBefore = before.difficulty, difficultyAfter = result.after.difficulty,
                    durationMs = 2_000, wasAmnesty = false,
                    prevStability = before.stability, prevDifficulty = before.difficulty,
                    prevDueAt = before.dueAt, prevLastReviewAt = before.lastReviewAt,
                    prevReps = before.reps, prevLapses = before.lapses,
                    prevIsNew = before.isNew, prevInAmnesty = before.inAmnesty
                ))
            }
            records += ReviewRecord(id = 9_000, chunkId = "one", ts = records.last().ts + 1,
                rating = 0, undoOf = records.last().id)
            fun jsonl(rows: List<ReviewRecord>) = rows.joinToString("\n") {
                ReviewRecord.json.encodeToString(ReviewRecord.serializer(), it)
            }
            val first = restore.restoreFromJsonl(jsonl(records))
            assertEquals(31, first.imported)
            assertEquals(29, first.replayed)
            assertEquals(beforeLast, db.cardDao().card("one", 0))
            val stored = db.reviewDao().all()
            assertEquals(31, stored.size)
            assertEquals(stored[29].id, stored.last().undoOf)
            for (i in 0 until 30) {
                assertEquals(records[i].inputRating, stored[i].inputRating)
                assertEquals(records[i].gradingVersion, stored[i].gradingVersion)
                assertEquals(records[i].latencyMs, stored[i].latencyMs)
                assertEquals(records[i].fsrsParameters, stored[i].fsrsParameters)
            }
            val stats = db.statsDao().lastDays(365)
            assertEquals(29, stats.sumOf { it.reviewsDone })
            assertEquals(stored.take(29).count { it.outcomeRating >= 3 }, stats.sumOf { it.correctCount })
            assertEquals(58_000L, stats.sumOf { it.activeMs })
            val second = restore.restoreFromJsonl(jsonl(records))
            assertEquals(0, second.imported)
            assertEquals(stored, db.reviewDao().all())
            assertEquals(beforeLast, db.cardDao().card("one", 0))
            val rejected = restore.restoreFromJsonl(jsonl(records.map { it.copy(synthetic = true) }))
            assertEquals(0, rejected.imported)
            assertEquals(0, rejected.replayed)
            assertEquals(stored, db.reviewDao().all())
            assertEquals(beforeLast, db.cardDao().card("one", 0))
            for (bad in listOf(
                records.first().copy(gradingVersion = 99),
                records.first().copy(inputRating = 1, rating = 2, gradingVersion = 1),
                records.first().copy(fsrsParameters = "{\"formatVersion\":99}")
            )) {
                try {
                    restore.restoreFromJsonl(jsonl(listOf(
                        records.first().copy(chunkId = "must-not-be-inserted"), bad
                    )))
                    fail("An unsupported or inconsistent version must fail before insertion")
                } catch (_: IllegalArgumentException) {
                    assertEquals(stored, db.reviewDao().all())
                    assertNull(db.cardDao().card("must-not-be-inserted", 0))
                }
            }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
