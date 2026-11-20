package dev.ikna.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.Text
import dev.ikna.ui.stats.IknaStatsExportAction
import java.io.File
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    var exportFailed by remember { mutableStateOf(false) }
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
        IknaStatsExportAction(enabled = snapshot != null && !exporting) { days, format ->
            val chooser = JFileChooser().apply { selectedFile = File("ikna-statistics.${format.extension}") }
            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                val destination = chooser.selectedFile
                if (!destination.exists() || JOptionPane.showConfirmDialog(
                    chooser, destination.path, S.t("stats.save"), JOptionPane.YES_NO_OPTION
                ) == JOptionPane.YES_OPTION) {
                    exporting = true
                    exportFailed = false
                    scope.launch {
                        try {
                            val content = container.studyStatsRepository.export(days, format)
                            withContext(Dispatchers.IO) { destination.writeText(content, Charsets.UTF_8) }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            exportFailed = true
                        } finally {
                            exporting = false
                        }
                    }
                }
            }
        }
        if (exportFailed) Text(S.t("share.003"))
        when {
            failed -> dev.ikna.ui.stats.IknaStatsLoadFailure { reload++ }
            snapshot == null -> IknaLatticePlaceholder()
            else -> dev.ikna.ui.stats.IknaStudyStatsContent(requireNotNull(snapshot))
        }
    }
}
