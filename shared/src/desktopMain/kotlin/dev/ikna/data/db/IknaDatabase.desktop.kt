package dev.ikna.data.db

import androidx.room.Room
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

/**
 * SQLite compiled into the application rather than borrowed from the system.
 *
 * Windows has no SQLite to borrow, and the bundled build is compiled with
 * FTS3/FTS4 enabled, which is what the deck search in ChunkFtsIndex needs.
 */
actual fun iknaSqliteDriver(): SQLiteDriver = BundledSQLiteDriver()

fun openIknaDatabase(file: File): IknaDatabase {
    file.parentFile?.mkdirs()
    return buildIknaDatabase(Room.databaseBuilder<IknaDatabase>(name = file.absolutePath))
}
