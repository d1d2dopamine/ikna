package dev.ikna.domain.session

import dev.ikna.data.db.ChunkEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that decide what a chunk can be asked.
 *
 * Every case here is a card that arrived from somewhere else. The app own packs
 * are all one shape, so these rules only ever matter for imported decks -- which
 * is exactly why they need tests rather than a look at the screen.
 */
class ChunkShapeTest {

    private fun chunk(
        text: String,
        context: String = text,
        translation: String = "",
        start: Int = 0,
        end: Int = 0
    ) = ChunkEntity(
        id = "c1",
        packId = "p1",
        lang = "de",
        text = text,
        contextSentence = context,
        translation = translation,
        targetStart = start,
        targetEnd = end,
        freqRank = 1
    )

    @Test
    fun `a phrase inside a sentence with a meaning has all three steps`() {
        val c = chunk(
            text = "einen Hund",
            context = "Ich habe einen Hund.",
            translation = "a dog",
            start = 9,
            end = 19
        )
        assertEquals(ChunkShape.PHRASE_IN_SENTENCE, Shapes.of(c))
        assertEquals(listOf(Ask.RECOGNISE, Ask.GAP, Ask.PRODUCE), Shapes.ladder(Shapes.of(c)))
    }

    @Test
    fun `a bare word is never asked as a gap`() {
        val c = chunk(text = "Hund", translation = "dog")
        assertEquals(ChunkShape.WORD, Shapes.of(c))
        assertFalse(Ask.GAP in Shapes.ladder(ChunkShape.WORD))
        assertEquals(Ask.RECOGNISE, Shapes.askAt(ChunkShape.WORD, 0))
        assertEquals(Ask.PRODUCE, Shapes.askAt(ChunkShape.WORD, 1))
    }

    @Test
    fun `a whole sentence is shown, never produced from its translation`() {
        val c = chunk(text = "Ich habe einen Hund.", translation = "I have a dog.")
        assertEquals(ChunkShape.SENTENCE, Shapes.of(c))
        assertEquals(listOf(Ask.RECOGNISE), Shapes.ladder(ChunkShape.SENTENCE))
    }

    @Test
    fun `a marked span with nothing written down is a gap and nothing else`() {
        val c = chunk(
            text = "Hund",
            context = "Ich habe einen Hund.",
            translation = "",
            start = 15,
            end = 19
        )
        assertEquals(ChunkShape.GAP_ONLY, Shapes.of(c))
        assertEquals(listOf(Ask.GAP), Shapes.ladder(ChunkShape.GAP_ONLY))
    }

    @Test
    fun `a chunk with nothing to ask is shown rather than dropped`() {
        val c = chunk(text = "Hund", translation = "")
        assertEquals(ChunkShape.SENTENCE, Shapes.of(c))
        assertEquals(Ask.RECOGNISE, Shapes.askAt(Shapes.of(c), 0))
    }

    @Test
    fun `a span covering the whole text singles nothing out`() {
        val text = "Ich habe einen Hund."
        assertFalse(Shapes.hasContext(text.length, 0, text.length))
        val c = chunk(text = text, translation = "I have a dog.", start = 0, end = text.length)
        assertEquals(ChunkShape.SENTENCE, Shapes.of(c))
    }

    @Test
    fun `an empty or backwards span singles nothing out`() {
        assertFalse(Shapes.hasContext(20, 5, 5))
        assertFalse(Shapes.hasContext(20, 9, 4))
        assertFalse(Shapes.hasContext(0, 0, 0))
    }

    @Test
    fun `offsets past the end of the text do not throw`() {
        assertFalse(Shapes.hasContext(10, 40, 90))
        assertTrue(Shapes.hasContext(10, 0, 4))
        assertTrue(Shapes.hasContext(10, 4, 40))
    }

    @Test
    fun `a sentence is judged by its words, then by its punctuation`() {
        assertTrue(Shapes.isSentence("Ich habe einen Hund und eine Katze"))
        assertTrue(Shapes.isSentence("Ich habe Hunde."))
        assertFalse(Shapes.isSentence("einen Hund"))
        assertFalse(Shapes.isSentence(""))
        assertFalse(Shapes.isSentence("   "))
    }

    @Test
    fun `a script written without spaces is judged by length`() {
        assertFalse(Shapes.isSentence("犬"))
        assertTrue(Shapes.isSentence("私は犬を飼っています。"))
    }

    @Test
    fun `the highest step matches the ladder`() {
        assertEquals(2, Shapes.maxLevel(ChunkShape.PHRASE_IN_SENTENCE))
        assertEquals(1, Shapes.maxLevel(ChunkShape.WORD))
        assertEquals(0, Shapes.maxLevel(ChunkShape.SENTENCE))
        assertEquals(0, Shapes.maxLevel(ChunkShape.GAP_ONLY))
    }

    @Test
    fun `a step the chunk no longer has is clamped, not refused`() {
        assertEquals(Ask.GAP, Shapes.askAt(ChunkShape.GAP_ONLY, 2))
        assertEquals(Ask.RECOGNISE, Shapes.askAt(ChunkShape.SENTENCE, 7))
        assertEquals(Ask.RECOGNISE, Shapes.askAt(ChunkShape.WORD, -3))
    }
}
