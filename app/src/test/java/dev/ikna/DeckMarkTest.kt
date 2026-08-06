package dev.ikna

import dev.ikna.ui.decks.DECK_MARK_FALLBACK
import dev.ikna.ui.decks.monogramOf
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The mark is how a deck is recognised on the home screen without reading, so it
 * has to be stable and it must never come out empty or three letters wide.
 */
class DeckMarkTest {

    @Test
    fun the_language_code_wins() {
        assertEquals("PL", monogramOf("pl", "Polski \u00b7 core"))
        assertEquals("EN", monogramOf("en", "English core"))
    }

    @Test
    fun case_does_not_matter() {
        assertEquals("EN", monogramOf("EN", "English"))
    }

    @Test
    fun imported_packs_use_their_title_not_the_word_custom() {
        // Imported files arrive with lang = "custom". Without this every deck a
        // user adds would carry the same mark.
        assertEquals("MW", monogramOf("custom", "my words"))
    }

    @Test
    fun one_word_gives_its_first_two_letters() {
        assertEquals("PO", monogramOf("custom", "Polski"))
    }

    @Test
    fun punctuation_separates_words() {
        assertEquals("AB", monogramOf("custom", "alpha-beta"))
        assertEquals("AB", monogramOf("custom", "alpha_beta.jsonl"))
    }

    @Test
    fun an_unknown_two_letter_code_is_taken_as_a_language() {
        assertEquals("HU", monogramOf("hu", "Magyar"))
    }

    @Test
    fun nothing_to_work_with_still_gives_a_mark() {
        assertEquals(DECK_MARK_FALLBACK, monogramOf("custom", "   "))
    }

    @Test
    fun a_mark_is_never_wider_than_two_characters() {
        val titles = listOf("my words", "Polski", "a", "\u0441\u043b\u043e\u0432\u0430 \u0438\u0437 \u0444\u0438\u043b\u044c\u043c\u043e\u0432", "   ", "2024 list")
        titles.forEach { title ->
            val mark = monogramOf("custom", title)
            assertEquals(title, true, mark.length in 1..2)
        }
    }
}
