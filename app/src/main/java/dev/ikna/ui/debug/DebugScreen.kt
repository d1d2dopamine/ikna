package dev.ikna.ui.debug

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
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
        TextButton(onClick = onBack) { Text("← Назад") }
        TextButton(onClick = {
            scope.launch {
                note = runCatching { "Файл: Документы/Ikna/" + container.jsonExporter.export() }
                    .getOrElse { "Не удалось сохранить файл" }
            }
        }) { Text("Выгрузить журнал ответов") }
        TextButton(onClick = {
            scope.launch {
                container.components.rebuildFromReviews()
                note = "Слой слов пересчитан"
            }
        }) { Text("Пересчитать слой слов") }
        TextButton(onClick = {
            scope.launch {
                container.learningRepository.invalidatePlan()
                container.learningRepository.ensureDailyPlan()
                log = container.learningRepository.governorLog(40)
                note = "План на сегодня пересобран"
            }
        }) { Text("Пересобрать план дня") }

        note?.let {
            Text(text = it, color = IknaMuted, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Решения регулятора по дням",
            style = MaterialTheme.typography.labelSmall,
            color = IknaMuted
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn {
            items(log) { e ->
                Text(
                    text = e.day + "  новых=" + e.allowedNew + "  " + e.reason +
                        "  к повтору=" + e.dueToday +
                        "  прогноз3д=" + String.format(Locale.US, "%.1f", e.forecastAvg3d) +
                        "  долг=" + e.backlog +
                        "  точность=" + String.format(Locale.US, "%.2f", e.accuracyRecent) +
                        "  запас=" + String.format(Locale.US, "%.1f", e.headroom),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
