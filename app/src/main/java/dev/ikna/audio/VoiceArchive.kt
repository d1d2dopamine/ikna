package dev.ikna.audio

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.Locale

/*
 * Opening the archive a speech model was published as.
 *
 * Every model on the sherpa-onnx releases page is a `.tar.bz2`, and Android can
 * open neither half of that: not the bzip2 wrapper, not the tar inside it. Until
 * 0.5.0 the app said so and asked the user to go and find a file manager that
 * could -- which is three apps deep into a flashcard app that has not shown a
 * card yet, and where most attempts stopped.
 *
 * The bytes were never the difficulty. This object is the whole of what was
 * missing, and it is deliberately small: one format family, no writing, no
 * guessing about what is inside. What comes out is examined by
 * VoiceModelLayout like any picked folder, so an archive of holiday photos is
 * refused by the same sentence as a folder of them.
 */
object VoiceArchive {

    /** The suffixes worth opening at all. */
    private val SUFFIXES = listOf(".tar.bz2", ".tbz2", ".tbz", ".tar", ".bz2")

    /**
     * Whether this name looks like an archive this object can open.
     *
     * The name is all there is to go on. The file picker is opened for any file
     * rather than for bzip2 specifically, because phones disagree about what a
     * `.tar.bz2` is called and several answer "nothing at all" -- which is how a
     * picker ends up greying out the very file it was opened to choose.
     */
    fun looksLikeArchive(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return SUFFIXES.any { lower.endsWith(it) }
    }

    /** Whether the tar inside is wrapped in bzip2, or lying there plain. */
    fun isCompressed(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return !lower.endsWith(".tar")
    }

    /**
     * The folder name to judge the contents by, taken from the archive's own name.
     *
     * Which language a model speaks is written in its name and nowhere else, so an
     * archive that holds its files at the top level still gets to say
     * `vits-piper-ru_RU-dmitri-medium` rather than nothing.
     */
    fun folderName(archiveName: String): String {
        var name = archiveName
        for (suffix in SUFFIXES) {
            if (name.lowercase(Locale.ROOT).endsWith(suffix)) {
                name = name.dropLast(suffix.length)
                break
            }
        }
        return name.substringAfterLast('/').ifEmpty { archiveName }
    }

    /**
     * Unpacks [source] into [dest].
     *
     * @param compressed whether to read bzip2 first. A plain `.tar` is read as it
     *   is rather than being refused for the sake of one flag.
     * @param onFile called with the number of files written so far, because a
     *   sixty-megabyte model takes long enough that a still screen looks broken.
     * @return how many files were written. Zero means the archive held nothing
     *   this app would keep, which is a refusal rather than a crash.
     */
    fun unpack(
        source: InputStream,
        dest: File,
        compressed: Boolean,
        onFile: (Int) -> Unit = {},
    ): Int {
        dest.mkdirs()
        val buffered = BufferedInputStream(source)
        // `true` for the second argument: some archives are several bzip2 streams
        // written one after another, and without it only the first is read -- which
        // looks exactly like a model with half its files missing.
        val bytes: InputStream =
            if (compressed) BZip2CompressorInputStream(buffered, true) else buffered

        var written = 0
        TarArchiveInputStream(bytes).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                val target = safeTarget(dest, entry.name) ?: continue
                if (entry.isDirectory) {
                    target.mkdirs()
                    continue
                }
                target.parentFile?.mkdirs()
                target.outputStream().use { output -> tar.copyTo(output) }
                written += 1
                onFile(written)
            }
        }
        return written
    }

    /**
     * Where an entry named [name] is allowed to land, or null if nowhere.
     *
     * Nothing inside an archive is trusted. An entry called
     * `../../databases/ikna.db` would, unpacked naively, overwrite the review log
     * -- the one thing in this app that is never allowed to be lost -- so every
     * path is resolved and anything pointing outside [root] is skipped in silence.
     */
    fun safeTarget(root: File, name: String): File? {
        val parts = name.split('/', '\\').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty()) return null
        if (parts.any { it == ".." }) return null
        if (name.startsWith("/") || name.startsWith("\\")) return null

        val target = File(root, parts.joinToString(File.separator))
        val here = runCatching { root.canonicalPath }.getOrNull() ?: return null
        val there = runCatching { target.canonicalPath }.getOrNull() ?: return null
        if (there != here && !there.startsWith(here + File.separator)) return null
        return target
    }
}
