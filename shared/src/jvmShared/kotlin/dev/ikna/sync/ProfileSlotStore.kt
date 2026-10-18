package dev.ikna.sync

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import dev.ikna.data.db.IKNA_DATABASE_VERSION
import dev.ikna.data.db.iknaSqliteDriver
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.nio.file.Files
import java.util.UUID

/** SQLite owns the durable startup selector. Learner DBs are never copied or deleted.
 * [directory] must be an established application-private directory, not a newly made slot folder.
 * SQLite DELETE/EXTRA durability still depends on the platform/filesystem honoring sync requests. */
class ProfileSlotStore(
    private val directory: File,
    private val legacyDatabase: File,
    private val faultInjector: (Phase) -> Unit = {},
) {
    enum class Phase { BEFORE_SELECTION_COMMIT, AFTER_SELECTION_COMMIT }
    data class Selection(val active: File, val recovery: File?, val operationId: String?, val operationDigest: String? = null)
    class Candidate internal constructor(val database: File) {
        internal var sealed: Boolean = false
        internal var operationId: String? = null
        internal var operationDigest: String? = null
    }
    private val selector get() = File(directory, "${legacyDatabase.name}.profile-selector.sqlite")

    @Synchronized fun selection(): Selection {
        require(directory.isDirectory) { "profile-directory-not-established" }
        if (!selector.exists()) {
            var entries = 0
            Files.newDirectoryStream(directory.toPath()).use { paths ->
                paths.forEach { path ->
                    require(++entries <= MAX_DIRECTORY_ENTRIES) { "profile-directory-limit" }
                    val name = path.fileName.toString()
                    require(!(name.endsWith(".db") && name.removeSuffix(".db").matches(SLOT))) { "missing-profile-selector" }
                }
            }
            return Selection(legacyDatabase, null, null)
        }
        return openSelector().use { readSelection(it) }
    }

    @Synchronized fun createCandidate(): Candidate {
        require(directory.isDirectory) { "profile-directory-not-established" }
        // Commit the selector schema before allocating any candidate. Never silently repair it.
        if (!selector.exists()) { selection(); initializeSelector() }
        openSelector().use { readSelection(it) }
        require(retainedDatabases().size < MAX_PROFILE_DATABASES) { "profile-slot-limit" }
        return Candidate(File(directory, "${UUID.randomUUID()}.db").also { check(!it.exists()) })
    }

    /** Only after all Room handles are CLOSED. Checkpoint, then commit the receipt with SQLite's
     * strongest rollback-journal sync mode; never assume Room's WAL NORMAL is durable enough. */
    fun seal(candidate: Candidate) {
        candidate.sealed = false
        require(candidate.database.isFile && candidate.database.length() > 0) { "empty-profile-candidate" }
        iknaSqliteDriver().open(candidate.database.absolutePath).use { db ->
            db.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use { row ->
                require(row.step() && row.getLong(0) == 0L) { "profile-checkpoint-busy" }
            }
            configureDurability(db)
            checkIntegrity(db)
            require(number(db, "PRAGMA user_version") == IKNA_DATABASE_VERSION.toLong()) { "profile-schema-mismatch" }
            db.execSQL("BEGIN IMMEDIATE")
            try {
                db.prepare("SELECT opId, operationDigest FROM profile_origin WHERE id = 1").use { row ->
                    require(row.step()) { "missing-profile-origin" }
                    candidate.operationId = row.getText(0)
                    candidate.operationDigest = row.getText(1)
                    require(candidate.operationId!!.matches(ID) && candidate.operationDigest!!.matches(HEX) && !row.step()) {
                        "invalid-profile-origin"
                    }
                }
                // This touches only a receipt in the NEW candidate, never review/history rows.
                db.execSQL("UPDATE profile_sync_operations SET operationDigest = operationDigest WHERE mode = 'adopt'")
                db.execSQL("COMMIT")
            } catch (error: Throwable) { runCatching { db.execSQL("ROLLBACK") }; throw error }
            checkIntegrity(db)
        }
        FileChannel.open(candidate.database.toPath(), StandardOpenOption.WRITE).use { it.force(true) }
        candidate.sealed = true
    }

    /** Caller has closed the original and durably saved the pending operation in sync config. */
    @Synchronized fun activate(candidate: Candidate, expectedActive: File, operationId: String) {
        require(candidate.sealed && candidate.database.isFile && candidate.operationId == operationId && candidate.operationDigest != null) {
            "unsealed-profile-candidate"
        }
        select(candidate.database, expectedActive, operationId, candidate.operationDigest!!)
    }

    /** Explicit recovery only; caller has verified Room schema and durably revoked pairing. */
    @Synchronized fun recover(expectedActive: File, operationId: String) {
        val current = selection()
        require(current.active.canonicalFile == expectedActive.canonicalFile) { "profile-slot-changed" }
        val recovery = checkNotNull(current.recovery) { "no-recovery-profile" }
        verifyRecovery(recovery)
        select(recovery, expectedActive, operationId,
            dev.ikna.data.export.ProfileTransferCodec.digest("recovery\n$operationId\n${slotId(recovery)}".toByteArray(Charsets.UTF_8)))
    }

    /** Read-only preflight accepts only explicitly migratable slot schemas. The supplied Room
     * opener performs the non-destructive migration before recovery or wipe uses the database. */
    fun verifyRecovery(database: File) {
        require(database.isFile && database.length() > 0) { "missing-recovery-profile" }
        iknaSqliteDriver().open(database.absolutePath).use { db ->
            checkIntegrity(db)
            require(number(db, "PRAGMA user_version") in READABLE_SLOT_SCHEMA_VERSIONS) { "profile-schema-mismatch" }
        }
    }

    /** All existing app-owned learner slots, including interrupted staging. Never follows
     * symlinks or guesses names. Caller must validate contents before destructive operations. */
    @Synchronized fun retainedDatabases(): List<File> {
        selection() // A corrupt or missing selector is never silently ignored by full wipe.
        val files = linkedSetOf<File>()
        if (legacyDatabase.exists()) {
            require(legacyDatabase.isFile && !Files.isSymbolicLink(legacyDatabase.toPath())) { "invalid-retained-profile" }
            files += legacyDatabase.canonicalFile
        }
        var entries = 0
        Files.newDirectoryStream(directory.toPath()).use { paths ->
            paths.forEach { path ->
                require(++entries <= MAX_DIRECTORY_ENTRIES) { "profile-directory-limit" }
                val name = path.fileName.toString()
                if (name.endsWith(".db") && name.removeSuffix(".db").matches(SLOT)) {
                    require(!Files.isSymbolicLink(path) && Files.isRegularFile(path) && Files.isReadable(path)) { "invalid-retained-profile" }
                    files += path.toFile().canonicalFile
                    require(files.size <= MAX_PROFILE_DATABASES) { "profile-slot-limit" }
                } else {
                    // A stranded WAL can contain history even if its main file was removed.
                    val main = name.removeSuffix("-wal").removeSuffix("-shm").removeSuffix("-journal")
                    if (main != name && main.endsWith(".db") && main.removeSuffix(".db").matches(SLOT)) {
                        require(File(directory, main).isFile) { "missing-profile-slot" }
                    }
                }
            }
        }
        require(files.isNotEmpty()) { "missing-profile-slot" }
        return files.sortedBy { it.absolutePath }
    }

    fun slotId(file: File): String = if (file.canonicalFile == legacyDatabase.canonicalFile) "legacy"
        else file.name.removeSuffix(".db").also {
            require(it.matches(SLOT) && file.name == "$it.db" && file.parentFile.canonicalFile == directory.canonicalFile) {
                "invalid-profile-slot"
            }
        }
    private fun resolve(key: String): File {
        if (key == "legacy") return legacyDatabase
        require(key.matches(SLOT)) { "invalid-profile-slot" }
        return File(directory, "$key.db")
    }
    private fun select(active: File, expectedActive: File, operationId: String, digest: String) {
        require(operationId.matches(ID) && digest.matches(HEX)) { "invalid-profile-operation" }
        openSelector().use { db ->
            db.execSQL("BEGIN IMMEDIATE")
            try {
                require(readSelection(db).active.canonicalFile == expectedActive.canonicalFile) { "profile-slot-changed" }
                db.prepare("INSERT INTO selections(active, recovery, operationId, operationDigest) VALUES (?, ?, ?, ?)").use {
                    it.bindText(1, slotId(active)); it.bindText(2, slotId(expectedActive))
                    it.bindText(3, operationId); it.bindText(4, digest); it.step()
                }
                faultInjector(Phase.BEFORE_SELECTION_COMMIT)
                db.execSQL("COMMIT")
            } catch (error: Throwable) { runCatching { db.execSQL("ROLLBACK") }; throw error }
            // A failure here is ambiguous to the caller: startup must reconcile the pending slot.
            faultInjector(Phase.AFTER_SELECTION_COMMIT)
        }
    }
    private fun initializeSelector() {
        iknaSqliteDriver().open(selector.absolutePath).use { db ->
            configureDurability(db)
            db.execSQL("BEGIN IMMEDIATE")
            try {
                db.execSQL("CREATE TABLE selections(sequence INTEGER PRIMARY KEY AUTOINCREMENT, active TEXT NOT NULL, recovery TEXT NOT NULL, operationId TEXT NOT NULL, operationDigest TEXT NOT NULL)")
                db.execSQL("PRAGMA user_version = 1")
                db.execSQL("COMMIT")
            } catch (error: Throwable) { runCatching { db.execSQL("ROLLBACK") }; throw error }
        }
    }
    private fun openSelector(): SQLiteConnection {
        require(selector.isFile) { "missing-profile-selector" }
        val db = iknaSqliteDriver().open(selector.absolutePath)
        try {
            configureDurability(db)
            require(number(db, "PRAGMA user_version") == 1L) { "invalid-profile-selector" }
            checkIntegrity(db)
            return db
        } catch (error: Throwable) { db.close(); throw error }
    }
    private fun readSelection(db: SQLiteConnection): Selection =
        db.prepare("SELECT active, recovery, operationId, operationDigest FROM selections ORDER BY sequence DESC LIMIT 1").use { row ->
            if (!row.step()) {
                require(legacyDatabase.isFile) { "missing-profile-slot" }
                return@use Selection(legacyDatabase, null, null)
            }
            val active = resolve(row.getText(0)); val recovery = resolve(row.getText(1))
            val operation = row.getText(2)
            require(active.isFile && recovery.isFile && active.canonicalFile != recovery.canonicalFile &&
                operation.matches(ID) && row.getText(3).matches(HEX)) { "invalid-profile-selection" }
            validateSelectedDatabase(active, operation, row.getText(3))
            Selection(active, recovery, operation, row.getText(3))
        }
    /** Portable selector provenance remains verifiable when device-private sync config is absent.
     * Do not compare current history/content/boundary: legitimate learning and resets change them.
     * No journal-mode changes on this handle; the selected learner DB may already be open. */
    private fun validateSelectedDatabase(active: File, operationId: String, digest: String) {
        iknaSqliteDriver().open(active.absolutePath).use { db ->
            db.execSQL("PRAGMA query_only = ON")
            checkIntegrity(db)
            require(number(db, "PRAGMA user_version") in READABLE_SLOT_SCHEMA_VERSIONS) { "profile-schema-mismatch" }
            val recoveryDigest = dev.ikna.data.export.ProfileTransferCodec.digest(
                "recovery\n$operationId\n${slotId(active)}".toByteArray(Charsets.UTF_8))
            if (digest != recoveryDigest) {
                db.prepare("SELECT opId, operationDigest FROM profile_origin WHERE id = 1").use { origin ->
                    require(origin.step() && origin.getText(0) == operationId && origin.getText(1) == digest && !origin.step()) {
                        "profile-selection-origin-mismatch"
                    }
                }
            }
        }
    }
    private fun configureDurability(db: SQLiteConnection) {
        db.prepare("PRAGMA journal_mode = DELETE").use { require(it.step() && it.getText(0).equals("delete", true)) { "profile-journal-mode" } }
        db.execSQL("PRAGMA synchronous = EXTRA")
        require(number(db, "PRAGMA synchronous") == 3L) { "profile-durability-unavailable" }
    }
    private fun checkIntegrity(db: SQLiteConnection) {
        db.prepare("PRAGMA quick_check").use { require(it.step() && it.getText(0) == "ok" && !it.step()) { "profile-integrity-failed" } }
    }
    private fun number(db: SQLiteConnection, sql: String): Long = db.prepare(sql).use { check(it.step()); it.getLong(0) }
    companion object {
        // Explicit allowlist: a future Room version must separately review slot compatibility.
        private val READABLE_SLOT_SCHEMA_VERSIONS = setOf(12L, 13L, 14L)
        const val MAX_PROFILE_DATABASES = 128
        private const val MAX_DIRECTORY_ENTRIES = 4096
        private val SLOT = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
        private val ID = Regex("[A-Za-z0-9:_-]{1,128}")
        private val HEX = Regex("[a-f0-9]{64}")
    }
}
