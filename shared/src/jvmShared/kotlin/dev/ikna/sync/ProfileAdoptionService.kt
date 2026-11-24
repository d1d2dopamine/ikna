package dev.ikna.sync

import dev.ikna.data.db.inTransaction
import dev.ikna.data.export.JournalAuthorization
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import dev.ikna.data.db.IknaDatabase
import dev.ikna.data.db.ProfileSyncOperationEntity
import dev.ikna.data.db.ProfileOriginEntity
import dev.ikna.data.db.recordProfileOrigin
import dev.ikna.data.export.ProfileTransferCodec
import dev.ikna.data.export.ProfileTransferSnapshot
import dev.ikna.data.repo.RestoreRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Reconstructs a NEW closed DB; the original DB and all its archives remain in place.
 * Settings, media directories and credentials are never copied. No journal rows are deleted.
 * The platform must install the startup selector before constructing any DAO. */
class ProfileAdoptionService(
    private val slots: ProfileSlotStore,
    private val liveRepository: RestoreRepository,
    private val openNewDatabase: (File) -> IknaDatabase,
    private val createRestoreRepository: (IknaDatabase) -> RestoreRepository,
    /** Hold the same exclusive guard as local answers, Undo, imports and content writes. */
    private val withLiveWriteGuard: suspend (suspend () -> Unit) -> Unit,
    /** Stops background jobs and transport, closes live DB, and gates ALL UI writes.
     * Must leave the process restart-required even if a later durability step throws. */
    private val quiesceAndCloseLive: suspend () -> Unit,
    /** Durably binds the negotiated operation to the candidate slot before pointer publication.
     * Must fail closed at startup if publication does not happen. No live DB access here. */
    private val prepareActivation: suspend (File, String, String) -> Unit,
    /** Durably revoke old pairing before choosing a recovery DB. No live DB access here. */
    private val prepareRecovery: suspend (String) -> Unit,
) {
    enum class State { IDLE, STAGING, RESTART_REQUIRED }
    @Volatile var state: State = State.IDLE; private set
    private val operation = Mutex()

    /** Only called after both devices approved the exact operation tuple. Same-ID content
     * conflicts fail closed: unique content is retained, never guessed or silently dropped. */
    suspend fun adopt(
        incoming: ProfileTransferSnapshot,
        expectedLocalRevision: String,
        operationId: String,
        tupleDigest: String,
        afterLocalId: Long = 0,
        boundaryToken: String? = null,
        journalAuthorization: JournalAuthorization? = null,
    ): String = operation.withLock {
        val caller = currentCoroutineContext()
        caller.ensureActive()
        check(state == State.IDLE) { "profile-restart-required" }
        require(tupleDigest.matches(Regex("[a-f0-9]{64}")) && expectedLocalRevision.matches(Regex("[a-f0-9]{64}")) &&
            operationId.matches(Regex("[A-Za-z0-9:_-]{1,128}"))) { "invalid-profile-operation" }
        state = State.STAGING
        try {
            val original = slots.selection().active
            val (local, localRevision) = liveRepository.profileTransfer.snapshotWithRevision(afterLocalId, boundaryToken)
            require(localRevision == expectedLocalRevision) {
                "local-profile-stale"
            }
            val candidate = slots.createCandidate()
            val compatibleLocal = dev.ikna.data.repo.ProfileTransferRepository.filterCompatibleExtraContent(
                incoming.copy(boundaryPresent = false),
                local.copy(boundaryPresent = false, reviews = emptyList())
            )
            var resultingIdentity: String
            val db = openNewDatabase(candidate.database)
            try {
                val target = createRestoreRepository(db).profileTransfer
                val chosen = incoming.copy(boundaryPresent = false)
                val chosenPreview = target.preview(chosen)
                require(chosenPreview.canMerge) { "invalid-source-profile" }
                target.commit(chosen, chosenPreview)
                // Retain all compatible unique local decks, while letting the chosen source win any deck conflicts.
                val contentPreview = target.preview(compatibleLocal)
                require(contentPreview.canMerge) { "profile-content-conflict" }
                target.commit(compatibleLocal, contentPreview)
                // Deck enablement and installation timestamps belong to this device. Existing
                // local decks keep them; newly received decks retain the importer's inactive default.
                val localPacksById = local.packs.associateBy { it.id }
                compatibleLocal.packs.forEach { pack -> localPacksById[pack.id]?.let { db.chunkDao().upsertPack(it) } }
                val verified = target.snapshot()
                resultingIdentity = ProfileTransferCodec.profileIdentity(verified)
                require(ProfileTransferCodec.canonical(verified).reviews == ProfileTransferCodec.canonical(chosen).reviews) {
                    "profile-journal-verification-failed"
                }
                require(target.preview(chosen).canMerge && target.preview(compatibleLocal).canMerge) { "profile-content-verification-failed" }
                db.inTransaction {
                caller.ensureActive()
                db.profileSyncOperationDao().insert(ProfileSyncOperationEntity(
                    opId = operationId, operationDigest = tupleDigest, localRevision = expectedLocalRevision,
                    incomingRevision = ProfileTransferCodec.revision(incoming),
                    resultingRevision = target.preview(verified).localRevision,
                    resultingProfileIdentity = ProfileTransferCodec.profileIdentity(verified),
                    resultingContentIdentity = ProfileTransferCodec.contentIdentity(verified),
                    boundaryIdentity = ProfileTransferCodec.digest("0\n".toByteArray(Charsets.UTF_8)), mode = "adopt"))
                db.recordProfileOrigin(operationId, tupleDigest)
                if (journalAuthorization != null) {
                    require(journalAuthorization.consentOperationId == operationId && journalAuthorization.operationDigest == tupleDigest) { "Journal authorization differs" }
                    createRestoreRepository(db).incrementalJournal.bootstrapInTransaction(journalAuthorization, 0, null, caller)
                }
                caller.ensureActive()
                }
            } finally { db.close() }
            // Validate the actual reopened SQLite database, including its checkpoint/WAL state.
            val reopened = openNewDatabase(candidate.database)
            try {
                val target = createRestoreRepository(reopened).profileTransfer
                val verified = target.snapshot()
                require(reopened.profileOriginDao().current() == ProfileOriginEntity(opId = operationId, operationDigest = tupleDigest)) { "profile-origin-verification-failed" }
                val receipt = checkNotNull(reopened.profileSyncOperationDao().find(operationId))
                require(receipt.operationDigest == tupleDigest && receipt.mode == "adopt" &&
                    receipt.resultingProfileIdentity == ProfileTransferCodec.profileIdentity(verified) &&
                    receipt.resultingContentIdentity == ProfileTransferCodec.contentIdentity(verified)) { "profile-receipt-verification-failed" }
                require(ProfileTransferCodec.canonical(verified).reviews == ProfileTransferCodec.canonical(incoming).reviews) {
                    "profile-journal-verification-failed"
                }
                require(target.preview(incoming.copy(boundaryPresent = false)).canMerge &&
                    target.preview(compatibleLocal).canMerge) {
                    "profile-content-verification-failed"
                }
            } finally { reopened.close() }
            slots.seal(candidate)
            withLiveWriteGuard {
                require(liveRepository.profileTransfer.preview(local, afterLocalId, boundaryToken).localRevision == expectedLocalRevision) {
                    "local-profile-stale"
                }
                withContext(NonCancellable) {
                    // This state is sticky: no failure or cancelled network coroutine may reopen writes.
                    state = State.RESTART_REQUIRED
                    quiesceAndCloseLive()
                    // A content/import writer may not share the learning guard. Once the original
                    // Room handle is CLOSED, reopen exclusively and verify its final committed state.
                    // Any race before close aborts publication, retaining the original startup slot.
                    val closedOriginal = openNewDatabase(original)
                    try {
                        val finalRepository = createRestoreRepository(closedOriginal).profileTransfer
                        require(finalRepository.preview(local, afterLocalId, boundaryToken).localRevision == expectedLocalRevision) {
                            "local-profile-stale"
                        }
                    } finally { closedOriginal.close() }
                    prepareActivation(candidate.database, operationId, tupleDigest)
                    slots.activate(candidate, original, operationId)
                }
            }
            resultingIdentity
        } finally {
            if (state == State.STAGING) state = State.IDLE
        }
    }

    /** User-selected recovery never restores old sync credentials or permits an old generation.
     * The dedicated platform callback revokes pairing durably before selection changes. */
    suspend fun recover(operationId: String) = operation.withLock {
        check(state == State.IDLE) { "profile-restart-required" }
        val current = slots.selection()
        val recovery = checkNotNull(current.recovery) { "no-recovery-profile" }
        slots.verifyRecovery(recovery)
        val recoveryDatabase = openNewDatabase(recovery)
        val caller = currentCoroutineContext()
        try { recoveryDatabase.inTransaction {
            caller.ensureActive()
            recoveryDatabase.incrementalJournalDao().invalidate()
            recoveryDatabase.chunkDao().packs()
            caller.ensureActive()
        } } finally { recoveryDatabase.close() }
        withLiveWriteGuard {
            withContext(NonCancellable) {
                state = State.RESTART_REQUIRED
                quiesceAndCloseLive()
                prepareRecovery(operationId)
                slots.recover(current.active, operationId)
            }
        }
    }
}
