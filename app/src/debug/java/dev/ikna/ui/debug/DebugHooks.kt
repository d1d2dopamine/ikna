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
 * The debug build's half of the technical screen: all of it.
 *
 * This file has a twin in `src/release/java` with the same object and the same
 * two members, which draws nothing and reports `available = false`. Only one of
 * the two is ever compiled, so the technical screen is absent from the app
 * people install rather than merely unreachable in it.
 *
 * That distinction matters here. R8 is off in both build types of this project
 * (`isMinifyEnabled = false`), so nothing is dropped for being unreachable: a
 * `BuildConfig.DEBUG` check around a button would have shipped this screen, its
 * fifteen strings and its three languages inside every release.
 *
 * What is lost by hiding it is close to nothing, which is the other reason this
 * was safe to do. Exporting the answer log already lives in Settings under
 * «Д А Н Н Ы Е», and it exports the settings alongside it; rebuilding the word
 * layer is in Advanced. Only the governor log and the plan rebuild were unique
 * to this screen, and the one question the log answers for a user — why nothing
 * new arrived today — is already answered in one sentence on the session screen.
 */
object DebugHooks {
    const val available: Boolean = true

    @Composable
    fun Screen(container: AppContainer, onBack: () -> Unit) = DebugScreen(container, onBack)
}

/**
 * The governor log is the only way to answer "why did it not give me anything new
 * today" with numbers instead of a sentence, and without that question being
 * answerable the safety valve is undebuggable.
 */
@Composable
private fun DebugScreen(container: AppContainer, onBack: () -> Unit) {
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
