package dev.ikna.domain.session

import dev.ikna.data.catalog.catalogMeaning
import dev.ikna.data.db.CardEntity
import dev.ikna.data.db.ChunkEntity

/**
 * Which step of the ladder a card is on.
 *
 * The names are historical: this used to be the kind of question itself, back
 * when every chunk was a phrase inside a sentence and step one was always
 * recognition. The value is the index into the chunk's ladder now, and what is
 * actually asked comes from [Ask]. The enum stays because the number is stored
 * on every card and in every history record.
 */
enum class Level(val value: Int) {
    RECOGNITION(0),
    CLOZE(1),
    PRODUCTION(2);

    companion object {
        fun of(v: Int): Level = entries.first { it.value == v }
    }
}

/**
 * One chunk, asked in as many ways as it can be asked.
 *
 * Novelty without new content, which matters more here than in a normal spaced
 * system: the same forty items feel different across steps, so boredom does not
 * force the queue to grow. Which ways exist is decided by [Shapes] from the
 * chunk itself, never guessed here -- a bare word imported from somewhere else
 * has no sentence to take a gap out of.
 */
data class SessionCard(
    val card: CardEntity,
    val chunk: ChunkEntity,
    val level: Level,
    val fromAmnesty: Boolean
) {
    /** The answer without the catalogue machine-readable source suffix. */
    val meaning: String
        get() = catalogMeaning(chunk.translation).text

    /** Public sentence id, present only on cards built out of Tatoeba. */
    val sourceId: String?
        get() = catalogMeaning(chunk.translation).tatoebaId

    /** What this chunk is, and therefore which steps exist for it. */
    val shape: ChunkShape
        get() = Shapes.of(chunk)

    /** What this card asks right now. */
    val ask: Ask
        get() = Shapes.askAt(shape, level.value)

    val prompt: String
        get() = when (ask) {
            Ask.RECOGNISE -> chunk.contextSentence
            Ask.GAP -> blanked()
            Ask.PRODUCE -> meaning
        }

    val answer: String
        get() = when (ask) {
            // A chunk with nothing written down about it is shown and
            // acknowledged rather than tested, so the back is the text itself
            // instead of an empty card.
            Ask.RECOGNISE -> meaning.ifBlank { chunk.text }
            Ask.GAP -> chunk.text
            Ask.PRODUCE -> chunk.contextSentence
        }

    /**
     * How the front sounds, in IPA, or null when saying so would be wrong.
     *
     * Only the recognition step has one. That step shows the sentence and asks
     * whether it is understood, so pronunciation is a note about something
     * already on the screen and costs the question nothing.
     *
     * The other two are null on purpose, and this is the part that matters:
     *
     *  - The gap step shows the sentence with the phrase cut out of it. Its
     *    transcription would still contain the missing phrase's sounds, so a
     *    line under the gap would spell out the answer -- "boh-KOO" under a
     *    sentence missing `bardzo` is not a hint, it is the card being given
     *    away, and the miss it prevents is recorded as a success.
     *  - The production step shows only the meaning, in the meaning language.
     *    Transcribing that would be transcribing the language the learner
     *    already speaks.
     */
    val promptIpa: String?
        get() = when (ask) {
            Ask.RECOGNISE -> chunk.ipaContext
            Ask.GAP -> null
            Ask.PRODUCE -> null
        }

    /**
     * The same for the back, where every step may have one because the answer
     * is no longer being withheld.
     *
     * The recognition case follows [answer]: a chunk with no meaning written
     * down shows its own text rather than an empty card, so what is transcribed
     * is the phrase and not the sentence.
     */
    val answerIpa: String?
        get() = when (ask) {
            Ask.RECOGNISE -> if (meaning.isBlank()) chunk.ipa else null
            Ask.GAP -> chunk.ipa
            Ask.PRODUCE -> chunk.ipaContext
        }

    /**
     * Where the phrase being learned sits inside the sentence on the front, so
     * the screen can mark it.
     *
     * The sentence is deliberately never translated -- it is the context to
     * guess from -- but the card asks about one phrase inside it. Leaving that
     * phrase unmarked adds a second, accidental question: which of these six
     * words am I being asked about. That is not desirable difficulty, it is
     * noise, and it lands in the grading, because a card failed with the eye on
     * the wrong word is still recorded as the phrase forgotten.
     *
     * Null when there is nothing to single out: the gap step already shows the
     * position as a gap, the production prompt is a translation with no
     * sentence in it, and an imported card whose span covers its whole text
     * would be marked end to end, which reads as a fault rather than a phrase.
     */
    val promptTarget: IntRange?
        get() = if (ask == Ask.RECOGNISE && Shapes.hasContext(chunk)) targetRange() else null

    /** The same mark on the back of a production card, which is the sentence. */
    val answerTarget: IntRange?
        get() = if (ask == Ask.PRODUCE && Shapes.hasContext(chunk)) targetRange() else null

    /**
     * Clamped rather than trusted. These offsets are written by the importer,
     * and an off-by-one in a hand-edited deck must not be able to end a session
     * in the middle of it.
     */
    private fun targetRange(): IntRange? {
        val s = chunk.contextSentence
        val a = chunk.targetStart.coerceIn(0, s.length)
        val b = chunk.targetEnd.coerceIn(a, s.length)
        return if (b > a) a until b else null
    }

    /**
     * Never answered before: first step, no repetitions yet.
     *
     * There is no separate introduction card. A chunk met for the first time is
     * asked like every other card -- the answer is one tap away and the first
     * miss costs nothing. This flag only decides that the card comes back once
     * more inside the same session, because a single pass on the day a chunk
     * appears is the weakest point of any spaced system.
     */
    val isFirstContact: Boolean
        get() = level.value == 0 && card.isNew && card.reps == 0

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
