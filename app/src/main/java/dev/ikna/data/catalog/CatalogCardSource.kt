package dev.ikna.data.catalog

import dev.ikna.data.db.ChunkEntity

/**
 * The catalogue keeps attribution beside the meaning so it survives every path
 * a deck can take: network import, bundled asset, export and re-import.
 *
 * The builder writes the suffix as `\n— Tatoeba #123`. Presentation code uses
 * this parser instead of showing that transport format as part of the answer.
 * A malformed suffix is ordinary text: no guessed identifier, no link.
 */
data class CatalogMeaning(
    val text: String,
    val tatoebaId: String? = null
)

private val TATOEBA_SUFFIX = Regex("(?s)^(.*)\\n[—-]\\s*Tatoeba #([1-9][0-9]{0,18})\\s*$")

fun catalogMeaning(raw: String): CatalogMeaning {
    val match = TATOEBA_SUFFIX.matchEntire(raw) ?: return CatalogMeaning(raw)
    return CatalogMeaning(
        text = match.groupValues[1].trimEnd(),
        tatoebaId = match.groupValues[2]
    )
}

/** A fixed host and a digits-only id: catalogue data can never choose a URL. */
fun tatoebaSentenceUrl(id: String?): String? {
    val safe = id?.takeIf { it.matches(Regex("[1-9][0-9]{0,18}")) } ?: return null
    return "https://tatoeba.org/en/sentences/show/$safe"
}

/**
 * A report made to be pasted into a GitHub issue without editing.
 *
 * The report deliberately contains no review state, timing, device data or user
 * identifier. It describes the public card and its public source only.
 */
fun catalogCardReport(chunk: ChunkEntity): String? {
    if (!chunk.packId.startsWith("catalog-")) return null
    val meaning = catalogMeaning(chunk.translation)
    val url = tatoebaSentenceUrl(meaning.tatoebaId) ?: return null
    return buildString {
        appendLine("ikna catalogue card report")
        appendLine()
        appendLine("Deck: ${chunk.packId}")
        appendLine("Chunk: ${chunk.id}")
        appendLine("Source: $url")
        appendLine("Phrase: ${oneLine(chunk.text)}")
        appendLine("Sentence: ${oneLine(chunk.contextSentence)}")
        appendLine("Translation: ${oneLine(meaning.text)}")
        append("Problem: ")
    }
}

private fun oneLine(value: String): String =
    value.replace('\n', ' ').replace('\r', ' ').trim()
