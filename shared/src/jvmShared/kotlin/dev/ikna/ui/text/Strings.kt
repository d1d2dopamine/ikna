package dev.ikna.ui.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

const val LANG_RU = "ru"
const val LANG_EN = "en"
const val LANG_PL = "pl"
const val LANG_ES = "es"
const val LANG_FR = "fr"
const val LANG_DE = "de"
const val LANG_PT = "pt"

/**
 * Interface text in seven languages.
 *
 * [lang] is snapshot state, so every composable that calls [t] redraws itself
 * when the language changes -- no activity restart, no configuration change.
 * A key that is missing from a translation falls back to English, and a key
 * that is missing everywhere returns itself, so a typo shows up as a visible
 * key instead of an empty screen.
 */
object S {
	var lang by mutableStateOf(LANG_RU)
		private set
	var pseudo by mutableStateOf(false)
		private set

	/** Accepts "system" or one of the supported ISO language codes. */
	fun apply(code: String, pseudoLocale: Boolean = false) {
		lang = resolve(code)
		pseudo = pseudoLocale
	}

	fun resolve(code: String): String {
		val raw = if (code == "system") Locale.getDefault().language else code
		return when (raw.lowercase(Locale.ROOT)) {
			LANG_RU -> LANG_RU
			LANG_EN -> LANG_EN
			LANG_PL -> LANG_PL
			LANG_ES -> LANG_ES
			LANG_FR -> LANG_FR
			LANG_DE -> LANG_DE
			LANG_PT -> LANG_PT
			else -> LANG_EN
		}
	}

	fun t(key: String): String {
		val table = when (lang) {
			LANG_EN -> STRINGS_EN
			LANG_PL -> STRINGS_PL
			LANG_ES -> STRINGS_ES
			LANG_FR -> STRINGS_FR
			LANG_DE -> STRINGS_DE
			LANG_PT -> STRINGS_PT
			else -> STRINGS_RU
		}
		val text = table[key] ?: STRINGS_EN[key] ?: key
		return if (pseudo) pseudoLocalize(text) else text
	}
}

/**
 * Makes layout problems visible without changing card or deck content.
 * Leading and trailing whitespace stays outside the markers because a few
 * catalogue entries are sentence fragments joined to a number at the call site.
 */
fun pseudoLocalize(text: String): String {
	if (text.isBlank()) return text
	val leading = text.takeWhile { it.isWhitespace() }
	val trailing = text.takeLastWhile { it.isWhitespace() }
	val end = text.length - trailing.length
	val core = text.substring(leading.length, end)
	if (core.isEmpty()) return text
	val extra = ((core.length * 35 + 99) / 100).coerceAtLeast(2)
	return leading + "⟦ " + core + "·".repeat(extra) + " ⟧" + trailing
}
