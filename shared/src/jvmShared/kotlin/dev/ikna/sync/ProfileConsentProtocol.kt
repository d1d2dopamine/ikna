package dev.ikna.sync

import dev.ikna.data.export.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.util.UUID

/** One authenticated peer, bounded transient staging, and safe profile convergence.
 * Pending consent is transient; completed consent is verified against durable Room receipts. */
class ProfileConsentProtocol(
    private val deviceId: String,
    private val binding: HistoryBinding,
    private val store: Store,
    private val now: () -> Long = System::currentTimeMillis,
    savedCompletion: Completion? = null,
    savedInvalidation: String? = null,
    private val autoAcceptFromInviter: () -> Boolean = { false },
    private val onLog: (String, Boolean) -> Unit = { _, _ -> },
) {
    interface Store {
        suspend fun snapshot(): ProfileTransferSnapshot
        suspend fun snapshotWithRevision(): Pair<ProfileTransferSnapshot, String>
        suspend fun committedContentIdentity(operationId: String): String
        suspend fun preview(incoming: ProfileTransferSnapshot): ProfileMergePreview
        /** Atomically validate preview, mutate, and persist tuple digest + resulting semantic identity. */
        suspend fun commit(incoming: ProfileTransferSnapshot, preview: ProfileMergePreview,
                           operationId: String, tupleDigest: String): String
        suspend fun previewAdoption(incoming: ProfileTransferSnapshot): ProfileMergePreview = preview(incoming)
        suspend fun prepareAdoptionRecipient() {}
        /** Recipient schedules adoption outside the network callback and returns null until restart. */
        suspend fun adopt(incoming: ProfileTransferSnapshot, preview: ProfileMergePreview,
                          proposal: Proposal, localIsSource: Boolean): String? = error("adoption-not-supported")
        fun incremental(completion: Completion, peerId: String): IncrementalJournalProtocol
        suspend fun validCompletion(completion: Completion): Boolean
        suspend fun saveCompletion(completion: Completion?)
        suspend fun saveInvalidatedOperationId(operationId: String?)
    }
    @Serializable enum class Mode { MERGE, ADOPT_THIS, ADOPT_OTHER }
    @Serializable data class Proposal(
        val protocol: Int = 3, val operationId: String, val mode: Mode,
        val firstDevice: String, val secondDevice: String, val binding: HistoryBinding,
        val firstSnapshot: String, val secondSnapshot: String,
        val firstRevision: String, val secondRevision: String, val expiresAtMillis: Long,
    )
    @Serializable data class Completion(val proposal: Proposal, val receipt: String, val contentIdentity: String)
    @Serializable private data class Wire(
        val protocol: Int = WIRE_PROTOCOL, val snapshot: ProfileTransferSnapshot? = null, val revision: String? = null,
        val completion: Completion? = null,
        val invalidatedOperationId: String? = null,
        val proposal: Proposal? = null, val approved: Boolean = false,
        val receipt: String? = null,
        val incremental: IncrementalJournalProtocol.Frame? = null,
        // Decode old peers only to return an explicit incompatibility category, never import it.
        val journal: List<ReviewRecord>? = null,
    )
    data class State(
        val peerId: String? = null, val operationId: String? = null,
        val preview: ProfileMergePreview? = null, val localApproved: Boolean = false,
        val remoteApproved: Boolean = false, val applied: Boolean = false,
        val complete: Boolean = false, val expiresAtMillis: Long? = null,
        val adoptionPending: Boolean = false, val selectedReviews: Int = 0, val archivedLocalReviews: Int = 0,
        val mode: Mode? = null, val proposerFirst: Boolean = true,
        val journalCaughtUp: Boolean = false, val journalPending: Boolean = false,
    )
    private data class Offer(val snapshot: ProfileTransferSnapshot, val revision: String)
    private val mutex = Mutex()
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = true }
    private var peerWireProtocol: Int = WIRE_PROTOCOL
    private var completion: Completion? = savedCompletion
    private var completionConfirmed = false
    private var invalidatedOperationId: String? = savedInvalidation?.also { require(it.matches(ID)) }
    private var cancelledOperationId: String? = null
    private var peer: String? = null
    private var local: Offer? = null
    private var remote: Offer? = null
    private var proposal: Proposal? = null
    private var preview: ProfileMergePreview? = null
    private var localApproved = false
    private var remoteApproved = false
    private var receipt: String? = null
    private var remoteReceipt: String? = null
    private var adoptionPending = false
    private var incremental: IncrementalJournalProtocol? = null
    private var journalObserved = false
    private val rejected = LinkedHashSet<String>()
    @Volatile var state = State(); private set

    private fun publish() {
        val c = completion
        state = if (c != null) State(peer, c.proposal.operationId, null, true, completionConfirmed, true, completionConfirmed)
        else State(peer, proposal?.operationId, preview, localApproved, remoteApproved,
            receipt != null, receipt != null && receipt == remoteReceipt, proposal?.expiresAtMillis)
        val p = c?.proposal ?: proposal
        state = state.copy(mode = p?.mode, proposerFirst = p == null || p.firstDevice == deviceId,
            adoptionPending = adoptionPending,
            journalCaughtUp = state.complete && journalObserved && incremental?.state?.caughtUp == true,
            journalPending = state.complete && (!journalObserved || incremental?.state?.caughtUp != true),
            selectedReviews = if (p != null && p.mode != Mode.MERGE) {
                if (localIsSource(p)) local?.snapshot?.reviews?.size ?: 0 else remote?.snapshot?.reviews?.size ?: 0
            } else 0,
            archivedLocalReviews = if (p != null && p.mode != Mode.MERGE && !localIsSource(p)) local?.snapshot?.reviews?.size ?: 0 else 0)
    }
    private fun clearPending(recordRejection: Boolean = true) {
        proposal?.let {
            if (recordRejection) {
                rejected.add(it.operationId)
                cancelledOperationId = it.operationId
            }
        }
        while (rejected.size > 128) rejected.remove(rejected.first())
        proposal = null; preview = null; localApproved = false; remoteApproved = false
        receipt = null; remoteReceipt = null; adoptionPending = false; local = null; incremental = null; journalObserved = false; publish()
    }
    private fun expire() {
        if (!state.complete && proposal?.let { now() >= it.expiresAtMillis } == true) clearPending()
    }
    suspend fun clear() = mutex.withLock {
        clearCurrentCatchUp()
        persistInvalidation(completion?.proposal?.operationId ?: proposal?.operationId ?: invalidatedOperationId)
        clearPending(); completion = null; completionConfirmed = false; store.saveCompletion(null)
        peer = null; remote = null; peerWireProtocol = WIRE_PROTOCOL; publish()
    }
    suspend fun pause() = mutex.withLock {
        if (completion == null) clearPending()
        completionConfirmed = false
        incremental = null
        journalObserved = false
        publish()
    }
    private fun checkPeer(id: String) {
        require(id != deviceId && (peer == null || peer == id)) { "single-peer-required" }
        completion?.let { require(setOf(it.proposal.firstDevice, it.proposal.secondDevice) == setOf(deviceId, id)) { "profile-peer-mismatch" } }
        peer = id
    }
    private suspend fun offer(): Offer {
        if (proposal == null || local == null) {
            val (snapshot, revision) = store.snapshotWithRevision()
            local = Offer(snapshot, revision)
        }
        return local!!
    }
    suspend fun snapshotFor(peerId: String): ByteArray = mutex.withLock {
        clearCurrentCatchUp()
        checkPeer(peerId); validateCompletion(); expire()
        completion?.let { c ->
            val frame = try { if (completionConfirmed) journalProtocol().snapshot().also { journalObserved = true } else null } finally { publish() }
            return@withLock encode(Wire(completion = c, incremental = frame))
        }
        val offer = offer()
        publish()
        encode(Wire(snapshot = offer.snapshot, revision = offer.revision,
            invalidatedOperationId = invalidatedOperationId ?: cancelledOperationId,
            proposal = proposal, approved = localApproved, receipt = receipt,
            journal = null))
    }
    suspend fun propose(peerId: String, mode: Mode = Mode.MERGE): State = mutex.withLock {
        proposeLocked(peerId, mode, approveImmediately = false)
    }
    /** Single-click inviter choice: creates the exact proposal and marks local approval in one step. */
    suspend fun proposeAndApprove(peerId: String, mode: Mode = Mode.MERGE): State = mutex.withLock {
        proposeLocked(peerId, mode, approveImmediately = true)
    }
    private suspend fun proposeLocked(peerId: String, mode: Mode, approveImmediately: Boolean): State {
        clearCurrentCatchUp()
        checkPeer(peerId); validateCompletion(); expire(); check(completion == null) { "profile-already-connected" }
        if (approveImmediately && proposal != null && receipt == null && !adoptionPending) {
            clearPending()
        }
        check(proposal == null) { "proposal-pending" }
        val incoming = checkNotNull(remote) { "awaiting-profile" }
        val own = offer()
        val checked = if (mode == Mode.MERGE) store.preview(incoming.snapshot) else store.previewAdoption(incoming.snapshot)
        val allowed = if (mode == Mode.MERGE) checked.canMerge else checked.canAdopt
        if (checked.localRevision != own.revision) {
            onLog("Proposal rejected: local profile changed before choice", true)
            throw IllegalArgumentException("profile-conflict-or-stale")
        }
        if (!allowed) {
            onLog("Proposal rejected (${mode.name}): ${checked.conflicts.take(2).joinToString("; ")}", true)
            throw IllegalArgumentException("profile-conflict-or-stale")
        }
        proposal = Proposal(operationId = UUID.randomUUID().toString(), mode = mode,
            firstDevice = deviceId, secondDevice = peerId, binding = binding,
            firstSnapshot = ProfileTransferCodec.revision(own.snapshot),
            secondSnapshot = ProfileTransferCodec.revision(incoming.snapshot),
            firstRevision = own.revision, secondRevision = incoming.revision,
            expiresAtMillis = now() + TTL_MILLIS)
        preview = checked
        if (approveImmediately) {
            localApproved = true
            onLog("Inviter chose ${mode.name} (local ${checked.localPacks}d/${checked.localReviews}r, peer ${checked.remotePacks}d/${checked.remoteReviews}r)", false)
        }
        publish()
        return state
    }
    suspend fun approve(operationId: String): State = mutex.withLock {
        clearCurrentCatchUp()
        expire()
        completion?.let { c ->
            if (c.proposal.operationId == operationId) {
                publish()
                return@withLock state
            }
        }
        val p = checkNotNull(proposal) { "no-proposal" }
        require(p.operationId == operationId) { "stale-proposal" }
        if (localApproved && (receipt != null || adoptionPending)) {
            publish()
            return@withLock state
        }
        require(receipt == null) { "already-applied" }
        val current = if (p.mode == Mode.MERGE) store.preview(remote!!.snapshot) else store.previewAdoption(remote!!.snapshot)
        val allowed = if (p.mode == Mode.MERGE) current.canMerge else current.canAdopt
        require(current == preview && allowed) { "profile-conflict-or-stale" }
        localApproved = true
        applyIfApproved(); publish(); state
    }
    suspend fun receiveFrom(peerId: String, bytes: ByteArray) = mutex.withLock {
        clearCurrentCatchUp()
        if (bytes.size > MAX_BYTES) throw dev.ikna.data.export.ProfileTransferLimitException(dev.ikna.data.export.ProfileTransferLimitException.Limit.BYTES)
        // Envelope vocabulary version is distinct from the unchanged v3 consent
        // tuple and stream-prefix formats. Reject older readers before nested
        // records or durable completion state can be interpreted or mutated.
        val envelope = json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        val wireVersion = envelope["protocol"]?.jsonPrimitive?.intOrNull
        if (wireVersion !in SUPPORTED_WIRE_PROTOCOLS)
            throw UnsupportedSyncProtocolException()
        val wire = json.decodeFromJsonElement(Wire.serializer(), envelope)
        if (wire.journal != null) throw UnsupportedSyncProtocolException()
        if (wireVersion != null && wireVersion < peerWireProtocol) peerWireProtocol = wireVersion
        checkPeer(peerId); validateCompletion(); expire()
        if (wire.proposal?.protocol?.let { it != 3 } == true ||
            wire.completion?.proposal?.protocol?.let { it != 3 } == true) throw UnsupportedSyncProtocolException()
        // A malformed or expired new proposal cannot discard an existing checkpoint.
        wire.proposal?.takeUnless { it == completion?.proposal }?.let { p ->
            require(p.protocol == 3 && p.binding == binding &&
                setOf(p.firstDevice, p.secondDevice) == setOf(deviceId, peerId) &&
                p.firstDevice != p.secondDevice && p.operationId.matches(ID) &&
                listOf(p.firstSnapshot, p.secondSnapshot, p.firstRevision, p.secondRevision).all { it.matches(HEX) }) {
                "invalid-profile-proposal"
            }
            val echoedOwnCancelled = completion == null && p.operationId in rejected && p.firstDevice == deviceId
            if (!echoedOwnCancelled) {
                val currentNow = now()
                if (p.expiresAtMillis <= currentNow || p.expiresAtMillis > currentNow + TTL_MILLIS + CLOCK_SKEW_MILLIS) {
                    val skewSec = (p.expiresAtMillis - (currentNow + TTL_MILLIS)) / 1000L
                    onLog("Proposal rejected: expired or clock skew (${skewSec}s)", true)
                    throw IllegalArgumentException("proposal-expired")
                }
                require(p.operationId !in rejected) { "proposal-cancelled" }
            }
            if (completion != null) {
                val incoming = requireNotNull(wire.snapshot) { "missing-profile" }
                val first = p.firstDevice == deviceId
                require(ProfileTransferCodec.revision(incoming) == (if (first) p.secondSnapshot else p.firstSnapshot) &&
                    wire.revision == (if (first) p.secondRevision else p.firstRevision)) { "remote-profile-stale" }
                val (own, revision) = store.snapshotWithRevision()
                require(ProfileTransferCodec.revision(own) == (if (first) p.firstSnapshot else p.secondSnapshot) &&
                    revision == (if (first) p.firstRevision else p.secondRevision)) { "local-profile-stale" }
                val checked = if (p.mode == Mode.MERGE) store.preview(incoming) else store.previewAdoption(incoming)
                val allowed = if (p.mode == Mode.MERGE) checked.canMerge else checked.canAdopt
                require(checked.localRevision == revision && allowed) { "profile-conflict-or-stale" }
            }
        }
        wire.completion?.let { received ->
            require(wire.snapshot == null && wire.proposal == null && received.proposal.binding == binding &&
                setOf(received.proposal.firstDevice, received.proposal.secondDevice) == setOf(deviceId, peerId)) { "invalid-completion" }
            if (completion == null) {
                require(proposal == received.proposal && localApproved) { "initial-consent-required" }
                if (receipt == null) {
                    // The adopting recipient stops its transport to replace the database.
                    // Its last approval frame may be lost. Its authenticated durable receipt
                    // is also approval of this exact tuple, but never supplies local consent.
                    val pending = requireNotNull(proposal)
                    require(pending.mode != Mode.MERGE && localIsSource(pending) &&
                        received.receipt.matches(HEX) && received.contentIdentity.matches(HEX)) { "initial-consent-required" }
                    require(now() < pending.expiresAtMillis) { "proposal-expired" }
                    remoteApproved = true
                    applyIfApproved() // Storage rechecks the original local revision atomically.
                }
                require(receipt != null && receipt == received.receipt &&
                    store.committedContentIdentity(received.proposal.operationId) == received.contentIdentity) { "profile-receipt-mismatch" }
                finish()
            }
            require(completion == received) { "profile-receipt-mismatch" }
            completionConfirmed = true
            try { wire.incremental?.let { journalProtocol().receive(it); journalObserved = true } } finally { publish() }
            return@withLock
        }
        require(wire.incremental == null) { "initial-consent-required" }
        // An in-flight progress frame for this exact completed tuple is stale, even
        // if it was serialized before the peer had its final receipt. Never revoke
        // completed consent because packets from the same operation arrive reordered.
        completion?.let { c ->
            if (wire.proposal == c.proposal) {
                require(wire.receipt == null || (wire.approved && wire.receipt == c.receipt)) { "profile-receipt-mismatch" }
                if (wire.receipt == c.receipt) completionConfirmed = true
                publish(); return@withLock
            }
        }
        // Bare discovery frames may predate this completed operation. Only an
        // explicit invalidation of this receipt, or a fresh proposal, can retire it.
        completion?.let { c ->
            if (wire.proposal == null && wire.invalidatedOperationId != c.proposal.operationId) {
                publish(); return@withLock
            }
            require(wire.proposal?.operationId !in rejected) { "proposal-cancelled" }
            persistInvalidation(c.proposal.operationId)
            completion = null; completionConfirmed = false; store.saveCompletion(null); clearPending()
        }
        val snapshot = requireNotNull(wire.snapshot) { "missing-profile" }
        val revision = requireNotNull(wire.revision) { "missing-profile-revision" }
        require(revision.matches(HEX)) { "invalid-profile-revision" }
        ProfileTransferCodec.encode(snapshot)
        val incoming = Offer(snapshot, revision)
        // If peer explicitly cancelled our uncompleted pending proposal, drop it cleanly so
        // both devices return to fresh discovery instead of deadlocking on proposal-conflict.
        val pendingBefore = proposal
        if (pendingBefore != null && !adoptionPending && wire.invalidatedOperationId == pendingBefore.operationId) {
            onLog("Peer cancelled pending proposal (${pendingBefore.operationId.take(8)}); resetting", false)
            clearPending(recordRejection = false)
        }
        val p = wire.proposal
        if (p != null) {
            require(p.protocol == 3 && p.binding == binding &&
                setOf(p.firstDevice, p.secondDevice) == setOf(deviceId, peerId) &&
                p.firstDevice != p.secondDevice && p.operationId.matches(ID) &&
                listOf(p.firstSnapshot, p.secondSnapshot, p.firstRevision, p.secondRevision).all { it.matches(HEX) }) {
                "invalid-profile-proposal"
            }
            if (p.operationId in rejected) {
                if (p.firstDevice == deviceId) {
                    // Peer echoed our own cancelled proposal before learning it was cancelled
                    // (or before learning we already replaced it with a newer proposal).
                    // Keep cancelledOperationId so our immediate response clears it on the peer.
                    onLog("Cleared echoed cancelled proposal (${p.operationId.take(8)})", false)
                    cancelledOperationId = p.operationId
                    if (proposal == null) {
                        remote = incoming
                        preview = store.preview(incoming.snapshot)
                    }
                    publish()
                    return@withLock
                }
                onLog("Rejected: proposal-cancelled (${p.operationId.take(8)})", true)
                throw IllegalArgumentException("proposal-cancelled")
            }
            val currentNow = now()
            if (!state.complete && (p.expiresAtMillis <= currentNow || p.expiresAtMillis > currentNow + TTL_MILLIS + CLOCK_SKEW_MILLIS)) {
                val skewSec = (p.expiresAtMillis - (currentNow + TTL_MILLIS)) / 1000L
                onLog("Proposal rejected: expired or clock skew (${skewSec}s)", true)
                throw IllegalArgumentException("proposal-expired")
            }
            if (proposal != null && proposal != p) {
                if (autoAcceptFromInviter() && p.firstDevice == peerId && !adoptionPending) {
                    onLog("Inviter replaced pending proposal (${proposal!!.operationId.take(8)} -> ${p.operationId.take(8)})", false)
                    clearPending(recordRejection = false)
                } else {
                    onLog("Rejected: proposal-conflict (local=${proposal!!.operationId.take(8)}, peer=${p.operationId.take(8)})", true)
                    throw IllegalArgumentException("proposal-conflict")
                }
            }
            val isFirst = p.firstDevice == deviceId
            val remoteDigest = if (isFirst) p.secondSnapshot else p.firstSnapshot
            val remoteRevision = if (isFirst) p.secondRevision else p.firstRevision
            val remoteSnapMatch = ProfileTransferCodec.revision(incoming.snapshot) == remoteDigest
            val remoteRevMatch = incoming.revision == remoteRevision
            if (!remoteSnapMatch || !remoteRevMatch) {
                onLog("Rejected: remote-profile-stale (snap=$remoteSnapMatch, rev=$remoteRevMatch)", true)
                throw IllegalArgumentException("remote-profile-stale")
            }
            if (proposal == null) {
                val own = offer()
                val localSnapMatch = ProfileTransferCodec.revision(own.snapshot) == (if (isFirst) p.firstSnapshot else p.secondSnapshot)
                val localRevMatch = own.revision == (if (isFirst) p.firstRevision else p.secondRevision)
                if (!localSnapMatch || !localRevMatch) {
                    onLog("Rejected: local-profile-stale (snap=$localSnapMatch, rev=$localRevMatch)", true)
                    throw IllegalArgumentException("local-profile-stale")
                }
                val checked = if (p.mode == Mode.MERGE) store.preview(incoming.snapshot) else store.previewAdoption(incoming.snapshot)
                val allowed = if (p.mode == Mode.MERGE) checked.canMerge else checked.canAdopt
                if (checked.localRevision != own.revision || !allowed) {
                    onLog("Rejected: profile-conflict-or-stale (conflicts=${checked.conflicts.size})", true)
                    throw IllegalArgumentException("profile-conflict-or-stale")
                }
                proposal = p; preview = checked
            }
            remote = incoming
            remoteApproved = wire.approved
            require(wire.receipt == null || (wire.approved && wire.receipt.matches(HEX))) { "invalid-profile-receipt" }
            remoteReceipt = wire.receipt
            if (autoAcceptFromInviter() && p.firstDevice == peerId && remoteApproved &&
                !localApproved && receipt == null && !adoptionPending) {
                onLog("Auto-applying inviter choice (${p.mode.name})", false)
                if (p.mode != Mode.MERGE && !localIsSource(p)) {
                    store.prepareAdoptionRecipient()
                }
                localApproved = true
            }
            applyIfApproved()
            if (receipt != null && receipt == remoteReceipt) finish()
        } else if (proposal == null) {
            require(!wire.approved && wire.receipt == null && wire.journal == null) { "missing-profile-proposal" }
            val hadPreview = preview != null
            remote = incoming
            val computed = store.preview(incoming.snapshot)
            preview = computed
            if (!hadPreview) {
                onLog(
                    "Snapshot compared: local ${computed.localPacks}d/${computed.localReviews}r, " +
                        "peer ${computed.remotePacks}d/${computed.remoteReviews}r, conflicts=${computed.conflicts.size}",
                    computed.conflicts.isNotEmpty()
                )
            }
        } else {
            // Peer has restarted/cancelled: never silently retain its previous approval.
            remoteApproved = false; remoteReceipt = null
        }
        publish()
    }
    private fun clearCurrentCatchUp() {
        // Validation itself may fail before the incremental actor is entered.
        // Historical last-success is separate from this current exchange claim.
        journalObserved = false
        state = state.copy(journalCaughtUp = false, journalPending = state.complete)
    }
    private fun journalProtocol(): IncrementalJournalProtocol = incremental ?: store.incremental(
        requireNotNull(completion), requireNotNull(peer)).also { incremental = it }

    private fun encode(wire: Wire): ByteArray {
        val protocolVersion = peerWireProtocol
        if (protocolVersion < WIRE_PROTOCOL) {
            val hasPurpose = wire.snapshot?.reviews?.any { it.purpose != null } == true ||
                wire.incremental?.data?.records?.any { it.purpose != null } == true
            if (hasPurpose) throw UnsupportedSyncProtocolException()
        }
        return json.encodeToString(wire.copy(protocol = protocolVersion)).toByteArray(Charsets.UTF_8).also {
            if (it.size > MAX_BYTES) throw dev.ikna.data.export.ProfileTransferLimitException(dev.ikna.data.export.ProfileTransferLimitException.Limit.BYTES)
        }
    }
    private suspend fun persistInvalidation(operationId: String?) {
        // Durable before removing a checkpoint: a crash must not strand the other
        // device with a receipt that this process can no longer invalidate.
        store.saveInvalidatedOperationId(operationId)
        invalidatedOperationId = operationId
    }
    private suspend fun validateCompletion() {
        completion?.let {
            if (it.proposal.protocol != 3 || invalidatedOperationId == it.proposal.operationId || !store.validCompletion(it)) {
                persistInvalidation(it.proposal.operationId)
                completion = null; completionConfirmed = false; store.saveCompletion(null); clearPending()
            }
        }
    }
    private suspend fun finish() {
        completion = Completion(proposal!!, receipt!!, store.committedContentIdentity(proposal!!.operationId))
        store.saveCompletion(completion)
        persistInvalidation(null)
        cancelledOperationId = null
        onLog("Profile sync completed (${proposal!!.mode.name})", false)
        // Retain no raw profile after the bilateral durable receipts are verified.
        local = null; remote = null; preview = null
        completionConfirmed = true
        incremental = null
        journalObserved = false
    }
    private suspend fun applyIfApproved() {
        val p = proposal ?: return
        if (localApproved && remoteApproved && receipt == null && !adoptionPending) {
            require(now() < p.expiresAtMillis) { "proposal-expired" }
            val digest = tupleDigest(p)
            if (p.mode == Mode.MERGE) {
                receipt = store.commit(remote!!.snapshot, preview!!, p.operationId, digest)
                onLog("Merged profile committed locally", false)
            } else {
                receipt = store.adopt(remote!!.snapshot, preview!!, p, localIsSource(p))
                adoptionPending = receipt == null
                onLog(if (adoptionPending) "Profile adoption staged (restart pending)" else "Donor profile committed locally", false)
            }
        }
    }
    private fun localIsSource(p: Proposal): Boolean =
        (p.mode == Mode.ADOPT_THIS && p.firstDevice == deviceId) ||
            (p.mode == Mode.ADOPT_OTHER && p.secondDevice == deviceId)

    companion object {
        /** v4 supports purpose-aware records; v3 peers without optional_hard records remain compatible. */
        const val WIRE_PROTOCOL = 4
        val SUPPORTED_WIRE_PROTOCOLS = setOf(3, WIRE_PROTOCOL)
        fun tupleDigest(proposal: Proposal): String = ProfileTransferCodec.digest(
            Json { encodeDefaults = true }.encodeToString(proposal).toByteArray(Charsets.UTF_8))
        const val TTL_MILLIS = 10 * 60 * 1000L
        /** Tolerates real-world Windows/Android clock drift without allowing stale replays. */
        const val CLOCK_SKEW_MILLIS = 5 * 60 * 1000L
        const val MAX_BYTES = 16 * 1024 * 1024
        private val HEX = Regex("[a-f0-9]{64}")
        private val ID = Regex("[A-Za-z0-9:_-]{1,128}")
    }
}
