package dev.ikna.domain.session

import dev.ikna.data.catalog.catalogMeaning
import dev.ikna.data.db.ChunkEntity

/**
 * What a chunk actually is, which decides what can be asked about it.
 *
 * The app's own packs are all the same shape: a phrase, the sentence it lives
 * in, and what it means. A deck that arrived from somewhere else is not. A bare
 * word has no sentence to take a gap out of; a whole sentence cannot be
 * produced from a translation nobody has been shown; a cloze card has a gap but
 * often nothing written down about what the missing words mean. Asking the
 * wrong question of a chunk does not merely look odd -- the miss is recorded
 * against the item, and the schedule is built out of those records.
 */
enum class ChunkShape {
    /** A phrase inside its own sentence, with a meaning. Everything is possible. */
    PHRASE_IN_SENTENCE,

    /** A word or phrase standing alone, with a meaning. */
    WORD,

    /** A whole sentence with a meaning: shown and acknowledged, not produced. */
    SENTENCE,

    /** A marked span with nothing written down about it. Only the gap works. */
    GAP_ONLY
}

/** What a card asks, as opposed to which step of the ladder it is on. */
enum class Ask {
    /** Here is the sentence: do you know this? */
    RECOGNISE,

    /** Here is the sentence with the phrase missing. */
    GAP,

    /** Here is the meaning: say it. */
    PRODUCE
}

/**
 * The rules that decide a chunk's shape and its ladder.
 *
 * Kept in one object, with no dependencies beyond the chunk itself, because the
 * session builder, the promotion rule, the screen and the importer all have to
 * agree. When two of them disagreed, the app asked for a gap in a sentence that
 * did not exist.
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

    fun ladder(shape: ChunkShape): List<Ask> = when (shape) {
        ChunkShape.PHRASE_IN_SENTENCE -> listOf(Ask.RECOGNISE, Ask.GAP, Ask.PRODUCE)
        ChunkShape.WORD -> listOf(Ask.RECOGNISE, Ask.PRODUCE)
        ChunkShape.SENTENCE -> listOf(Ask.RECOGNISE)
        ChunkShape.GAP_ONLY -> listOf(Ask.GAP)
    }

    /** The highest step this shape has, for the promotion rule. */
    fun maxLevel(shape: ChunkShape): Int = ladder(shape).size - 1

    /**
     * What the card on this step asks.
     *
     * Clamped rather than checked: a card stored at a step its chunk no longer
     * has -- because a deck was re-imported, or a translation was filled in
     * later -- must still be answerable instead of crashing the session.
     */
    fun askAt(shape: ChunkShape, step: Int): Ask {
        val steps = ladder(shape)
        return steps[step.coerceIn(0, steps.size - 1)]
    }

    /**
     * Whether the span singles something out inside a longer text.
     *
     * The single definition of that question. A span covering the text end to
     * end marks nothing: the importer writes one for every ordinary card,
     * because a card's own text is all the context it has. Treating that as a
     * marked phrase highlights the whole sentence, gives every word full weight
     * in the component layer, and produces a gap card with nothing left around
     * the gap.
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
