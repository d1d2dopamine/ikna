package dev.ikna.ui.settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ikna.data.repo.LocalOptimizer
import dev.ikna.domain.fsrs.Verdict
import dev.ikna.domain.optimizer.OptimizerIssue
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaToggle
import dev.ikna.ui.theme.IknaWideButton
import dev.ikna.ui.theme.IknaTextButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One settings surface for Android and desktop. No computation belongs to composition. */
@Composable fun LocalOptimizerPanel(controller: LocalOptimizer, modifier: Modifier = Modifier) {
    val state by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.stored.latest?.attemptedAt) {
        while (true) { now = System.currentTimeMillis(); delay(60_000) }
    }
    val latest = state.stored.latest; val applied = state.stored.applied; val candidate = state.stored.candidate
    val next = latest?.nextFitAt ?: 0L
    val ink = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    fun date(ts: Long) = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(ts))
    fun loss(value: Double?) = value?.let { String.format(Locale.ROOT, "%.5f", it) } ?: "—"
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(S.t("optimizer.001"), style = MaterialTheme.typography.titleMedium, color = ink)
        Text(S.t("optimizer.002"), style = MaterialTheme.typography.bodySmall, color = muted)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(S.t("optimizer.003"), Modifier.weight(1f), color = ink)
            Spacer(Modifier.width(12.dp))
            IknaToggle(checked = state.usingOptimized, enabled = state.ready && applied != null,
                label = S.t("optimizer.003"), onCheckedChange = { on -> scope.launch { controller.setEnabled(on) } })
        }
        Text(S.t(if (state.usingOptimized) "optimizer.022" else "optimizer.021"), color = muted)
        Text(S.t("optimizer.012"), style = MaterialTheme.typography.bodySmall, color = muted)
        val message = when {
            state.running -> S.t("optimizer.011")
            latest == null -> S.t("optimizer.007")
            latest.verdict == Verdict.TOO_FEW_ANSWERS -> S.t("optimizer.008").replace("{count}", latest.scoredAnswers.toString())
            latest.verdict == Verdict.NO_IMPROVEMENT -> S.t("optimizer.009")
            else -> S.t("optimizer.010")
        }
        Text(message, color = ink)
        if (latest != null) {
            Text(S.t("optimizer.014") + ": " + date(latest.attemptedAt) + " · " +
                S.t("optimizer.013") + ": " + latest.scoredAnswers, style = MaterialTheme.typography.bodySmall, color = muted)
            if (latest.heldOutLossDefaults != null) Text(S.t("optimizer.015") + ": " +
                loss(latest.heldOutLossDefaults) + " / " + loss(latest.heldOutLossOptimised),
                style = MaterialTheme.typography.bodySmall, color = muted)
        }
        if (applied != null) Text(S.t("optimizer.023").replace("{date}", date(applied.attemptedAt)), color = muted)
        state.issue?.let { issue ->
            val key = when (issue) {
                OptimizerIssue.STORAGE -> "optimizer.017"
                OptimizerIssue.FAILED -> "optimizer.018"
                OptimizerIssue.STALE_HISTORY -> "optimizer.019"
                OptimizerIssue.CANCELLED -> "optimizer.020"
                OptimizerIssue.COOLDOWN -> "optimizer.024"
            }
            Text(S.t(key), color = muted)
        }
        if (next > now) Text(S.t("optimizer.016").replace("{date}", date(next)), color = muted)
        if (state.running) IknaTextButton(S.t("optimizer.005"), onClick = { controller.cancel() })
        else IknaWideButton(S.t("optimizer.004"), onClick = { controller.start() }, enabled = state.ready && now >= next)
        if (candidate != null && candidate != applied) {
            Text(S.t("optimizer.025").replace("{date}", date(candidate.attemptedAt)), color = muted)
            IknaWideButton(S.t("optimizer.006"), onClick = { scope.launch { controller.applyCandidate() } }, enabled = state.ready && !state.running)
        }
    }
}
