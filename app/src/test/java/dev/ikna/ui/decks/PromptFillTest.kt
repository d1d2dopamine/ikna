package dev.ikna.ui.decks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The prompt, answered by the app instead of by hand.
 *
 * The rule these tests hold in place is that filling the prompt in adds answers
 * and changes nothing else: the model reads three kilobytes of rules before it
 * reaches the questions, and a fill that drops, reorders or splits a line
 * quietly turns a deck builder into a chatbot.
 */
class PromptFillTest {

    private val base = listOf(
        "HARD RULES",
        "1. The sentence must contain the phrase EXACTLY.",
        "",
        "MY REQUEST",
        PROMPT_LEARNING,
        PROMPT_MEANINGS,
        PROMPT_COUNT,
        PROMPT_TOPIC,
        "My level (beginner / can hold a conversation / advanced):",
        "Anything else:"
    ).joinToString("\n")

    private fun fill(
        learning: String = "pl",
        meanings: String = "ru",
        count: Int = 100,
        topic: String = "cafes",
        level: String = LEVEL_TALKING
    ) = fillPrompt(base, learning, meanings, count, topic, level)

    @Test
    fun `every question is answered in the language the model reads`() {
        val out = fill().lines()
        assertTrue(out.contains(PROMPT_LEARNING + " Polish"))
        assertTrue(out.contains(PROMPT_MEANINGS + " Russian"))
        assertTrue(out.contains(PROMPT_COUNT + " 100"))
        assertTrue(out.contains(PROMPT_TOPIC + " cafes"))
        assertTrue(out.any { it.startsWith(PROMPT_LEVEL) && it.endsWith(LEVEL_TALKING) })
    }

    @Test
    fun `the rules above the questions are untouched`() {
        val out = fill().lines()
        assertEquals(base.lines().size, out.size)
        assertEquals("HARD RULES", out[0])
        assertEquals("1. The sentence must contain the phrase EXACTLY.", out[1])
        assertEquals("", out[2])
        assertEquals("Anything else:", out.last())
    }

    @Test
    fun `a question left unanswered stays blank rather than saying none`() {
        val out = fill(learning = "", topic = "   ").lines()
        assertTrue(out.contains(PROMPT_LEARNING))
        assertTrue(out.contains(PROMPT_TOPIC))
    }

    @Test
    fun `a topic pasted with line breaks cannot break the shape of the prompt`() {
        val out = fill(topic = "cafes\nand trains").lines()
        assertEquals(base.lines().size, out.size)
        assertTrue(out.contains(PROMPT_TOPIC + " cafes and trains"))
    }

    @Test
    fun `an empty asset is returned as it is`() {
        assertEquals("", fillPrompt("", "pl", "ru", 100, "cafes", LEVEL_BEGINNER))
    }
}
