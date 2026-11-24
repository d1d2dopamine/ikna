package dev.ikna.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaWideButton
import dev.ikna.ui.theme.Space

/**
 * Serverless file-based sync controls (.ikna-sync delta journal).
 * Works across any transfer medium (Nearby Share, Syncthing, USB, Saved Messages)
 * even when devices are not on the same Wi-Fi network.
 */
@Composable
fun SyncFilePanel(
    busy: Boolean,
    statusMessage: String?,
    isError: Boolean,
    onExportFile: () -> Unit,
    onMergeFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        Spacer(Modifier.height(Space.sm))
        Text(
            text = S.t("sync.file_title"),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = S.t("sync.file_desc"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IknaWideButton(
                label = S.t("sync.file_export"),
                enabled = !busy,
                height = 46.dp,
                modifier = Modifier.weight(1f),
                onClick = onExportFile
            )
            IknaWideButton(
                label = S.t("sync.file_merge"),
                enabled = !busy,
                height = 46.dp,
                modifier = Modifier.weight(1f),
                onClick = onMergeFile
            )
        }
        if (!statusMessage.isNullOrBlank()) {
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
