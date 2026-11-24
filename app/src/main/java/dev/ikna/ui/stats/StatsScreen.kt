package dev.ikna.ui.stats

import dev.ikna.ui.text.S
import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import dev.ikna.data.repo.StatsExportFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ikna.AppContainer
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.repo.StudyStats
import dev.ikna.ui.theme.LocalIknaPalette
import dev.ikna.ui.theme.paletteSpec
import kotlinx.coroutines.CancellationException
import dev.ikna.ui.theme.IknaLatticePlaceholder
import dev.ikna.ui.theme.IknaBottomBar
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaIconButton

/** Shared factual journal summary; loading failures never become empty history. */

private val EDGE = 20.dp

@Composable
fun StatsScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.flow.collectAsState(initial = IknaSettings())
    val activePalette = LocalIknaPalette.current
    var pending by remember { mutableStateOf<Pair<Int?, StatsExportFormat>?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var exportFailed by remember { mutableStateOf(false) }
    var pendingPngExport by rememberSaveable { mutableStateOf(false) }
    var exportingPng by remember { mutableStateOf(false) }
    var pngStatus by remember { mutableStateOf<String?>(null) }
    var snapshot by remember { mutableStateOf<StudyStats?>(null) }
    val savePng = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        val shouldExport = pendingPngExport
        pendingPngExport = false
        if (uri != null && shouldExport) {
            exportingPng = true
            pngStatus = null
            scope.launch {
                try {
                    val currentSnapshot = snapshot ?: container.studyStatsRepository.load()
                    val currentSettings = container.settings.current()
                    val card = buildProfileStatsCardData(
                        profileName = currentSettings.profileName,
                        snapshot = currentSnapshot,
                        palette = activePalette,
                        paletteDisplayName = S.t(paletteSpec(currentSettings.paletteId).nameKey)
                    )
                    val bytes = withContext(Dispatchers.Default) { renderProfileStatsCardPng(card) }
                    withContext(Dispatchers.IO) {
                        checkNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(bytes) }
                    }
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
    val save = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val request = pending
        pending = null
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null && request != null) {
            exporting = true
            scope.launch {
                try {
                    val content = container.studyStatsRepository.export(request.first, request.second)
                    withContext(Dispatchers.IO) {
                        checkNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter(Charsets.UTF_8)
                            .use { it.write(content) }
                    }
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
    var failed by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) {
        failed = false
        snapshot = null
        try {
            snapshot = container.studyStatsRepository.load()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        dev.ikna.ui.theme.IknaBrandPatternBackdrop(intensity = 0.65f)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = EDGE)
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = S.t("stats.001"),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(16.dp))
            IknaLocalProfileCard(
                profileName = settings.profileName,
                snapshot = snapshot,
                paletteDisplayName = S.t(paletteSpec(settings.paletteId).nameKey),
                exportingPng = exportingPng,
                pngStatus = pngStatus,
                onSaveName = { name -> scope.launch { container.settings.setProfileName(name) } },
                onExportPng = {
                    pendingPngExport = true
                    savePng.launch("ikna-profile-stats.png")
                }
            )
            Spacer(Modifier.height(16.dp))

            IknaStatsExportAction(enabled = snapshot != null && pending == null && !exporting) { days, format ->
                exportFailed = false
                pending = days to format
                save.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = format.mimeType
                    putExtra(Intent.EXTRA_TITLE, "ikna-statistics.${format.extension}")
                })
            }
            if (exportFailed) Text(S.t("share.003"))
            Spacer(Modifier.height(32.dp))
            when {
                failed -> IknaStatsLoadFailure { reload++ }
                snapshot == null -> IknaLatticePlaceholder()
                else -> IknaStudyStatsContent(requireNotNull(snapshot))
            }

            // Room for the bar to sit over nothing but background.
            Spacer(Modifier.height(96.dp))
        }

        IknaBottomBar(modifier = Modifier.align(Alignment.BottomCenter)) {
            IknaIconButton(glyph = IknaGlyph.BACK, onClick = onBack, label = S.t("a11y.001"))
        }
    }
}
