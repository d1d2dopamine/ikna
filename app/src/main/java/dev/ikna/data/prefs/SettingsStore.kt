package dev.ikna.data.prefs

import dev.ikna.domain.phonetics.PhoneticsMode

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
 * The palette the app wears out of the box: "Чернила", a calm ink-blue field
 * with the warm ikna mark kept as its accent.
 *
 * Lives here rather than in the theme package because a stored preference has to
 * have a default that the preferences layer can name without reaching up into the
 * interface. The colours themselves are in ui/theme/Theme.kt.
 */
const val DEFAULT_PALETTE_ID = "ink"

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

/** Interface language: "system" follows the phone, or a supported ISO code. */
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
private const val DEFAULT_CUSTOM_BACKGROUND = 0xFF0B1120
private const val DEFAULT_CUSTOM_INK = 0xFFE5EAF4
private const val DEFAULT_CUSTOM_MUTED = 0xFF78859C
private const val DEFAULT_CUSTOM_ACCENT = 0xFFFF7A5C

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
     * Whether the phone's own engine may read a card.
     *
     * On by default, and it decides nothing for a language an added model
     * speaks: a model of one's own is always preferred where it has a voice.
     * Off means a card nothing else can read stays silent and says so by not
     * drawing the mark — the honest answer for whoever finds the built-in
     * voices of their phone unbearable.
     *
     * One switch replaces three controls: speed, pitch, and a chosen voice per
     * language. Speed and pitch belonged to an engine this app did not write,
     * pitch did nothing at all on most of them, and both were part of the name
     * of every cached rendering — so a card rendered before the stored values
     * arrived was looked for under a different name and never played.
     */
    val phoneVoice: Boolean = true,
    /**
     * Whether a card reads itself every time it appears, or only the first time
     * that phrase is ever met.
     *
     * First contact is the meeting that needs a voice, so that is the default.
     * Whoever learns by ear turns this on and every card speaks by itself.
     */
    val autoSpeakEvery: Boolean = false,
    /** File name of the installed content font. Empty means the built-in one. */
    val fontName: String = "",
    /**
     * The ikna mark in the bottom bar. On by default and off by choice: it is
     * the one place the app says its own name, which is worth having on the
     * first day and worth nothing on the four hundredth. Whoever is tired of it
     * gets the room back.
     */
    val showWordmark: Boolean = true,
    /**
     * The bottom bar mirrored, so the marks sit under a left thumb.
     *
     * Not a theme and not decoration: a phone is held in one hand, the bar is
     * the only row of controls on the home screen, and for a left-handed user
     * every one of those marks is currently on the far side of the screen.
     */
    val leftHanded: Boolean = false,
    /**
     * How each deck's square looks: a label and a colour, stored as
     * "packId=label|tint" pairs joined by semicolons.
     *
     * A preference rather than a column on the deck, and deliberately so. This
     * is how one person likes their own list to look; it is not part of the
     * deck's content, it must not travel to whoever the deck is shared with, and
     * putting it in the database would mean a schema migration for a decoration.
     */
    val deckLooks: String = "",
    /**
     * Which transcription each deck shows, stored as "packId=mode" pairs joined
     * by semicolons. A deck not named here uses the default.
     *
     * Per deck rather than one switch for the whole app, and the reason is that
     * the question genuinely has different answers on the same phone. Somebody
     * learning Polish needs the line under every phrase; the same person
     * reading Spanish from English mostly does not, because Spanish spelling
     * already says how Spanish sounds. One global switch would force them to
     * pick the worse answer for one of the two decks.
     *
     * It follows deckLooks into a preference for the reasons that one is a
     * preference: this is how a person reads their own copy of a deck, it must
     * not travel to whoever the deck is sent to, and a schema migration is too
     * much machinery for a line of text under a phrase.
     */
    val deckPhonetics: String = "",
    /**
     * Chunks the learner has marked as wrong, separated by semicolons.
     *
     * A deck written by a language model can contain a card that is simply
     * false, and spaced repetition is extremely good at teaching whatever it is
     * handed. The only judge available is the person reading the card, so there
     * is now a third answer beside "knew it" and "did not": this card is broken.
     *
     * A chunk named here is never scheduled and never asked again, at any level.
     * Crucially it is not an error either: marking it costs no accuracy, so one
     * hallucination cannot close the governor's gate on new material for a week.
     *
     * Kept in preferences rather than in the database because it is a judgement
     * about content rather than content itself, and because putting a set of ids
     * in `chunks` would mean a schema migration for a list of mistakes. It rides
     * along with the settings backup, which is where a person's own corrections
     * belong.
     */
    val suppressed: String = "",
    val onboardingDone: Boolean = false,
    /** How many times the tap-to-reveal hint has been shown. Stops at 5. */
    val revealHintsShown: Int = 0,
    /**
     * How many answers have been given by swiping rather than by pressing a
     * button. Once there are enough of them the two rare ratings drop off the
     * screen and stay on the gesture, which is where they belong.
     */
    val swipesDone: Int = 0,
    /**
     * How long one answer took, last time there was enough history to measure
     * it, in milliseconds. Zero means never measured.
     *
     * Kept here rather than recomputed each time because the measurement needs
     * a handful of recent answers and there is not always a handful: a short
     * evening, or a run of instant recognitions, and the median goes back to
     * null. The figure used to vanish with it, so the one line that says what a
     * session costs came and went for no reason the user could see. A slightly
     * stale number is worth far more than no number.
     */
    val answerMs: Int = 0,
    /**
     * Whether the app may ask the releases page about a newer build.
     *
     * On, because an app that is installed by hand from a file cannot be
     * updated by anything else, and a bug fixed in a release nobody hears about
     * is a bug that is still shipping. Off, it opens no socket at all: the
     * switch is not a preference about notifications, it is the network.
     */
    val updateCheck: Boolean = true,
    /**
     * A version the user has pressed "skip" on, e.g. "0.5.0 press".
     *
     * Skipping silences that one version and nothing else -- the next release
     * asks again. Kept as the version rather than as a flag so that the record
     * cannot outlive what it was about, and so Settings can still offer the
     * update to somebody who changed their mind an hour later.
     */
    val updateSkipped: String = "",
    /** When the last check happened, epoch millis. Zero means never. */
    val updateCheckedAt: Long = 0L
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
        val phoneVoice = booleanPreferencesKey("phoneVoice")
        val autoSpeakEvery = booleanPreferencesKey("autoSpeakEvery")
        val fontName = stringPreferencesKey("fontName")
        val showWordmark = booleanPreferencesKey("showWordmark")
        val leftHanded = booleanPreferencesKey("leftHanded")
        val deckLooks = stringPreferencesKey("deckLooks")
        val deckPhonetics = stringPreferencesKey("deckPhonetics")
        val suppressed = stringPreferencesKey("suppressed")
        val onboardingDone = booleanPreferencesKey("onboardingDone")
        val revealHintsShown = intPreferencesKey("revealHintsShown")
        val swipesDone = intPreferencesKey("swipesDone")
        val answerMs = intPreferencesKey("answerMs")
        val updateCheck = booleanPreferencesKey("updateCheck")
        val updateSkipped = stringPreferencesKey("updateSkipped")
        val updateCheckedAt = longPreferencesKey("updateCheckedAt")
        // Internal migration marker, deliberately absent from IknaSettings:
        // it is not a preference and no screen is allowed to change it.
        val schedulerVersion = intPreferencesKey("schedulerVersion")
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
            // A file written by an older build still carries a speed, a pitch
            // and a voice per language. They are not read and not migrated:
            // there is nowhere left to put them, and an update has to survive
            // finding them rather than fail on them.
            phoneVoice = p[Keys.phoneVoice] ?: defaults.phoneVoice,
            autoSpeakEvery = p[Keys.autoSpeakEvery] ?: defaults.autoSpeakEvery,
            fontName = p[Keys.fontName] ?: defaults.fontName,
            showWordmark = p[Keys.showWordmark] ?: defaults.showWordmark,
            leftHanded = p[Keys.leftHanded] ?: defaults.leftHanded,
            deckLooks = p[Keys.deckLooks] ?: defaults.deckLooks,
            deckPhonetics = p[Keys.deckPhonetics] ?: defaults.deckPhonetics,
            suppressed = p[Keys.suppressed] ?: defaults.suppressed,
            onboardingDone = p[Keys.onboardingDone] ?: defaults.onboardingDone,
            revealHintsShown = p[Keys.revealHintsShown] ?: defaults.revealHintsShown,
            swipesDone = p[Keys.swipesDone] ?: defaults.swipesDone,
            answerMs = p[Keys.answerMs] ?: defaults.answerMs,
            updateCheck = p[Keys.updateCheck] ?: defaults.updateCheck,
            updateSkipped = p[Keys.updateSkipped] ?: defaults.updateSkipped,
            updateCheckedAt = p[Keys.updateCheckedAt] ?: defaults.updateCheckedAt
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

    suspend fun setPhoneVoice(on: Boolean) = put { it[Keys.phoneVoice] = on }

    suspend fun setAutoSpeakEvery(on: Boolean) = put { it[Keys.autoSpeakEvery] = on }

    suspend fun setFontName(name: String) = put { it[Keys.fontName] = name }

    suspend fun setShowWordmark(on: Boolean) = put { it[Keys.showWordmark] = on }
    suspend fun setLeftHanded(on: Boolean) = put { it[Keys.leftHanded] = on }

    /** Every deck's look at once. Used by a restore. */
    suspend fun setDeckLooks(value: String) = put { it[Keys.deckLooks] = value }

    /**
     * One deck's look, read-modify-write inside a single edit for the same
     * reason a voice is: two decks decorated a second apart must not be able to
     * drop each other.
     *
     * A deck set back to no icon and no colour is removed from the string rather
     * than stored as empty, so the preference does not grow a line for every
     * deck that was ever looked at.
     */
    suspend fun setDeckLook(packId: String, label: String?, tint: Int?) = put { prefs ->
        val map = parseDeckLooks(prefs[Keys.deckLooks] ?: "").toMutableMap()
        val look = DeckLook(label = deckLabelOf(label), tint = tint ?: NO_TINT)
        if (look.isPlain) map.remove(packId) else map[packId] = look
        prefs[Keys.deckLooks] = encodeDeckLooks(map)
    }

    /** Every deck's transcription choice at once. Used by a restore. */
    suspend fun setDeckPhonetics(value: String) = put { it[Keys.deckPhonetics] = value }

    /**
     * One deck's transcription, read-modify-write inside a single edit, for the
     * same reason a deck's look is: two decks changed a second apart must not be
     * able to drop each other's choice.
     *
     * Unlike a deck's look, the chosen mode is written even when it happens to
     * equal the current default. Storing only what differs would mean that
     * changing the default in a later release silently changes the behaviour of
     * decks somebody had already made up their mind about, and three words per
     * deck is not worth buying that with.
     */
    suspend fun setDeckPhonetic(packId: String, mode: PhoneticsMode) = put { prefs ->
        val map = parseDeckPhonetics(prefs[Keys.deckPhonetics] ?: "").toMutableMap()
        map[packId] = mode
        prefs[Keys.deckPhonetics] = encodeDeckPhonetics(map)
    }

    /**
     * Marks one chunk as wrong, read-modify-write inside a single edit.
     *
     * Newest first, and capped: the list is a handful of corrections, not a
     * second copy of the deck. Past the cap the oldest entry falls off, which is
     * safe -- a chunk that dropped out simply becomes askable again, and if it is
     * still wrong it can be marked again in one tap.
     */
    suspend fun suppressChunk(chunkId: String) = put { prefs ->
        val id = chunkId.filter { it != ';' && !it.isISOControl() }.trim()
        if (id.isNotEmpty()) {
            val kept = suppressedOf(prefs[Keys.suppressed] ?: "").filter { it != id }
            prefs[Keys.suppressed] = (listOf(id) + kept)
                .take(SUPPRESS_LIMIT)
                .joinToString(";")
        }
    }

    /** Puts every marked chunk back into circulation. */
    suspend fun clearSuppressed() = put { it[Keys.suppressed] = "" }

    suspend fun setSuppressed(value: String) = put { it[Keys.suppressed] = value }

    suspend fun setUpdateCheck(on: Boolean) = put { it[Keys.updateCheck] = on }

    /**
     * Remembers that this version was waved away, and when the asking happened.
     *
     * Both in one edit: a skip that recorded the version but not the time would
     * be asked again tomorrow, and one that recorded the time but not the
     * version would come back the moment the day rolled over.
     */
    suspend fun skipUpdate(version: String, now: Long) = put {
        it[Keys.updateSkipped] = version
        it[Keys.updateCheckedAt] = now
    }

    suspend fun markUpdateChecked(now: Long) = put { it[Keys.updateCheckedAt] = now }

    /** Which scheduler has produced the card table. Zero means a pre-marker build. */
    suspend fun schedulerVersion(): Int =
        context.iknaDataStore.data.first()[Keys.schedulerVersion] ?: 0

    /** Written only after the card-table replay has committed successfully. */
    suspend fun setSchedulerVersion(version: Int) = put {
        it[Keys.schedulerVersion] = version
    }

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

    /** The latest measured length of one answer, so the estimate outlives a session. */
    suspend fun setAnswerMs(ms: Int) = put { it[Keys.answerMs] = ms }

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

// ---- deck looks ------------------------------------------------------------
//
// Same shape as the voice map above, and hand-rolled for the same reasons: a
// handful of pairs, read by builds that may be older than the feature, and a
// malformed entry must lose one deck's colour rather than take the home screen
// down. Anything that does not parse is skipped.

/** No colour chosen: the deck's square uses the palette, the way it always did. */
const val NO_TINT = -1

/**
 * How one deck's square is drawn: up to two characters, and a colour. Both
 * halves are optional; a label without a colour and a colour without a label are
 * both ordinary choices.
 *
 * It held an emoji until 0.2.0. Emoji are drawn by the phone, in full colour,
 * with their own shading and their own idea of a corner radius -- next to this
 * app's flat single-colour marks they look like a sticker on a blueprint. Two
 * characters of the app's own typeface do the same job (tell two decks apart at
 * a glance) and cannot be out of place, because they are the same letters the
 * square already drew by itself.
 */
data class DeckLook(val label: String = "", val tint: Int = NO_TINT) {
    /** Nothing chosen at all, which is what the app does by default. */
    val isPlain: Boolean get() = label.isEmpty() && tint == NO_TINT
}

/**
 * A typed label, made safe to store and to draw.
 *
 * Two characters, because three do not fit the square at a legible size. The
 * three separator characters are stripped rather than escaped: this string is
 * stored in a hand-rolled "id=label|tint;..." format, and one semicolon typed
 * into a deck label would otherwise silently delete the decoration of every
 * deck after it.
 *
 * Line breaks and control characters go too. They arrive by paste, they are
 * invisible in the field, and they would push the square's own layout around.
 */
fun deckLabelOf(raw: String?): String {
    val cleaned = raw.orEmpty()
        .filter { it != ';' && it != '=' && it != '|' && !it.isISOControl() }
        .trim()
    if (cleaned.isEmpty()) return ""
    // Counted in code points, not in chars: a two-char label that is one letter
    // plus its combining accent must not be cut in half.
    val points = cleaned.codePointCount(0, cleaned.length)
    if (points <= 2) return cleaned
    return cleaned.substring(0, cleaned.offsetByCodePoints(0, 2))
}

fun parseDeckLooks(value: String): Map<String, DeckLook> =
    value.split(';')
        .asSequence()
        .mapNotNull { pair ->
            val at = pair.indexOf('=')
            if (at <= 0 || at == pair.lastIndex) return@mapNotNull null
            val id = pair.substring(0, at).trim()
            val body = pair.substring(at + 1)
            val bar = body.indexOf('|')
            val label = deckLabelOf(if (bar < 0) body else body.substring(0, bar))
            val tint = if (bar < 0) NO_TINT
            else body.substring(bar + 1).trim().toIntOrNull() ?: NO_TINT
            val look = DeckLook(label = label, tint = tint)
            if (id.isEmpty() || look.isPlain) null else id to look
        }
        .toMap()

fun encodeDeckLooks(map: Map<String, DeckLook>): String =
    map.entries
        .filter { it.key.isNotBlank() && !it.value.isPlain }
        .joinToString(";") { it.key + "=" + it.value.label + "|" + it.value.tint }

/** What this deck should look like. Never null: an undecorated deck is plain. */
fun IknaSettings.lookFor(packId: String): DeckLook =
    parseDeckLooks(deckLooks)[packId] ?: DeckLook()

// ---- deck transcriptions ---------------------------------------------------
//
// The same hand-rolled shape as the deck looks above, minus the second half:
// "packId=mode" pairs joined by semicolons. Third appearance of this pattern in
// this file, and the reasons have not changed -- a handful of entries, read by
// builds that may predate the feature, and a format somebody can read in an
// exported backup and repair by hand.
//
// One difference is worth naming. A deck's label is typed by a person, so the
// three separator characters have to be stripped out of it. A mode is not typed
// by anybody: it is one of three words this file writes and this file reads, so
// there is nothing to sanitise and an unrecognised word is simply dropped.

/**
 * Which transcription this deck shows. Never null: a deck nobody has decided
 * about gets the default, which is what almost every deck is.
 */
fun IknaSettings.phoneticsFor(packId: String): PhoneticsMode =
    parseDeckPhonetics(deckPhonetics)[packId] ?: PhoneticsMode.DEFAULT

/**
 * A damaged pair loses one deck's choice and nothing else, the same way a
 * damaged deck look does. An unknown mode is dropped rather than defaulted, so
 * a deck whose entry was mangled behaves like a deck with no entry instead of
 * quietly switching to something nobody picked.
 */
fun parseDeckPhonetics(value: String): Map<String, PhoneticsMode> =
    value.split(';')
        .asSequence()
        .mapNotNull { pair ->
            val at = pair.indexOf('=')
            if (at <= 0 || at == pair.lastIndex) return@mapNotNull null
            val id = pair.substring(0, at).trim()
            val mode = when (pair.substring(at + 1).trim().lowercase()) {
                PhoneticsMode.OFF.stored -> PhoneticsMode.OFF
                PhoneticsMode.RESPELL.stored -> PhoneticsMode.RESPELL
                PhoneticsMode.IPA.stored -> PhoneticsMode.IPA
                else -> null
            }
            if (id.isEmpty() || mode == null) null else id to mode
        }
        .toMap()

fun encodeDeckPhonetics(map: Map<String, PhoneticsMode>): String =
    map.entries
        .filter { it.key.isNotBlank() }
        .joinToString(";") { it.key + "=" + it.value.stored }

// ---- suppressed chunks -----------------------------------------------------
//
// A set of ids in one string, in the same hand-rolled shape as the deck looks
// above and for the same reasons: a handful of entries, read by builds that may
// predate the feature, and a format a person can read in an exported backup.

/**
 * How many corrections are remembered. Four hundred is far more than any deck
 * has wrong cards, and small enough that the preference stays a preference.
 */
const val SUPPRESS_LIMIT = 400

fun suppressedOf(value: String): List<String> =
    value.split(';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
