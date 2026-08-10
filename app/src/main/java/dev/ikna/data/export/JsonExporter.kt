package dev.ikna.data.export

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import dev.ikna.data.db.ReviewDao
import java.io.BufferedWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Weekly dump of the append-only review log to shared storage.
 *
 * Written through MediaStore into Documents/ikna/, deliberately NOT into the app
 * sandbox: the point is to survive an uninstall, a factory reset and a new phone.
 * Everything else in this database can be regenerated; four months of answers
 * cannot.
 *
 * Two files come out of here now, not one. The log was the only thing being
 * saved, which meant a restore gave back every card and every date and then the
 * app looked like a fresh install: default theme, custom colours gone, font gone.
 * Those live in a second, tiny file beside the log rather than inside it, because
 * the log is read line by line and a settings record on one of those lines would
 * look to the restore like a damaged answer.
 */
class JsonExporter(
    private val context: Context,
    private val reviewDao: ReviewDao
) {

    /**
     * The review log.
     *
     * @return the file name written, or null when there was nothing to write.
     *   Null is not a failure — an empty log is what a new install looks like —
     *   and the difference matters to what the user is told. A real failure
     *   throws.
     */
    suspend fun export(now: Long = System.currentTimeMillis()): String? {
        val reviews = reviewDao.all()
        if (reviews.isEmpty()) return null

        // Serialised, not concatenated. This used to be a StringBuilder that
        // wrapped chunkId in quotes and hoped: a pack whose ids contain a quote,
        // a backslash or a newline wrote a line that no longer parsed, and the
        // first anyone would find out is the day they needed the file. Ids are
        // well behaved in the bundled packs, but "importFile" takes any .jsonl
        // the user has, so the guarantee has to come from the encoder.
        val name = "ikna-reviews-" + stamp(now) + ".jsonl"
        return write(name, "application/x-ndjson") { out ->
            for (r in reviews) {
                out.write(
                    ReviewRecord.json.encodeToString(
                        ReviewRecord.serializer(),
                        ReviewRecord.of(r)
                    )
                )
                out.newLine()
            }
        }
    }

    /**
     * Theme, colours, load, reminder and voices — everything that is a choice
     * rather than a record. One small JSON object beside the log.
     */
    fun exportSettings(json: String, now: Long = System.currentTimeMillis()): String? {
        if (json.isBlank()) return null
        val name = "ikna-settings-" + stamp(now) + ".json"
        return write(name, "application/json") { out -> out.write(json) }
    }

    private fun stamp(now: Long): String = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        .format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()))

    private fun write(name: String, mime: String, body: (BufferedWriter) -> Unit): String? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/ikna")
        }

        val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values) ?: return null
        val stream = resolver.openOutputStream(uri) ?: return null
        stream.bufferedWriter().use { out -> body(out) }
        return name
    }
}
