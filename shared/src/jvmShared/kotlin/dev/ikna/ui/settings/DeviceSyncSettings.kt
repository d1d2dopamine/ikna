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
    val state = DeviceSyncUiState(
        enabled = current.enabled, paired = current.peers > 0,
        status = deviceSyncStatus(current),
        peers = current.devices.map { peer ->
            DeviceSyncPeerUiState(peer.id, peer.fingerprint, peer.address,
                peer.lastSuccessAtMillis?.let { time ->
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time))
                }, peer.lastError, peer.name)
        },
        localAddresses = current.addresses,
        invitation = current.invitation,
        lastSuccessText = current.lastSuccessAtMillis?.let {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it))
        },
        error = current.error?.let {
            deviceSyncError(it)
        },
        blockedAfterReset = current.blocked,
        canStartNewHistory = current.boundaryReady,
    )
    ProfileConsentPanel(
        state = current.profileConsent,
        thisDeviceName = thisDeviceLabel,
        peerDeviceName = peerDeviceLabel,
        onProposeMerge = { scope.launch { controller.proposeMerge(it) } },
        onProposeAdoption = { peer, useThis -> scope.launch { controller.proposeAdoption(peer, useThis) } },
        onApprove = { scope.launch { controller.approveProfile(it) } },
        onCancel = { scope.launch { controller.cancelProfile() } }
    )
    DeviceSyncPanel(state,
        onEnabledChange = { scope.launch { controller.setEnabled(it) } },
        onCreateInvitation = { scope.launch { controller.createInvitation(it) } },
        onPair = { scope.launch { controller.pair(it) } },
        onRevoke = { scope.launch { controller.revoke() } },
        onRevokePeer = { scope.launch { controller.revokePeer(it) } },
        onStartNewHistory = { scope.launch { controller.startNewHistory() } },
        scannedCode = scannedCode,
        onScannedCodeConsumed = onScannedCodeConsumed,
        onScanQr = onScanQr)
    if (current.recoveryAvailable) ProfileRecoveryPanel {
        scope.launch { controller.recoverProfile() }
    }
}

/** Stable, allowlisted presentation codes; raw exception text never reaches the UI. */
internal fun deviceSyncError(code: String): DeviceSyncError = when (code) {
    "unsupported_sync_protocol" -> DeviceSyncError.INCOMPATIBLE_PROTOCOL
    "journal_reconciliation_required" -> DeviceSyncError.JOURNAL_RECONCILIATION
    "journal_item_too_large" -> DeviceSyncError.JOURNAL_ITEM_TOO_LARGE
    "profile-too-large", "payload_too_large" -> DeviceSyncError.PROFILE_TOO_LARGE
    "adoption-recovery-required" -> DeviceSyncError.ADOPTION
    "history-mismatch" -> DeviceSyncError.HISTORY_MISMATCH
    "expired_invitation" -> DeviceSyncError.EXPIRED_INVITATION
    "invalid_invitation" -> DeviceSyncError.INVALID_INVITATION
    "connection_failed" -> DeviceSyncError.NETWORK
    "unavailable" -> DeviceSyncError.UNAVAILABLE
    else -> DeviceSyncError.MERGE_FAILED
}

/** Consent approval and successful control frames are not journal catch-up. */
internal fun deviceSyncStatus(current: DeviceSyncController.State): DeviceSyncStatus = when {
    current.error != null -> DeviceSyncStatus.ERROR
    !current.enabled -> DeviceSyncStatus.OFF
    current.peers == 0 -> DeviceSyncStatus.UNPAIRED
    !current.running -> DeviceSyncStatus.WAITING
    current.profileConsent.complete && current.profileConsent.journalPending -> DeviceSyncStatus.SYNCING
    current.profileConsent.complete && current.profileConsent.journalCaughtUp && current.lastSuccessAtMillis != null -> DeviceSyncStatus.UP_TO_DATE
    else -> DeviceSyncStatus.WAITING
}
