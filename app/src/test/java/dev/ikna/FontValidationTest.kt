package dev.ikna

import dev.ikna.data.prefs.FontStore
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * The font check is the only thing standing between a wrong file and an app that
 * cannot be opened: Compose parses a font at layout time, so a bad one throws on
 * every screen, including the settings screen with the reset button. Everything
 * that is not a usable single font must be refused here, with a sentence saying
 * why.
 */
class FontValidationTest {

    @Test
    fun `a well formed sfnt is accepted`() {
        assertNull(FontStore.validate(font(tags = listOf("cmap", "glyf"))))
    }

    @Test
    fun `an OpenType CFF font is accepted`() {
        assertNull(FontStore.validate(font(version = 0x4F54544F, tags = listOf("cmap", "CFF "))))
    }

    @Test
    fun `a woff2 file is refused by name`() {
        val problem = FontStore.validate(font(version = 0x774F4632, tags = listOf("cmap", "glyf")))
        assertNotNull(problem)
        assertTrue(problem!!.contains("woff"))
    }

    @Test
    fun `a font collection is refused by name`() {
        val problem = FontStore.validate(font(version = 0x74746366, tags = listOf("cmap", "glyf")))
        assertNotNull(problem)
        assertTrue(problem!!.contains(".ttc"))
    }

    @Test
    fun `a jpeg is not a font`() {
        assertNotNull(FontStore.validate(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0, 0, 0, 0, 0, 0, 0)))
    }

    @Test
    fun `an empty file is not a font`() {
        assertNotNull(FontStore.validate(ByteArray(0)))
    }

    @Test
    fun `a font without a character map is refused`() {
        assertNotNull(FontStore.validate(font(tags = listOf("head", "glyf"))))
    }

    @Test
    fun `a font without outlines is refused`() {
        assertNotNull(FontStore.validate(font(tags = listOf("cmap", "head"))))
    }

    @Test
    fun `a truncated table is refused`() {
        val bytes = font(tags = listOf("cmap", "glyf"))
        assertNotNull(FontStore.validate(bytes.copyOf(bytes.size - 40)))
    }

    /** Builds the smallest thing that has a real sfnt header and table directory. */
    private fun font(version: Long = 0x00010000, tags: List<String>): ByteArray {
        val payload = ByteArray(64) { 7 }
        val directoryEnd = 12 + tags.size * 16
        val out = ByteArrayOutputStream()

        out.write(u32(version))
        out.write(u16(tags.size))
        out.write(u16(0))
        out.write(u16(0))
        out.write(u16(0))

        tags.forEachIndexed { index, tag ->
            out.write(tag.toByteArray(Charsets.US_ASCII))
            out.write(u32(0))
            out.write(u32((directoryEnd + index * payload.size).toLong()))
            out.write(u32(payload.size.toLong()))
        }
        repeat(tags.size) { out.write(payload) }
        return out.toByteArray()
    }

    private fun u16(value: Int): ByteArray =
        byteArrayOf(((value shr 8) and 0xFF).toByte(), (value and 0xFF).toByte())

    private fun u32(value: Long): ByteArray = byteArrayOf(
        ((value shr 24) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )
}
