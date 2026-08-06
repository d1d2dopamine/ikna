package dev.ikna.ui.debug

import dev.ikna.ui.text.S

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import dev.ikna.ui.theme.IknaTextButton
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The technical screen.
 *
 * The governor log is the only way to answer "why did it not give me anything new
 * today", and without that question being answerable the safety valve is
 * undebuggable.
 */
@Composable
fun DebugScreen(container: AppContainer, onBack: () -> Unit) {
    var log by remember { mutableStateOf(listOf<GovernorLogEntity>()) }
    var note by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { log = container.learningRepository.governorLog(40) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        IknaTextButton(label = S.t("dbg.001"), onClick = onBack)
        IknaTextButton(
            label = S.t("dbg.002"),
            onClick = {
                scope.launch {
                    note = runCatching { S.t("dbg.003") + container.jsonExporter.export() }
                        .getOrElse { S.t("dbg.004") }
                }
            }
        )
        IknaTextButton(
            label = S.t("dbg.005"),
            onClick = {
                scope.launch {
                    container.components.rebuildFromReviews()
                    note = S.t("dbg.006")
                }
            }
        )
        IknaTextButton(
            label = S.t("dbg.007"),
            onClick = {
                scope.launch {
                    container.learningRepository.invalidatePlan()
                    container.learningRepository.ensureDailyPlan()
                    log = container.learningRepository.governorLog(40)
                    note = S.t("dbg.008")
                }
            }
        )

        note?.let {
            Text(text = it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = S.t("dbg.009"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn {
            items(log) { e ->
                Text(
                    text = e.day + S.t("dbg.010") + e.allowedNew + "  " + e.reason +
                        S.t("dbg.011") + e.dueToday +
                        S.t("dbg.012") + String.format(Locale.US, "%.1f", e.forecastAvg3d) +
                        S.t("dbg.013") + e.backlog +
                        S.t("dbg.014") + String.format(Locale.US, "%.2f", e.accuracyRecent) +
                        S.t("dbg.015") + String.format(Locale.US, "%.1f", e.headroom),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
