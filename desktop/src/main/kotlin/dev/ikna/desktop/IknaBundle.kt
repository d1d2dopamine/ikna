package dev.ikna.desktop

import dev.ikna.data.export.ReviewRecord
import dev.ikna.data.export.SettingsBackup
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** What a saved file ended up holding. */
data class BundleWrite(
    val path: String,
    val reviews: Int,
    val decks: Int,
    val bytes: Long
)

/** What a picked file turned out to be, and what it changed. */
data class BundleRead(
    val kind: BundleKind,
    val imported: Int,
    val skipped: Int,
    val replayed: Int,
    val decks: Int,
    val settings: Boolean
)

/**
 * Which of the shapes a picked file was.
 *
 * The three single-file shapes are not a courtesy: the phone writes exactly
 * those two files, and a deck is a plain text file people already have. Making
 * the restore recognise them means the phone needs no changes at all to take
 * part in this.
 */
enum class BundleKind { BUNDLE, REVIEWS, SETTINGS, DECK, UNKNOWN }

/**
 * Everything this install cannot regenerate, in one file.
 *
 * The file is a zip holding the review log, the settings and the text of every
 * deck. Nothing here is a new format: the log is the same JSONL the phone has
 * been writing weekly, and the settings are the same object the phone saves
 * beside it. The zip is only an envelope, so that "save my ikna" is one action
 * and one file rather than a folder the user has to keep together.
 *
 * Conflicts are absent by construction rather than resolved. The log is
 * append-only and every answer is identified by card, level and timestamp, so
 * merging two logs is a set union: an answer already present is dropped, an
 * answer missing is added, and the schedule is recomputed from the merged log
 * afterwards. That is why the same file can travel phone to computer and back
 * indefinitely without anyone deciding which side wins -- neither side wins,
 * both sides' answers survive.
 */
object IknaBundle {

    const val EXTENSION = ".ikna"

    private const val ENTRY_REVIEWS = "reviews.jsonl"
    private const val ENTRY_SETTINGS = "settings.json"
    private const val ENTRY_DECKS = "decks/"

    // A log of four months is well under a megabyte, so these are guards
    // against a malformed or hostile file, not real ceilings.
    private const val MAX_ENTRY_BYTES = 64L * 1024L * 1024L
    private const val MAX_ENTRIES = 5_000
    private const val COPY_BUFFER = 64 * 1024

    /** Default file name, dated so that successive saves do not overwrite. */
    fun defaultName(now: Long = System.currentTimeMillis()): String =
        "ikna-" + stamp(now) + EXTENSION

    /**
     * Writes the whole install into one file.
     *
     * The log is written first and the decks last, which is also the order the
     * restore needs them in; a reader that stops early still has the part that
     * cannot be regenerated.
     */
    suspend fun write(container: DesktopContainer, target: File): BundleWrite {
        val reviews = container.db.reviewDao().all()
        val settings = SettingsBackup.encode(container.settings.current())
        val decks = container.deckRepository.decks()

        target.parentFile?.mkdirs()
        var deckCount = 0
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(ENTRY_REVIEWS))
            for (review in reviews) {
                val line = ReviewRecord.json.encodeToString(
                    ReviewRecord.serializer(),
                    ReviewRecord.of(review)
                )
                zip.write((line + "\n").toByteArray(Charsets.UTF_8))
            }
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(ENTRY_SETTINGS))
            zip.write(settings.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            for (deck in decks) {
                val body = runCatching {
                    container.deckRepository.exportText(deck.id)
                }.getOrNull().orEmpty()
                if (body.isBlank()) continue
                zip.putNextEntry(ZipEntry(ENTRY_DECKS + safeName(deck.id) + ".txt"))
                zip.write(body.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                deckCount += 1
            }
        }

        return BundleWrite(
            path = target.absolutePath,
            reviews = reviews.size,
            decks = deckCount,
            bytes = target.length()
        )
    }

    /**
     * Writes the two files the phone's own restore reads, under the names it
     * expects.
     *
     * This is the other direction of the sync, and it costs nothing: the phone
     * already knows how to read a review log and a settings object, so the
     * computer writing them is enough. No change on the phone, no new format
     * for it to learn, and nothing to go wrong on the side I am not allowed to
     * break.
     */
    suspend fun writeForPhone(
        container: DesktopContainer,
        directory: File,
        now: Long = System.currentTimeMillis()
    ): List<String> {
        directory.mkdirs()
        val written = mutableListOf<String>()

        val reviews = container.db.reviewDao().all()
        if (reviews.isNotEmpty()) {
            val file = File(directory, "ikna-reviews-" + stamp(now) + ".jsonl")
            file.bufferedWriter().use { out ->
                for (review in reviews) {
                    out.write(
                        ReviewRecord.json.encodeToString(
                            ReviewRecord.serializer(),
                            ReviewRecord.of(review)
                        )
                    )
                    out.newLine()
                }
            }
            written += file.name
        }

        val settings = SettingsBackup.encode(container.settings.current())
        if (settings.isNotBlank()) {
            val file = File(directory, "ikna-settings-" + stamp(now) + ".json")
            file.writeText(settings)
            written += file.name
        }

        return written
    }

    /**
     * Reads a picked file, whatever of the four shapes it is.
     *
     * The zip signature decides rather than the extension: a file renamed on
     * the way through a chat app is still the file it was.
     */
    suspend fun read(container: DesktopContainer, source: File): BundleRead {
        val head = runCatching {
            source.inputStream().use { it.readNBytes(2) }
        }.getOrNull() ?: ByteArray(0)
        val isZip = head.size == 2 &&
            head[0] == 'P'.code.toByte() &&
            head[1] == 'K'.code.toByte()
        return if (isZip) readBundle(container, source) else readSingle(container, source)
    }

    private suspend fun readBundle(container: DesktopContainer, source: File): BundleRead {
        var reviewsText = ""
        var settingsText = ""
        val decks = mutableListOf<Pair<String, String>>()

        ZipFile(source).use { zip ->
            var seen = 0
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                seen += 1
                if (seen > MAX_ENTRIES) break
                if (entry.isDirectory) continue
                val leaf = entry.name.substringAfterLast('/')
                when {
                    entry.name.endsWith(ENTRY_REVIEWS) ->
                        reviewsText = text(zip.getInputStream(entry))
                    entry.name.endsWith(ENTRY_SETTINGS) ->
                        settingsText = text(zip.getInputStream(entry))
                    entry.name.startsWith(ENTRY_DECKS) && leaf.isNotBlank() ->
                        decks += leaf to text(zip.getInputStream(entry))
                }
            }
        }

        val deckCount = installDecks(container, decks)
        val restored = replay(container, reviewsText)
        val applied = applySettings(container, settingsText)
        container.learningRepository.invalidatePlan()

        return BundleRead(
            kind = BundleKind.BUNDLE,
            imported = restored?.imported ?: 0,
            skipped = restored?.skipped ?: 0,
            replayed = restored?.replayed ?: 0,
            decks = deckCount,
            settings = applied
        )
    }

    private suspend fun readSingle(container: DesktopContainer, source: File): BundleRead {
        if (source.length() > MAX_ENTRY_BYTES) return empty(BundleKind.UNKNOWN)
        val body = runCatching { source.readText() }.getOrNull()
            ?: return empty(BundleKind.UNKNOWN)
        if (body.isBlank()) return empty(BundleKind.UNKNOWN)

        if (SettingsBackup.looksLikeSettings(body)) {
            val applied = applySettings(container, body)
            return BundleRead(
                kind = if (applied) BundleKind.SETTINGS else BundleKind.UNKNOWN,
                imported = 0,
                skipped = 0,
                replayed = 0,
                decks = 0,
                settings = applied
            )
        }

        // A review log is one JSON object per line. Judged on the first real
        // line, because a deck text file never starts one.
        val first = body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim()
        if (first.startsWith("{")) {
            val restored = replay(container, body)
            container.learningRepository.invalidatePlan()
            return BundleRead(
                kind = if (restored == null) BundleKind.UNKNOWN else BundleKind.REVIEWS,
                imported = restored?.imported ?: 0,
                skipped = restored?.skipped ?: 0,
                replayed = restored?.replayed ?: 0,
                decks = 0,
                settings = false
            )
        }

        val installed = installDecks(container, listOf(source.name to body))
        container.learningRepository.invalidatePlan()
        return BundleRead(
            kind = if (installed > 0) BundleKind.DECK else BundleKind.UNKNOWN,
            imported = 0,
            skipped = 0,
            replayed = 0,
            decks = installed,
            settings = false
        )
    }

    private suspend fun installDecks(
        container: DesktopContainer,
        decks: List<Pair<String, String>>
    ): Int {
        var count = 0
        for ((name, body) in decks) {
            if (body.isBlank()) continue
            val ok = runCatching {
                container.deckRepository.importText(
                    fileName = name,
                    text = body,
                    fallbackTitle = name.removeSuffix(".txt")
                )
            }.isSuccess
            if (ok) count += 1
        }
        return count
    }

    private suspend fun replay(container: DesktopContainer, text: String) =
        if (text.isBlank()) {
            null
        } else {
            runCatching { container.restoreRepository.restoreFromJsonl(text) }.getOrNull()
        }

    private suspend fun applySettings(container: DesktopContainer, text: String): Boolean {
        if (text.isBlank()) return false
        val snapshot = SettingsBackup.decode(text) ?: return false
        return runCatching { SettingsBackup.apply(container.settings, snapshot) }.isSuccess
    }

    private fun empty(kind: BundleKind) = BundleRead(
        kind = kind,
        imported = 0,
        skipped = 0,
        replayed = 0,
        decks = 0,
        settings = false
    )

    private fun text(stream: InputStream): String {
        val out = StringBuilder()
        val buffer = ByteArray(COPY_BUFFER)
        var total = 0L
        stream.use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                if (total > MAX_ENTRY_BYTES) return ""
                out.append(String(buffer, 0, read, Charsets.UTF_8))
            }
        }
        return out.toString()
    }

    /** Deck ids are ours, but a zip entry name is not the place to trust that. */
    private fun safeName(id: String): String =
        id.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '-' }
            .joinToString("")
            .take(80)
            .ifBlank { "deck" }

    private fun stamp(now: Long): String = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        .format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()))
}
