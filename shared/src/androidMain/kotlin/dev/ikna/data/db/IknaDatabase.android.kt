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
 * The default keeps the historical real profile path. Developer Sandbox passes
 * a different file name before the dependency graph is constructed.
 */
fun openIknaDatabase(context: Context, fileName: String = "ikna.db"): IknaDatabase =
    buildIknaDatabase(
        Room.databaseBuilder<IknaDatabase>(
            context = context.applicationContext,
            name = context.getDatabasePath(fileName).absolutePath
        )
    )
