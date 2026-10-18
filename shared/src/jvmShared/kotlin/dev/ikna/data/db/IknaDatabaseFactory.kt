package dev.ikna.data.db

import androidx.room.RoomDatabase
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

/** The driver each platform opens the file with. */
expect fun iknaSqliteDriver(): SQLiteDriver

/**
 * Everything both applications must agree about when opening the database.
 *
 * A driver is set on Android too, and that is not optional: without one Room
 * stays in compatibility mode and hands migrations the old SupportSQLiteDatabase
 * that Migrations.kt no longer accepts.
 */
fun buildIknaDatabase(builder: RoomDatabase.Builder<IknaDatabase>): IknaDatabase =
    builder
        .addMigrations(*IknaMigrations.ALL)
        // The search index is not a Room entity, so Room does not create it. A
        // fresh install has to end up in the same state as an upgrade, which is
        // what this callback is for; MIGRATION_3_4 does the same thing for a
        // database that already exists.
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(connection: SQLiteConnection) {
                ChunkFtsIndex.create(connection)
            }
        })
        // NOTE: fallbackToDestructiveMigration() is banned in this project.
        // It is the one line that silently deletes the user history on a schema
        // change. If a migration is missing the app must crash loudly in
        // development instead of losing data in production.
        .setDriver(iknaSqliteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

/**
 * One transaction, on either platform.
 *
 * Replaces androidx.room.withTransaction, which is an Android-only extension.
 */
suspend fun <R> IknaDatabase.inTransaction(block: suspend () -> R): R =
    useWriterConnection { transactor -> transactor.immediateTransaction { block() } }

/**
 * Clears learner/content/operational data in one transaction on both platforms.
 * Immutable profile_origin is deliberately retained: it identifies the physical slot,
 * never grants consent and cannot restore wiped progress.
 *
 * Room's generated Android database exposes clearAllTables(), but its KMP
 * desktop base type does not. This explicit DAO is therefore also the schema
 * checklist: every new entity needs an explicit wipe or preservation policy
 * in the source contract that compares the two sets.
 */
suspend fun IknaDatabase.wipeAllData() = inTransaction {
    val wipe = wipeDao()
    wipe.clearProfileSyncOperations()
    incrementalJournalDao().invalidate()
    wipe.clearHistoryBoundary()
    historyBoundaryDao().put(HistoryBoundaryEntity(afterLocalId = 0, token = java.util.UUID.randomUUID().toString()))
    wipe.clearBrowseExposures()
    wipe.clearReviews()
    wipe.clearCards()
    wipe.clearComponents()
    wipe.clearDailyStats()
    wipe.clearGovernorLog()
    wipe.clearDailyPlan()
    wipe.clearChunkContexts()
    wipe.clearPackChunks()
    wipe.clearChunkTokens()
    wipe.clearChunks()
    wipe.clearPacks()
}

/** Only the creator of a new adopted slot records provenance. Repeating the exact
 * operation is harmless; replacing it is forbidden, including after full wipe. */
suspend fun IknaDatabase.recordProfileOrigin(opId: String, operationDigest: String) = inTransaction {
    require(opId.matches(Regex("[A-Za-z0-9:_-]{1,128}")) && operationDigest.matches(Regex("[a-f0-9]{64}"))) {
        "Invalid profile origin"
    }
    val desired = ProfileOriginEntity(opId = opId, operationDigest = operationDigest)
    val current = profileOriginDao().current()
    require(current == null || current == desired) { "Profile origin cannot be replaced" }
    if (current == null) profileOriginDao().insert(desired)
}

/** Wipe an explicitly selected, CLOSED retained database with the same policy as
 * the live database. Caller owns slot validation and exclusion of other writers.
 * No file deletion, path discovery, profile switching or implicit database creation. */
suspend fun wipeRetainedProfileDatabase(
    file: File,
    expectedOrigin: ProfileOriginEntity?,
    openDatabase: (File) -> IknaDatabase
) {
    require(file.isFile) { "Retained profile database does not exist" }
    val caller = currentCoroutineContext()
    caller.ensureActive()
    val database = openDatabase(file)
    try {
        database.inTransaction {
            caller.ensureActive()
            require(database.profileOriginDao().current() == expectedOrigin) { "Retained profile origin mismatch" }
            database.wipeAllData()
            caller.ensureActive()
        }
    } finally { database.close() }
}
