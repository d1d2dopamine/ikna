package dev.ikna.desktop

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dev.ikna.data.db.*
import dev.ikna.sync.ProfileRetainedDataWiper
import dev.ikna.sync.ProfileSlotStore
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Genuine committed-schema12/13 fixtures; no user databases or destructive migration fallback. */
class ProfileSlotMigrationIntegrationTest {
    private val digest = "a".repeat(64)
    private val derived = listOf("journal_stream", "journal_event_index", "journal_checkpoint")
    private fun sql(file: File, block: (SQLiteConnection) -> Unit) =
        BundledSQLiteDriver().open(file.absolutePath).use(block)
    private fun version(file: File): Long {
        var result = -1L
        sql(file) { db -> db.prepare("PRAGMA user_version").use { check(it.step()); result = it.getLong(0) } }
        return result
    }
    private fun createLegacy(file: File, event: String, origin: String? = null, schemaVersion: Int = 12) {
        val schemaFile = listOf(File("../app/schemas/dev.ikna.data.db.IknaDatabase/$schemaVersion.json"),
            File("app/schemas/dev.ikna.data.db.IknaDatabase/$schemaVersion.json")).first { it.exists() }
        val schema = Json.parseToJsonElement(schemaFile.readText()).jsonObject.getValue("database").jsonObject
        sql(file) { db ->
            schema.getValue("entities").jsonArray.forEach { entity ->
                val obj = entity.jsonObject
                val name = obj.getValue("tableName").jsonPrimitive.content
                db.execSQL(obj.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", name))
                obj["indices"]?.jsonArray.orEmpty().forEach { index ->
                    db.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", name))
                }
            }
            schema["setupQueries"]?.jsonArray.orEmpty().forEach { db.execSQL(it.jsonPrimitive.content) }
            db.execSQL("INSERT INTO reviews(id,chunkId,level,ts,rating,elapsedDays,stabilityBefore,stabilityAfter,difficultyBefore,difficultyAfter,durationMs,wasAmnesty,eventId) VALUES(42,'target',0,1577880000000,3,2.5,1.5,3.5,5.0,4.5,123,0,'$event')")
            if (origin != null) db.execSQL("INSERT INTO profile_origin(id,opId,operationDigest) VALUES(1,'$origin','$digest')")
            db.execSQL("PRAGMA user_version = $schemaVersion")
        }
    }
    private fun seedDerived(file: File) = sql(file) { db ->
        db.execSQL("INSERT INTO journal_stream VALUES(1,'$digest','{}',42,1,'$digest',1,123)")
        db.execSQL("INSERT INTO journal_event_index VALUES('$digest',1,'fixture-event','$digest',NULL,42,'$digest')")
        db.execSQL("INSERT INTO journal_checkpoint VALUES('fixture-peer','{}',1,'$digest')")
        derived.forEach { table -> db.prepare("SELECT COUNT(*) FROM $table").use {
            assertTrue(it.step()); assertEquals(1L, it.getLong(0))
        } }
    }
    private data class Fixture(val root: File, val slots: ProfileSlotStore, val legacy: File, val active: File)
    private fun fixture(schemaVersion: Int = 12): Fixture {
        val root = Files.createTempDirectory("ikna-slot-schema12-").toFile()
        val legacy = File(root, "original.db").also { createLegacy(it, "legacy-event", schemaVersion = schemaVersion) }
        val slots = ProfileSlotStore(root, legacy)
        val candidate = slots.createCandidate()
        createLegacy(candidate.database, "adopted-event", "old-adoption", schemaVersion)
        // Reproduce a selector committed by schema12/13 application code, without sealing an
        // outdated candidate through the current app (which must reject it).
        sql(File(root, "original.db.profile-selector.sqlite")) { db ->
            db.prepare("INSERT INTO selections(active,recovery,operationId,operationDigest) VALUES(?,'legacy','old-adoption',?)").use {
                it.bindText(1, slots.slotId(candidate.database)); it.bindText(2, digest); it.step()
            }
        }
        return Fixture(root, slots, legacy, candidate.database)
    }

    @Test fun `schema12 and13 adopted startup validate origin before explicit Room migration to14`() = runBlocking {
        for (schemaVersion in listOf(12, 13)) {
            val f = fixture(schemaVersion)
            try {
                val selected = f.slots.selection()
                assertEquals(f.active, selected.active)
                assertEquals("old-adoption", selected.operationId)
                assertEquals(digest, selected.operationDigest)
                assertEquals(schemaVersion.toLong(), version(f.active))
                val migrated = openIknaDatabase(f.active)
                try {
                    val review = migrated.reviewDao().all().single()
                    assertEquals(42L, review.id); assertEquals("adopted-event", review.eventId)
                    assertEquals(3.5, review.stabilityAfter, 0.0)
                    assertNull(review.purpose)
                    assertEquals(ProfileOriginEntity(opId = "old-adoption", operationDigest = digest), migrated.profileOriginDao().current())
                } finally { migrated.close() }
                assertEquals(14L, version(f.active))
                assertEquals(selected, f.slots.selection())
                sql(f.active) { db -> derived.forEach { table ->
                    db.prepare("SELECT COUNT(*) FROM $table").use { assertTrue(it.step()) }
                } }
            } finally { f.root.deleteRecursively() }
        }
    }

    @Test fun `schema12 and13 retained recovery migrate explicitly and preserve original history`() = runBlocking {
        for (schemaVersion in listOf(12, 13)) {
            val f = fixture(schemaVersion)
            try {
                f.slots.verifyRecovery(f.legacy)
                assertEquals(schemaVersion.toLong(), version(f.legacy))
                val recovery = openIknaDatabase(f.legacy)
                try {
                    val review = recovery.reviewDao().all().single()
                    assertEquals("legacy-event", review.eventId)
                    assertNull(review.purpose)
                } finally { recovery.close() }
                assertEquals(14L, version(f.legacy))
                f.slots.recover(f.active, "explicit-recovery")
                assertEquals(f.legacy, f.slots.selection().active)
                assertEquals(f.active, f.slots.selection().recovery)
                assertEquals(schemaVersion.toLong(), version(f.active))
            } finally { f.root.deleteRecursively() }
        }
    }

    @Test fun `only known slot schemas accepted and candidate sealing requires current14`() {
        val f = fixture()
        try {
            for (schemaVersion in listOf(12, 13)) {
                val candidate = f.slots.createCandidate()
                createLegacy(candidate.database, "staging-event", "staging-op", schemaVersion)
                assertFails { f.slots.seal(candidate) }
            }
            for (unsupported in listOf(0, 11, 15, 999)) {
                sql(f.active) { it.execSQL("PRAGMA user_version = $unsupported") }
                assertFails { f.slots.selection() }
                assertFails { f.slots.verifyRecovery(f.active) }
            }
            sql(f.active) { it.execSQL("PRAGMA user_version = 12") }
            assertEquals(f.active, f.slots.selection().active)
            sql(f.active) { it.execSQL("UPDATE profile_origin SET operationDigest = '${"b".repeat(64)}'") }
            assertFails { f.slots.selection() }
        } finally { f.root.deleteRecursively() }
    }

    @Test fun `all profile wipe migrates12 and13 retained databases while retaining selected origin`() = runBlocking {
        val f = fixture()
        try {
            val orphan = f.slots.createCandidate().database
            createLegacy(orphan, "orphan-event", schemaVersion = 13)
            val currentOrphan = f.slots.createCandidate().database
            createLegacy(currentOrphan, "current-orphan-event")
            for (file in listOf(f.active, currentOrphan)) {
                val migration = openIknaDatabase(file)
                try { assertEquals(1, migration.reviewDao().all().size) } finally { migration.close() }
                seedDerived(file)
            }
            val active = openIknaDatabase(f.active)
            try {
                assertEquals(1, active.reviewDao().all().size)
                assertEquals(4, ProfileRetainedDataWiper(f.slots, f.active, active, ::openIknaDatabase).wipeAllProfiles())
                assertTrue(active.reviewDao().all().isEmpty())
                assertEquals(ProfileOriginEntity(opId = "old-adoption", operationDigest = digest), active.profileOriginDao().current())
            } finally { active.close() }
            for (file in listOf(f.legacy, f.active, orphan, currentOrphan)) {
                assertEquals(14L, version(file))
                sql(file) { db ->
                    (derived + "reviews").forEach { table ->
                        db.prepare("SELECT COUNT(*) FROM $table").use { assertTrue(it.step()); assertEquals(0L, it.getLong(0)) }
                    }
                }
            }
            assertEquals(f.active, f.slots.selection().active)
        } finally { f.root.deleteRecursively() }
    }

    @Test fun `unsupported orphan aborts before any learner history is wiped`() = runBlocking {
        val f = fixture()
        try {
            val orphan = f.slots.createCandidate().database
            createLegacy(orphan, "orphan-event")
            sql(orphan) { it.execSQL("PRAGMA user_version = 15") }
            val active = openIknaDatabase(f.active)
            try {
                assertEquals(1, active.reviewDao().all().size)
                var rejected = false
                try { ProfileRetainedDataWiper(f.slots, f.active, active, ::openIknaDatabase).wipeAllProfiles() }
                catch (_: Exception) { rejected = true }
                assertTrue(rejected)
                assertEquals(1, active.reviewDao().all().size)
                sql(f.legacy) { db -> db.prepare("SELECT COUNT(*) FROM reviews").use { assertTrue(it.step()); assertEquals(1L, it.getLong(0)) } }
            } finally { active.close() }
        } finally { f.root.deleteRecursively() }
    }
    private fun assertFails(block: () -> Unit) {
        var rejected = false
        try { block() } catch (_: Exception) { rejected = true }
        assertTrue("Expected rejected slot operation", rejected)
    }
}
