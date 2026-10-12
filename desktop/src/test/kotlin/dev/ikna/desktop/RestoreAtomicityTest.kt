package dev.ikna.desktop

import dev.ikna.data.db.*
import dev.ikna.data.export.ReviewRecord
import dev.ikna.data.repo.ComponentRepository
import dev.ikna.data.repo.RestoreRepository
import dev.ikna.domain.fsrs.FsrsParams
import dev.ikna.domain.fsrs.FsrsSnapshotCodec
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.GovernorConfig
import java.nio.file.Files
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Real Room, synthetic records, disposable databases only. */
class RestoreAtomicityTest {
    private val replayStages = listOf("cards.clear", "stats.clear", "plans.clear", "cards.write.1",
        "cards.write.2", "stats.write.1", "stats.write.2", "amnesty", "components.clear",
        "tokens.1", "tokens.2", "components.write.1", "components.write.2")
    private val restoreStages = listOf("answers.1", "answers.2", "answers.3", "undo.1", "undo.2") + replayStages

    @Test fun `restore failure at every stage rolls back durably and retry is idempotent`() = runBlocking {
        verifyStages(replay = false, cancel = false)
    }
    @Test fun `restore cancellation at every stage rolls back durably and retry is idempotent`() = runBlocking {
        verifyStages(replay = false, cancel = true)
    }
    @Test fun `standalone replay failure at every stage preserves all prior tables`() = runBlocking {
        verifyStages(replay = true, cancel = false)
    }
    @Test fun `standalone replay cancellation at every stage preserves all prior tables`() = runBlocking {
        verifyStages(replay = true, cancel = true)
    }

    private suspend fun verifyStages(replay: Boolean, cancel: Boolean) = coroutineScope {
        for (stage in if (replay) replayStages else restoreStages) withFixture { f ->
            if (replay) repository(f.db).restoreFromJsonl(jsonl)
            // Deliberately differs from a replay, to detect both clears and partial rebuilds.
            seedDerivedSentinels(f.db)
            val before = snapshot(f.db)
            if (cancel) {
                val reached = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val repo = repository(f.db) { if (it == stage) { reached.complete(Unit); release.await() } }
                val operation = launch { if (replay) repo.replayFromLog() else repo.restoreFromJsonl(jsonl) }
                try {
                    withTimeout(20_000) { reached.await() }
                    operation.cancel()
                    release.complete(Unit)
                    withTimeout(20_000) { operation.join() }
                    assertTrue(operation.isCancelled)
                } finally { release.complete(Unit); operation.cancelAndJoin() }
            } else {
                val repo = repository(f.db) { if (it == stage) error("injected $stage") }
                try {
                    if (replay) repo.replayFromLog() else repo.restoreFromJsonl(jsonl)
                    fail("Expected $stage")
                } catch (e: IllegalStateException) { assertEquals("injected $stage", e.message) }
            }
            assertEquals("Rollback at $stage", before, snapshot(f.db))
            f.reopen()
            assertEquals("Durable rollback at $stage", before, snapshot(f.db))
            val result = repository(f.db).restoreFromJsonl(jsonl)
            assertEquals(if (replay) 0 else 1503, result.imported)
            assertEquals(502, result.replayed)
            val committed = snapshot(f.db)
            assertEquals(1504, committed.reviews.size)
            assertEquals(502, committed.cards.size)
            assertEquals(502, committed.components.size)
            assertNull(committed.plan)
            assertTrue(committed.stats.none { it.planCompleted })
            assertEquals(502, committed.stats.sumOf { it.reviewsDone })
            for (undo in committed.reviews.filter { it.undoOf != null }) {
                val answer = committed.reviews.single { it.id == undo.undoOf }
                assertEquals(undo.chunkId, answer.chunkId)
                assertEquals(parameters, answer.fsrsParameters)
            }
            val retry = repository(f.db).restoreFromJsonl(jsonl)
            assertEquals(0, retry.imported)
            assertEquals(1503, retry.skipped)
            assertEquals(committed, snapshot(f.db))
            f.reopen()
            assertEquals("Durable successful retry at $stage", committed, snapshot(f.db))
        }
    }

    @Test fun `nested Anki shaped transaction commits together and outer failure rolls everything back`() = runBlocking {
        withFixture { f ->
            val before = snapshot(f.db)
            try {
                f.db.inTransaction {
                    f.db.chunkDao().setPackTitle(PACK, "replacement")
                    repository(f.db).restoreFromJsonl(jsonl)
                    error("outer failure")
                }
                fail("Expected outer failure")
            } catch (e: IllegalStateException) { assertEquals("outer failure", e.message) }
            f.reopen()
            assertEquals(before, snapshot(f.db))
            f.db.inTransaction {
                f.db.chunkDao().setPackTitle(PACK, "replacement")
                assertEquals(1503, repository(f.db).restoreFromJsonl(jsonl).imported)
            }
            f.reopen()
            assertEquals("replacement", f.db.chunkDao().pack(PACK)!!.title)
            assertEquals(1504, f.db.reviewDao().all().size)
        }
    }

    @Test fun `concurrent restore serializes signature lookup and does not duplicate history`() = runBlocking {
        withFixture { f ->
            val results = listOf(
                async(Dispatchers.Default) { repository(f.db).restoreFromJsonl(jsonl) },
                async(Dispatchers.Default) { repository(f.db).restoreFromJsonl(jsonl) }
            ).awaitAll()
            assertEquals(listOf(0, 1503), results.map { it.imported }.sorted())
            assertEquals(1504, f.db.reviewDao().all().size)
            f.reopen()
            assertEquals(1504, f.db.reviewDao().all().size)
        }
    }

    @Test fun `invalid late record is rejected before any mutation and synthetic only is inert`() = runBlocking {
        withFixture { f ->
            val before = snapshot(f.db)
            for (bad in listOf(records.last().copy(gradingVersion = 99),
                records.last().copy(fsrsParameters = "invalid"))) {
                try {
                    repository(f.db).restoreFromJsonl(jsonl + "\n" + encode(bad))
                    fail("Expected validation failure")
                } catch (_: IllegalArgumentException) { assertEquals(before, snapshot(f.db)) }
            }
            assertEquals(0, repository(f.db).restoreFromJsonl(encode(records.first().copy(synthetic = true))).replayed)
            assertEquals(before, snapshot(f.db))
        }
    }

    private fun repository(db: IknaDatabase, after: suspend (String) -> Unit = {}): RestoreRepository {
        val cards = db.cardDao(); val reviews = db.reviewDao(); val stats = db.statsDao()
        val plans = db.planDao(); val components = db.componentDao(); val chunks = db.chunkDao()
        val counts = mutableMapOf<String, Int>()
        suspend fun batch(name: String) { val n = counts.getOrDefault(name, 0) + 1; counts[name] = n; after("$name.$n") }
        return RestoreRepository(
            db = db,
            cardDao = object : CardDao by cards {
                override suspend fun clear() { cards.clear(); after("cards.clear") }
                override suspend fun upsertAll(cardsToWrite: List<CardEntity>) { cards.upsertAll(cardsToWrite); batch("cards.write") }
                override suspend fun moveOverdueToAmnesty(threshold: Long): Int = cards.moveOverdueToAmnesty(threshold).also { after("amnesty") }
            },
            reviewDao = object : ReviewDao by reviews {
                override suspend fun insertAll(rows: List<ReviewEntity>): List<Long> = reviews.insertAll(rows).also {
                    batch(if (rows.first().undoOf == null) "answers" else "undo")
                }
            },
            statsDao = object : StatsDao by stats {
                override suspend fun clear() { stats.clear(); after("stats.clear") }
                override suspend fun upsertAll(rows: List<DailyStatEntity>) { stats.upsertAll(rows); batch("stats.write") }
            },
            planDao = object : PlanDao by plans {
                override suspend fun clear() { plans.clear(); after("plans.clear") }
            },
            components = ComponentRepository(object : ComponentDao by components {
                override suspend fun clear() { components.clear(); after("components.clear") }
                override suspend fun upsertAll(items: List<ComponentEntity>) { components.upsertAll(items); batch("components.write") }
            }, object : ChunkDao by chunks {
                override suspend fun tokensFor(ids: List<String>): List<ChunkTokenEntity> = chunks.tokensFor(ids).also { batch("tokens") }
            }, reviews),
            scheduler = Scheduler(), config = GovernorConfig()
        )
    }

    private data class Snapshot(val reviews: List<ReviewEntity>, val cards: List<CardEntity>,
        val components: List<ComponentEntity>, val stats: List<DailyStatEntity>,
        val plan: DailyPlanEntity?, val packs: List<PackEntity>)
    private suspend fun snapshot(db: IknaDatabase) = Snapshot(db.reviewDao().all(),
        db.cardDao().all().sortedWith(compareBy({ it.chunkId }, { it.level })),
        db.componentDao().all().sortedBy { it.lemma }, db.statsDao().lastDays(5000),
        db.planDao().plan(DAY), db.chunkDao().packs())

    private class Fixture(val file: java.io.File, var db: IknaDatabase) {
        fun reopen() { db.close(); db = openIknaDatabase(file) }
    }
    private suspend fun seedDerivedSentinels(db: IknaDatabase) {
        db.cardDao().upsert(CardEntity("dev-sentinel", 0, 8.0, 4.0, START, START, START))
        db.componentDao().upsertAll(listOf(ComponentEntity("dev-sentinel", "NOUN", 7.0, 6.0, 8.0, START, START)))
        db.statsDao().upsert(DailyStatEntity(DAY, 99, 9, 999, 0.75, true, 74))
        db.planDao().upsert(DailyPlanEntity(DAY, "dev-sentinel:0", 1, 40, 1, 0, "OK", 0, START))
    }
    private suspend fun withFixture(block: suspend (Fixture) -> Unit) {
        val home = Files.createTempDirectory("ikna-dev-restore-atomic-").toFile()
        val file = home.resolve("disposable.db")
        val f = Fixture(file, openIknaDatabase(file))
        try {
            f.db.chunkDao().upsertPack(PackEntity(PACK, 1, "en", 1003, START))
            val ids = listOf("dev-existing") + (0 until 1002).map { "dev-restore-$it" }
            f.db.chunkDao().insertChunksIgnore(ids.map { ChunkEntity(it, PACK, "en", "sample", "sample", "DEV", 0, 6, 1) })
            f.db.chunkDao().insertTokensIgnore(ids.map { ChunkTokenEntity(it, 0, "sample", it, "NOUN", true, true, 1.0) })
            f.db.reviewDao().insert(ReviewRecord(id = 90000, chunkId = "dev-existing", ts = START - 86400000L, rating = 3).toEntity(id = 90000))
            seedDerivedSentinels(f.db)
            block(f)
        } finally { f.db.close(); home.deleteRecursively() }
    }

    private companion object {
        const val PACK = "dev-restore-pack"
        const val DAY = "2020-01-01"
        const val START = 1577880000000L
        val parameters = FsrsSnapshotCodec.encode(FsrsParams())
        val records = (0 until 1002).map { i -> ReviewRecord(id = i + 1L, chunkId = "dev-restore-$i",
            ts = START + i * 86400000L, rating = 3, durationMs = 1200, fsrsParameters = parameters) } +
            (0 until 501).map { i -> ReviewRecord(id = 10000L + i, chunkId = "dev-restore-$i",
                ts = START + 1100 * 86400000L + i, rating = 0, undoOf = i + 1L) }
        fun encode(record: ReviewRecord) = ReviewRecord.json.encodeToString(ReviewRecord.serializer(), record)
        val jsonl = records.joinToString("\n", transform = ::encode)
    }
}
