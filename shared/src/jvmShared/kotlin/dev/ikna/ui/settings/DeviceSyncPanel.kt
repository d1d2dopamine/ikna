package dev.ikna.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ikna.sync.DeviceSyncController
import dev.ikna.sync.IknaBrandedQrCard
import dev.ikna.sync.PairedLanTransport
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaProgress
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.IknaTextField
import dev.ikna.ui.theme.IknaWideButton
import dev.ikna.ui.theme.Space
import dev.ikna.ui.theme.iknaNumberStyle
import dev.ikna.ui.theme.iknaTileShape
import kotlin.math.roundToInt

/** Transport-independent presentation states; never display raw transport exceptions. */
enum class DeviceSyncStatus { OFF, UNPAIRED, WAITING, CONNECTING, SYNCING, UP_TO_DATE, ERROR }
enum class DeviceSyncError {
    CONNECTION_TIMEOUT,
    CONNECTION_REFUSED,
    NETWORK_UNREACHABLE,
    PEER_CLOSED_CONNECTION,
    NETWORK,
    INVALID_INVITATION,
    EXPIRED_INVITATION,
    ALREADY_PAIRED,
    PORT_UNAVAILABLE,
    UNTRUSTED_DEVICE,
    PROFILE_CONFLICT,
    PROFILE_STALE,
    MERGE_FAILED,
    UNAVAILABLE,
    HISTORY_MISMATCH,
    CONTENT_MISSING,
    ADOPTION,
    PROFILE_TOO_LARGE,
    INCOMPATIBLE_PROTOCOL,
    JOURNAL_ITEM_TOO_LARGE,
    JOURNAL_RECONCILIATION,
}

data class DeviceSyncProgressUi(
    val stage: DeviceSyncController.SyncStage,
    val fraction: Float,
    val attempt: Int = 0,
    val total: Int = 0,
    val peerName: String = "",
)

data class DeviceSyncPeerUiState(
    val id: String, val fingerprint: String, val address: String,
    val lastSuccessText: String? = null, val error: String? = null,
    val name: String = "",
    val role: PairedLanTransport.PeerRole = PairedLanTransport.PeerRole.UNKNOWN,
) {
    override fun toString(): String = "DeviceSyncPeerUiState(device=<redacted>)"
}

data class DeviceSyncLogItem(
    val time: String,
    val isError: Boolean,
    val message: String,
)

data class DeviceSyncUiState(
    val enabled: Boolean = false,
    val paired: Boolean = false,
    val peers: List<DeviceSyncPeerUiState> = emptyList(),
    val status: DeviceSyncStatus = DeviceSyncStatus.OFF,
    val localAddresses: List<String> = emptyList(),
    val invitation: String? = null,
    val lastSuccessText: String? = null,
    val error: DeviceSyncError? = null,
    val blockedAfterReset: Boolean = false,
    val canStartNewHistory: Boolean = false,
    val busy: Boolean = false,
    val progress: DeviceSyncProgressUi? = null,
    val logs: List<DeviceSyncLogItem> = emptyList(),
) {
    // Invitations contain pairing credentials. Do not accidentally include them in logs.
    override fun toString(): String = "DeviceSyncUiState(enabled=$enabled, paired=$paired, status=$status, error=$error, blockedAfterReset=$blockedAfterReset, busy=$busy)"
}

internal fun syncErrorTitleKey(error: DeviceSyncError): String = when (error) {
    DeviceSyncError.CONNECTION_TIMEOUT -> "sync.error.timeout"
    DeviceSyncError.CONNECTION_REFUSED -> "sync.error.refused"
    DeviceSyncError.NETWORK_UNREACHABLE -> "sync.error.unreachable"
    DeviceSyncError.PEER_CLOSED_CONNECTION -> "sync.error.peer_closed"
    DeviceSyncError.NETWORK -> "sync.error.network"
    DeviceSyncError.INVALID_INVITATION -> "sync.error.invalid"
    DeviceSyncError.EXPIRED_INVITATION -> "sync.error.expired"
    DeviceSyncError.ALREADY_PAIRED -> "sync.error.already_paired"
    DeviceSyncError.PORT_UNAVAILABLE -> "sync.error.port_unavailable"
    DeviceSyncError.UNTRUSTED_DEVICE -> "sync.error.trust"
    DeviceSyncError.PROFILE_CONFLICT -> "sync.error.profile_conflict"
    DeviceSyncError.PROFILE_STALE -> "sync.error.profile_stale"
    DeviceSyncError.MERGE_FAILED -> "sync.error.merge"
    DeviceSyncError.UNAVAILABLE -> "sync.error.unavailable"
    DeviceSyncError.HISTORY_MISMATCH -> "sync.error.history_mismatch"
    DeviceSyncError.CONTENT_MISSING -> "sync.error.content_missing"
    DeviceSyncError.ADOPTION -> "sync.error.adoption"
    DeviceSyncError.PROFILE_TOO_LARGE -> "sync.error.profile_too_large"
    DeviceSyncError.INCOMPATIBLE_PROTOCOL -> "sync.error.protocol"
    DeviceSyncError.JOURNAL_ITEM_TOO_LARGE -> "sync.error.journal_item_too_large"
    DeviceSyncError.JOURNAL_RECONCILIATION -> "sync.error.journal_reconciliation"
}

internal fun syncErrorFixKey(error: DeviceSyncError): String = when (error) {
    DeviceSyncError.CONNECTION_TIMEOUT -> "sync.fix.timeout"
    DeviceSyncError.CONNECTION_REFUSED -> "sync.fix.refused"
    DeviceSyncError.NETWORK_UNREACHABLE -> "sync.fix.unreachable"
    DeviceSyncError.PEER_CLOSED_CONNECTION -> "sync.fix.peer_closed"
    DeviceSyncError.NETWORK -> "sync.fix.network"
    DeviceSyncError.INVALID_INVITATION -> "sync.fix.invalid"
    DeviceSyncError.EXPIRED_INVITATION -> "sync.fix.expired"
    DeviceSyncError.ALREADY_PAIRED -> "sync.fix.already_paired"
    DeviceSyncError.PORT_UNAVAILABLE -> "sync.fix.port_unavailable"
    DeviceSyncError.UNTRUSTED_DEVICE -> "sync.fix.trust"
    DeviceSyncError.PROFILE_CONFLICT -> "sync.fix.profile_conflict"
    DeviceSyncError.PROFILE_STALE -> "sync.fix.profile_stale"
    DeviceSyncError.MERGE_FAILED -> "sync.fix.merge"
    DeviceSyncError.UNAVAILABLE -> "sync.fix.unavailable"
    DeviceSyncError.HISTORY_MISMATCH -> "sync.fix.history_mismatch"
    DeviceSyncError.CONTENT_MISSING -> "sync.fix.content_missing"
    DeviceSyncError.ADOPTION -> "sync.fix.adoption"
    DeviceSyncError.PROFILE_TOO_LARGE -> "sync.fix.profile_too_large"
    DeviceSyncError.INCOMPATIBLE_PROTOCOL -> "sync.fix.protocol"
    DeviceSyncError.JOURNAL_ITEM_TOO_LARGE -> "sync.fix.journal_item_too_large"
    DeviceSyncError.JOURNAL_RECONCILIATION -> "sync.fix.journal_reconciliation"
}

internal fun syncStageLabel(progress: DeviceSyncProgressUi): String {
    val device = progress.peerName.ifBlank { S.t("sync.other_device") }
    return when (progress.stage) {
        DeviceSyncController.SyncStage.IDLE -> ""
        DeviceSyncController.SyncStage.PAIRING_CONNECTING -> {
            if (progress.total > 1) {
                S.t("sync.stage.pairing_connecting_attempt")
                    .replace("{attempt}", progress.attempt.toString())
                    .replace("{total}", progress.total.toString())
            } else {
                S.t("sync.stage.pairing_connecting")
            }
        }
        DeviceSyncController.SyncStage.PAIRING_HANDSHAKE -> S.t("sync.stage.pairing_handshake")
        DeviceSyncController.SyncStage.CONNECTING_PEER -> {
            if (progress.total > 1) {
                S.t("sync.stage.connecting_peer_attempt")
                    .replace("{attempt}", progress.attempt.toString())
                    .replace("{total}", progress.total.toString())
            } else {
                S.t("sync.stage.connecting_peer")
            }
        }
        DeviceSyncController.SyncStage.HANDSHAKING_PEER -> S.t("sync.stage.handshaking_peer")
        DeviceSyncController.SyncStage.EXCHANGING_PROFILES -> S.t("sync.stage.exchanging_profiles")
        DeviceSyncController.SyncStage.WAITING_FOR_INVITER ->
            S.t("sync.stage.waiting_for_inviter").replace("{device}", device)
        DeviceSyncController.SyncStage.APPLYING_PROFILE -> S.t("sync.stage.applying_profile")
        DeviceSyncController.SyncStage.CONFIRMING_PROFILE -> S.t("sync.stage.confirming_profile")
        DeviceSyncController.SyncStage.SYNCING_JOURNAL -> S.t("sync.stage.syncing_journal")
    }
}

/** Matches the deck-installation progress line in [dev.ikna.ui.catalog.IknaCatalogDeckRow]. */
@Composable
internal fun IknaSyncProgressStrip(
    label: String,
    fraction: Float,
    detail: String? = null,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val percent = (fraction.coerceIn(0f, 1f) * 100f).roundToInt()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = ink,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$percent%",
                style = iknaNumberStyle(MaterialTheme.typography.labelLarge),
                color = muted
            )
        }
        IknaProgress(
            fraction = fraction,
            height = 6.dp,
            color = ink,
            track = true
        )
        if (!detail.isNullOrBlank()) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = muted
            )
        }
    }
}

/**
 * This panel never starts network work during composition and never writes the clipboard.
 * The controller must independently enforce consent, expiry, trust and reset boundaries.
 * Invitation and consent inputs deliberately do not survive navigation/process recreation.
 */
@Composable
fun DeviceSyncPanel(
    state: DeviceSyncUiState,
    onEnabledChange: (Boolean) -> Unit,
    onCreateInvitation: (String) -> Unit,
    onPair: (String) -> Unit,
    onRevoke: () -> Unit,
    onRevokePeer: (String) -> Unit,
    onStartNewHistory: () -> Unit,
    modifier: Modifier = Modifier,
    onSyncNow: (() -> Unit)? = null,
    scannedCode: String? = null,
    onScannedCodeConsumed: () -> Unit = {},
    onScanQr: (() -> Unit)? = null
) {
    var revokeId by remember(state.peers.map { it.id }) { mutableStateOf<String?>(null) }
    var pasted by remember(state.enabled, state.paired, state.blockedAfterReset) { mutableStateOf("") }
    var showManualInput by remember(state.enabled, state.paired, state.blockedAfterReset) {
        mutableStateOf(onScanQr == null)
    }
    var showAddressPicker by remember(state.localAddresses) { mutableStateOf(false) }
    var confirmNewHistory by remember(state.blockedAfterReset, state.canStartNewHistory) { mutableStateOf(false) }
    var address by remember(state.localAddresses) { mutableStateOf(state.localAddresses.firstOrNull().orEmpty()) }
    val canPair = state.enabled && !state.paired && !state.blockedAfterReset && !state.busy
    LaunchedEffect(scannedCode, canPair) {
        val code = scannedCode?.trim().orEmpty()
        if (code.isNotEmpty()) {
            onScannedCodeConsumed()
            if (canPair) {
                pasted = ""
                onPair(code.take(4096))
            } else {
                pasted = code.take(4096)
                showManualInput = true
            }
        }
    }
    val pair = {
        if (canPair && pasted.isNotBlank()) {
            val invitation = pasted.trim()
            pasted = ""
            onPair(invitation)
        }
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IknaSettingsToggleRow(
            title = S.t("sync.enabled"),
            subtitle = S.t("sync.scope"),
            checked = state.enabled,
            enabled = !state.blockedAfterReset,
            onCheckedChange = onEnabledChange
        )
        Text(
            text = S.t(when (state.status) {
                DeviceSyncStatus.OFF -> "sync.status.off"
                DeviceSyncStatus.UNPAIRED -> "sync.status.unpaired"
                DeviceSyncStatus.WAITING -> "sync.status.waiting"
                DeviceSyncStatus.CONNECTING -> "sync.status.connecting"
                DeviceSyncStatus.SYNCING -> "sync.status.syncing"
                DeviceSyncStatus.UP_TO_DATE -> "sync.status.current"
                DeviceSyncStatus.ERROR -> "sync.status.error"
            }),
            style = MaterialTheme.typography.bodyMedium
        )
        state.progress?.let { progress ->
            IknaSyncProgressStrip(
                label = syncStageLabel(progress),
                fraction = progress.fraction,
                detail = S.t("sync.stage.keep_open"),
            )
        }
        state.lastSuccessText?.let {
            Text(S.t("sync.last_success").replace("{time}", it), style = MaterialTheme.typography.bodySmall)
        }
        state.error?.let { err ->
            val cardShape = iknaTileShape()
            val retryable = err in setOf(
                DeviceSyncError.CONNECTION_TIMEOUT,
                DeviceSyncError.CONNECTION_REFUSED,
                DeviceSyncError.NETWORK_UNREACHABLE,
                DeviceSyncError.PEER_CLOSED_CONNECTION,
                DeviceSyncError.NETWORK,
                DeviceSyncError.MERGE_FAILED,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .background(MaterialTheme.colorScheme.surface, cardShape)
                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.65f), cardShape)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = S.t(syncErrorTitleKey(err)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = S.t("sync.fix_title"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = S.t(syncErrorFixKey(err)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (retryable && state.enabled && state.paired && !state.blockedAfterReset && onSyncNow != null) {
                    IknaTextButton(
                        label = S.t("sync.retry_now"),
                        enabled = !state.busy,
                        horizontalPadding = 0.dp,
                        onClick = onSyncNow
                    )
                } else if (err == DeviceSyncError.EXPIRED_INVITATION && canPair && address.isNotBlank()) {
                    IknaTextButton(
                        label = S.t("sync.show_qr_btn"),
                        horizontalPadding = 0.dp,
                        onClick = { onCreateInvitation(address) }
                    )
                }
            }
        }
        if (state.blockedAfterReset) {
            Text(S.t("sync.reset_blocked"), style = MaterialTheme.typography.bodySmall)
            if (!state.canStartNewHistory) {
                Text(S.t("sync.reset_required"), style = MaterialTheme.typography.bodySmall)
            }
            IknaSettingsToggleRow(
                title = S.t("sync.new_history_confirm"),
                subtitle = S.t("sync.new_history_scope"),
                checked = confirmNewHistory,
                enabled = state.canStartNewHistory && !state.busy,
                onCheckedChange = { confirmNewHistory = it }
            )
            IknaWideButton(
                label = S.t("sync.new_history"),
                enabled = state.canStartNewHistory && confirmNewHistory && !state.busy,
                onClick = {
                    if (state.canStartNewHistory && confirmNewHistory && !state.busy) {
                        confirmNewHistory = false
                        onStartNewHistory()
                    }
                }
            )
        } else if (state.enabled && !state.paired) {
            Text(
                text = S.t("sync.quick_guide"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onScanQr != null) {
                IknaWideButton(
                    label = S.t("sync.scan_qr"),
                    enabled = canPair,
                    onClick = onScanQr
                )
            }
            if (state.localAddresses.size > 1) {
                IknaTextButton(
                    label = S.t("sync.address") + " · " + address,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    horizontalPadding = 0.dp,
                    onClick = { showAddressPicker = !showAddressPicker }
                )
                if (showAddressPicker) {
                    state.localAddresses.forEach { candidate ->
                        IknaSettingsToggleRow(
                            title = candidate,
                            subtitle = null,
                            checked = address == candidate,
                            enabled = !state.busy,
                            onCheckedChange = { if (it) { address = candidate; showAddressPicker = false } }
                        )
                    }
                }
            }
            IknaWideButton(
                label = S.t("sync.show_qr_btn"),
                enabled = canPair && address.isNotBlank(),
                onClick = { if (canPair && address.isNotBlank()) onCreateInvitation(address) }
            )
            if (address.isBlank()) {
                Text(
                    text = S.t("sync.no_network_hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.invitation?.takeIf { it.isNotBlank() }?.let { invitation ->
                val qrCode = remember(invitation) {
                    runCatching { PairedLanTransport.encodeQrInvitation(invitation) }
                        .recoverCatching { PairedLanTransport.encodeCompactInvitation(invitation) }
                        .getOrDefault(invitation)
                }
                @Suppress("DEPRECATION")
                val clipboard = LocalClipboardManager.current
                var copied by remember(qrCode) { mutableStateOf(false) }
                var showRawCode by remember(qrCode) { mutableStateOf(false) }
                IknaBrandedQrCard(
                    content = qrCode,
                    subtitle = S.t("sync.qr_hint")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IknaTextButton(
                        label = S.t(if (copied) "sync.code_copied" else "sync.copy_code"),
                        onClick = {
                            clipboard.setText(AnnotatedString(qrCode))
                            copied = true
                        }
                    )
                    IknaTextButton(
                        label = S.t(if (showRawCode) "sync.hide_code" else "sync.show_code"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { showRawCode = !showRawCode }
                    )
                }
                if (showRawCode) {
                    SelectionContainer {
                        // Expose the standard JSON invitation as a fallback for peers on older builds.
                        Text(invitation, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (onScanQr != null) {
                IknaTextButton(
                    label = S.t(if (showManualInput) "sync.manual_hide" else "sync.manual_code"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { showManualInput = !showManualInput }
                )
            }
            if (showManualInput) {
                @Suppress("DEPRECATION")
                val pasteClipboard = LocalClipboardManager.current
                IknaTextField(
                    value = pasted,
                    onValueChange = { pasted = it },
                    placeholder = S.t("sync.paste"),
                    onSearch = pair,
                    modifier = Modifier.fillMaxWidth(),
                    maxLength = 4096
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IknaWideButton(
                        label = S.t("sync.pair"),
                        enabled = canPair && pasted.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        onClick = pair
                    )
                    IknaTextButton(
                        label = S.t("sync.paste_btn"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = {
                            val clip = pasteClipboard.getText()?.text?.trim().orEmpty()
                            if (clip.isNotEmpty()) pasted = clip.take(4096)
                        }
                    )
                }
            }
        }
        if (state.enabled && state.paired && !state.blockedAfterReset && onSyncNow != null) {
            IknaWideButton(
                label = S.t("sync.sync_now"),
                enabled = !state.busy,
                onClick = onSyncNow
            )
        }
        if (state.peers.isNotEmpty()) {
            Text(S.t("sync.devices"), style = MaterialTheme.typography.titleSmall)
            state.peers.forEach { peer ->
                val displayName = peer.name.ifBlank { peer.address }
                val shortId = peer.fingerprint.replace(" ", "").take(8).chunked(4).joinToString(" ")
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${peer.address} · #$shortId",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(peer.lastSuccessText?.let { S.t("sync.device_success").replace("{time}", it) }
                        ?: S.t("sync.device_no_exchange"), style = MaterialTheme.typography.bodySmall)
                    peer.error?.let { rawError ->
                        val mappedPeerError = deviceSyncError(rawError)
                        Text(
                            text = S.t(syncErrorTitleKey(mappedPeerError)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    IknaSettingsToggleRow(
                        title = S.t("sync.device_revoke_confirm"),
                        subtitle = S.t("sync.device_revoke_scope"),
                        checked = revokeId == peer.id,
                        enabled = !state.busy,
                        onCheckedChange = { revokeId = if (it) peer.id else null }
                    )
                    IknaWideButton(label = S.t("sync.device_revoke"),
                        enabled = revokeId == peer.id && !state.busy,
                        onClick = {
                            if (revokeId == peer.id && !state.busy) {
                                revokeId = null
                                onRevokePeer(peer.id)
                            }
                        })
                }
            }
        }
        if (state.logs.isNotEmpty()) {
            var showLog by remember(state.error != null) { mutableStateOf(state.error != null) }
            var copiedLog by remember(state.logs.size) { mutableStateOf(false) }
            @Suppress("DEPRECATION")
            val logClipboard = LocalClipboardManager.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IknaTextButton(
                    label = S.t(if (showLog) "sync.log_hide" else "sync.log_show")
                        .replace("{count}", state.logs.size.toString()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    horizontalPadding = 0.dp,
                    onClick = { showLog = !showLog }
                )
                if (showLog) {
                    IknaTextButton(
                        label = S.t(if (copiedLog) "sync.log_copied" else "sync.log_copy"),
                        color = MaterialTheme.colorScheme.primary,
                        horizontalPadding = 0.dp,
                        onClick = {
                            val text = state.logs.joinToString("\n") { entry ->
                                val tag = if (entry.isError) "ERR" else "OK"
                                "[${entry.time}] [$tag] ${entry.message}"
                            }
                            logClipboard.setText(AnnotatedString(text))
                            copiedLog = true
                        }
                    )
                }
            }
            if (showLog) {
                val logShape = iknaTileShape()
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, logShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, logShape)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        state.logs.asReversed().forEach { entry ->
                            Text(
                                text = "${entry.time} · ${entry.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (entry.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        if (state.paired || state.invitation != null || state.blockedAfterReset) {
            IknaTextButton(label = S.t("sync.revoke"), onClick = onRevoke)
        }
    }
}
