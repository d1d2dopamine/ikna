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
     * Never answered before: level zero, no repetitions yet.
     *
     * The first meeting with a chunk is the weakest link in this app. Retrieval
     * practice helps, but it cannot repair material that was never properly
     * encoded, so a first contact is shown as an introduction rather than asked
     * as a question the user is guaranteed to fail.
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
