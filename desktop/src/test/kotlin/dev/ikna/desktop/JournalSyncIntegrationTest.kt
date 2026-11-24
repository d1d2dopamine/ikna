package dev.ikna.desktop

import dev.ikna.data.db.*
import dev.ikna.data.export.ReviewRecord
import dev.ikna.data.repo.*
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.GovernorConfig
import java.nio.file.Files
import java.io.File
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.room.useWriterConnection
import kotlinx.serialization.json.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Actual disposable Room databases; never learner data. */
class JournalSyncIntegrationTest {
    private fun repo(db: IknaDatabase) = RestoreRepository(db, db.cardDao(), db.reviewDao(),
        db.statsDao(), db.planDao(), ComponentRepository(db.componentDao(), db.chunkDao(), db.reviewDao()),
        Scheduler(dayStartHour = 4), GovernorConfig())

    private suspend fun fixture(block: suspend (IknaDatabase, RestoreRepository) -> Unit) {
        val home = Files.createTempDirectory("ikna-sync-test-").toFile()
        val db = openIknaDatabase(home.resolve("test.db"))
        try {
            db.chunkDao().upsertPack(PackEntity("pack", 1, "en", 1, TS))
            db.chunkDao().insertChunksIgnore(listOf(ChunkEntity("target", "pack", "en", "word", "word", "meaning", 0, 4, 1)))
            block(db, repo(db))
        } finally { db.close(); home.deleteRecursively() }
    }
    private fun answer(id: String, rating: Int = 3) = ReviewRecord(chunkId = "target", ts = TS,
        rating = rating, eventId = id, durationMs = 100, prevIsNew = true)

    @Test fun `same timestamp independent answers survive order duplicates and retry`() = runBlocking {
        fixture { a, ar -> fixture { b, br ->
            val x = answer("event-a", 2); val y = answer("event-b", 4)
            ar.mergeJournal(listOf(y, x, x)); br.mergeJournal(listOf(x, y))
            assertEquals(2, a.reviewDao().all().size)
            assertEquals(ar.snapshotJournal(), br.snapshotJournal())
            assertEquals(a.cardDao().all(), b.cardDao().all())
            assertEquals(0, ar.mergeJournal(br.snapshotJournal()).imported)
            assertEquals(0, ar.mergeJournal(br.snapshotJournal()).replayed)
        } }
    }
    @Test fun `skipMissingTargets imports installed deck reviews and skips uninstalled targets`() = runBlocking {
        fixture { db, r ->
            val result = r.mergeJournal(
                listOf(answer("installed-a"), answer("uninstalled-b").copy(chunkId = "missing-deck-target")),
                skipMissingTargets = true
            )
            assertEquals(1, result.imported)
            assertEquals(1, result.skipped)
            assertEquals(listOf("installed-a"), db.reviewDao().all().map { it.eventId })
        }
    }

    @Test fun `conflicting identity and missing content fail without partial state`() = runBlocking {
        fixture { db, r ->
            r.mergeJournal(listOf(answer("a")))
            val before = r.snapshotJournal(); val cards = db.cardDao().all()
            for (bad in listOf(answer("a", 1), answer("unknown").copy(chunkId = "missing"),
                answer("nan").copy(prevStability = Double.NaN),
                answer("undo").copy(rating = 0, undoEventId = "missing"))) {
                try { r.mergeJournal(listOf(answer("new"), bad)); fail("must reject") }
                catch (_: IllegalArgumentException) { }
                assertEquals(before, r.snapshotJournal()); assertEquals(cards, db.cardDao().all())
            }
        }
    }
    @Test fun `stable undo invalidates stale plan while completion and budget remain local`() = runBlocking {
        fixture { db, r ->
            val plan = DailyPlanEntity("2020-01-01", "target:0", 1, 40, 1, 0, "OK", 0, TS)
            db.planDao().upsert(plan)
            db.statsDao().upsert(DailyStatEntity("2020-01-01", 1, 1, 100, 1.0, true, 1))
            val a = answer("a"); val u = answer("undo").copy(rating = 0, undoEventId = "a")
            r.mergeJournal(listOf(u, a))
            assertEquals(0, db.reviewDao().allAnswers().size)
            assertNull(db.planDao().latest())
            assertEquals(1, db.statsDao().day("2020-01-01")!!.newIntroduced)
            db.planDao().upsert(plan)
            assertEquals(0, r.mergeJournal(listOf(a, u)).imported)
            assertEquals(plan, db.planDao().latest())
            assertTrue(db.statsDao().day("2020-01-01")!!.planCompleted)
            assertTrue(db.cardDao().card("target", 0)!!.isNew)
        }
    }
    @Test fun `merge waits for in flight plan build and atomically invalidates its result`() = runBlocking {
        fixture { db, r ->
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val learning = LearningRepository(db, db.cardDao(), db.chunkDao(), db.reviewDao(),
                db.statsDao(), db.governorDao(), object : PlanDao by db.planDao() {
                    override suspend fun plan(day: String): DailyPlanEntity? {
                        entered.complete(Unit)
                        release.await()
                        return db.planDao().plan(day)
                    }
                }, db.browseDao(), ComponentRepository(db.componentDao(), db.chunkDao(), db.reviewDao()),
                Scheduler(dayStartHour = 4), dev.ikna.domain.governor.ChunkSelector(), GovernorConfig())
            r.journalMergeGuard = { block -> learning.withJournalWriteLock(block) }
            val build = async { learning.ensureDailyPlan(TS) }
            withTimeout(5000) { entered.await() }
            val merge = async(start = CoroutineStart.UNDISPATCHED) { r.mergeJournal(listOf(answer("after-build"))) }
            assertFalse(merge.isCompleted)
            assertTrue(db.reviewDao().all().isEmpty())
            release.complete(Unit)
            withTimeout(5000) { build.await(); merge.await() }
            assertNull(db.planDao().latest())
            assertEquals(1, db.reviewDao().all().size)
        }
    }

    @Test fun `deck deletion and merge serialize without orphan cards or lost history`() = runBlocking {
        for (deleteFirst in listOf(false, true)) fixture { db, r ->
            db.chunkDao().upsertPackChunks(listOf(PackChunkEntity("pack", "target", 1)))
            r.mergeJournal(listOf(answer("existing")))
            if (deleteFirst) {
                db.chunkDao().deletePackPreservingSharedHistory("pack")
                try { r.mergeJournal(listOf(answer("incoming"))); fail("deleted content must reject merge") }
                catch (_: IllegalArgumentException) { }
                assertEquals(1, db.reviewDao().all().size)
            } else {
                r.mergeJournal(listOf(answer("incoming")))
                db.chunkDao().deletePackPreservingSharedHistory("pack")
                assertEquals(2, db.reviewDao().all().size)
            }
            assertNull(db.chunkDao().chunk("target"))
            assertTrue(db.cardDao().all().isEmpty())
        }
    }

    @Test fun `deck deletion failure rolls back cards chunks memberships and pack`() = runBlocking {
        fixture { db, r ->
            db.chunkDao().upsertPackChunks(listOf(PackChunkEntity("pack", "target", 1)))
            r.mergeJournal(listOf(answer("existing")))
            db.useWriterConnection { connection ->
                connection.usePrepared("CREATE TRIGGER fail_pack_delete BEFORE DELETE ON packs BEGIN SELECT RAISE(ABORT, 'injected'); END") { it.step() }
            }
            try { db.chunkDao().deletePackPreservingSharedHistory("pack"); fail("must rollback") }
            catch (_: androidx.sqlite.SQLiteException) { }
            assertNotNull(db.chunkDao().pack("pack"))
            assertNotNull(db.chunkDao().chunk("target"))
            assertNotNull(db.cardDao().card("target", 0))
            assertEquals(1, db.chunkDao().packChunks("pack").size)
            assertEquals(1, db.reviewDao().all().size)
        }
    }

    @Test fun `duration overflow rejects sync and manual replay atomically`() = runBlocking {
        fixture { db, r ->
            r.mergeJournal(listOf(answer("existing").copy(durationMs = 0)))
            val before = r.snapshotJournal()
            val cards = db.cardDao().all()
            val stats = db.statsDao().lastDays(30)
            val plan = DailyPlanEntity("2020-01-01", "target:0", 1, 40, 1, 0, "OK", 0, TS)
            db.planDao().upsert(plan)
            val records = listOf(answer("max").copy(durationMs = Long.MAX_VALUE),
                answer("overflow").copy(durationMs = 1))
            for (sync in listOf(true, false)) {
                try {
                    if (sync) r.mergeJournal(records)
                    else r.restoreFromJsonl(records.joinToString("\n") {
                        ReviewRecord.json.encodeToString(ReviewRecord.serializer(), it)
                    })
                    fail("overflow must reject the whole transaction")
                } catch (_: ArithmeticException) { }
                assertEquals(before, r.snapshotJournal())
                assertEquals(cards, db.cardDao().all())
                assertEquals(plan, db.planDao().latest())
                assertEquals(stats, db.statsDao().lastDays(30))
            }
        }
    }

    @Test fun `legacy identities survive local row renumbering and backup restore`() = runBlocking {
        fixture { a, ar -> fixture { b, br ->
            val old = answer("unused").copy(eventId = null, id = 17)
            a.reviewDao().insert(old.toEntity(id = 17))
            b.reviewDao().insert(old.toEntity(id = 83))
            assertEquals(ar.snapshotJournal(), br.snapshotJournal())
            val text = ReviewRecord.json.encodeToString(ReviewRecord.serializer(), old)
            assertEquals(0, ar.restoreFromJsonl(text).imported)
            assertEquals(0, br.mergeJournal(ar.snapshotJournal()).imported)
            fixture { c, cr ->
                cr.mergeJournal(ar.snapshotJournal())
                assertEquals(0, cr.restoreFromJsonl(text).imported)
                assertEquals(1, c.reviewDao().all().size)
            }
        } }
    }
    @Test fun `fresh history cutoff excludes retained old answers from snapshot and replay`() = runBlocking {
        fixture { db, r ->
            r.mergeJournal(listOf(answer("old")))
            val cutoff = r.maxJournalId()
            db.cardDao().clear(); db.statsDao().clear()
            assertTrue(r.snapshotJournal(cutoff).isEmpty())
            r.mergeJournal(listOf(answer("new", 2)), cutoff)
            assertEquals(listOf("new"), r.snapshotJournal(cutoff).map { it.eventId })
            assertEquals(2, db.reviewDao().all().size)
            assertEquals(1, db.cardDao().card("target", 0)!!.reps)
        }
    }
    @Test fun `concurrent writers merge atomically without losing independent events`() = runBlocking {
        fixture { db, r ->
            coroutineScope { (0 until 12).map { n -> async(Dispatchers.Default) {
                r.mergeJournal(listOf(answer("event-$n")))
            } }.awaitAll() }
            assertEquals(12, db.reviewDao().all().size)
            assertEquals(12, db.cardDao().card("target", 0)!!.reps)
        }
    }
    @Test fun `undo of earlier answer does not seed from later contaminated snapshot`() = runBlocking {
        fixture { a, ar -> fixture { b, br ->
            val first = answer("first", 3).copy(prevReps = 0, prevStability = 0.0)
            val later = answer("later", 4).copy(ts = TS + 86400000L, prevIsNew = false,
                prevReps = 1, prevStability = 123.0, prevLastReviewAt = TS)
            val undo = answer("undo").copy(ts = TS + 86400001L, rating = 0, undoEventId = "first")
            ar.mergeJournal(listOf(first, later, undo))
            br.mergeJournal(listOf(later.copy(prevIsNew = true, prevReps = 0, prevStability = 0.0,
                prevLastReviewAt = null)))
            val actual = a.cardDao().card("target", 0)!!
            val expected = b.cardDao().card("target", 0)!!
            assertEquals(1, actual.reps)
            assertEquals(expected.stability, actual.stability, 0.0)
        } }
    }
    @Test fun `replay write failure rolls journal union back`() = runBlocking {
        fixture { db, r ->
            r.mergeJournal(listOf(answer("first")))
            val before = r.snapshotJournal(); val cards = db.cardDao().all()
            val realCards = db.cardDao()
            val failing = RestoreRepository(db, object : CardDao by realCards {
                override suspend fun upsertAll(cards: List<CardEntity>) { error("injected replay failure") }
            }, db.reviewDao(), db.statsDao(), db.planDao(),
                ComponentRepository(db.componentDao(), db.chunkDao(), db.reviewDao()), Scheduler(), GovernorConfig())
            try { failing.mergeJournal(listOf(answer("second"))); fail("must rollback") }
            catch (e: IllegalStateException) { assertEquals("injected replay failure", e.message) }
            assertEquals(before, r.snapshotJournal()); assertEquals(cards, db.cardDao().all())
            assertEquals(1, r.mergeJournal(listOf(answer("second"))).imported)
        }
    }
    @Test fun `sync undo replays merged history and never touches reset archive`() = runBlocking {
        fixture { db, r ->
            val learning = LearningRepository(db, db.cardDao(), db.chunkDao(), db.reviewDao(),
                db.statsDao(), db.governorDao(), db.planDao(), db.browseDao(),
                ComponentRepository(db.componentDao(), db.chunkDao(), db.reviewDao()),
                Scheduler(dayStartHour = 4), dev.ikna.domain.governor.ChunkSelector(), GovernorConfig())
            learning.syncHistoryBoundary = { 0L }
            r.mergeJournal(listOf(answer("a"), answer("b", 4)))
            assertEquals("target:0", learning.undoLast(TS + 1))
            assertEquals(1, db.cardDao().card("target", 0)!!.reps)
            val cutoff = learning.resetProgressWithBoundary()
            learning.syncHistoryBoundary = { cutoff }
            assertNull(learning.undoLast(TS + 2))
            r.mergeJournal(listOf(answer("new")), cutoff, r.persistedHistoryBoundary()?.token)
            assertEquals("target:0", learning.undoLast(TS + 3))
            assertTrue(db.cardDao().card("target", 0)!!.isNew)
            assertEquals(5, db.reviewDao().all().size)
        }
    }
    @Test fun `pending history mutation blocks replay before writes and reset retry remains available`() = runBlocking {
        fixture { db, r ->
            r.mergeJournal(listOf(answer("a")))
            val before = db.cardDao().all()
            r.historyBoundary = { error("pending reset") }
            try { r.replayFromLog(); fail("must gate") } catch (_: IllegalStateException) { }
            assertEquals(before, db.cardDao().all())
            val learning = LearningRepository(db, db.cardDao(), db.chunkDao(), db.reviewDao(),
                db.statsDao(), db.governorDao(), db.planDao(), db.browseDao(),
                ComponentRepository(db.componentDao(), db.chunkDao(), db.reviewDao(), db),
                Scheduler(), dev.ikna.domain.governor.ChunkSelector(), GovernorConfig())
            learning.ensureHistoryWritable = { error("pending reset") }
            try { learning.undoLast(TS + 1); fail("must gate") } catch (_: IllegalStateException) { }
            assertEquals(1, db.reviewDao().all().size)
            assertEquals(1L, learning.resetProgressWithBoundary())
            assertTrue(db.cardDao().all().isEmpty())
        }
    }
    @Test fun `persisted reset boundary overrides stale zero cutoff in maintenance`() = runBlocking {
        fixture { db, r ->
            r.mergeJournal(listOf(answer("old")))
            val marker = HistoryBoundaryEntity(afterLocalId = r.maxJournalId(), token = "copied-db-boundary")
            db.inTransaction { db.historyBoundaryDao().put(marker); db.cardDao().clear(); db.componentDao().clear() }
            r.historyBoundary = { 0L } // old configuration copied independently of this database
            r.replayFromLog()
            assertTrue(db.cardDao().all().isEmpty())
            val components = ComponentRepository(db.componentDao(), db.chunkDao(), db.reviewDao(), db)
            components.historyBoundary = { 0L }
            components.rebuildFromReviews()
            assertTrue(db.componentDao().all().isEmpty())
            try { r.snapshotJournal(); fail("missing configuration token must reject") }
            catch (_: IllegalArgumentException) { }
            try { r.mergeJournal(listOf(answer("remote"))); fail("missing token must reject") }
            catch (_: IllegalArgumentException) { }
            assertEquals(1, db.reviewDao().all().size)
            assertTrue(r.snapshotJournal(marker.afterLocalId, marker.token).isEmpty())
        }
    }
    @Test fun `memory converges while due times keep each local study day boundary`() = runBlocking {
        fixture { a, _ -> fixture { b, _ ->
            fun local(db: IknaDatabase, hour: Int) = RestoreRepository(db, db.cardDao(), db.reviewDao(),
                db.statsDao(), db.planDao(), ComponentRepository(db.componentDao(), db.chunkDao(), db.reviewDao()),
                Scheduler(dayStartHour = hour), GovernorConfig(dayStartHour = hour))
            val ar = local(a, 4); val br = local(b, 12)
            val journal = listOf(answer("portable-answer").copy(ts = TS - 6 * 3600000L))
            ar.mergeJournal(journal); br.mergeJournal(journal)
            val ac = a.cardDao().card("target", 0)!!; val bc = b.cardDao().card("target", 0)!!
            assertEquals(ac.stability, bc.stability, 0.0)
            assertEquals(ac.difficulty, bc.difficulty, 0.0)
            assertEquals(ac.reps, bc.reps); assertEquals(ac.lapses, bc.lapses)
            assertEquals(dev.ikna.domain.time.DayBoundary(4).startOfDay(ac.dueAt), ac.dueAt)
            assertEquals(dev.ikna.domain.time.DayBoundary(12).startOfDay(bc.dueAt), bc.dueAt)
            assertNotEquals(ac.dueAt, bc.dueAt)
            assertEquals(ar.snapshotJournal(), br.snapshotJournal())
        } }
    }
    @Test fun `clock rollback cannot double count a later snapshot baseline`() = runBlocking {
        fixture { db, r ->
            val original = answer("first").copy(ts = TS + 1000, prevReps = 0, prevIsNew = true)
            val later = answer("later").copy(ts = TS, prevReps = 1, prevIsNew = false,
                prevLastReviewAt = TS + 1000, prevStability = 3.0)
            r.mergeJournal(listOf(later, original))
            assertEquals(2, db.cardDao().card("target", 0)!!.reps)
            assertEquals(2, db.reviewDao().allAnswers().size)
        }
        fixture { db, r ->
            // Imported partial history still includes its explicitly recorded earlier repetitions.
            r.mergeJournal(listOf(answer("partial").copy(prevReps = 100, prevIsNew = false,
                prevStability = 3.0, prevLastReviewAt = TS - 86400000)))
            assertEquals(101, db.cardDao().card("target", 0)!!.reps)
        }
    }
    @Test fun `version ten migration preserves all journal fields and matches fresh schema`() = runBlocking {
        val home = Files.createTempDirectory("ikna-migration11-").toFile()
        val file = home.resolve("v10.db")
        val schemaFile = listOf(File("../app/schemas/dev.ikna.data.db.IknaDatabase/10.json"),
            File("app/schemas/dev.ikna.data.db.IknaDatabase/10.json")).first { it.exists() }
        val schema = Json.parseToJsonElement(schemaFile.readText()).jsonObject.getValue("database").jsonObject
        try {
            BundledSQLiteDriver().open(file.path).use { connection ->
                for (entity in schema.getValue("entities").jsonArray) {
                    val obj = entity.jsonObject
                    val name = obj.getValue("tableName").jsonPrimitive.content
                    connection.execSQL(obj.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", name))
                    for (index in obj.getValue("indices").jsonArray) {
                        connection.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", name))
                    }
                }
                connection.execSQL("INSERT INTO reviews(id,chunkId,level,ts,rating,elapsedDays,stabilityBefore,stabilityAfter,difficultyBefore,difficultyAfter,durationMs,wasAmnesty) VALUES(42,'target',0,$TS,3,2.5,1.5,3.5,5.0,4.5,123,0)")
                connection.execSQL("PRAGMA user_version = 10")
            }
            val migrated = openIknaDatabase(file)
            try {
                val row = migrated.reviewDao().all().single()
                assertEquals(42L, row.id); assertEquals(123L, row.durationMs)
                assertEquals(2.5, row.elapsedDays, 0.0); assertEquals(3.5, row.stabilityAfter, 0.0)
                assertNull(row.eventId); assertNull(row.undoEventId)
                // Room validates migrated columns/indexes against generated version 12 metadata on open.
                assertEquals(14, IKNA_DATABASE_VERSION)
            } finally { migrated.close() }
        } finally { home.deleteRecursively() }
    }
    companion object { const val TS = 1577880000000L }
}
