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

/** The fixed side of every deck seal. Seven stays legible inside 52dp. */
const val DECK_SEAL_SIDE = 7

/** The 5×5 centre is paper reserved for the two letters. */
fun isDeckSealLetterZone(index: Int): Boolean {
    if (index !in 0 until DECK_SEAL_SIDE * DECK_SEAL_SIDE) return false
    val row = index / DECK_SEAL_SIDE
    val column = index % DECK_SEAL_SIDE
    return row in 1..5 && column in 1..5
}

/**
 * Decorative cells derived from this deck and this local installation time.
 * Integer-only generation keeps the result identical on every JVM architecture.
 */
fun deckSealCells(deckId: String, installedAt: Long): Set<Int> {
    val seed = sealKey(deckId, installedAt, "pattern")
    val cells = linkedSetOf<Int>()
    var state = sealHash(seed)
    for (row in 0 until DECK_SEAL_SIDE) {
        for (column in 0 until DECK_SEAL_SIDE / 2) {
            state = sealStep(state + row * 37 + column * 101)
            if ((state ushr 29) < 3) {
                cells += row * DECK_SEAL_SIDE + column
                cells += row * DECK_SEAL_SIDE + (DECK_SEAL_SIDE - 1 - column)
            }
        }
        state = sealStep(state xor (row * 0x45D9F3B))
        if ((state and 1) != 0) cells += row * DECK_SEAL_SIDE + DECK_SEAL_SIDE / 2
    }

    val anchor = (sealHash(seed) ushr 1) % DECK_SEAL_SIDE
    cells += anchor
    cells += DECK_SEAL_SIDE - 1 - anchor
    cells += (DECK_SEAL_SIDE - 1) * DECK_SEAL_SIDE + anchor
    cells += (DECK_SEAL_SIDE - 1) * DECK_SEAL_SIDE + (DECK_SEAL_SIDE - 1 - anchor)
    cells.removeAll(::isDeckSealLetterZone)

    if (cells.size < 12) {
        outer@ for (row in 0 until DECK_SEAL_SIDE) {
            for (column in 0 until DECK_SEAL_SIDE / 2) {
                val left = row * DECK_SEAL_SIDE + column
                val right = row * DECK_SEAL_SIDE + DECK_SEAL_SIDE - 1 - column
                if (isDeckSealLetterZone(left)) continue
                cells += left
                cells += right
                if (cells.size >= 12) break@outer
            }
        }
    }
    return cells
}

/** Four brighter cells from the same installation seed, using another stream. */
fun deckSealHighlights(deckId: String, installedAt: Long): Set<Int> {
    val allowed = (0 until DECK_SEAL_SIDE * DECK_SEAL_SIDE)
        .filterNot(::isDeckSealLetterZone)
    val cells = linkedSetOf<Int>()
    var state = sealHash(sealKey(deckId, installedAt, "highlight"))
    var attempts = 0
    while (cells.size < 4 && attempts < 32) {
        state = sealStep(state + attempts * 17)
        cells += allowed[(state ushr 1) % allowed.size]
        attempts++
    }
    return cells
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
