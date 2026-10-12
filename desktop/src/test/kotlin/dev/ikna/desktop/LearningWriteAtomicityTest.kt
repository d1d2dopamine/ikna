package dev.ikna.desktop

import dev.ikna.data.db.*
import dev.ikna.data.repo.ComponentRepository
import dev.ikna.data.repo.LearningRepository
import dev.ikna.domain.fsrs.ComponentPrior
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.ChunkSelector
import dev.ikna.domain.governor.GovernorConfig
import dev.ikna.domain.session.Level
import dev.ikna.domain.session.SessionCard
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Actual Room writes, synthetic fixtures, disposable files; never opens learner data. */
class LearningWriteAtomicityTest {
    private enum class Stage { CARD, REVIEW, COMPONENTS, STATS, EARNED_CARD, EARNED_STATS, PLAN,
        PROMOTION_DELETE, PROMOTION_STATS }
    private val answerStages = listOf(Stage.CARD, Stage.REVIEW, Stage.COMPONENTS, Stage.STATS,
        Stage.EARNED_CARD, Stage.EARNED_STATS, Stage.PLAN)
    private val undoStages = listOf(Stage.CARD, Stage.PROMOTION_DELETE, Stage.PROMOTION_STATS,
        Stage.REVIEW, Stage.STATS)

    @Test fun `answer failure after every write rolls back and retry commits once`() = runBlocking {
        verifyFailures(undo = false)
    }
    @Test fun `undo failure after every write rolls back and retry retracts once`() = runBlocking {
        verifyFailures(undo = true)
    }
    @Test fun `answer cancellation at every write including commit gate rolls back`() = runBlocking {
        verifyCancellation(undo = false)
    }
    @Test fun `undo cancellation at every write including commit gate rolls back`() = runBlocking {
        verifyCancellation(undo = true)
    }

    private suspend fun verifyFailures(undo: Boolean) {
        for (stage in if (undo) undoStages else answerStages) withFixture(undo) { fixture ->
            val before = snapshot(fixture.db)
            val repo = repository(fixture.db, undo) { if (it == stage) error("injected $stage") }
            try {
                operate(repo, fixture.session, undo)
                fail("Expected injected $stage")
            } catch (e: IllegalStateException) {
                assertEquals("injected $stage", e.message)
            }
            assertEquals("Rollback after $stage", before, snapshot(fixture.db))
            fixture.reopen()
            assertEquals("Durable rollback after $stage", before, snapshot(fixture.db))
            retryAndAssert(fixture, undo, before)
        }
    }

    private suspend fun verifyCancellation(undo: Boolean) = coroutineScope {
        for (stage in if (undo) undoStages else answerStages) withFixture(undo) { fixture ->
            val before = snapshot(fixture.db)
            val reached = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val repo = repository(fixture.db, undo) {
                if (it == stage) { reached.complete(Unit); release.await() }
            }
            val operation = launch { operate(repo, fixture.session, undo) }
            try {
                withTimeout(10_000) { reached.await() }
                operation.cancel()
                release.complete(Unit)
                withTimeout(10_000) { operation.join() }
                assertTrue(operation.isCancelled)
            } finally {
                release.complete(Unit)
                operation.cancelAndJoin()
            }
            assertEquals("Cancelled at $stage", before, snapshot(fixture.db))
            fixture.reopen()
            assertEquals("Durable cancellation at $stage", before, snapshot(fixture.db))
            retryAndAssert(fixture, undo, before)
        }
    }

    private suspend fun retryAndAssert(f: Fixture, undo: Boolean, before: Snapshot) {
        val repo = repository(f.db)
        operate(repo, f.session, undo)
        val committed = snapshot(f.db)
        assertEquals(before.reviews.size + 1, committed.reviews.size)
        val stat = requireNotNull(committed.stat)
        if (undo) {
            assertEquals(1, committed.reviews.count { it.undoOf != null })
            assertEquals(0, f.db.reviewDao().allAnswers().size)
            assertEquals(f.session.card, f.db.cardDao().card(TARGET, 0))
            assertNull(f.db.cardDao().card(TARGET, 1))
            assertEquals(before.stat!!.reviewsDone - 1, stat.reviewsDone)
            assertEquals(before.stat.newIntroduced - 1, stat.newIntroduced)
            assertEquals(before.components, committed.components) // Existing undo policy retains exposures.
            assertNull(repo.undoLast(NOW + 2))
            assertEquals(committed, snapshot(f.db))
        } else {
            assertEquals(1, committed.reviews.count { it.undoOf == null })
            assertEquals(1, f.db.cardDao().card(TARGET, 0)!!.reps)
            assertEquals(1.0, committed.components.single().exposures, 0.0)
            assertEquals(before.stat!!.reviewsDone + 1, stat.reviewsDone)
            assertEquals(before.stat.newIntroduced + 1, stat.newIntroduced)
            assertEquals(before.stat.activeMs + 1200, stat.activeMs)
            assertEquals(before.plan!!.plannedTotal + 1, committed.plan!!.plannedTotal)
            assertNotNull(f.db.cardDao().card(FRESH, 0))
        }
        f.reopen()
        assertEquals("Successful retry survives reopen", committed, snapshot(f.db))
    }

    private suspend fun operate(repo: LearningRepository, session: SessionCard, undo: Boolean) {
        if (undo) assertEquals("$TARGET:0", repo.undoLast(NOW + 1))
        else repo.answer(session, Rating.GOOD, 1200, NOW)
    }

    private fun repository(db: IknaDatabase, undo: Boolean = false, after: suspend (Stage) -> Unit = {}): LearningRepository {
        val cards = db.cardDao()
        val reviews = db.reviewDao()
        val stats = db.statsDao()
        val plans = db.planDao()
        val components = db.componentDao()
        var statWrites = 0
        return LearningRepository(
            db = db,
            cardDao = object : CardDao by cards {
                override suspend fun upsert(card: CardEntity) { cards.upsert(card); after(Stage.CARD) }
                override suspend fun upsertAll(cardsToWrite: List<CardEntity>) {
                    cards.upsertAll(cardsToWrite); after(Stage.EARNED_CARD)
                }
                override suspend fun delete(chunkId: String, level: Int) {
                    cards.delete(chunkId, level); after(Stage.PROMOTION_DELETE)
                }
            },
            chunkDao = db.chunkDao(),
            reviewDao = object : ReviewDao by reviews {
                override suspend fun insert(review: ReviewEntity): Long {
                    val id = reviews.insert(review); after(Stage.REVIEW); return id
                }
            },
            statsDao = object : StatsDao by stats {
                override suspend fun upsert(stat: DailyStatEntity) {
                    stats.upsert(stat)
                    statWrites++
                    after(if (undo && statWrites == 1) Stage.PROMOTION_STATS
                        else if (!undo && statWrites == 2) Stage.EARNED_STATS else Stage.STATS)
                }
            },
            governorDao = db.governorDao(),
            planDao = object : PlanDao by plans {
                override suspend fun upsert(plan: DailyPlanEntity) { plans.upsert(plan); after(Stage.PLAN) }
            },
            browseDao = db.browseDao(),
            components = ComponentRepository(object : ComponentDao by components {
                override suspend fun upsertAll(items: List<ComponentEntity>) {
                    components.upsertAll(items); after(Stage.COMPONENTS)
                }
            }, db.chunkDao(), db.reviewDao()),
            scheduler = Scheduler(), selector = ChunkSelector(),
            baseConfig = GovernorConfig(earnedNewPerReviews = 1)
        )
    }

    private data class Snapshot(val cards: List<CardEntity>, val reviews: List<ReviewEntity>,
        val components: List<ComponentEntity>, val stat: DailyStatEntity?, val plan: DailyPlanEntity?)
    private suspend fun snapshot(db: IknaDatabase) = Snapshot(db.cardDao().all(), db.reviewDao().all(),
        db.componentDao().all(), db.statsDao().day(DAY), db.planDao().plan(DAY))

    private class Fixture(val file: java.io.File, var db: IknaDatabase, val session: SessionCard) {
        fun reopen() { db.close(); db = openIknaDatabase(file) }
    }
    private suspend fun withFixture(undo: Boolean, block: suspend (Fixture) -> Unit) {
        val home = Files.createTempDirectory("ikna-dev-learning-atomic-").toFile()
        val file = home.resolve("disposable.db")
        val db = openIknaDatabase(file)
        val chunk = ChunkEntity(TARGET, PACK, "en", "sample", "sample", "DEV meaning", 0, 6, 1)
        val card = Scheduler().introduce(TARGET, 0, ComponentPrior(0.0, 1, emptyList()), NOW - 1000)
        val f = Fixture(file, db, SessionCard(card, chunk, Level.RECOGNITION, false))
        try {
            db.chunkDao().upsertPack(PackEntity(PACK, 1, "en", 2, NOW))
            db.chunkDao().insertChunksIgnore(listOf(chunk, chunk.copy(id = FRESH, freqRank = 2)))
            db.chunkDao().upsertPackChunks(listOf(PackChunkEntity(PACK, TARGET, 1), PackChunkEntity(PACK, FRESH, 2)))
            db.chunkDao().insertTokensIgnore(listOf(TARGET, FRESH).map {
                ChunkTokenEntity(it, 0, "sample", "sample", "NOUN", true, true, 1.0)
            })
            db.cardDao().upsert(card)
            db.statsDao().upsert(DailyStatEntity(DAY, 1, 1, 1000, 1.0, true, 1))
            db.planDao().upsert(DailyPlanEntity(DAY, "$TARGET:0", 1, 40, 1, 0, "OK", 0, NOW))
            if (undo) {
                repository(db).answer(f.session, Rating.GOOD, 1200, NOW)
                // Compatibility branch: undo can retract a historical untouched promotion.
                db.cardDao().upsert(card.copy(level = 1, introducedAt = NOW))
                val stat = requireNotNull(db.statsDao().day(DAY))
                db.statsDao().upsert(stat.copy(newIntroduced = stat.newIntroduced + 1))
            }
            block(f)
        } finally { f.db.close(); home.deleteRecursively() }
    }
    private companion object {
        const val TARGET = "dev-atomic-target"
        const val FRESH = "dev-atomic-fresh"
        const val PACK = "dev-atomic-pack"
        val NOW = LocalDateTime.of(2026, 10, 12, 12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        const val DAY = "2026-10-12"
    }
}
