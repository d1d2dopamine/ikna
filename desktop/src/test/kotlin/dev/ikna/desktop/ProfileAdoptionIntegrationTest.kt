package dev.ikna.desktop

import dev.ikna.data.db.*
import dev.ikna.data.export.*
import dev.ikna.data.repo.*
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.GovernorConfig
import dev.ikna.sync.ProfileAdoptionService
import dev.ikna.sync.ProfileSlotStore
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProfileAdoptionIntegrationTest {
    private fun repo(db: IknaDatabase) = RestoreRepository(db, db.cardDao(), db.reviewDao(),
        db.statsDao(), db.planDao(), ComponentRepository(db.componentDao(), db.chunkDao(), db.reviewDao()),
        Scheduler(dayStartHour = 4), GovernorConfig())
    private suspend fun install(db: IknaDatabase, id: String) {
        db.chunkDao().upsertPack(PackEntity(id, 1, "en", 1, 1000, title = id))
        db.chunkDao().insertChunksIgnore(listOf(ChunkEntity(id, id, "en", "word", "word", "meaning", 0, 4, 1)))
        db.chunkDao().upsertPackChunks(listOf(PackChunkEntity(id, id, 1)))
    }
    private fun answer(id: String) = ReviewRecord(chunkId = id, ts = 1_700_000_000_000L,
        rating = 3, eventId = "event-$id", durationMs = 100, prevIsNew = true)

    @Test fun `adoption keeps original history and unique content with selected journal only`() = runBlocking {
        val home = Files.createTempDirectory("ikna-adoption-test-").toFile()
        val original = home.resolve("original.db")
        val live = openIknaDatabase(original)
        val donor = openIknaDatabase(home.resolve("donor.db"))
        try {
            install(live, "local"); install(donor, "source")
            val lr = repo(live); val dr = repo(donor)
            lr.mergeJournal(listOf(answer("local"))); dr.mergeJournal(listOf(answer("source")))
            val local = lr.profileTransfer.snapshot()
            val incoming = dr.profileTransfer.snapshot()
            val revision = lr.profileTransfer.preview(incoming).localRevision
            val slots = ProfileSlotStore(home, original)
            var quiesced = false
            val service = ProfileAdoptionService(slots, lr, ::openIknaDatabase, ::repo,
                withLiveWriteGuard = { it() }, quiesceAndCloseLive = { live.close(); quiesced = true },
                prepareActivation = { _, _, _ -> }, prepareRecovery = {})
            val resultingIdentity = service.adopt(incoming, revision, "adopt-test", DIGEST)
            assertTrue(quiesced)
            assertEquals(ProfileAdoptionService.State.RESTART_REQUIRED, service.state)
            val selected = slots.selection()
            assertEquals(original, selected.recovery)
            val adopted = openIknaDatabase(selected.active)
            try {
                val snapshot = repo(adopted).profileTransfer.snapshot()
                assertEquals(setOf("local", "source"), snapshot.packs.map { it.id }.toSet())
                assertTrue(snapshot.packs.single { it.id == "local" }.isActive)
                assertFalse(snapshot.packs.single { it.id == "source" }.isActive)
                assertEquals(incoming.reviews, snapshot.reviews)
                assertEquals(resultingIdentity, ProfileTransferCodec.profileIdentity(snapshot))
                assertEquals("adopt", adopted.profileSyncOperationDao().find("adopt-test")!!.mode)
            } finally { adopted.close() }
            val old = openIknaDatabase(original)
            try { assertEquals(local, repo(old).profileTransfer.snapshot()) } finally { old.close() }
        } finally { live.close(); donor.close(); home.deleteRecursively() }
    }

    @Test fun `write committed at quiescence aborts switch instead of stranding history`() = runBlocking {
        val home = Files.createTempDirectory("ikna-adoption-race-test-").toFile()
        val original = home.resolve("original.db")
        val live = openIknaDatabase(original)
        val donor = openIknaDatabase(home.resolve("donor.db"))
        try {
            install(live, "local"); install(donor, "source")
            val lr = repo(live)
            val incoming = repo(donor).profileTransfer.snapshot()
            val revision = lr.profileTransfer.preview(incoming).localRevision
            val slots = ProfileSlotStore(home, original)
            val service = ProfileAdoptionService(slots, lr, ::openIknaDatabase, ::repo,
                withLiveWriteGuard = { it() }, quiesceAndCloseLive = {
                    lr.mergeJournal(listOf(answer("local")))
                    live.close()
                }, prepareActivation = { _, _, _ -> }, prepareRecovery = {})
            try { service.adopt(incoming, revision, "adopt-race", DIGEST); fail("Expected stale profile") }
            catch (expected: IllegalArgumentException) { assertEquals("local-profile-stale", expected.message) }
            assertEquals(original, slots.selection().active)
            assertEquals(ProfileAdoptionService.State.RESTART_REQUIRED, service.state)
            val old = openIknaDatabase(original)
            try { assertEquals(1, old.reviewDao().all().size) } finally { old.close() }
        } finally { live.close(); donor.close(); home.deleteRecursively() }
    }
    @Test fun `activation preparation failure retains original selection and requires restart`() = runBlocking {
        val home = Files.createTempDirectory("ikna-adoption-failure-test-").toFile()
        val original = home.resolve("original.db")
        val live = openIknaDatabase(original)
        val donor = openIknaDatabase(home.resolve("donor.db"))
        try {
            install(live, "local"); install(donor, "source")
            val lr = repo(live)
            val incoming = repo(donor).profileTransfer.snapshot()
            val revision = lr.profileTransfer.preview(incoming).localRevision
            val slots = ProfileSlotStore(home, original)
            val service = ProfileAdoptionService(slots, lr, ::openIknaDatabase, ::repo,
                withLiveWriteGuard = { it() }, quiesceAndCloseLive = { live.close() },
                prepareActivation = { _, _, _ -> error("synthetic-persist-failure") }, prepareRecovery = {})
            try { service.adopt(incoming, revision, "adopt-failure", DIGEST); fail("Expected preparation failure") }
            catch (expected: IllegalStateException) { assertEquals("synthetic-persist-failure", expected.message) }
            assertEquals(original, slots.selection().active)
            assertEquals(ProfileAdoptionService.State.RESTART_REQUIRED, service.state)
            val old = openIknaDatabase(original)
            try { assertEquals(listOf("local"), old.chunkDao().packs().map { it.id }) } finally { old.close() }
        } finally { live.close(); donor.close(); home.deleteRecursively() }
    }
    companion object { private val DIGEST = "a".repeat(64) }
}
