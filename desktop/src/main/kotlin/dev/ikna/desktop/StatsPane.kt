package dev.ikna.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.ikna.data.repo.StudyStats
import kotlinx.coroutines.CancellationException
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaLatticePlaceholder
import dev.ikna.ui.theme.IknaPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shared factual journal summary; a failed read is not an empty journal. */
@Composable
fun StatsPane(
    container: DesktopContainer,
    palette: IknaPalette,
    onBack: () -> Unit = {}
) {
    var snapshot by remember { mutableStateOf<StudyStats?>(null) }
    var failed by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) {
        failed = false
        snapshot = null
        try {
            snapshot = container.studyStatsRepository.load()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            logLine("stats failed: " + error)
            failed = true
        }
    }
    DesktopScrollablePane(S.t("stats.001"), onBack) {
        when {
            failed -> dev.ikna.ui.stats.IknaStatsLoadFailure { reload++ }
            snapshot == null -> IknaLatticePlaceholder()
            else -> dev.ikna.ui.stats.IknaStudyStatsContent(requireNotNull(snapshot))
        }
    }
}
