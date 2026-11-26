package dev.ikna.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ikna.sync.DeviceSyncController
import dev.ikna.sync.PairedLanTransport
import dev.ikna.sync.ProfileConsentProtocol
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.IknaWideButton
import dev.ikna.ui.theme.iknaTileShape

/**
 * Inline device-sync choice card shown inside Settings -> Data before profile changes:
 * - On the inviting device (the one that showed the QR/code): presents one-click choices
 *   to merge both devices, keep this device's data, or take the other device's data.
 * - On the invited/joining device: shows live progress and waits for the inviter's choice,
 *   then applies it automatically (with fallback manual approval only for legacy peers).
 * Rendered inline rather than as a modal Dialog so it never blocks the rest of the app.
 */
@Composable
fun ProfileConsentPanel(
    state: ProfileConsentProtocol.State,
    onChooseMerge: (String) -> Unit,
    onChooseAdoption: (String, Boolean) -> Unit,
    onApprove: (String) -> Unit,
    onCancel: () -> Unit,
    peerRole: PairedLanTransport.PeerRole = PairedLanTransport.PeerRole.UNKNOWN,
    stage: DeviceSyncController.SyncStage = DeviceSyncController.SyncStage.IDLE,
    thisDeviceName: String = "",
    peerDeviceName: String = "",
    onDismiss: () -> Unit = onCancel,
) {
    val peer = state.peerId ?: return
    if (state.complete) return
    val preview = state.preview
    // Avoid showing an empty card while discovery/error states are already shown in DeviceSyncPanel.
    if (preview == null && state.operationId == null && !state.adoptionPending && !state.applied) return
    val thisLabel = thisDeviceName.ifBlank { S.t("sync.this_device") }
    val otherLabel = peerDeviceName.ifBlank { S.t("sync.other_device") }
    val isJoiner = peerRole == PairedLanTransport.PeerRole.JOINER
    val shape = iknaTileShape()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = S.t(if (isJoiner) "sync.profile.joiner_title" else "sync.profile.dialog_title")
                .replace("{device}", otherLabel),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (preview == null) {
            IknaSyncProgressStrip(
                label = S.t("sync.stage.exchanging_profiles"),
                fraction = 0.62f,
                detail = S.t("sync.stage.keep_open"),
            )
            if (!state.applied && !state.adoptionPending) {
                IknaTextButton(label = S.t("sync.profile.cancel"), onClick = onCancel)
            }
        } else if (isJoiner) {
            if (state.operationId == null) {
                IknaSyncProgressStrip(
                    label = S.t("sync.stage.waiting_for_inviter").replace("{device}", otherLabel),
                    fraction = 0.72f,
                    detail = S.t("sync.profile.joiner_waiting").replace("{device}", otherLabel),
                )
                Text(
                    text = S.t("sync.profile.device_summary")
                        .replace("{device}", thisLabel)
                        .replace("{decks}", preview.localPacks.toString())
                        .replace("{targets}", preview.localTargets.toString())
                        .replace("{events}", preview.localReviews.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = S.t("sync.profile.device_summary")
                        .replace("{device}", otherLabel)
                        .replace("{decks}", preview.remotePacks.toString())
                        .replace("{targets}", preview.remoteTargets.toString())
                        .replace("{events}", preview.remoteReviews.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IknaTextButton(label = S.t("sync.profile.later"), onClick = onDismiss)
            } else if (!state.localApproved && !state.remoteApproved) {
                // Legacy peer sent an unapproved proposal: keep manual confirm available.
                Text(
                    text = S.t("sync.profile.confirm"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val approveEnabled = if (state.mode == ProfileConsentProtocol.Mode.MERGE) preview.canMerge else preview.canAdopt
                IknaWideButton(
                    label = S.t("sync.profile.approve"),
                    enabled = approveEnabled,
                    onClick = { state.operationId?.let(onApprove) }
                )
                IknaTextButton(label = S.t("sync.profile.cancel"), onClick = onCancel)
            } else {
                val progressFraction = when {
                    state.applied -> 0.93f
                    state.adoptionPending -> 0.88f
                    else -> 0.82f
                }
                val stageText = when {
                    state.adoptionPending -> S.t("sync.profile.adoption_pending")
                    state.applied -> S.t("sync.stage.confirming_profile")
                    else -> S.t("sync.stage.applying_profile")
                }
                IknaSyncProgressStrip(
                    label = stageText,
                    fraction = progressFraction,
                    detail = S.t("sync.profile.joiner_applying").replace("{device}", otherLabel),
                )
                if (!state.applied && !state.adoptionPending) {
                    IknaTextButton(label = S.t("sync.profile.cancel"), onClick = onCancel)
                }
            }
        } else {
            // Inviting device (or legacy UNKNOWN role): owns the single-click choice.
            Text(
                text = S.t(if (peerRole == PairedLanTransport.PeerRole.INVITER) "sync.profile.inviter_scope" else "sync.profile.scope"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background, shape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = S.t("sync.profile.device_summary")
                        .replace("{device}", thisLabel)
                        .replace("{decks}", preview.localPacks.toString())
                        .replace("{targets}", preview.localTargets.toString())
                        .replace("{events}", preview.localReviews.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = S.t("sync.profile.device_summary")
                        .replace("{device}", otherLabel)
                        .replace("{decks}", preview.remotePacks.toString())
                        .replace("{targets}", preview.remoteTargets.toString())
                        .replace("{events}", preview.remoteReviews.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                val hasIncoming = preview.addedPacks > 0 || preview.addedTargets > 0 || preview.addedReviews > 0
                val hasOutgoing = preview.outgoingPacks > 0 || preview.outgoingTargets > 0 || preview.outgoingReviews > 0
                if (hasIncoming) {
                    Text(
                        text = S.t("sync.profile.incoming_summary")
                            .replace("{device}", thisLabel)
                            .replace("{decks}", preview.addedPacks.toString())
                            .replace("{targets}", preview.addedTargets.toString())
                            .replace("{events}", preview.addedReviews.toString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (hasOutgoing) {
                    Text(
                        text = S.t("sync.profile.outgoing_summary")
                            .replace("{device}", otherLabel)
                            .replace("{decks}", preview.outgoingPacks.toString())
                            .replace("{targets}", preview.outgoingTargets.toString())
                            .replace("{events}", preview.outgoingReviews.toString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!hasIncoming && !hasOutgoing) {
                    Text(
                        text = S.t("sync.profile.already_equal"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (preview.conflicts.isNotEmpty()) {
                Text(
                    text = S.t("sync.profile.conflicts").replace("{count}", preview.conflicts.size.toString()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                preview.conflicts.take(3).forEach { conflict ->
                    Text(
                        text = conflict,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (state.mode != null && state.mode != ProfileConsentProtocol.Mode.MERGE) {
                val localSource = (state.mode == ProfileConsentProtocol.Mode.ADOPT_THIS && state.proposerFirst) ||
                    (state.mode == ProfileConsentProtocol.Mode.ADOPT_OTHER && !state.proposerFirst)
                Text(
                    text = S.t(if (localSource) "sync.profile.this_source" else "sync.profile.other_source"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = S.t("sync.profile.adoption_counts")
                        .replace("{selected}", state.selectedReviews.toString())
                        .replace("{archived}", state.archivedLocalReviews.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = S.t("sync.profile.adoption_scope"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.operationId == null) {
                IknaWideButton(
                    label = S.t("sync.profile.choose_merge"),
                    enabled = preview.canMerge,
                    onClick = { onChooseMerge(peer) }
                )
                IknaWideButton(
                    label = S.t("sync.profile.use_this_named").replace("{device}", thisLabel),
                    enabled = preview.canAdopt,
                    onClick = { onChooseAdoption(peer, true) }
                )
                IknaWideButton(
                    label = S.t("sync.profile.use_other_named").replace("{device}", otherLabel),
                    enabled = preview.canAdopt,
                    onClick = { onChooseAdoption(peer, false) }
                )
                IknaTextButton(label = S.t("sync.profile.later"), onClick = onDismiss)
            } else if (!state.localApproved) {
                Text(
                    text = S.t("sync.profile.confirm"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val approveEnabled = if (state.mode == ProfileConsentProtocol.Mode.MERGE) preview.canMerge else preview.canAdopt
                IknaWideButton(
                    label = S.t("sync.profile.approve"),
                    enabled = approveEnabled,
                    onClick = { state.operationId?.let(onApprove) }
                )
                IknaTextButton(label = S.t("sync.profile.cancel"), onClick = onCancel)
            } else {
                val progressFraction = when {
                    state.applied -> 0.93f
                    state.adoptionPending -> 0.88f
                    stage == DeviceSyncController.SyncStage.CONFIRMING_PROFILE -> 0.93f
                    else -> 0.84f
                }
                val progressLabel = when {
                    state.adoptionPending -> S.t("sync.profile.adoption_pending")
                    state.applied -> S.t("sync.stage.confirming_profile")
                    else -> S.t("sync.stage.applying_profile")
                }
                val detail = if (peerRole == PairedLanTransport.PeerRole.INVITER) {
                    S.t("sync.profile.inviter_applying").replace("{device}", otherLabel)
                } else if (!state.remoteApproved) {
                    S.t("sync.profile.wait_remote")
                } else {
                    S.t("sync.stage.keep_open")
                }
                IknaSyncProgressStrip(
                    label = progressLabel,
                    fraction = progressFraction,
                    detail = detail,
                )
                if (!state.applied && !state.adoptionPending) {
                    IknaTextButton(label = S.t("sync.profile.cancel"), onClick = onCancel)
                }
            }
        }
    }
}
