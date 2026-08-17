package dev.ikna.audio

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.file.Files

/**
 * Unpacking a model, and saying so while it happens.
 *
 * These tests exist because of one bug report and one sentence in it: "it shows
 * that it copied 1 file and nothing else happens". Nothing was broken -- the app
 * was writing a three-hundred-megabyte file and had no way to mention it, since
 * progress was counted in finished files and there was only ever going to be one
 * of those. So the first test here is not about correctness of the bytes. It is
 * about whether anything is said while they are moving.
 */
class VoiceUnpackTest {

    @Test
    fun progressIsReportedFromInsideOneLargeFile() {
        val big = ByteArray(4 * 1024 * 1024)
        val archive = tarOf(
            listOf("model.onnx" to big, "tokens.txt" to "a b c".toByteArray()),
            compressed = false,
        )
        val dest = tempDir()

        val seen = mutableListOf<Pair<Int, Long>>()
        val files = VoiceArchive.unpack(
            source = ByteArrayInputStream(archive),
            dest = dest,
            compressed = false,
            notifyEvery = 256L * 1024L,
        ) { done, bytes -> seen.add(done to bytes) }

        assertEquals(2, files)
        assertEquals(big.size.toLong(), File(dest, "model.onnx").length())

        // Reports made while no file had been finished yet. Before this change
        // there were none: the first word came after the whole model was on disk.
        assertTrue(seen.count { it.first == 0 } >= 2)

        // And the number only ever goes up, because a bar that goes backwards is
        // worse than no bar.
        assertEquals(seen.map { it.second }.sorted(), seen.map { it.second })
    }

    @Test
    fun aBzip2ArchiveStillArrivesIntact() {
        val body = "kokoro tokens".repeat(400).toByteArray()
        val archive = tarOf(
            listOf("kokoro/tokens.txt" to body, "kokoro/voices.bin" to byteArrayOf(1, 2, 3)),
            compressed = true,
        )
        val dest = tempDir()

        var last = 0L
        val files = VoiceArchive.unpack(
            source = ByteArrayInputStream(archive),
            dest = dest,
            compressed = true,
        ) { _, bytes -> last = bytes }

        assertEquals(2, files)
        assertEquals(body.size.toLong(), File(dest, "kokoro/tokens.txt").length())
        assertEquals(3L, File(dest, "kokoro/voices.bin").length())
        assertTrue(last > 0L)
    }

    @Test
    fun aPercentageIsOnlyOfferedWhenThereIsATotal() {
        // No total means no honest percentage, and the screen falls back to
        // counting files rather than inventing a bar.
        assertEquals(-1, VoiceInstallProgress(files = 3, bytes = 900L, total = 0L).percent)
        assertEquals(25, VoiceInstallProgress(files = 1, bytes = 50L, total = 200L).percent)

        // A compressed archive can hand out more bytes than it weighs. Past the
        // end is still the end.
        assertEquals(100, VoiceInstallProgress(files = 4, bytes = 500L, total = 200L).percent)
    }

    // ---- helpers ----------------------------------------------------------

    private fun tarOf(files: List<Pair<String, ByteArray>>, compressed: Boolean): ByteArray {
        val raw = ByteArrayOutputStream()
        val sink: OutputStream = if (compressed) BZip2CompressorOutputStream(raw) else raw
        TarArchiveOutputStream(sink).use { tar ->
            for ((name, body) in files) {
                val entry = TarArchiveEntry(name)
                entry.size = body.size.toLong()
                tar.putArchiveEntry(entry)
                tar.write(body)
                tar.closeArchiveEntry()
            }
        }
        return raw.toByteArray()
    }

    private fun tempDir(): File = Files.createTempDirectory("ikna-voice").toFile()
}
