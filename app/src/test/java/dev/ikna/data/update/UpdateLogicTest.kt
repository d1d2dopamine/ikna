package dev.ikna.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateLogicTest {

    @Test
    fun `a later release is offered`() {
        assertTrue(isNewer("0.4.0 press", "v0.5.0-press"))
        assertTrue(isNewer("0.4.0 press", "v0.4.1-press"))
    }

    @Test
    fun `the running version is not an update`() {
        assertFalse(isNewer("0.4.0 press", "v0.4.0-press"))
    }

    @Test
    fun `an older release is not an update`() {
        // The epoch word is not compared, so a proof tag left on the page
        // cannot pull a press install backwards.
        assertFalse(isNewer("0.4.0 press", "v0.3.0-press"))
        assertFalse(isNewer("0.4.0 press", "v0.5.0-proof"))
    }

    @Test
    fun `ten is later than nine`() {
        assertTrue(isNewer("0.9.0 press", "v0.10.0-press"))
        assertFalse(isNewer("0.10.0 press", "v0.9.0-press"))
    }

    @Test
    fun `nonsense on either side means no update`() {
        assertFalse(isNewer("0.4.0 press", "nightly"))
        assertFalse(isNewer("", "v0.5.0-press"))
        assertNull(versionNumbers("v"))
    }

    @Test
    fun `a phone without a 64 bit abi is given the small apk`() {
        val assets = listOf(
            UpdateAsset("ikna-v0.5.0-press.apk", "a", 40_000_000),
            UpdateAsset("ikna-v0.5.0-press-32bit.apk", "b", 30_000_000)
        )
        assertEquals("b", pickAsset(assets, has64Bit = false)?.url)
        assertEquals("a", pickAsset(assets, has64Bit = true)?.url)
    }

    @Test
    fun `a release with no apk offers nothing`() {
        val assets = listOf(UpdateAsset("mapping.txt", "a", 10))
        assertNull(pickAsset(assets, has64Bit = true))
    }

    @Test
    fun `a size is rounded to a tenth of a megabyte`() {
        assertEquals("1.0", megabytes(1_048_576))
        assertEquals("25.7", megabytes(26_952_499))
        assertEquals("?", megabytes(0))
    }

    @Test
    fun `the badge block at the top of a release is not shown to the reader`() {
        val body = listOf(
            "<p align=\"center\">",
            "  <a href=\"x\"><img src=\"y\" alt=\"Android APK\"></a>",
            "</p>",
            "---",
            "## Pasting a whole deck",
            "**Paste from clipboard** reads the clipboard directly.",
            "Full list: [CHANGELOG.md](https://example.com/CHANGELOG.md)"
        ).joinToString("\n")
        val notes = tidyNotes(body)
        assertFalse(notes.contains("<"))
        assertFalse(notes.contains("**"))
        assertFalse(notes.contains("https://"))
        assertTrue(notes.startsWith("Pasting a whole deck"))
        assertTrue(notes.contains("Full list: CHANGELOG.md"))
    }

    @Test
    fun `long notes are cut on a line break`() {
        val line = "a".repeat(60)
        val notes = tidyNotes((1..80).joinToString("\n") { line }, maxChars = 200)
        assertTrue(notes.length <= 220)
        assertTrue(notes.endsWith("\u2026"))
    }
}
