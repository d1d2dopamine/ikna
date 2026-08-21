package dev.ikna.data.anki

import dev.ikna.data.repo.NO_LANG
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The question this replaces was eleven chips and one tap, asked before the file
 * was open. These are the decks that tap used to get wrong.
 */
class DeckLanguageTest {

    @Test
    fun `a language in the deck name decides it`() {
        assertEquals(
            "de",
            deck(
                "Deutsch A1", "ru",
                "Hund" to "собака",
                "Katze" to "кошка",
                "Fenster" to "окно"
            )
        )
    }

    @Test
    fun `an exam counts as a name`() {
        assertEquals("ja", deck("JLPT N5 nouns", "en", "neko" to "cat", "inu" to "dog"))
    }

    @Test
    fun `a name outranks the subject test`() {
        // English idioms read in English are still English, not a subject.
        assertEquals(
            "en",
            deck(
                "English idioms", "en",
                "to bite the bullet" to "to accept something unpleasant",
                "under the weather" to "not feeling well at all"
            )
        )
    }

    @Test
    fun `a script answers for itself`() {
        assertEquals(
            "ja",
            deck(
                "カード", "ru",
                "日本語を勉強しています" to "я учу японский",
                "これはなんですか" to "что это такое"
            )
        )
    }

    @Test
    fun `Cyrillic cards with English meanings are Russian`() {
        assertEquals(
            "ru",
            deck(
                "deck 1", "en",
                "Собака не мой друг." to "The dog is not my friend.",
                "Она читает книгу." to "She is reading a book with me."
            )
        )
    }

    @Test
    fun `Ukrainian is told apart from Russian`() {
        assertEquals(
            "uk",
            deck(
                "deck 2", "en",
                "Це мої книги та її зошити." to "These are my books and her notebooks.",
                "Я їв сніданок удома." to "I had breakfast at home with the others."
            )
        )
    }

    @Test
    fun `Latin script is told by accents and small words`() {
        assertEquals(
            "pl",
            deck(
                "deck 3", "ru",
                "Nie jestem gotowy, ale to si\u0119 zmieni." to "Я не готов, но это изменится.",
                "To jest ksi\u0105\u017cka mojej siostry." to "Это книга моей сестры."
            )
        )
    }

    @Test
    fun `a deck in the reader's own language on both sides is a subject`() {
        assertEquals(
            NO_LANG,
            deck(
                "Biology", "en",
                "What is the mitochondrion for?" to "It is the site of respiration in the cell.",
                "What does the ribosome do?" to "It builds the proteins that the cell needs."
            )
        )
    }

    @Test
    fun `translations in the reader's language make it a language deck`() {
        assertEquals(
            "en",
            deck(
                "deck 4", "ru",
                "The dog is not my friend at all." to "Собака совсем не мой друг.",
                "She was reading that book with you." to "Она читала ту книгу с тобой."
            )
        )
    }

    @Test
    fun `bare words nobody can place are left unnamed`() {
        // Latin letters, no accents, no function words: a guess here would put a
        // voice and the wrong alphabet behind every card in the deck.
        assertEquals(
            DeckLanguage.UNDECIDED,
            deck(
                "deck 5", "ru",
                "Hund" to "собака живёт в доме",
                "Fenster" to "окно в большой комнате",
                "Tisch" to "стол стоит у окна"
            )
        )
    }

    @Test
    fun `nothing to tell apart is a subject`() {
        assertEquals(NO_LANG, deck("deck 6", "en", "2 + 2" to "4", "7 * 8" to "56"))
    }

    @Test
    fun `a regional app language still matches`() {
        assertEquals(
            NO_LANG,
            deck(
                "Chemie", "de-AT",
                "Was ist eine Molmasse und wofür ist das?" to
                    "Das ist die Masse mit der Einheit, nicht ein Volumen.",
                "Was macht ein Katalysator?" to
                    "Er ist ein Stoff, der die Reaktion nicht selbst mitmacht."
            )
        )
    }

    @Test
    fun `an empty deck is a subject rather than a guess`() {
        assertEquals(NO_LANG, DeckLanguage.of("deck 7", emptyList(), "en"))
    }

    private fun deck(name: String, app: String, vararg cards: Pair<String, String>): String =
        DeckLanguage.of(
            deckName = name,
            samples = cards.map { LanguageSample(it.first, it.second) },
            appLanguage = app
        )
}
