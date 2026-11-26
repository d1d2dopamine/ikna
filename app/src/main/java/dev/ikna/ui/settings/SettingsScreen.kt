package dev.ikna.ui.settings

import dev.ikna.data.export.backupJournalThenWipe
import dev.ikna.data.export.PreWipeBackupException
import kotlinx.coroutines.CancellationException

import dev.ikna.data.prefs.suppressedOf
import dev.ikna.data.dev.IknaDataProfile
import dev.ikna.ui.text.S
import dev.ikna.ui.text.UI_LANGUAGES
import dev.ikna.ui.text.uiLanguageLabel
import dev.ikna.ui.debug.DebugHooks

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ikna.AppContainer
import dev.ikna.audio.SpeakerStatus
import dev.ikna.data.export.SettingsBackup
import dev.ikna.data.update.UpdateCheck
import dev.ikna.data.update.UpdateRelease
import dev.ikna.data.update.megabytes
import dev.ikna.ui.update.UpdateDownloadPanel
import dev.ikna.ui.update.UpdatePhase
import dev.ikna.ui.update.has64Bit
import dev.ikna.ui.update.installedVersion
import dev.ikna.ui.update.openInBrowser
import dev.ikna.ui.update.rememberUpdateDownload
import dev.ikna.data.prefs.FontStore
import dev.ikna.data.prefs.FontMode
import dev.ikna.data.prefs.IknaAppearanceVariant
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.LANGUAGE_SYSTEM
import dev.ikna.data.prefs.ThemeMode
import dev.ikna.ui.theme.BarHeight
import dev.ikna.ui.theme.IknaChip
import dev.ikna.ui.theme.IknaDialog
import dev.ikna.ui.theme.IknaBottomBar
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaGlyphIcon
import dev.ikna.ui.theme.IknaHexField
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaRule
import dev.ikna.ui.theme.IknaSwatch
import dev.ikna.ui.theme.LocalIknaControlColors
import dev.ikna.ui.theme.MIN_READABLE_CONTRAST
import dev.ikna.ui.theme.Motion
import dev.ikna.ui.theme.contrastRatio
import dev.ikna.ui.theme.palettePreviewUsesGrey
import dev.ikna.ui.theme.hexOf
import dev.ikna.ui.theme.parseHexColor
import dev.ikna.ui.theme.ratioText
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.IknaWideButton
import dev.ikna.work.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Settings: one screen, with a row of jumps pinned above it.
 *
 * Anki opens a menu of eleven categories first and hides a search box in it.
 * That was considered and rejected. A category menu turns every setting into at
 * least two taps and one act of recall — you must remember which of "general",
 * "reviewing" and "advanced" the thing lives in — and recall is exactly the part
 * that is expensive here: reordering held items is one of the most reliably
 * impaired abilities in ADHD, and a search box is worse still, because it
 * requires knowing the app's own word for the thing before you can type it.
 * Depth also buys nothing at this size: flat structures beat deep ones until a
 * screen has far more entries than this one has.
 *
 * The opposite extreme — an undifferentiated canvas of everything — was rejected
 * too: the usability study of an ADHD self-management app found its users "did
 * not know where to start" when a screen offered content without visible
 * structure. So: sections stay visible and ordered by how often they are
 * actually touched, the pinned row gives one-tap access to any of them without
 * hiding anything, and the rare or destructive things sit behind one expander at
 * the bottom, where they cannot be hit by accident but are still findable
 * without a menu.
 *
 * The other rule this screen keeps: every switch is either reversible or
 * explains its consequence in the same sentence.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    settings: IknaSettings,
    onOpenDebug: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenDeveloper: (dev.ikna.data.dev.DeveloperDestination, String?) -> Unit = { _, _ -> },
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var restoreMessage by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    // Wiping everything is two taps on the same button rather than a dialog:
    // a dialog is one more thing to read at the exact moment you already know
    // what you want.
    var wipeArmed by remember { mutableStateOf(false) }

    // Rare stays closed; DEV exit is visible outside the fold.
    var advancedOpen by remember { mutableStateOf(false) }
    var developerRestarting by remember { mutableStateOf(false) }

    // The update section keeps its own answer rather than reading a stored one:
    // a check made here is a question asked on purpose, and its result belongs to
    // this visit to the screen. Nothing about it survives leaving.
    var updateBusy by remember { mutableStateOf(false) }
    var updateFound by remember { mutableStateOf<UpdateRelease?>(null) }
    var updateNote by remember { mutableStateOf<String?>(null) }

    // The same download the update window runs, with the same band and the same
    // percentage. Two ways in, one thing happening.
    val updateDownload = rememberUpdateDownload()

    // Every interface language at once, for when there are more of them than the
    // section can show without turning into a wall. See LANGUAGE_FOLD.
    var languagesOpen by remember { mutableStateOf(false) }

    // Generated only on request. The report stays in memory until this screen
    // leaves and is not sent anywhere; copying it is a separate explicit tap.
    var diagnosticsOpen by remember { mutableStateOf(false) }
    var diagnosticsBusy by remember { mutableStateOf(false) }
    var diagnostics by remember { mutableStateOf<String?>(null) }

    fun restartWithProfile(profile: IknaDataProfile) {
        check(!developerRestarting)
        container.requestDataProfile(profile)
        try { dev.ikna.ProfileRestartActivity.restart(context); developerRestarting = true }
        catch (failure: Exception) {
            container.requestDataProfile(container.dataProfile)
            throw failure
        }
    }

    // Navigation keeps both destinations composed until Shared Axis X finishes.
    // This screen also starts database reads, optional speech-engine warm-up and
    // a nested jump-row animation. Let the route own those first 280 ms instead
    // of asking the entering layout and its internal state to move at once.
    var routeSettled by remember { mutableStateOf(!settings.animations) }
    LaunchedEffect(Unit) {
        if (settings.animations) delay(Motion.sharedAxisDurationMillis.toLong())
        routeSettled = true
    }

    val listState = rememberLazyListState()
    val speechSectionVisible by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.any { it.index == SPEECH_SECTION_INDEX }
        }
    }


    // Whether anything can speak at all. Which voice reads which deck is a
    // question about languages, and it is answered on the voice screen, one line
    // per deck. It used to be answered here, as a list of the engine's own voice
    // names -- "RU . ORDINARY" next to "default" -- which said nothing about who
    // would read a card and changed nothing anybody could hear.
    var speechStatus by remember { mutableStateOf(SpeakerStatus.UNKNOWN) }
    LaunchedEffect(routeSettled, settings.speechEnabled, speechSectionVisible) {
        if (!routeSettled || !settings.speechEnabled || !speechSectionVisible) {
            return@LaunchedEffect
        }
        speechStatus = if (container.speaker.warmUp()) SpeakerStatus.READY
        else SpeakerStatus.NO_ENGINE
    }

    // A font file is validated before it is stored, not after: Compose parses a
    // font on the first frame that lays out text, so a bad file would throw on
    // every screen at once, including this one.
    val fontPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val name = withContext(Dispatchers.IO) { displayNameOf(context, uri) }
            // install() answers with null when the font was accepted, and the
            // elvis operator below used to swallow exactly that: a successful
            // install produced null, null was replaced by the "could not open
            // the file" message, and the font was never applied. The compiler
            // had been saying so all along - it warned that `problem == null`
            // could never be true. Opening the stream is checked on its own now,
            // so null keeps meaning success.
            val problem: String? = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: return@runCatching S.t("set.001")
                    stream.use { FontStore.install(it) }
                }.getOrElse { S.t("set.002") }
            }
            if (problem == null) {
                container.settings.setCustomFont(name)
                message = S.t("set.003") + name
            } else {
                message = problem
            }
            busy = false
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) message = S.t("set.004")
    }

    var syncFileBusy by remember { mutableStateOf(false) }
    var syncFileStatus by remember { mutableStateOf<String?>(null) }
    var syncFileError by remember { mutableStateOf(false) }
    var scannedSyncCode by remember { mutableStateOf<String?>(null) }
    var showQrScanner by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showQrScanner = true
        } else {
            message = S.t("sync.camera_denied")
        }
    }

    if (showQrScanner) {
        IknaQrScannerDialog(
            onScanned = { code ->
                showQrScanner = false
                scannedSyncCode = code
            },
            onDismiss = { showQrScanner = false }
        )
    }

    val syncFileExportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(dev.ikna.sync.SyncDeltaFile.MIME_TYPE)
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        syncFileBusy = true
        syncFileError = false
        syncFileStatus = null
        scope.launch {
            try {
                val exported = dev.ikna.sync.SyncDeltaFile.exportFrom(
                    container.restoreRepository,
                    profileName = settings.profileName
                )
                withContext(Dispatchers.IO) {
                    checkNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(exported.bytes) }
                }
                syncFileStatus = S.t("sync.file_exported")
                    .replace("{count}", exported.recordCount.toString())
            } catch (_: Exception) {
                syncFileError = true
                syncFileStatus = S.t("sync.file_error")
            } finally {
                syncFileBusy = false
            }
        }
    }

    val syncFileMergePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        syncFileBusy = true
        syncFileError = false
        syncFileStatus = null
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    checkNotNull(context.contentResolver.openInputStream(uri)).use {
                        dev.ikna.sync.SyncDeltaFile.readBounded(it)
                    }
                }
                val merged = dev.ikna.sync.SyncDeltaFile.mergeInto(
                    bytes,
                    container.restoreRepository
                )
                syncFileStatus = S.t("sync.file_merged")
                    .replace("{imported}", merged.imported.toString())
                    .replace("{skipped}", merged.skipped.toString())
            } catch (_: Exception) {
                syncFileError = true
                syncFileStatus = S.t("sync.file_error")
            } finally {
                syncFileBusy = false
            }
        }
    }

    val restorePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            restoreMessage = null
            try {
                val text = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: throw java.io.IOException("Restore source could not be opened")
                    input.bufferedReader().use { it.readText() }
                }

                // One button for both files. Asking the user which of the two they
                // picked would be asking them to remember a distinction the app
                // invented; the file says what it is in its first line.
                if (SettingsBackup.looksLikeSettings(text)) {
                    val snapshot = SettingsBackup.decode(text)
                    if (snapshot == null) {
                        restoreMessage = S.t("set.005")
                    } else {
                        SettingsBackup.apply(container.settings, snapshot)
                        val restoredFontMode = snapshot.fontMode.takeIf { it.isNotBlank() }?.let { raw ->
                            runCatching { FontMode.valueOf(raw) }.getOrNull()
                        } ?: if (snapshot.fontName.isNotBlank()) FontMode.CUSTOM else FontMode.GEOLOGICA
                        restoreMessage = if (restoredFontMode == FontMode.CUSTOM && snapshot.fontName.isNotBlank()) {
                            S.t("set.007") + snapshot.fontName + S.t("set.008")
                        } else {
                            S.t("set.006")
                        }
                    }
                    return@launch
                }

                val result = withContext(Dispatchers.IO) {
                    container.restoreRepository.restoreFromJsonl(text)
                }
                restoreMessage = S.t("set.009") + result.imported +
                    S.t("set.010") + result.replayed +
                    (if (result.skipped > 0) S.t("set.011") + result.skipped else "")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                restoreMessage = S.t("restore.001")
            } finally {
                busy = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        dev.ikna.ui.theme.IknaBrandPatternBackdrop(intensity = 0.65f)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = BarHeight)
        ) {
            // Header and jump row stay put; only the settings themselves scroll.
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IknaGlyphIcon(
                    glyph = IknaGlyph.GEAR,
                    color = MaterialTheme.colorScheme.primary,
                    size = 20.dp
                )
                Text(
                    text = S.t("set.012"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(16.dp))

            // The jump row reads keyed sections near the top of the viewport.
            IknaSettingsJumpRow(
                sections = JUMPS,
                listState = listState,
                animations = settings.animations,
                settled = routeSettled
            ) { id ->
                val targetIndex = JUMPS.indexOfFirst { it.first == id }
                // Keep this lambda expression-only. A labelled return inherited the
                // old JumpRow call name and became illegal after scroll observation
                // moved into SettingsJumpRow.
                if (targetIndex >= 0) {
                    scope.launch {
                        if (settings.animations) {
                            listState.animateScrollToItem(targetIndex)
                        } else {
                            listState.scrollToItem(targetIndex)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            IknaRule(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                item(key = ID_LOAD, contentType = SETTINGS_SECTION_CONTENT_TYPE) {
                    IknaSettingsSection(S.t("set.013"), null, icon = iknaSettingsSectionGlyph(ID_LOAD)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IknaChip(
                                label = S.t("set.015"),
                                selected = settings.autoLoad,
                                onClick = { scope.launch { container.settings.setAutoLoad(true) } }
                            )
                            IknaChip(
                                label = S.t("set.016"),
                                selected = !settings.autoLoad,
                                onClick = {
                                    scope.launch {
                                        container.settings.setManualLoad(settings.manualLoad)
                                    }
                                }
                            )
                        }
                        // In AUTO the app shows no figure here: a number under
                        // the mode chips reads as a daily norm, and the adaptive
                        // load is the one thing this screen must not turn into a
                        // quota (docs/ai-audits.md 7.2).
                        if (!settings.autoLoad) {
                            Spacer(Modifier.height(16.dp))
                            IknaSettingsStepper(
                                value = settings.manualLoad,
                                enabled = !busy,
                                onChange = { next ->
                                    scope.launch { container.settings.setManualLoad(next) }
                                }
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = S.t("set.018"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item(key = ID_LOOK, contentType = SETTINGS_SECTION_CONTENT_TYPE) {
                    IknaSettingsSection(S.t("set.019"), null, icon = iknaSettingsSectionGlyph(ID_LOOK)) {
                        // Which palette, then how it is lit. In that order, because
                        // the palette is the app's face and the mode is only the lamp
                        // pointed at it — and because the tiles below are the answer to
                        // "what do these look like", which no list of words is.
                        Text(
                            text = S.t("set.114"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        IknaPaletteTiles(
                            selectedId = settings.paletteId,
                            grey = palettePreviewUsesGrey(settings, systemDark = isSystemInDarkTheme()),
                            onPick = { id -> scope.launch { container.settings.setPalette(id) } }
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = S.t("set.122"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        // Two rows of two rather than one row of four: "как в системе"
                        // is three words long in every language the app speaks, and
                        // four chips on one line either truncate it or run off the
                        // edge on a narrow phone.
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeMode.entries.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    pair.forEach { mode ->
                                        IknaChip(
                                            label = themeLabel(mode),
                                            selected = settings.theme == mode,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                scope.launch { container.settings.setTheme(mode) }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = S.t("set.153"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IknaChip(
                                label = S.t("set.151"),
                                selected = settings.appearanceVariant == IknaAppearanceVariant.ANGULAR,
                                onClick = {
                                    scope.launch {
                                        container.settings.setAppearanceVariant(IknaAppearanceVariant.ANGULAR)
                                    }
                                }
                            )
                            IknaChip(
                                label = S.t("set.152"),
                                selected = settings.appearanceVariant == IknaAppearanceVariant.ROUNDED,
                                onClick = {
                                    scope.launch {
                                        container.settings.setAppearanceVariant(IknaAppearanceVariant.ROUNDED)
                                    }
                                }
                            )
                        }
                        if (settings.theme == ThemeMode.CUSTOM) {
                            Spacer(Modifier.height(16.dp))
                            SettingsCustomColors(
                                settings = settings,
                                onChange = { background, ink, muted, accent ->
                                    scope.launch {
                                        container.settings.setCustomColors(background, ink, muted, accent)
                                    }
                                }
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        IknaSettingsToggleRow(
                            title = S.t("set.020"),
                            subtitle = null,
                            checked = settings.animations,
                            onCheckedChange = { scope.launch { container.settings.setAnimations(it) } }
                        )
                        IknaSettingsToggleRow(
                            title = S.t("set.ui_sounds"),
                            subtitle = null,
                            checked = settings.uiSounds,
                            onCheckedChange = { scope.launch { container.settings.setUiSounds(it) } }
                        )
                        IknaSettingsToggleRow(
                            title = S.t("set.022"),
                            subtitle = null,
                            checked = settings.haptics,
                            onCheckedChange = { scope.launch { container.settings.setHaptics(it) } }
                        )

                        IknaSettingsToggleRow(
                            title = S.t("bar.003"),
                            subtitle = null,
                            checked = settings.leftHanded,
                            onCheckedChange = { scope.launch { container.settings.setLeftHanded(it) } }
                        )
                        IknaSettingsToggleRow(
                            title = S.t("bar.dock_top"),
                            subtitle = null,
                            checked = settings.homeDockAtTop,
                            onCheckedChange = { scope.launch { container.settings.setHomeDockAtTop(it) } }
                        )
                        IknaSettingsToggleRow(
                            title = S.t("bar.dock_full"),
                            subtitle = null,
                            checked = settings.homeDockFullWidth,
                            onCheckedChange = { scope.launch { container.settings.setHomeDockFullWidth(it) } }
                        )
                        Spacer(Modifier.height(16.dp))
                        dev.ikna.ui.decks.IknaDeckBackgroundSettings(
                            background = settings.deckBackground,
                            onBackground = { background -> scope.launch { container.settings.setDeckBackground(background) } }
                        )
                        Spacer(Modifier.height(8.dp))
                        IknaSettingsToggleRow(
                            title = S.t("deck.background_anim"),
                            subtitle = null,
                            checked = settings.deckBackgroundAnimation,
                            onCheckedChange = { scope.launch { container.settings.setDeckBackgroundAnimation(it) } }
                        )
                    }
                }

                item(key = ID_LANGUAGE, contentType = SETTINGS_SECTION_CONTENT_TYPE) {
                    IknaSettingsSection(S.t("set.024"), null, icon = iknaSettingsSectionGlyph(ID_LANGUAGE)) {
                        // Two chips to a row rather than one full-width chip per
                        // language. A section that grows by a row of forty-four
                        // points for every language added is a section that becomes a
                        // wall the moment the app speaks six languages instead of
                        // three -- and past the fold it stops growing at all: what is
                        // shown then is the phone's own setting and whatever is
                        // chosen, with the rest one tap away.
                        val folded = LANGUAGES.size > LANGUAGE_FOLD && !languagesOpen
                        val shown = if (!folded) LANGUAGES else LANGUAGES.filter {
                            it == LANGUAGE_SYSTEM || it == settings.language
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            shown.chunked(2).forEach { pair ->
                                Row(
                                    modifier = Modifier.height(IntrinsicSize.Min),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    pair.forEach { code ->
                                        IknaChip(
                                            label = languageLabel(code),
                                            selected = settings.language == code,
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                            onClick = {
                                                scope.launch { container.settings.setLanguage(code) }
                                            }
                                        )
                                    }
                                    // A last row of one must not stretch to the width
                                    // of two, or the grid stops being a grid.
                                    repeat(2 - pair.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                        if (LANGUAGES.size > LANGUAGE_FOLD) {
                            Spacer(Modifier.height(8.dp))
                            IknaTextButton(
                                label = if (languagesOpen) S.t("set.065") else S.t("set.128"),
                                onClick = { languagesOpen = !languagesOpen },
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item(key = ID_SPEECH, contentType = SETTINGS_SECTION_CONTENT_TYPE) {
                    IknaSettingsSection(
                        S.t("set.026") + " · " + S.t("set.123"),
                        S.t("set.124") + " " + S.t("set.027"),
                        icon = iknaSettingsSectionGlyph(ID_SPEECH)
                    ) {
                        SettingsSpeechSectionContent(
                            context = context,
                            container = container,
                            settings = settings,
                            speechStatus = speechStatus,
                            scope = scope,
                            onOpenVoice = onOpenVoice,
                            onMessage = { message = it }
                        )
                    }
                }

                item(key = ID_FONT, contentType = SETTINGS_SECTION_CONTENT_TYPE) {
                    IknaSettingsSection(
                        S.t("set.040"),
                        S.t("set.041"),
                        icon = iknaSettingsSectionGlyph(ID_FONT)
                    ) {
                        SettingsFontSectionContent(
                            container = container,
                            settings = settings,
                            busy = busy,
                            scope = scope,
                            onPickFont = { fontPicker.launch(arrayOf("*/*")) }
                        )
                    }
                }

                item(key = ID_REMINDER, contentType = SETTINGS_SECTION_CONTENT_TYPE) {
                    IknaSettingsSection(S.t("set.047"), null, icon = iknaSettingsSectionGlyph(ID_REMINDER)) {
                        SettingsReminderSectionContent(
                            context = context,
                            container = container,
                            settings = settings,
                            scope = scope,
                            onRequestNotificationPermission = { notificationPermission.launch(it) }
                        )
                    }
                }

                item(key = ID_UPDATE, contentType = SETTINGS_SECTION_CONTENT_TYPE) {
                    IknaSettingsSection(S.t("set.138"), S.t("set.139"), icon = iknaSettingsSectionGlyph(ID_UPDATE)) {
                        SettingsUpdateSectionContent(
                            context = context,
                            container = container,
                            settings = settings,
                            scope = scope,
                            updateBusy = updateBusy,
                            updateFound = updateFound,
                            updateNote = updateNote,
                            updateDownload = updateDownload,
                            onUpdateBusyChange = { updateBusy = it },
                            onUpdateFoundChange = { updateFound = it },
                            onUpdateNoteChange = { updateNote = it }
                        )
                    }
                }

                item(key = ID_DATA, contentType = SETTINGS_SECTION_CONTENT_TYPE) {
                    IknaSettingsSection(
                        S.t("set.052"),
                        S.t("set.053"),
                        icon = iknaSettingsSectionGlyph(ID_DATA)
                    ) {
                        LocalProfileSettingsCard(
                            profileName = settings.profileName,
                            onSaveName = { name -> scope.launch { container.settings.setProfileName(name) } }
                        )
                        Spacer(Modifier.height(16.dp))
                        if (!container.isDeveloperMode) {
                            DeviceSyncSettings(
                                controller = container.deviceSync,
                                scannedCode = scannedSyncCode,
                                onScannedCodeConsumed = { scannedSyncCode = null },
                                onScanQr = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                        showQrScanner = true
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                localProfileName = settings.profileName
                            )
                            SyncFilePanel(
                                busy = syncFileBusy,
                                statusMessage = syncFileStatus,
                                isError = syncFileError,
                                onExportFile = {
                                    syncFileExportPicker.launch("ikna-sync.${dev.ikna.sync.SyncDeltaFile.FILE_EXTENSION}")
                                },
                                onMergeFile = {
                                    syncFileMergePicker.launch(arrayOf("*/*"))
                                }
                            )
                            Spacer(Modifier.height(16.dp))
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outline)
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = S.t("sync.title"),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = S.t("sync.dev_notice"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                        IknaSettingsToggleRow(
                            title = S.t("set.054"),
                            subtitle = S.t("set.055"),
                            checked = settings.autoExport,
                            onCheckedChange = { scope.launch { container.settings.setAutoExport(it) } }
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IknaWideButton(
                                label = S.t("set.056"),
                                modifier = Modifier.weight(1f),
                                height = 52.dp,
                                enabled = !busy,
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        val current = container.settings.current()
                                        // Two files: the log, and the look. The second
                                        // one is small and it is the difference between
                                        // a restore that gives your app back and one
                                        // that gives you a stranger's app with your
                                        // history in it.
                                        val outcome = withContext(Dispatchers.IO) {
                                            runCatching {
                                                val log = container.jsonExporter.export()
                                                container.jsonExporter.exportSettings(
                                                    SettingsBackup.encode(
                                                        current,
                                                        synthetic = container.isDeveloperMode
                                                    )
                                                )
                                                log
                                            }
                                        }
                                        busy = false
                                        // An empty log is not a failure, and saying "не
                                        // удалось" for it sends the user looking for a
                                        // problem that does not exist.
                                        message = when {
                                            outcome.isFailure -> S.t("set.057")
                                            outcome.getOrNull() == null ->
                                                S.t("set.058")
                                            else -> S.t("set.059")
                                        }
                                    }
                                }
                            )
                            IknaWideButton(
                                label = S.t("set.060"),
                                modifier = Modifier.weight(1f),
                                height = 52.dp,
                                enabled = !busy,
                                onClick = { restorePicker.launch(arrayOf("*/*")) }
                            )
                        }

                        restoreMessage?.let { status ->
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = status,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Cards taken out of rotation by "this card is wrong".
                        //
                        // Marking a card wrong has to be one tap and no dialog, or it
                        // will not be used and a bad card stays in the rotation. The
                        // price of that is that it can be tapped by accident, and a
                        // card removed by accident with no way back would be data loss
                        // in an app whose whole promise is that nothing is lost. So the
                        // door is here, it only appears when there is something behind
                        // it, and it says how many.
                        val quarantined = suppressedOf(settings.suppressed).size
                        if (quarantined > 0) {
                            Spacer(Modifier.height(20.dp))
                            Text(
                                text = S.t("set.135") + quarantined,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            IknaTextButton(
                                label = S.t("set.136"),
                                onClick = {
                                    scope.launch {
                                        container.settings.clearSuppressed()
                                        // The day was planned without them in it.
                                        container.learningRepository.invalidatePlan()
                                        message = S.t("set.137")
                                    }
                                }
                            )
                        }

                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = S.t("diag.001"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = S.t("diag.002"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        IknaTextButton(
                            label = when {
                                diagnosticsBusy -> S.t("diag.005")
                                diagnosticsOpen -> S.t("diag.004")
                                else -> S.t("diag.003")
                            },
                            onClick = {
                                if (diagnosticsBusy) return@IknaTextButton
                                if (diagnosticsOpen) {
                                    diagnosticsOpen = false
                                } else {
                                    diagnosticsOpen = true
                                    diagnosticsBusy = true
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            runCatching { collectDiagnostics(context, container) }
                                        }
                                        diagnostics = result.getOrNull()
                                        diagnosticsBusy = false
                                        if (result.isFailure) message = S.t("diag.008")
                                    }
                                }
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val report = diagnostics
                        if (diagnosticsOpen && report != null) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = report,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            IknaWideButton(
                                label = S.t("diag.006"),
                                height = 52.dp,
                                onClick = {
                                    context.getSystemService(ClipboardManager::class.java)
                                        ?.setPrimaryClip(
                                            ClipData.newPlainText("ikna diagnostics", report)
                                        )
                                    message = S.t("diag.007")
                                }
                            )
                        }
                    }
                }

                item(key = ID_ADVANCED, contentType = SETTINGS_SECTION_CONTENT_TYPE) {
                    IknaSettingsSection(S.t("set.063"), null, icon = iknaSettingsSectionGlyph(ID_ADVANCED)) {
                        if (container.isDeveloperMode) {
                            dev.ikna.ui.dev.DeveloperProfileControls(
                                profile = container.dataProfile, enabled = !developerRestarting,
                                onSwitch = ::restartWithProfile
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        IknaTextButton(
                            label = if (advancedOpen) S.t("set.065") else S.t("set.066"),
                            onClick = { if (!developerRestarting) advancedOpen = !advancedOpen },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (advancedOpen) {
                            Spacer(Modifier.height(16.dp))
                            if (!container.isDeveloperMode) {
                                dev.ikna.ui.dev.DeveloperProfileControls(
                                    profile = container.dataProfile, enabled = !developerRestarting,
                                    onSwitch = ::restartWithProfile
                                )
                            } else {
                                container.developerTools?.let { tools ->
                                    dev.ikna.ui.dev.DeveloperToolsPanel(
                                        tools = tools, enabled = !developerRestarting,
                                        applyProductLimits = settings.developerApplyProductLimits,
                                        onOpen = onOpenDeveloper
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                dev.ikna.ui.dev.DeveloperScenarioControls(
                                    scenarioId = settings.developerScenario, enabled = !developerRestarting,
                                    onReseed = { scenario ->
                                        check(!developerRestarting)
                                        container.requestDeveloperScenario(scenario)
                                        try { restartWithProfile(IknaDataProfile.DEVELOPER) }
                                        catch (failure: Exception) {
                                            container.cancelDeveloperScenarioRequest()
                                            throw failure
                                        }
                                    }
                                )
                            }

                            Spacer(Modifier.height(18.dp))
                            Text(
                                text = S.t("set.144"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(8.dp))
                            IknaSettingsToggleRow(
                                title = S.t("pseudo.001"),
                                subtitle = S.t("pseudo.002"),
                                checked = settings.pseudoLocale,
                                onCheckedChange = {
                                    scope.launch { container.settings.setPseudoLocale(it) }
                                }
                            )
                            Spacer(Modifier.height(8.dp))
                            if (container.isDeveloperMode) {
                                IknaWideButton(
                                    label = S.t("set.067"),
                                    height = 52.dp,
                                    enabled = !busy,
                                    onClick = {
                                        scope.launch {
                                            busy = true
                                            withContext(Dispatchers.IO) {
                                                container.components.rebuildFromReviews()
                                            }
                                            busy = false
                                            message = S.t("set.068")
                                        }
                                    }
                                )
                            }
                            // The way into the technical screen, in debug builds
                            // only. This is not a hidden button: in a release build
                            // the screen behind it is not compiled at all, and
                            // `available` is a constant false, so the branch is gone
                            // before R8 would have had a chance to look at it (R8 is
                            // off here anyway). See ui/debug/DebugHooks.kt, of which
                            // there are two.
                            if (DebugHooks.available) {
                                Spacer(Modifier.height(8.dp))
                                IknaWideButton(
                                    label = S.t("set.069"),
                                    height = 52.dp,
                                    onClick = onOpenDebug
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            IknaTextButton(
                                label = S.t("set.070"),
                                onClick = { confirmReset = true },
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(Modifier.height(24.dp))
                            Text(
                                text = S.t("set.071"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = S.t("set.072"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            IknaWideButton(
                                label = if (wipeArmed) S.t("set.073") else S.t("set.074"),
                                enabled = !busy,
                                height = 60.dp,
                                onClick = {
                                    if (!wipeArmed) {
                                        wipeArmed = true
                                        message = S.t("set.075")
                                    } else {
                                        wipeArmed = false
                                        busy = true
                                        scope.launch {
                                            try {
                                                withContext(Dispatchers.IO) {
                                                    backupJournalThenWipe(
                                                        exportJournal = { container.jsonExporter.export() },
                                                        exportSettings = {
                                                            container.jsonExporter.exportSettings(
                                                                SettingsBackup.encode(
                                                                    container.settings.current(),
                                                                    synthetic = container.isDeveloperMode
                                                                )
                                                            )
                                                        },
                                                        wipe = {
                                                            container.wipeDatabase()
                                                            container.settings.clearAll()
                                                        }
                                                    )
                                                }
                                                dev.ikna.ProfileRestartActivity.restart(context)
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (failure: Exception) {
                                                message = S.t(if (failure is PreWipeBackupException)
                                                    "wipe.001" else "wipe.002")
                                            } finally {
                                                busy = false
                                            }
                                        }
                                    }
                                }
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (wipeArmed) S.t("set.076") else S.t("set.077"),
                                style = MaterialTheme.typography.bodySmall,
                                // The palette decides what danger looks like, and on a
                                // warm palette it is not a colour at all: see
                                // dangerFor in Theme.kt.
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                if (message != null) {
                    item(key = "settings-message") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Spacer(Modifier.height(20.dp))
                            Text(
                                text = message.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                item(key = "settings-bottom-space") {
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
        IknaBottomBar(modifier = Modifier.align(Alignment.BottomCenter)) {
            IknaIconButton(glyph = IknaGlyph.BACK, onClick = onBack, label = S.t("a11y.001"))
        }
    }

    if (confirmReset) {
        IknaDialog(
            title = S.t("set.078"),
            body = S.t("set.079") + "\n\n" + S.t("sync.reset_warning"),
            confirmLabel = S.t("set.080"),
            confirmColor = MaterialTheme.colorScheme.onSurfaceVariant,
            onConfirm = {
                confirmReset = false
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { container.resetProgress() }
                        message = S.t("set.081")
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        message = S.t("sync.reset_failed")
                    }
                }
            },
            dismissLabel = S.t("set.082"),
            onDismiss = { confirmReset = false }
        )
    }
}

/**
 * Reads scroll state in a small restart scope. A fast fling can change the first
 * visible section several times without recomposing the settings document.
 */


/**
 * The pinned row of jumps.
 *
 * This is the whole answer to "menu or canvas": it gives the one thing a menu is
 * good for — naming what exists — without the tap, the back button, or the need
 * to remember which drawer a switch was filed in. Nothing is hidden behind it,
 * so scrolling still works for anyone who would rather scroll.
 */


/**
 * Minus, number, plus.
 *
 * A slider would be smaller on screen and worse in every other way: it demands a
 * precise drag to land on a number, it has no idea what a sensible step is, and
 * it invites fiddling with a figure that only matters in steps of five.
 */






private const val SETTINGS_SECTION_CONTENT_TYPE = "settings-section"

private const val ID_LOAD = "load"
private const val ID_LOOK = "look"
private const val ID_LANGUAGE = "language"
private const val ID_SPEECH = "speech"
private const val ID_FONT = "font"
private const val ID_REMINDER = "reminder"
private const val ID_DATA = "data"
private const val ID_UPDATE = "update"

private const val ID_ADVANCED = "advanced"

private val JUMPS = listOf(
    ID_LOAD to "set.091",
    ID_LOOK to "set.092",
    ID_LANGUAGE to "set.093",
    ID_SPEECH to "set.094",
    ID_FONT to "set.095",
    ID_REMINDER to "set.096",
    ID_UPDATE to "set.140",
    ID_DATA to "set.097",
    ID_ADVANCED to "set.098"
)

private val SPEECH_SECTION_INDEX = JUMPS.indexOfFirst { it.first == ID_SPEECH }

private val LANGUAGES = listOf(
    LANGUAGE_SYSTEM
) + UI_LANGUAGES.map { it.code }

private const val LANGUAGE_FOLD = 6

private fun languageLabel(code: String): String = when (code) {
    LANGUAGE_SYSTEM -> S.t("set.099")
    else -> uiLanguageLabel(code) ?: code.uppercase(Locale.getDefault())
}
