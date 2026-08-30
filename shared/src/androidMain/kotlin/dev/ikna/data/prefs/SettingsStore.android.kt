package dev.ikna.data.prefs

import android.content.Context
import java.io.File

/**
 * SettingsStore(context), still.
 *
 * A function with the same name as the class, in the same package, so that every
 * caller in :app compiles unchanged -- Kotlin resolves the classifier and the
 * callable under one import.
 */
fun SettingsStore(context: Context): SettingsStore =
    SettingsStore(
        createSettingsDataStore(
            File(File(context.applicationContext.filesDir, "datastore"), SETTINGS_DATASTORE_FILE)
        )
    )
