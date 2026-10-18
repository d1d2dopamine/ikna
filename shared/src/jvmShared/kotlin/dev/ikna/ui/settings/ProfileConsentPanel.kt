package dev.ikna.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ikna.sync.ProfileConsentProtocol
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.IknaWideButton

/** No card text or secret invitations in the preview; identity conflicts never silently resolve. */
@Composable
fun ProfileConsentPanel(
    state: ProfileConsentProtocol.State,
    onProposeMerge: (String) -> Unit,
    onProposeAdoption: (String, Boolean) -> Unit,
    onApprove: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val peer = state.peerId ?: return
    if (state.complete) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(S.t("sync.profile.title"), style = MaterialTheme.typography.titleSmall)
        Text(S.t("sync.profile.scope"), style = MaterialTheme.typography.bodySmall)
        state.preview?.let { preview ->
            Text(S.t("sync.profile.counts")
                .replace("{decks}", preview.addedPacks.toString())
                .replace("{targets}", preview.addedTargets.toString())
                .replace("{events}", preview.addedReviews.toString()),
                style = MaterialTheme.typography.bodyMedium)
            if (preview.conflicts.isNotEmpty()) {
                Text(S.t("sync.profile.conflicts").replace("{count}", preview.conflicts.size.toString()),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (state.mode != null && state.mode != ProfileConsentProtocol.Mode.MERGE) {
                val localSource = (state.mode == ProfileConsentProtocol.Mode.ADOPT_THIS && state.proposerFirst) ||
                    (state.mode == ProfileConsentProtocol.Mode.ADOPT_OTHER && !state.proposerFirst)
                Text(S.t(if (localSource) "sync.profile.this_source" else "sync.profile.other_source"), style = MaterialTheme.typography.bodyMedium)
                Text(S.t("sync.profile.adoption_counts").replace("{selected}", state.selectedReviews.toString())
                    .replace("{archived}", state.archivedLocalReviews.toString()), style = MaterialTheme.typography.bodySmall)
                Text(S.t("sync.profile.adoption_scope"), style = MaterialTheme.typography.bodySmall)
            }
            if (state.operationId == null) {
                IknaWideButton(S.t("sync.profile.propose_merge"),
                    enabled = preview.canMerge, onClick = { onProposeMerge(peer) })
                IknaWideButton(S.t("sync.profile.use_this"), enabled = preview.canMerge,
                    onClick = { onProposeAdoption(peer, true) })
                IknaWideButton(S.t("sync.profile.use_other"), enabled = preview.canMerge,
                    onClick = { onProposeAdoption(peer, false) })
            } else {
                Text(S.t(if (state.localApproved) "sync.profile.local_approved" else "sync.profile.confirm"),
                    style = MaterialTheme.typography.bodySmall)
                if (!state.localApproved) {
                    IknaWideButton(S.t("sync.profile.approve"), enabled = preview.canMerge,
                        onClick = { state.operationId?.let(onApprove) })
                }
                if (state.localApproved && !state.remoteApproved) {
                    Text(S.t("sync.profile.wait_remote"), style = MaterialTheme.typography.bodySmall)
                }
                if (state.adoptionPending) {
                    Text(S.t("sync.profile.adoption_pending"), style = MaterialTheme.typography.bodySmall)
                }
                if (state.applied) {
                    Text(S.t("sync.profile.applied_wait"), style = MaterialTheme.typography.bodySmall)
                }
                if (!state.applied && !state.adoptionPending) IknaTextButton(S.t("sync.profile.cancel"), onClick = onCancel)
            }
        } ?: Text(S.t("sync.profile.wait_snapshot"), style = MaterialTheme.typography.bodySmall)
    }
}
