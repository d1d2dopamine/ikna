package dev.ikna.data.repo

import androidx.room.useWriterConnection
import dev.ikna.data.db.*
import dev.ikna.data.export.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.encodeToString
import java.util.UUID
import kotlin.coroutines.CoroutineContext

/** Consent-scoped derived index. Reviews stay append-only and are never renumbered.
 * Each wire page is bounded; a changed page still replays the complete active journal.
 * This deliberately does not claim bounded replay CPU/memory. */
class IncrementalJournalRepository(private val db: IknaDatabase, private val restore: RestoreRepository) {
    private val dao get() = db.incrementalJournalDao()
    private val reviews get() = db.reviewDao()
    private val json = ReviewRecord.json

    /** Caller already holds the learning guard and Room writer transaction. Only explicit
     * consent invokes this: migration or first connection must never manufacture approval. */
    internal suspend fun bootstrapInTransaction(auth: JournalAuthorization, afterLocalId: Long,
                                                boundaryToken: String?, caller: CoroutineContext) {
        validateAuth(auth)
        caller.ensureActive()
        restore.validateSyncBoundary(afterLocalId, boundaryToken)
        val receipt = requireNotNull(db.profileSyncOperationDao().find(auth.consentOperationId)) { "Missing journal consent" }
        require(receipt.operationDigest == auth.operationDigest && receipt.boundaryIdentity == boundary(afterLocalId, boundaryToken)) {
            "Journal consent differs"
        }
        // Same unchanged bootstrap cap as profile transfer, before any full legacy load.
        db.preflightProfileTransfer(afterLocalId, caller)
        val content = restore.profileTransfer.contentIdentityInTransaction(afterLocalId, boundaryToken, caller)
        require(content == receipt.resultingContentIdentity) { "Profile content changed" }
        val rows = reviews.afterId(afterLocalId)
        caller.ensureActive()
        val canonical = JournalIdentity.normalize(rows.map { ReviewRecord.of(it) })
        restore.validateJournalRecords(canonical)
        val ids = canonical.map { it.eventId!! }.toSet()
        canonical.forEach { record ->
            caller.ensureActive()
            record.undoEventId?.let { require(it in ids) { "Inactive undo dependency" } }
        }
        val binding = JournalStreamBinding(sourceDevice = auth.localDeviceId, targetDevice = auth.peerDeviceId,
            streamId = UUID.randomUUID().toString(), groupId = auth.groupId, epoch = auth.epoch,
            freshStart = auth.freshStart, consentOperationId = auth.consentOperationId,
            contentIdentity = content, sourceBoundaryIdentity = boundary(afterLocalId, boundaryToken))
        dao.invalidate()
        var stream = JournalStreamEntity(authorizationDigest = authDigest(auth), bindingJson = json.encodeToString(binding),
            scanThrough = reviews.maxId(), sequence = 0, prefix = seed(binding), eventCount = 0, durationMs = 0)
        for ((row, record) in rows.zip(canonical).sortedBy { it.first.id }) {
            caller.ensureActive()
            stream = appendIndex(stream, row.id, record, caller)
        }
        dao.putStream(stream)
        caller.ensureActive()
    }

    internal suspend fun requireAuthorizationInTransaction(auth: JournalAuthorization, afterLocalId: Long,
                                                            boundaryToken: String?, caller: CoroutineContext): JournalStreamEntity {
        validateAuth(auth)
        caller.ensureActive()
        restore.validateSyncBoundary(afterLocalId, boundaryToken)
        val receipt = requireNotNull(db.profileSyncOperationDao().find(auth.consentOperationId)) { "Missing journal consent" }
        val stream = dao.stream() ?: throw JournalReconciliationRequiredException()
        val binding = binding(stream)
        require(receipt.operationDigest == auth.operationDigest && receipt.boundaryIdentity == boundary(afterLocalId, boundaryToken) &&
            stream.authorizationDigest == authDigest(auth) && binding.protocol == 3 &&
            binding.sourceDevice == auth.localDeviceId && binding.targetDevice == auth.peerDeviceId &&
            binding.groupId == auth.groupId && binding.epoch == auth.epoch && binding.freshStart == auth.freshStart &&
            binding.consentOperationId == auth.consentOperationId && binding.sourceBoundaryIdentity == receipt.boundaryIdentity &&
            binding.contentIdentity == receipt.resultingContentIdentity) { "Journal scope changed" }
        require(restore.profileTransfer.contentIdentityInTransaction(afterLocalId, boundaryToken, caller) == binding.contentIdentity) {
            "Profile content changed; review again"
        }
        if (stream.sequence > 0) {
            val tail = dao.at(binding.streamId, stream.sequence) ?: throw JournalReconciliationRequiredException()
            if (tail.prefix != stream.prefix) throw JournalReconciliationRequiredException()
        } else if (stream.prefix != seed(binding)) throw JournalReconciliationRequiredException()
        rejectUnmappedLegacy(stream, afterLocalId, caller)
        caller.ensureActive()
        return stream
    }

    suspend fun localHead(auth: JournalAuthorization, afterLocalId: Long = 0, boundaryToken: String? = null): JournalHead =
        transaction { caller ->
            var stream = requireAuthorizationInTransaction(auth, afterLocalId, boundaryToken, caller)
            val highWater = reviews.maxId()
            // Scalar lengths and ids only. Oversized raw text is rejected before entity loading.
            val rawIds = boundedRawIds(stream.scanThrough, highWater, caller)
            for (id in rawIds) {
                caller.ensureActive()
                if (dao.raw(binding(stream).streamId, id) == null) {
                    val row = requireNotNull(loadRow(id)) { "Journal row disappeared" }
                    require(row.eventId != null) { "New legacy history requires explicit approval" }
                    val record = canonicalRow(stream, row, afterLocalId, caller)
                    validateActiveRecord(stream, record, afterLocalId, emptyMap(), caller)
                    stream = appendIndex(stream, id, record, caller)
                }
                stream = stream.copy(scanThrough = id)
            }
            dao.putStream(stream)
            JournalHead(binding(stream), cursor(stream), stream.scanThrough < highWater)
        }

    suspend fun checkpoint(auth: JournalAuthorization, remoteHead: JournalHead, afterLocalId: Long = 0,
                           boundaryToken: String? = null): JournalAck = transaction { caller ->
        val stream = requireAuthorizationInTransaction(auth, afterLocalId, boundaryToken, caller)
        val checkpoint = checkpointFor(stream, remoteHead.binding, caller)
        validateCursor(remoteHead.highWater)
        require(remoteHead.highWater.sequence >= checkpoint.sequence) { "Remote highwater regressed" }
        if (remoteHead.highWater.sequence == checkpoint.sequence) require(remoteHead.highWater.prefix == checkpoint.prefix) { "Remote prefix changed" }
        JournalAck(remoteHead.binding, JournalCursor(checkpoint.sequence, checkpoint.prefix))
    }

    suspend fun inventory(auth: JournalAuthorization, after: JournalCursor, highWater: JournalCursor,
                          afterLocalId: Long = 0, boundaryToken: String? = null): JournalInventory? = transaction { caller ->
        val stream = requireAuthorizationInTransaction(auth, afterLocalId, boundaryToken, caller)
        verifyLocalCursor(stream, after); verifyLocalCursor(stream, highWater)
        require(after.sequence <= highWater.sequence) { "Invalid journal range" }
        if (after == highWater) return@transaction null
        val binding = binding(stream)
        val indexed = dao.page(binding.streamId, after.sequence, highWater.sequence, IncrementalJournalLimits.MAX_ENTRIES)
        require(indexed.isNotEmpty()) { "Journal index gap" }
        val selected = mutableListOf<JournalEventIndexEntity>()
        val records = linkedMapOf<String, ReviewRecord>()
        var result: JournalInventory? = null
        for (index in indexed) {
            caller.ensureActive()
            require(index.sequence == after.sequence + selected.size + 1L) { "Journal index gap" }
            val row = indexedRecord(stream, index, afterLocalId, caller)
            val additions = linkedMapOf(row.eventId!! to row)
            row.undoEventId?.let { id -> additions[id] = requireNotNull(known(stream, id, afterLocalId, caller)).second }
            val candidateRows = records + additions
            val dependencies = (selected + index).mapNotNull { it.undoEventId }.distinct().map { id ->
                JournalDependency(id, digest(candidateRows.getValue(id)))
            }
            if (selected.size + 1 + dependencies.size > IncrementalJournalLimits.MAX_ENTRIES) break
            val candidate = makeInventory(binding, after, highWater, selected + index, dependencies)
            val size = json.encodeToString(JournalData(candidate, candidateRows.values.toList())).toByteArray(Charsets.UTF_8).size
            if (size > IncrementalJournalLimits.MAX_DATA_BYTES) {
                if (selected.isEmpty()) throw IncrementalJournalLimitException()
                break
            }
            selected += index
            records.putAll(additions)
            result = candidate
        }
        requireNotNull(result)
    }

    suspend fun missing(auth: JournalAuthorization, inventory: JournalInventory, afterLocalId: Long = 0,
                        boundaryToken: String? = null): JournalRequest = transaction { caller ->
        val stream = requireAuthorizationInTransaction(auth, afterLocalId, boundaryToken, caller)
        validateInventory(stream, inventory, caller)
        val wanted = inventory.entries.map { JournalDependency(it.eventId, it.payloadDigest) } + inventory.dependencies
        val missing = wanted.distinctBy { it.eventId }.filter { entry ->
            caller.ensureActive()
            val local = known(stream, entry.eventId, afterLocalId, caller)
            if (local != null) require(digest(local.second) == entry.payloadDigest) { "Conflicting journal payload" }
            local == null
        }.map { it.eventId }
        JournalRequest(inventory, missing)
    }

    suspend fun payload(auth: JournalAuthorization, request: JournalRequest, afterLocalId: Long = 0,
                        boundaryToken: String? = null): JournalData = transaction { caller ->
        val stream = requireAuthorizationInTransaction(auth, afterLocalId, boundaryToken, caller)
        val inv = request.inventory
        require(inv.binding == binding(stream)) { "Wrong journal direction" }
        validateInventoryShape(inv)
        verifyLocalCursor(stream, inv.from); verifyLocalCursor(stream, inv.to); verifyLocalCursor(stream, inv.highWater)
        require(request.missingIds.size == request.missingIds.toSet().size && request.missingIds.all { id -> inv.entries.any { it.eventId == id } || inv.dependencies.any { it.eventId == id } }) {
            "Invalid journal request"
        }
        val output = linkedMapOf<String, ReviewRecord>()
        for (entry in inv.entries) {
            val index = requireNotNull(dao.at(inv.binding.streamId, entry.sequence)) { "Journal index gap" }
            require(entry == entry(index)) { "Inventory changed" }
            if (entry.eventId in request.missingIds) {
                val row = indexedRecord(stream, index, afterLocalId, caller)
                output[entry.eventId] = row
                row.undoEventId?.let { id ->
                    val dependency = requireNotNull(known(stream, id, afterLocalId, caller)).second
                    require(inv.dependencies.single { it.eventId == id }.payloadDigest == digest(dependency)) { "Dependency proof changed" }
                    output[id] = dependency
                }
            }
        }
        for (dependency in inv.dependencies.filter { it.eventId in request.missingIds }) {
            val row = requireNotNull(known(stream, dependency.eventId, afterLocalId, caller)).second
            require(digest(row) == dependency.payloadDigest) { "Dependency proof changed" }
            output[dependency.eventId] = row
        }
        JournalData(inv, output.values.toList()).also { requireDataSize(it); caller.ensureActive() }
    }

    suspend fun apply(auth: JournalAuthorization, data: JournalData, afterLocalId: Long = 0,
                      boundaryToken: String? = null): JournalApplyResult {
        // Capture before either the learning mutex or Room changes context.
        val caller = currentCoroutineContext()
        caller.ensureActive()
        var result: JournalApplyResult? = null
        restore.journalMergeGuard {
            db.inTransaction {
                caller.ensureActive()
                var stream = requireAuthorizationInTransaction(auth, afterLocalId, boundaryToken, caller)
                val checkpoint = validateInventory(stream, data.inventory, caller)
                requireDataSize(data)
                require(data.records.size <= IncrementalJournalLimits.MAX_ENTRIES &&
                    data.records.map { it.eventId }.toSet().size == data.records.size) { "Invalid journal payload set" }
                val normalized = restore.validateJournalRecords(data.records)
                require(normalized == data.records) { "Journal payload is not canonical" }
                val supplied = normalized.associateBy { it.eventId!! }
                val expected = data.inventory.entries.associateBy { it.eventId }
                val dependencies = data.inventory.dependencies.associateBy { it.eventId }
                require(supplied.keys.all { it in expected || it in dependencies }) { "Unexpected journal payload" }
                val resolved = linkedMapOf<String, Pair<Long?, ReviewRecord>>()
                // Budget the complete hypothetical page, including already-known rows.
                // This is the same JSON envelope/record encoding as the honest sender.
                var resolvedBytes = json.encodeToString(JournalData(data.inventory, emptyList())).toByteArray(Charsets.UTF_8).size

                for (id in expected.keys + supplied.keys + dependencies.keys) {
                    caller.ensureActive()
                    val old = known(stream, id, afterLocalId, caller)
                    val incoming = supplied[id]
                    require(old != null || incoming != null) { "Missing requested journal payload" }
                    if (old != null && incoming != null) require(old.second == incoming) { "Conflicting journal payload" }
                    val row = old?.second ?: incoming!!
                    val encodedBytes = json.encodeToString(row).toByteArray(Charsets.UTF_8).size
                    val separator = if (resolved.isEmpty()) 0 else 1
                    require(encodedBytes <= IncrementalJournalLimits.MAX_DATA_BYTES - resolvedBytes - separator) {
                        "Resolved journal page exceeds byte limit"
                    }
                    resolvedBytes += encodedBytes + separator
                    resolved[id] = old?.first to row
                }
                for ((id, value) in resolved) {
                    val row = value.second
                    dependencies[id]?.let { require(it.payloadDigest == digest(row)) { "Journal dependency digest mismatch" } }
                    expected[id]?.let { require(it.payloadDigest == digest(row) && it.undoEventId == row.undoEventId) { "Journal digest mismatch" } }
                    validateActiveRecord(stream, row, afterLocalId, resolved.mapValues { it.value.second }, caller)
                }
                // A duplicate page may only acknowledge an already committed exact index range.
                if (data.inventory.to.sequence <= checkpoint.sequence) {
                    require(resolved.values.all { it.first != null }) { "Duplicate page lost committed history" }
                    result = JournalApplyResult(JournalAck(data.inventory.binding, JournalCursor(checkpoint.sequence, checkpoint.prefix), null), 0)
                    caller.ensureActive()
                    return@inTransaction RestoreResult(0, data.inventory.entries.size, 0)
                }
                val additions = resolved.values.filter { it.first == null }.map { it.second }
                    .sortedWith(compareBy<ReviewRecord> { it.undoEventId != null }.thenBy { it.ts }.thenBy { it.eventId })
                if (additions.isNotEmpty()) checkActiveTotals(afterLocalId, additions, caller)
                val localIds = resolved.mapNotNull { (id, pair) -> pair.first?.let { id to it } }.toMap().toMutableMap()
                for (row in additions) {
                    caller.ensureActive()
                    val targetId = row.undoEventId?.let { id -> localIds[id] ?: known(stream, id, afterLocalId, caller)?.first
                        ?: error("Missing undo dependency") }
                    val rawId = reviews.insert(row.toEntity(undoOf = targetId))
                    localIds[row.eventId!!] = rawId
                    stream = appendIndex(stream, rawId, row, caller)
                }
                dao.putStream(stream)
                var prefix = data.inventory.from.prefix
                for (entry in data.inventory.entries) {
                    caller.ensureActive()
                    prefix = chain(prefix, entry)
                    val rawId = localIds[entry.eventId] ?: requireNotNull(known(stream, entry.eventId, afterLocalId, caller)).first
                    dao.insertIndex(JournalEventIndexEntity(data.inventory.binding.streamId, entry.sequence, entry.eventId,
                        entry.payloadDigest, entry.undoEventId, rawId, prefix))
                }
                val replayed = if (additions.isEmpty()) 0 else restore.replayFromLogBody(caller, sync = true, afterLocalId = afterLocalId, journalStreamId = binding(stream).streamId)
                    .also { db.planDao().clear() }
                dao.putCheckpoint(checkpoint.copy(sequence = data.inventory.to.sequence, prefix = data.inventory.to.prefix))
                result = JournalApplyResult(JournalAck(data.inventory.binding, data.inventory.to, data.inventory.pageDigest), additions.size)
                caller.ensureActive()
                RestoreResult(additions.size, data.inventory.entries.size - additions.size, replayed)
            }
        }
        caller.ensureActive()
        return checkNotNull(result)
    }

    private suspend fun <T> transaction(block: suspend (CoroutineContext) -> T): T {
        val caller = currentCoroutineContext()
        caller.ensureActive()
        return db.inTransaction { block(caller).also { caller.ensureActive() } }
    }

    private suspend fun appendIndex(stream: JournalStreamEntity, rawId: Long, row: ReviewRecord,
                                    caller: CoroutineContext): JournalStreamEntity {
        caller.ensureActive()
        val next = Math.addExact(stream.sequence, 1)
        val count = Math.addExact(stream.eventCount, 1)
        require(count <= 1_000_000) { "Journal exceeds supported event count" }
        val duration = Math.addExact(stream.durationMs, row.durationMs)
        val entry = JournalEntry(next, requireNotNull(row.eventId), digest(row), row.undoEventId)
        val prefix = chain(stream.prefix, entry)
        dao.insertIndex(JournalEventIndexEntity(binding(stream).streamId, next, entry.eventId,
            entry.payloadDigest, entry.undoEventId, rawId, prefix))
        return stream.copy(sequence = next, prefix = prefix, eventCount = count, durationMs = duration)
    }

    private suspend fun boundedRawIds(after: Long, highWater: Long, caller: CoroutineContext): List<Long> {
        val ids = mutableListOf<Long>()
        db.useWriterConnection { connection ->
            val lengths = listOf("chunkId", "timingDiscardReason", "gradingReason", "inputMethod", "peekSemantics", "fsrsParameters", "eventId", "undoEventId", "purpose")
                .joinToString(" + ") { "COALESCE(length(CAST($it AS BLOB)),0)" }
            connection.usePrepared("SELECT id, $lengths FROM reviews WHERE id > ? AND id <= ? ORDER BY id LIMIT ${IncrementalJournalLimits.MAX_ENTRIES}") { statement ->
                statement.bindLong(1, after); statement.bindLong(2, highWater)
                while (statement.step()) {
                    caller.ensureActive()
                    if (statement.getLong(1) > IncrementalJournalLimits.MAX_DATA_BYTES) throw IncrementalJournalLimitException()
                    ids += statement.getLong(0)
                }
            }
        }
        return ids
    }

    private suspend fun loadRow(id: Long): ReviewEntity? {
        db.useWriterConnection { connection ->
            val lengths = listOf("chunkId", "timingDiscardReason", "gradingReason", "inputMethod", "peekSemantics", "fsrsParameters", "eventId", "undoEventId", "purpose")
                .joinToString(" + ") { "COALESCE(length(CAST($it AS BLOB)),0)" }
            connection.usePrepared("SELECT $lengths FROM reviews WHERE id = ?") { statement ->
                statement.bindLong(1, id)
                if (statement.step() && statement.getLong(0) > IncrementalJournalLimits.MAX_DATA_BYTES) throw IncrementalJournalLimitException()
            }
        }
        return reviews.byId(id)
    }

    private suspend fun rejectUnmappedLegacy(stream: JournalStreamEntity, cutoff: Long, caller: CoroutineContext) {
        db.useWriterConnection { connection ->
            connection.usePrepared("SELECT 1 FROM reviews r WHERE r.id > ? AND r.eventId IS NULL AND NOT EXISTS (SELECT 1 FROM journal_event_index i WHERE i.streamId = ? AND i.reviewId = r.id) LIMIT 1") { statement ->
                statement.bindLong(1, cutoff); statement.bindText(2, binding(stream).streamId)
                caller.ensureActive()
                if (statement.step()) throw JournalReconciliationRequiredException()
            }
        }
    }

    private suspend fun checkActiveTotals(cutoff: Long, additions: List<ReviewRecord>, caller: CoroutineContext) {
        db.useWriterConnection { connection ->
            connection.usePrepared("SELECT COUNT(*), COALESCE(SUM(durationMs),0) FROM reviews WHERE id > ?") { statement ->
                statement.bindLong(1, cutoff); check(statement.step())
                require(Math.addExact(statement.getLong(0), additions.size.toLong()) <= 1_000_000) { "Journal exceeds supported event count" }
                var duration = statement.getLong(1)
                additions.forEach { caller.ensureActive(); duration = Math.addExact(duration, it.durationMs) }
            }
        }
    }

    private suspend fun canonicalRow(stream: JournalStreamEntity, row: ReviewEntity, cutoff: Long,
                                     caller: CoroutineContext): ReviewRecord {
        require(row.id > cutoff) { "Archived journal dependency" }
        caller.ensureActive()
        val indexed = dao.raw(binding(stream).streamId, row.id)
        if (indexed != null && ((row.eventId != null && row.eventId != indexed.eventId) ||
                (row.undoEventId != null && row.undoEventId != indexed.undoEventId))) {
            throw JournalReconciliationRequiredException()
        }
        val rawTarget = row.undoOf?.let { raw ->
            require(raw > cutoff) { "Archived undo dependency" }
            dao.raw(binding(stream).streamId, raw)?.eventId ?: loadRow(raw)?.eventId
                ?: throw JournalReconciliationRequiredException()
        }
        if (rawTarget != null && ((row.undoEventId != null && row.undoEventId != rawTarget) ||
                (indexed != null && indexed.undoEventId != rawTarget))) {
            throw JournalReconciliationRequiredException()
        }
        val id = indexed?.eventId ?: requireNotNull(row.eventId) { "Unmapped legacy journal" }
        val target = indexed?.undoEventId ?: row.undoEventId ?: rawTarget
        if ((target != null) != (row.undoOf != null)) throw JournalReconciliationRequiredException()
        val value = JournalIdentity.canonical(ReviewRecord.of(row)).copy(eventId = id, undoEventId = target)
        if (indexed != null && digest(value) != indexed.payloadDigest) throw JournalReconciliationRequiredException()
        return value
    }

    private suspend fun indexedRecord(stream: JournalStreamEntity, index: JournalEventIndexEntity, cutoff: Long,
                                      caller: CoroutineContext): ReviewRecord = canonicalRow(stream,
        requireNotNull(loadRow(index.reviewId)) { "Indexed journal disappeared" }, cutoff, caller).also {
        require(it.eventId == index.eventId && digest(it) == index.payloadDigest && it.undoEventId == index.undoEventId) { "Indexed journal changed" }
    }

    private suspend fun known(stream: JournalStreamEntity, id: String, cutoff: Long,
                              caller: CoroutineContext): Pair<Long, ReviewRecord>? {
        caller.ensureActive()
        val index = dao.event(binding(stream).streamId, id)
        if (index != null) return index.reviewId to indexedRecord(stream, index, cutoff, caller)
        var rawId: Long? = null
        db.useWriterConnection { connection ->
            connection.usePrepared("SELECT id FROM reviews WHERE eventId = ? AND id > ?") { statement ->
                statement.bindText(1, id); statement.bindLong(2, cutoff)
                if (statement.step()) rawId = statement.getLong(0)
            }
        }
        val row = rawId?.let { loadRow(it) } ?: return null
        return row.id to canonicalRow(stream, row, cutoff, caller)
    }

    private suspend fun validateActiveRecord(stream: JournalStreamEntity, row: ReviewRecord, cutoff: Long,
                                             supplied: Map<String, ReviewRecord>, caller: CoroutineContext) {
        restore.validateJournalRecords(listOf(row))
        caller.ensureActive()
        require(db.chunkDao().chunk(row.chunkId) != null) { "Missing journal target" }
        row.undoEventId?.let { id ->
            val original = supplied[id] ?: known(stream, id, cutoff, caller)?.second
            require(original != null && original.undoEventId == null && original.chunkId == row.chunkId && original.level == row.level && original.purpose == row.purpose) {
                "Missing or invalid active undo target"
            }
        }
    }

    private suspend fun checkpointFor(stream: JournalStreamEntity, remote: JournalStreamBinding,
                                      caller: CoroutineContext): JournalCheckpointEntity {
        val local = binding(stream)
        validateBinding(remote)
        require(remote.sourceDevice == local.targetDevice && remote.targetDevice == local.sourceDevice &&
            remote.groupId == local.groupId && remote.epoch == local.epoch && remote.freshStart == local.freshStart &&
            remote.consentOperationId == local.consentOperationId && remote.contentIdentity == local.contentIdentity &&
            remote.streamId != local.streamId) { "Wrong remote journal scope" }
        val old = dao.checkpoint(remote.sourceDevice)
        if (old != null) {
            require(old.bindingJson == json.encodeToString(remote)) { "Remote journal stream changed" }
            return old
        }
        caller.ensureActive()
        return JournalCheckpointEntity(remote.sourceDevice, json.encodeToString(remote), 0, seed(remote)).also { dao.putCheckpoint(it) }
    }

    private suspend fun validateInventory(stream: JournalStreamEntity, inv: JournalInventory,
                                          caller: CoroutineContext): JournalCheckpointEntity {
        validateInventoryShape(inv)
        val checkpoint = checkpointFor(stream, inv.binding, caller)
        if (inv.to.sequence <= checkpoint.sequence) {
            val from = if (inv.from.sequence == 0L) seed(inv.binding) else dao.at(inv.binding.streamId, inv.from.sequence)?.prefix
            require(from == inv.from.prefix) { "Duplicate prefix differs" }
            for (entry in inv.entries) {
                caller.ensureActive()
                val stored = dao.at(inv.binding.streamId, entry.sequence)
                require(stored != null && entry(stored) == entry) { "Duplicate inventory differs" }
            }
            require(dao.at(inv.binding.streamId, inv.to.sequence)?.prefix == inv.to.prefix) { "Duplicate prefix differs" }
        } else require(inv.from == JournalCursor(checkpoint.sequence, checkpoint.prefix)) { "Journal cursor gap or overlap" }
        return checkpoint
    }

    private fun validateInventoryShape(inv: JournalInventory) {
        validateBinding(inv.binding)
        validateCursor(inv.from); validateCursor(inv.to); validateCursor(inv.highWater)
        require(inv.entries.isNotEmpty() && inv.entries.size <= IncrementalJournalLimits.MAX_ENTRIES &&
            inv.to.sequence - inv.from.sequence == inv.entries.size.toLong() && inv.to.sequence <= inv.highWater.sequence &&
            inv.entries.map { it.eventId }.toSet().size == inv.entries.size) { "Invalid journal inventory" }
        if (inv.to.sequence == inv.highWater.sequence) require(inv.to.prefix == inv.highWater.prefix) { "Highwater prefix differs" }
        var prefix = inv.from.prefix
        inv.entries.forEachIndexed { offset, entry ->
            require(entry.sequence == inv.from.sequence + offset + 1L && validId(entry.eventId) && validDigest(entry.payloadDigest) &&
                (entry.undoEventId == null || validId(entry.undoEventId))) { "Invalid journal entry" }
            prefix = chain(prefix, entry)
        }
        require(inv.dependencies.size + inv.entries.size <= IncrementalJournalLimits.MAX_ENTRIES &&
            inv.dependencies.map { it.eventId }.toSet().size == inv.dependencies.size &&
            inv.dependencies.map { it.eventId }.toSet() == inv.entries.mapNotNull { it.undoEventId }.toSet() &&
            inv.dependencies.all { validId(it.eventId) && validDigest(it.payloadDigest) }) { "Invalid dependency proof" }
        require(prefix == inv.to.prefix && inv.pageDigest == inventoryDigest(inv)) { "Invalid journal proof" }
    }

    private suspend fun verifyLocalCursor(stream: JournalStreamEntity, value: JournalCursor) {
        validateCursor(value)
        require(value.sequence <= stream.sequence) { "Unknown source highwater" }
        val expected = if (value.sequence == 0L) seed(binding(stream)) else dao.at(binding(stream).streamId, value.sequence)?.prefix
        require(value.prefix == expected) { "Source cursor differs" }
    }

    private fun makeInventory(binding: JournalStreamBinding, from: JournalCursor, highWater: JournalCursor,
                              rows: List<JournalEventIndexEntity>, dependencies: List<JournalDependency>): JournalInventory {
        val last = rows.last()
        val value = JournalInventory(binding, from, JournalCursor(last.sequence, last.prefix), highWater, rows.map(::entry), "", dependencies)
        return value.copy(pageDigest = inventoryDigest(value))
    }
    private fun entry(row: JournalEventIndexEntity) = JournalEntry(row.sequence, row.eventId, row.payloadDigest, row.undoEventId)
    private fun cursor(row: JournalStreamEntity) = JournalCursor(row.sequence, row.prefix)
    private fun binding(row: JournalStreamEntity) = json.decodeFromString(JournalStreamBinding.serializer(), row.bindingJson)
    private fun digest(row: ReviewRecord) = hash(json.encodeToString(JournalIdentity.canonical(row)))
    private fun seed(binding: JournalStreamBinding) = hash("ikna-journal-v3\n" + json.encodeToString(binding))
    private fun chain(prefix: String, entry: JournalEntry) = hash(prefix + "\n" + json.encodeToString(entry))
    private fun inventoryDigest(value: JournalInventory) = hash(json.encodeToString(value.copy(pageDigest = "")))
    private fun hash(value: String) = ProfileTransferCodec.digest(value.toByteArray(Charsets.UTF_8))
    private fun boundary(cutoff: Long, token: String?) = hash("$cutoff\n${token.orEmpty()}")
    private fun authDigest(auth: JournalAuthorization) = hash(listOf(auth.localDeviceId, auth.peerDeviceId, auth.groupId,
        auth.epoch, auth.freshStart.toString(), auth.consentOperationId, auth.operationDigest).joinToString("\n"))
    private fun validId(value: String) = value.matches(Regex("[A-Za-z0-9:_-]{1,128}"))
    private fun validDigest(value: String) = value.matches(Regex("[a-f0-9]{64}"))
    private fun validateAuth(auth: JournalAuthorization) {
        require(listOf(auth.localDeviceId, auth.peerDeviceId, auth.groupId, auth.epoch, auth.consentOperationId).all(::validId) &&
            auth.localDeviceId != auth.peerDeviceId && validDigest(auth.operationDigest)) { "Invalid journal authorization" }
    }
    private fun validateBinding(binding: JournalStreamBinding) {
        require(binding.protocol == 3 && listOf(binding.sourceDevice, binding.targetDevice, binding.streamId,
            binding.groupId, binding.epoch, binding.consentOperationId).all(::validId) &&
            binding.sourceDevice != binding.targetDevice && validDigest(binding.contentIdentity) && validDigest(binding.sourceBoundaryIdentity)) {
            "Invalid journal binding"
        }
    }
    private fun validateCursor(cursor: JournalCursor) {
        require(cursor.sequence in 0..1_000_000 && validDigest(cursor.prefix)) { "Invalid journal cursor" }
    }
    private fun requireDataSize(value: JournalData) {
        require(json.encodeToString(value).toByteArray(Charsets.UTF_8).size <= IncrementalJournalLimits.MAX_DATA_BYTES) { "Journal page exceeds byte limit" }
    }
}
