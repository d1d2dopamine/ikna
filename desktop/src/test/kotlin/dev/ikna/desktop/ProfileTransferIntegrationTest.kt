package dev.ikna.desktop

import dev.ikna.data.db.*
import dev.ikna.data.export.*
import dev.ikna.data.repo.*
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.GovernorConfig
import java.nio.file.Files
import java.io.File
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.serialization.json.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Disposable metadata/content fixtures only; no generated study material. */
class ProfileTransferIntegrationTest {
    private fun repo(db: IknaDatabase) = RestoreRepository(db, db.cardDao(), db.reviewDao(),
        db.statsDao(), db.planDao(), ComponentRepository(db.componentDao(), db.chunkDao(), db.reviewDao()),
        Scheduler(dayStartHour = 4), GovernorConfig())
    private suspend fun fixture(id: String, block: suspend (IknaDatabase, RestoreRepository) -> Unit) {
        val home = Files.createTempDirectory("ikna-profile-test-").toFile()
        val db = openIknaDatabase(home.resolve("test.db"))
        try {
            db.chunkDao().upsertPack(PackEntity(id, 1, "en", 1, TS, title = "same title"))
            db.chunkDao().insertChunksIgnore(listOf(ChunkEntity(id, id, "en", "word", "word", "meaning", 0, 4, 1)))
            db.chunkDao().upsertPackChunks(listOf(PackChunkEntity(id, id, 1)))
            block(db, repo(db))
        } finally { db.close(); home.deleteRecursively() }
    }
    private fun answer(chunk: String, event: String) = ReviewRecord(chunkId = chunk, ts = TS,
        rating = 3, eventId = event, durationMs = 100, prevIsNew = true)

    @Test fun `preview is read only and merge preserves same named unique decks history and undo`() = runBlocking {
        fixture("a") { a, ar -> fixture("b") { b, br ->
            ar.mergeJournal(listOf(answer("a", "event-a")))
            br.mergeJournal(listOf(answer("b", "event-b"),
                answer("b", "undo-b").copy(rating = 0, undoEventId = "event-b")))
            val incoming = br.profileTransfer.snapshot()
            val before = ar.profileTransfer.snapshot()
            val preview = ar.profileTransfer.preview(incoming)
            assertTrue(preview.canMerge)
            assertEquals(1, preview.addedPacks); assertEquals(1, preview.addedTargets)
            assertEquals(2, preview.addedReviews)
            assertEquals(before, ar.profileTransfer.snapshot())
            ar.profileTransfer.commit(incoming, preview)
            assertEquals(2, a.chunkDao().packs().size)
            assertEquals(3, a.reviewDao().all().size)
            val undo = a.reviewDao().all().single { it.eventId == "undo-b" }
            assertEquals(a.reviewDao().all().single { it.eventId == "event-b" }.id, undo.undoOf)
            assertFalse(a.chunkDao().pack("b")!!.isActive)
            val reverse = ar.profileTransfer.snapshot()
            br.profileTransfer.commit(reverse, br.profileTransfer.preview(reverse))
            assertEquals(ProfileTransferCodec.profileIdentity(ar.profileTransfer.snapshot()),
                ProfileTransferCodec.profileIdentity(br.profileTransfer.snapshot()))
        } }
    }

    @Test fun `same identity changed text and tokens blocks whole merge`() = runBlocking {
        fixture("a") { db, r ->
            val original = r.profileTransfer.snapshot()
            val changed = original.copy(chunks = original.chunks.map { it.copy(translation = "changed fixture") })
            val preview = r.profileTransfer.preview(changed)
            assertFalse(preview.canMerge)
            expectRejected { r.profileTransfer.commit(changed, preview) }
            val token = ChunkTokenEntity("a", 0, "word", "word", "NOUN", true, true, 1.0)
            assertFalse(r.profileTransfer.preview(original.copy(tokens = listOf(token))).canMerge)
            assertFalse(r.profileTransfer.preview(original.copy(packs = original.packs + original.packs)).canMerge)
            assertEquals(original, r.profileTransfer.snapshot())
            assertTrue(db.reviewDao().all().isEmpty())
        }
    }

    @Test fun `same deck identity with independent extra members blocks rather than silently unioning`() = runBlocking {
        fixture("a") { _, r ->
            val before = r.profileTransfer.snapshot()
            val incoming = before.copy(chunks = before.chunks + before.chunks.single().copy(id = "extra"),
                memberships = before.memberships + PackChunkEntity("a", "extra", 2))
            val preview = r.profileTransfer.preview(incoming)
            assertFalse(preview.canMerge)
            assertTrue(preview.conflicts.any { it.startsWith("deck manifest conflict") })
            expectRejected { r.profileTransfer.commit(incoming, preview) }
            assertEquals(before, r.profileTransfer.snapshot())
        }
    }

    @Test fun `both preview revisions are checked and receipt retry does not replay newer work`() = runBlocking {
        fixture("a") { a, ar -> fixture("b") { _, br ->
            val incoming = br.profileTransfer.snapshot()
            val stale = ar.profileTransfer.preview(incoming)
            ar.mergeJournal(listOf(answer("a", "new-local")))
            expectRejected { ar.profileTransfer.commit(incoming, stale) }
            val preview = ar.profileTransfer.preview(incoming)
            expectRejected { ar.profileTransfer.commit(incoming.copy(packs = incoming.packs.map { it.copy(title = "changed") }), preview) }
            ar.profileTransfer.commit(incoming, preview, operationId = "operation", operationDigest = DIGEST)
            val receipt = ar.profileTransfer.appliedOperation("operation")!!
            assertEquals(preview.localRevision, receipt.localRevision)
            assertEquals(DIGEST, receipt.operationDigest)
            ar.mergeJournal(listOf(answer("a", "newer-local").copy(ts = TS + 1)))
            val before = ar.profileTransfer.snapshot()
            assertEquals(0, ar.profileTransfer.commit(incoming, preview, operationId = "operation", operationDigest = DIGEST).replayed)
            assertEquals(before, ar.profileTransfer.snapshot())
            expectRejected { ar.profileTransfer.commit(incoming, preview, operationId = "operation", operationDigest = "b".repeat(64)) }
            assertEquals(2, a.reviewDao().all().size)
            val cutoff = ar.maxJournalId()
            a.historyBoundaryDao().put(HistoryBoundaryEntity(afterLocalId = cutoff, token = "reset"))
            expectRejected { ar.profileTransfer.commit(incoming, preview, cutoff, "reset", "operation", DIGEST) }
        } }
    }

    @Test fun `invalid journal references cannot partially install a deck or receipt`() = runBlocking {
        fixture("a") { a, ar -> fixture("b") { _, br ->
            val incoming = br.profileTransfer.snapshot().copy(reviews = listOf(answer("missing", "event")))
            val preview = ar.profileTransfer.preview(incoming)
            assertFalse(preview.canMerge)
            expectRejected { ar.profileTransfer.commit(incoming, preview, operationId = "bad", operationDigest = DIGEST) }
            assertNull(a.chunkDao().pack("b")); assertNull(ar.profileTransfer.appliedOperation("bad"))
        } }
    }

    @Test fun `cutoff stays local archived history never returns and changes invalidate preview`() = runBlocking {
        fixture("a") { db, r ->
            r.mergeJournal(listOf(answer("a", "archive")))
            val cutoff = r.maxJournalId()
            db.historyBoundaryDao().put(HistoryBoundaryEntity(afterLocalId = cutoff, token = "boundary"))
            val snapshot = r.profileTransfer.snapshot(cutoff, "boundary")
            assertTrue(snapshot.reviews.isEmpty()); assertTrue(snapshot.boundaryPresent)
            val preview = r.profileTransfer.preview(snapshot, cutoff, "boundary")
            db.historyBoundaryDao().put(HistoryBoundaryEntity(afterLocalId = cutoff, token = "changed"))
            expectRejected { r.profileTransfer.commit(snapshot, preview, cutoff, "boundary") }
            assertEquals(1, db.reviewDao().all().size)
        }
    }

    @Test fun `replay failure rolls back installed content history derived state and receipt`() = runBlocking {
        fixture("a") { db, original -> fixture("b") { _, incomingRepo ->
            val failingComponents = object : ComponentDao by db.componentDao() {
                override suspend fun clear() { throw IllegalStateException("injected replay failure") }
            }
            val failing = RestoreRepository(db, db.cardDao(), db.reviewDao(), db.statsDao(), db.planDao(),
                ComponentRepository(failingComponents, db.chunkDao(), db.reviewDao()), Scheduler(dayStartHour = 4), GovernorConfig())
            incomingRepo.mergeJournal(listOf(answer("b", "incoming-event")))
            val incoming = incomingRepo.profileTransfer.snapshot()
            val before = original.profileTransfer.snapshot()
            val preview = failing.profileTransfer.preview(incoming)
            assertTrue(preview.canMerge)
            try {
                failing.profileTransfer.commit(incoming, preview, operationId = "fault", operationDigest = DIGEST)
                fail("Expected injected failure")
            } catch (_: IllegalStateException) { }
            assertEquals(before, original.profileTransfer.snapshot())
            assertNull(original.profileTransfer.appliedOperation("fault"))
            assertTrue(db.cardDao().all().isEmpty())
            original.profileTransfer.commit(incoming, preview, operationId = "fault", operationDigest = DIGEST)
            assertEquals(1, db.reviewDao().all().size)
        } }
    }

    @Test fun `context primary references and morphology JSON are validated`() = runBlocking {
        fixture("a") { _, r ->
            val snapshot = r.profileTransfer.snapshot()
            val missing = snapshot.copy(memberships = listOf(PackChunkEntity("a", "a", 1, "missing", "meaning")))
            assertFalse(r.profileTransfer.preview(missing).canMerge)
            val invalid = snapshot.copy(contexts = listOf(ChunkContextEntity("a", "a", "context", "meaning",
                "fixture", "word", "meaning", 0, 4, 1, tokensJson = "not JSON")))
            assertFalse(r.profileTransfer.preview(invalid).canMerge)
            val badJournal = snapshot.copy(reviews = listOf(answer("a", "event").copy(rating = 9)))
            assertFalse(r.profileTransfer.preview(badJournal).canMerge)
        }
    }

    @Test fun `adoption donor adds unique content but retains only its own learning and receipt mode`() = runBlocking {
        fixture("a") { a, ar -> fixture("b") { _, br ->
            ar.mergeJournal(listOf(answer("a", "donor-history")))
            br.mergeJournal(listOf(answer("b", "receiver-history")))
            val incoming = br.profileTransfer.snapshot()
            val preview = ar.profileTransfer.previewAdoption(incoming)
            assertTrue(preview.canMerge); assertEquals(0, preview.addedReviews)
            ar.profileTransfer.commitAdoptionDonor(incoming, preview, 0, null, "adopt", DIGEST)
            assertEquals(listOf("donor-history"), a.reviewDao().all().map { it.eventId })
            assertNotNull(a.chunkDao().pack("b"))
            assertEquals("adopt-donor", ar.profileTransfer.appliedOperation("adopt")!!.mode)
            assertEquals(0, ar.profileTransfer.commitAdoptionDonor(incoming, preview, 0, null, "adopt", DIGEST).imported)
            expectRejected { ar.profileTransfer.commit(incoming, preview, operationId = "adopt", operationDigest = DIGEST) }
            val conflict = incoming.copy(chunks = incoming.chunks.map { it.copy(translation = "changed fixture") })
            val conflictingPreview = ar.profileTransfer.previewAdoption(conflict)
            assertFalse(conflictingPreview.canMerge)
            expectRejected { ar.profileTransfer.commitAdoptionDonor(conflict, conflictingPreview, 0, null, "conflict", DIGEST) }
            assertEquals(listOf("donor-history"), a.reviewDao().all().map { it.eventId })
        } }
    }

    @Test fun `automatic journal merge rejects unreviewed content edit`() = runBlocking {
        fixture("a") { db, r ->
            val approved = ProfileTransferCodec.contentIdentity(r.profileTransfer.snapshot())
            r.profileTransfer.mergeJournalForProfile(listOf(answer("a", "accepted")), approved)
            db.chunkDao().setPackTitle("a", "changed fixture")
            expectRejected { r.profileTransfer.mergeJournalForProfile(listOf(answer("a", "blocked")), approved) }
            assertEquals(listOf("accepted"), db.reviewDao().all().map { it.eventId })
        }
    }

    @Test fun `canonical snapshot encoding is order stable and bounded`() = runBlocking {
        fixture("a") { _, ar -> fixture("b") { _, br ->
            val incoming = br.profileTransfer.snapshot()
            ar.profileTransfer.commit(incoming, ar.profileTransfer.preview(incoming))
            val snapshot = ar.profileTransfer.snapshot()
            assertEquals(ProfileTransferCodec.revision(snapshot), ProfileTransferCodec.revision(snapshot.copy(
                packs = snapshot.packs.reversed(), chunks = snapshot.chunks.reversed())))
            assertEquals(snapshot, ProfileTransferCodec.decode(ProfileTransferCodec.encode(snapshot)))
            try { ProfileTransferCodec.encode(snapshot.copy(packs = snapshot.packs.map { it.copy(title = "x".repeat(ProfileTransferCodec.MAX_BYTES)) })); fail() }
            catch (_: IllegalArgumentException) { }
        } }
    }

    @Test fun `full wipe clears learner content receipts while immutable slot provenance survives`() = runBlocking {
        fixture("a") { db, r ->
            db.recordProfileOrigin("adopted-slot", DIGEST)
            val origin = db.profileOriginDao().current()!!
            db.recordProfileOrigin("adopted-slot", DIGEST)
            expectRejected { db.recordProfileOrigin("other-slot", DIGEST) }
            r.mergeJournal(listOf(answer("a", "history")))
            val snapshot = r.profileTransfer.snapshot()
            r.profileTransfer.commit(snapshot, r.profileTransfer.preview(snapshot), operationId = "receipt", operationDigest = DIGEST)
            assertNotNull(db.profileSyncOperationDao().find("receipt"))
            db.wipeAllData()
            assertEquals(origin, db.profileOriginDao().current())
            assertNull(db.profileSyncOperationDao().find("receipt"))
            assertTrue(db.reviewDao().all().isEmpty())
            assertTrue(db.cardDao().all().isEmpty())
            assertTrue(db.chunkDao().packs().isEmpty())
            assertTrue(db.chunkDao().allChunks().isEmpty())
            assertTrue(db.chunkDao().allMemberships().isEmpty())
            assertEquals(0L, db.historyBoundaryDao().current()!!.afterLocalId)
            expectRejected { db.recordProfileOrigin("replacement", DIGEST) }
        }
    }

    @Test fun `retained profile wipe validates origin closes database and preserves provenance after reopen`() = runBlocking {
        val home = Files.createTempDirectory("ikna-retained-wipe-").toFile()
        val file = home.resolve("retained.db")
        val origin = ProfileOriginEntity(opId = "slot", operationDigest = DIGEST)
        try {
            val db = openIknaDatabase(file)
            try {
                db.recordProfileOrigin(origin.opId, origin.operationDigest)
                db.chunkDao().upsertPack(PackEntity("pack", 1, "en", 0, TS))
            } finally { db.close() }
            expectRejected { wipeRetainedProfileDatabase(file, null, ::openIknaDatabase) }
            val unchanged = openIknaDatabase(file)
            try { assertEquals(1, unchanged.chunkDao().packs().size) } finally { unchanged.close() }
            wipeRetainedProfileDatabase(file, origin, ::openIknaDatabase)
            val reopened = openIknaDatabase(file)
            try {
                assertEquals(origin, reopened.profileOriginDao().current())
                assertTrue(reopened.chunkDao().packs().isEmpty())
                assertNotNull(reopened.historyBoundaryDao().current())
            } finally { reopened.close() }
            val absent = home.resolve("absent.db")
            expectRejected { wipeRetainedProfileDatabase(absent, null, ::openIknaDatabase) }
            assertFalse(absent.exists())
        } finally { home.deleteRecursively() }
    }

    @Test fun `version eleven migration preserves existing journal and durable receipt survives reopen`() = runBlocking {
        val home = Files.createTempDirectory("ikna-profile-migration-").toFile()
        val file = home.resolve("v11.db")
        val schemaFile = listOf(File("../app/schemas/dev.ikna.data.db.IknaDatabase/11.json"),
            File("app/schemas/dev.ikna.data.db.IknaDatabase/11.json")).first { it.exists() }
        val schema = Json.parseToJsonElement(schemaFile.readText()).jsonObject.getValue("database").jsonObject
        try {
            BundledSQLiteDriver().open(file.path).use { connection ->
                for (entity in schema.getValue("entities").jsonArray) {
                    val obj = entity.jsonObject
                    val name = obj.getValue("tableName").jsonPrimitive.content
                    connection.execSQL(obj.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", name))
                    for (index in obj["indices"]?.jsonArray.orEmpty()) {
                        connection.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", name))
                    }
                }
                connection.execSQL("INSERT INTO reviews(id,chunkId,level,ts,rating,elapsedDays,stabilityBefore,stabilityAfter,difficultyBefore,difficultyAfter,durationMs,wasAmnesty,eventId) VALUES(42,'target',0,$TS,3,2.5,1.5,3.5,5.0,4.5,123,0,'retained')")
                connection.execSQL("PRAGMA user_version = 11")
            }
            val migrated = openIknaDatabase(file)
            try {
                assertEquals("retained", migrated.reviewDao().all().single().eventId)
                assertNull(migrated.profileOriginDao().current())
                migrated.profileSyncOperationDao().insert(ProfileSyncOperationEntity("operation", DIGEST, "local", "incoming", "boundary", "result", "semantic", "content", "merge"))
            } finally { migrated.close() }
            val reopened = openIknaDatabase(file)
            try {
                assertEquals("result", reopened.profileSyncOperationDao().find("operation")!!.resultingRevision)
                assertEquals(42L, reopened.reviewDao().all().single().id)
                assertEquals(14, IKNA_DATABASE_VERSION)
            } finally { reopened.close() }
        } finally { home.deleteRecursively() }
    }

    private suspend fun expectRejected(block: suspend () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) { }
    }
    companion object { const val TS = 1577880000000L; val DIGEST = "a".repeat(64) }
}
