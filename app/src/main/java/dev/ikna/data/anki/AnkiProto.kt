package dev.ikna.data.anki

/**
 * Just enough Protocol Buffers to read two things out of a modern Anki
 * collection: whether a notetype is cloze, and the two sides of a template.
 *
 * Anki stopped keeping notetypes as JSON in one column and started keeping them
 * in their own tables, with the parts that are not columns encoded as protobuf
 * blobs. Reading those blobs is the whole difference between "this file is not
 * supported yet" and an import that works.
 *
 * This is deliberately not a protobuf library. It walks the tag/value pairs of a
 * message, skips every field it was not asked for, and returns null the moment
 * anything does not add up -- a truncated blob, a length that runs past the end,
 * a group (wire types 3 and 4, removed from the language long ago). Callers
 * treat null as "this notetype did not say", which every caller here already had
 * to handle for collections that are simply missing a notetype.
 *
 * The alternative was a code-generated protobuf runtime for two integers and two
 * strings, and the schema they come from is Anki's, so it can change under us in
 * a release we do not control. Something that cannot crash and cannot be wrong
 * in an interesting way is worth more here than something complete.
 */
internal object AnkiProto {

    /** The varint field [field], or null when it is absent or unreadable. */
    fun varint(blob: ByteArray, field: Int): Long? {
        val found = find(blob, field) ?: return null
        return if (found.wire == WIRE_VARINT) found.value else null
    }

    /** The length-delimited field [field] read as UTF-8 text. */
    fun text(blob: ByteArray, field: Int): String? {
        val found = find(blob, field) ?: return null
        if (found.wire != WIRE_BYTES) return null
        return String(blob, found.start, found.length, Charsets.UTF_8)
    }

    private const val WIRE_VARINT = 0
    private const val WIRE_FIXED64 = 1
    private const val WIRE_BYTES = 2
    private const val WIRE_FIXED32 = 5

    private class Found(val wire: Int, val value: Long, val start: Int, val length: Int)

    private class Step(val value: Long, val next: Int)

    private fun find(blob: ByteArray, field: Int): Found? {
        var at = 0
        while (at < blob.size) {
            val tag = varintAt(blob, at) ?: return null
            at = tag.next
            val number = (tag.value ushr 3).toInt()
            when (val wire = (tag.value and 0x7L).toInt()) {
                WIRE_VARINT -> {
                    val value = varintAt(blob, at) ?: return null
                    if (number == field) return Found(wire, value.value, at, value.next - at)
                    at = value.next
                }
                WIRE_FIXED64 -> {
                    if (at + 8 > blob.size) return null
                    if (number == field) return Found(wire, 0L, at, 8)
                    at += 8
                }
                WIRE_BYTES -> {
                    val header = varintAt(blob, at) ?: return null
                    val length = header.value.toInt()
                    if (length < 0 || header.next + length > blob.size) return null
                    if (number == field) return Found(wire, 0L, header.next, length)
                    at = header.next + length
                }
                WIRE_FIXED32 -> {
                    if (at + 4 > blob.size) return null
                    if (number == field) return Found(wire, 0L, at, 4)
                    at += 4
                }
                else -> return null
            }
        }
        return null
    }

    private fun varintAt(blob: ByteArray, from: Int): Step? {
        var result = 0L
        var shift = 0
        var at = from
        while (at < blob.size && shift <= 63) {
            val byte = blob[at].toInt()
            result = result or ((byte and 0x7F).toLong() shl shift)
            at++
            if (byte and 0x80 == 0) return Step(result, at)
            shift += 7
        }
        return null
    }
}
