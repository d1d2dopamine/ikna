package dev.ikna.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.ikna.data.repo.StatsDigest
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaLatticePlaceholder
import dev.ikna.ui.theme.IknaPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Sixteen percent of the phone screen was a stub here; this is the whole thing. */
private const val HOUR_CONFIDENT = 12

/**
 * The statistics screen, ported whole from the phone.
 *
 * The window used to show three numbers and stop. The phone shows the month as
 * a grid of days, the day against its norm, retention with the sample size that
 * earns it, minutes today and this week, the hours when recall is best, and the
 * cards that refuse to stick -- each with the sentence that says what the number
 * means and when not to trust it. Numbers without those sentences are decoration,
 * so they came across too.
 */
@Composable
fun StatsPane(
    container: DesktopContainer,
    palette: IknaPalette,
    onBack: () -> Unit = {}
) {
    var digest by remember { mutableStateOf<StatsDigest?>(null) }
    var days by remember { mutableStateOf<List<Boolean>>(emptyList()) }
    var forecast by remember { mutableStateOf<List<Int>>(emptyList()) }
    var answered by remember { mutableStateOf(0) }
    var target by remember { mutableStateOf(0) }
    var measured by remember { mutableStateOf(false) }
    var words by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                val learning = container.learningRepository
                digest = learning.statsDigest()
                days = learning.activityMap()
                forecast = learning.forecast(days = 14)
                answered = learning.answeredToday()
                target = learning.currentDailyTarget()
                measured = learning.normIsMeasured()
                words = container.componentRepository.knownWordCount()
            }.onFailure { error -> logLine("stats failed: " + error) }
        }
        loading = false
    }

    DesktopScrollablePane(S.t("stats.001"), onBack, titleStyle = MaterialTheme.typography.displaySmall) {
        if (loading && digest == null) IknaLatticePlaceholder()
        else dev.ikna.ui.stats.IknaStatsContent(days, target, measured, words, answered,
            forecast, digest ?: StatsDigest())
    }
}
