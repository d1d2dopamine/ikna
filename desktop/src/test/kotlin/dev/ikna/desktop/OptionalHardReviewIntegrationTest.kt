package dev.ikna.desktop

import androidx.room.useWriterConnection
import dev.ikna.data.db.*
import dev.ikna.data.export.*
import dev.ikna.data.repo.*
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.ChunkSelector
import dev.ikna.domain.governor.GovernorConfig
import dev.ikna.domain.session.*
import kotlinx.coroutines.*
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

/** Real Room with technical identifiers only. Optional work changes memory, never the obligation. */
class OptionalHardReviewIntegrationTest {
    private class Fixture {
        val home = Files.createTempDirectory("ikna-optional-hard-").toFile()
        val file = home.resolve("profile.db")
        var db = openIknaDatabase(file)
        var hidden = emptySet<String>()
        val config = GovernorConfig(earnedNewPerReviews = 4)
        fun repo(reviews: ReviewDao = db.reviewDao()) = LearningRepository(db, db.cardDao(), db.chunkDao(), reviews,
            db.statsDao(), db.governorDao(), db.planDao(), db.browseDao(),
            ComponentRepository(db.componentDao(), db.chunkDao(), db.reviewDao(), db), Scheduler(), ChunkSelector(), config)
            .also { it.suppressedChunks = { hidden } }
        fun restore() = RestoreRepository(db, db.cardDao(), db.reviewDao(), db.statsDao(), db.planDao(),
            ComponentRepository(db.componentDao(), db.chunkDao(), db.reviewDao(), db), Scheduler(), config)
        suspend fun card(id: String) = SessionBuilder(db.cardDao(), db.chunkDao(), config).materialize(listOf("$id:0")).single()
        suspend fun seed(complete: Boolean = true) {
            db.chunkDao().upsertPack(PackEntity("pack", 1, "en", 10, NOW))
            db.chunkDao().upsertPack(PackEntity("other", 1, "en", 1, NOW))
            val ids = listOf("required", "fresh") + (0..7).map { "hard-$it" }
            db.chunkDao().insertChunksIgnore(ids.mapIndexed { i, id -> ChunkEntity(id, "pack", "en", "", "", "", 0, 0, i) })
            db.chunkDao().insertTokensIgnore(ids.map { id ->
                ChunkTokenEntity(id, 0, id, id, "NOUN", true, true, 1.0)
            })
            db.chunkDao().upsertPackChunks(ids.mapIndexed { i, id -> PackChunkEntity("pack", id, i) } + PackChunkEntity("other", "hard-0", 0))
            db.cardDao().upsertAll(ids.filter { it != "fresh" }.map { id ->
                CardEntity(id, 0, 3.0, 5.0, NOW + 86_400_000, NOW - 86_400_000, NOW - 2 * 86_400_000,
                    reps = 8, lapses = if (id.startsWith("hard")) 4 else 0, isNew = false)
            })
            db.planDao().upsert(DailyPlanEntity(DAY, "required:0", 1, 40, 1, 0, "NO_HEADROOM", 0, NOW - 2000))
            db.statsDao().upsert(DailyStatEntity(DAY, 0, 1, 0, 1.0, false))
            if (complete) repo().answer(card("required"), Rating.GOOD, 100, NOW - 1000)
        }
        fun reopen() { db.close(); db = openIknaDatabase(file) }
        fun close() { db.close(); home.deleteRecursively() }
    }
    private suspend fun fixture(complete: Boolean = true, block: suspend (Fixture) -> Unit) {
        val f = Fixture()
        try { f.seed(complete); block(f) } finally { f.close() }
    }
    private suspend fun rejected(block: suspend () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {}
    }
    private data class Snapshot(val cards: List<CardEntity>, val reviews: List<ReviewEntity>, val components: List<ComponentEntity>, val stats: List<DailyStatEntity>, val plan: DailyPlanEntity?)
    private suspend fun snapshot(f: Fixture) = Snapshot(f.db.cardDao().all(), f.db.reviewDao().all(), f.db.componentDao().all(),
        f.db.statsDao().lastDays(Int.MAX_VALUE), f.db.planDao().plan(DAY))

    @Test fun `availability requires existing global completion and is read only`() = runBlocking {
        fixture(false) { f ->
            val repo = f.repo(); val before = snapshot(f)
            assertFalse(repo.optionalHardAvailable("other", NOW))
            assertNull(repo.startOptionalHardReview("other", NOW))
            assertEquals(before, snapshot(f))
            f.db.planDao().clear()
            val without = snapshot(f)
            assertFalse(repo.optionalHardAvailable(now = NOW)); assertNull(repo.startOptionalHardReview(now = NOW))
            assertEquals(without, snapshot(f))
        }
    }

    @Test fun `selection filters before stable five limit and deduplicates memberships`() = runBlocking {
        fixture { f ->
            f.hidden = setOf("hard-0", "hard-1")
            f.db.cardDao().upsert(f.card("hard-2").card.copy(isNew = true))
            f.db.cardDao().upsert(f.card("hard-3").card.copy(level = 1, lapses = 100))
            val repo = f.repo(); val before = snapshot(f)
            val batch = requireNotNull(repo.startOptionalHardReview(now = NOW))
            assertEquals(listOf("hard-3", "hard-4", "hard-5", "hard-6", "hard-7"), batch.cards.map { it.chunk.id })
            assertEquals(5, batch.cards.map { it.card.key }.toSet().size)
            assertTrue(batch.cards.all { !it.card.isNew && it.card.level == 0 })
            assertEquals(before, snapshot(f))
            repo.cancelOptionalHardReview(batch)
            assertEquals(before, snapshot(f))
            rejected { repo.answerOptionalHard(batch, batch.cards.first(), Rating.GOOD, 10, NOW + 1) }
            f.hidden = emptySet()
            assertEquals(listOf("hard-0"), repo.startOptionalHardReview("other", NOW)!!.cards.map { it.chunk.id })
        }
    }

    @Test fun `normal scheduler factual totals and daily finish survive finite optional answers`() = runBlocking {
        fixture { f ->
            val repo = f.repo(); val batch = repo.startOptionalHardReview(now = NOW)!!
            val plan = f.db.planDao().plan(DAY); val before = f.db.statsDao().day(DAY)!!
            batch.cards.forEachIndexed { i, card ->
                val rating = if (i == 0) Rating.AGAIN else Rating.GOOD
                val oracle = Scheduler().apply(f.db.cardDao().card(card.chunk.id, 0)!!, rating, NOW + i).card
                repo.answerOptionalHard(batch, card, rating, 1200, NOW + i)
                assertEquals(oracle, f.db.cardDao().card(card.chunk.id, 0))
            }
            assertEquals(plan, f.db.planDao().plan(DAY))
            val stat = f.db.statsDao().day(DAY)!!
            assertTrue(stat.planCompleted); assertEquals(before.newIntroduced, stat.newIntroduced)
            assertEquals(before.reviewsDone + 5, stat.reviewsDone); assertEquals(before.activeMs + 6000, stat.activeMs)
            assertEquals(before.correctCount + 4, stat.correctCount)
            assertEquals(1, repo.goalAnswersToday(NOW + 10))
            assertEquals(6, repo.answeredToday(NOW + 10))
            assertEquals(listOf("required:0"), f.db.reviewDao().answeredKeysSince(NOW - 5000))
            assertEquals(5, f.db.reviewDao().all().count { it.purpose == ReviewPurpose.OPTIONAL_HARD })
            assertNull(f.db.cardDao().card("fresh", 0))
            val committed = snapshot(f)
            rejected { repo.answerOptionalHard(batch, batch.cards.first(), Rating.GOOD, 10, NOW + 10) }
            assertEquals(committed, snapshot(f))
        }
    }

    @Test fun `optional rows never retroactively earn new cards after restart or replay`() = runBlocking {
        fixture { f ->
            val repo = f.repo(); val batch = repo.startOptionalHardReview(now = NOW)!!
            batch.cards.forEachIndexed { i, card -> repo.answerOptionalHard(batch, card, Rating.GOOD, 10, NOW + i) }
            f.reopen()
            f.restore().replaySyncJournal(0)
            val resumed = f.repo()
            resumed.answer(f.card("required"), Rating.GOOD, 10, NOW + 100)
            assertNull(f.db.cardDao().card("fresh", 0))
            // Four ordinary beyond-obligation answers still earn exactly the historical allowance.
            for (i in 1..3) resumed.answer(f.card("required"), Rating.GOOD, 10, NOW + 100 + i)
            assertNotNull(f.db.cardDao().card("fresh", 0))
            assertEquals(2, f.db.statsDao().day(DAY)!!.newIntroduced)
        }
    }

    @Test fun `optional undo retains normal completion and restores same batch card without earning`() = runBlocking {
        fixture { f ->
            val repo = f.repo(); val before = snapshot(f); val batch = repo.startOptionalHardReview(now = NOW)!!
            val card = batch.cards.first()
            repo.answerOptionalHard(batch, card, Rating.AGAIN, 500, NOW)
            assertEquals(card, repo.undoOptionalHard(batch, NOW + 1))
            assertEquals(before.cards, f.db.cardDao().all()); assertEquals(before.stats, f.db.statsDao().lastDays(Int.MAX_VALUE))
            assertEquals(before.plan, f.db.planDao().plan(DAY))
            val optional = f.db.reviewDao().all().filter { it.purpose != null }
            assertEquals(2, optional.size); assertEquals(optional.first().id, optional.last().undoOf)
            assertEquals(0, f.db.reviewDao().optionalHardCountBetween(NOW - 5000, NOW + 5000, 0))
            repo.answerOptionalHard(batch, card, Rating.GOOD, 100, NOW + 2)
            repo.answer(f.card("required"), Rating.GOOD, 100, NOW + 3)
            val current = snapshot(f)
            assertNull(repo.undoOptionalHard(batch, NOW + 4))
            assertEquals(current, snapshot(f))
        }
    }

    @Test fun `sync undo replay keeps optional subtraction and same batch semantics`() = runBlocking {
        fixture { f ->
            val repo = f.repo().also { it.syncHistoryBoundary = { 0L } }
            val batch = repo.startOptionalHardReview(now = NOW)!!
            val plan = f.db.planDao().plan(DAY)
            repo.answerOptionalHard(batch, batch.cards[0], Rating.AGAIN, 500, NOW)
            assertNotNull(repo.undoOptionalHard(batch, NOW + 1))
            assertEquals(1, f.db.statsDao().day(DAY)!!.reviewsDone)
            assertEquals(0, f.db.reviewDao().optionalHardCountBetween(NOW - 5000, NOW + 5000, 0))
            assertEquals(plan, f.db.planDao().plan(DAY))
            assertTrue(f.db.statsDao().day(DAY)!!.planCompleted)
            repo.answerOptionalHard(batch, batch.cards[0], Rating.GOOD, 100, NOW + 2)
            repo.answer(f.card("required"), Rating.GOOD, 100, NOW + 3)
            assertNull(f.db.cardDao().card("fresh", 0))
            assertEquals(1, f.db.reviewDao().optionalHardCountBetween(NOW - 5000, NOW + 5000, 0))
        }
    }

    @Test fun `stale day boundary suppression content and replaced tokens reject without writes`() = runBlocking {
        fixture { f ->
            val repo = f.repo()
            suspend fun failAfter(change: suspend () -> Unit, reset: suspend () -> Unit) {
                val b = repo.startOptionalHardReview(now = NOW)!!
                change(); val before = snapshot(f)
                rejected { repo.answerOptionalHard(b, b.cards.first(), Rating.GOOD, 10, NOW) }
                assertEquals(before, snapshot(f)); reset()
            }
            val dayBatch = repo.startOptionalHardReview(now = NOW)!!
            rejected { repo.answerOptionalHard(dayBatch, dayBatch.cards.first(), Rating.GOOD, 10, NOW + 86_400_000) }
            failAfter({ f.hidden = setOf("hard-0") }, { f.hidden = emptySet() })
            val original = f.db.chunkDao().chunk("hard-0")!!
            failAfter({ f.db.useWriterConnection { it.usePrepared("UPDATE chunks SET freqRank=999 WHERE id='hard-0'") { s -> s.step() } } },
                { f.db.useWriterConnection { it.usePrepared("UPDATE chunks SET freqRank=${original.freqRank} WHERE id='hard-0'") { s -> s.step() } } })
            val stale = repo.startOptionalHardReview(now = NOW)!!
            repo.startOptionalHardReview(now = NOW)
            rejected { repo.answerOptionalHard(stale, stale.cards.first(), Rating.GOOD, 10, NOW) }
            val boundaryBatch = repo.startOptionalHardReview(now = NOW)!!
            f.db.historyBoundaryDao().put(HistoryBoundaryEntity(afterLocalId = 0, token = "new-boundary"))
            val before = snapshot(f)
            rejected { repo.answerOptionalHard(boundaryBatch, boundaryBatch.cards.first(), Rating.GOOD, 10, NOW) }
            assertEquals(before, snapshot(f))
        }
    }

    @Test fun `removed membership and newly incomplete global plan invalidate batch`() = runBlocking {
        fixture { f ->
            val repo = f.repo(); val batch = repo.startOptionalHardReview("other", NOW)!!
            f.db.useWriterConnection { it.usePrepared("DELETE FROM pack_chunks WHERE packId='other'") { s -> s.step() } }
            val before = snapshot(f)
            rejected { repo.answerOptionalHard(batch, batch.cards.first(), Rating.GOOD, 10, NOW) }
            assertEquals(before, snapshot(f))
            val global = repo.startOptionalHardReview(now = NOW)!!
            val plan = f.db.planDao().plan(DAY)!!
            f.db.planDao().upsert(plan.copy(plannedIds = "required:0,hard-7:0", plannedTotal = 2))
            assertFalse(repo.optionalHardAvailable("other", NOW))
            rejected { repo.answerOptionalHard(global, global.cards.first(), Rating.GOOD, 10, NOW) }
        }
    }

    @Test fun `optional cancellation after review insert rolls back and same batch retries once`() = runBlocking {
        fixture { f -> coroutineScope {
            val reached = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); var intercept = true
            val real = f.db.reviewDao()
            val repo = f.repo(object : ReviewDao by real {
                override suspend fun insert(review: ReviewEntity): Long {
                    val id = real.insert(review)
                    if (intercept) { reached.complete(Unit); release.await() }
                    return id
                }
            })
            val batch = repo.startOptionalHardReview(now = NOW)!!; val before = snapshot(f)
            val job = launch { repo.answerOptionalHard(batch, batch.cards.first(), Rating.GOOD, 100, NOW) }
            try {
                withTimeout(10_000) { reached.await() }; job.cancel(); release.complete(Unit)
                withTimeout(10_000) { job.join() }; assertTrue(job.isCancelled)
            } finally { release.complete(Unit); job.cancelAndJoin() }
            assertEquals(before, snapshot(f)); intercept = false
            repo.answerOptionalHard(batch, batch.cards.first(), Rating.GOOD, 100, NOW)
            assertEquals(before.reviews.size + 1, f.db.reviewDao().all().size)
        } }
    }

    @Test fun `purpose survives export restore undo and replay without inventing introductions`() = runBlocking {
        fixture { f ->
            val repo = f.repo(); val batch = repo.startOptionalHardReview(now = NOW)!!
            repo.answerOptionalHard(batch, batch.cards[0], Rating.GOOD, 100, NOW)
            repo.answerOptionalHard(batch, batch.cards[1], Rating.AGAIN, 200, NOW + 1)
            repo.undoOptionalHard(batch, NOW + 2)
            val rows = f.db.reviewDao().all().map { ReviewRecord.of(it) }
            val out = rows.joinToString("\n") { ReviewRecord.json.encodeToString(ReviewRecord.serializer(), it) }
            val target = Fixture()
            try {
                target.seed(false)
                val result = target.restore().restoreFromJsonl(out)
                assertEquals(rows.size, result.imported)
                assertEquals(JournalIdentity.normalize(rows), JournalIdentity.normalize(target.db.reviewDao().all().map { ReviewRecord.of(it) }))
                val stat = target.db.statsDao().day(DAY)!!
                assertEquals(2, stat.reviewsDone); assertEquals(200L, stat.activeMs)
                assertEquals(1, stat.newIntroduced) // Ordinary required row only, never optional target.
                assertFalse(stat.planCompleted)
                target.db.planDao().upsert(DailyPlanEntity(DAY, "hard-0:0", 1, 40, 0, 0, "OK", 0, NOW))
                assertFalse(target.repo().optionalHardAvailable(now = NOW + 3))
                assertEquals(1, target.repo().goalAnswersToday(NOW + 3))
                assertFalse("Optional answer cannot satisfy rebuilt ordinary plan", target.repo().buildSession(now = NOW + 3).cards.isEmpty())
            } finally { target.close() }
        }
    }

    @Test fun `unknown purpose fails closed before any import and payload conflict remains exact`() = runBlocking {
        fixture { f ->
            val before = snapshot(f)
            val unknown = "{\"chunkId\":\"hard-0\",\"ts\":$NOW,\"rating\":3,\"purpose\":\"future\"}"
            rejected { f.restore().restoreFromJsonl("{\"chunkId\":\"hard-0\",\"ts\":$NOW,\"rating\":3}\n$unknown") }
            assertEquals(before, snapshot(f))
            rejected { ReviewRecord(chunkId = "hard-0", ts = NOW, rating = 3, purpose = "future") }
            val normal = ReviewRecord.of(f.db.reviewDao().all().single()).copy(purpose = ReviewPurpose.OPTIONAL_HARD)
            rejected { f.restore().mergeJournal(listOf(normal)) }
            assertEquals(before, snapshot(f))
        }
    }

    @Test fun `normal canonical record profile bytes and legacy identity match sealed55 fixtures`() {
        val r = ReviewRecord(chunkId = "target", ts = 1000, rating = 3)
        fun fixture(name: String) = javaClass.getResourceAsStream("/optional-review/$name")!!.use { it.readBytes() }
        assertArrayEquals(fixture("normal-record-before-purpose.json"), ReviewRecord.json.encodeToString(ReviewRecord.serializer(), r).toByteArray())
        assertEquals(String(fixture("normal-legacy-event-before-purpose.txt")), JournalIdentity.normalize(listOf(r)).single().eventId)
        val profile = ProfileTransferSnapshot(packs = emptyList(), chunks = emptyList(), tokens = emptyList(), memberships = emptyList(), contexts = emptyList(), reviews = listOf(r))
        assertArrayEquals(fixture("normal-profile-before-purpose.json"), ProfileTransferCodec.encode(profile))
        val optional = r.copy(purpose = ReviewPurpose.OPTIONAL_HARD)
        assertNotEquals(JournalIdentity.normalize(listOf(r)).single().eventId, JournalIdentity.normalize(listOf(optional)).single().eventId)
        assertEquals(optional, ReviewRecord.json.decodeFromString(ReviewRecord.serializer(), ReviewRecord.json.encodeToString(ReviewRecord.serializer(), optional)))
    }

    companion object {
        private val NOW = LocalDateTime.of(2026, 10, 12, 12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        private const val DAY = "2026-10-12"
    }
}
