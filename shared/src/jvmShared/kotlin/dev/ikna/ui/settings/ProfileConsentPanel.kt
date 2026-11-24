package dev.ikna.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.ikna.sync.ProfileConsentProtocol
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.IknaWideButton
import dev.ikna.ui.theme.iknaTileShape

/**
 * Modal device-sync choice window shown on paired devices before any history changes:
 * lets the user merge both devices, keep this device's data, or adopt the other device's data.
 */
@Composable
fun ProfileConsentPanel(
    state: ProfileConsentProtocol.State,
    onProposeMerge: (String) -> Unit,
    onProposeAdoption: (String, Boolean) -> Unit,
    onApprove: (String) -> Unit,
    onCancel: () -> Unit,
    thisDeviceName: String = "",
    peerDeviceName: String = "",
) {
    val peer = state.peerId ?: return
    if (state.complete) return
    val thisLabel = thisDeviceName.ifBlank { S.t("sync.this_device") }
    val otherLabel = peerDeviceName.ifBlank { S.t("sync.other_device") }
    val shape = iknaTileShape()

    Dialog(onDismissRequest = { if (!state.applied && !state.adoptionPending) onCancel() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .background(MaterialTheme.colorScheme.background, shape)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = S.t("sync.profile.dialog_title").replace("{device}", otherLabel),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = S.t("sync.profile.scope"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.preview?.let { preview ->
                Text(
                    text = S.t("sync.profile.counts")
                        .replace("{decks}", preview.addedPacks.toString())
                        .replace("{targets}", preview.addedTargets.toString())
                        .replace("{events}", preview.addedReviews.toString()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (preview.conflicts.isNotEmpty()) {
                    Text(
                        text = S.t("sync.profile.conflicts").replace("{count}", preview.conflicts.size.toString()),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
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
                        label = S.t("sync.profile.propose_merge"),
                        enabled = preview.canMerge,
                        onClick = { onProposeMerge(peer) }
                    )
                    IknaWideButton(
                        label = S.t("sync.profile.use_this_named").replace("{device}", thisLabel),
                        enabled = preview.canAdopt,
                        onClick = { onProposeAdoption(peer, true) }
                    )
                    IknaWideButton(
                        label = S.t("sync.profile.use_other_named").replace("{device}", otherLabel),
                        enabled = preview.canAdopt,
                        onClick = { onProposeAdoption(peer, false) }
                    )
                } else {
                    Text(
                        text = S.t(if (state.localApproved) "sync.profile.local_approved" else "sync.profile.confirm"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!state.localApproved) {
                        val approveEnabled = if (state.mode == ProfileConsentProtocol.Mode.MERGE) preview.canMerge else preview.canAdopt
                        IknaWideButton(
                            label = S.t("sync.profile.approve"),
                            enabled = approveEnabled,
                            onClick = { state.operationId?.let(onApprove) }
                        )
                    }
                    if (state.localApproved && !state.remoteApproved) {
                        Text(
                            text = S.t("sync.profile.wait_remote"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.adoptionPending) {
                        Text(
                            text = S.t("sync.profile.adoption_pending"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.applied) {
                        Text(
                            text = S.t("sync.profile.applied_wait"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!state.applied && !state.adoptionPending) {
                    IknaTextButton(label = S.t("sync.profile.cancel"), onClick = onCancel)
                }
            } ?: Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = S.t("sync.profile.wait_snapshot"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IknaTextButton(label = S.t("sync.profile.cancel"), onClick = onCancel)
            }
        }
    }
}
