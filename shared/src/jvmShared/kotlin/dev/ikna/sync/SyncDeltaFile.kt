package dev.ikna.sync

import dev.ikna.data.export.ReviewRecord
import dev.ikna.data.repo.RestoreRepository
import dev.ikna.data.repo.RestoreResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Serverless offline sync delta file (.ikna-sync).
 *
 * Encodes the normalized review journal into a compact GZIP-compressed envelope
 * with a SHA-256 payload digest and history-boundary lineage marker so two devices
 * can synchronize review progress via any file channel (Nearby Share, Syncthing,
 * USB, Saved Messages) without requiring a shared Wi-Fi network or remote server,
 * while preventing stale pre-reset files from reviving cleared history.
 */
object SyncDeltaFile {
    const val FILE_EXTENSION = "ikna-sync"
    const val MIME_TYPE = "application/octet-stream"
    const val FORMAT_VERSION = 1
    const val MAX_COMPRESSED_BYTES = 16 * 1024 * 1024
    private const val MAX_DECOMPRESSED_BYTES = 64 * 1024 * 1024
    private val json = Json { ignoreUnknownKeys = false; isLenient = false; encodeDefaults = true }

    @Serializable
    data class Envelope(
        val format: String = "ikna_sync_delta_v1",
        val version: Int = FORMAT_VERSION,
        val createdAtMillis: Long,
        val profileName: String = "",
        val historyBoundaryToken: String? = null,
        val recordCount: Int,
        val payloadSha256: String,
        val records: List<ReviewRecord>
    )

    data class ExportSummary(
        val bytes: ByteArray,
        val recordCount: Int,
        val createdAtMillis: Long
    )

    data class MergeSummary(
        val totalInFile: Int,
        val imported: Int,
        val skipped: Int,
        val replayed: Int,
        val sourceProfileName: String
    )

    /** Reads at most [MAX_COMPRESSED_BYTES] bytes from [input] to avoid OOM on oversized selections. */
    fun readBounded(input: InputStream, maxBytes: Int = MAX_COMPRESSED_BYTES): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            require(total <= maxBytes) { "Sync file exceeds maximum size limit" }
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    fun encodeEnvelope(
        records: List<ReviewRecord>,
        profileName: String = "",
        historyBoundaryToken: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): ByteArray {
        val recordsJson = json.encodeToString(records)
        val digest = sha256Hex(recordsJson.toByteArray(Charsets.UTF_8))
        val envelope = Envelope(
            createdAtMillis = nowMillis,
            profileName = profileName.trim().take(64),
            historyBoundaryToken = historyBoundaryToken,
            recordCount = records.size,
            payloadSha256 = digest,
            records = records
        )
        val raw = json.encodeToString(envelope).toByteArray(Charsets.UTF_8)
        require(raw.size <= MAX_DECOMPRESSED_BYTES) { "Sync journal exceeds exportable size limit" }
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(raw) }
        val compressed = out.toByteArray()
        require(compressed.size <= MAX_COMPRESSED_BYTES) { "Compressed sync file exceeds size limit" }
        return compressed
    }

    fun decodeEnvelope(compressedBytes: ByteArray): Envelope {
        require(compressedBytes.isNotEmpty() && compressedBytes.size <= MAX_COMPRESSED_BYTES) {
            "Sync file size is out of bounds"
        }
        val raw = gunzipBounded(compressedBytes)
        val envelope = json.decodeFromString<Envelope>(raw.toString(Charsets.UTF_8))
        require(envelope.format == "ikna_sync_delta_v1" && envelope.version == FORMAT_VERSION) {
            "Unsupported sync file format"
        }
        require(envelope.recordCount == envelope.records.size) {
            "Sync file record count mismatch"
        }
        val expectedDigest = sha256Hex(json.encodeToString(envelope.records).toByteArray(Charsets.UTF_8))
        require(expectedDigest == envelope.payloadSha256) {
            "Sync file integrity check failed"
        }
        return envelope
    }

    /** Rejects pre-reset sync files when the receiver has a different progress-reset lineage. */
    fun validateHistoryLineage(envelope: Envelope, localBoundaryToken: String?) {
        require(envelope.historyBoundaryToken == localBoundaryToken) {
            "Sync file comes from a different progress-reset history generation"
        }
    }

    suspend fun exportFrom(
        repository: RestoreRepository,
        profileName: String = "",
        nowMillis: Long = System.currentTimeMillis()
    ): ExportSummary = withContext(Dispatchers.IO) {
        val boundary = repository.persistedHistoryBoundary()
        val records = repository.snapshotJournal(
            afterLocalId = boundary?.afterLocalId ?: 0L,
            boundaryToken = boundary?.token
        )
        val bytes = encodeEnvelope(
            records = records,
            profileName = profileName,
            historyBoundaryToken = boundary?.token,
            nowMillis = nowMillis
        )
        ExportSummary(bytes = bytes, recordCount = records.size, createdAtMillis = nowMillis)
    }

    suspend fun mergeInto(
        compressedBytes: ByteArray,
        repository: RestoreRepository,
        onMerged: suspend () -> Unit = {}
    ): MergeSummary = withContext(Dispatchers.IO) {
        val envelope = decodeEnvelope(compressedBytes)
        val boundary = repository.persistedHistoryBoundary()
        validateHistoryLineage(envelope, boundary?.token)
        val result: RestoreResult = repository.mergeJournal(
            records = envelope.records,
            afterLocalId = boundary?.afterLocalId ?: 0L,
            boundaryToken = boundary?.token
        )
        if (result.imported > 0 || result.replayed > 0) {
            onMerged()
        }
        MergeSummary(
            totalInFile = envelope.recordCount,
            imported = result.imported,
            skipped = result.skipped,
            replayed = result.replayed,
            sourceProfileName = envelope.profileName
        )
    }

    private fun gunzipBounded(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(bytes)).use { gz ->
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val n = gz.read(buf)
                if (n < 0) break
                total += n
                require(total <= MAX_DECOMPRESSED_BYTES) { "Sync file exceeds decompressed size limit" }
                out.write(buf, 0, n)
            }
        }
        return out.toByteArray()
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}
