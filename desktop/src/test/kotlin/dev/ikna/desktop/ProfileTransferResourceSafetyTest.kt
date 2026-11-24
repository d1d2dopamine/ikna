package dev.ikna.desktop

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.ikna.data.db.*
import dev.ikna.data.export.*
import dev.ikna.data.repo.*
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.GovernorConfig
import java.io.File
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Real SQLite, with instrumentation at the driver boundary rather than timing/heap guesses. */
class ProfileTransferResourceSafetyTest {
    private class TracedDriver : SQLiteDriver {
        private val delegate = BundledSQLiteDriver()
        val entityQueries = Collections.synchronizedList(mutableListOf<String>())
        val entityRows = AtomicInteger()
        val scalarQueries = Collections.synchronizedList(mutableListOf<String>())
        @Volatile var afterStep: ((String) -> Unit)? = null
        override fun open(fileName: String): SQLiteConnection {
            val connection = delegate.open(fileName)
            return object : SQLiteConnection by connection {
                override fun prepare(sql: String): SQLiteStatement {
                    val statement = connection.prepare(sql)
                    val entities = Regex("(?i)^SELECT \\* FROM (packs|chunks|chunk_tokens|pack_chunks|chunk_contexts|reviews)(?: |$)").containsMatchIn(sql)
                    if (entities) entityQueries += sql
                    if (sql.startsWith("SELECT COUNT(*) FROM (SELECT 1") || sql.startsWith("SELECT COALESCE(length(")) scalarQueries += sql
                    return object : SQLiteStatement by statement {
                        override fun step(): Boolean = statement.step().also { row ->
                            if (row && entities) entityRows.incrementAndGet()
                            afterStep?.invoke(sql)
                        }
                    }
                }
            }
        }
        fun reset() { entityQueries.clear(); entityRows.set(0); scalarQueries.clear(); afterStep = null }
    }

    private class Fixture {
        val home = Files.createTempDirectory("ikna-profile-resources-").toFile()
        val file = File(home, "profile.db")
        val trace = TracedDriver()
        var db = open()
        fun open(): IknaDatabase = Room.databaseBuilder<IknaDatabase>(name = file.absolutePath)
            .addMigrations(*IknaMigrations.ALL)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(connection: SQLiteConnection) { ChunkFtsIndex.create(connection) }
            })
            .setDriver(trace).setQueryCoroutineContext(Dispatchers.IO).build()
        fun repository() = RestoreRepository(db, db.cardDao(), db.reviewDao(), db.statsDao(), db.planDao(),
            ComponentRepository(db.componentDao(), db.chunkDao(), db.reviewDao()), Scheduler(dayStartHour = 4), GovernorConfig())
        fun reopen() { db.close(); db = open(); trace.reset() }
        suspend fun seed() {
            db.chunkDao().upsertPack(PackEntity("pack", 1, "en", 1, TS, title = "fixture"))
            db.chunkDao().insertChunksIgnore(listOf(ChunkEntity("target", "pack", "en", "word", "word", "meaning", 0, 4, 1)))
            db.chunkDao().upsertPackChunks(listOf(PackChunkEntity("pack", "target", 1)))
        }
        suspend fun sql(sql: String) {
            db.useWriterConnection { it.usePrepared(sql) { statement -> statement.step() } }
        }
        fun close() { db.close(); home.deleteRecursively() }
    }
    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val fixture = Fixture()
        try { block(fixture) } finally { fixture.close() }
    }
    private fun answer(event: String?, timestamp: Long = TS) = ReviewRecord(chunkId = "target", ts = timestamp,
        rating = 3, eventId = event, durationMs = 100, prevIsNew = true)

    /** The Round52 algorithm deliberately retained as an executable regression witness. */
    private suspend fun unboundedRound52Snapshot(db: IknaDatabase, cutoff: Long = 0): ProfileTransferSnapshot = db.inTransaction {
        val dao = db.chunkDao()
        val records = JournalIdentity.normalize(db.reviewDao().all().filter { it.id > cutoff }.map { ReviewRecord.of(it) })
        ProfileTransferCodec.canonical(ProfileTransferSnapshot(boundaryPresent = db.historyBoundaryDao().current() != null,
            packs = dao.packs(), chunks = dao.allChunks(), tokens = dao.allTokens(),
            memberships = dao.allMemberships(), contexts = dao.allContexts(), reviews = records))
            .also { ProfileTransferCodec.encode(it) }
    }
    private suspend fun expectLimit(kind: ProfileTransferLimitException.Limit, block: suspend () -> Unit) {
        try { block(); fail("Expected profile limit rejection") }
        catch (failure: ProfileTransferLimitException) { assertEquals(kind, failure.limit) }
    }

    @Test fun `all six table row overflows reject before any transferred entity is materialized`() = runBlocking {
        val inserts = linkedMapOf(
            "packs" to "INSERT INTO packs(id,version,lang,chunkCount,installedAt,isActive) SELECT 'p'||n,1,'en',0,0,1 FROM seq",
            "chunks" to "INSERT INTO chunks(id,packId,lang,text,contextSentence,translation,targetStart,targetEnd,freqRank) SELECT 't'||n,'p','en','','','',0,0,n FROM seq",
            "chunk_tokens" to "INSERT INTO chunk_tokens(chunkId,position,surface,lemma,pos,isTarget,isContent,weight) SELECT 't',n,'','','',0,0,0 FROM seq",
            "pack_chunks" to "INSERT INTO pack_chunks(packId,chunkId,freqRank) SELECT 'p','t'||n,n FROM seq",
            "chunk_contexts" to "INSERT INTO chunk_contexts(packId,chunkId,contextId,meaningId,sourceFamily,contextSentence,translation,targetStart,targetEnd,freqRank,tokensJson) SELECT 'p','t','c'||n,'m','','','',0,0,n,'[]' FROM seq",
            "reviews" to "INSERT INTO reviews(chunkId,level,ts,rating,elapsedDays,stabilityBefore,stabilityAfter,difficultyBefore,difficultyAfter,durationMs,wasAmnesty,eventId) SELECT 'target',0,n,3,0,0,0,5,5,0,0,'event:'||n FROM seq"
        )
        for ((table, insert) in inserts) fixture { f ->
            f.sql("WITH RECURSIVE seq(n) AS (VALUES(1) UNION ALL SELECT n+1 FROM seq WHERE n < ${ProfileTransferCodec.MAX_ROWS + 1}) $insert")
            f.trace.reset()
            expectLimit(ProfileTransferLimitException.Limit.ROWS) { unboundedRound52Snapshot(f.db) }
            assertTrue("Round52 must demonstrate the oversized $table read", f.trace.entityRows.get() >= ProfileTransferCodec.MAX_ROWS + 1)
            f.trace.reset()
            expectLimit(ProfileTransferLimitException.Limit.ROWS) { f.repository().profileTransfer.snapshot() }
            assertTrue("Preflight must precede every entity query for $table", f.trace.entityQueries.isEmpty())
            assertEquals(0, f.trace.entityRows.get())
            assertTrue(f.trace.scalarQueries.all { it.contains("LIMIT ${ProfileTransferCodec.MAX_ROWS + 1}") })
        }
    }

    @Test fun `exact row ceiling is not lowered and final byte codec still rejects JSON overhead`() = runBlocking {
        fixture { f ->
            f.sql("WITH RECURSIVE seq(n) AS (VALUES(1) UNION ALL SELECT n+1 FROM seq WHERE n < ${ProfileTransferCodec.MAX_ROWS}) " +
                "INSERT INTO chunk_tokens(chunkId,position,surface,lemma,pos,isTarget,isContent,weight) SELECT 't',n,'','','',0,0,0 FROM seq")
            f.trace.reset()
            expectLimit(ProfileTransferLimitException.Limit.BYTES) { f.repository().profileTransfer.snapshot() }
            assertEquals(ProfileTransferCodec.MAX_ROWS, f.trace.entityRows.get())
            assertTrue(f.trace.scalarQueries.any { it.startsWith("SELECT COALESCE(length(") })
        }
    }

    @Test fun `raw byte overflow counts UTF8 and embedded NUL before loading strings`() = runBlocking {
        fixture { f ->
            f.seed()
            // Five raw UTF-8 bytes per repetition; SQLite length(TEXT) would stop at the first NUL.
            val title = "🙂\u0000".repeat(ProfileTransferCodec.MAX_BYTES / 5 + 1)
            f.db.chunkDao().setPackTitle("pack", title)
            f.trace.reset()
            expectLimit(ProfileTransferLimitException.Limit.BYTES) { unboundedRound52Snapshot(f.db) }
            assertTrue(f.trace.entityRows.get() >= 3)
            f.trace.reset()
            expectLimit(ProfileTransferLimitException.Limit.BYTES) { f.repository().profileTransfer.snapshot() }
            assertTrue(f.trace.entityQueries.isEmpty())
            assertEquals(0, f.trace.entityRows.get())
        }
    }

    @Test fun `byte accounting is cumulative across tables and catches oversized active observations`() = runBlocking {
        fixture { f ->
            f.seed()
            val payload = "x".repeat(ProfileTransferCodec.MAX_BYTES / 2)
            f.db.chunkDao().setPackTitle("pack", payload)
            f.db.reviewDao().insert(answer("active").copy(gradingReason = payload).toEntity())
            f.trace.reset()
            expectLimit(ProfileTransferLimitException.Limit.BYTES) { f.repository().profileTransfer.snapshot() }
            assertTrue(f.trace.entityQueries.isEmpty())
        }
    }

    @Test fun `large archive does not consume active row or byte limits and near byte limit is cold warm identical`() = runBlocking {
        fixture { f ->
            f.seed()
            f.sql("WITH RECURSIVE seq(n) AS (VALUES(1) UNION ALL SELECT n+1 FROM seq WHERE n < ${ProfileTransferCodec.MAX_ROWS + 1}) " +
                "INSERT INTO reviews(chunkId,level,ts,rating,elapsedDays,stabilityBefore,stabilityAfter,difficultyBefore,difficultyAfter,durationMs,wasAmnesty,eventId,gradingReason) " +
                "SELECT 'target',0,n,3,0,0,0,5,5,0,0,'archive:'||n,'${"a".repeat(100)}' FROM seq")
            val cutoff = f.repository().maxJournalId()
            f.db.historyBoundaryDao().put(HistoryBoundaryEntity(afterLocalId = cutoff, token = "reset"))
            f.db.reviewDao().insert(answer("active").toEntity())
            val base = f.repository().profileTransfer.snapshot(cutoff, "reset")
            val slack = ProfileTransferCodec.MAX_BYTES - ProfileTransferCodec.encode(base).size
            f.db.chunkDao().setPackTitle("pack", "fixture" + "x".repeat(slack))
            val expected = unboundedRound52Snapshot(f.db, cutoff)
            assertEquals(ProfileTransferCodec.MAX_BYTES, ProfileTransferCodec.encode(expected).size)
            f.trace.reset()
            val warm = f.repository().profileTransfer.snapshotWithRevision(cutoff, "reset")
            assertEquals(expected, warm.first)
            assertEquals(listOf("active"), warm.first.reviews.map { it.eventId })
            assertEquals(4, f.trace.entityRows.get()) // One pack, target, membership and ACTIVE review.
            assertTrue(f.trace.entityQueries.filter { it.contains("FROM reviews") }.all { it.contains("WHERE id >") })
            f.reopen()
            val cold = f.repository().profileTransfer.snapshotWithRevision(cutoff, "reset")
            assertEquals(warm, cold)
            assertEquals(4, f.trace.entityRows.get())
            f.db.chunkDao().setPackTitle("pack", expected.packs.single().title + "x")
            expectLimit(ProfileTransferLimitException.Limit.BYTES) { f.repository().profileTransfer.snapshot(cutoff, "reset") }
        }
    }

    @Test fun `boundary mismatch precedes size queries and preserves all rows`() = runBlocking {
        fixture { f ->
            f.seed()
            f.db.reviewDao().insert(answer("archived").toEntity())
            val cutoff = f.repository().maxJournalId()
            f.db.historyBoundaryDao().put(HistoryBoundaryEntity(afterLocalId = cutoff, token = "new-boundary"))
            f.trace.reset()
            try { f.repository().profileTransfer.snapshot(cutoff, "old-boundary"); fail("Stale boundary must fail") }
            catch (_: IllegalArgumentException) { }
            assertTrue(f.trace.scalarQueries.isEmpty()); assertTrue(f.trace.entityQueries.isEmpty())
            assertEquals(1, f.db.reviewDao().all().size)
        }
    }

    @Test fun `SQL active ordering preserves legacy identities and undo resolution without reading archive`() = runBlocking {
        fixture { f ->
            f.seed()
            f.db.reviewDao().insert(answer(null, TS - 1).toEntity())
            val cutoff = f.repository().maxJournalId()
            f.db.historyBoundaryDao().put(HistoryBoundaryEntity(afterLocalId = cutoff, token = "reset"))
            val original = f.db.reviewDao().insert(answer(null).toEntity())
            f.db.reviewDao().insert(answer(null, TS + 1).copy(rating = 0, undoOf = original).toEntity())
            val expected = unboundedRound52Snapshot(f.db, cutoff)
            f.trace.reset()
            assertEquals(expected, f.repository().profileTransfer.snapshot(cutoff, "reset"))
            assertEquals(expected.reviews, f.repository().snapshotJournal(cutoff, "reset"))
            assertEquals(expected.reviews.first().eventId, expected.reviews.last().undoEventId)
            assertTrue(f.trace.entityQueries.filter { it.contains("FROM reviews") }.all { it.contains("WHERE id >") })
            f.trace.reset()
            assertEquals(original + 1, f.repository().maxJournalId())
            assertTrue(f.trace.entityQueries.isEmpty())
        }
    }

    @Test fun `journal merge and replay use active SQL without materializing archived history`() = runBlocking {
        fixture { f ->
            f.seed()
            f.db.reviewDao().insert(answer("archive", TS - 1).toEntity())
            val cutoff = f.repository().maxJournalId()
            f.db.historyBoundaryDao().put(HistoryBoundaryEntity(afterLocalId = cutoff, token = "reset"))
            f.trace.reset()
            val result = f.repository().mergeJournal(listOf(answer("active")), cutoff, "reset")
            assertEquals(1, result.imported); assertEquals(1, result.replayed)
            assertTrue(f.trace.entityQueries.filter { it.contains("FROM reviews") }.all { it.contains("WHERE id >") })
            val state = f.db.cardDao().all() to f.db.statsDao().lastDays(Int.MAX_VALUE)
            f.trace.reset()
            assertEquals(1, f.repository().replaySyncJournal(cutoff))
            assertEquals(state, f.db.cardDao().all() to f.db.statsDao().lastDays(Int.MAX_VALUE))
            assertTrue(f.trace.entityQueries.filter { it.contains("FROM reviews") }.all { it.contains("WHERE id >") })
            assertEquals(2, f.db.reviewDao().all().size) // The archive remains in the full export API.
        }
    }

    @Test fun `caller cancellation during Room preflight stops every snapshot entry point before entity reads`() = runBlocking {
        fixture { f ->
            f.seed()
            val repository = f.repository().profileTransfer
            val incoming = repository.snapshot()
            val operations: List<suspend () -> Unit> = listOf(
                { repository.snapshot() }, { repository.snapshotWithRevision() },
                { repository.preview(incoming) }, { repository.previewAdoption(incoming) }
            )
            for (operation in operations) {
                f.trace.reset()
                val observed = AtomicBoolean()
                val job = launch(start = CoroutineStart.LAZY) {
                    try { operation(); fail("Cancellation must propagate") }
                    catch (_: CancellationException) { observed.set(true) }
                }
                f.trace.afterStep = { sql -> if (sql.startsWith("SELECT COUNT(*) FROM (SELECT 1")) job.cancel() }
                job.start(); withTimeout(10_000) { job.join() }
                assertTrue(observed.get())
                assertTrue(f.trace.entityQueries.isEmpty())
                f.trace.afterStep = null
            }
        }
    }

    @Test fun `caller cancellation after active journal read prevents content materialization`() = runBlocking {
        fixture { f ->
            f.seed()
            f.db.reviewDao().insert(answer("active").toEntity())
            f.trace.reset()
            val observed = AtomicBoolean()
            val job = launch(start = CoroutineStart.LAZY) {
                try { f.repository().profileTransfer.snapshot(); fail("Cancellation must propagate") }
                catch (_: CancellationException) { observed.set(true) }
            }
            f.trace.afterStep = { sql -> if (sql.startsWith("SELECT * FROM reviews WHERE id >")) job.cancel() }
            job.start(); withTimeout(10_000) { job.join() }
            f.trace.afterStep = null
            assertTrue(observed.get())
            assertTrue(f.trace.entityQueries.none { it.contains("FROM packs") || it.contains("FROM chunks") })
        }
    }

    companion object { private const val TS = 1577880000000L }
}
