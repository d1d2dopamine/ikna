package dev.ikna.domain.session

import dev.ikna.data.catalog.catalogMeaning
import dev.ikna.data.db.ChunkEntity

/**
 * Content shape metadata for imported decks and target highlighting.
 * Shape does not select an exercise; every active card uses the classic front/back.
 */
enum class ChunkShape {
    /** A phrase inside its own sentence, with a meaning. */
    PHRASE_IN_SENTENCE,

    /** A word or phrase standing alone, with a meaning. */
    WORD,

    /** A whole sentence with a meaning. */
    SENTENCE,

    /** Historical import classification: marked span without a meaning. */
    GAP_ONLY
}

/** The sole active card presentation. */
enum class Ask { RECOGNISE }

/**
 * Shared content classification and span validation. Import and presentation
 * use the same definition of a target inside a longer context.
 */
object Shapes {
    /** Words from which a run of text reads as a sentence rather than a phrase. */
    const val WORDS_FOR_SENTENCE = 6

    /** The same judgement for scripts that are written without spaces. */
    const val CHARS_FOR_SENTENCE = 12

    private val TERMINAL = charArrayOf('.', '!', '?', '…', '。', '！', '？')

    fun of(chunk: ChunkEntity): ChunkShape {
        val context = hasContext(chunk)
        val meaning = hasMeaning(chunk)
        return when {
            context && meaning -> ChunkShape.PHRASE_IN_SENTENCE
            context -> ChunkShape.GAP_ONLY
            meaning && isSentence(chunk.contextSentence) -> ChunkShape.SENTENCE
            meaning -> ChunkShape.WORD
            // Nothing to test against: shown once and acknowledged, which is
            // still better than dropping it silently on import.
            else -> ChunkShape.SENTENCE
        }
    }

    /**
     * Whether the span singles something out inside a longer text.
     *
     * The single definition of that question. A span covering the text end to
     * end marks nothing: the importer writes one for every ordinary card,
     * because a card's own text is all the context it has. Treating that as a
     * marked phrase highlights the whole sentence, gives every word full weight
     * in the component layer instead of identifying a phrase within its context.
     */
    fun hasContext(contextLength: Int, targetStart: Int, targetEnd: Int): Boolean {
        val start = targetStart.coerceIn(0, contextLength)
        val end = targetEnd.coerceIn(start, contextLength)
        return end > start && (start > 0 || end < contextLength)
    }

    fun hasContext(chunk: ChunkEntity): Boolean =
        hasContext(chunk.contextSentence.length, chunk.targetStart, chunk.targetEnd)

    /**
     * Whether a run of text reads as a sentence.
     *
     * Counting words first, then falling back to length, because Japanese and
     * Chinese are written without spaces and would otherwise count as one word
     * forever. Final punctuation lowers the bar in both cases: three words and
     * a full stop is a sentence, three words without one is a phrase.
     */
    fun isSentence(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val terminal = trimmed.last() in TERMINAL
        val words = trimmed.split(' ', ' ', '\n', '\t').count { it.isNotBlank() }
        return if (words >= 2) {
            words >= WORDS_FOR_SENTENCE || (terminal && words >= 3)
        } else {
            trimmed.length >= CHARS_FOR_SENTENCE || (terminal && trimmed.length >= 6)
        }
    }

    private fun hasMeaning(chunk: ChunkEntity): Boolean =
        catalogMeaning(chunk.translation).text.isNotBlank()
}
