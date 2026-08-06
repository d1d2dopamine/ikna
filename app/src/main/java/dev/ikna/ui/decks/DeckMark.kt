package dev.ikna.ui.decks

import java.util.Locale

/*
 * A deck's mark.
 *
 * Two letters, drawn large, so the list is scanned by shape instead of read word
 * by word. This matters more here than in most apps: someone learning two
 * languages in parallel opens this screen several times a day and is always
 * looking for the same two rows, and "Polish · core" and "Polski — podstawa" are
 * the same silhouette at a glance while PL and EN are not.
 *
 * Language code first, because it is the honest answer and it is stable. Titles
 * are user-supplied and get renamed; a mark that changes when a deck is renamed
 * is a mark nobody learns.
 */

private val KNOWN_LANGUAGES = mapOf(
    "pl" to "PL",
    "en" to "EN",
    "ru" to "RU",
    "de" to "DE",
    "es" to "ES",
    "fr" to "FR",
    "it" to "IT",
    "cs" to "CS",
    "uk" to "UK",
    "pt" to "PT",
    "nl" to "NL",
    "sv" to "SV",
    "tr" to "TR"
)

/** Nothing to derive a mark from. Rare enough to be worth a shape of its own. */
const val DECK_MARK_FALLBACK = "\u2022"

/**
 * "pl" -> PL. An imported file called "my words" -> MW. A single word -> its
 * first two letters.
 *
 * Imported packs arrive with lang = "custom", which is not a language and must
 * not become the mark CU on every deck the user adds.
 */
fun monogramOf(lang: String, title: String): String {
    val code = lang.trim().lowercase(Locale.ROOT)
    KNOWN_LANGUAGES[code]?.let { return it }
    if (code.length == 2 && code.all { it.isLetter() }) return code.uppercase(Locale.ROOT)

    val words = title.trim()
        .split(Regex("[\\s_\\-·—–.:/]+"))
        .filter { word -> word.any { it.isLetterOrDigit() } }
    val initials = words.mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } }

    return when {
        initials.size >= 2 -> (initials[0].toString() + initials[1]).uppercase(Locale.ROOT)
        initials.size == 1 -> {
            val letters = words.first().filter { it.isLetterOrDigit() }
            letters.take(2).uppercase(Locale.ROOT)
        }
        else -> DECK_MARK_FALLBACK
    }
}
