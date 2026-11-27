package dev.ikna.desktop

import dev.ikna.data.db.*
import dev.ikna.data.export.ReviewRecord
import dev.ikna.data.repo.*
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.ChunkSelector
import dev.ikna.domain.governor.GovernorConfig
import dev.ikna.domain.session.*
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

/** Real Room completion semantics; technical identifiers only. */
class CompletionBoundaryIntegrationTest {
    private class Fixture {
        val home = Files.createTempDirectory("ikna-completion-boundary-").toFile()
        val file = home.resolve("profile.db")
        var db = openIknaDatabase(file)
        val config = GovernorConfig()
        fun repo() = LearningRepository(db, db.cardDao(), db.chunkDao(), db.reviewDao(), db.statsDao(),
            db.governorDao(), db.planDao(), db.browseDao(),
            ComponentRepository(db.componentDao(), db.chunkDao(), db.reviewDao(), db),
            Scheduler(dayStartHour = config.dayStartHour), ChunkSelector(), config)
        suspend fun seed(twoDecks: Boolean) {
            for (pack in listOf("pack-a", "pack-b")) db.chunkDao().upsertPack(PackEntity(pack, 1, "en", 2, NOW))
            val ids = listOf("required", "other", "hard")
            db.chunkDao().insertChunksIgnore(ids.mapIndexed { i, id -> ChunkEntity(id, if (id == "other") "pack-b" else "pack-a", "en", "", "", "", 0, 0, i) })
            db.chunkDao().insertTokensIgnore(ids.map { ChunkTokenEntity(it, 0, it, it, "NOUN", true, true, 1.0) })
            db.chunkDao().upsertPackChunks(ids.mapIndexed { i, id -> PackChunkEntity(if (id == "other") "pack-b" else "pack-a", id, i) })
            db.cardDao().upsertAll(ids.map { id ->
                CardEntity(id, 0, if (id == "required") 0.0 else 3.0, 5.0, NOW, null, NOW - 5000,
                    reps = if (id == "required") 0 else 8, lapses = if (id == "hard") 4 else 0, isNew = id == "required")
            })
            val keys = if (twoDecks) "required:0,other:0" else "required:0"
            db.planDao().upsert(DailyPlanEntity(DAY, keys, if (twoDecks) 2 else 1, 40, 1, 0, "NO_HEADROOM", 0, NOW - 2000))
            db.statsDao().upsert(DailyStatEntity(DAY, 0, 1, 0, 1.0, false))
        }
        fun reopen() { db.close(); db = openIknaDatabase(file) }
        fun close() { db.close(); home.deleteRecursively() }
    }
    private suspend fun fixture(twoDecks: Boolean = false, block: suspend (Fixture) -> Unit) {
        val f = Fixture()
        try { f.seed(twoDecks); block(f) } finally { f.close() }
    }

    private data class State(val reviews: List<ReviewEntity>, val cards: List<CardEntity>,
        val components: List<ComponentEntity>, val stats: List<DailyStatEntity>, val plan: DailyPlanEntity?,
        val governor: List<GovernorLogEntity>, val boundary: HistoryBoundaryEntity?)
    private suspend fun state(f: Fixture) = State(f.db.reviewDao().all(), f.db.cardDao().all(),
        f.db.componentDao().all(), f.db.statsDao().lastDays(Int.MAX_VALUE), f.db.planDao().latest(),
        f.db.governorDao().recent(Int.MAX_VALUE), f.db.historyBoundaryDao().current())

    @Test fun `first contact good or again completes the required key and restart and undo agree`() = runBlocking {
        for (rating in listOf(Rating.GOOD, Rating.AGAIN)) fixture { f ->
            val repo = f.repo()
            val loaded = repo.buildSession(now = NOW)
            val card = loaded.cards.single()
            assertTrue(card.isFirstContact); assertFalse(repo.requiredPlanComplete(NOW))
            val oracle = Scheduler(dayStartHour = f.config.dayStartHour).apply(card.card, rating, NOW).card
            repo.answer(card, rating, 100, NOW)
            assertEquals(oracle, f.db.cardDao().card("required", 0))
            val committed = state(f)
            assertTrue(repo.requiredPlanComplete(NOW))
            assertTrue(f.db.statsDao().day(DAY)!!.planCompleted)
            assertEquals(1, f.db.reviewDao().all().size)
            assertEquals(committed, state(f))
            assertTrue(repo.buildSession(now = NOW).cards.isEmpty())
            f.reopen()
            val restarted = f.repo()
            assertTrue(restarted.requiredPlanComplete(NOW + 1))
            assertTrue(restarted.buildSession(now = NOW + 1).cards.isEmpty())
            assertEquals("required:0", restarted.undoLast(NOW + 2))
            assertFalse(restarted.requiredPlanComplete(NOW + 2))
            assertFalse(f.db.statsDao().day(DAY)!!.planCompleted)
            assertEquals(listOf("required"), restarted.buildSession(now = NOW + 2).cards.map { it.chunk.id })
        }
    }

    @Test fun `a completed deck does not complete another decks unanswered required key`() = runBlocking {
        fixture(twoDecks = true) { f ->
            val repo = f.repo()
            val a = repo.buildSession("pack-a", NOW)
            assertEquals(1, a.cards.size)
            repo.answer(a.cards.single(), Rating.AGAIN, 100, NOW)
            val finishedDeck = repo.buildSession("pack-a", NOW + 1)
            assertEquals(1, finishedDeck.sessionDone); assertEquals(1, finishedDeck.sessionTotal)
            assertTrue(finishedDeck.cards.isEmpty())
            assertFalse(f.db.statsDao().day(DAY)!!.planCompleted)
            assertFalse(repo.requiredPlanComplete(NOW + 1))
            assertEquals(listOf("other"), repo.buildSession(now = NOW + 1).cards.map { it.chunk.id })
        }
    }

    @Test fun `missing versus empty existing plan is read only`() = runBlocking {
        fixture { f ->
            val repo = f.repo()
            f.db.planDao().clear()
            var before = state(f)
            assertFalse(repo.requiredPlanComplete(NOW)); assertEquals(before, state(f))
            f.db.planDao().upsert(DailyPlanEntity(DAY, "", 0, 40, 0, 0, "NO_HEADROOM", 0, NOW))
            before = state(f)
            assertTrue(repo.requiredPlanComplete(NOW))
            assertEquals(before, state(f))
            assertFalse(repo.requiredPlanComplete(NOW + 86_400_000L)); assertEquals(before, state(f))
        }
    }

    @Test fun `repeated answers cannot replace another required key or trust cached completion`() = runBlocking {
        fixture(twoDecks = true) { f ->
            val repo = f.repo()
            val first = repo.buildSession("pack-a", NOW).cards.single()
            repeat(3) { repo.answer(first, Rating.AGAIN, 100, NOW + it) }
            assertEquals(3, repo.answeredToday(NOW + 3))
            f.db.statsDao().upsert(f.db.statsDao().day(DAY)!!.copy(planCompleted = true))
            assertFalse(repo.requiredPlanComplete(NOW + 3))
            val other = repo.buildSession("pack-b", NOW + 3).cards.single()
            repo.answer(other, Rating.AGAIN, 100, NOW + 4)
            assertTrue(repo.requiredPlanComplete(NOW + 4))
        }
    }

    @Test fun `explicit unanswered extras remain queued without moving the required boundary`() = runBlocking {
        fixture { f ->
            val repo = f.repo()
            repo.answer(repo.buildSession(now = NOW).cards.single(), Rating.GOOD, 100, NOW)
            assertTrue(repo.requiredPlanComplete(NOW))
            val original = f.db.planDao().plan(DAY)!!
            assertEquals(1, repo.addExtra(count = 1, deckId = "pack-b", now = NOW + 1))
            val withExtra = f.db.planDao().plan(DAY)!!
            assertEquals(original.ids, withExtra.ids.take(withExtra.ids.size - withExtra.extraRequested))
            assertTrue(repo.requiredPlanComplete(NOW + 1))
            assertEquals(listOf("other"), repo.buildSession(now = NOW + 1).cards.map { it.chunk.id })
            repo.answer(repo.buildSession(now = NOW + 1).cards.single(), Rating.AGAIN, 100, NOW + 2)
            assertTrue(repo.requiredPlanComplete(NOW + 2))
            assertTrue(repo.buildSession(now = NOW + 2).cards.isEmpty())
            assertEquals("other:0", repo.undoLast(NOW + 3))
            assertTrue(repo.requiredPlanComplete(NOW + 3))
            assertEquals(listOf("other"), repo.buildSession(now = NOW + 3).cards.map { it.chunk.id })
        }
    }

    @Test fun `an existing extras-only plan keeps its unanswered voluntary work`() = runBlocking {
        fixture { f ->
            f.db.planDao().upsert(DailyPlanEntity(DAY, "other:0", 1, 40, 0, 0, "NO_HEADROOM", 1, NOW))
            val repo = f.repo(); val before = state(f)
            assertTrue(repo.requiredPlanComplete(NOW))
            assertEquals(before, state(f))
            assertEquals(listOf("other"), repo.buildSession(now = NOW).cards.map { it.chunk.id })
        }
    }

    @Test fun `historical optional practice does not satisfy a later rebuilt required key`() = runBlocking {
        fixture { f ->
            val repo = f.repo()
            repo.answer(repo.buildSession(now = NOW).cards.single(), Rating.GOOD, 100, NOW)
            f.db.reviewDao().insert(ReviewRecord(
                chunkId = "hard", ts = NOW + 2, rating = Rating.GOOD.code,
                durationMs = 100, eventId = "historical-optional-hard",
                purpose = ReviewPurpose.OPTIONAL_HARD
            ).toEntity())
            f.db.statsDao().upsert(f.db.statsDao().day(DAY)!!.copy(reviewsDone = 2, correctCount = 2))
            assertTrue(repo.requiredPlanComplete(NOW + 2))
            val plan = f.db.planDao().plan(DAY)!!
            f.db.planDao().upsert(plan.copy(plannedIds = "required:0,hard:0", plannedTotal = 2))
            assertFalse(repo.requiredPlanComplete(NOW + 3))
            assertEquals(listOf("hard"), repo.buildSession(now = NOW + 3).cards.map { it.chunk.id })
            assertEquals(2, repo.answeredToday(NOW + 3)); assertEquals(1, repo.goalAnswersToday(NOW + 3))
        }
    }

    companion object {
        private val NOW = LocalDateTime.of(2026, 10, 12, 12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        private const val DAY = "2026-10-12"
    }
}
