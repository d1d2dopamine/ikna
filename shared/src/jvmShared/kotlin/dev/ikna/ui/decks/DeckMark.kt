package dev.ikna.ui.decks

import java.util.Locale

/*
 * A deck's mark: stable letters over a small installation-seeded pixel seal.
 * The letters remain semantic; only the decorative pattern changes by install.
 */
private val KNOWN_LANGUAGES = mapOf(
    "pl" to "PL", "en" to "EN", "ru" to "RU", "de" to "DE",
    "es" to "ES", "fr" to "FR", "it" to "IT", "cs" to "CS",
    "uk" to "UK", "pt" to "PT", "nl" to "NL", "sv" to "SV",
    "tr" to "TR"
)

/** Nothing to derive a mark from. Rare enough to be worth a shape of its own. */
const val DECK_MARK_FALLBACK = "•"

/** Language code first; imported custom decks fall back to title initials. */
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
        initials.size == 1 -> words.first().filter { it.isLetterOrDigit() }.take(2).uppercase(Locale.ROOT)
        else -> DECK_MARK_FALLBACK
    }
}

/** The 9×9 field leaves space for a second rim around the monogram. */
const val DECK_SEAL_SIDE = 9

/** The 5×5 centre is paper reserved for the two letters. */
fun isDeckSealLetterZone(index: Int): Boolean {
    if (index !in 0 until DECK_SEAL_SIDE * DECK_SEAL_SIDE) return false
    val row = index / DECK_SEAL_SIDE
    val column = index % DECK_SEAL_SIDE
    return row in 2..6 && column in 2..6
}

/**
 * Build a frame from mirrored pairs. Top and bottom have distinct rhythms,
 * while the sides form continuous runs instead of scattering isolated pixels.
 */
fun deckSealCells(deckId: String, installedAt: Long): Set<Int> {
    val seed = sealHash(sealKey(deckId, installedAt, "pattern"))
    val cells = linkedSetOf<Int>()

    fun pair(row: Int, column: Int) {
        cells += row * DECK_SEAL_SIDE + column
        cells += row * DECK_SEAL_SIDE + DECK_SEAL_SIDE - 1 - column
    }

    val last = DECK_SEAL_SIDE - 1
    val pairedColumns = 0 until DECK_SEAL_SIDE / 2
    fun edge(row: Int, innerRow: Int, rhythm: Int) {
        val gap = (rhythm ushr 1) % 5
        for (column in pairedColumns) {
            if (column != gap) pair(row, column)
        }
        if ((rhythm and 1) == 0) pair(row, DECK_SEAL_SIDE / 2)
        val echo = (rhythm ushr 5) % pairedColumns.count()
        pair(innerRow, echo)
        if ((rhythm and 0x80) != 0) pair(innerRow, (echo + 1) % pairedColumns.count())
    }

    edge(0, 1, seed)
    edge(last, last - 1, sealStep(seed xor 0x24C17))
    val sides = sealStep(seed xor 0x6C3D9)
    val length = 2 + (sides and 1)
    val first = 2 + (sides ushr 3) % (last - 2 - length)
    for (row in first until first + length) pair(row, 0)

    if ((sides and 4) != 0) {
        val accentRow = if (first > 2) 2 else last - 2
        pair(accentRow, 0)
    }
    return cells
}

/** Two mirrored accents follow existing marks and keep the monogram clear. */
fun deckSealHighlights(deckId: String, installedAt: Long): Set<Int> {
    val cells = deckSealCells(deckId, installedAt)
    val seed = sealHash(sealKey(deckId, installedAt, "highlight"))
    val lastRow = DECK_SEAL_SIDE - 1
    return buildSet {
        for (row in listOf(0, lastRow)) {
            val choices = (0 until DECK_SEAL_SIDE / 2).filter { row * DECK_SEAL_SIDE + it in cells }
            val column = choices[(sealStep(seed xor (row + 1) * 0x42D) ushr 1) % choices.size]
            add(row * DECK_SEAL_SIDE + column)
            add(row * DECK_SEAL_SIDE + DECK_SEAL_SIDE - 1 - column)
        }
    }
}

private fun sealKey(deckId: String, installedAt: Long, stream: String): String =
    (deckId.ifEmpty { "deck" }) + ":" + installedAt + ":" + stream

private fun sealHash(text: String): Int {
    var hash = 0x811C9DC5.toInt()
    text.forEach { character -> hash = (hash xor character.code) * 16_777_619 }
    return hash
}

private fun sealStep(value: Int): Int {
    var x = value
    x = x xor (x shl 13)
    x = x xor (x ushr 17)
    x = x xor (x shl 5)
    return x
}
