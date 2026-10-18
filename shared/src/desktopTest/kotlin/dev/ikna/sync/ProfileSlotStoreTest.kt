package dev.ikna.sync

import androidx.sqlite.execSQL
import dev.ikna.data.db.IKNA_DATABASE_VERSION
import dev.ikna.data.db.iknaSqliteDriver
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull

/** Synthetic SQLite fixtures only; not real learner databases. */
class ProfileSlotStoreTest {
    private fun fixture(file: File, op: String = "original") {
        iknaSqliteDriver().open(file.absolutePath).use {
            it.execSQL("PRAGMA user_version = $IKNA_DATABASE_VERSION")
            it.execSQL("CREATE TABLE profile_origin(id INTEGER PRIMARY KEY, opId TEXT, operationDigest TEXT)")
            it.execSQL("INSERT INTO profile_origin VALUES (1, '$op', '${"a".repeat(64)}')")
            it.execSQL("CREATE TABLE profile_sync_operations(opId TEXT, operationDigest TEXT, mode TEXT)")
            it.execSQL("INSERT INTO profile_sync_operations VALUES ('$op', '${"a".repeat(64)}', 'adopt')")
        }
    }
    @Test fun `unpublished candidate never replaces original and recovery preserves both files`() {
        val root = Files.createTempDirectory("ikna-profile-slot-test").toFile()
        try {
            val original = File(root, "original.db").also { fixture(it) }
            val store = ProfileSlotStore(root, original)
            val candidate = store.createCandidate().also { fixture(it.database, "adopt-1") }
            assertEquals(original, store.selection().active)
            assertNull(store.selection().recovery)
            assertFails { store.activate(candidate, original, "adopt-1") }
            store.seal(candidate)
            store.activate(candidate, original, "adopt-1")
            assertEquals(candidate.database, store.selection().active)
            assertEquals(original, store.selection().recovery)
            store.recover(candidate.database, "recover-2")
            assertEquals(original, store.selection().active)
            assertEquals(candidate.database, store.selection().recovery)
            assertEquals("recover-2", store.selection().operationId)
            store.verifyRecovery(candidate.database)
        } finally { root.deleteRecursively() }
    }
    @Test fun `failure before selector commit leaves original active`() {
        val root = Files.createTempDirectory("ikna-profile-slot-test").toFile()
        try {
            val original = File(root, "original.db").also { fixture(it) }
            val store = ProfileSlotStore(root, original) { if (it == ProfileSlotStore.Phase.BEFORE_SELECTION_COMMIT) error("synthetic-before-commit") }
            val candidate = store.createCandidate().also { fixture(it.database, "op-1") }
            store.seal(candidate)
            assertFails { store.activate(candidate, original, "op-1") }
            assertEquals(original, store.selection().active)
            assertNull(store.selection().recovery)
            store.verifyRecovery(original)
        } finally { root.deleteRecursively() }
    }
    @Test fun `interrupted staging never becomes active and stale activation is rejected`() {
        val root = Files.createTempDirectory("ikna-profile-slot-test").toFile()
        try {
            val original = File(root, "original.db").also { fixture(it) }
            val store = ProfileSlotStore(root, original)
            val first = store.createCandidate().also { fixture(it.database, "op-1") }
            assertEquals(original, ProfileSlotStore(root, original).selection().active)
            val second = store.createCandidate().also { fixture(it.database, "op-2") }
            store.seal(second); store.activate(second, original, "op-2")
            store.seal(first)
            assertFails { store.activate(first, original, "op-1") }
            assertEquals(second.database, store.selection().active)
        } finally { root.deleteRecursively() }
    }
    @Test fun `post commit failure still leaves a recoverable verified selection`() {
        val root = Files.createTempDirectory("ikna-profile-slot-test").toFile()
        try {
            val original = File(root, "original.db").also { fixture(it) }
            val store = ProfileSlotStore(root, original) { if (it == ProfileSlotStore.Phase.AFTER_SELECTION_COMMIT) error("synthetic-after-commit") }
            val candidate = store.createCandidate().also { fixture(it.database, "op-pending") }
            store.seal(candidate)
            assertFails { store.activate(candidate, original, "op-pending") }
            val startup = ProfileSlotStore(root, original).selection()
            assertEquals(candidate.database, startup.active)
            assertEquals(original, startup.recovery)
            assertEquals("op-pending", startup.operationId)
        } finally { root.deleteRecursively() }
    }
    @Test fun `transferred selector validates adoption receipt without private sync configuration`() {
        val originalHome = Files.createTempDirectory("ikna-profile-transfer-original").toFile()
        val transferredHome = Files.createTempDirectory("ikna-profile-transfer-new").toFile()
        try {
            val original = File(originalHome, "original.db").also { fixture(it) }
            val store = ProfileSlotStore(originalHome, original)
            val candidate = store.createCandidate().also { fixture(it.database, "transfer-op") }
            store.seal(candidate); store.activate(candidate, original, "transfer-op")
            // All synthetic fixture connections are closed before this simulated device transfer.
            originalHome.listFiles().orEmpty().filter { it.isFile }.forEach { it.copyTo(File(transferredHome, it.name)) }
            val transferred = ProfileSlotStore(transferredHome, File(transferredHome, "original.db"))
            val selected = transferred.selection()
            assertEquals("transfer-op", selected.operationId)
            iknaSqliteDriver().open(selected.active.absolutePath).use {
                it.execSQL("UPDATE profile_origin SET operationDigest = '${"b".repeat(64)}'")
            }
            assertFails { transferred.selection() }
        } finally { originalHome.deleteRecursively(); transferredHome.deleteRecursively() }
    }

    @Test fun `slot origin survives operational receipt clearing and enumeration includes orphan staging`() {
        val root = Files.createTempDirectory("ikna-profile-retained-test").toFile()
        try {
            val original = File(root, "original.db").also { fixture(it) }
            val store = ProfileSlotStore(root, original)
            val selected = store.createCandidate().also { fixture(it.database, "op-1") }
            store.seal(selected); store.activate(selected, original, "op-1")
            val orphan = store.createCandidate().also { fixture(it.database, "orphan") }
            File(root, "unrelated.db").writeText("outside scope")
            iknaSqliteDriver().open(selected.database.absolutePath).use { it.execSQL("DELETE FROM profile_sync_operations") }
            assertEquals(selected.database, store.selection().active)
            assertEquals(setOf(original.canonicalFile, selected.database.canonicalFile, orphan.database.canonicalFile),
                store.retainedDatabases().toSet())
        } finally { root.deleteRecursively() }
    }

    @Test fun `corrupt selector and recovery fail closed without replacing active database`() {
        val root = Files.createTempDirectory("ikna-profile-slot-test").toFile()
        try {
            val original = File(root, "original.db").also { fixture(it) }
            val store = ProfileSlotStore(root, original)
            val candidate = store.createCandidate().also { fixture(it.database, "op-1") }
            store.seal(candidate); store.activate(candidate, original, "op-1")
            original.writeText("synthetic-corruption")
            assertFails { store.recover(candidate.database, "recover-2") }
            assertEquals(candidate.database, store.selection().active)
            File(root, "original.db.profile-selector.sqlite").writeText("synthetic-corruption")
            assertFails { store.selection() }
            File(root, "original.db.profile-selector.sqlite").delete()
            assertFails { store.selection() }
        } finally { root.deleteRecursively() }
    }
}
