package dev.ikna.ui.debug

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ikna.AppContainer
import dev.ikna.data.db.GovernorLogEntity
import dev.ikna.ui.theme.IknaMuted
import kotlinx.coroutines.launch

/**
 * For the author, not the user. The governor log is the only way to answer
 * "why did it not give me anything new today", and without that question being
 * answerable the valve is undebuggable.
 */
@Composable
fun DebugScreen(container: AppContainer, onBack: () -> Unit) {
    var log by remember { mutableStateOf(listOf<GovernorLogEntity>()) }
    var exported by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { log = container.learningRepository.governorLog(40) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            TextButton(onClick = onBack) { Text("back") }
            TextButton(onClick = {
                scope.launch { exported = container.jsonExporter.export() }
            }) { Text("export reviews now") }
            TextButton(onClick = {
                scope.launch { container.components.rebuildFromReviews() }
            }) { Text("rebuild component layer") }

            exported?.let {
                Text("wrote Documents/Ikna/" + it, color = IknaMuted)
            }

            Spacer(Modifier.height(16.dp))
            Text("governor log", style = MaterialTheme.typography.labelSmall, color = IknaMuted)

            LazyColumn {
                items(log) { e ->
                    Text(
                        text = e.day + "  new=" + e.allowedNew + "  " + e.reason +
                            "  due=" + e.dueToday + "  f3d=" + String.format("%.1f", e.forecastAvg3d) +
                            "  backlog=" + e.backlog + "  acc=" + String.format("%.2f", e.accuracyRecent) +
                            "  headroom=" + String.format("%.1f", e.headroom),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
