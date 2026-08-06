package dev.ikna.ui.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

const val LANG_RU = "ru"
const val LANG_EN = "en"
const val LANG_PL = "pl"

/**
 * Interface text in three languages.
 *
 * [lang] is snapshot state, so every composable that calls [t] redraws itself
 * when the language changes -- no activity restart, no configuration change.
 * A key that is missing from a translation falls back to Russian, and a key
 * that is missing everywhere returns itself, so a typo shows up as a visible
 * key instead of an empty screen.
 */
object S {
	var lang by mutableStateOf(LANG_RU)
		private set

	/** Accepts a stored setting value: "system", "ru", "en" or "pl". */
	fun apply(code: String) {
		lang = resolve(code)
	}

	fun resolve(code: String): String {
		val raw = if (code == "system") Locale.getDefault().language else code
		return when (raw.lowercase(Locale.ROOT)) {
			LANG_EN -> LANG_EN
			LANG_PL -> LANG_PL
			else -> LANG_RU
		}
	}

	fun t(key: String): String {
		val table = when (lang) {
			LANG_EN -> STRINGS_EN
			LANG_PL -> STRINGS_PL
			else -> STRINGS_RU
		}
		return table[key] ?: STRINGS_RU[key] ?: key
	}
}
