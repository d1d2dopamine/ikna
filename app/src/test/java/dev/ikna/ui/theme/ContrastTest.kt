package dev.ikna.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The colours, checked as numbers.
 *
 * Every palette the app ships is asserted to be readable here, so "the light
 * theme is not actually light" is a failing test rather than a complaint. The
 * ratios are WCAG 2.1; 4.5:1 is the line for body text.
 */
class ContrastTest {

    private val white = Color(0xFFFFFFFF)
    private val black = Color(0xFF000000)

    @Test
    fun `black on white is the maximum ratio`() {
        assertEquals(21.0, contrastRatio(black, white), 0.05)
    }

    @Test
    fun `a colour on itself is invisible`() {
        assertEquals(1.0, contrastRatio(white, white), 0.001)
        assertEquals(1.0, contrastRatio(Color(0xFF33469E), Color(0xFF33469E)), 0.001)
    }

    @Test
    fun `order does not matter`() {
        val a = Color(0xFF121110)
        val b = Color(0xFFEDE9E1)
        assertEquals(contrastRatio(a, b), contrastRatio(b, a), 0.0001)
    }

    @Test
    fun `the dark palette is readable`() {
        assertReadable("dark ink", DarkPalette.ink, DarkPalette.background)
        assertReadable("dark muted", DarkPalette.muted, DarkPalette.background)
        assertReadable("dark accent", DarkPalette.accent, DarkPalette.background)
    }

    @Test
    fun `the light palette is readable`() {
        assertReadable("light ink", LightPalette.ink, LightPalette.background)
        assertReadable("light muted", LightPalette.muted, LightPalette.background)
        assertReadable("light accent", LightPalette.accent, LightPalette.background)
    }

    /**
     * The light theme has to be light and the dark one dark. This is what the
     * status bar icons are chosen from, so getting it wrong means an invisible
     * clock rather than an ugly one.
     */
    @Test
    fun `each palette knows which one it is`() {
        assertTrue("light palette reported as dark", LightPalette.light)
        assertTrue("dark palette reported as light", !DarkPalette.light)
        assertTrue(isLight(white))
        assertTrue(!isLight(black))
    }

    /**
     * A light theme that is merely "less dark" is the thing that was wrong
     * before: it has to be genuinely bright, not an inverted dark theme.
     *
     * The line is 0.80 rather than 0.85 because the light version of a palette is
     * tinted paper, not white — the default one measures 0.84. That is the point
     * of it: a white light theme and a coloured dark theme are two apps, and the
     * hue has to survive the lamp being turned on. Anything below 0.80 stops being
     * paper and starts being a dim room.
     */
    @Test
    fun `the light background is actually bright`() {
        assertTrue(relativeLuminance(LightPalette.background) > 0.80)
        assertTrue(relativeLuminance(DarkPalette.background) < 0.05)
    }

    @Test
    fun `hex parsing accepts what a person types`() {
        val expected = Color(0xFFFBFAF8)
        assertEquals(expected, parseHexColor("FBFAF8"))
        assertEquals(expected, parseHexColor("fbfaf8"))
        assertEquals(expected, parseHexColor("#FBFAF8"))
        assertEquals(expected, parseHexColor("  #fbfaf8  "))
    }

    @Test
    fun `half typed hex does not apply`() {
        assertNull(parseHexColor(""))
        assertNull(parseHexColor("#"))
        assertNull(parseHexColor("12345"))
        assertNull(parseHexColor("1234567"))
        assertNull(parseHexColor("zzzzzz"))
        assertNull(parseHexColor("12 34 56"))
    }

    @Test
    fun `a colour survives the trip through the text field`() {
        assertEquals("1B1813", hexOf(parseHexColor("1b1813")!!))
        assertEquals("B8431F", hexOf(LightPalette.accent))
        assertEquals("17100C", hexOf(DarkPalette.background))
    }

    @Test
    fun `the ratio reads as a ratio`() {
        assertEquals("21.0:1", ratioText(contrastRatio(black, white)))
        assertEquals("1.0:1", ratioText(1.0))
    }

    private fun assertReadable(what: String, fg: Color, bg: Color) {
        val ratio = contrastRatio(fg, bg)
        assertTrue(
            what + " is " + ratioText(ratio) + ", below " + MIN_READABLE_CONTRAST,
            ratio >= MIN_READABLE_CONTRAST
        )
    }
}
