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
import dev.ikna.ui.stats.ProfileTab
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
    var selectedTab by remember { mutableStateOf(ProfileTab.OVERVIEW) }
    var showJournalDetails by remember { mutableStateOf(false) }
    val changeToken by container.studyStatsRepository.observeChanges().collectAsState(initial = 0L)
    val syncGeneration by container.sessionChanges.generation.collectAsState()
    LaunchedEffect(reload, changeToken, syncGeneration) {
        failed = false
        try {
            snapshot = container.studyStatsRepository.load()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            logLine("stats failed: " + error)
            failed = true
        }
    }
    DesktopScrollablePane(
        S.t("stats.001"),
        onBack,
        icon = dev.ikna.ui.theme.IknaGlyph.PROFILE,
        backdropIntensity = 0.26f
    ) {
        IknaLocalProfileCard(
            profileName = settings.profileName,
            profileBio = settings.profileBio,
            profileAvatar = settings.profileAvatar,
            profileBanner = settings.profileBanner,
            snapshot = snapshot,
            paletteDisplayName = S.t(paletteSpec(settings.paletteId).nameKey),
            exportingPng = exportingPng,
            pngStatus = pngStatus,
            selectedTab = selectedTab,
            onSelectTab = { selectedTab = it },
            onSaveName = { name -> scope.launch { container.settings.setProfileName(name) } },
            onSaveProfile = { name, bio ->
                scope.launch {
                    container.settings.setProfileName(name)
                    container.settings.setProfileBio(bio)
                }
            },
            onPickAvatar = {
                val chooser = JFileChooser().apply {
                    fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "bmp", "gif")
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    val src = chooser.selectedFile
                    scope.launch {
                        val savedPath = withContext(Dispatchers.IO) {
                            runCatching {
                                val loaded = javax.imageio.ImageIO.read(src) ?: return@runCatching null
                                val side = minOf(loaded.width, loaded.height)
                                val sx = (loaded.width - side) / 2
                                val sy = (loaded.height - side) / 2
                                val scaled = java.awt.image.BufferedImage(256, 256, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                                val g = scaled.createGraphics()
                                try {
                                    g.setRenderingHint(
                                        java.awt.RenderingHints.KEY_INTERPOLATION,
                                        java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR
                                    )
                                    g.drawImage(loaded, 0, 0, 256, 256, sx, sy, sx + side, sy + side, null)
                                } finally {
                                    g.dispose()
                                }
                                val dir = File(System.getProperty("user.home"), ".ikna/avatars").apply { mkdirs() }
                                dir.listFiles()?.forEach { it.delete() }
                                val outFile = File(dir, "avatar-${System.currentTimeMillis()}.png")
                                javax.imageio.ImageIO.write(scaled, "png", outFile)
                                outFile.absolutePath
                            }.getOrNull()
                        }
                        if (!savedPath.isNullOrBlank()) {
                            container.settings.setProfileAvatar(savedPath)
                        }
                    }
                }
            },
            onRemoveAvatar = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching { File(System.getProperty("user.home"), ".ikna/avatars").deleteRecursively() }
                    }
                    container.settings.setProfileAvatar("")
                }
            },
            onPickBanner = {
                val chooser = JFileChooser().apply {
                    fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "bmp", "gif")
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    val src = chooser.selectedFile
                    scope.launch {
                        val savedPath = withContext(Dispatchers.IO) {
                            runCatching {
                                val loaded = javax.imageio.ImageIO.read(src) ?: return@runCatching null
                                val targetW = 1200
                                val targetH = 360
                                val targetRatio = targetW.toDouble() / targetH.toDouble()
                                val srcRatio = loaded.width.toDouble() / loaded.height.toDouble()
                                val (cropW, cropH) = if (srcRatio > targetRatio) {
                                    (loaded.height * targetRatio).toInt() to loaded.height
                                } else {
                                    loaded.width to (loaded.width / targetRatio).toInt()
                                }
                                val sx = ((loaded.width - cropW) / 2).coerceAtLeast(0)
                                val sy = ((loaded.height - cropH) / 2).coerceAtLeast(0)
                                val scaled = java.awt.image.BufferedImage(targetW, targetH, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                                val g = scaled.createGraphics()
                                try {
                                    g.setRenderingHint(
                                        java.awt.RenderingHints.KEY_INTERPOLATION,
                                        java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR
                                    )
                                    g.drawImage(loaded, 0, 0, targetW, targetH, sx, sy, sx + cropW, sy + cropH, null)
                                } finally {
                                    g.dispose()
                                }
                                val dir = File(System.getProperty("user.home"), ".ikna/banners").apply { mkdirs() }
                                dir.listFiles()?.forEach { it.delete() }
                                val outFile = File(dir, "banner-${System.currentTimeMillis()}.png")
                                javax.imageio.ImageIO.write(scaled, "png", outFile)
                                outFile.absolutePath
                            }.getOrNull()
                        }
                        if (!savedPath.isNullOrBlank()) {
                            container.settings.setProfileBanner(savedPath)
                        }
                    }
                }
            },
            onRemoveBanner = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching { File(System.getProperty("user.home"), ".ikna/banners").deleteRecursively() }
                    }
                    container.settings.setProfileBanner("")
                }
            },
            onSelectBannerPreset = { presetId ->
                scope.launch {
                    container.settings.setProfileBanner("preset:$presetId")
                }
            },
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
        Spacer(Modifier.height(16.dp))
        when {
            failed -> dev.ikna.ui.stats.IknaStatsLoadFailure { reload++ }
            snapshot == null -> IknaLatticePlaceholder()
            selectedTab == ProfileTab.ANALYTICS -> {
                dev.ikna.ui.theme.IknaRule()
                Spacer(Modifier.height(8.dp))
                dev.ikna.ui.theme.IknaTextButton(
                    label = S.t(if (showJournalDetails) "prof.details_hide" else "prof.details_show"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    horizontalPadding = 0.dp,
                    onClick = { showJournalDetails = !showJournalDetails }
                )
                if (showJournalDetails) {
                    Spacer(Modifier.height(12.dp))
                    IknaStatsExportAction(enabled = !exporting) { days, format ->
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
                    Spacer(Modifier.height(12.dp))
                    dev.ikna.ui.stats.IknaStudyStatsContent(requireNotNull(snapshot))
                }
            }
        }
    }
}
