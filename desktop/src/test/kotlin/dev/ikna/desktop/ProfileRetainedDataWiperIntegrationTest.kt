package dev.ikna.desktop

import dev.ikna.data.db.*
import dev.ikna.data.export.ReviewRecord
import dev.ikna.data.repo.*
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.GovernorConfig
import dev.ikna.sync.ProfileRetainedDataWiper
import dev.ikna.sync.ProfileSlotStore
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Disposable Room fixtures, never user databases. */
class ProfileRetainedDataWiperIntegrationTest {
    private fun repo(db: IknaDatabase) = RestoreRepository(db, db.cardDao(), db.reviewDao(),
        db.statsDao(), db.planDao(), ComponentRepository(db.componentDao(), db.chunkDao(), db.reviewDao()),
        Scheduler(dayStartHour = 4), GovernorConfig())
    private suspend fun populate(db: IknaDatabase, id: String) {
        db.chunkDao().upsertPack(PackEntity(id, 1, "en", 1, 1000, title = id))
        db.chunkDao().insertChunksIgnore(listOf(ChunkEntity(id, id, "en", "word", "word", "meaning", 0, 4, 1)))
        db.chunkDao().upsertPackChunks(listOf(PackChunkEntity(id, id, 1)))
        repo(db).mergeJournal(listOf(ReviewRecord(chunkId = id, ts = 1_700_000_000_000L,
            rating = 3, eventId = "event-$id", durationMs = 100, prevIsNew = true)))
    }
    private suspend fun setup(home: File): Pair<ProfileSlotStore, File> {
        val legacy = File(home, "original.db")
        openIknaDatabase(legacy).let { db -> try { populate(db, "original") } finally { db.close() } }
        val slots = ProfileSlotStore(home, legacy)
        val candidate = slots.createCandidate()
        openIknaDatabase(candidate.database).let { db ->
            try { populate(db, "active"); db.recordProfileOrigin("adopt-test", DIGEST) } finally { db.close() }
        }
        slots.seal(candidate); slots.activate(candidate, legacy, "adopt-test")
        val orphan = slots.createCandidate()
        openIknaDatabase(orphan.database).let { db -> try { populate(db, "orphan") } finally { db.close() } }
        return slots to candidate.database
    }

    @Test fun `full wipe clears active retained original and orphan but preserves immutable origin`() = runBlocking {
        val home = Files.createTempDirectory("ikna-all-profile-wipe").toFile()
        try {
            val (slots, activeFile) = setup(home)
            val active = openIknaDatabase(activeFile)
            try {
                assertEquals(3, ProfileRetainedDataWiper(slots, activeFile, active, ::openIknaDatabase).wipeAllProfiles())
                assertEquals(activeFile, slots.selection().active)
                assertEquals(ProfileOriginEntity(opId = "adopt-test", operationDigest = DIGEST), active.profileOriginDao().current())
                for (file in slots.retainedDatabases()) {
                    val db = if (file == activeFile.canonicalFile) active else openIknaDatabase(file)
                    try {
                        assertTrue(db.reviewDao().all().isEmpty())
                        assertTrue(db.chunkDao().packs().isEmpty())
                        assertTrue(db.chunkDao().allChunks().isEmpty())
                    } finally { if (db !== active) db.close() }
                }
            } finally { active.close() }
        } finally { home.deleteRecursively() }
    }

    @Test fun `corrupt orphan rejects before any database is wiped`() = runBlocking {
        val home = Files.createTempDirectory("ikna-all-profile-wipe-corrupt").toFile()
        try {
            val (slots, activeFile) = setup(home)
            val corrupt = slots.createCandidate().database
            corrupt.writeText("corrupt interrupted staging")
            val active = openIknaDatabase(activeFile)
            try {
                var rejected = false
                try { ProfileRetainedDataWiper(slots, activeFile, active, ::openIknaDatabase).wipeAllProfiles() }
                catch (_: Exception) { rejected = true }
                assertTrue(rejected)
                assertEquals(1, active.reviewDao().all().size)
                for (file in slots.retainedDatabases().filter { it != corrupt.canonicalFile && it != activeFile.canonicalFile }) {
                    val db = openIknaDatabase(file)
                    try { assertEquals(1, db.reviewDao().all().size) } finally { db.close() }
                }
            } finally { active.close() }
        } finally { home.deleteRecursively() }
    }

    @Test fun `runtime failure after first retained wipe reports partial completion and keeps active data`() = runBlocking {
        val home = Files.createTempDirectory("ikna-all-profile-wipe-failure").toFile()
        try {
            val (slots, activeFile) = setup(home)
            val active = openIknaDatabase(activeFile)
            try {
                val passive = slots.retainedDatabases().filter { it != activeFile.canonicalFile }
                val failFile = passive.last()
                val opens = mutableMapOf<File, Int>()
                val wiper = ProfileRetainedDataWiper(slots, activeFile, active, openDatabase = { file ->
                    val count = opens.getOrDefault(file, 0) + 1
                    opens[file] = count
                    if (file == failFile && count == 2) error("synthetic-second-wipe-failure")
                    openIknaDatabase(file)
                })
                try { wiper.wipeAllProfiles(); fail("Expected explicit partial failure") }
                catch (failure: ProfileRetainedDataWiper.PartialFailure) { assertEquals(1, failure.wipedProfiles) }
                assertEquals(1, active.reviewDao().all().size)
                val first = openIknaDatabase(passive.first())
                try { assertTrue(first.reviewDao().all().isEmpty()) } finally { first.close() }
            } finally { active.close() }
        } finally { home.deleteRecursively() }
    }
    companion object { private val DIGEST = "a".repeat(64) }
}
