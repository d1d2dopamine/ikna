package dev.ikna.data.export

import kotlinx.serialization.Serializable

/** Non-secret context of one explicitly approved, durably committed operation. */
data class JournalAuthorization(
    val localDeviceId: String, val peerDeviceId: String,
    val groupId: String, val epoch: String, val freshStart: Boolean,
    val consentOperationId: String, val operationDigest: String
)

@Serializable
data class JournalStreamBinding(
    val protocol: Int = 3,
    val sourceDevice: String, val targetDevice: String, val streamId: String,
    val groupId: String, val epoch: String, val freshStart: Boolean,
    val consentOperationId: String, val contentIdentity: String,
    val sourceBoundaryIdentity: String
)

@Serializable
data class JournalCursor(val sequence: Long, val prefix: String)

@Serializable
data class JournalHead(
    val binding: JournalStreamBinding, val highWater: JournalCursor,
    /** A bounded indexing pass has more committed rows to inspect. Never report caught-up. */
    val hasUnindexed: Boolean = false
)

@Serializable
data class JournalEntry(
    val sequence: Long, val eventId: String, val payloadDigest: String,
    val undoEventId: String? = null
)

@Serializable
data class JournalInventory(
    val binding: JournalStreamBinding,
    val from: JournalCursor, val to: JournalCursor, val highWater: JournalCursor,
    val entries: List<JournalEntry>, val pageDigest: String,
    val dependencies: List<JournalDependency> = emptyList()
)

@Serializable
data class JournalRequest(val inventory: JournalInventory, val missingIds: List<String>)

@Serializable
data class JournalData(val inventory: JournalInventory, val records: List<ReviewRecord>)

@Serializable
data class JournalAck(val binding: JournalStreamBinding, val cursor: JournalCursor, val pageDigest: String? = null)

object IncrementalJournalLimits {
    const val MAX_ENTRIES = 128
    const val MAX_DATA_BYTES = 252 * 1024
    const val MAX_FRAME_BYTES = 256 * 1024
}

/** Local-only result; remote messages cannot claim mutations. */
data class JournalApplyResult(val ack: JournalAck, val imported: Int)

@Serializable
data class JournalDependency(val eventId: String, val payloadDigest: String)

class IncrementalJournalLimitException : IllegalArgumentException("Journal item exceeds exchange limit")
