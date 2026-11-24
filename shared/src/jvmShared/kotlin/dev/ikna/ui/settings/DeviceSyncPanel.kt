package dev.ikna.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.ikna.sync.IknaBrandedQrCard
import dev.ikna.sync.PairedLanTransport
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.IknaTextField
import dev.ikna.ui.theme.IknaWideButton

/** Transport-independent presentation states; never display raw transport exceptions. */
enum class DeviceSyncStatus { OFF, UNPAIRED, WAITING, CONNECTING, SYNCING, UP_TO_DATE, ERROR }
enum class DeviceSyncError { NETWORK, INVALID_INVITATION, EXPIRED_INVITATION, UNTRUSTED_DEVICE, MERGE_FAILED, UNAVAILABLE, HISTORY_MISMATCH, CONTENT_MISSING, ADOPTION, PROFILE_TOO_LARGE, INCOMPATIBLE_PROTOCOL, JOURNAL_ITEM_TOO_LARGE, JOURNAL_RECONCILIATION }

data class DeviceSyncPeerUiState(
    val id: String, val fingerprint: String, val address: String,
    val lastSuccessText: String? = null, val error: String? = null,
) {
    override fun toString(): String = "DeviceSyncPeerUiState(device=<redacted>)"
}

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
    val busy: Boolean = false
) {
    // Invitations contain pairing credentials. Do not accidentally include them in logs.
    override fun toString(): String = "DeviceSyncUiState(enabled=$enabled, paired=$paired, status=$status, error=$error, blockedAfterReset=$blockedAfterReset, busy=$busy)"
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
    scannedCode: String? = null,
    onScannedCodeConsumed: () -> Unit = {},
    onScanQr: (() -> Unit)? = null
) {
    var revokeId by remember(state.peers.map { it.id }) { mutableStateOf<String?>(null) }
    var pasted by remember(state.enabled, state.paired, state.blockedAfterReset) { mutableStateOf("") }
    var showManualInput by remember(state.enabled, state.paired, state.blockedAfterReset) {
        mutableStateOf(onScanQr == null)
    }
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
        state.lastSuccessText?.let {
            Text(S.t("sync.last_success").replace("{time}", it), style = MaterialTheme.typography.bodySmall)
        }
        state.error?.let {
            Text(
                text = S.t(when (it) {
                    DeviceSyncError.NETWORK -> "sync.error.network"
                    DeviceSyncError.INVALID_INVITATION -> "sync.error.invalid"
                    DeviceSyncError.EXPIRED_INVITATION -> "sync.error.expired"
                    DeviceSyncError.UNTRUSTED_DEVICE -> "sync.error.trust"
                    DeviceSyncError.MERGE_FAILED -> "sync.error.merge"
                    DeviceSyncError.UNAVAILABLE -> "sync.error.unavailable"
                    DeviceSyncError.HISTORY_MISMATCH -> "sync.error.history_mismatch"
                    DeviceSyncError.CONTENT_MISSING -> "sync.error.content_missing"
                    DeviceSyncError.ADOPTION -> "sync.error.adoption"
                    DeviceSyncError.PROFILE_TOO_LARGE -> "sync.error.profile_too_large"
                    DeviceSyncError.INCOMPATIBLE_PROTOCOL -> "sync.error.protocol"
                    DeviceSyncError.JOURNAL_ITEM_TOO_LARGE -> "sync.error.journal_item_too_large"
                    DeviceSyncError.JOURNAL_RECONCILIATION -> "sync.error.journal_reconciliation"
                }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
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
                Text(S.t("sync.address"), style = MaterialTheme.typography.bodySmall)
                state.localAddresses.forEach { candidate ->
                    IknaSettingsToggleRow(
                        title = candidate,
                        subtitle = null,
                        checked = address == candidate,
                        enabled = !state.busy,
                        onCheckedChange = { if (it) address = candidate }
                    )
                }
            }
            IknaWideButton(
                label = S.t("sync.show_qr_btn"),
                enabled = canPair && address.isNotBlank(),
                onClick = { if (canPair && address.isNotBlank()) onCreateInvitation(address) }
            )
            state.invitation?.takeIf { it.isNotBlank() }?.let { invitation ->
                val compact = remember(invitation) {
                    runCatching { PairedLanTransport.encodeCompactInvitation(invitation) }.getOrDefault(invitation)
                }
                @Suppress("DEPRECATION")
                val clipboard = LocalClipboardManager.current
                var copied by remember(compact) { mutableStateOf(false) }
                var showRawCode by remember(compact) { mutableStateOf(false) }
                IknaBrandedQrCard(
                    content = compact,
                    subtitle = S.t("sync.qr_hint")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IknaTextButton(
                        label = S.t(if (copied) "sync.code_copied" else "sync.copy_code"),
                        onClick = {
                            clipboard.setText(AnnotatedString(compact))
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
        if (state.peers.isNotEmpty()) {
            Text(S.t("sync.devices"), style = MaterialTheme.typography.titleSmall)
            Text(S.t("sync.devices_scope"), style = MaterialTheme.typography.bodySmall)
            state.peers.forEach { peer ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SelectionContainer {
                        Text(S.t("sync.device_fingerprint").replace("{fingerprint}", peer.fingerprint),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text(S.t("sync.device_address").replace("{address}", peer.address), style = MaterialTheme.typography.bodySmall)
                    Text(peer.lastSuccessText?.let { S.t("sync.device_success").replace("{time}", it) }
                        ?: S.t("sync.device_no_exchange"), style = MaterialTheme.typography.bodySmall)
                    if (peer.error != null) Text(S.t("sync.device_error"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
        if (state.paired || state.invitation != null || state.blockedAfterReset) {
            IknaTextButton(label = S.t("sync.revoke"), onClick = onRevoke)
        }
    }
}
