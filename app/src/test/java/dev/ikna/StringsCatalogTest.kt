package dev.ikna

import dev.ikna.ui.text.LANG_EN
import dev.ikna.ui.text.LANG_ES
import dev.ikna.ui.text.LANG_FR
import dev.ikna.ui.text.LANG_DE
import dev.ikna.ui.text.LANG_PL
import dev.ikna.ui.text.LANG_PT
import dev.ikna.ui.text.LANG_RU
import dev.ikna.ui.text.S
import dev.ikna.ui.text.STRINGS_EN
import dev.ikna.ui.text.STRINGS_ES
import dev.ikna.ui.text.STRINGS_FR
import dev.ikna.ui.text.STRINGS_DE
import dev.ikna.ui.text.STRINGS_PL
import dev.ikna.ui.text.STRINGS_PT
import dev.ikna.ui.text.STRINGS_RU
import dev.ikna.ui.text.pseudoLocalize
import dev.ikna.ui.text.quantityWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The interface is written seven times over, and a missing line is invisible
 * until someone is standing in front of it in Warsaw. These tests are the only
 * place where all copies are laid next to each other, so a key added to
 * one language and forgotten in another fails the build instead of shipping an
 * empty label.
 */
class StringsCatalogTest {

    @Test
    fun `every language has exactly the russian key set`() {
        val tables = listOf(STRINGS_EN, STRINGS_PL, STRINGS_ES, STRINGS_FR, STRINGS_DE, STRINGS_PT)
        tables.forEach { table -> assertEquals(STRINGS_RU.keys, table.keys) }
    }

    @Test
    fun `no translation carries a key russian does not have`() {
        val strays = (STRINGS_EN.keys + STRINGS_PL.keys + STRINGS_ES.keys +
            STRINGS_FR.keys + STRINGS_DE.keys + STRINGS_PT.keys)
            .filterNot { it in STRINGS_RU }.sorted()

        assertEquals("keys without a russian original: $strays", emptyList<String>(), strays)
    }

    @Test
    fun `nothing is blank`() {
        val blank = (STRINGS_RU + STRINGS_EN + STRINGS_PL + STRINGS_ES +
            STRINGS_FR + STRINGS_DE + STRINGS_PT).filterValues { it.isEmpty() }.keys

        assertEquals("blank text for: $blank", emptySet<String>(), blank)
    }

    @Test
    fun `a key nobody translated shows the key instead of nothing`() {
        assertEquals("set.999", S.t("set.999"))
    }

    @Test
    fun `the load chip says exactly one word`() {
        assertEquals("\u0410\u0412\u0422\u041e", STRINGS_RU["set.015"])
        assertEquals("AUTO", STRINGS_EN["set.015"])
        assertEquals("AUTO", STRINGS_PL["set.015"])
    }

    @Test
    fun `the screen is called statistics in all three`() {
        assertEquals("\u0421\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043a\u0430", STRINGS_RU["stats.001"])
        assertEquals("Statistics", STRINGS_EN["stats.001"])
        assertEquals("Statystyka", STRINGS_PL["stats.001"])
    }
}

/**
 * Language resolution. "system" has to mean the phone, because that is the
 * setting nobody will ever open, and an unknown code has to land somewhere
 * readable rather than on a screen of raw keys.
 */
class LanguageResolverTest {

    private fun withLocale(locale: Locale, body: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            body()
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `an explicit choice wins over the phone`() {
        withLocale(Locale("ru")) {
            assertEquals(LANG_PL, S.resolve("pl"))
            assertEquals(LANG_EN, S.resolve("en"))
            assertEquals(LANG_ES, S.resolve("es"))
            assertEquals(LANG_FR, S.resolve("fr"))
            assertEquals(LANG_DE, S.resolve("de"))
            assertEquals(LANG_PT, S.resolve("pt"))
        }
    }

    @Test
    fun `system follows the phone`() {
        withLocale(Locale("pl", "PL")) { assertEquals(LANG_PL, S.resolve("system")) }
        withLocale(Locale("en", "GB")) { assertEquals(LANG_EN, S.resolve("system")) }
        withLocale(Locale("ru", "RU")) { assertEquals(LANG_RU, S.resolve("system")) }
        withLocale(Locale("es", "ES")) { assertEquals(LANG_ES, S.resolve("system")) }
        withLocale(Locale("fr", "FR")) { assertEquals(LANG_FR, S.resolve("system")) }
        withLocale(Locale("de", "DE")) { assertEquals(LANG_DE, S.resolve("system")) }
        withLocale(Locale("pt", "BR")) { assertEquals(LANG_PT, S.resolve("system")) }
    }

    @Test
    fun `a phone in a language we do not have falls back to english`() {
        withLocale(Locale("ja", "JP")) { assertEquals(LANG_EN, S.resolve("system")) }
        assertEquals(LANG_EN, S.resolve("klingon"))
    }

    @Test
    fun `switching the language changes what the screens read`() {
        S.apply("en")
        assertEquals("Settings", S.t("set.012"))

        S.apply("pl")
        assertEquals("Ustawienia", S.t("set.012"))

        S.apply("es")
        assertEquals("Ajustes", S.t("set.012"))

        S.apply("fr")
        assertEquals("Réglages", S.t("set.012"))

        S.apply("de")
        assertEquals("Einstellungen", S.t("set.012"))

        S.apply("pt")
        assertEquals("Configurações", S.t("set.012"))

        S.apply("ru")
        assertTrue(S.t("set.012").isNotEmpty())
    }

    @Test
    fun `pseudo locale preserves edge whitespace and substitution tokens`() {
        val result = pseudoLocalize("  {count} / 500  ")
        assertTrue(result.startsWith("  ⟦ {count} / 500"))
        assertTrue(result.endsWith(" ⟧  "))
        assertTrue("{count}" in result)
        assertTrue("500" in result)
    }

    @Test
    fun `non slavic languages use singular only for exactly one`() {
        try {
            S.apply("en")
            assertEquals("card", quantityWord(1, "deck.014", "deck.015", "deck.016", "deck.017"))
            assertEquals("cards", quantityWord(21, "deck.014", "deck.015", "deck.016", "deck.017"))

            S.apply("pt")
            assertEquals("cartão", quantityWord(1, "deck.014", "deck.015", "deck.016", "deck.017"))
            assertEquals("cartões", quantityWord(21, "deck.014", "deck.015", "deck.016", "deck.017"))
        } finally {
            S.apply("ru")
        }
    }
}
