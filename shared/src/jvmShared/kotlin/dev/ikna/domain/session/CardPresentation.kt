package dev.ikna.domain.session

import dev.ikna.data.catalog.catalogMeaning
import dev.ikna.data.db.CardEntity
import dev.ikna.data.db.ChunkEntity

/**
 * Historical stored presentation index. Only level 0 is active.
 *
 * Preserve all stored values for history/export/restore. They no longer choose
 * an exercise or trigger promotion: every active session uses recognition.
 */
enum class Level(val value: Int) {
    RECOGNITION(0),
    CLOZE(1),
    PRODUCTION(2);

    companion object {
        fun of(v: Int): Level = entries.first { it.value == v }
    }
}

/** One classic presentation. Stored levels 1/2 are historical compatibility only. */
data class SessionCard(
    val card: CardEntity,
    val chunk: ChunkEntity,
    val level: Level,
    val fromAmnesty: Boolean
) {
    val meaning: String get() = catalogMeaning(chunk.translation).text
    val sourceId: String? get() = catalogMeaning(chunk.translation).tatoebaId
    val shape: ChunkShape get() = Shapes.of(chunk)
    val ask: Ask get() = Ask.RECOGNISE
    val prompt: String get() = chunk.contextSentence
    val answer: String get() = meaning.ifBlank { chunk.text }
    val promptIpa: String? get() = chunk.ipaContext
    val answerIpa: String? get() = if (meaning.isBlank()) chunk.ipa else null
    val promptTarget: IntRange? get() = if (Shapes.hasContext(chunk)) targetRange() else null
    val answerTarget: IntRange? get() = null
    val isFirstContact: Boolean get() = level.value == 0 && card.isNew && card.reps == 0
    val target: String
        get() = chunk.contextSentence.substring(
            chunk.targetStart.coerceIn(0, chunk.contextSentence.length),
            chunk.targetEnd.coerceIn(0, chunk.contextSentence.length)
        )

    private fun targetRange(): IntRange? {
        val s = chunk.contextSentence
        val a = chunk.targetStart.coerceIn(0, s.length)
        val b = chunk.targetEnd.coerceIn(a, s.length)
        return if (b > a) a until b else null
    }
}
