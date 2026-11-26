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
    val pickAvatar = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val savedPath = withContext(Dispatchers.IO) {
                runCatching {
                    val decoded = context.contentResolver.openInputStream(uri)?.use {
                        android.graphics.BitmapFactory.decodeStream(it)
                    } ?: return@runCatching null
                    val side = minOf(decoded.width, decoded.height)
                    val sx = (decoded.width - side) / 2
                    val sy = (decoded.height - side) / 2
                    val square = android.graphics.Bitmap.createBitmap(decoded, sx, sy, side, side)
                    val scaled = android.graphics.Bitmap.createScaledBitmap(square, 256, 256, true)
                    if (square !== decoded) decoded.recycle()
                    if (scaled !== square) square.recycle()
                    val dir = java.io.File(context.filesDir, "avatars").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    val outFile = java.io.File(dir, "avatar-${System.currentTimeMillis()}.png")
                    outFile.outputStream().use { scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 95, it) }
                    scaled.recycle()
                    outFile.absolutePath
                }.getOrNull()
            }
            if (!savedPath.isNullOrBlank()) {
                container.settings.setProfileAvatar(savedPath)
            }
        }
    }
    val pickBanner = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val savedPath = withContext(Dispatchers.IO) {
                runCatching {
                    val decoded = context.contentResolver.openInputStream(uri)?.use {
                        android.graphics.BitmapFactory.decodeStream(it)
                    } ?: return@runCatching null
                    val targetW = 1200
                    val targetH = 360
                    val targetRatio = targetW.toDouble() / targetH.toDouble()
                    val srcRatio = decoded.width.toDouble() / decoded.height.toDouble()
                    val (cropW, cropH) = if (srcRatio > targetRatio) {
                        (decoded.height * targetRatio).toInt().coerceAtLeast(1) to decoded.height
                    } else {
                        decoded.width to (decoded.width / targetRatio).toInt().coerceAtLeast(1)
                    }
                    val sx = ((decoded.width - cropW) / 2).coerceAtLeast(0)
                    val sy = ((decoded.height - cropH) / 2).coerceAtLeast(0)
                    val cropped = android.graphics.Bitmap.createBitmap(decoded, sx, sy, cropW, cropH)
                    val scaled = android.graphics.Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
                    if (cropped !== decoded) decoded.recycle()
                    if (scaled !== cropped) cropped.recycle()
                    val dir = java.io.File(context.filesDir, "banners").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    val outFile = java.io.File(dir, "banner-${System.currentTimeMillis()}.png")
                    outFile.outputStream().use { scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 92, it) }
                    scaled.recycle()
                    outFile.absolutePath
                }.getOrNull()
            }
            if (!savedPath.isNullOrBlank()) {
                container.settings.setProfileBanner(savedPath)
            }
        }
    }
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
                        paletteDisplayName = S.t(paletteSpec(currentSettings.paletteId).nameKey),
                        profileBio = currentSettings.profileBio,
                        profileAvatar = currentSettings.profileAvatar
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
    var selectedTab by remember { mutableStateOf(ProfileTab.OVERVIEW) }
    var showJournalDetails by remember { mutableStateOf(false) }
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
        dev.ikna.ui.theme.IknaBrandPatternBackdrop(intensity = 0.26f)
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
                onPickAvatar = { pickAvatar.launch("image/*") },
                onRemoveAvatar = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching { java.io.File(context.filesDir, "avatars").deleteRecursively() }
                        }
                        container.settings.setProfileAvatar("")
                    }
                },
                onPickBanner = { pickBanner.launch("image/*") },
                onRemoveBanner = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching { java.io.File(context.filesDir, "banners").deleteRecursively() }
                        }
                        container.settings.setProfileBanner("")
                    }
                },
                onExportPng = {
                    pendingPngExport = true
                    savePng.launch("ikna-profile-stats.png")
                }
            )
            Spacer(Modifier.height(16.dp))
            when {
                failed -> IknaStatsLoadFailure { reload++ }
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
                        IknaStatsExportAction(enabled = pending == null && !exporting) { days, format ->
                            exportFailed = false
                            pending = days to format
                            save.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = format.mimeType
                                putExtra(Intent.EXTRA_TITLE, "ikna-statistics.${format.extension}")
                            })
                        }
                        if (exportFailed) Text(S.t("share.003"))
                        Spacer(Modifier.height(16.dp))
                        IknaStudyStatsContent(requireNotNull(snapshot))
                    }
                }
            }

            // Room for the bar to sit over nothing but background.
            Spacer(Modifier.height(96.dp))
        }

        IknaBottomBar(modifier = Modifier.align(Alignment.BottomCenter)) {
            IknaIconButton(glyph = IknaGlyph.BACK, onClick = onBack, label = S.t("a11y.001"))
        }
    }
}
