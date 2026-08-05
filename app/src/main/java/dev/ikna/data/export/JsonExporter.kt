package dev.ikna.data.export

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import dev.ikna.data.db.ReviewDao
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Weekly dump of the append-only review log to shared storage.
 *
 * Written through MediaStore into Documents/Ikna/, deliberately NOT into the
 * app sandbox: the point is to survive an uninstall, a factory reset and a new
 * phone. Everything else in this database can be regenerated; four months of
 * answers cannot.
 */
class JsonExporter(
    private val context: Context,
    private val reviewDao: ReviewDao
) {

    suspend fun export(now: Long = System.currentTimeMillis()): String? {
        val reviews = reviewDao.all()
        if (reviews.isEmpty()) return null

        val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()))
        val name = "ikna-reviews-" + stamp + ".jsonl"

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/x-ndjson")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/Ikna")
        }

        val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values) ?: return null
        resolver.openOutputStream(uri)?.bufferedWriter()?.use { out ->
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
        return name
    }
}
