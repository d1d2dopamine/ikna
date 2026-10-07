package dev.ikna.domain.session

import dev.ikna.data.db.CardEntity
import dev.ikna.data.db.ChunkEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Classic front/back rendering, including compatibility with historical levels.
 */
class CardPresentationTest {

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

    private fun cardAt(level: Int, isNew: Boolean = false, reps: Int = 3) = CardEntity(
        chunkId = chunk.id,
        level = level,
        stability = 5.0,
        difficulty = 5.0,
        dueAt = 0L,
        lastReviewAt = null,
        introducedAt = 0L,
        reps = reps,
        isNew = isNew
    )

    private fun sessionCard(level: Level, isNew: Boolean = false, reps: Int = 3) = SessionCard(
        card = cardAt(level.value, isNew, reps),
        chunk = chunk,
        level = level,
        fromAmnesty = false
    )

    @Test
    fun `the target span points at the trained phrase`() {
        assertEquals("take a shower", sessionCard(Level.RECOGNITION).target)
    }

    @Test
    fun `all stored levels render the full classic prompt and meaning`() {
        for (level in Level.entries) {
            assertEquals(chunk.contextSentence, sessionCard(level).prompt)
            assertEquals(chunk.translation, sessionCard(level).answer)
            assertEquals(Ask.RECOGNISE, sessionCard(level).ask)
        }
    }

    @Test
    fun `a never answered card is marked for its second pass`() {
        assertTrue(sessionCard(Level.RECOGNITION, isNew = true, reps = 0).isFirstContact)
        assertTrue(!sessionCard(Level.RECOGNITION, isNew = false, reps = 4).isFirstContact)
        assertTrue(!sessionCard(Level.CLOZE, isNew = true, reps = 0).isFirstContact)
    }

    @Test
    fun `levels round trip through their stored value`() {
        for (level in Level.entries) {
            assertEquals(level, Level.of(level.value))
        }
    }
}
