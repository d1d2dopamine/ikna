package dev.ikna.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.ikna.sync.DeviceSyncController
import dev.ikna.sync.PairedLanTransport
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Same opt-in controls on phone and desktop, without a second pairing implementation. */
@Composable
fun DeviceSyncSettings(
    controller: DeviceSyncController,
    scannedCode: String? = null,
    onScannedCodeConsumed: () -> Unit = {},
    onScanQr: (() -> Unit)? = null,
    localProfileName: String = ""
) {
    val current by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    val defaultName = remember { PairedLanTransport.defaultDeviceDisplayName() }
    val thisDeviceLabel = localProfileName.trim().ifBlank { defaultName }
    val peerDeviceLabel = current.devices
        .firstOrNull { it.id == current.profileConsent.peerId }
        ?.let { it.name.ifBlank { it.address } }
        ?: current.devices.firstOrNull()?.let { it.name.ifBlank { it.address } }
        ?: ""
    val hasConflicts = current.profileConsent.preview?.conflicts?.isNotEmpty() == true
    val state = DeviceSyncUiState(
        enabled = current.enabled,
        paired = current.peers > 0,
        status = deviceSyncStatus(current),
        peers = current.devices.map { peer ->
            DeviceSyncPeerUiState(
                id = peer.id,
                fingerprint = peer.fingerprint,
                address = peer.address,
                lastSuccessText = peer.lastSuccessAtMillis?.let { time ->
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time))
                },
                error = peer.lastError,
                name = peer.name,
                role = peer.role,
            )
        },
        localAddresses = current.addresses,
        invitation = current.invitation,
        lastSuccessText = current.lastSuccessAtMillis?.let {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it))
        },
        error = current.error?.let {
            deviceSyncError(it, hasProfileConflicts = hasConflicts)
        },
        blockedAfterReset = current.blocked,
        canStartNewHistory = current.boundaryReady,
        busy = current.busy,
        progress = deviceSyncProgress(current, peerDeviceLabel),
        logs = current.logs.map { entry ->
            val time = DateTimeFormatter.ofPattern("HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(entry.timestampMillis))
            DeviceSyncLogItem(time = time, isError = entry.isError, message = entry.message)
        },
    )
    ProfileConsentPanel(
        state = current.profileConsent,
        peerRole = current.peerRole,
        stage = current.stage,
        thisDeviceName = thisDeviceLabel,
        peerDeviceName = peerDeviceLabel,
        onChooseMerge = { scope.launch { controller.chooseMerge(it) } },
        onChooseAdoption = { peer, useThis -> scope.launch { controller.chooseAdoption(peer, useThis) } },
        onApprove = { scope.launch { controller.approveProfile(it) } },
        onCancel = { scope.launch { controller.cancelProfile() } }
    )
    DeviceSyncPanel(
        state = state,
        onEnabledChange = { scope.launch { controller.setEnabled(it) } },
        onCreateInvitation = { scope.launch { controller.createInvitation(it) } },
        onPair = { scope.launch { controller.pair(it) } },
        onRevoke = { scope.launch { controller.revoke() } },
        onRevokePeer = { scope.launch { controller.revokePeer(it) } },
        onStartNewHistory = { scope.launch { controller.startNewHistory() } },
        onSyncNow = { scope.launch { controller.syncNow() } },
        scannedCode = scannedCode,
        onScannedCodeConsumed = onScannedCodeConsumed,
        onScanQr = onScanQr
    )
    if (current.recoveryAvailable) ProfileRecoveryPanel {
        scope.launch { controller.recoverProfile() }
    }
}

/** Stable, allowlisted presentation codes; raw exception text never reaches the UI. */
internal fun deviceSyncError(code: String, hasProfileConflicts: Boolean = false): DeviceSyncError = when (code) {
    "unsupported_sync_protocol" -> DeviceSyncError.INCOMPATIBLE_PROTOCOL
    "journal_reconciliation_required" -> DeviceSyncError.JOURNAL_RECONCILIATION
    "journal_item_too_large" -> DeviceSyncError.JOURNAL_ITEM_TOO_LARGE
    "profile-too-large", "payload_too_large" -> DeviceSyncError.PROFILE_TOO_LARGE
    "adoption-recovery-required" -> DeviceSyncError.ADOPTION
    "history-mismatch" -> DeviceSyncError.HISTORY_MISMATCH
    "expired_invitation" -> DeviceSyncError.EXPIRED_INVITATION
    "invalid_invitation" -> DeviceSyncError.INVALID_INVITATION
    "already_paired" -> DeviceSyncError.ALREADY_PAIRED
    "tls_trust_failed", "unpaired_device" -> DeviceSyncError.UNTRUSTED_DEVICE
    "connection_timeout" -> DeviceSyncError.CONNECTION_TIMEOUT
    "connection_refused" -> DeviceSyncError.CONNECTION_REFUSED
    "network_unreachable" -> DeviceSyncError.NETWORK_UNREACHABLE
    "peer_closed_connection", "connection_interrupted" -> DeviceSyncError.PEER_CLOSED_CONNECTION
    "port_unavailable" -> DeviceSyncError.PORT_UNAVAILABLE
    "connection_failed" -> DeviceSyncError.NETWORK
    "profile-conflict-or-stale" -> if (hasProfileConflicts) DeviceSyncError.PROFILE_CONFLICT else DeviceSyncError.PROFILE_STALE
    "profile_conflict", "profile-content-conflict" -> DeviceSyncError.PROFILE_CONFLICT
    "profile_stale", "stale-proposal", "proposal-expired", "proposal-cancelled",
    "local-profile-stale", "remote-profile-stale" -> DeviceSyncError.PROFILE_STALE
    "unavailable" -> DeviceSyncError.UNAVAILABLE
    else -> DeviceSyncError.MERGE_FAILED
}

/** Consent approval and successful control frames are not journal catch-up. */
internal fun deviceSyncStatus(current: DeviceSyncController.State): DeviceSyncStatus = when {
    current.stage in setOf(
        DeviceSyncController.SyncStage.PAIRING_CONNECTING,
        DeviceSyncController.SyncStage.PAIRING_HANDSHAKE,
        DeviceSyncController.SyncStage.CONNECTING_PEER,
        DeviceSyncController.SyncStage.HANDSHAKING_PEER,
    ) -> DeviceSyncStatus.CONNECTING
    current.stage in setOf(
        DeviceSyncController.SyncStage.EXCHANGING_PROFILES,
        DeviceSyncController.SyncStage.APPLYING_PROFILE,
        DeviceSyncController.SyncStage.CONFIRMING_PROFILE,
        DeviceSyncController.SyncStage.SYNCING_JOURNAL,
    ) -> DeviceSyncStatus.SYNCING
    current.error != null -> DeviceSyncStatus.ERROR
    !current.enabled -> DeviceSyncStatus.OFF
    current.peers == 0 -> DeviceSyncStatus.UNPAIRED
    !current.running -> DeviceSyncStatus.WAITING
    current.profileConsent.complete && current.profileConsent.journalPending -> DeviceSyncStatus.SYNCING
    current.profileConsent.complete && current.profileConsent.journalCaughtUp && current.lastSuccessAtMillis != null -> DeviceSyncStatus.UP_TO_DATE
    else -> DeviceSyncStatus.WAITING
}

internal fun deviceSyncProgress(
    current: DeviceSyncController.State,
    peerDeviceLabel: String = ""
): DeviceSyncProgressUi? {
    val stage = current.stage
    if (stage == DeviceSyncController.SyncStage.IDLE) return null
    val fraction = when (stage) {
        DeviceSyncController.SyncStage.IDLE -> 0f
        DeviceSyncController.SyncStage.PAIRING_CONNECTING -> {
            if (current.stageTotal > 1) {
                0.15f + 0.20f * ((current.stageAttempt - 1).coerceAtLeast(0).toFloat() / current.stageTotal)
            } else 0.22f
        }
        DeviceSyncController.SyncStage.PAIRING_HANDSHAKE -> 0.45f
        DeviceSyncController.SyncStage.CONNECTING_PEER -> {
            if (current.stageTotal > 1) {
                0.20f + 0.15f * ((current.stageAttempt - 1).coerceAtLeast(0).toFloat() / current.stageTotal)
            } else 0.25f
        }
        DeviceSyncController.SyncStage.HANDSHAKING_PEER -> 0.45f
        DeviceSyncController.SyncStage.EXCHANGING_PROFILES -> 0.62f
        DeviceSyncController.SyncStage.WAITING_FOR_INVITER -> 0.72f
        DeviceSyncController.SyncStage.APPLYING_PROFILE -> 0.85f
        DeviceSyncController.SyncStage.CONFIRMING_PROFILE -> 0.93f
        DeviceSyncController.SyncStage.SYNCING_JOURNAL -> 0.88f
    }
    return DeviceSyncProgressUi(
        stage = stage,
        fraction = fraction,
        attempt = current.stageAttempt,
        total = current.stageTotal,
        peerName = peerDeviceLabel,
    )
}
