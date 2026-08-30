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

/** The fixed side of every language seal. Seven stays legible inside 52dp. */
const val LANGUAGE_SEAL_SIDE = 7

/** The 5×5 centre is paper reserved for the two language letters. */
fun isDeckSealLetterZone(index: Int): Boolean {
    if (index !in 0 until LANGUAGE_SEAL_SIDE * LANGUAGE_SEAL_SIDE) return false
    val row = index / LANGUAGE_SEAL_SIDE
    val column = index % LANGUAGE_SEAL_SIDE
    return row in 1..5 && column in 1..5
}

/**
 * The base pixel seal of a language.
 *
 * It is generated from the canonical language code, mirrored around the middle
 * column and independent of a deck title or database id. Renaming a deck cannot
 * change its family resemblance, and adding a new language does not require a
 * new drawable resource. The algorithm is deliberately integer-only so JVM
 * tests and every Android architecture produce the same cells.
 */
fun languageSealCells(lang: String): Set<Int> {
    val code = lang.trim().lowercase(Locale.ROOT).ifEmpty { "und" }
    val cells = linkedSetOf<Int>()
    var state = sealHash(code)
    for (row in 0 until LANGUAGE_SEAL_SIDE) {
        for (column in 0 until LANGUAGE_SEAL_SIDE / 2) {
            state = sealStep(state + row * 37 + column * 101)
            if ((state ushr 29) < 3) {
                cells += row * LANGUAGE_SEAL_SIDE + column
                cells += row * LANGUAGE_SEAL_SIDE + (LANGUAGE_SEAL_SIDE - 1 - column)
            }
        }
        state = sealStep(state xor (row * 0x45D9F3B))
        if ((state and 1) != 0) {
            cells += row * LANGUAGE_SEAL_SIDE + LANGUAGE_SEAL_SIDE / 2
        }
    }

    // Two anchors make even an unusually sparse hash read as a designed stamp.
    val anchor = (sealHash(code) ushr 1) % LANGUAGE_SEAL_SIDE
    cells += anchor
    cells += LANGUAGE_SEAL_SIDE - 1 - anchor
    cells += (LANGUAGE_SEAL_SIDE - 1) * LANGUAGE_SEAL_SIDE + anchor
    cells += (LANGUAGE_SEAL_SIDE - 1) * LANGUAGE_SEAL_SIDE +
        (LANGUAGE_SEAL_SIDE - 1 - anchor)

    // Pattern and text never share a pixel neighbourhood. Filtering a symmetric
    // window preserves the mirror while leaving the monogram optically clean.
    cells.removeAll { isDeckSealLetterZone(it) }
    if (cells.size < 12) {
        // Add mirrored border pairs only. The centre remains empty even for the
        // rare language hash that would otherwise become too sparse.
        outer@ for (row in 0 until LANGUAGE_SEAL_SIDE) {
            for (column in 0 until LANGUAGE_SEAL_SIDE / 2) {
                val left = row * LANGUAGE_SEAL_SIDE + column
                val right = row * LANGUAGE_SEAL_SIDE + LANGUAGE_SEAL_SIDE - 1 - column
                if (isDeckSealLetterZone(left)) continue
                cells += left
                cells += right
                if (cells.size >= 12) break@outer
            }
        }
    }
    return cells
}

/** Four brighter cells that distinguish decks without changing their language. */
fun deckSealHighlights(deckId: String): Set<Int> {
    val allowed = (0 until LANGUAGE_SEAL_SIDE * LANGUAGE_SEAL_SIDE)
        .filterNot(::isDeckSealLetterZone)
    val cells = linkedSetOf<Int>()
    var state = sealHash(deckId.ifEmpty { "deck" })
    var attempts = 0
    while (cells.size < 4 && attempts < 32) {
        state = sealStep(state + attempts * 17)
        cells += allowed[(state ushr 1) % allowed.size]
        attempts++
    }
    return cells
}

private fun sealHash(text: String): Int {
    var hash = 0x811C9DC5.toInt()
    text.forEach { character ->
        hash = (hash xor character.code) * 16_777_619
    }
    return hash
}

private fun sealStep(value: Int): Int {
    var x = value
    x = x xor (x shl 13)
    x = x xor (x ushr 17)
    x = x xor (x shl 5)
    return x
}
