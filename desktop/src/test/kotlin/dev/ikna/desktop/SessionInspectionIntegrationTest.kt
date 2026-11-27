package dev.ikna.desktop

import dev.ikna.data.db.*
import dev.ikna.data.export.ReviewRecord
import dev.ikna.data.repo.*
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.ChunkSelector
import dev.ikna.domain.governor.GovernorConfig
import dev.ikna.domain.session.ReviewPurpose
import kotlinx.coroutines.*
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

/** Real Room reads/merges; the captured open plan is not an Android runtime claim. */
class SessionInspectionIntegrationTest {
    private class Fixture {
        val home = Files.createTempDirectory("ikna-session-inspection-").toFile()
        val db = openIknaDatabase(home.resolve("profile.db"))
        val config = GovernorConfig()
        val components = ComponentRepository(db.componentDao(), db.chunkDao(), db.reviewDao(), db)
        val learning = LearningRepository(db, db.cardDao(), db.chunkDao(), db.reviewDao(), db.statsDao(),
            db.governorDao(), db.planDao(), db.browseDao(), components,
            Scheduler(dayStartHour = config.dayStartHour), ChunkSelector(), config)
        val restore = RestoreRepository(db, db.cardDao(), db.reviewDao(), db.statsDao(), db.planDao(),
            components, Scheduler(dayStartHour = config.dayStartHour), config).also {
            it.journalMergeGuard = { block -> learning.withJournalWriteLock(block) }
        }
        suspend fun seed() {
            for (pack in listOf("pack-a", "pack-b")) db.chunkDao().upsertPack(PackEntity(pack, 1, "en", 4, NOW))
            val ids = listOf("a", "b", "extra", "hard")
            db.chunkDao().insertChunksIgnore(ids.mapIndexed { i, id -> ChunkEntity(id, if (id == "b") "pack-b" else "pack-a", "en", "", "", "", 0, 0, i) })
            db.chunkDao().insertTokensIgnore(ids.map { ChunkTokenEntity(it, 0, it, it, "NOUN", true, true, 1.0) })
            db.chunkDao().upsertPackChunks(ids.mapIndexed { i, id -> PackChunkEntity(if (id == "b") "pack-b" else "pack-a", id, i) })
            db.cardDao().upsertAll(ids.map { id -> CardEntity(id, 0, 3.0, 5.0, NOW, NOW - 86400000,
                NOW - 86400000, 8, if (id == "hard") 4 else 0, isNew = false) })
            db.planDao().upsert(plan())
            db.statsDao().upsert(DailyStatEntity(DAY, 0, 0, 0, 1.0, false))
        }
        fun close() { db.close(); home.deleteRecursively() }
    }
    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val f = Fixture()
        try { f.seed(); block(f) } finally { f.close() }
    }
    private data class State(val reviews: List<ReviewEntity>, val cards: List<CardEntity>,
        val components: List<ComponentEntity>, val stats: List<DailyStatEntity>, val plan: DailyPlanEntity?,
        val governor: List<GovernorLogEntity>, val boundary: HistoryBoundaryEntity?)
    private suspend fun state(f: Fixture) = State(f.db.reviewDao().all(), f.db.cardDao().all(),
        f.db.componentDao().all(), f.db.statsDao().lastDays(Int.MAX_VALUE), f.db.planDao().latest(),
        f.db.governorDao().recent(Int.MAX_VALUE), f.db.historyBoundaryDao().current())

    @Test fun `changed merge leaves captured queue stale but read only inspection pauses with committed counts`() = runBlocking {
        fixture { f ->
            val open = f.learning.buildSession(now = NOW)
            assertEquals(listOf("a", "b", "extra"), open.cards.map { it.chunk.id })
            assertEquals(1, f.restore.mergeJournal(listOf(answer("peer-b", "b"))).imported)
            assertNull(f.db.planDao().plan(DAY))
            assertTrue(open.cards.any { it.chunk.id == "b" })
            assertEquals(0, open.answeredToday)
            val committed = state(f)
            val read = f.learning.inspectSession(now = NOW)
            assertNull(read.plan); assertFalse(read.requiredComplete)
            assertEquals(setOf("b:0"), read.answeredKeys)
            assertEquals(1, read.answeredToday); assertEquals(1, read.goalAnswersToday)
            assertEquals(1L, read.observation.reviewCount)
            assertEquals(committed, state(f))
        }
    }

    @Test fun `existing plan inspection removes remote answered tail and preserves unanswered explicit extra and scope`() = runBlocking {
        fixture { f ->
            val open = f.learning.buildSession(now = NOW)
            f.restore.mergeJournal(listOf(answer("peer-b", "b")))
            // A different caller has already supplied a persisted plan. Inspection itself must not rebuild it.
            f.db.planDao().upsert(plan())
            val before = state(f)
            val scoped = f.learning.inspectSession("pack-a", NOW)
            assertEquals(listOf("a", "extra"), scoped.plan!!.cards.map { it.chunk.id })
            assertEquals(2, scoped.plan!!.sessionTotal); assertEquals(0, scoped.plan!!.sessionDone)
            assertFalse(scoped.requiredComplete); assertEquals(before, state(f))
            f.learning.answer(open.cards.first(), Rating.AGAIN, 100, NOW + 1)
            val committed = state(f)
            val all = f.learning.inspectSession(now = NOW + 2)
            assertTrue(all.requiredComplete)
            assertEquals(listOf("extra"), all.plan!!.cards.map { it.chunk.id })
            assertEquals(3, all.plan!!.sessionTotal); assertEquals(2, all.plan!!.sessionDone)
            assertEquals(2, all.answeredToday); assertEquals(2, all.goalAnswersToday)
            assertEquals(1, all.observation.storedPlan!!.extraRequested)
            assertEquals(committed, state(f))
        }
    }

    @Test fun `missing empty and changed study day inspections never create a plan or admit material`() = runBlocking {
        fixture { f ->
            f.learning.loadSettings = { error("inspection must not load settings through plan creation") }
            f.db.planDao().clear()
            var before = state(f)
            assertNull(f.learning.inspectSession(now = NOW).plan)
            assertEquals(before, state(f))
            f.db.planDao().upsert(plan().copy(plannedIds = "", plannedTotal = 0, extraRequested = 0))
            before = state(f)
            val empty = f.learning.inspectSession(now = NOW)
            assertNotNull(empty.plan); assertTrue(empty.requiredComplete)
            val next = f.learning.inspectSession(now = NOW + 86400000)
            assertNull(next.plan); assertFalse(next.requiredComplete)
            assertNotEquals(empty.observation.day, next.observation.day)
            assertNotEquals(empty.observation.dayStart, next.observation.dayStart)
            assertEquals(before, state(f))
        }
    }

    @Test fun `observation detects undo plan identity and boundary changes with equal factual totals`() = runBlocking {
        fixture { f ->
            val first = f.learning.inspectSession(now = NOW)
            assertEquals(first, f.learning.inspectSession(now = NOW + 1))
            val card = first.plan!!.cards.first()
            f.learning.answer(card, Rating.GOOD, 100, NOW)
            assertEquals("a:0", f.learning.undoLast(NOW + 1))
            val undo = f.learning.inspectSession(now = NOW + 2)
            assertEquals(first.answeredToday, undo.answeredToday)
            assertNotEquals(first.observation, undo.observation)
            assertEquals(2L, undo.observation.reviewCount)
            assertEquals(2L, undo.observation.reviewHighWater)
            f.db.planDao().upsert(plan().copy(extraRequested = 0))
            val changedPlan = f.learning.inspectSession(now = NOW + 2)
            assertEquals(undo.observation.storedPlan!!.createdAt, changedPlan.observation.storedPlan!!.createdAt)
            assertNotEquals(undo.observation, changedPlan.observation)
            f.db.historyBoundaryDao().put(HistoryBoundaryEntity(afterLocalId = 0, token = "inspection-boundary"))
            val boundary = f.learning.inspectSession(now = NOW + 2)
            assertNotEquals(changedPlan.observation, boundary.observation)
            f.learning.syncHistoryBoundary = { 0L }
            f.learning.historyBoundaryToken = { "configured-inspection-boundary" }
            assertNotEquals(boundary.observation, f.learning.inspectSession(now = NOW + 2).observation)
        }
    }

    @Test fun `incoming historical optional purpose only increases factual totals`() = runBlocking {
        fixture { f ->
            val open = f.learning.buildSession(now = NOW)
            for (card in open.cards.take(2)) f.learning.answer(card, Rating.GOOD, 100, NOW)
            val before = state(f)
            val inspected = f.learning.inspectSession(now = NOW + 1)
            assertTrue(inspected.requiredComplete)
            assertEquals(before, state(f))
            f.restore.mergeJournal(listOf(answer("peer-optional", "hard", ReviewPurpose.OPTIONAL_HARD)))
            val committed = state(f)
            val remote = f.learning.inspectSession(now = NOW + 3)
            assertNull(remote.plan)
            assertEquals(3, remote.answeredToday); assertEquals(2, remote.goalAnswersToday)
            assertEquals(setOf("a:0", "b:0"), remote.answeredKeys)
            assertEquals(committed, state(f))
        }
    }

    @Test fun `duplicate merge leaves observation unchanged and incoming undo changes durable pending state`() = runBlocking {
        fixture { f ->
            val row = answer("peer-b", "b")
            f.restore.mergeJournal(listOf(row)); f.db.planDao().upsert(plan())
            val first = f.learning.inspectSession(now = NOW)
            assertEquals(0, f.restore.mergeJournal(listOf(row)).imported)
            assertEquals(first, f.learning.inspectSession(now = NOW))
            f.restore.mergeJournal(listOf(row.copy(eventId = "peer-undo", rating = 0, durationMs = 0, undoEventId = row.eventId)))
            val missing = f.learning.inspectSession(now = NOW)
            assertNull(missing.plan); assertEquals(0, missing.answeredToday); assertTrue(missing.answeredKeys.isEmpty())
            assertNotEquals(first.observation, missing.observation)
            f.db.planDao().upsert(plan())
            assertEquals(listOf("a", "b", "extra"), f.learning.inspectSession(now = NOW).plan!!.cards.map { it.chunk.id })
        }
    }

    @Test fun `inspection waits for accepted in flight answer and returns one complete committed state`() = runBlocking {
        fixture { f ->
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val learning = repository(f, reviews = object : ReviewDao by f.db.reviewDao() {
                override suspend fun insert(review: ReviewEntity): Long {
                    val id = f.db.reviewDao().insert(review)
                    entered.complete(Unit); release.await(); return id
                }
            })
            val card = learning.buildSession(now = NOW).cards.first()
            val answerJob = async { learning.answer(card, Rating.GOOD, 100, NOW) }
            var inspectJob: Deferred<dev.ikna.domain.session.CommittedSessionSnapshot>? = null
            try {
                withTimeout(5000) { entered.await() }
                val pending = async(start = CoroutineStart.UNDISPATCHED) { learning.inspectSession(now = NOW) }
                inspectJob = pending
                assertFalse(pending.isCompleted)
                release.complete(Unit)
                withTimeout(5000) { answerJob.await() }
                val read = withTimeout(5000) { pending.await() }
                assertEquals(1L, read.observation.reviewCount); assertEquals(1, read.answeredToday)
                assertEquals(setOf("a:0"), read.answeredKeys)
                assertEquals(listOf("b", "extra"), read.plan!!.cards.map { it.chunk.id })
                assertEquals(9, f.db.cardDao().card("a", 0)!!.reps)
                assertEquals(100L, f.db.statsDao().day(DAY)!!.activeMs)
            } finally { release.complete(Unit); answerJob.cancelAndJoin(); inspectJob?.cancelAndJoin() }
        }
    }

    @Test fun `originating caller cancellation inside Room inspection is honored without writes`() = runBlocking {
        fixture { f ->
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val learning = repository(f, stats = object : StatsDao by f.db.statsDao() {
                override suspend fun day(day: String): DailyStatEntity? {
                    val result = f.db.statsDao().day(day)
                    entered.complete(Unit); release.await(); return result
                }
            })
            val before = state(f)
            val read = async { learning.inspectSession(now = NOW) }
            try {
                withTimeout(5000) { entered.await() }
                read.cancel(); release.complete(Unit)
                withTimeout(5000) { read.join() }
                assertTrue(read.isCancelled)
                assertEquals(before, state(f))
            } finally { release.complete(Unit); read.cancelAndJoin() }
        }
    }

    private fun repository(f: Fixture, reviews: ReviewDao = f.db.reviewDao(), stats: StatsDao = f.db.statsDao()) =
        LearningRepository(f.db, f.db.cardDao(), f.db.chunkDao(), reviews, stats, f.db.governorDao(),
            f.db.planDao(), f.db.browseDao(), f.components, Scheduler(dayStartHour = f.config.dayStartHour),
            ChunkSelector(), f.config)

    companion object {
        private val NOW = LocalDateTime.of(2026, 10, 12, 12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        private const val DAY = "2026-10-12"
        private fun plan() = DailyPlanEntity(DAY, "a:0,b:0,extra:0", 3, 40, 0, 0, "NO_HEADROOM", 1, NOW - 2000)
        private fun answer(id: String, target: String, purpose: String? = null) = ReviewRecord(chunkId = target,
            ts = NOW, rating = 3, eventId = id, durationMs = 100, purpose = purpose,
            prevStability = 3.0, prevDifficulty = 5.0, prevDueAt = NOW, prevLastReviewAt = NOW - 86400000,
            prevReps = 8, prevLapses = if (target == "hard") 4 else 0, prevIsNew = false, prevInAmnesty = false)
    }
}
