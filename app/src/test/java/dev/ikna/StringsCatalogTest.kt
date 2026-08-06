package dev.ikna

import dev.ikna.ui.text.LANG_EN
import dev.ikna.ui.text.LANG_PL
import dev.ikna.ui.text.LANG_RU
import dev.ikna.ui.text.S
import dev.ikna.ui.text.STRINGS_EN
import dev.ikna.ui.text.STRINGS_PL
import dev.ikna.ui.text.STRINGS_RU
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The interface is written three times over, and a missing line is invisible
 * until someone is standing in front of it in Warsaw. These tests are the only
 * place where all three copies are laid next to each other, so a key added to
 * one language and forgotten in another fails the build instead of shipping an
 * empty label.
 */
class StringsCatalogTest {

    @Test
    fun `every russian line is also written in english and polish`() {
        val missingEn = STRINGS_RU.keys.filterNot { it in STRINGS_EN }
        val missingPl = STRINGS_RU.keys.filterNot { it in STRINGS_PL }

        assertEquals("english is missing these keys: $missingEn", emptyList<String>(), missingEn)
        assertEquals("polish is missing these keys: $missingPl", emptyList<String>(), missingPl)
    }

    @Test
    fun `no translation carries a key russian does not have`() {
        val strays = (STRINGS_EN.keys + STRINGS_PL.keys).filterNot { it in STRINGS_RU }.sorted()

        assertEquals("keys without a russian original: $strays", emptyList<String>(), strays)
    }

    @Test
    fun `nothing is blank`() {
        val blank = (STRINGS_RU + STRINGS_EN + STRINGS_PL).filterValues { it.isEmpty() }.keys

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
        }
    }

    @Test
    fun `system follows the phone`() {
        withLocale(Locale("pl", "PL")) { assertEquals(LANG_PL, S.resolve("system")) }
        withLocale(Locale("en", "GB")) { assertEquals(LANG_EN, S.resolve("system")) }
        withLocale(Locale("ru", "RU")) { assertEquals(LANG_RU, S.resolve("system")) }
    }

    @Test
    fun `a phone in a language we do not have falls back to russian`() {
        withLocale(Locale("ja", "JP")) { assertEquals(LANG_RU, S.resolve("system")) }
        assertEquals(LANG_RU, S.resolve("klingon"))
    }

    @Test
    fun `switching the language changes what the screens read`() {
        S.apply("en")
        assertEquals("Settings", S.t("set.012"))

        S.apply("pl")
        assertEquals("Ustawienia", S.t("set.012"))

        S.apply("ru")
        assertTrue(S.t("set.012").isNotEmpty())
    }
}
