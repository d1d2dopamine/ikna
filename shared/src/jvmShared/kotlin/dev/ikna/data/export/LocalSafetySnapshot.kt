package dev.ikna.data.export

import dev.ikna.data.db.ReviewDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Automatic bounded local JSONL safety snapshots taken before destructive operations
 * (progress reset, external restore, full wipe). Purely local, zero cloud/network I/O.
 */
object LocalSafetySnapshot {
    private const val DIR_NAME = "safety-backups"
    private const val DEFAULT_MAX_RETAINED = 3
    private val stampFmt: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

    fun directory(baseDir: File): File = File(baseDir, DIR_NAME)

    fun count(baseDir: File): Int =
        directory(baseDir).listFiles { f -> f.isFile && f.name.endsWith(".jsonl") }?.size ?: 0

    suspend fun capture(
        baseDir: File,
        reviewDao: ReviewDao,
        reason: String,
        nowMillis: Long = System.currentTimeMillis(),
        maxRetained: Int = DEFAULT_MAX_RETAINED
    ): File? = withContext(Dispatchers.IO) {
        val rows = reviewDao.all()
        if (rows.isEmpty()) return@withContext null
        val dir = directory(baseDir).apply { mkdirs() }
        val safeReason = reason.lowercase().replace(Regex("[^a-z0-9_-]"), "").ifBlank { "op" }.take(24)
        val stamp = stampFmt.format(Instant.ofEpochMilli(nowMillis))
        val target = File(dir, "safety-$safeReason-$stamp.jsonl")
        val tmp = File(dir, ".${target.name}.tmp")
        try {
            tmp.bufferedWriter(Charsets.UTF_8).use { writer ->
                for (row in rows) {
                    writer.write(ReviewRecord.json.encodeToString(ReviewRecord.serializer(), ReviewRecord.of(row)))
                    writer.newLine()
                }
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            prune(dir, maxRetained.coerceAtLeast(1))
            target
        } catch (_: Exception) {
            runCatching { tmp.delete() }
            null
        }
    }

    private fun prune(dir: File, maxRetained: Int) {
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith("safety-") && f.name.endsWith(".jsonl") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        if (files.size > maxRetained) {
            files.drop(maxRetained).forEach { runCatching { it.delete() } }
        }
    }
}
