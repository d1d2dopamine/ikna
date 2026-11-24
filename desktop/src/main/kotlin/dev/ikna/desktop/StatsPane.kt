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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.repo.StudyStats
import dev.ikna.ui.stats.IknaLocalProfileCard
import dev.ikna.ui.stats.renderProfileStatsCardPng
import dev.ikna.ui.theme.paletteSpec
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
    val settings by container.settings.flow.collectAsState(initial = IknaSettings())
    var exporting by remember { mutableStateOf(false) }
    var exportFailed by remember { mutableStateOf(false) }
    var exportingPng by remember { mutableStateOf(false) }
    var pngStatus by remember { mutableStateOf<String?>(null) }
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
    DesktopScrollablePane(S.t("stats.001"), onBack, icon = dev.ikna.ui.theme.IknaGlyph.BARS) {
        IknaLocalProfileCard(
            profileName = settings.profileName,
            snapshot = snapshot,
            paletteDisplayName = S.t(paletteSpec(settings.paletteId).nameKey),
            exportingPng = exportingPng,
            pngStatus = pngStatus,
            onSaveName = { name -> scope.launch { container.settings.setProfileName(name) } },
            onExportPng = { cardData ->
                val chooser = JFileChooser().apply { selectedFile = File("ikna-profile-stats.png") }
                if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                    val dest = chooser.selectedFile
                    if (!dest.exists() || JOptionPane.showConfirmDialog(
                            chooser, dest.path, S.t("stats.save"), JOptionPane.YES_NO_OPTION
                        ) == JOptionPane.YES_OPTION
                    ) {
                        exportingPng = true
                        pngStatus = null
                        scope.launch {
                            try {
                                val bytes = withContext(Dispatchers.Default) { renderProfileStatsCardPng(cardData) }
                                withContext(Dispatchers.IO) { dest.writeBytes(bytes) }
                                pngStatus = S.t("prof.png_saved")
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                pngStatus = S.t("share.003")
                            } finally {
                                exportingPng = false
                            }
                        }
                    }
                }
            }
        )
        Spacer(Modifier.height(14.dp))
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
