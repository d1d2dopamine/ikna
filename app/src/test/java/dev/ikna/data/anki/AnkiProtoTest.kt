package dev.ikna.data.anki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The blobs these tests build are the shape Anki writes: a notetype config whose
 * kind says cloze, and a template config whose two sides are strings. Anki's
 * schema is not ours, so the interesting cases are the malformed ones -- what a
 * future release, or a damaged file, could hand this reader.
 */
class AnkiProtoTest {

    @Test
    fun `reads the kind of a notetype`() {
        assertEquals(1L, AnkiProto.varint(varintField(1, 1L), 1))
    }

    @Test
    fun `reads both sides of a template`() {
        val blob = bytesField(1, "{{Front}}") + bytesField(2, "{{FrontSide}}{{Back}}")
        assertEquals("{{Front}}", AnkiProto.text(blob, 1))
        assertEquals("{{FrontSide}}{{Back}}", AnkiProto.text(blob, 2))
    }

    @Test
    fun `skips every wire type on the way to the field asked for`() {
        val blob = fixed64Field(3) + varintField(4, 300L) + bytesField(5, "ignored") +
            fixed32Field(6) + varintField(1, 1L)
        assertEquals(1L, AnkiProto.varint(blob, 1))
    }

    @Test
    fun `a field the message does not carry is absent, not false`() {
        // A normal notetype omits kind entirely; only cloze writes it.
        assertNull(AnkiProto.varint(varintField(7, 9L), 1))
    }

    @Test
    fun `a field of the wrong wire type is not read`() {
        assertNull(AnkiProto.varint(bytesField(1, "cloze"), 1))
        assertNull(AnkiProto.text(varintField(1, 1L), 1))
    }

    @Test
    fun `a length that runs past the end of the blob gives up`() {
        val blob = tag(1, 2) + varint(100L) + byteArrayOf(1, 2, 3)
        assertNull(AnkiProto.text(blob, 1))
    }

    @Test
    fun `a varint with no final byte gives up`() {
        val blob = tag(1, 0) + byteArrayOf(0x80.toByte(), 0x80.toByte())
        assertNull(AnkiProto.varint(blob, 1))
    }

    @Test
    fun `a group is refused rather than guessed at`() {
        val blob = tag(9, 3) + varintField(1, 1L)
        assertNull(AnkiProto.varint(blob, 1))
    }

    @Test
    fun `a large varint survives the shifting`() {
        assertEquals(1_600_000_000_001L, AnkiProto.varint(varintField(1, 1_600_000_000_001L), 1))
    }

    @Test
    fun `an empty blob says nothing at all`() {
        assertNull(AnkiProto.varint(ByteArray(0), 1))
        assertNull(AnkiProto.text(ByteArray(0), 1))
    }

    @Test
    fun `text is decoded as UTF-8`() {
        assertEquals("डेक — 日本語", AnkiProto.text(bytesField(2, "डेक — 日本語"), 2))
    }

    private fun varint(value: Long): ByteArray {
        var rest = value
        val out = ArrayList<Byte>()
        while (true) {
            val byte = (rest and 0x7FL).toInt()
            rest = rest ushr 7
            if (rest == 0L) {
                out.add(byte.toByte())
                break
            }
            out.add((byte or 0x80).toByte())
        }
        return out.toByteArray()
    }

    private fun tag(number: Int, wire: Int): ByteArray =
        varint((number.toLong() shl 3) or wire.toLong())

    private fun varintField(number: Int, value: Long): ByteArray = tag(number, 0) + varint(value)

    private fun bytesField(number: Int, value: String): ByteArray {
        val payload = value.toByteArray(Charsets.UTF_8)
        return tag(number, 2) + varint(payload.size.toLong()) + payload
    }

    private fun fixed64Field(number: Int): ByteArray = tag(number, 1) + ByteArray(8)

    private fun fixed32Field(number: Int): ByteArray = tag(number, 5) + ByteArray(4)
}
