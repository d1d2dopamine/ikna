package dev.ikna.sync

import dev.ikna.data.export.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Synthetic event store for transport scheduling tests, not a substitute for Room proofs. */
internal class FakeJournalStore(
    val device: String, val peer: String,
    operation: String = "operation", content: String = hash("content"),
    var pageSize: Int = 2,
) : IncrementalJournalProtocol.Store {
    val binding = JournalStreamBinding(sourceDevice = device, targetDevice = peer, streamId = "stream-$device",
        groupId = "profile", epoch = "epoch", freshStart = false, consentOperationId = operation,
        contentIdentity = content, sourceBoundaryIdentity = hash("boundary-$device"))
    val records = linkedMapOf<String, ReviewRecord>()
    val checkpoints = mutableMapOf<JournalStreamBinding, JournalCursor>()
    val accepted = mutableMapOf<JournalStreamBinding, MutableList<JournalEntry>>()
    var heads = 0
    var commits = 0
    var payloadRecords = 0
    var hasUnindexed = false
    var failApply = false
    var failHead = false
    fun add(id: String, undo: String? = null) {
        records[id] = ReviewRecord(chunkId = "technical-fixture", ts = records.size.toLong(), rating = if (undo == null) 3 else 0,
            eventId = id, undoEventId = undo)
    }
    override suspend fun localHead(): JournalHead { if (failHead) throw JournalReconciliationRequiredException(); heads++; return JournalHead(binding, cursor(entries()), hasUnindexed) }
    override suspend fun checkpoint(remoteHead: JournalHead) = JournalAck(remoteHead.binding,
        checkpoints.getOrPut(remoteHead.binding) { seed(remoteHead.binding) })
    private fun entries() = records.values.mapIndexed { index, r -> JournalEntry(index + 1L, r.eventId!!, digest(r), r.undoEventId) }
    private fun cursor(rows: List<JournalEntry>, scope: JournalStreamBinding = binding): JournalCursor {
        var value = seed(scope)
        rows.forEach { value = extend(value, it) }
        return value
    }
    override suspend fun inventory(after: JournalCursor, highWater: JournalCursor): JournalInventory? {
        val rows = entries()
        require(highWater.sequence <= rows.size && highWater == cursor(rows.take(highWater.sequence.toInt())))
        require(after.sequence <= highWater.sequence && after == cursor(rows.take(after.sequence.toInt())))
        val page = rows.drop(after.sequence.toInt()).take((highWater.sequence - after.sequence).toInt()).take(pageSize)
        if (page.isEmpty()) return null
        return JournalInventory(binding, after, cursor(rows.take(page.last().sequence.toInt())), highWater, page,
            hash(Json.encodeToString(page)))
    }
    override suspend fun missing(inventory: JournalInventory): JournalRequest {
        validate(inventory)
        val ids = inventory.entries.flatMap { listOfNotNull(it.eventId, it.undoEventId) }.distinct()
        inventory.entries.forEach { e -> records[e.eventId]?.let { require(digest(it) == e.payloadDigest) } }
        return JournalRequest(inventory, ids.filter { it !in records })
    }
    override suspend fun payload(request: JournalRequest): JournalData {
        require(request.inventory.binding == binding)
        val result = request.missingIds.map { records.getValue(it) }
        payloadRecords += result.size
        return JournalData(request.inventory, result)
    }
    override suspend fun apply(data: JournalData): JournalAck {
        check(!failApply) { "synthetic-transaction-failure" }
        val page = data.inventory
        validate(page)
        val previous = checkpoints.getOrPut(page.binding) { seed(page.binding) }
        if (page.to.sequence <= previous.sequence) return JournalAck(page.binding, previous, if (page.to == previous) page.pageDigest else null)
        require(page.from == previous)
        val merged = records.toMutableMap()
        data.records.forEach { r ->
            merged[r.eventId]?.let { require(digest(it) == digest(r)) }
            merged[r.eventId!!] = r
        }
        page.entries.forEach { e ->
            val r = merged.getValue(e.eventId)
            require(digest(r) == e.payloadDigest && r.undoEventId == e.undoEventId)
            e.undoEventId?.let { require(it in merged) }
        }
        records.putAll(merged)
        accepted.getOrPut(page.binding) { mutableListOf() }.addAll(page.entries)
        checkpoints[page.binding] = page.to
        commits++
        return JournalAck(page.binding, page.to, page.pageDigest)
    }
    private fun validate(page: JournalInventory) {
        require(page.binding.targetDevice == device && page.binding.sourceDevice == peer)
        require(page.entries.size <= IncrementalJournalLimits.MAX_ENTRIES)
        require(page.pageDigest == hash(Json.encodeToString(page.entries)))
        var cursor = page.from
        page.entries.forEach { require(it.sequence == cursor.sequence + 1); cursor = extend(cursor, it) }
        require(cursor == page.to)
        val previous = checkpoints.getOrPut(page.binding) { seed(page.binding) }
        if (page.to.sequence <= previous.sequence) {
            val history = accepted[page.binding].orEmpty()
            require(page.entries == history.drop(page.from.sequence.toInt()).take(page.entries.size))
        } else require(page.from == previous)
    }
    companion object {
        fun hash(value: String) = ProfileTransferCodec.digest(value.toByteArray())
        fun digest(record: ReviewRecord) = hash(Json.encodeToString(record.copy(id = 0, undoOf = null)))
        fun seed(binding: JournalStreamBinding) = JournalCursor(0, hash("test-seed:" + Json.encodeToString(binding)))
        fun extend(previous: JournalCursor, entry: JournalEntry) = JournalCursor(entry.sequence,
            hash("test-prefix:" + Json.encodeToString(previous) + ":" + Json.encodeToString(entry)))
    }
}
