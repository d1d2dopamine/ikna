package dev.ikna.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, DARK, LIGHT }

/**
 * The only load control exposed to the user.
 *
 * One switch with three positions instead of the twenty numbers in
 * `assets/governor.json`. The numbers stay in the asset on purpose: tuning a
 * scheduler is far more entertaining than using one, and an app that invites
 * tuning gets tuned instead of studied.
 */
enum class LoadPreset(val dailyReviews: Int) {
    CALM(25),
    NORMAL(40),
    DENSE(60)
}

data class IknaSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val load: LoadPreset = LoadPreset.NORMAL,
    /**
     * When true the daily norm is measured from behaviour and [load] is ignored.
     * On by default: the app should work out the right size of a day by itself.
     */
    val autoLoad: Boolean = true,
    /** On by default. One notification a day, and only when the minimum is unmet. */
    val reminderEnabled: Boolean = true,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val haptics: Boolean = true,
    val animations: Boolean = true,
    val autoExport: Boolean = true,
    val onboardingDone: Boolean = false,
    /** How many times the tap-to-reveal hint has been shown. Stops at 5. */
    val revealHintsShown: Int = 0
)

private val Context.iknaDataStore: DataStore<Preferences> by preferencesDataStore(name = "ikna-settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamicColor")
        val load = stringPreferencesKey("load")
        val autoLoad = booleanPreferencesKey("autoLoad")
        val reminderEnabled = booleanPreferencesKey("reminderEnabled")
        val reminderHour = intPreferencesKey("reminderHour")
        val reminderMinute = intPreferencesKey("reminderMinute")
        val haptics = booleanPreferencesKey("haptics")
        val animations = booleanPreferencesKey("animations")
        val autoExport = booleanPreferencesKey("autoExport")
        val onboardingDone = booleanPreferencesKey("onboardingDone")
        val revealHintsShown = intPreferencesKey("revealHintsShown")
    }

    val flow: Flow<IknaSettings> = context.iknaDataStore.data.map { p ->
        val defaults = IknaSettings()
        IknaSettings(
            theme = p[Keys.theme]?.let { name -> runCatching { ThemeMode.valueOf(name) }.getOrNull() }
                ?: defaults.theme,
            dynamicColor = p[Keys.dynamicColor] ?: defaults.dynamicColor,
            load = p[Keys.load]?.let { name -> runCatching { LoadPreset.valueOf(name) }.getOrNull() }
                ?: defaults.load,
            autoLoad = p[Keys.autoLoad] ?: defaults.autoLoad,
            reminderEnabled = p[Keys.reminderEnabled] ?: defaults.reminderEnabled,
            reminderHour = p[Keys.reminderHour] ?: defaults.reminderHour,
            reminderMinute = p[Keys.reminderMinute] ?: defaults.reminderMinute,
            haptics = p[Keys.haptics] ?: defaults.haptics,
            animations = p[Keys.animations] ?: defaults.animations,
            autoExport = p[Keys.autoExport] ?: defaults.autoExport,
            onboardingDone = p[Keys.onboardingDone] ?: defaults.onboardingDone,
            revealHintsShown = p[Keys.revealHintsShown] ?: defaults.revealHintsShown
        )
    }

    suspend fun current(): IknaSettings = flow.first()

    suspend fun setTheme(mode: ThemeMode) = put { it[Keys.theme] = mode.name }
    suspend fun setDynamicColor(on: Boolean) = put { it[Keys.dynamicColor] = on }
    /** Picking a preset by hand turns the measured norm off. */
    suspend fun setLoad(preset: LoadPreset) = put {
        it[Keys.load] = preset.name
        it[Keys.autoLoad] = false
    }

    suspend fun setAutoLoad(on: Boolean) = put { it[Keys.autoLoad] = on }
    suspend fun setHaptics(on: Boolean) = put { it[Keys.haptics] = on }
    suspend fun setAnimations(on: Boolean) = put { it[Keys.animations] = on }
    suspend fun setAutoExport(on: Boolean) = put { it[Keys.autoExport] = on }
    suspend fun setOnboardingDone(done: Boolean) = put { it[Keys.onboardingDone] = done }

    suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int) = put {
        it[Keys.reminderEnabled] = enabled
        it[Keys.reminderHour] = hour.coerceIn(0, 23)
        it[Keys.reminderMinute] = minute.coerceIn(0, 59)
    }

    suspend fun bumpRevealHint() = put {
        it[Keys.revealHintsShown] = (it[Keys.revealHintsShown] ?: 0) + 1
    }

    /**
     * Back to first-run defaults, including the onboarding flag.
     *
     * Used by "стереть всё" in settings: a wipe that leaves the app thinking
     * it is already set up is not a wipe, it is a bug factory.
     */
    suspend fun clearAll() = put { it.clear() }

    private suspend fun put(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.iknaDataStore.edit { block(it) }
    }
}
