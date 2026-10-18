package dev.ikna.sync

import dev.ikna.data.db.IknaDatabase
import dev.ikna.data.db.ProfileOriginEntity
import dev.ikna.data.db.inTransaction
import dev.ikna.data.db.wipeAllData
import dev.ikna.data.db.wipeRetainedProfileDatabase
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

/** Caller owns all writer/adoption exclusion and must durably disable sync first.
 * Validation completes for ALL slots before the first wipe. Individual DB wipes are atomic;
 * a disk/runtime failure between databases can partially complete and is always reported. */
class ProfileRetainedDataWiper(
    private val slots: ProfileSlotStore,
    private val activeDatabaseFile: File,
    private val activeDatabase: IknaDatabase,
    private val openDatabase: (File) -> IknaDatabase,
) {
    class PartialFailure(val wipedProfiles: Int, cause: Throwable) :
        IllegalStateException("Full data wipe did not complete for all retained profiles", cause)

    suspend fun wipeAllProfiles(): Int {
        val caller = currentCoroutineContext()
        caller.ensureActive()
        val files = slots.retainedDatabases()
        val active = activeDatabaseFile.canonicalFile
        require(active in files && slots.selection().active.canonicalFile == active) { "active-profile-changed" }
        val origins = linkedMapOf<File, ProfileOriginEntity?>()
        for (file in files) {
            caller.ensureActive()
            slots.verifyRecovery(file)
            val database = if (file == active) activeDatabase else openDatabase(file)
            try {
                // First Room query explicitly migrates a supported schema12/13 retained/orphan DB
                // to current14 and validates it before any learner data is wiped.
                // Shared wipeAllData also clears journal_stream, journal_event_index
                // and journal_checkpoint; immutable profile_origin remains untouched.
                origins[file] = database.profileOriginDao().current()
                database.chunkDao().packs()
            } finally { if (file != active) database.close() }
        }
        // Detect staging/selection changes before any wipe. Caller continues to exclude writers.
        require(slots.retainedDatabases() == files && slots.selection().active.canonicalFile == active) { "profile-slots-changed" }
        var wiped = 0
        try {
            for (file in files.filter { it != active }) {
                caller.ensureActive()
                wipeRetainedProfileDatabase(file, origins[file], openDatabase)
                wiped++
            }
            caller.ensureActive()
            activeDatabase.inTransaction {
                require(activeDatabase.profileOriginDao().current() == origins[active]) { "active-profile-origin-changed" }
                activeDatabase.wipeAllData()
                caller.ensureActive()
            }
            return wiped + 1
        } catch (error: Throwable) {
            // Cancellation is also incomplete work; never turn it into a success indication.
            throw PartialFailure(wiped, error)
        }
    }
}
