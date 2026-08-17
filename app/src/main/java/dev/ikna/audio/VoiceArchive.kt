package dev.ikna.audio

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
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
     * How much is read and written at a time.
     *
     * A megabyte rather than the eight kilobytes the standard library uses by
     * default. At that size a three-hundred-megabyte model is forty thousand
     * trips through the document provider and forty thousand writes, and most
     * of the minutes people spent watching a still screen were spent in them.
     */
    private const val BUFFER = 1 shl 20

    /** How much of an archive may go by before the screen is told about it. */
    const val NOTIFY_EVERY = 512L * 1024L

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
     * @param notifyEvery how many bytes of the archive may pass between two words
     *   about it: small enough that the screen keeps moving, large enough that
     *   the unpacking is not spent talking about itself.
     * @param onProgress called with the files finished so far and the bytes of
     *   [source] consumed so far -- including from the middle of a single file,
     *   which is the entire point of it. A speech model is one file of a few
     *   hundred megabytes and a handful of crumbs, so counting finished files
     *   leaves the screen reading "1" for the whole unpacking. That is how a
     *   working app comes to look like a hung one, and it is what this replaced.
     * @return how many files were written. Zero means the archive held nothing
     *   this app would keep, which is a refusal rather than a crash.
     */
    fun unpack(
        source: InputStream,
        dest: File,
        compressed: Boolean,
        notifyEvery: Long = NOTIFY_EVERY,
        onProgress: (Int, Long) -> Unit = { _, _ -> },
    ): Int {
        dest.mkdirs()
        val counted = CountingStream(source)
        val buffered = BufferedInputStream(counted, BUFFER)
        // `true` for the second argument: some archives are several bzip2 streams
        // written one after another, and without it only the first is read -- which
        // looks exactly like a model with half its files missing.
        val bytes: InputStream =
            if (compressed) BZip2CompressorInputStream(buffered, true) else buffered

        var written = 0
        var announced = 0L
        val buffer = ByteArray(BUFFER)
        TarArchiveInputStream(bytes).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                val target = safeTarget(dest, entry.name) ?: continue
                if (entry.isDirectory) {
                    target.mkdirs()
                    continue
                }
                target.parentFile?.mkdirs()
                BufferedOutputStream(FileOutputStream(target), BUFFER).use { output ->
                    while (true) {
                        val read = tar.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)

                        // Said from inside the file rather than after it. bzip2 is
                        // decompressed in Java here -- Android has no native one --
                        // and a few megabytes a second over a large model is minutes
                        // during which this loop is the only thing that knows the app
                        // is alive.
                        val seen = counted.count
                        if (seen - announced >= notifyEvery) {
                            announced = seen
                            onProgress(written, seen)
                        }
                    }
                }
                written += 1
                announced = counted.count
                onProgress(written, announced)
            }
        }
        return written
    }

    /**
     * The archive, counting what has been taken out of it.
     *
     * Progress is told in bytes of the file the user downloaded, because that is
     * the only number they can check it against. Counting the decompressed side
     * instead would mean a percentage of a total nobody knows until the end.
     */
    private class CountingStream(private val inner: InputStream) : InputStream() {

        var count: Long = 0L
            private set

        override fun read(): Int {
            val byte = inner.read()
            if (byte >= 0) count += 1L
            return byte
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val read = inner.read(b, off, len)
            if (read > 0) count += read.toLong()
            return read
        }

        override fun skip(n: Long): Long {
            val skipped = inner.skip(n)
            if (skipped > 0L) count += skipped
            return skipped
        }

        override fun available(): Int = inner.available()

        override fun close() {
            inner.close()
        }
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
