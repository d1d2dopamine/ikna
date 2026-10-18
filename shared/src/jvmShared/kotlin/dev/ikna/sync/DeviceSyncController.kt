package dev.ikna.sync

import dev.ikna.data.export.ReviewRecord
import dev.ikna.data.export.ProfileTransferCodec
import dev.ikna.data.export.ProfileTransferSnapshot
import dev.ikna.data.export.ProfileMergePreview
import dev.ikna.data.export.JournalAuthorization
import dev.ikna.data.export.JournalHead
import dev.ikna.data.export.JournalCursor
import dev.ikna.data.export.JournalInventory
import dev.ikna.data.export.JournalRequest
import dev.ikna.data.export.JournalData
import dev.ikna.data.repo.RestoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** Foreground-only opt-in owner of a paired journal. Never included in backup/export. */
class DeviceSyncController(
    private val directory: File,
    private val repository: RestoreRepository,
    private val onMerged: suspend () -> Unit,
    private val available: Boolean = true,
    private val intervalMillis: Long = 15_000,
    private val currentDatabaseSlot: String = "legacy",
    private val canRecoverProfile: Boolean = false,
    private val currentSlotOperationId: String? = null,
    private val currentSlotOperationDigest: String? = null,
) {
    @Serializable private data class Configuration(
        val version: Int = 1,
        val binding: HistoryBinding = HistoryBinding(UUID.randomUUID().toString(), UUID.randomUUID().toString()),
        val enabled: Boolean = false,
        val hasEnabled: Boolean = false,
        val blocked: Boolean = false,
        val boundaryReady: Boolean = false,
        val localHistoryReady: Boolean = false,
        val mutationGeneration: Long = 0,
        val boundaryToken: String? = null,
        val afterLocalId: Long = 0,
        val databaseSlot: String = "legacy",
        val pendingAdoptionSlot: String? = null,
        val pendingRecoverySlot: String? = null,
        val pendingRecoveryOperationId: String? = null,
        val pendingAdoption: ProfileConsentProtocol.Proposal? = null,
    )
    data class AdoptionRequest(val incoming: ProfileTransferSnapshot, val preview: ProfileMergePreview,
        val proposal: ProfileConsentProtocol.Proposal, val afterLocalId: Long, val boundaryToken: String?,
        val journalAuthorization: JournalAuthorization)
    var adoptionPreflight: (suspend () -> Unit)? = null
    var recoveryHandler: (suspend () -> Unit)? = null
    var isRestartRequired: () -> Boolean = { false }
    var adoptionHandler: (suspend (AdoptionRequest) -> Unit)? = null
    @Volatile private var adoptionInProgress = false
    @Serializable private data class Journal(val version: Int = 1, val records: List<ReviewRecord>)
    data class State(
        val enabled: Boolean = false, val running: Boolean = false, val peers: Int = 0,
        val addresses: List<String> = emptyList(), val invitation: String? = null,
        val lastSuccessAtMillis: Long? = null, val error: String? = null,
        val blocked: Boolean = false, val boundaryReady: Boolean = false,
        val devices: List<PairedLanTransport.PeerStatus> = emptyList(),
        val profileConsent: ProfileConsentProtocol.State = ProfileConsentProtocol.State(),
        val recoveryAvailable: Boolean = false,
    ) {
        override fun toString(): String = "State(enabled=$enabled, running=$running, peers=$peers, blocked=$blocked, error=$error, invitation=<redacted>)"
    }
    private val json = Json { ignoreUnknownKeys = false; isLenient = false; encodeDefaults = true }
    private val operation = Mutex()
    private val file = File(directory, "configuration.json")
    private val invalidationFile = File(directory, "profile-invalidation")
    private val completionFile = File(directory, "profile-completion.json")
    @Volatile private var protocol: ProfileConsentProtocol? = null
    @Volatile private var configuration = Configuration()
    @Volatile private var loaded = false
    @Volatile private var foreground = false
    @Volatile private var foregroundRequested = false
    private val lifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var transport: PairedLanTransport? = null
    @Volatile private var permitted = false
    private val journalErrors = JournalErrorLatch()
    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable.asStateFlow()

    @Synchronized private fun load() {
        if (loaded) return
        check(available) { "unavailable" }
        LanSyncSecurity.privateDirectory(directory)
        configuration = if (file.exists()) {
            require(file.length() <= 16_384) { "invalid-state" }
            json.decodeFromString<Configuration>(LanSyncSecurity.readPrivate(file, 16_384)).also {
                require(it.version == 1 && it.afterLocalId >= 0) { "invalid-state" }
            }
        } else Configuration(databaseSlot = currentDatabaseSlot).also { save(it) }
        if (configuration.databaseSlot != currentDatabaseSlot && configuration.pendingAdoptionSlot != currentDatabaseSlot && configuration.pendingRecoverySlot != currentDatabaseSlot) {
            save(configuration.copy(enabled = false, blocked = true, boundaryReady = false, localHistoryReady = false))
        }
        loaded = true
        publish()
    }
    @Synchronized private fun save(value: Configuration) {
        LanSyncSecurity.privateDirectory(directory)
        LanSyncSecurity.writePrivate(file, json.encodeToString(value))
        configuration = value
    }
    private fun publish(error: String? = null) {
        val status = transport?.status
        val transportError = status?.lastError?.takeIf { status.running }
        val consentState = protocol?.state ?: ProfileConsentProtocol.State()
        val errorScope = if (configuration.enabled && !configuration.blocked && status?.running == true &&
            status.peers.size == 1 && consentState.complete && consentState.operationId != null)
            JournalErrorLatch.Scope(configuration.binding, status.peers.single().id, consentState.operationId)
        else null
        val retainedError = journalErrors.update(errorScope, consentState.journalCaughtUp, error ?: transportError)
        mutable.update { it.copy(enabled = configuration.enabled,
            running = status?.running == true, peers = status?.peerCount ?: 0,
            devices = status?.peers ?: emptyList(),
            addresses = if (foreground) PairedLanTransport.localAddresses() else emptyList(),
            lastSuccessAtMillis = if (protocol?.state?.complete != true) null
                else if (protocol?.state?.journalCaughtUp == true && status != null) status.lastSuccessAtMillis
                else it.lastSuccessAtMillis,
            profileConsent = protocol?.state ?: ProfileConsentProtocol.State(),
            recoveryAvailable = canRecoverProfile,
            error = error ?: retainedError ?: transportError, blocked = configuration.blocked,
            boundaryReady = configuration.boundaryReady) }
    }
    private fun instance(): PairedLanTransport = transport ?: PairedLanTransport(
        File(directory, "transport"), configuration.binding,
        object : PairedLanTransport.PayloadCallbacks {
            override suspend fun snapshot(): ByteArray = error("authenticated-peer-required")
            override suspend fun receive(payload: ByteArray) { error("authenticated-peer-required") }
            override suspend fun snapshotFor(peerId: String): ByteArray {
                check(permitted) { "paused" }
                check(instance().status.peerCount == 1) { "single-peer-required" }
                return consent().snapshotFor(peerId).also { publish() }
            }
            override suspend fun receiveFrom(peerId: String, payload: ByteArray) {
                check(permitted) { "paused" }
                check(instance().status.peerCount == 1) { "single-peer-required" }
                consent().receiveFrom(peerId, payload)
                publish()
            }
        }, onStatus = { publish() }, intervalMillis = intervalMillis
    ).also { transport = it }

    private fun journalAuthorization(operationId: String, digest: String, peerId: String = instance().status.peers.single().id): JournalAuthorization {
        val binding = configuration.binding
        return JournalAuthorization(instance().deviceId, peerId, binding.groupId, binding.epoch,
            binding.freshStart, operationId, digest)
    }

    @Synchronized private fun consent(): ProfileConsentProtocol = protocol ?: ProfileConsentProtocol(
        instance().deviceId, configuration.binding,
        object : ProfileConsentProtocol.Store {
            override suspend fun snapshot() = repository.profileTransfer.snapshot(configuration.afterLocalId, configuration.boundaryToken)
            override suspend fun snapshotWithRevision() = repository.profileTransfer.snapshotWithRevision(configuration.afterLocalId, configuration.boundaryToken)
            override suspend fun committedContentIdentity(operationId: String) =
                checkNotNull(repository.profileTransfer.appliedOperation(operationId, configuration.afterLocalId, configuration.boundaryToken)).resultingContentIdentity
            override suspend fun preview(incoming: ProfileTransferSnapshot) =
                repository.profileTransfer.preview(incoming, configuration.afterLocalId, configuration.boundaryToken)
            override suspend fun commit(incoming: ProfileTransferSnapshot, preview: ProfileMergePreview,
                                        operationId: String, tupleDigest: String): String {
                check(permitted && !configuration.blocked) { "paused" }
                val alreadyApplied = repository.profileTransfer.appliedOperation(operationId,
                    configuration.afterLocalId, configuration.boundaryToken) != null
                repository.profileTransfer.commit(incoming, preview, configuration.afterLocalId,
                    configuration.boundaryToken, operationId, tupleDigest, journalAuthorization(operationId, tupleDigest))
                val receipt = checkNotNull(repository.profileTransfer.appliedOperation(operationId, configuration.afterLocalId, configuration.boundaryToken))
                if (!alreadyApplied && receipt.localRevision != receipt.resultingRevision) onMerged()
                return receipt.resultingProfileIdentity
            }
            override suspend fun previewAdoption(incoming: ProfileTransferSnapshot) =
                repository.profileTransfer.previewAdoption(incoming, configuration.afterLocalId, configuration.boundaryToken)
            override suspend fun adopt(incoming: ProfileTransferSnapshot, preview: ProfileMergePreview,
                                       proposal: ProfileConsentProtocol.Proposal, localIsSource: Boolean): String? {
                check(permitted && !configuration.blocked) { "paused" }
                val digest = ProfileConsentProtocol.tupleDigest(proposal)
                if (localIsSource) {
                    val alreadyApplied = repository.profileTransfer.appliedOperation(proposal.operationId,
                        configuration.afterLocalId, configuration.boundaryToken) != null
                    repository.profileTransfer.commitAdoptionDonor(incoming, preview, configuration.afterLocalId,
                        configuration.boundaryToken, proposal.operationId, digest, journalAuthorization(proposal.operationId, digest))
                    val receipt = checkNotNull(repository.profileTransfer.appliedOperation(proposal.operationId,
                        configuration.afterLocalId, configuration.boundaryToken))
                    // Content-only donor changes also invalidate presentations, after storage guards return.
                    if (!alreadyApplied && receipt.localRevision != receipt.resultingRevision) onMerged()
                    return receipt.resultingProfileIdentity
                }
                val handler = checkNotNull(adoptionHandler) { "adoption-unavailable" }
                check(!adoptionInProgress) { "adoption-in-progress" }
                adoptionInProgress = true
                val request = AdoptionRequest(incoming, preview, proposal, configuration.afterLocalId, configuration.boundaryToken,
                    journalAuthorization(proposal.operationId, digest))
                // Never stop a transport from inside its own callback barrier.
                lifecycleScope.launch {
                    operation.withLock {
                        try {
                            permitted = false
                            transport?.stop()
                            save(configuration.copy(pendingAdoption = proposal))
                            handler(request)
                        } catch (failure: Exception) {
                            if (!isRestartRequired()) {
                                adoptionInProgress = false
                                save(configuration.copy(enabled = false, pendingAdoption = null, pendingAdoptionSlot = null))
                            }
                            publish("adoption-recovery-required")
                        }
                    }
                }
                return null
            }
            override fun incremental(completion: ProfileConsentProtocol.Completion, peerId: String): IncrementalJournalProtocol {
                val boundary = configuration
                check(completion.proposal.binding == boundary.binding) { "history-mismatch" }
                val authorization = journalAuthorization(completion.proposal.operationId,
                    ProfileConsentProtocol.tupleDigest(completion.proposal), peerId)
                fun ensureAllowed() {
                    val current = configuration
                    check(permitted && !current.blocked && current.binding == boundary.binding &&
                        current.afterLocalId == boundary.afterLocalId && current.boundaryToken == boundary.boundaryToken) { "paused" }
                }
                val journal = repository.incrementalJournal
                return IncrementalJournalProtocol(object : IncrementalJournalProtocol.Store {
                    override suspend fun localHead(): JournalHead {
                        ensureAllowed()
                        return journal.localHead(authorization, boundary.afterLocalId, boundary.boundaryToken)
                    }
                    override suspend fun checkpoint(remoteHead: JournalHead) = run {
                        ensureAllowed()
                        journal.checkpoint(authorization, remoteHead, boundary.afterLocalId, boundary.boundaryToken)
                    }
                    override suspend fun inventory(after: JournalCursor, highWater: JournalCursor) = run {
                        ensureAllowed()
                        journal.inventory(authorization, after, highWater, boundary.afterLocalId, boundary.boundaryToken)
                    }
                    override suspend fun missing(inventory: JournalInventory) = run {
                        ensureAllowed()
                        journal.missing(authorization, inventory, boundary.afterLocalId, boundary.boundaryToken)
                    }
                    override suspend fun payload(request: JournalRequest) = run {
                        ensureAllowed()
                        journal.payload(authorization, request, boundary.afterLocalId, boundary.boundaryToken)
                    }
                    override suspend fun apply(data: JournalData) = run {
                        ensureAllowed()
                        val result = journal.apply(authorization, data, boundary.afterLocalId, boundary.boundaryToken)
                        if (result.imported > 0) onMerged()
                        result.ack
                    }
                })
            }
            override suspend fun validCompletion(completion: ProfileConsentProtocol.Completion): Boolean {
                val p = completion.proposal
                val marker = repository.profileTransfer.appliedOperation(p.operationId, configuration.afterLocalId, configuration.boundaryToken) ?: return false
                return p.binding == configuration.binding && marker.operationDigest == ProfileConsentProtocol.tupleDigest(p) &&
                    marker.resultingProfileIdentity == completion.receipt &&
                    repository.profileTransfer.contentIdentity(configuration.afterLocalId, configuration.boundaryToken) == completion.contentIdentity
            }
            override suspend fun saveInvalidatedOperationId(operationId: String?) {
                if (operationId == null) { if (invalidationFile.exists()) check(invalidationFile.delete()) }
                else {
                    require(operationId.matches(Regex("[A-Za-z0-9:_-]{1,128}")))
                    LanSyncSecurity.writePrivate(invalidationFile, operationId)
                }
            }
            override suspend fun saveCompletion(completion: ProfileConsentProtocol.Completion?) {
                if (completion == null) { if (completionFile.exists()) check(completionFile.delete()) }
                else LanSyncSecurity.writePrivate(completionFile, json.encodeToString(completion))
            }
        }, savedCompletion = if (completionFile.exists()) {
            require(completionFile.length() <= 16_384) { "invalid-profile-state" }
            json.decodeFromString<ProfileConsentProtocol.Completion>(LanSyncSecurity.readPrivate(completionFile, 16_384))
        } else null,
        savedInvalidation = if (invalidationFile.exists()) {
            require(invalidationFile.length() <= 128) { "invalid-profile-state" }
            LanSyncSecurity.readPrivate(invalidationFile, 128).also { require(it.matches(Regex("[A-Za-z0-9:_-]{1,128}"))) }
        } else null,
    ).also { protocol = it }

    suspend fun proposeMerge(peerId: String) = action {
        check(permitted && instance().status.peerCount == 1) { "paused" }
        consent().propose(peerId)
    }
    suspend fun proposeAdoption(peerId: String, useThisDevice: Boolean) = action {
        check(permitted && instance().status.peerCount == 1 && adoptionHandler != null) { "adoption-unavailable" }
        if (!useThisDevice) adoptionPreflight?.invoke()
        consent().propose(peerId, if (useThisDevice) ProfileConsentProtocol.Mode.ADOPT_THIS else ProfileConsentProtocol.Mode.ADOPT_OTHER)
    }
    /** Called after closed candidate verification, before the startup pointer is published. */
    fun prepareProfileActivation(slot: String, operationId: String, digest: String) {
        val proposal = checkNotNull(configuration.pendingAdoption) { "missing-adoption-consent" }
        require(proposal.operationId == operationId && ProfileConsentProtocol.tupleDigest(proposal) == digest)
        require(slot.matches(Regex("[a-f0-9-]{36}")))
        save(configuration.copy(pendingAdoptionSlot = slot))
    }
    suspend fun prepareProfileRecovery(slot: String, operationId: String) {
        permitted = false
        transport?.stop()
        instance().unpairAll()
        clearConsent()
        save(configuration.copy(enabled = false, binding = HistoryBinding(UUID.randomUUID().toString(), UUID.randomUUID().toString(), freshStart = true),
            pendingRecoverySlot = slot, pendingRecoveryOperationId = operationId, pendingAdoptionSlot = null, pendingAdoption = null))
    }
    suspend fun recoverProfile() = action(checkBoundary = false) {
        check(canRecoverProfile && !adoptionInProgress) { "recovery-unavailable" }
        val handler = checkNotNull(recoveryHandler) { "recovery-unavailable" }
        permitted = false
        transport?.stop()
        adoptionInProgress = true
        try { handler() } catch (failure: Exception) {
            if (!isRestartRequired()) adoptionInProgress = false
            throw failure
        }
    }
    suspend fun initializeProfileSlot() = withContext(Dispatchers.IO) {
        operation.withLock { load(); reconcileProfileSlot(); detectTransferredHistory() }
    }
    private suspend fun reconcileProfileSlot() {
        if (adoptionInProgress) return
        if (configuration.pendingRecoverySlot == currentDatabaseSlot) {
            val recoveryOperation = checkNotNull(configuration.pendingRecoveryOperationId)
            require(currentSlotOperationId == recoveryOperation && currentSlotOperationDigest ==
                ProfileTransferCodec.digest("recovery\n$recoveryOperation\n$currentDatabaseSlot".toByteArray(Charsets.UTF_8))) {
                "profile-recovery-operation-mismatch"
            }
            val marker = repository.persistedHistoryBoundary()
            save(configuration.copy(databaseSlot = currentDatabaseSlot, pendingRecoverySlot = null, pendingRecoveryOperationId = null,
                pendingAdoptionSlot = null, pendingAdoption = null, enabled = false, blocked = false,
                hasEnabled = false, boundaryReady = false, localHistoryReady = false,
                afterLocalId = marker?.afterLocalId ?: 0, boundaryToken = marker?.token))
        }
        val pending = configuration.pendingAdoption
        if (pending != null && configuration.pendingAdoptionSlot == currentDatabaseSlot) {
            require(currentSlotOperationId == pending.operationId && currentSlotOperationDigest == ProfileConsentProtocol.tupleDigest(pending)) { "profile-slot-operation-mismatch" }
            val marker = checkNotNull(repository.profileTransfer.appliedOperation(pending.operationId, 0, null))
            require(marker.operationDigest == ProfileConsentProtocol.tupleDigest(pending) && marker.mode == "adopt") { "invalid-adoption-receipt" }
            val completed = ProfileConsentProtocol.Completion(pending, marker.resultingProfileIdentity, marker.resultingContentIdentity)
            LanSyncSecurity.writePrivate(completionFile, json.encodeToString(completed))
            save(configuration.copy(databaseSlot = currentDatabaseSlot, pendingAdoptionSlot = null,
                pendingAdoption = null, afterLocalId = 0, boundaryToken = null, blocked = false,
                boundaryReady = false, localHistoryReady = false))
        } else if (pending != null && configuration.databaseSlot == currentDatabaseSlot) {
            // Staging failed or was interrupted before pointer activation. Original DB is authoritative.
            save(configuration.copy(enabled = false, pendingAdoption = null, pendingAdoptionSlot = null))
        }
    }
    suspend fun approveProfile(operationId: String) = action {
        check(permitted) { "paused" }
        val state = consent().state
        val recipient = (state.mode == ProfileConsentProtocol.Mode.ADOPT_THIS && !state.proposerFirst) ||
            (state.mode == ProfileConsentProtocol.Mode.ADOPT_OTHER && state.proposerFirst)
        if (recipient) adoptionPreflight?.invoke()
        consent().approve(operationId)
    }
    suspend fun cancelProfile() = action(checkBoundary = false) { consent().clear() }
    private suspend fun clearConsent() {
        protocol?.clear()
        protocol = null
        if (completionFile.exists()) check(completionFile.delete())
    }

    fun requestForeground(value: Boolean) {
        foregroundRequested = value
        lifecycleScope.launch { applyForeground(null) }
    }
    suspend fun setForeground(value: Boolean) = applyForeground(value)
    private suspend fun applyForeground(requested: Boolean?) = withContext(Dispatchers.IO) {
        operation.withLock {
            val value = requested ?: foregroundRequested
            foreground = value
            if (!available) return@withLock
            try {
                load()
                if (value) { reconcileProfileSlot(); detectTransferredHistory() }
                if (value && configuration.enabled && !configuration.blocked && !adoptionInProgress) {
                    permitted = true
                    instance().start()
                } else {
                    permitted = false
                    transport?.stop()
                    protocol?.pause()
                    mutable.update { it.copy(invitation = null) }
                }
                publish()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                permitted = false
                withContext(NonCancellable) { transport?.stop() }
                throw cancelled
            } catch (error: Exception) {
                permitted = false
                transport?.stop()
                publish("unavailable")
            }
        }
    }
    suspend fun setEnabled(value: Boolean) = action(checkBoundary = value) {
        check(!configuration.blocked) { "history-blocked" }
        save(configuration.copy(enabled = value, hasEnabled = configuration.hasEnabled || value))
        permitted = value && foreground
        if (permitted) instance().start() else { transport?.stop(); protocol?.pause() }
        mutable.update { it.copy(invitation = null) }
    }
    suspend fun createInvitation(host: String) = action {
        check(configuration.enabled && foreground && !configuration.blocked) { "paused" }
        check(instance().status.peerCount == 0) { "single-peer-required" }
        val invitation = instance().createInvitation(host)
        mutable.update { it.copy(invitation = invitation) }
    }
    suspend fun pair(text: String) = action {
        check(configuration.enabled && foreground && !configuration.blocked) { "paused" }
        check(instance().status.peerCount == 0) { "single-peer-required" }
        val invite = PairedLanTransport.parseInvitation(text)
        if (invite.binding.freshStart && invite.binding != configuration.binding) {
            // A previous fresh epoch is still old history relative to this invitation.
            // Joining a new generation requires an empty eligible local journal.
            check(repository.snapshotJournal(configuration.afterLocalId, configuration.boundaryToken).isEmpty()) {
                "fresh-history-reset-required"
            }
        }
        if (configuration.binding.freshStart && !invite.binding.freshStart) {
            error("fresh-history-reset-required")
        }
        if (invite.binding != configuration.binding) {
            check((transport?.status?.peerCount ?: 0) == 0) { "history-mismatch" }
            permitted = false
            transport?.close()
            transport = null
            clearConsent()
            save(configuration.copy(binding = invite.binding))
            mutable.update { it.copy(lastSuccessAtMillis = null) }
        }
        permitted = true
        instance().start()
        instance().pair(text)
        mutable.update { it.copy(invitation = null) }
    }
    suspend fun revoke() = action(checkBoundary = false) {
        permitted = false
        transport?.stop()
        clearConsent()
        instance().unpairAll()
        save(configuration.copy(enabled = false))
        mutable.update { it.copy(invitation = null, lastSuccessAtMillis = null) }
    }

    suspend fun revokePeer(id: String) = action(checkBoundary = false) {
        permitted = false
        transport?.stop()
        clearConsent()
        instance().revokePeer(id)
        permitted = configuration.enabled && foreground && !configuration.blocked
        if (permitted) instance().start()
        publish()
    }

    /** Fail closed while reset outcome/cutoff is not yet durably known. */
    fun ensureHistoryWritable() {
        if (!available) return
        load()
        check(!adoptionInProgress && configuration.pendingAdoption == null) { "profile-restart-required" }
        check(!configuration.blocked || configuration.boundaryReady || configuration.localHistoryReady) {
            "History change is incomplete; reset progress in Settings before continuing learning"
        }
    }
    fun historyBoundary(): Long? {
        if (!available) return null
        ensureHistoryWritable()
        return configuration.afterLocalId.takeIf {
            configuration.hasEnabled || configuration.binding.freshStart || configuration.afterLocalId > 0
        }
    }
    fun undoBoundary(): Long? = historyBoundary()
    fun boundaryToken(): String? { if (!available) return null; load(); return configuration.boundaryToken }

    private suspend fun detectTransferredHistory() {
        val marker = repository.persistedHistoryBoundary()
        if (marker?.token != configuration.boundaryToken) {
            save(configuration.copy(enabled = false, blocked = true, boundaryReady = false, localHistoryReady = false))
            permitted = false
            transport?.stop()
            publish()
        }
    }
    suspend fun beforeExternalRestore(): Long = withContext(Dispatchers.IO) {
        operation.withLock {
            if (!available) return@withLock 0L
            load()
            check(!adoptionInProgress && configuration.pendingAdoption == null && !isRestartRequired()) {
                "profile-restart-required"
            }
            permitted = false
            transport?.stop()
            detectTransferredHistory()
            if (configuration.hasEnabled || configuration.binding.freshStart || configuration.blocked) blockHistoryChange()
            else {
                save(configuration.copy(mutationGeneration = Math.addExact(configuration.mutationGeneration, 1)))
                configuration.mutationGeneration
            }
        }
    }

    /** Restore succeeded: local whole-journal learning is safe; pairing still needs reset. */
    suspend fun afterExternalRestore(generation: Long) = withContext(Dispatchers.IO) {
        operation.withLock {
            if (!available) return@withLock
            load()
            check(configuration.mutationGeneration == generation) { "Stale history operation completion" }
            if (configuration.blocked) save(configuration.copy(afterLocalId = 0,
                boundaryReady = false, localHistoryReady = true,
                boundaryToken = repository.persistedHistoryBoundary()?.token))
            publish()
        }
    }

    /** Persist fail-closed state BEFORE a reset/restore can mutate learner history. */
    suspend fun beforeHistoryChange(): Long = withContext(Dispatchers.IO) {
        operation.withLock {
            if (!available) return@withLock 0L
            load()
            check(!adoptionInProgress && configuration.pendingAdoption == null && !isRestartRequired()) {
                "profile-restart-required"
            }
            blockHistoryChange()
        }
    }
    private suspend fun blockHistoryChange(): Long {
        save(configuration.copy(enabled = false, blocked = true, boundaryReady = false, localHistoryReady = false,
            mutationGeneration = Math.addExact(configuration.mutationGeneration, 1)))
        permitted = false
        transport?.stop()
        clearConsent()
        instance().unpairAll()
        mutable.update { it.copy(invitation = null, lastSuccessAtMillis = null) }
        publish()
        return configuration.mutationGeneration
    }
    /** Release listener and worker threads; persisted pairing remains available after reopening. */
    suspend fun close() = withContext(NonCancellable + Dispatchers.IO) {
        operation.withLock {
            permitted = false
            foreground = false
            foregroundRequested = false
            transport?.close()
            protocol?.pause()
            transport = null
            mutable.update { it.copy(invitation = null) }
            publish()
        }
        lifecycleScope.cancel()
    }
    /** Called only after an atomic progress reset succeeds, never after a manual restore. */
    suspend fun afterProgressReset(boundary: Long, generation: Long) = withContext(Dispatchers.IO) {
        operation.withLock {
            if (!available) return@withLock
            load()
            require(configuration.blocked && boundary >= 0 && configuration.mutationGeneration == generation)
            val marker = repository.persistedHistoryBoundary()
            require(marker != null && marker.afterLocalId == boundary) { "Reset boundary was not committed" }
            save(configuration.copy(afterLocalId = boundary, boundaryReady = true, localHistoryReady = true,
                boundaryToken = marker.token))
            publish()
        }
    }
    /** Explicit user action after reset: retain archived reviews, exchange only the new generation. */
    suspend fun startNewHistory() = action {
        check(configuration.blocked && configuration.boundaryReady) { "reset-required" }
        permitted = false
        transport?.close()
        transport = null
        save(configuration.copy(binding = HistoryBinding(UUID.randomUUID().toString(), UUID.randomUUID().toString(), true),
            blocked = false, boundaryReady = false, enabled = false))
        mutable.update { it.copy(invitation = null, lastSuccessAtMillis = null) }
    }
    private suspend fun action(checkBoundary: Boolean = true, block: suspend () -> Unit) = withContext(Dispatchers.IO) {
        operation.withLock {
            try { load(); if (checkBoundary) detectTransferredHistory(); block(); publish() }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) {
                // No invitation, credential, peer data or raw exception is logged/displayed.
                val safe = if (error is UnsupportedSyncProtocolException) "unsupported_sync_protocol"
                else if (error is dev.ikna.data.export.JournalReconciliationRequiredException) "journal_reconciliation_required"
                else if (error is dev.ikna.data.export.IncrementalJournalLimitException) "journal_item_too_large"
                else if (error is dev.ikna.data.export.ProfileTransferLimitException) "profile-too-large" else when (error.message) {
                    "history-mismatch", "fresh-history-reset-required", "reset-required", "history-blocked" -> "history-mismatch"
                    "adoption-not-supported", "awaiting-profile", "profile-conflict-or-stale", "proposal-pending",
                    "single-peer-required", "profile-too-large", "proposal-expired", "stale-proposal" -> error.message!!
                    else -> "operation-failed"
                }
                publish(safe)
            }
        }
    }
    companion object { const val MAX_BYTES = 4 * 1024 * 1024; const val MAX_RECORDS = 50_000 }
}
