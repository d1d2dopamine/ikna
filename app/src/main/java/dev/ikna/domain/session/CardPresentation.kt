package dev.ikna.domain.session

import dev.ikna.data.db.CardEntity
import dev.ikna.data.db.ChunkEntity

enum class Level(val value: Int) {
    RECOGNITION(0),
    CLOZE(1),
    PRODUCTION(2);

    companion object {
        fun of(v: Int): Level = entries.first { it.value == v }
    }
}

/**
 * One chunk, three ways of asking. Novelty without new content, which matters
 * more here than in a normal SRS: the same 40 items feel different across
 * levels, so boredom does not force the queue to grow.
 *
 * Cloze is the primary format because it attributes an error to a specific
 * word for free, which is exactly the labelling the component layer needs.
 */
data class SessionCard(
    val card: CardEntity,
    val chunk: ChunkEntity,
    val level: Level,
    val fromAmnesty: Boolean
) {
    val prompt: String
        get() = when (level) {
            Level.RECOGNITION -> chunk.contextSentence
            Level.CLOZE -> blanked()
            Level.PRODUCTION -> chunk.translation
        }

    val answer: String
        get() = when (level) {
            Level.RECOGNITION -> chunk.translation
            Level.CLOZE -> chunk.text
            Level.PRODUCTION -> chunk.contextSentence
        }

    /**
     * Where the phrase being learned sits inside the sentence on the front of
     * the card, so the screen can mark it.
     *
     * The sentence is deliberately never translated -- it is the context to
     * guess from -- but the card only asks one question, and that question is
     * about one phrase inside it. Leaving the phrase unmarked adds a second,
     * accidental question: which of these six words am I being asked about. That
     * is not desirable difficulty, it is noise, and it lands in the grading: a
     * card failed because the eye was on the wrong word is still recorded as the
     * phrase forgotten, and the whole schedule is built out of those records.
     *
     * Null at the other two levels, and for different reasons: the cloze already
     * shows the position as a gap, and the production prompt is the translation,
     * which contains no sentence to mark.
     */
    val promptTarget: IntRange?
        get() = if (level == Level.RECOGNITION) targetRange() else null

    /** The same mark on the back of a production card, which is the sentence. */
    val answerTarget: IntRange?
        get() = if (level == Level.PRODUCTION) targetRange() else null

    /**
     * Clamped rather than trusted. The offsets are written by the importer and
     * an off-by-one in a hand-edited deck file must not be able to crash a
     * session in the middle of it.
     */
    private fun targetRange(): IntRange? {
        val s = chunk.contextSentence
        val a = chunk.targetStart.coerceIn(0, s.length)
        val b = chunk.targetEnd.coerceIn(a, s.length)
        return if (b > a) a until b else null
    }

    /**
     * Never answered before: level zero, no repetitions yet.
     *
     * There is no separate introduction card. A chunk met for the first time
     * is asked like every other card -- the answer is one tap away and the
     * first miss costs nothing. This flag only decides that the card comes
     * back once more inside the same session, because a single pass on the day
     * a chunk appears is the weakest point of any spaced system.
     */
    val isFirstContact: Boolean
        get() = level == Level.RECOGNITION && card.isNew && card.reps == 0

    val target: String
        get() = chunk.contextSentence.substring(
            chunk.targetStart.coerceIn(0, chunk.contextSentence.length),
            chunk.targetEnd.coerceIn(0, chunk.contextSentence.length)
        )

    private fun blanked(): String {
        val s = chunk.contextSentence
        val a = chunk.targetStart.coerceIn(0, s.length)
        val b = chunk.targetEnd.coerceIn(a, s.length)
        return s.substring(0, a) + "\u2022\u2022\u2022" + s.substring(b)
    }
}
