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

/**
 * Dark, light, or four colours of your own.
 *
 * SYSTEM is gone. Following the phone meant the app had no look of its own and
 * changed under the user at sunset, and with a custom scheme in the list the
 * setting has to mean "this is what the app looks like", not "ask Android".
 *
 * Removing an entry from a stored enum is the kind of change that crashes an
 * app on launch: installs from the previous version still have the string
 * "SYSTEM" written in their preferences. The reader below resolves unknown names
 * to the default instead of throwing, which turns that upgrade into a theme
 * switch rather than a crash loop.
 */
enum class ThemeMode { DARK, LIGHT, CUSTOM }

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

/** Dark palette values, duplicated here as the starting point for a custom theme. */
private const val DEFAULT_CUSTOM_BACKGROUND = 0xFF121110
private const val DEFAULT_CUSTOM_INK = 0xFFEDE9E1
private const val DEFAULT_CUSTOM_MUTED = 0xFF8F887A
private const val DEFAULT_CUSTOM_ACCENT = 0xFF97A4D8

data class IknaSettings(
    val theme: ThemeMode = ThemeMode.DARK,
    /**
     * The four colours of the custom theme, as ARGB integers. Everything else in
     * the scheme — panels, rules, disabled states — is mixed from these, so there
     * is no combination that produces an invisible control.
     */
    val customBackground: Int = DEFAULT_CUSTOM_BACKGROUND.toInt(),
    val customInk: Int = DEFAULT_CUSTOM_INK.toInt(),
    val customMuted: Int = DEFAULT_CUSTOM_MUTED.toInt(),
    val customAccent: Int = DEFAULT_CUSTOM_ACCENT.toInt(),
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
    val revealHintsShown: Int = 0,
    /**
     * How many answers have been given by swiping rather than by pressing a
     * button. Once there are enough of them the two rare ratings drop off the
     * screen and stay on the gesture, which is where they belong.
     */
    val swipesDone: Int = 0
)

private val Context.iknaDataStore: DataStore<Preferences> by preferencesDataStore(name = "ikna-settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val customBackground = intPreferencesKey("customBackground")
        val customInk = intPreferencesKey("customInk")
        val customMuted = intPreferencesKey("customMuted")
        val customAccent = intPreferencesKey("customAccent")
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
        val swipesDone = intPreferencesKey("swipesDone")
    }

    val flow: Flow<IknaSettings> = context.iknaDataStore.data.map { p ->
        val defaults = IknaSettings()
        IknaSettings(
            // Unknown names — "SYSTEM" from an older install — fall back to the
            // default rather than throwing on the first frame after an update.
            theme = p[Keys.theme]?.let { name -> runCatching { ThemeMode.valueOf(name) }.getOrNull() }
                ?: defaults.theme,
            customBackground = p[Keys.customBackground] ?: defaults.customBackground,
            customInk = p[Keys.customInk] ?: defaults.customInk,
            customMuted = p[Keys.customMuted] ?: defaults.customMuted,
            customAccent = p[Keys.customAccent] ?: defaults.customAccent,
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
            revealHintsShown = p[Keys.revealHintsShown] ?: defaults.revealHintsShown,
            swipesDone = p[Keys.swipesDone] ?: defaults.swipesDone
        )
    }

    suspend fun current(): IknaSettings = flow.first()

    suspend fun setTheme(mode: ThemeMode) = put { it[Keys.theme] = mode.name }

    /**
     * All four at once, so the scheme can never be observed half-applied — a
     * background written before its ink is one frame of unreadable screen.
     */
    suspend fun setCustomColors(background: Int, ink: Int, muted: Int, accent: Int) = put {
        it[Keys.customBackground] = background
        it[Keys.customInk] = ink
        it[Keys.customMuted] = muted
        it[Keys.customAccent] = accent
    }

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

    suspend fun bumpSwipe() = put {
        it[Keys.swipesDone] = (it[Keys.swipesDone] ?: 0) + 1
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
