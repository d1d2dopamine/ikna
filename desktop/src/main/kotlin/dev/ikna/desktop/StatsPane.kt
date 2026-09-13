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

/**
 * The statistics screen, ported whole from the phone.
 *
 * The desktop uses the same evidence-first renderer as the phone: activity over
 * the last month, accumulated history, retention only after enough reviews, a
 * time-of-day pattern only after enough evidence, troublesome targets and the
 * scheduler forecast. There is deliberately no daily quota on this screen.
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
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                val learning = container.learningRepository
                digest = learning.statsDigest()
                days = learning.activityMap()
                forecast = learning.forecast(days = 14)
            }.onFailure { error -> logLine("stats failed: " + error) }
        }
        loading = false
    }

    DesktopScrollablePane(S.t("stats.001"), onBack, titleStyle = MaterialTheme.typography.displaySmall) {
        if (loading && digest == null) IknaLatticePlaceholder()
        else dev.ikna.ui.stats.IknaStatsContent(days, forecast, digest ?: StatsDigest())
    }
}
