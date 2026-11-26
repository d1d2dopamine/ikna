package dev.ikna.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ikna.AppContainer
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.update.UpdateCheck
import dev.ikna.data.update.UpdateRelease
import dev.ikna.data.update.megabytes
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.IknaWideButton
import dev.ikna.ui.update.UpdateDownloadPanel
import dev.ikna.ui.update.UpdateDownloadState
import dev.ikna.ui.update.UpdatePhase
import dev.ikna.ui.update.has64Bit
import dev.ikna.ui.update.installedVersion
import dev.ikna.ui.update.openInBrowser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun SettingsUpdateSectionContent(
    context: Context,
    container: AppContainer,
    settings: IknaSettings,
    scope: CoroutineScope,
    updateBusy: Boolean,
    updateFound: UpdateRelease?,
    updateNote: String?,
    updateDownload: UpdateDownloadState,
    onUpdateBusyChange: (Boolean) -> Unit,
    onUpdateFoundChange: (UpdateRelease?) -> Unit,
    onUpdateNoteChange: (String?) -> Unit
) {
    Text(
        text = S.t("upd.011") + installedVersion(context),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (settings.updateSkipped.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = S.t("upd.015") + settings.updateSkipped,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(12.dp))
    IknaSettingsToggleRow(
        title = S.t("upd.008"),
        subtitle = S.t("upd.009"),
        checked = settings.updateCheck,
        onCheckedChange = { on ->
            scope.launch { container.settings.setUpdateCheck(on) }
        }
    )
    Spacer(Modifier.height(12.dp))
    IknaWideButton(
        label = if (updateBusy) S.t("upd.014") else S.t("upd.010"),
        height = 52.dp,
        enabled = !updateBusy,
        onClick = {
            scope.launch {
                onUpdateBusyChange(true)
                onUpdateNoteChange(null)
                val now = System.currentTimeMillis()
                val release = UpdateCheck(
                    installedVersion(context),
                    has64Bit()
                ).latest()
                container.settings.markUpdateChecked(now)
                onUpdateBusyChange(false)
                onUpdateFoundChange(release)
                onUpdateNoteChange(if (release == null) S.t("upd.012") else null)
            }
        }
    )
    if (updateFound != null) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = S.t("upd.016") + updateFound.version,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = S.t("upd.002") + megabytes(updateFound.sizeBytes) + " " + S.t("upd.003"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        if (updateDownload.phase == UpdatePhase.IDLE) {
            IknaWideButton(
                label = S.t("upd.005"),
                height = 52.dp,
                onClick = { updateDownload.start(updateFound) }
            )
        } else {
            UpdateDownloadPanel(
                state = updateDownload,
                release = updateFound,
                onBrowser = { openInBrowser(context, updateFound.apkUrl) }
            )
        }
    }
    if (updateNote != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = updateNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(8.dp))
    IknaTextButton(
        label = S.t("upd.013"),
        onClick = { openInBrowser(context, UpdateCheck.RELEASES_PAGE) },
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
