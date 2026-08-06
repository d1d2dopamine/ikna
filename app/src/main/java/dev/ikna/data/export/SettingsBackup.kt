package dev.ikna.data.export

import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.LoadPreset
import dev.ikna.data.prefs.SettingsStore
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
    val kind: String = SETTINGS_BACKUP_KIND,
    val version: Int = 1,
    val theme: String = ThemeMode.DARK.name,
    val customBackground: Int = 0,
    val customInk: Int = 0,
    val customMuted: Int = 0,
    val customAccent: Int = 0,
    val load: String = LoadPreset.NORMAL.name,
    val autoLoad: Boolean = true,
    val reminderEnabled: Boolean = true,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val haptics: Boolean = true,
    val animations: Boolean = true,
    val autoExport: Boolean = true,
    val speech: Boolean = true,
    val speechVoices: String = "",
    val fontName: String = ""
)

object SettingsBackup {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun snapshotOf(settings: IknaSettings): SettingsSnapshot = SettingsSnapshot(
        theme = settings.theme.name,
        customBackground = settings.customBackground,
        customInk = settings.customInk,
        customMuted = settings.customMuted,
        customAccent = settings.customAccent,
        load = settings.load.name,
        autoLoad = settings.autoLoad,
        reminderEnabled = settings.reminderEnabled,
        reminderHour = settings.reminderHour,
        reminderMinute = settings.reminderMinute,
        haptics = settings.haptics,
        animations = settings.animations,
        autoExport = settings.autoExport,
        speech = settings.speechEnabled,
        speechVoices = settings.speechVoices,
        fontName = settings.fontName
    )

    fun encode(settings: IknaSettings): String = json.encodeToString(
        SettingsSnapshot.serializer(),
        snapshotOf(settings)
    )

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
        val load = runCatching { LoadPreset.valueOf(snapshot.load) }.getOrDefault(LoadPreset.NORMAL)

        store.setCustomColors(
            background = snapshot.customBackground,
            ink = snapshot.customInk,
            muted = snapshot.customMuted,
            accent = snapshot.customAccent
        )
        store.setTheme(theme)
        if (snapshot.autoLoad) store.setAutoLoad(true) else store.setLoad(load)
        store.setReminder(snapshot.reminderEnabled, snapshot.reminderHour, snapshot.reminderMinute)
        store.setHaptics(snapshot.haptics)
        store.setAnimations(snapshot.animations)
        store.setAutoExport(snapshot.autoExport)
        store.setSpeechEnabled(snapshot.speech)
        store.setSpeechVoices(snapshot.speechVoices)
    }
}
