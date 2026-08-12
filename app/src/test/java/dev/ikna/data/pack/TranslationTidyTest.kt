package dev.ikna.data.pack

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The third column, as it arrives from a language model rather than as it was
 * asked for.
 *
 * Two habits are repaired at import time: a lost question mark, and a list of
 * two or three wordings where one was requested. Both matter more than they
 * look. The mark is half of what a phrase means, and a list of wordings turns
 * self-grading into multiple choice -- the learner sees three answers, one of
 * them matches whatever they thought, and the card is marked known.
 *
 * The other half of these tests is the part that must NOT happen: a translation
 * that is simply long, or that contains a comma because the phrase does, has to
 * come out exactly as written. A tidy step that edits correct lines is worse
 * than no tidy step at all.
 */
class TranslationTidyTest {

    // ---- the mark ----------------------------------------------------------

    @Test
    fun `a question keeps its question mark`() {
        assertEquals(
            "\u043a\u0430\u043a \u0434\u0435\u043b\u0430?",
            SeedFormat.tidyTranslation("how are you?", "\u043a\u0430\u043a \u0434\u0435\u043b\u0430")
        )
    }

    @Test
    fun `an exclamation keeps its exclamation mark`() {
        assertEquals("\u043e\u0441\u0442\u043e\u0440\u043e\u0436\u043d\u043e!", SeedFormat.tidyTranslation("watch out!", "\u043e\u0441\u0442\u043e\u0440\u043e\u0436\u043d\u043e"))
    }

    @Test
    fun `a full stop where the phrase asks is replaced, not appended`() {
        assertEquals(
            "\u043a\u0430\u043a \u0434\u0435\u043b\u0430?",
            SeedFormat.tidyTranslation("how are you?", "\u043a\u0430\u043a \u0434\u0435\u043b\u0430.")
        )
    }

    @Test
    fun `a mark the phrase has not got is removed`() {
        assertEquals("\u043f\u043e\u0433\u043e\u0434\u0438", SeedFormat.tidyTranslation("hang on", "\u043f\u043e\u0433\u043e\u0434\u0438?"))
    }

    @Test
    fun `a translation that already ends right is untouched`() {
        assertEquals(
            "\u043a\u0430\u043a \u0434\u0435\u043b\u0430?",
            SeedFormat.tidyTranslation("how are you?", "\u043a\u0430\u043a \u0434\u0435\u043b\u0430?")
        )
        assertEquals("\u043f\u043e\u0433\u043e\u0434\u0438", SeedFormat.tidyTranslation("hang on", "\u043f\u043e\u0433\u043e\u0434\u0438"))
    }

    @Test
    fun `both marks are copied when the phrase carries both`() {
        assertEquals("\u0447\u0442\u043e?!", SeedFormat.tidyTranslation("what?!", "\u0447\u0442\u043e"))
    }

    // ---- one wording -------------------------------------------------------

    @Test
    fun `a comma-separated list of synonyms keeps the first`() {
        assertEquals(
            "\u0443\u0441\u043f\u0435\u0442\u044c",
            SeedFormat.tidyTranslation(
                "make it",
                "\u0443\u0441\u043f\u0435\u0442\u044c, \u043f\u0440\u0438\u0439\u0442\u0438, \u0434\u043e\u0431\u0440\u0430\u0442\u044c\u0441\u044f"
            )
        )
    }

    @Test
    fun `a slash separates wordings`() {
        assertEquals("tired", SeedFormat.tidyTranslation("worn out", "tired / worn out"))
        assertEquals("tired", SeedFormat.tidyTranslation("worn out", "tired/worn out"))
    }

    @Test
    fun `a bracketed note is dropped`() {
        assertEquals("tired", SeedFormat.tidyTranslation("worn out", "tired (informal)"))
    }

    @Test
    fun `a semicolon separates wordings`() {
        assertEquals("tired", SeedFormat.tidyTranslation("worn out", "tired; worn out"))
    }

    @Test
    fun `the mark survives the list being cut`() {
        assertEquals(
            "\u043a\u0430\u043a \u0434\u0435\u043b\u0430?",
            SeedFormat.tidyTranslation(
                "how are you?",
                "\u043a\u0430\u043a \u0434\u0435\u043b\u0430, \u043a\u0430\u043a \u0436\u0438\u0432\u0451\u0448\u044c"
            )
        )
    }

    // ---- what must not be touched ------------------------------------------

    @Test
    fun `a phrase with a comma allows a comma in its translation`() {
        val translation = "\u043d\u0443, \u043b\u0430\u0434\u043d\u043e"
        assertEquals(translation, SeedFormat.tidyTranslation("well, okay", translation))
    }

    @Test
    fun `a long translation with a comma is a sentence, not a list`() {
        val translation = "as far as I know, nobody has asked"
        assertEquals(translation, SeedFormat.tidyTranslation("as far as I know", translation))
    }

    @Test
    fun `a date is not a list of wordings`() {
        assertEquals("12/05", SeedFormat.tidyTranslation("the twelfth of May", "12/05"))
    }

    @Test
    fun `an empty result is never produced`() {
        assertEquals("(informal)", SeedFormat.tidyTranslation("whatever", "(informal)"))
    }

    // ---- the whole way through a parse -------------------------------------

    @Test
    fun `a parsed row carries the tidied translation`() {
        val parsed = SeedFormat.parse(
            "how are you? | So, how are you? | \u043a\u0430\u043a \u0434\u0435\u043b\u0430, \u043a\u0430\u043a \u0436\u0438\u0437\u043d\u044c"
        )

        assertEquals(1, parsed.rows.size)
        assertEquals("\u043a\u0430\u043a \u0434\u0435\u043b\u0430?", parsed.rows[0].translation)
        assertEquals(0, parsed.problems.size)
    }
}
