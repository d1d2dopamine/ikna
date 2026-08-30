package dev.ikna.data.db

import androidx.room.RoomDatabase
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import kotlinx.coroutines.Dispatchers

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
