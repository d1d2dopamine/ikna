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
 * How the chosen palette is lit: dark, light, whatever the phone is doing, or
 * four colours of your own.
 *
 * SYSTEM is back, and it no longer means what it used to. It was removed when a
 * mode *was* the whole look of the app — following the phone then meant the app
 * had no identity of its own and changed character at sunset. The identity now
 * lives in the palette (see IknaPalettes), and the mode only says how that
 * palette is lit, so following the phone keeps the brand and changes the light.
 *
 * Removing an entry from a stored enum is the kind of change that crashes an
 * app on launch: installs from a previous version still have their own string
 * written in their preferences. The reader below resolves unknown names to the
 * default instead of throwing, which turns that upgrade into a theme switch
 * rather than a crash loop.
 */
enum class ThemeMode { DARK, LIGHT, SYSTEM, CUSTOM }

/**
 * The palette the app wears out of the box: "Уголь", the warm near-black of the
 * launcher icon with the ember of the mark on it.
 *
 * Lives here rather than in the theme package because a stored preference has to
 * have a default that the preferences layer can name without reaching up into the
 * interface. The colours themselves are in ui/theme/Theme.kt.
 */
const val DEFAULT_PALETTE_ID = "ember"

/**
 * The hand-set daily norm.
 *
 * The three named presets are gone. "Спокойно / обычно / плотно" asked the user
 * to translate a mood into a number the app had already chosen for them, and the
 * names lied in both directions: "плотно" is a decision about tomorrow's queue
 * dressed up as a personality trait, and none of the three said what it would
 * actually cost. Either the app measures the norm — the default — or the user
 * states it as a number. Nothing in between.
 */
const val MANUAL_LOAD_MIN = 10
const val MANUAL_LOAD_MAX = 120
const val MANUAL_LOAD_STEP = 5
const val MANUAL_LOAD_DEFAULT = 40

/**
 * Speech speed and pitch, as a percent of whatever the engine does on its own.
 *
 * Percent rather than the float the platform wants, because an integer is the
 * only form that reads back identically forever: a stored 1.0f that returns as
 * 0.99999f prints a different number on the screen every time it is shown, and
 * the same value also has to survive a settings backup unchanged.
 */
const val SPEECH_TONE_MIN = 50
const val SPEECH_TONE_MAX = 150
const val SPEECH_TONE_STEP = 10
const val SPEECH_TONE_DEFAULT = 100

/** Interface language: "system" follows the phone, or "ru" / "en" / "pl". */
const val LANGUAGE_SYSTEM = "system"

/**
 * Installs from before the presets were removed still have "CALM" written in
 * their preferences. The number behind the name is recovered here rather than
 * reset, because a norm that silently jumps from 25 to 40 on update is a load
 * increase nobody asked for.
 */
private fun legacyLoad(name: String?): Int? = when (name) {
    "CALM" -> 25
    "NORMAL" -> 40
    "DENSE" -> 60
    else -> null
}

/**
 * The default palette's dark values, duplicated here as the starting point for a
 * custom theme: whoever opens the hex fields starts from what they were already
 * looking at, not from a scheme the app no longer uses.
 */
private const val DEFAULT_CUSTOM_BACKGROUND = 0xFF17100C
private const val DEFAULT_CUSTOM_INK = 0xFFF2E6D9
private const val DEFAULT_CUSTOM_MUTED = 0xFF9C8574
private const val DEFAULT_CUSTOM_ACCENT = 0xFFF2683C

data class IknaSettings(
    val theme: ThemeMode = ThemeMode.DARK,
    /**
     * Which built-in palette the app wears. Unknown ids — a palette dropped in a
     * later version, or a hand-edited backup — resolve to the default instead of
     * failing, the same way an unknown [ThemeMode] does.
     */
    val paletteId: String = DEFAULT_PALETTE_ID,
    /**
     * The four colours of the custom theme, as ARGB integers. Everything else in
     * the scheme — panels, rules, disabled states — is mixed from these, so there
     * is no combination that produces an invisible control.
     */
    val customBackground: Int = DEFAULT_CUSTOM_BACKGROUND.toInt(),
    val customInk: Int = DEFAULT_CUSTOM_INK.toInt(),
    val customMuted: Int = DEFAULT_CUSTOM_MUTED.toInt(),
    val customAccent: Int = DEFAULT_CUSTOM_ACCENT.toInt(),
    /** The daily norm in answers. Used only when [autoLoad] is off. */
    val manualLoad: Int = MANUAL_LOAD_DEFAULT,
    /**
     * Interface language. Defaults to following the phone, so the app speaks the
     * right language before the user has been asked anything at all.
     */
    val language: String = LANGUAGE_SYSTEM,
    /**
     * When true the daily norm is measured from behaviour and [manualLoad] is
     * ignored.
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
    /**
     * Speech, through the engine already installed on the phone. **Off by
     * default, and marked beta in settings.**
     *
     * It used to be on, on the theory that a phone without an engine simply never
     * shows the mark. That was true and still wrong: what quality of voice the
     * phone has is unknown, a bad one reading a card aloud is worse than silence,
     * and a feature that speaks without being asked is not a default anyone chose.
     * Whoever wants it turns it on.
     */
    val speechEnabled: Boolean = false,
    /**
     * Chosen voice per language, stored as "pl=voice;ru=voice". A map is not a
     * preference type, and one string keeps every language's choice in a single
     * write — which matters, because a half-written pair is a voice that silently
     * falls back to the engine default.
     */
    val speechVoices: String = "",
    /**
     * Speed and pitch of the voice, in percent. 100 means untouched, so a phone
     * with a good engine sounds exactly as its own settings say until the user
     * decides otherwise here.
     */
    val speechRate: Int = SPEECH_TONE_DEFAULT,
    val speechPitch: Int = SPEECH_TONE_DEFAULT,
    /** File name of the installed content font. Empty means the built-in one. */
    val fontName: String = "",
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
        val paletteId = stringPreferencesKey("paletteId")
        val customBackground = intPreferencesKey("customBackground")
        val customInk = intPreferencesKey("customInk")
        val customMuted = intPreferencesKey("customMuted")
        val customAccent = intPreferencesKey("customAccent")
        val load = stringPreferencesKey("load")
        val manualLoad = intPreferencesKey("manualLoad")
        val language = stringPreferencesKey("language")
        val autoLoad = booleanPreferencesKey("autoLoad")
        val reminderEnabled = booleanPreferencesKey("reminderEnabled")
        val reminderHour = intPreferencesKey("reminderHour")
        val reminderMinute = intPreferencesKey("reminderMinute")
        val haptics = booleanPreferencesKey("haptics")
        val animations = booleanPreferencesKey("animations")
        val autoExport = booleanPreferencesKey("autoExport")
        val speechEnabled = booleanPreferencesKey("speechEnabled")
        val speechVoices = stringPreferencesKey("speechVoices")
        val speechRate = intPreferencesKey("speechRate")
        val speechPitch = intPreferencesKey("speechPitch")
        val fontName = stringPreferencesKey("fontName")
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
            // An id no build knows is resolved by the theme, not here: this layer
            // stores what it was given and the palette lookup falls back.
            paletteId = p[Keys.paletteId] ?: defaults.paletteId,
            customBackground = p[Keys.customBackground] ?: defaults.customBackground,
            customInk = p[Keys.customInk] ?: defaults.customInk,
            customMuted = p[Keys.customMuted] ?: defaults.customMuted,
            customAccent = p[Keys.customAccent] ?: defaults.customAccent,
            manualLoad = p[Keys.manualLoad] ?: legacyLoad(p[Keys.load]) ?: defaults.manualLoad,
            language = p[Keys.language] ?: defaults.language,
            autoLoad = p[Keys.autoLoad] ?: defaults.autoLoad,
            reminderEnabled = p[Keys.reminderEnabled] ?: defaults.reminderEnabled,
            reminderHour = p[Keys.reminderHour] ?: defaults.reminderHour,
            reminderMinute = p[Keys.reminderMinute] ?: defaults.reminderMinute,
            haptics = p[Keys.haptics] ?: defaults.haptics,
            animations = p[Keys.animations] ?: defaults.animations,
            autoExport = p[Keys.autoExport] ?: defaults.autoExport,
            speechEnabled = p[Keys.speechEnabled] ?: defaults.speechEnabled,
            speechVoices = p[Keys.speechVoices] ?: defaults.speechVoices,
            // Clamped on the way out as well as on the way in: a value written by
            // a restore from a hand-edited file must not be able to produce a
            // voice too fast to understand.
            speechRate = (p[Keys.speechRate] ?: defaults.speechRate)
                .coerceIn(SPEECH_TONE_MIN, SPEECH_TONE_MAX),
            speechPitch = (p[Keys.speechPitch] ?: defaults.speechPitch)
                .coerceIn(SPEECH_TONE_MIN, SPEECH_TONE_MAX),
            fontName = p[Keys.fontName] ?: defaults.fontName,
            onboardingDone = p[Keys.onboardingDone] ?: defaults.onboardingDone,
            revealHintsShown = p[Keys.revealHintsShown] ?: defaults.revealHintsShown,
            swipesDone = p[Keys.swipesDone] ?: defaults.swipesDone
        )
    }

    suspend fun current(): IknaSettings = flow.first()

    suspend fun setTheme(mode: ThemeMode) = put { it[Keys.theme] = mode.name }

    /**
     * Which palette to wear. Deliberately independent of [setTheme]: changing the
     * colours must not silently move a user off "follow the phone", and picking a
     * palette while on a custom scheme must not throw their four colours away.
     */
    suspend fun setPalette(id: String) = put { it[Keys.paletteId] = id }

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

    /** Naming the number by hand turns the measured norm off. */
    suspend fun setManualLoad(value: Int) = put {
        it[Keys.manualLoad] = value.coerceIn(MANUAL_LOAD_MIN, MANUAL_LOAD_MAX)
        it[Keys.autoLoad] = false
    }

    suspend fun setLanguage(code: String) = put { it[Keys.language] = code }

    suspend fun setAutoLoad(on: Boolean) = put { it[Keys.autoLoad] = on }
    suspend fun setHaptics(on: Boolean) = put { it[Keys.haptics] = on }
    suspend fun setAnimations(on: Boolean) = put { it[Keys.animations] = on }
    suspend fun setAutoExport(on: Boolean) = put { it[Keys.autoExport] = on }
    suspend fun setOnboardingDone(done: Boolean) = put { it[Keys.onboardingDone] = done }

    suspend fun setSpeechEnabled(on: Boolean) = put { it[Keys.speechEnabled] = on }

    /** Whole map at once. Used by a restore, which brings every language back. */
    suspend fun setSpeechVoices(value: String) = put { it[Keys.speechVoices] = value }

    /**
     * One language's voice, read-modify-write inside the same edit so choosing a
     * Polish voice cannot drop the Russian one picked a second earlier.
     */
    suspend fun setVoiceFor(lang: String, voiceName: String?) = put { prefs ->
        val map = parseVoiceMap(prefs[Keys.speechVoices] ?: "").toMutableMap()
        if (voiceName.isNullOrBlank()) map.remove(lang) else map[lang] = voiceName
        prefs[Keys.speechVoices] = encodeVoiceMap(map)
    }

    /**
     * Both at once, for the same reason the four colours are written together:
     * one write means the speaker can never be observed with tomorrow's speed and
     * yesterday's pitch.
     */
    suspend fun setSpeechTone(rate: Int, pitch: Int) = put {
        it[Keys.speechRate] = rate.coerceIn(SPEECH_TONE_MIN, SPEECH_TONE_MAX)
        it[Keys.speechPitch] = pitch.coerceIn(SPEECH_TONE_MIN, SPEECH_TONE_MAX)
    }

    suspend fun setFontName(name: String) = put { it[Keys.fontName] = name }

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

// ---- voice map -------------------------------------------------------------
//
// Deliberately hand-rolled rather than JSON: it is two or three pairs, it has to
// survive being read by an older build, and a parse failure here must never be
// able to take the settings screen down. Anything malformed is skipped and the
// engine default is used for that language.

fun parseVoiceMap(value: String): Map<String, String> =
    value.split(';')
        .asSequence()
        .mapNotNull { pair ->
            val at = pair.indexOf('=')
            if (at <= 0 || at == pair.lastIndex) return@mapNotNull null
            val lang = pair.substring(0, at).trim()
            val voice = pair.substring(at + 1).trim()
            if (lang.isEmpty() || voice.isEmpty()) null else lang to voice
        }
        .toMap()

fun encodeVoiceMap(map: Map<String, String>): String =
    map.entries
        .filter { it.key.isNotBlank() && it.value.isNotBlank() }
        .joinToString(";") { it.key + "=" + it.value }

/**
 * The voice chosen for a language, or null to let the engine decide.
 *
 * Matching is by the language part alone: a deck tagged "pl" and a voice stored
 * for "pl-PL" are the same choice to everyone except a string comparison.
 */
fun IknaSettings.voiceFor(lang: String): String? {
    if (lang.isBlank()) return null
    val map = parseVoiceMap(speechVoices)
    map[lang]?.let { return it }
    val head = lang.substringBefore('-').lowercase()
    return map.entries.firstOrNull { it.key.substringBefore('-').lowercase() == head }?.value
}
