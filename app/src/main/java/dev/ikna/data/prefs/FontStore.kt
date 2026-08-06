package dev.ikna.data.prefs

import dev.ikna.ui.text.S

import android.content.Context
import java.io.File
import java.io.InputStream

/*
 * Installing a font the user picked from their own storage.
 *
 * The whole reason this file exists is that a bad font file is not a cosmetic
 * problem. Compose does not parse a font when it is loaded, it parses it when it
 * first lays out text — so a corrupt or wrongly-named file does not fail on the
 * settings screen where it was chosen, it throws on the next frame of every
 * screen, including the one with the button that would undo it. That is an app
 * that has to be reinstalled to be recovered.
 *
 * So the file is validated here, before it is stored, by actually reading the
 * sfnt table directory rather than trusting the extension or the MIME type the
 * picker reported. If the tables do not line up, nothing is written and the user
 * gets a sentence saying what was wrong with the file.
 */
object FontStore {

    const val FILE_NAME = "content-font.ttf"

    /** Fonts are small; a cap keeps a wrongly picked video out of app storage. */
    private const val MAX_BYTES = 12 * 1024 * 1024

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun exists(context: Context): Boolean = file(context).let { it.exists() && it.length() > 0 }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    /**
     * Copies the picked file into app storage if it is a real font.
     *
     * @return null on success, or a Russian explanation of why it was rejected.
     */
    fun install(context: Context, input: InputStream): String? {
        val bytes = runCatching { input.readBytes() }.getOrNull()
            ?: return S.t("font.001")

        if (bytes.size > MAX_BYTES) return S.t("font.002")

        val problem = validate(bytes)
        if (problem != null) return problem

        val target = file(context)
        val tmp = File(context.filesDir, FILE_NAME + ".part")
        return runCatching {
            tmp.writeBytes(bytes)
            if (target.exists()) target.delete()
            if (tmp.renameTo(target)) {
                null
            } else {
                tmp.delete()
                S.t("font.003")
            }
        }.getOrElse {
            tmp.delete()
            S.t("font.004")
        }
    }

    /**
     * Reads the sfnt header and the table directory.
     *
     * @return null when the file is a usable single font, otherwise the reason.
     */
    fun validate(bytes: ByteArray): String? {
        if (bytes.size < 12) return S.t("font.005")

        when (u32(bytes, 0)) {
            0x00010000L, 0x74727565L /* true */, 0x4F54544FL /* OTTO */ -> Unit
            0x74746366L /* ttcf */ ->
                return S.t("font.006")
            0x774F4632L /* wOF2 */, 0x774F4646L /* wOFF */ ->
                return S.t("font.007")
            else -> return S.t("font.008")
        }

        val tableCount = u16(bytes, 4)
        if (tableCount <= 0 || tableCount > 512) return S.t("font.009")

        val directoryEnd = 12 + tableCount * 16
        if (directoryEnd > bytes.size) return S.t("font.010")

        var hasCmap = false
        var hasOutlines = false

        for (i in 0 until tableCount) {
            val record = 12 + i * 16
            val name = String(bytes, record, 4, Charsets.US_ASCII)
            val offset = u32(bytes, record + 8)
            val length = u32(bytes, record + 12)
            if (offset < 0 || length < 0 || offset + length > bytes.size) {
                return S.t("font.011")
            }
            when (name) {
                "cmap" -> hasCmap = true
                "glyf", "CFF ", "CFF2" -> hasOutlines = true
            }
        }

        if (!hasCmap) return S.t("font.012")
        if (!hasOutlines) return S.t("font.013")
        return null
    }

    private fun u16(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

    private fun u32(b: ByteArray, at: Int): Long =
        ((b[at].toLong() and 0xFF) shl 24) or
            ((b[at + 1].toLong() and 0xFF) shl 16) or
            ((b[at + 2].toLong() and 0xFF) shl 8) or
            (b[at + 3].toLong() and 0xFF)
}
