package dev.ikna.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ikna.sync.ProfileRestartGate
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaWideButton

/** Replaces database-driven navigation completely. This is not a dismissible overlay. */
@Composable
fun ProfileRestartScreen(gate: ProfileRestartGate, onClose: () -> Unit) {
    val ready by gate.readyToClose.collectAsState()
    LaunchedEffect(gate) { gate.acknowledgeUiRemoved() }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(S.t("sync.profile.restart_title"), style = MaterialTheme.typography.titleLarge)
        Text(S.t("sync.profile.restart_scope"), style = MaterialTheme.typography.bodyMedium)
        IknaWideButton(S.t("sync.profile.close_app"), enabled = ready, onClick = onClose)
    }
}
