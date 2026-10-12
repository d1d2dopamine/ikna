package dev.ikna.ui.stats

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.ikna.data.repo.StudyStats
import dev.ikna.data.repo.StudyTarget
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Three small factual counts, with context and concrete cards available on request. */
@Composable
fun IknaStudyStatsContent(snapshot: StudyStats) {
    var days by remember { mutableStateOf(7) }
    var showExplanation by remember { mutableStateOf(false) }
    val period = if (days == 7) snapshot.week else snapshot.month
    Column(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            IknaChip(label = S.t("study.001"), selected = days == 7, onClick = { days = 7 })
            IknaChip(label = S.t("study.002"), selected = days == 30, onClick = { days = 30 })
        }
        Spacer(Modifier.height(Space.lg))
        if (period.answers == 0) {
            Text(S.t("study.017"), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(Space.md))
        }
        StudyMetric(S.t("study.003"), period.targets)
        StudyMetric(S.t("study.004"), period.firstRecordedTargets)
        StudyMetric(S.t("study.005"), period.repeatAnswers)
        Spacer(Modifier.height(Space.sm))
        Text(studyText("study.006", "count" to period.answers),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IknaTextButton(S.t(if (showExplanation) "study.015" else "study.008"),
            onClick = { showExplanation = !showExplanation })
        if (showExplanation) {
            val zone = ZoneId.of(snapshot.zone)
            val formatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.forLanguageTag(S.lang))
            Text(studyText("study.021",
                "from" to formatter.format(Instant.ofEpochMilli(period.from).atZone(zone)),
                "to" to formatter.format(Instant.ofEpochMilli(period.until).atZone(zone)),
                "hour" to snapshot.dayStartHour.toString().padStart(2, '0'), "zone" to snapshot.zone),
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(Space.sm))
            Text(S.t("study.009"), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(Space.sm))
            Text(studyText("study.007", "recalled" to period.recalledRepeatAnswers, "total" to period.repeatAnswers),
                style = MaterialTheme.typography.bodySmall)
            if (period.unclassifiedAnswers > 0) {
                Spacer(Modifier.height(Space.sm))
                Text(studyText("study.022", "count" to period.unclassifiedAnswers), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(Space.sm))
            Text(S.t("study.020"), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(Space.lg)); IknaRule(); Spacer(Modifier.height(Space.lg))
        key(days) {
            StudyTargetGroup("study.010", "study.011", period.recovered)
            Spacer(Modifier.height(Space.lg)); IknaRule(); Spacer(Modifier.height(Space.lg))
            StudyTargetGroup("study.012", "study.013", period.attention)
        }
    }
}

@Composable
private fun StudyMetric(label: String, count: Int) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(Space.md))
        Text(count.toString(), style = iknaNumberStyle(MaterialTheme.typography.headlineMedium, strong = true))
    }
}

@Composable
private fun StudyTargetGroup(title: String, explanation: String, targets: List<StudyTarget>) {
    var expanded by remember { mutableStateOf(false) }
    StudyMetric(S.t(title), targets.size)
    IknaTextButton(S.t(if (expanded) "study.015" else "study.014"), onClick = { expanded = !expanded })
    if (!expanded) return
    Text(S.t(explanation), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (targets.isEmpty()) {
        Spacer(Modifier.height(Space.sm))
        Text(S.t("study.016"), style = MaterialTheme.typography.bodyMedium)
    }
    targets.take(5).forEach { target ->
        Spacer(Modifier.height(Space.md))
        Text(target.text.ifBlank { S.t("study.018") }, style = MaterialTheme.typography.bodyLarge)
        if (target.language.isNotBlank()) Text(target.language.uppercase(Locale.ROOT), style = MaterialTheme.typography.labelSmall)
        if (target.translation.isNotBlank()) Text(target.translation, style = MaterialTheme.typography.bodyMedium)
        Text(studyText("study.023", "misses" to target.misses, "later" to target.laterRecalls),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (targets.size > 5) {
        Spacer(Modifier.height(Space.sm))
        Text(studyText("study.019", "shown" to 5, "total" to targets.size), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun IknaStatsLoadFailure(onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(S.t("study.024"), style = MaterialTheme.typography.bodyMedium)
        IknaTextButton(S.t("load.002"), onClick = onRetry)
    }
}

internal fun studyText(key: String, vararg values: Pair<String, Any>): String =
    values.fold(S.t(key)) { result, (name, value) -> result.replace("{$name}", value.toString()) }
