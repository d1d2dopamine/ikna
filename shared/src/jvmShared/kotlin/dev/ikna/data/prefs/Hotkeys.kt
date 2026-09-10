package dev.ikna.data.prefs

/**
 * Stable, portable representation of the configurable desktop review keys.
 *
 * A chord is one ordinary key with up to two modifiers. Keeping the stored
 * value independent of Compose/AWT means settings backups do not depend on a
 * desktop toolkit key code or on the machine that created the backup.
 */
const val DEFAULT_HOTKEYS =
    "miss=LEFT;know=RIGHT;reveal=SPACE;undo=Z"

private val HOTKEY_TOKEN = Regex("^[A-Z0-9_]+$")
private val MODIFIER_ORDER = listOf("CTRL", "ALT", "SHIFT", "META")
private val MODIFIERS = MODIFIER_ORDER.toSet()

enum class HotkeyAction(val storedName: String, val defaultValue: String) {
    MISS("miss", "LEFT"),
    KNOW("know", "RIGHT"),
    REVEAL("reveal", "SPACE"),
    UNDO("undo", "Z");

    companion object {
        fun fromStoredName(value: String): HotkeyAction? =
            entries.firstOrNull { it.storedName == value }
    }
}

class HotkeyChord private constructor(val tokens: List<String>) {
    val encoded: String get() = tokens.joinToString("+")

    override fun equals(other: Any?): Boolean = other is HotkeyChord && tokens == other.tokens
    override fun hashCode(): Int = tokens.hashCode()
    override fun toString(): String = encoded

    companion object {
        fun parse(value: String?): HotkeyChord? {
            if (value.isNullOrBlank()) return null
            return of(value.split('+'))
        }

        fun of(rawTokens: Iterable<String>): HotkeyChord? {
            val clean = rawTokens
                .map { it.trim().uppercase() }
                .filter { it.isNotEmpty() }
                .distinct()
            if (clean.size !in 1..3 || clean.any { !HOTKEY_TOKEN.matches(it) }) return null
            val main = clean.filterNot { it in MODIFIERS }
            if (main.size != 1) return null
            val canonical = MODIFIER_ORDER.filter { it in clean } + main.single()
            return HotkeyChord(canonical)
        }
    }
}

object HotkeyBindings {
    fun defaults(): Map<HotkeyAction, HotkeyChord> = linkedMapOf<HotkeyAction, HotkeyChord>().apply {
        HotkeyAction.entries.forEach { action ->
            put(action, requireNotNull(HotkeyChord.parse(action.defaultValue)))
        }
    }

    /** Malformed or future entries are ignored one-by-one, never as a whole file. */
    fun decode(value: String?): Map<HotkeyAction, HotkeyChord> {
        val result = defaults().toMutableMap()
        value.orEmpty().split(';').forEach { entry ->
            val separator = entry.indexOf('=')
            if (separator <= 0) return@forEach
            val action = HotkeyAction.fromStoredName(entry.substring(0, separator).trim())
                ?: return@forEach
            val chord = HotkeyChord.parse(entry.substring(separator + 1)) ?: return@forEach
            result[action] = chord
        }
        return HotkeyAction.entries.associateWithTo(linkedMapOf()) { action ->
            requireNotNull(result[action])
        }
    }

    fun encode(bindings: Map<HotkeyAction, HotkeyChord>): String =
        HotkeyAction.entries.joinToString(";") { action ->
            val chord = bindings[action]
                ?: requireNotNull(HotkeyChord.parse(action.defaultValue))
            "${action.storedName}=${chord.encoded}"
        }

    fun replace(value: String?, action: HotkeyAction, chord: HotkeyChord): String {
        val bindings = decode(value).toMutableMap()
        bindings[action] = chord
        return encode(bindings)
    }

    fun conflictingAction(
        bindings: Map<HotkeyAction, HotkeyChord>,
        action: HotkeyAction,
        chord: HotkeyChord
    ): HotkeyAction? = bindings.entries
        .firstOrNull { (otherAction, otherChord) -> otherAction != action && otherChord == chord }
        ?.key
}
