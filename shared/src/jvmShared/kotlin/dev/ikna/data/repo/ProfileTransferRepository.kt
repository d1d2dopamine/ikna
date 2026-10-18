package dev.ikna.data.repo

import dev.ikna.data.db.*
import dev.ikna.data.export.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.CoroutineContext

/** Additive content+journal merge. Controller owns authenticated lineage/pairing policy.
 * Never adopts another boundary, copies settings or deletes local/archive content. */
class ProfileTransferRepository internal constructor(
    private val db: IknaDatabase,
    private val restore: RestoreRepository
) {
    suspend fun snapshot(afterLocalId: Long = 0, boundaryToken: String? = null): ProfileTransferSnapshot {
        val caller = currentCoroutineContext()
        caller.ensureActive()
        return db.inTransaction { snapshotInTransaction(afterLocalId, boundaryToken, caller) }
    }

    private suspend fun snapshotInTransaction(afterLocalId: Long, boundaryToken: String?,
                                              caller: CoroutineContext): ProfileTransferSnapshot {
        suspend fun <T> read(block: suspend () -> T): T {
            caller.ensureActive()
            return block().also { caller.ensureActive() }
        }
        // Validate the exact reset marker before inspecting either active rows or sizes.
        read { restore.validateSyncBoundary(afterLocalId, boundaryToken) }
        db.preflightProfileTransfer(afterLocalId, caller)
        val dao = db.chunkDao()
        val records = read { restore.snapshotJournal(afterLocalId, boundaryToken) }
        val value = ProfileTransferSnapshot(
            boundaryPresent = read { db.historyBoundaryDao().current() != null },
            packs = read { dao.packs() }, chunks = read { dao.allChunks() }, tokens = read { dao.allTokens() },
            memberships = read { dao.allMemberships() }, contexts = read { dao.allContexts() }, reviews = records
        )
        caller.ensureActive()
        return ProfileTransferCodec.canonical(value).also {
            caller.ensureActive()
            ProfileTransferCodec.encode(it)
            caller.ensureActive()
        }
    }

    /** Bounded content-only identity. Does not query, count or normalize reviews. */
    suspend fun contentIdentity(afterLocalId: Long = 0, boundaryToken: String? = null): String {
        val caller = currentCoroutineContext()
        return db.inTransaction { contentIdentityInTransaction(afterLocalId, boundaryToken, caller) }
    }

    internal suspend fun contentIdentityInTransaction(afterLocalId: Long, boundaryToken: String?,
                                                     caller: CoroutineContext): String {
        caller.ensureActive()
        restore.validateSyncBoundary(afterLocalId, boundaryToken)
        db.preflightProfileTransfer(afterLocalId, caller, includeReviews = false)
        val dao = db.chunkDao()
        suspend fun <T> read(block: suspend () -> T): T {
            caller.ensureActive()
            return block().also { caller.ensureActive() }
        }
        val content = ProfileTransferSnapshot(boundaryPresent = false,
            packs = read { dao.packs() }, chunks = read { dao.allChunks() },
            tokens = read { dao.allTokens() }, memberships = read { dao.allMemberships() },
            contexts = read { dao.allContexts() }, reviews = emptyList())
        ProfileTransferCodec.encode(content)
        return ProfileTransferCodec.contentIdentity(content).also { caller.ensureActive() }
    }

    suspend fun snapshotWithRevision(afterLocalId: Long = 0, boundaryToken: String? = null): Pair<ProfileTransferSnapshot, String> {
        val caller = currentCoroutineContext()
        caller.ensureActive()
        return db.inTransaction {
            val value = snapshotInTransaction(afterLocalId, boundaryToken, caller)
            (value to compare(value, value, afterLocalId, boundaryToken).localRevision)
                .also { caller.ensureActive() }
        }
    }

    suspend fun preview(incoming: ProfileTransferSnapshot, afterLocalId: Long = 0,
                        boundaryToken: String? = null): ProfileMergePreview {
        val caller = currentCoroutineContext()
        caller.ensureActive()
        return db.inTransaction {
            compare(snapshotInTransaction(afterLocalId, boundaryToken, caller), incoming, afterLocalId, boundaryToken)
                .also { caller.ensureActive() }
        }
    }

    /** Adoption donor keeps its own journal; approval still binds the entire peer payload. */
    suspend fun previewAdoption(incoming: ProfileTransferSnapshot, afterLocalId: Long = 0,
                                boundaryToken: String? = null): ProfileMergePreview {
        val caller = currentCoroutineContext()
        caller.ensureActive()
        return db.inTransaction {
            compare(snapshotInTransaction(afterLocalId, boundaryToken, caller), incoming.copy(reviews = emptyList()), afterLocalId, boundaryToken)
                .copy(incomingRevision = ProfileTransferCodec.revision(incoming))
                .also { caller.ensureActive() }
        }
    }

    /** Subsequent automatic journal transfer cannot cross an unreviewed content edit. */
    suspend fun mergeJournalForProfile(records: List<ReviewRecord>, expectedContentIdentity: String,
                                      afterLocalId: Long = 0, boundaryToken: String? = null): RestoreResult =
        restore.journalMergeGuard {
            // Room changes coroutine context; retain the originating cancellation gate.
            val caller = currentCoroutineContext()
            db.inTransaction {
                caller.ensureActive()
                require(ProfileTransferCodec.contentIdentity(snapshotInTransaction(afterLocalId, boundaryToken, caller)) == expectedContentIdentity) {
                    "Profile content changed; review again"
                }
                val result = restore.mergeJournalLocked(records, afterLocalId, boundaryToken)
                caller.ensureActive()
                result
            }
        }

    suspend fun appliedOperation(operationId: String, afterLocalId: Long = 0,
                                 boundaryToken: String? = null): ProfileSyncOperationEntity? = db.inTransaction {
        restore.validateSyncBoundary(afterLocalId, boundaryToken)
        db.profileSyncOperationDao().find(operationId)?.takeIf {
            it.boundaryIdentity == boundaryIdentity(afterLocalId, boundaryToken)
        }
    }

    /** Caller must present the same payload and revision pair it explicitly reviewed.
     * Lock order matches answer/Undo/plan: learning mutex, then Room. No nested mutex. */
    suspend fun commit(incoming: ProfileTransferSnapshot, preview: ProfileMergePreview,
                       afterLocalId: Long = 0, boundaryToken: String? = null, operationId: String? = null,
                       operationDigest: String? = null, journalAuthorization: JournalAuthorization? = null): RestoreResult =
        commitProfile(incoming, preview, afterLocalId, boundaryToken, operationId, operationDigest, "merge", journalAuthorization)

    suspend fun commitAdoptionDonor(incoming: ProfileTransferSnapshot, preview: ProfileMergePreview,
                                   afterLocalId: Long, boundaryToken: String?, operationId: String,
                                   operationDigest: String, journalAuthorization: JournalAuthorization? = null): RestoreResult =
        commitProfile(incoming, preview, afterLocalId, boundaryToken, operationId, operationDigest, "adopt-donor", journalAuthorization)

    private suspend fun commitProfile(source: ProfileTransferSnapshot, preview: ProfileMergePreview,
                                      afterLocalId: Long, boundaryToken: String?, operationId: String?,
                                      operationDigest: String?, mode: String, journalAuthorization: JournalAuthorization?): RestoreResult =
        restore.journalMergeGuard {
            val incoming = if (mode == "adopt-donor") source.copy(reviews = emptyList()) else source
            val caller = currentCoroutineContext()
            db.inTransaction {
                caller.ensureActive()
                require(journalAuthorization == null ||
                    (journalAuthorization.consentOperationId == operationId && journalAuthorization.operationDigest == operationDigest)) {
                    "Journal authorization differs from approved operation"
                }
                require((operationId == null) == (operationDigest == null)) { "Incomplete profile operation identity" }
                require(operationId == null || operationId.matches(Regex("[A-Za-z0-9:_-]{1,128}"))) { "Invalid profile operation identity" }
                require(operationDigest == null || operationDigest.matches(Regex("[a-f0-9]{64}"))) { "Invalid profile operation digest" }
                require(ProfileTransferCodec.revision(source) == preview.incomingRevision) { "Incoming profile changed after preview" }
                if (operationId != null) {
                    db.profileSyncOperationDao().find(operationId)?.let { applied ->
                        require(applied.operationDigest == operationDigest && applied.mode == mode &&
                            applied.localRevision == preview.localRevision && applied.incomingRevision == preview.incomingRevision &&
                            applied.boundaryIdentity == boundaryIdentity(afterLocalId, boundaryToken)) { "Conflicting profile operation identity" }
                        // Validate the local reset boundary even for an acknowledged retry.
                        restore.validateSyncBoundary(afterLocalId, boundaryToken)
                        if (journalAuthorization != null) restore.incrementalJournal.requireAuthorizationInTransaction(
                            journalAuthorization, afterLocalId, boundaryToken, caller)
                        caller.ensureActive()
                        return@inTransaction RestoreResult(0, incoming.reviews.size, 0)
                    }
                }
                val local = snapshotInTransaction(afterLocalId, boundaryToken, caller)
                val current = compare(local, incoming, afterLocalId, boundaryToken)
                    .copy(incomingRevision = ProfileTransferCodec.revision(source))
                require(current == preview) { "Profile changed after preview; review again" }
                require(current.canMerge) { "Profile has identity conflicts" }
                val dao = db.chunkDao()
                val packIds = local.packs.map { it.id }.toSet()
                val chunkIds = local.chunks.map { it.id }.toSet()
                // New decks are installed locally, inactive until the learner enables them.
                incoming.packs.filter { it.id !in packIds }.forEach {
                    dao.upsertPack(it.copy(installedAt = System.currentTimeMillis(), isActive = false))
                }
                dao.insertChunksIgnore(incoming.chunks.filter { it.id !in chunkIds })
                dao.insertTokensIgnore(incoming.tokens.filter { it.chunkId !in chunkIds })
                val membershipIds = local.memberships.map { listOf(it.packId, it.chunkId) }.toSet()
                dao.upsertPackChunks(incoming.memberships.filter { listOf(it.packId, it.chunkId) !in membershipIds })
                val contextIds = local.contexts.map(::contextKey).toSet()
                dao.upsertChunkContexts(incoming.contexts.filter { contextKey(it) !in contextIds })
                val result = restore.mergeJournalLocked(incoming.reviews, afterLocalId, boundaryToken)
                if (current.addedPacks > 0 || current.addedTargets > 0 ||
                    incoming.memberships.any { listOf(it.packId, it.chunkId) !in membershipIds } ||
                    incoming.contexts.any { contextKey(it) !in contextIds }) db.planDao().clear()
                // Bounds apply to the resulting profile too, even without a protocol receipt.
                val resultingSnapshot = snapshotInTransaction(afterLocalId, boundaryToken, caller)
                if (operationId != null) {
                    val resulting = compare(resultingSnapshot, incoming, afterLocalId, boundaryToken).localRevision
                    db.profileSyncOperationDao().insert(ProfileSyncOperationEntity(operationId, operationDigest!!, preview.localRevision,
                        preview.incomingRevision, boundaryIdentity(afterLocalId, boundaryToken), resulting, ProfileTransferCodec.profileIdentity(resultingSnapshot),
                        ProfileTransferCodec.contentIdentity(resultingSnapshot), mode))
                }
                if (journalAuthorization != null) restore.incrementalJournal.bootstrapInTransaction(
                    journalAuthorization, afterLocalId, boundaryToken, caller)
                caller.ensureActive()
                result
            }
        }

    private fun compare(local: ProfileTransferSnapshot, remote: ProfileTransferSnapshot,
                        cutoff: Long, token: String?): ProfileMergePreview {
        val localDigest = ProfileTransferCodec.revision(local)
        val localRevision = ProfileTransferCodec.digest("$localDigest\n$cutoff\n${token.orEmpty()}".toByteArray(Charsets.UTF_8))
        val remoteRevision = ProfileTransferCodec.revision(remote)
        val errors = mutableListOf<String>()
        validate(local, "local", errors)
        validate(remote, "incoming", errors)
        fun <T, K> conflicts(a: List<T>, b: List<T>, key: (T) -> K, canonical: (T) -> Any, label: String) {
            val known = a.associateBy(key)
            b.forEach { row -> known[key(row)]?.let { old ->
                if (canonical(old) != canonical(row)) errors += "$label identity conflict: ${key(row)}"
            } }
        }
        conflicts(local.packs, remote.packs, { it.id }, { it.copy(installedAt = 0, isActive = true) }, "deck")
        // packId is a local home pointer; actual membership identities are compared separately.
        conflicts(local.chunks, remote.chunks, { it.id }, { it.copy(packId = "") }, "target")
        conflicts(local.memberships, remote.memberships, { listOf(it.packId, it.chunkId) }, { it }, "membership")
        conflicts(local.contexts, remote.contexts, ::contextKey, { it }, "context")
        // A reused deck identity is a complete manifest, not a bag into which independent
        // edits can silently add memberships while leaving the declared count unchanged.
        val sharedPacks = local.packs.map { it.id }.toSet().intersect(remote.packs.map { it.id }.toSet())
        val localMemberships = local.memberships.groupBy { it.packId }
        val remoteMemberships = remote.memberships.groupBy { it.packId }
        val localContexts = local.contexts.groupBy { it.packId }
        val remoteContexts = remote.contexts.groupBy { it.packId }
        sharedPacks.forEach { id ->
            if (localMemberships[id].orEmpty().toSet() != remoteMemberships[id].orEmpty().toSet() ||
                localContexts[id].orEmpty().toSet() != remoteContexts[id].orEmpty().toSet()) {
                errors += "deck manifest conflict: $id"
            }
        }
        val localTargets = local.chunks.map { it.id }.toSet()
        val localTokens = local.tokens.groupBy { it.chunkId }
        val remoteTokens = remote.tokens.groupBy { it.chunkId }
        remote.chunks.filter { it.id in localTargets }.forEach {
            if (localTokens[it.id].orEmpty().sortedBy { row -> row.position } !=
                remoteTokens[it.id].orEmpty().sortedBy { row -> row.position }) errors += "target tokens conflict: ${it.id}"
        }
        val localRecords = JournalIdentity.normalize(local.reviews)
        val remoteRecords = JournalIdentity.normalize(remote.reviews)
        conflicts(localRecords, remoteRecords, { it.eventId }, { it }, "event")
        val union = (localRecords + remoteRecords).associateBy { it.eventId }
        val targets = localTargets + remote.chunks.map { it.id }
        union.values.forEach { row ->
            if (row.chunkId !in targets) errors += "Missing journal target: ${row.chunkId}"
            row.undoEventId?.let { undo ->
                val original = union[undo]
                if (original == null || original.undoEventId != null || original.chunkId != row.chunkId ||
                    original.level != row.level || original.purpose != row.purpose) errors += "Invalid undo target: $undo"
            }
        }
        try {
            ProfileTransferCodec.encode(local.copy(
                packs = (local.packs + remote.packs).distinctBy { it.id },
                chunks = (local.chunks + remote.chunks).distinctBy { it.id },
                tokens = (local.tokens + remote.tokens).distinctBy { listOf(it.chunkId, it.position.toString()) },
                memberships = (local.memberships + remote.memberships).distinctBy { listOf(it.packId, it.chunkId) },
                contexts = (local.contexts + remote.contexts).distinctBy(::contextKey),
                reviews = union.values.toList()
            ))
        } catch (_: IllegalArgumentException) { errors += "Combined profile exceeds transfer limits" }
        val localPackIds = local.packs.map { it.id }.toSet()
        return ProfileMergePreview(localRevision, remoteRevision,
            remote.packs.count { it.id !in localPackIds },
            remote.chunks.count { it.id !in localTargets },
            remoteRecords.map { it.eventId }.toSet().minus(localRecords.map { it.eventId }.toSet()).size,
            errors.distinct().sorted())
    }

    private fun boundaryIdentity(cutoff: Long, token: String?): String =
        ProfileTransferCodec.digest("$cutoff\n${token.orEmpty()}".toByteArray(Charsets.UTF_8))

    private fun contextKey(row: ChunkContextEntity) = listOf(row.packId, row.chunkId, row.contextId, row.meaningId)

    private fun validate(snapshot: ProfileTransferSnapshot, origin: String, errors: MutableList<String>) {
        fun <T, K> unique(rows: List<T>, key: (T) -> K, label: String) {
            if (rows.map(key).distinct().size != rows.size) errors += "$origin duplicate $label identity"
        }
        unique(snapshot.packs, { it.id }, "deck")
        unique(snapshot.chunks, { it.id }, "target")
        unique(snapshot.tokens, { listOf(it.chunkId, it.position.toString()) }, "token")
        unique(snapshot.memberships, { listOf(it.packId, it.chunkId) }, "membership")
        unique(snapshot.contexts, ::contextKey, "context")
        unique(snapshot.reviews, { it.eventId }, "event")
        val packs = snapshot.packs.map { it.id }.toSet()
        val targets = snapshot.chunks.map { it.id }.toSet()
        if (snapshot.packs.any { it.id.isBlank() || it.lang.isBlank() || it.version < 0 || it.chunkCount < 0 } ||
            snapshot.chunks.any { it.id.isBlank() || it.packId !in packs || it.lang.isBlank() || it.targetStart < 0 ||
                it.targetEnd < it.targetStart || it.targetEnd > it.contextSentence.length } ||
            snapshot.tokens.any { it.chunkId !in targets || it.position < 0 || !it.weight.isFinite() || it.weight != dev.ikna.data.pack.PackLoader.weightFor(it.isTarget, it.isContent) } ||
            snapshot.memberships.any { it.packId !in packs || it.chunkId !in targets } ||
            snapshot.contexts.any { it.packId !in packs || it.chunkId !in targets || it.contextId.isBlank() ||
                it.targetStart < 0 || it.targetEnd < it.targetStart || it.targetEnd > it.contextSentence.length }) {
            errors += "$origin invalid content references or values"
        }
        val memberships = snapshot.memberships.map { listOf(it.packId, it.chunkId) }.toSet()
        if (snapshot.chunks.any { listOf(it.packId, it.id) !in memberships }) {
            errors += "$origin target has no home deck membership; content reconciliation required"
        }
        val contextKeys = snapshot.contexts.map(::contextKey).toSet()
        if (snapshot.memberships.any { row ->
            (row.primaryContextId == null) != (row.primaryMeaningId == null) ||
                (row.primaryContextId != null && listOf(row.packId, row.chunkId,
                    row.primaryContextId, row.primaryMeaningId) !in contextKeys)
        }) errors += "$origin unresolved primary context"
        snapshot.contexts.forEach { row ->
            try {
                val tokens = ProfileTransferCodec.json.decodeFromString<List<dev.ikna.data.pack.PackToken>>(row.tokensJson)
                require(tokens.size <= ProfileTransferCodec.MAX_ROWS)
            } catch (_: IllegalArgumentException) { errors += "$origin invalid context tokens" }
        }
        try { restore.validateJournalRecords(snapshot.reviews) }
        catch (_: IllegalArgumentException) { errors += "$origin invalid journal observations" }
        // A transfer snapshot is portable, never a row-id-based restore file.
        if (snapshot.reviews.any { it.eventId == null || it.id != 0L || it.undoOf != null || it.synthetic }) {
            errors += "$origin non-portable journal"
        }
    }
}
