package dev.ikna.sync

import dev.ikna.data.export.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Bounded transport state only. Identity, prefix proofs and durable ACKs belong to Store. */
class IncrementalJournalProtocol(private val store: Store) {
    interface Store {
        suspend fun localHead(): JournalHead
        suspend fun checkpoint(remoteHead: JournalHead): JournalAck
        /** Also validates the supplied cursor's prefix, including when already at highWater. */
        suspend fun inventory(after: JournalCursor, highWater: JournalCursor): JournalInventory?
        suspend fun missing(inventory: JournalInventory): JournalRequest
        suspend fun payload(request: JournalRequest): JournalData
        /** Validate, add/replay and checkpoint in one transaction; return only after commit. */
        suspend fun apply(data: JournalData): JournalAck
    }
    @Serializable data class Frame(
        val head: JournalHead,
        val inventory: JournalInventory? = null,
        val request: JournalRequest? = null,
        val data: JournalData? = null,
        val ack: JournalAck? = null,
    )
    data class State(
        val outboundCaughtUp: Boolean = false,
        val inboundCaughtUp: Boolean = false,
        val pending: Boolean = true,
    ) { val caughtUp: Boolean get() = outboundCaughtUp && inboundCaughtUp && !pending }

    private val mutex = Mutex()
    private var ownHead: JournalHead? = null
    private var remoteHead: JournalHead? = null
    private var outboundCursor: JournalCursor? = null
    private var inboundCursor: JournalCursor? = null
    private var outbound: JournalInventory? = null
    private var outboundData: JournalData? = null
    private var inbound: JournalRequest? = null
    private var ackToSend: JournalAck? = null
    private var requestToSend: JournalRequest? = null
    private var controlLast = false
    @Volatile var state = State(); private set

    suspend fun snapshot(): Frame = mutex.withLock {
        state = State() // A failed metadata/content check must never retain a caught-up claim.
        val head = refreshLocal()
        if (outbound == null) outboundCursor?.let { outbound = store.inventory(it, head.highWater) }
        val hasOutbound = outbound != null
        val hasControl = ackToSend != null || requestToSend != null
        val frame = if (hasControl && (!hasOutbound || !controlLast)) {
            controlLast = true
            // Requests must not be starved by ACKs queued by every incoming head.
            requestToSend?.let { requestToSend = null; Frame(head, request = it) }
                ?: Frame(head, ack = ackToSend.also { ackToSend = null })
        } else {
            controlLast = false
            outboundData?.let { Frame(head, data = it) } ?: Frame(head, inventory = outbound)
        }
        checkFrame(frame)
        publish()
        frame
    }

    suspend fun receive(frame: Frame) = mutex.withLock {
        state = State()
        checkFrame(frame)
        val own = refreshLocal()
        checkRemoteBinding(own.binding, frame.head.binding)
        remoteHead?.let { old ->
            require(old.binding == frame.head.binding) { "journal-stream-changed" }
            if (old.highWater.sequence == frame.head.highWater.sequence)
                require(old.highWater == frame.head.highWater) { "journal-prefix-conflict" }
        }
        if (remoteHead == null || frame.head.highWater.sequence >= remoteHead!!.highWater.sequence)
            remoteHead = frame.head
        // Resume ACKs are durable receiver checkpoints, never estimates from received frames.
        val checkpoint = store.checkpoint(requireNotNull(remoteHead))
        require(checkpoint.binding == frame.head.binding) { "journal-checkpoint-binding" }
        rememberInbound(checkpoint)
        ackToSend = checkpoint
        frame.ack?.let { receiveAck(it, own) }
        frame.inventory?.let { receiveInventory(it, frame.head) }
        frame.request?.let { request ->
            val page = outbound
            require(request.inventory.binding == own.binding) { "journal-request-binding" }
            if (page == request.inventory) {
                require(request.missingIds.size <= IncrementalJournalLimits.MAX_ENTRIES &&
                    request.missingIds.distinct().size == request.missingIds.size) { "journal-request-size" }
                val data = store.payload(request)
                require(data.inventory == page) { "journal-payload-inventory" }
                checkFrame(Frame(own, data = data))
                outboundData = data
            } else {
                // A delayed request for an already acknowledged page cannot replace current work.
                require(request.inventory.to.sequence <= (outboundCursor?.sequence ?: -1)) { "journal-unrequested-page" }
            }
        }
        frame.data?.let { data ->
            checkSourcePage(data.inventory, frame.head)
            // Either peer may restart with a newer fixed high-water after a request.
            // Only the durable contiguous-prefix check in apply authorizes a retried
            // page. Transient request identity is not a commit prerequisite.
            val ack = store.apply(data)
            require(ack.binding == data.inventory.binding && ack.cursor.sequence >= data.inventory.to.sequence) {
                "journal-incomplete-commit"
            }
            // Imported rows also extend our outgoing stream. Never compare its
            // old head with the peer ACK and report caught-up before those echoes
            // have been advertised and durably acknowledged.
            refreshLocal()
            rememberInbound(ack)
            ackToSend = ack
            if (inbound?.inventory?.from?.sequence?.let { it < ack.cursor.sequence } == true) { inbound = null; requestToSend = null }
        }
        publish()
    }

    private suspend fun receiveAck(ack: JournalAck, head: JournalHead) {
        require(ack.binding == head.binding) { "journal-ack-binding" }
        val old = outboundCursor
        if (old != null && ack.cursor.sequence < old.sequence) return
        if (old == ack.cursor) return
        require(ack.cursor.sequence <= head.highWater.sequence) { "journal-ack-ahead" }
        outbound?.let { page ->
            if (ack.cursor == page.to && ack.pageDigest != null)
                require(ack.pageDigest == page.pageDigest) { "journal-ack-page" }
        }
        // This repository read validates the source prefix even for an initial resume ACK.
        val next = store.inventory(ack.cursor, head.highWater)
        outboundCursor = ack.cursor
        outbound = next
        outboundData = null
    }

    private suspend fun receiveInventory(page: JournalInventory, advertised: JournalHead) {
        checkSourcePage(page, advertised)
        val request = store.missing(page)
        require(request.inventory == page) { "journal-request-inventory" }
        if (request.missingIds.isEmpty()) {
            val ack = store.apply(JournalData(page, emptyList()))
            require(ack.binding == page.binding && ack.cursor.sequence >= page.to.sequence) { "journal-incomplete-commit" }
            // Imported rows also extend our outgoing stream. Never compare its
            // old head with the peer ACK and report caught-up before those echoes
            // have been advertised and durably acknowledged.
            refreshLocal()
            rememberInbound(ack)
            ackToSend = ack
            if (inbound?.inventory?.from?.sequence?.let { it < ack.cursor.sequence } == true) { inbound = null; requestToSend = null }
        } else {
            // missing() verified this page against the durable prior prefix. A
            // restarted source can propose a different fixed high-water from that
            // same prefix; replace the one bounded transient request, never append.
            inbound = request
            requestToSend = request
        }
    }

    private fun checkSourcePage(page: JournalInventory, head: JournalHead) {
        require(page.binding == head.binding && page.highWater.sequence <= head.highWater.sequence &&
            (page.highWater.sequence != head.highWater.sequence || page.highWater == head.highWater)) {
            "journal-inventory-binding"
        }
    }

    private suspend fun refreshLocal(): JournalHead = store.localHead().also { head ->
        if (head.binding.protocol != 3) throw UnsupportedSyncProtocolException()
        ownHead?.let { old ->
            require(old.binding == head.binding && head.highWater.sequence >= old.highWater.sequence) { "journal-local-stream-changed" }
            if (head.highWater.sequence == old.highWater.sequence) require(head.highWater == old.highWater) { "journal-prefix-conflict" }
        }
        ownHead = head
    }
    private fun rememberInbound(ack: JournalAck) {
        inboundCursor?.let { old ->
            require(ack.cursor.sequence >= old.sequence) { "journal-checkpoint-regressed" }
            if (ack.cursor.sequence == old.sequence) require(ack.cursor == old) { "journal-prefix-conflict" }
        }
        inboundCursor = ack.cursor
    }
    private fun publish() {
        val outgoing = ownHead?.let { !it.hasUnindexed && outboundCursor == it.highWater && outbound == null } == true
        val incoming = remoteHead?.let { !it.hasUnindexed && (inboundCursor?.sequence ?: -1) >= it.highWater.sequence && inbound == null } == true
        state = State(outgoing, incoming, !outgoing || !incoming)
    }
    private fun checkRemoteBinding(local: JournalStreamBinding, remote: JournalStreamBinding) {
        if (remote.protocol != 3) throw UnsupportedSyncProtocolException()
        require(remote.sourceDevice == local.targetDevice && remote.targetDevice == local.sourceDevice &&
            remote.groupId == local.groupId && remote.epoch == local.epoch && remote.freshStart == local.freshStart &&
            remote.consentOperationId == local.consentOperationId && remote.contentIdentity == local.contentIdentity) {
            "journal-scope-mismatch"
        }
    }
    private fun checkFrame(frame: Frame) {
        if (frame.head.binding.protocol != 3) throw UnsupportedSyncProtocolException()
        require(listOf(frame.inventory, frame.request, frame.data, frame.ack).count { it != null } <= 1) { "journal-multiple-messages" }
        val page = frame.inventory ?: frame.request?.inventory ?: frame.data?.inventory
        page?.let {
            require(it.entries.size + it.dependencies.size <= IncrementalJournalLimits.MAX_ENTRIES && it.entries.isNotEmpty() &&
                it.from.sequence >= 0 && it.to.sequence > it.from.sequence && it.highWater.sequence >= it.to.sequence) { "journal-page-size" }
        }
        frame.request?.let { request ->
            val allowed = (request.inventory.entries.map { it.eventId } + request.inventory.dependencies.map { it.eventId }).toSet()
            require(request.missingIds.size <= IncrementalJournalLimits.MAX_ENTRIES &&
                request.missingIds.distinct().size == request.missingIds.size && request.missingIds.all { it in allowed }) {
                "journal-request-size"
            }
        }
        require((frame.data?.records?.size ?: 0) <= IncrementalJournalLimits.MAX_ENTRIES) { "journal-dependency-size" }
        if (JSON.encodeToString(frame).toByteArray(Charsets.UTF_8).size > IncrementalJournalLimits.MAX_FRAME_BYTES)
            throw IncrementalJournalLimitException()
    }
    companion object {
        // Match the repository's canonical page budget and the enclosing v3 wire.
        // Explicit nulls would add hundreds of bytes per event after sizing.
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        private val JSON = Json { encodeDefaults = true; explicitNulls = false }
    }
}
