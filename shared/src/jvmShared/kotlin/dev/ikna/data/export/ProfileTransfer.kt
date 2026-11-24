package dev.ikna.data.export

import dev.ikna.data.db.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/** Installed content and ACTIVE journal only. Not a recovery backup: archives, settings,
 * media bytes, Browse observations and device credentials are deliberately absent. */
@Serializable
data class ProfileTransferSnapshot(
    val version: Int = 1,
    val boundaryPresent: Boolean = false,
    val packs: List<PackEntity>,
    val chunks: List<ChunkEntity>,
    val tokens: List<ChunkTokenEntity>,
    val memberships: List<PackChunkEntity>,
    val contexts: List<ChunkContextEntity>,
    val reviews: List<ReviewRecord>
)

data class ProfileMergePreview(
    val localRevision: String,
    val incomingRevision: String,
    val addedPacks: Int,
    val addedTargets: Int,
    val addedReviews: Int,
    val conflicts: List<String>
) {
    val canMerge: Boolean get() = conflicts.isEmpty()
}

/** Safe, content-free failure shared by the preflight, codec and sync status UI. */
class ProfileTransferLimitException(val limit: Limit) : IllegalArgumentException(
    when (limit) {
        Limit.ROWS -> "Profile exceeds row limit"
        Limit.BYTES -> "Profile exceeds byte limit"
    }
) {
    enum class Limit { ROWS, BYTES }
}

/** Exact Unicode/content, without name-based identity or lossy text normalization. */
object ProfileTransferCodec {
    const val MAX_BYTES = 16 * 1024 * 1024
    const val MAX_ROWS = 150_000
    val json = Json { ignoreUnknownKeys = false; isLenient = false; encodeDefaults = true }

    fun canonical(snapshot: ProfileTransferSnapshot): ProfileTransferSnapshot = snapshot.copy(
        packs = snapshot.packs.sortedBy { it.id },
        chunks = snapshot.chunks.sortedBy { it.id },
        tokens = snapshot.tokens.sortedWith(compareBy<ChunkTokenEntity> { it.chunkId }.thenBy { it.position }),
        memberships = snapshot.memberships.sortedWith(compareBy<PackChunkEntity> { it.packId }.thenBy { it.chunkId }),
        contexts = snapshot.contexts.sortedWith(compareBy<ChunkContextEntity> { it.packId }.thenBy { it.chunkId }
            .thenBy { it.contextId }.thenBy { it.meaningId }),
        reviews = JournalIdentity.normalize(snapshot.reviews).sortedWith(compareBy<ReviewRecord> { it.ts }.thenBy { it.eventId })
    )

    fun encode(snapshot: ProfileTransferSnapshot): ByteArray {
        require(snapshot.version == 1) { "Unsupported profile version" }
        if (listOf(snapshot.packs.size, snapshot.chunks.size, snapshot.tokens.size,
            snapshot.memberships.size, snapshot.contexts.size, snapshot.reviews.size).any { it > MAX_ROWS }) {
            throw ProfileTransferLimitException(ProfileTransferLimitException.Limit.ROWS)
        }
        return json.encodeToString(canonical(snapshot)).toByteArray(Charsets.UTF_8).also {
            if (it.size > MAX_BYTES) throw ProfileTransferLimitException(ProfileTransferLimitException.Limit.BYTES)
        }
    }
    fun decode(bytes: ByteArray): ProfileTransferSnapshot {
        if (bytes.size > MAX_BYTES) throw ProfileTransferLimitException(ProfileTransferLimitException.Limit.BYTES)
        return json.decodeFromString<ProfileTransferSnapshot>(bytes.toString(Charsets.UTF_8)).also { encode(it) }
    }
    /** Convergence identity excludes device-local installation metadata and reset markers.
     * Authenticated lineage is checked separately by the pairing protocol. */
    fun contentIdentity(snapshot: ProfileTransferSnapshot): String = profileIdentity(snapshot.copy(reviews = emptyList()))

    fun profileIdentity(snapshot: ProfileTransferSnapshot): String = revision(snapshot.copy(
        boundaryPresent = false,
        packs = snapshot.packs.map { it.copy(installedAt = 0, isActive = true) },
        chunks = snapshot.chunks.map { it.copy(packId = "") }
    ))
    fun revision(snapshot: ProfileTransferSnapshot): String = digest(encode(snapshot))
    fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
