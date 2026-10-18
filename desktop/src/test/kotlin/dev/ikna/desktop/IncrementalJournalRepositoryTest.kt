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
import kotlinx.coroutines.*
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test
import androidx.sqlite.execSQL
import kotlinx.serialization.json.*
import java.io.File

/** Real SQLite; technical metadata only, no new study material. */
class IncrementalJournalRepositoryTest {
    private class Trace : SQLiteDriver {
        private val delegate = BundledSQLiteDriver()
        val queries = Collections.synchronizedList(mutableListOf<String>())
        @Volatile var afterStep: ((String) -> Unit)? = null
        override fun open(fileName: String): SQLiteConnection {
            val db = delegate.open(fileName)
            return object : SQLiteConnection by db {
                override fun prepare(sql: String): SQLiteStatement {
                    queries += sql
                    val statement = db.prepare(sql)
                    return object : SQLiteStatement by statement {
                        override fun step(): Boolean = statement.step().also { afterStep?.invoke(sql) }
                    }
                }
            }
        }
        fun reset() { queries.clear(); afterStep = null }
        fun snapshot(): List<String> = synchronized(queries) { queries.toList() }
    }
    private class Fixture(val device: String) {
        val home = Files.createTempDirectory("ikna-incremental-").toFile()
        var file = home.resolve("profile.db")
        val trace = Trace()
        var db = open()
        fun open(): IknaDatabase = Room.databaseBuilder<IknaDatabase>(file.absolutePath)
            .addMigrations(*IknaMigrations.ALL).setDriver(trace).setQueryCoroutineContext(Dispatchers.IO)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(connection: SQLiteConnection) { ChunkFtsIndex.create(connection) }
            }).build()
        fun repo() = RestoreRepository(db, db.cardDao(), db.reviewDao(), db.statsDao(), db.planDao(),
            ComponentRepository(db.componentDao(), db.chunkDao(), db.reviewDao(), db), Scheduler(dayStartHour = 4), GovernorConfig())
        fun auth() = JournalAuthorization(device, if (device == "a") "b" else "a", "group", "epoch", false, "operation", "d".repeat(64))
        suspend fun seed() {
            db.chunkDao().upsertPack(PackEntity("pack", 1, "en", 1, TS, title = "fixture"))
            db.chunkDao().insertChunksIgnore(listOf(ChunkEntity("target", "pack", "en", "", "", "", 0, 0, 1)))
            db.chunkDao().upsertPackChunks(listOf(PackChunkEntity("pack", "target", 1)))
        }
        fun reopen() { db.close(); db = open(); trace.reset() }
        /** Reconstruct a genuine committed schema13 fixture, copying only its declared columns. */
        fun migrateFrom13() {
            db.close()
            val previous = file
            val target = home.resolve("schema13.db")
            val schemaFile = listOf(File("../app/schemas/dev.ikna.data.db.IknaDatabase/13.json"),
                File("app/schemas/dev.ikna.data.db.IknaDatabase/13.json")).first { it.exists() }
            val schema = Json.parseToJsonElement(schemaFile.readText()).jsonObject.getValue("database").jsonObject
            BundledSQLiteDriver().open(target.absolutePath).use { sql ->
                sql.execSQL("ATTACH DATABASE '${previous.absolutePath.replace("'", "''")}' AS original")
                schema.getValue("entities").jsonArray.forEach { item ->
                    val entity = item.jsonObject
                    val name = entity.getValue("tableName").jsonPrimitive.content
                    sql.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", name))
                    entity["indices"]?.jsonArray.orEmpty().forEach { index ->
                        sql.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", name))
                    }
                    val columns = entity.getValue("fields").jsonArray.joinToString(",") {
                        "`" + it.jsonObject.getValue("columnName").jsonPrimitive.content + "`"
                    }
                    sql.execSQL("INSERT INTO `$name` ($columns) SELECT $columns FROM original.`$name`")
                }
                schema.getValue("setupQueries").jsonArray.forEach { sql.execSQL(it.jsonPrimitive.content) }
                ChunkFtsIndex.create(sql)
                sql.execSQL("PRAGMA user_version=13")
                sql.execSQL("DETACH DATABASE original")
            }
            file = target
            db = open()
        }
        suspend fun sql(sql: String) { db.useWriterConnection { it.usePrepared(sql) { statement -> statement.step() } } }
        fun close() { db.close(); home.deleteRecursively() }
    }
    private suspend fun fixtures(block: suspend (Fixture, Fixture) -> Unit) {
        val a = Fixture("a"); val b = Fixture("b")
        try { a.seed(); b.seed(); block(a, b) } finally { a.close(); b.close() }
    }
    private fun answer(id: String?, ts: Long = TS) = ReviewRecord(chunkId = "target", ts = ts, rating = 3,
        eventId = id, durationMs = 100, prevIsNew = true)
    private suspend fun consent(a: Fixture, b: Fixture) {
        val ar = a.repo().profileTransfer; val br = b.repo().profileTransfer
        val av = ar.snapshot(); val bv = br.snapshot()
        val ap = ar.preview(bv); val bp = br.preview(av)
        ar.commit(bv, ap, operationId = "operation", operationDigest = "d".repeat(64), journalAuthorization = a.auth())
        br.commit(av, bp, operationId = "operation", operationDigest = "d".repeat(64), journalAuthorization = b.auth())
    }
    private suspend fun reject(block: suspend () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {}
    }
    private suspend fun head(f: Fixture): JournalHead {
        var head = f.repo().incrementalJournal.localHead(f.auth())
        while (head.hasUnindexed) head = f.repo().incrementalJournal.localHead(f.auth())
        return head
    }
    private suspend fun transfer(a: Fixture, b: Fixture, fixed: JournalHead? = null): Int {
        val high = fixed ?: head(a)
        val source = a.repo().incrementalJournal; val target = b.repo().incrementalJournal
        var cursor = target.checkpoint(b.auth(), high).cursor
        var added = 0
        while (cursor != high.highWater) {
            val inv = requireNotNull(source.inventory(a.auth(), cursor, high.highWater))
            assertTrue(inv.entries.size + inv.dependencies.size <= 128)
            val request = target.missing(b.auth(), inv)
            val data = source.payload(a.auth(), request)
            assertTrue(ReviewRecord.json.encodeToString(JournalData.serializer(), data).toByteArray().size <= 252 * 1024)
            val result = target.apply(b.auth(), data)
            cursor = result.ack.cursor; added += result.imported
        }
        return added
    }

    @Test fun `consent maps legacy and undo without rewriting history and survives reopen`() = runBlocking {
        fixtures { a, b ->
            val first = a.db.reviewDao().insert(answer(null).toEntity())
            a.db.reviewDao().insert(answer(null, TS + 1).copy(rating = 0, undoOf = first).toEntity())
            val before = a.db.reviewDao().all()
            consent(a, b)
            assertEquals(before, a.db.reviewDao().all())
            val initial = head(a)
            assertEquals(2L, initial.highWater.sequence)
            assertEquals(0, transfer(a, b, initial))
            a.reopen(); b.reopen()
            assertEquals(initial, head(a))
            assertEquals(0, transfer(a, b, initial))
            assertEquals(a.repo().snapshotJournal(), b.repo().snapshotJournal())
        }
    }

    @Test fun `indexed replay equals full normalization with same-time legacy undo and unindexed modern rows`() = runBlocking {
        fixtures { a, b ->
            val original = a.db.reviewDao().insert(answer(null).toEntity())
            a.db.reviewDao().insert(answer(null).copy(durationMs = 101).toEntity())
            a.db.reviewDao().insert(answer(null).copy(rating = 0, undoOf = original, durationMs = 0).toEntity())
            consent(a, b)
            val modern = answer("modern", TS).copy(prevReps = 1, prevIsNew = false)
            a.db.reviewDao().insert(modern.toEntity())
            b.db.reviewDao().insert(modern.toEntity())
            b.db.reviewDao().insert(answer("trigger", TS + 1).toEntity())
            // A's modern row is committed but has not been indexed when receive applies.
            assertEquals(3L, a.db.incrementalJournalDao().stream()!!.sequence)
            assertEquals(1, transfer(b, a))
            b.repo().replaySyncJournal(0)
            assertEquals(a.repo().snapshotJournal(), b.repo().snapshotJournal())
            assertEquals(a.db.cardDao().all(), b.db.cardDao().all())
            assertEquals(a.db.statsDao().lastDays(Int.MAX_VALUE), b.db.statsDao().lastDays(Int.MAX_VALUE))
            assertEquals(2, a.db.reviewDao().allAnswers().count { it.ts == TS })
        }
    }

    @Test fun `schema13 migration preserves legacy prefix receipts and resumed purpose-aware stream`() = runBlocking {
        fixtures { a, b ->
            a.db.reviewDao().insert(answer(null).toEntity())
            consent(a, b)
            val ah = head(a); val bh = head(b)
            assertEquals(0, transfer(a, b))
            val asaved = a.db.incrementalJournalDao().stream()
            val bsaved = b.db.incrementalJournalDao().stream()
            val cp = b.db.incrementalJournalDao().checkpoint("a")
            val original = a.repo().profileTransfer.snapshot()
            val receipt = a.db.profileSyncOperationDao().find("operation")
            a.migrateFrom13(); b.migrateFrom13()
            assertEquals(14, IKNA_DATABASE_VERSION)
            assertEquals(asaved, a.db.incrementalJournalDao().stream())
            assertEquals(bsaved, b.db.incrementalJournalDao().stream())
            assertEquals(cp, b.db.incrementalJournalDao().checkpoint("a"))
            assertEquals(receipt, a.db.profileSyncOperationDao().find("operation"))
            assertEquals(ah, head(a)); assertEquals(bh, head(b))
            assertEquals(original, a.repo().profileTransfer.snapshot())
            assertTrue(a.db.reviewDao().all().all { it.purpose == null })
            val optional = answer("optional", TS + 10).copy(prevIsNew = false, purpose = "optional_hard")
            val raw = a.db.reviewDao().insert(optional.toEntity())
            a.db.reviewDao().insert(optional.copy(eventId = "optional-undo", ts = TS + 11,
                rating = 0, undoOf = raw, undoEventId = "optional", durationMs = 0).toEntity())
            assertEquals(2, transfer(a, b))
            assertEquals(ah.highWater.prefix, a.db.incrementalJournalDao().at(ah.binding.streamId, ah.highWater.sequence)!!.prefix)
            assertEquals(a.repo().snapshotJournal(), b.repo().snapshotJournal())
            assertEquals(2, b.db.reviewDao().all().count { it.purpose == "optional_hard" })
            val durable = b.db.incrementalJournalDao().checkpoint("a")
            b.reopen()
            assertEquals(0, transfer(a, b)); assertEquals(durable, b.db.incrementalJournalDao().checkpoint("a"))
        }
    }

    @Test fun `bootstrap index and receipt failure roll back together`() = runBlocking {
        fixtures { a, b ->
            a.db.reviewDao().insert(answer(null).toEntity())
            val target = a.repo().profileTransfer; val incoming = b.repo().profileTransfer.snapshot()
            val preview = target.preview(incoming); val before = a.db.reviewDao().all()
            a.trace.afterStep = { sql -> if (sql.startsWith("INSERT", true) && sql.contains("journal_event_index")) error("injected index failure") }
            reject { target.commit(incoming, preview, operationId = "operation", operationDigest = "d".repeat(64), journalAuthorization = a.auth()) }
            a.trace.reset()
            assertNull(a.db.profileSyncOperationDao().find("operation")); assertNull(a.db.incrementalJournalDao().stream())
            assertEquals(before, a.db.reviewDao().all())
            target.commit(incoming, preview, operationId = "operation", operationDigest = "d".repeat(64), journalAuthorization = a.auth())
            assertEquals(1L, head(a).highWater.sequence)
        }
    }

    @Test fun `fixed highwater bounded pages restart and later append preserve exact events`() = runBlocking {
        fixtures { a, b ->
            consent(a, b)
            a.db.reviewDao().insertAll((1..300).map { answer("event-$it", TS + it).toEntity() })
            val partial = a.repo().incrementalJournal.localHead(a.auth())
            assertTrue(partial.hasUnindexed); assertEquals(128L, partial.highWater.sequence)
            val high = head(a); assertEquals(300L, high.highWater.sequence)
            val cursor = b.repo().incrementalJournal.checkpoint(b.auth(), high).cursor
            val inv = a.repo().incrementalJournal.inventory(a.auth(), cursor, high.highWater)!!
            val data = a.repo().incrementalJournal.payload(a.auth(), b.repo().incrementalJournal.missing(b.auth(), inv))
            assertEquals(128, b.repo().incrementalJournal.apply(b.auth(), data).imported)
            a.reopen(); b.reopen()
            a.db.reviewDao().insert(answer("later", TS + 999).toEntity())
            assertEquals(172, transfer(a, b, high))
            assertEquals(300, b.db.reviewDao().all().size)
            assertEquals(1, transfer(a, b))
            assertEquals(a.repo().snapshotJournal(), b.repo().snapshotJournal())
        }
    }

    @Test fun `indexed raw identities and undo pointers cannot be masked by canonical mapping`() = runBlocking {
        fixtures { a, b ->
            consent(a, b)
            val original = a.db.reviewDao().insert(answer("original").toEntity())
            val other = a.db.reviewDao().insert(answer("other", TS + 1).toEntity())
            a.db.reviewDao().insert(answer("undo", TS + 2).copy(rating = 0, undoEventId = "original").toEntity(undoOf = original))
            val high = head(a); val zero = b.repo().incrementalJournal.checkpoint(b.auth(), high).cursor
            val repository = a.repo().incrementalJournal
            a.sql("UPDATE reviews SET eventId = 'changed' WHERE eventId = 'original'")
            try { repository.inventory(a.auth(), zero, high.highWater); fail("Changed event ID masked") }
            catch (_: JournalReconciliationRequiredException) {}
            a.sql("UPDATE reviews SET eventId = 'original' WHERE eventId = 'changed'")
            a.sql("UPDATE reviews SET undoEventId = 'other' WHERE eventId = 'undo'")
            try { repository.inventory(a.auth(), zero, high.highWater); fail("Changed undo ID masked") }
            catch (_: JournalReconciliationRequiredException) {}
            a.sql("UPDATE reviews SET undoEventId = 'original', undoOf = $other WHERE eventId = 'undo'")
            try { repository.inventory(a.auth(), zero, high.highWater); fail("Changed undo pointer masked") }
            catch (_: JournalReconciliationRequiredException) {}
            a.sql("UPDATE reviews SET undoOf = NULL WHERE eventId = 'undo'")
            try { repository.inventory(a.auth(), zero, high.highWater); fail("Missing undo pointer masked") }
            catch (_: JournalReconciliationRequiredException) {}
            assertEquals(zero, b.repo().incrementalJournal.checkpoint(b.auth(), high).cursor)
        }
    }

    @Test fun `duplicate old pages acknowledge durable checkpoint without replay or journal scan`() = runBlocking {
        fixtures { a, b ->
            consent(a, b)
            a.db.reviewDao().insertAll((1..200).map { answer("event-$it", TS + it).toEntity() })
            val high = head(a); val zero = b.repo().incrementalJournal.checkpoint(b.auth(), high).cursor
            val inv = a.repo().incrementalJournal.inventory(a.auth(), zero, high.highWater)!!
            val data = a.repo().incrementalJournal.payload(a.auth(), b.repo().incrementalJournal.missing(b.auth(), inv))
            b.repo().incrementalJournal.apply(b.auth(), data); transfer(a, b, high)
            b.trace.reset()
            val result = b.repo().incrementalJournal.apply(b.auth(), JournalData(inv, emptyList()))
            assertEquals(0, result.imported); assertEquals(high.highWater, result.ack.cursor); assertNull(result.ack.pageDigest)
            assertFalse(b.trace.snapshot().any { it.startsWith("SELECT * FROM reviews WHERE id >") || it.startsWith("SELECT * FROM cards") })
        }
    }

    @Test fun `undo dependency digest is verified and both append and checkpoint are atomic`() = runBlocking {
        fixtures { a, b ->
            consent(a, b)
            val original = a.db.reviewDao().insert(answer("original").toEntity())
            a.db.reviewDao().insert(answer("undo", TS + 1).copy(rating = 0, undoEventId = "original").toEntity(undoOf = original))
            val high = head(a); val zero = b.repo().incrementalJournal.checkpoint(b.auth(), high).cursor
            val inv = a.repo().incrementalJournal.inventory(a.auth(), zero, high.highWater)!!
            assertEquals("original", inv.dependencies.single().eventId)
            val data = a.repo().incrementalJournal.payload(a.auth(), b.repo().incrementalJournal.missing(b.auth(), inv))
            reject { b.repo().incrementalJournal.apply(b.auth(), data.copy(records = data.records.map { if (it.eventId == "original") it.copy(durationMs = 101) else it })) }
            assertEquals(0, b.db.reviewDao().all().size)
            assertEquals(zero, b.repo().incrementalJournal.checkpoint(b.auth(), high).cursor)
            assertEquals(2, b.repo().incrementalJournal.apply(b.auth(), data).imported)
            val rows = b.db.reviewDao().all()
            assertEquals(rows.single { it.eventId == "original" }.id, rows.single { it.eventId == "undo" }.undoOf)
            assertEquals(0, b.db.reviewDao().allAnswers().size)
        }
    }

    @Test fun `wrong stream cursor direction content boundary and absent index fail closed`() = runBlocking {
        fixtures { a, b ->
            consent(a, b); a.db.reviewDao().insert(answer("a").toEntity())
            val high = head(a); val zero = b.repo().incrementalJournal.checkpoint(b.auth(), high).cursor
            reject { b.repo().incrementalJournal.checkpoint(b.auth(), high.copy(binding = high.binding.copy(streamId = "other"))) }
            reject { b.repo().incrementalJournal.checkpoint(b.auth(), high.copy(binding = high.binding.copy(targetDevice = "other"))) }
            reject { a.repo().incrementalJournal.inventory(a.auth(), zero.copy(prefix = "0".repeat(64)), high.highWater) }
            reject { a.repo().incrementalJournal.localHead(a.auth().copy(epoch = "other")) }
            a.db.chunkDao().setPackTitle("pack", "changed")
            reject { a.repo().incrementalJournal.localHead(a.auth()) }
            a.db.chunkDao().setPackTitle("pack", "fixture")
            a.db.historyBoundaryDao().put(HistoryBoundaryEntity(afterLocalId = 0, token = "reset"))
            reject { a.repo().incrementalJournal.localHead(a.auth()) }
            reject { a.repo().incrementalJournal.localHead(a.auth(), 0, "reset") }
            b.db.incrementalJournalDao().clearStreams()
            try { b.repo().incrementalJournal.localHead(b.auth()); fail("Missing stream accepted") }
            catch (_: JournalReconciliationRequiredException) {}
            assertNull(b.db.incrementalJournalDao().stream())
        }
    }

    @Test fun `content identity never queries reviews and postconsent oversized history remains pageable`() = runBlocking {
        fixtures { a, b ->
            consent(a, b)
            a.db.reviewDao().insertAll((1..1050).map { answer("event-$it", TS + it).copy(timingDiscardReason = "x".repeat(4096)).toEntity() })
            a.trace.reset(); a.repo().profileTransfer.contentIdentity()
            assertFalse(a.trace.snapshot().any { Regex("(?i)\\b(reviews|journal_event_index)\\b").containsMatchIn(it) })
            try { a.repo().profileTransfer.snapshot(); fail("Expected bootstrap byte cap") } catch (_: ProfileTransferLimitException) {}
            val high = head(a)
            assertEquals(1050L, high.highWater.sequence)
            val zero = b.repo().incrementalJournal.checkpoint(b.auth(), high).cursor
            val page = a.repo().incrementalJournal.inventory(a.auth(), zero, high.highWater)!!
            assertTrue(page.entries.size in 1..127)
            val data = a.repo().incrementalJournal.payload(a.auth(), b.repo().incrementalJournal.missing(b.auth(), page))
            assertEquals(page.entries.size, b.repo().incrementalJournal.apply(b.auth(), data).imported)
        }
    }

    @Test fun `crafted known-only inventory stops cumulative resolution before retaining an oversized page`() = runBlocking {
        fixtures { a, b ->
            a.db.reviewDao().insertAll((1..50).map { answer("known-$it", TS + it).copy(timingDiscardReason = "x".repeat(10_000)).toEntity() })
            consent(a, b)
            val high = head(a); val zero = b.repo().incrementalJournal.checkpoint(b.auth(), high).cursor
            val rows = a.db.incrementalJournalDao().page(high.binding.streamId, 0, high.highWater.sequence, 128)
            val unsealed = JournalInventory(high.binding, zero, high.highWater, high.highWater,
                rows.map { JournalEntry(it.sequence, it.eventId, it.payloadDigest, it.undoEventId) }, "")
            val crafted = unsealed.copy(pageDigest = ProfileTransferCodec.digest(
                ReviewRecord.json.encodeToString(JournalInventory.serializer(), unsealed).toByteArray(Charsets.UTF_8)))
            b.trace.reset()
            reject { b.repo().incrementalJournal.apply(b.auth(), JournalData(crafted, emptyList())) }
            val loads = b.trace.snapshot().count { it.startsWith("SELECT * FROM reviews WHERE id =") }
            assertTrue("Must stop before loading all50 known records; loaded $loads", loads in 1..49)
            assertEquals(zero, b.repo().incrementalJournal.checkpoint(b.auth(), high).cursor)
            val valid = a.repo().incrementalJournal.inventory(a.auth(), zero, high.highWater)!!
            b.trace.reset()
            assertEquals(0, b.repo().incrementalJournal.apply(b.auth(), JournalData(valid, emptyList())).imported)
            assertFalse("Known-only advancing page must not scan active aggregate totals",
                b.trace.snapshot().any { it.contains("SUM(durationMs)") })
            assertFalse("Known-only advancing page must not replay",
                b.trace.snapshot().any { it.startsWith("SELECT * FROM reviews WHERE id >") || it.startsWith("SELECT * FROM cards") })
            assertEquals(valid.to, b.repo().incrementalJournal.checkpoint(b.auth(), high).cursor)
        }
    }

    @Test fun `unmapped legacy and oversized new record reject before entity materialization`() = runBlocking {
        fixtures { a, b ->
            consent(a, b)
            a.db.reviewDao().insert(answer(null).toEntity()); a.trace.reset()
            try { head(a); fail("Legacy accepted") } catch (_: JournalReconciliationRequiredException) {}
            assertFalse(a.trace.snapshot().any { it.startsWith("SELECT * FROM reviews") })
            b.db.reviewDao().insert(answer("huge").copy(timingDiscardReason = "x".repeat(300_000)).toEntity()); b.trace.reset()
            try { head(b); fail("Oversized accepted") } catch (_: IncrementalJournalLimitException) {}
            assertFalse(b.trace.snapshot().any { it.startsWith("SELECT * FROM reviews") })
        }
    }

    @Test fun `originating caller cancellation during append rolls back history index replay and checkpoint`() = runBlocking {
        fixtures { a, b ->
            consent(a, b); a.db.reviewDao().insert(answer("a").toEntity())
            val high = head(a); val zero = b.repo().incrementalJournal.checkpoint(b.auth(), high).cursor
            val inv = a.repo().incrementalJournal.inventory(a.auth(), zero, high.highWater)!!
            val data = a.repo().incrementalJournal.payload(a.auth(), b.repo().incrementalJournal.missing(b.auth(), inv))
            val fired = AtomicBoolean()
            val task = launch(start = CoroutineStart.LAZY) { b.repo().incrementalJournal.apply(b.auth(), data) }
            b.trace.afterStep = { sql -> if (sql.startsWith("INSERT", true) && sql.contains("reviews") && fired.compareAndSet(false, true)) task.cancel() }
            task.start(); task.join(); b.trace.reset()
            assertTrue(fired.get()); assertTrue(task.isCancelled)
            assertEquals(0, b.db.reviewDao().all().size)
            assertEquals(0L, head(b).highWater.sequence)
            assertEquals(zero, b.repo().incrementalJournal.checkpoint(b.auth(), high).cursor)
            assertEquals(1, b.repo().incrementalJournal.apply(b.auth(), data).imported)
        }
    }

    @Test fun `checked duration includes committed unindexed local rows and rejected page does not advance`() = runBlocking {
        fixtures { a, b ->
            consent(a, b)
            a.db.reviewDao().insert(answer("a").copy(durationMs = 1).toEntity())
            b.db.reviewDao().insert(answer("local").copy(durationMs = Long.MAX_VALUE).toEntity())
            val high = head(a); val zero = b.repo().incrementalJournal.checkpoint(b.auth(), high).cursor
            val inv = a.repo().incrementalJournal.inventory(a.auth(), zero, high.highWater)!!
            val data = a.repo().incrementalJournal.payload(a.auth(), b.repo().incrementalJournal.missing(b.auth(), inv))
            try { b.repo().incrementalJournal.apply(b.auth(), data); fail("Overflow accepted") } catch (_: ArithmeticException) {}
            assertEquals(1, b.db.reviewDao().all().size)
            assertEquals(zero, b.repo().incrementalJournal.checkpoint(b.auth(), high).cursor)
        }
    }

    @Test fun `external restore and full wipe invalidate stream without reusing consent`() = runBlocking {
        fixtures { a, b ->
            consent(a, b)
            a.repo().restoreFromJsonl(ReviewRecord.json.encodeToString(ReviewRecord.serializer(), answer("restore")))
            assertNull(a.db.incrementalJournalDao().stream())
            reject { head(a) }
            b.db.wipeAllData(); assertNull(b.db.incrementalJournalDao().stream())
            assertNull(b.db.profileSyncOperationDao().find("operation"))
        }
    }

    companion object { private const val TS = 1_700_000_000_000L }
}
