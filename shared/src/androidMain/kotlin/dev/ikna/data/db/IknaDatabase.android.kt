package dev.ikna.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver

actual fun iknaSqliteDriver(): SQLiteDriver = AndroidSQLiteDriver()

/**
 * The same file the app has always used.
 *
 * The absolute path from getDatabasePath is passed rather than the bare name so
 * that the driver-based builder resolves to the very database an installed copy
 * already has, instead of creating a second one beside it.
 */
fun openIknaDatabase(context: Context): IknaDatabase =
    buildIknaDatabase(
        Room.databaseBuilder<IknaDatabase>(
            context = context.applicationContext,
            name = context.getDatabasePath("ikna.db").absolutePath
        )
    )
