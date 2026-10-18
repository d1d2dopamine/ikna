package dev.ikna.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaWideButton

/** Recovery is an explicit swap with a retained database, never an automatic old-history merge. */
@Composable
fun ProfileRecoveryPanel(onRecover: () -> Unit) {
    var confirmed by remember { mutableStateOf(false) }
    Column {
        Text(S.t("sync.profile.recovery_scope"), style = MaterialTheme.typography.bodySmall)
        IknaSettingsToggleRow(S.t("sync.profile.recovery_confirm"), subtitle = null,
            checked = confirmed, onCheckedChange = { confirmed = it })
        IknaWideButton(S.t("sync.profile.recover"), enabled = confirmed, onClick = {
            if (confirmed) { confirmed = false; onRecover() }
        })
    }
}
