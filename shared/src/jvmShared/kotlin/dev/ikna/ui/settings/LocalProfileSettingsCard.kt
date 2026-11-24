package dev.ikna.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import dev.ikna.sync.PairedLanTransport
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaTextField
import dev.ikna.ui.theme.IknaWideButton
import dev.ikna.ui.theme.iknaTileShape

/**
 * Shared local-only profile settings card for Android and Desktop Settings.
 * Lets the user set their local profile / device label (used on PNG stats cards and LAN sync).
 */
@Composable
fun LocalProfileSettingsCard(
    profileName: String,
    onSaveName: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var draft by remember { mutableStateOf(profileName) }
    var savedFlash by remember { mutableStateOf(false) }
    val defaultDevice = remember { PairedLanTransport.defaultDeviceDisplayName() }
    LaunchedEffect(profileName) {
        draft = profileName
    }
    val shape = iknaTileShape()
    val dirty = draft.trim() != profileName.trim()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = S.t("prof.title"),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = S.t("prof.settings_hint").replace("{device}", defaultDevice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IknaTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    savedFlash = false
                },
                placeholder = S.t("prof.name_placeholder"),
                onSearch = {
                    if (dirty) {
                        onSaveName(draft.trim())
                        savedFlash = true
                    }
                },
                modifier = Modifier.weight(1f),
                maxLength = 48
            )
            IknaWideButton(
                label = S.t("prof.save_name"),
                enabled = dirty,
                onClick = {
                    onSaveName(draft.trim())
                    savedFlash = true
                }
            )
        }
    }
}
