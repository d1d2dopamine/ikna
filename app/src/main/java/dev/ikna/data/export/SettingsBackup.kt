package dev.ikna.data.export

import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.LANGUAGE_SYSTEM
import dev.ikna.data.prefs.SettingsStore
import dev.ikna.data.prefs.DEFAULT_PALETTE_ID
import dev.ikna.data.prefs.ThemeMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * The other half of a backup.
 *
 * The review log is the part that cannot be recreated, and it was already being
 * exported. But restoring it onto a fresh install gave back every card and every
 * date and none of the look: theme, the four custom colours and the chosen font
 * all had to be typed in again from memory. Small, and exactly the kind of small
 * that makes a restore feel like it did not work.
 *
 * Kept in a separate file from the log on purpose. The log is one JSON object per
 * line and a restore reads it line by line; mixing a settings record into it
 * would make every version of the restore count that line as a damaged answer.
 *
 * The font file itself is not included — it is the user's own file, it can be
 * megabytes, and it lives wherever they picked it from. Its name is remembered so
 * a restore can say which font to pick again.
 */

const val SETTINGS_BACKUP_KIND = "ikna-settings"

@Serializable
data class SettingsSnapshot(
    /*
     * No default here, on purpose, and it is the only field without one.
     *
     * Every other field has a default so that a file written by a newer build
     * still opens in an older one. But a default on this field meant that any
     * JSON object at all decoded into a complete, valid-looking snapshot:
     * unknown keys were ignored, missing keys were filled in, and the marker the
     * check relies on was invented by the parser. A line of the review log
     * decoded into "settings" whose every value was a default — so restoring the
     * wrong file of the two would have quietly reset the theme, the four custom
     * colours and the font instead of refusing.
     *
     * A settings file has to say that it is one.
     */
    val kind: String,
    val version: Int = 1,
    val theme: String = ThemeMode.DARK.name,
    // A file written before palettes existed has no id in it, and the default is
    // the right answer for it: that build had exactly one dark scheme, and the
    // palette that carries it forward is the default one.
    val paletteId: String = DEFAULT_PALETTE_ID,
    val customBackground: Int = 0,
    val customInk: Int = 0,
    val customMuted: Int = 0,
    val customAccent: Int = 0,
    val manualLoad: Int = 40,
    val language: String = LANGUAGE_SYSTEM,
    val autoLoad: Boolean = true,
    val reminderEnabled: Boolean = true,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val haptics: Boolean = true,
    val animations: Boolean = true,
    val autoExport: Boolean = true,
    // Off, matching the setting's own default. A file from an older build has no
    // such field, and a missing field must not be able to switch speech on for
    // someone who never asked for it.
    val speech: Boolean = false,
    val speechVoices: String = "",
    // 100 means the engine's own speed and pitch, matching SPEECH_TONE_DEFAULT. A
    // literal is used because a serializable default has to be a constant here,
    // and a file written by an older build simply has neither field.
    val speechRate: Int = 100,
    val speechPitch: Int = 100,
    val fontName: String = ""
)

object SettingsBackup {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun snapshotOf(settings: IknaSettings): SettingsSnapshot = SettingsSnapshot(
        kind = SETTINGS_BACKUP_KIND,
        theme = settings.theme.name,
        paletteId = settings.paletteId,
        customBackground = settings.customBackground,
        customInk = settings.customInk,
        customMuted = settings.customMuted,
        customAccent = settings.customAccent,
        manualLoad = settings.manualLoad,
        language = settings.language,
        autoLoad = settings.autoLoad,
        reminderEnabled = settings.reminderEnabled,
        reminderHour = settings.reminderHour,
        reminderMinute = settings.reminderMinute,
        haptics = settings.haptics,
        animations = settings.animations,
        autoExport = settings.autoExport,
        speech = settings.speechEnabled,
        speechVoices = settings.speechVoices,
        speechRate = settings.speechRate,
        speechPitch = settings.speechPitch,
        fontName = settings.fontName
    )

    fun encode(settings: IknaSettings): String = json.encodeToString(
        SettingsSnapshot.serializer(),
        snapshotOf(settings)
    )

    /**
     * Returns null for anything that is not one of our settings files.
     *
     * Two guards, because they fail on different inputs: a missing `kind` makes
     * the parser throw, and a `kind` belonging to some other tool makes the
     * check below reject it.
     */
    fun decode(text: String): SettingsSnapshot? =
        runCatching { json.decodeFromString(SettingsSnapshot.serializer(), text) }
            .getOrNull()
            ?.takeIf { it.kind == SETTINGS_BACKUP_KIND }

    /**
     * Cheap check used to route a picked file to the right restore path, so the
     * user does not have to remember which of the two files is which.
     */
    fun looksLikeSettings(text: String): Boolean = text.take(400).contains(SETTINGS_BACKUP_KIND)

    /**
     * Writes the snapshot back into settings.
     *
     * The font comes back as a name only: the file was the user's and may be
     * gone, so the message says which one to pick again rather than pretending
     * it was restored.
     */
    suspend fun apply(store: SettingsStore, snapshot: SettingsSnapshot) {
        val theme = runCatching { ThemeMode.valueOf(snapshot.theme) }.getOrDefault(ThemeMode.DARK)

        store.setCustomColors(
            background = snapshot.customBackground,
            ink = snapshot.customInk,
            muted = snapshot.customMuted,
            accent = snapshot.customAccent
        )
        store.setPalette(snapshot.paletteId)
        store.setTheme(theme)
        if (snapshot.autoLoad) store.setAutoLoad(true) else store.setManualLoad(snapshot.manualLoad)
        store.setLanguage(snapshot.language)
        store.setReminder(snapshot.reminderEnabled, snapshot.reminderHour, snapshot.reminderMinute)
        store.setHaptics(snapshot.haptics)
        store.setAnimations(snapshot.animations)
        store.setAutoExport(snapshot.autoExport)
        store.setSpeechEnabled(snapshot.speech)
        store.setSpeechVoices(snapshot.speechVoices)
        store.setSpeechTone(snapshot.speechRate, snapshot.speechPitch)
    }
}
