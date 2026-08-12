package dev.ikna.domain.session

import dev.ikna.data.db.CardEntity
import dev.ikna.data.db.ChunkEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which part of the card is the question.
 *
 * The sentence around the phrase is never translated -- that is the whole idea
 * -- but the card still asks about one phrase inside it, and until this existed
 * the screen never said which. A miss then meant either "forgot the phrase" or
 * "was looking at the wrong word", and both were written into the schedule as
 * the first one.
 *
 * The offsets come from a deck file, so the arithmetic is tested against a bad
 * one too: a session must never be able to crash on a card, because a card that
 * crashes cannot be answered and therefore never leaves the queue.
 */
class CardTargetTest {

    private val chunk = ChunkEntity(
        id = "en-ru-core-0001",
        packId = "en-ru-core",
        lang = "en",
        text = "take a shower",
        contextSentence = "I usually take a shower before breakfast.",
        translation = "\u043f\u0440\u0438\u043d\u044f\u0442\u044c \u0434\u0443\u0448",
        targetStart = 10,
        targetEnd = 23,
        freqRank = 1
    )

    private fun card(level: Level, on: ChunkEntity = chunk) = SessionCard(
        card = CardEntity(
            chunkId = on.id,
            level = level.value,
            stability = 5.0,
            difficulty = 5.0,
            dueAt = 0L,
            lastReviewAt = null,
            introducedAt = 0L,
            reps = 3,
            isNew = false
        ),
        chunk = on,
        level = level,
        fromAmnesty = false
    )

    @Test
    fun `the marked part of the sentence is the phrase itself`() {
        val range = card(Level.RECOGNITION).promptTarget

        assertEquals(10 until 23, range)
        assertEquals("take a shower", chunk.contextSentence.substring(10, 23))
    }

    @Test
    fun `a cloze marks nothing because the gap already shows the place`() {
        assertNull(card(Level.CLOZE).promptTarget)
        assertNull(card(Level.CLOZE).answerTarget)
    }

    @Test
    fun `production marks the sentence on the back and not the prompt`() {
        assertNull("the prompt is a translation, there is nothing to mark", card(Level.PRODUCTION).promptTarget)
        assertEquals(10 until 23, card(Level.PRODUCTION).answerTarget)
    }

    @Test
    fun `recognition marks the front and leaves the translation alone`() {
        assertEquals(10 until 23, card(Level.RECOGNITION).promptTarget)
        assertNull(card(Level.RECOGNITION).answerTarget)
    }

    @Test
    fun `offsets past the end of the sentence do not produce a broken span`() {
        val broken = chunk.copy(targetStart = 30, targetEnd = 900)
        val range = card(Level.RECOGNITION, broken).promptTarget

        assertEquals(30 until broken.contextSentence.length, range)
    }

    @Test
    fun `an empty span marks nothing rather than a zero length range`() {
        val empty = chunk.copy(targetStart = 12, targetEnd = 12)
        val backwards = chunk.copy(targetStart = 20, targetEnd = 4)

        assertNull(card(Level.RECOGNITION, empty).promptTarget)
        assertNull(card(Level.RECOGNITION, backwards).promptTarget)
    }
}
