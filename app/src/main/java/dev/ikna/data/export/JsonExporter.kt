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

        val name = "ikna-reviews-" + stamp(now) + ".jsonl"
        return write(name, "application/x-ndjson") { out ->
            for (r in reviews) {
                out.write(
                    buildString {
                        append('{')
                        append("\"id\":").append(r.id).append(',')
                        append("\"chunkId\":\"").append(r.chunkId).append("\",")
                        append("\"level\":").append(r.level).append(',')
                        append("\"ts\":").append(r.ts).append(',')
                        append("\"rating\":").append(r.rating).append(',')
                        append("\"elapsedDays\":").append(r.elapsedDays).append(',')
                        append("\"stabilityBefore\":").append(r.stabilityBefore).append(',')
                        append("\"stabilityAfter\":").append(r.stabilityAfter).append(',')
                        append("\"difficultyBefore\":").append(r.difficultyBefore).append(',')
                        append("\"difficultyAfter\":").append(r.difficultyAfter).append(',')
                        append("\"durationMs\":").append(r.durationMs).append(',')
                        append("\"wasAmnesty\":").append(r.wasAmnesty)
                        // v2: the undo trail. Exported so a restore replays the
                        // history the user actually kept, retractions included,
                        // instead of reviving answers they took back.
                        r.prevStability?.let { append(",\"prevStability\":").append(it) }
                        r.prevDifficulty?.let { append(",\"prevDifficulty\":").append(it) }
                        r.prevDueAt?.let { append(",\"prevDueAt\":").append(it) }
                        r.prevLastReviewAt?.let { append(",\"prevLastReviewAt\":").append(it) }
                        r.prevReps?.let { append(",\"prevReps\":").append(it) }
                        r.prevLapses?.let { append(",\"prevLapses\":").append(it) }
                        r.prevIsNew?.let { append(",\"prevIsNew\":").append(it) }
                        r.prevInAmnesty?.let { append(",\"prevInAmnesty\":").append(it) }
                        r.undoOf?.let { append(",\"undoOf\":").append(it) }
                        append('}')
                    }
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
