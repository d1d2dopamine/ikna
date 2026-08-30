package dev.ikna.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File

/**
 * The settings file, named exactly as DataStore's Android delegate named it.
 *
 * preferencesDataStore(name = "ikna-settings") wrote to
 * filesDir/datastore/ikna-settings.preferences_pb. Building the store by hand
 * has to land on that same path or every installed copy of the app silently
 * starts again with default settings after this update.
 */
const val SETTINGS_DATASTORE_FILE = "ikna-settings.preferences_pb"

fun createSettingsDataStore(file: File): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(produceFile = { file })
