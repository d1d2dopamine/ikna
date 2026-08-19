package dev.ikna.ui.settings

import dev.ikna.data.prefs.suppressedOf
import dev.ikna.ui.text.S
import dev.ikna.ui.debug.DebugHooks

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ikna.AppContainer
import dev.ikna.MainActivity
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
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.LANGUAGE_SYSTEM
import dev.ikna.data.prefs.MANUAL_LOAD_MAX
import dev.ikna.data.prefs.MANUAL_LOAD_MIN
import dev.ikna.data.prefs.MANUAL_LOAD_STEP
import dev.ikna.data.prefs.ThemeMode
import dev.ikna.ui.theme.IknaChip
import dev.ikna.ui.theme.IknaDialog
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaHexField
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaRule
import dev.ikna.ui.theme.IknaSwatch
import dev.ikna.ui.theme.IknaPalettes
import dev.ikna.ui.theme.MIN_READABLE_CONTRAST
import dev.ikna.ui.theme.Motion
import dev.ikna.ui.theme.contrastRatio
import dev.ikna.ui.theme.isLight
import dev.ikna.ui.theme.hexOf
import dev.ikna.ui.theme.parseHexColor
import dev.ikna.ui.theme.ratioText
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.IknaToggle
import dev.ikna.ui.theme.IknaWideButton
import dev.ikna.work.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.system.exitProcess

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
@Composable
fun SettingsScreen(
    container: AppContainer,
    settings: IknaSettings,
    onOpenDebug: () -> Unit,
    onOpenVoice: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    // Wiping everything is two taps on the same button rather than a dialog:
    // a dialog is one more thing to read at the exact moment you already know
    // what you want.
    var wipeArmed by remember { mutableStateOf(false) }

    // The rare and the irreversible live behind one expander. Closed by default,
    // so nothing here can be hit while scrolling past it.
    var advancedOpen by remember { mutableStateOf(false) }

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

    val scroll = rememberScrollState()
    // Filled in as the sections are laid out, so the pinned row can jump to a
    // real offset instead of a guessed one.
    val anchors = remember { mutableStateMapOf<String, Int>() }

    // The measured norm is shown rather than asked for. The chip says "АВТО" and
    // nothing else; the number goes on its own line, in the same form the font
    // section uses, because a chip that changes its own text is a chip that
    // moves under your finger.
    var measuredNorm by remember { mutableStateOf(0) }
    var normMeasured by remember { mutableStateOf(true) }
    LaunchedEffect(settings.autoLoad, settings.manualLoad) {
        measuredNorm = container.learningRepository.currentDailyTarget()
        normMeasured = container.learningRepository.normIsMeasured()
    }

    // Whether anything can speak at all. Which voice reads which deck is a
    // question about languages, and it is answered on the voice screen, one line
    // per deck. It used to be answered here, as a list of the engine's own voice
    // names -- "RU . ORDINARY" next to "default" -- which said nothing about who
    // would read a card and changed nothing anybody could hear.
    var speechStatus by remember { mutableStateOf(SpeakerStatus.UNKNOWN) }
    LaunchedEffect(settings.speechEnabled) {
        if (!settings.speechEnabled) return@LaunchedEffect
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
                    stream.use { FontStore.install(context, it) }
                }.getOrElse { S.t("set.002") }
            }
            if (problem == null) {
                container.settings.setFontName(name)
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

    val restorePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() } ?: ""
                }.getOrDefault("")
            }

            // One button for both files. Asking the user which of the two they
            // picked would be asking them to remember a distinction the app
            // invented; the file says what it is in its first line.
            if (SettingsBackup.looksLikeSettings(text)) {
                val snapshot = SettingsBackup.decode(text)
                if (snapshot == null) {
                    message = S.t("set.005")
                } else {
                    SettingsBackup.apply(container.settings, snapshot)
                    message = if (snapshot.fontName.isBlank()) {
                        S.t("set.006")
                    } else {
                        S.t("set.007") + snapshot.fontName +
                            S.t("set.008")
                    }
                }
                busy = false
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                container.restoreRepository.restoreFromJsonl(text)
            }
            busy = false
            message = S.t("set.009") + result.imported +
                S.t("set.010") + result.replayed +
                (if (result.skipped > 0) S.t("set.011") + result.skipped else "")
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header and jump row stay put; only the settings themselves scroll.
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            // The glyph box is 44dp with a 19dp mark in it, so it is pulled back
            // by half the difference to stand on the same margin as the text.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .offset(x = (-12).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IknaIconButton(glyph = IknaGlyph.BACK, onClick = onBack, label = S.t("a11y.001"))
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = S.t("set.012"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(16.dp))

        // Which section the page is actually showing.
        //
        // The row above used to be a one-way remote control: it could send
        // the page somewhere, and then stood still while the page moved under
        // it. So the name of the section you were reading could be anywhere,
        // including scrolled off the edge, and nothing on screen agreed with
        // anything else. The anchors were already being measured for the jump;
        // reading them the other way costs one comparison per frame.
        //
        // The bias is what makes it feel right rather than merely correct: a
        // section counts as the current one once its heading has passed the
        // top by a finger's width, not the instant its first pixel appears.
        val bias = with(LocalDensity.current) { 120.dp.toPx() }
        val activeSection by remember(bias) {
            derivedStateOf {
                val y = scroll.value + bias
                JUMPS.lastOrNull { (anchors[it.first] ?: Int.MAX_VALUE) <= y }?.first
                    ?: JUMPS.first().first
            }
        }
        JumpRow(activeId = activeSection, animations = settings.animations) { id ->
            val target = anchors[id] ?: return@JumpRow
            scope.launch {
                if (settings.animations) {
                    scroll.animateScrollTo(
                        target,
                        animationSpec = tween(
                            durationMillis = Motion.sectionScrollDurationMillis,
                            easing = LinearOutSlowInEasing
                        )
                    )
                } else {
                    scroll.scrollTo(target)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        IknaRule(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp)
        ) {
            Anchored(ID_LOAD, anchors) {
                Section(S.t("set.013"), null) {
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
                    if (settings.autoLoad) {
                        if (normMeasured && measuredNorm > 0) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = S.t("set.017") + measuredNorm,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Spacer(Modifier.height(16.dp))
                        Stepper(
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

            Anchored(ID_LOOK, anchors) {
                Section(S.t("set.019"), null) {
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
                    PaletteTiles(
                        selectedId = settings.paletteId,
                        // The tiles are drawn in the lighting the app is in right
                        // now, read off the background rather than asked of the
                        // system: with a custom scheme the two can disagree, and
                        // what matters is what the eye is currently adapted to.
                        light = isLight(MaterialTheme.colorScheme.background),
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
                    if (settings.theme == ThemeMode.CUSTOM) {
                        Spacer(Modifier.height(16.dp))
                        CustomColors(
                            settings = settings,
                            onChange = { background, ink, muted, accent ->
                                scope.launch {
                                    container.settings.setCustomColors(background, ink, muted, accent)
                                }
                            }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    ToggleRow(
                        title = S.t("set.020"),
                        subtitle = null,
                        checked = settings.animations,
                        onCheckedChange = { scope.launch { container.settings.setAnimations(it) } }
                    )
                    ToggleRow(
                        title = S.t("set.022"),
                        subtitle = null,
                        checked = settings.haptics,
                        onCheckedChange = { scope.launch { container.settings.setHaptics(it) } }
                    )

                    ToggleRow(
                        title = S.t("bar.001"),
                        subtitle = null,
                        checked = settings.showWordmark,
                        onCheckedChange = { scope.launch { container.settings.setShowWordmark(it) } }
                    )
                    ToggleRow(
                        title = S.t("bar.003"),
                        subtitle = null,
                        checked = settings.leftHanded,
                        onCheckedChange = { scope.launch { container.settings.setLeftHanded(it) } }
                    )
                }
            }

            Anchored(ID_LANGUAGE, anchors) {
                Section(S.t("set.024"), null) {
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
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { code ->
                                    IknaChip(
                                        label = languageLabel(code),
                                        selected = settings.language == code,
                                        modifier = Modifier.weight(1f),
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

            Anchored(ID_SPEECH, anchors) {
                Section(
                    // Marked beta in the heading and off by default. The feature
                    // works, but how good it sounds is decided by an engine this
                    // app did not write and cannot inspect, so it is offered
                    // rather than assumed.
                    S.t("set.026") + " · " + S.t("set.123"),
                    S.t("set.124") + " " + S.t("set.027")
                ) {
                    ToggleRow(
                        title = S.t("set.028"),
                        subtitle = S.t("set.029"),
                        checked = settings.speechEnabled,
                        onCheckedChange = { on ->
                            scope.launch {
                                container.settings.setSpeechEnabled(on)
                                if (!on) container.speaker.stop()
                            }
                        }
                    )

                    // Which voice actually speaks -- the phone's own engine or a
                    // model somebody added -- has a screen of its own now. It used
                    // to have nowhere at all, which is how an APK with a model
                    // inside came to look exactly like one without.
                    Spacer(Modifier.height(12.dp))
                    IknaWideButton(
                        label = S.t("voice.001"),
                        onClick = onOpenVoice
                    )

                    if (settings.speechEnabled) {
                        // The phone's engine, as one switch. Speed, pitch and a
                        // voice per language were three controls over an engine
                        // this app did not write: pitch did nothing on most of
                        // them, and both numbers were part of the name of every
                        // cached rendering, which is what left the first card of
                        // a session silent. A model of one's own keeps its own
                        // speed, on the voice screen, where it belongs.
                        Spacer(Modifier.height(12.dp))
                        ToggleRow(
                            title = S.t("set.130"),
                            subtitle = S.t("set.131"),
                            checked = settings.phoneVoice,
                            onCheckedChange = { on ->
                                scope.launch {
                                    container.settings.setPhoneVoice(on)
                                    // Told directly as well as through settings,
                                    // so an open session obeys it on the next card.
                                    container.speaker.setPhoneVoice(on)
                                    if (!on) container.speaker.stop()
                                }
                            }
                        )
                        ToggleRow(
                            title = S.t("set.132"),
                            subtitle = if (settings.autoSpeakEvery) S.t("set.133")
                            else S.t("set.134"),
                            checked = settings.autoSpeakEvery,
                            onCheckedChange = { on ->
                                scope.launch { container.settings.setAutoSpeakEvery(on) }
                            }
                        )

                        when (speechStatus) {
                            SpeakerStatus.UNKNOWN -> {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = S.t("set.030"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            SpeakerStatus.NO_ENGINE -> {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = S.t("set.031"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                IknaTextButton(
                                    label = S.t("set.032"),
                                    onClick = {
                                        if (!openTtsSettings(context)) {
                                            message = S.t("set.033")
                                        }
                                    }
                                )
                            }

                            SpeakerStatus.NO_VOICE -> {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = S.t("set.034"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                IknaTextButton(
                                    label = S.t("set.035"),
                                    onClick = {
                                        if (!installVoices(context)) {
                                            message = S.t("set.036")
                                        }
                                    }
                                )
                            }

                            SpeakerStatus.READY -> {
                                Spacer(Modifier.height(16.dp))
                                IknaTextButton(
                                    label = S.t("set.032"),
                                    onClick = {
                                        if (!openTtsSettings(context)) {
                                            message = S.t("set.033")
                                        }
                                    }
                                )

                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = S.t("set.039"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Anchored(ID_FONT, anchors) {
                Section(
                    S.t("set.040"),
                    S.t("set.041")
                ) {
                    Text(
                        text = if (settings.fontName.isBlank()) S.t("set.042")
                        else S.t("set.043") + settings.fontName.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IknaWideButton(
                            label = S.t("set.044"),
                            modifier = Modifier.weight(1f),
                            height = 52.dp,
                            enabled = !busy,
                            onClick = { fontPicker.launch(arrayOf("*/*")) }
                        )
                        if (settings.fontName.isNotBlank()) {
                            IknaWideButton(
                                label = S.t("set.045"),
                                modifier = Modifier.weight(1f),
                                height = 52.dp,
                                enabled = !busy,
                                onClick = {
                                    scope.launch {
                                        container.settings.setFontName("")
                                        withContext(Dispatchers.IO) { FontStore.clear(context) }
                                        message = S.t("set.046")
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Anchored(ID_REMINDER, anchors) {
                Section(S.t("set.047"), null) {
                    ToggleRow(
                        title = S.t("set.049"),
                        subtitle = if (settings.reminderEnabled)
                            S.t("set.050") + timeText(settings.reminderHour, settings.reminderMinute)
                        else S.t("set.051"),
                        checked = settings.reminderEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                container.settings.setReminder(
                                    enabled,
                                    settings.reminderHour,
                                    settings.reminderMinute
                                )
                                WorkScheduler.scheduleReminder(
                                    context,
                                    enabled,
                                    settings.reminderHour,
                                    settings.reminderMinute
                                )
                            }
                            if (enabled && Build.VERSION.SDK_INT >= 33) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )
                    if (settings.reminderEnabled) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            REMINDER_TIMES.forEach { time ->
                                val hour = time.first
                                val minute = time.second
                                IknaChip(
                                    label = timeText(hour, minute),
                                    selected = settings.reminderHour == hour &&
                                        settings.reminderMinute == minute,
                                    onClick = {
                                        scope.launch {
                                            container.settings.setReminder(true, hour, minute)
                                            WorkScheduler.scheduleReminder(context, true, hour, minute)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Updates.
            //
            // The window that offers a new version can be waved away, and a
            // person who waved it away and then changed their mind had nowhere
            // to go: the offer was gone until the release after it. So the same
            // check lives here, on demand, and here the skipped version is
            // ignored -- pressing the button is the change of mind.
            Anchored(ID_UPDATE, anchors) {
                Section(S.t("set.138"), S.t("set.139")) {
                    Text(
                        text = S.t("upd.011") + installedVersion(context),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (settings.updateSkipped.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = S.t("upd.015") + settings.updateSkipped,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    ToggleRow(
                        title = S.t("upd.008"),
                        subtitle = S.t("upd.009"),
                        checked = settings.updateCheck,
                        onCheckedChange = { on ->
                            scope.launch { container.settings.setUpdateCheck(on) }
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    IknaWideButton(
                        label = if (updateBusy) S.t("upd.014") else S.t("upd.010"),
                        height = 52.dp,
                        enabled = !updateBusy,
                        onClick = {
                            scope.launch {
                                updateBusy = true
                                updateNote = null
                                val now = System.currentTimeMillis()
                                val release = UpdateCheck(
                                    installedVersion(context),
                                    has64Bit()
                                ).latest()
                                container.settings.markUpdateChecked(now)
                                updateBusy = false
                                updateFound = release
                                // A failed request and an up-to-date install both
                                // arrive as nothing, and the app cannot honestly
                                // tell them apart from here, so the line says both.
                                updateNote = if (release == null) S.t("upd.012") else null
                            }
                        }
                    )
                    val offered = updateFound
                    if (offered != null) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = S.t("upd.016") + offered.version,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = S.t("upd.002") + megabytes(offered.sizeBytes) +
                                " " + S.t("upd.003"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        if (updateDownload.phase == UpdatePhase.IDLE) {
                            IknaWideButton(
                                label = S.t("upd.005"),
                                height = 52.dp,
                                onClick = { updateDownload.start(offered) }
                            )
                        } else {
                            UpdateDownloadPanel(
                                state = updateDownload,
                                release = offered,
                                onBrowser = { openInBrowser(context, offered.apkUrl) }
                            )
                        }
                    }
                    val note = updateNote
                    if (note != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    // The way out when the check itself cannot work: no network
                    // here, a rate limit there, or simply wanting to read the
                    // whole release rather than its first paragraphs.
                    IknaTextButton(
                        label = S.t("upd.013"),
                        onClick = { openInBrowser(context, UpdateCheck.RELEASES_PAGE) },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Anchored(ID_DATA, anchors) {
                Section(
                    S.t("set.052"),
                    S.t("set.053")
                ) {
                    ToggleRow(
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
                                                SettingsBackup.encode(current)
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

            Anchored(ID_ADVANCED, anchors) {
                Section(S.t("set.063"), null) {
                    IknaTextButton(
                        label = if (advancedOpen) S.t("set.065") else S.t("set.066"),
                        onClick = { advancedOpen = !advancedOpen },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (advancedOpen) {
                        Spacer(Modifier.height(16.dp))
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
                                        withContext(Dispatchers.IO) {
                                            // Export first: if it fails, the wipe
                                            // still proceeds, because the user
                                            // asked for a wipe, not for a backup.
                                            runCatching { container.jsonExporter.export() }
                                            container.wipeDatabase()
                                            container.settings.clearAll()
                                        }
                                        // Restart the process: repositories, workers
                                        // and session state all outlive the tables
                                        // otherwise, and a half-empty app in memory
                                        // looks exactly like a bug.
                                        val restart = Intent(context, MainActivity::class.java)
                                            .addFlags(
                                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                                            )
                                        context.startActivity(restart)
                                        exitProcess(0)
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

            message?.let {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (confirmReset) {
        IknaDialog(
            title = S.t("set.078"),
            body = S.t("set.079"),
            confirmLabel = S.t("set.080"),
            confirmColor = MaterialTheme.colorScheme.onSurfaceVariant,
            onConfirm = {
                confirmReset = false
                scope.launch {
                    withContext(Dispatchers.IO) { container.learningRepository.resetProgress() }
                    message = S.t("set.081")
                }
            },
            dismissLabel = S.t("set.082"),
            onDismiss = { confirmReset = false }
        )
    }
}

/**
 * The pinned row of jumps.
 *
 * This is the whole answer to "menu or canvas": it gives the one thing a menu is
 * good for — naming what exists — without the tap, the back button, or the need
 * to remember which drawer a switch was filed in. Nothing is hidden behind it,
 * so scrolling still works for anyone who would rather scroll.
 */
@Composable
private fun JumpRow(
    activeId: String,
    animations: Boolean,
    onJump: (String) -> Unit
) {
    val row = rememberScrollState()

    // Where each label sits inside the row, measured the way the sections below
    // measure themselves. Centring needs the width of the label as well as its
    // position, so this stores the span rather than the left edge.
    val spots = remember { mutableStateMapOf<String, IntRange>() }
    var rowWidth by remember { mutableStateOf(0) }

    // The turn. Not a jump to the edge: the label of the section being read ends
    // up in the middle, which is the only position that reads as "you are here"
    // rather than "here is a list".
    LaunchedEffect(activeId, rowWidth, animations) {
        if (rowWidth == 0) return@LaunchedEffect
        val spot = spots[activeId] ?: return@LaunchedEffect
        val middle = spot.first + (spot.last - spot.first) / 2
        val target = (middle - rowWidth / 2).coerceIn(0, row.maxValue)
        if (animations) {
            row.animateScrollTo(
                target,
                animationSpec = tween(
                    durationMillis = Motion.sectionScrollDurationMillis,
                    easing = LinearOutSlowInEasing
                )
            )
        } else {
            row.scrollTo(target)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { rowWidth = it.size.width }
            .horizontalScroll(row)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        JUMPS.forEach { jump ->
            val here = jump.first == activeId
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.onGloballyPositioned { coords ->
                    val x = coords.parentLayoutCoordinates
                        ?.localPositionOf(coords, Offset.Zero)
                        ?.x
                        ?: 0f
                    val left = x.roundToInt()
                    spots[jump.first] = left..(left + coords.size.width)
                }
            ) {
                IknaTextButton(
                    label = S.t(jump.second),
                    onClick = { onJump(jump.first) },
                    color = if (here) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(3) { index ->
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .height(2.dp)
                                .background(
                                    if (here) MaterialTheme.colorScheme.primary.copy(
                                        alpha = if (index == 1) 1f else 0.52f
                                    ) else Color.Transparent
                                )
                        )
                    }
                }
            }
        }
    }
}

/** Remembers where a section starts, so the row above can scroll straight to it. */
@Composable
private fun Anchored(
    id: String,
    anchors: MutableMap<String, Int>,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                // positionInParent() reads better, but it is an extension that has
                // to be imported, and the import was missing — nothing here could
                // tell me that until a build reached this file. The parent's own
                // coordinates answer the same question using members only.
                val y = coords.parentLayoutCoordinates
                    ?.localPositionOf(coords, Offset.Zero)
                    ?.y
                    ?: 0f
                anchors[id] = y.roundToInt()
            }
    ) {
        content()
    }
}

/**
 * Minus, number, plus.
 *
 * A slider would be smaller on screen and worse in every other way: it demands a
 * precise drag to land on a number, it has no idea what a sensible step is, and
 * it invites fiddling with a figure that only matters in steps of five.
 */
@Composable
private fun Stepper(
    value: Int,
    enabled: Boolean,
    min: Int = MANUAL_LOAD_MIN,
    max: Int = MANUAL_LOAD_MAX,
    step: Int = MANUAL_LOAD_STEP,
    onChange: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IknaWideButton(
            label = "-",
            modifier = Modifier.width(72.dp),
            height = 52.dp,
            enabled = enabled && value > min,
            onClick = { onChange((value - step).coerceAtLeast(min)) }
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        )
        IknaWideButton(
            label = "+",
            modifier = Modifier.width(72.dp),
            height = 52.dp,
            enabled = enabled && value < max,
            onClick = { onChange((value + step).coerceAtMost(max)) }
        )
    }
}

@Composable
private fun Section(
    title: String,
    subtitle: String?,
    content: @Composable () -> Unit
) {
    Spacer(Modifier.height(24.dp))
    IknaRule(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))
    Spacer(Modifier.height(16.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium
    )
    if (subtitle != null) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(12.dp))
    content()
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // The row's own title is what the switch is called. A screen reader
        // treats the switch as a separate stop, so without this it would be
        // announced as an anonymous "switch, on" after the text has been read.
        IknaToggle(
            checked = checked,
            onCheckedChange = onCheckedChange,
            label = title
        )
    }
}

/**
 * Four colours, typed, with their contrast printed underneath.
 *
 * Not a colour wheel: a wheel is a circle, and this app does not draw circles.
 * More to the point, a wheel cannot tell you that the text colour you just
 * picked is unreadable on the background you picked two taps ago. These are the
 * standard contrast ratios; below 4.5 the app says so plainly and still applies
 * the colour, because it is the user's own screen and not a compliance audit.
 */
@Composable
private fun CustomColors(
    settings: IknaSettings,
    onChange: (Int, Int, Int, Int) -> Unit
) {
    val background = Color(settings.customBackground)
    val ink = Color(settings.customInk)
    val muted = Color(settings.customMuted)
    val accent = Color(settings.customAccent)

    ColorRow(S.t("set.083"), background) {
        onChange(it, settings.customInk, settings.customMuted, settings.customAccent)
    }
    ColorRow(S.t("set.084"), ink) {
        onChange(settings.customBackground, it, settings.customMuted, settings.customAccent)
    }
    ColorRow(S.t("set.085"), muted) {
        onChange(settings.customBackground, settings.customInk, it, settings.customAccent)
    }
    ColorRow(S.t("set.086"), accent) {
        onChange(settings.customBackground, settings.customInk, settings.customMuted, it)
    }

    Spacer(Modifier.height(12.dp))

    val inkRatio = contrastRatio(ink, background)
    val mutedRatio = contrastRatio(muted, background)
    val accentRatio = contrastRatio(accent, background)
    Text(
        text = S.t("set.087") + ratioText(inkRatio) +
            S.t("set.088") + ratioText(mutedRatio) +
            S.t("set.089") + ratioText(accentRatio),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (minOf(inkRatio, mutedRatio, accentRatio) < MIN_READABLE_CONTRAST) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = S.t("set.090"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun ColorRow(label: String, color: Color, onColor: (Int) -> Unit) {
    // Seeded from the stored colour and written back only when the text is a
    // complete one, so half-typed hex never repaints the whole app.
    var text by remember(color) { mutableStateOf(hexOf(color)) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IknaSwatch(color = color)
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        IknaHexField(
            value = text,
            onValueChange = { typed ->
                text = typed
                parseHexColor(typed)?.let { onColor(it.toArgb()) }
            },
            modifier = Modifier.width(104.dp)
        )
    }
}

/**
 * The palettes, shown as themselves.
 *
 * A list of names is not a choice of colours — nobody knows what "Слива" is until
 * they have already switched to it and switched back. Each tile is painted in the
 * palette it offers: its own background, the wordmark in its ink, and a bar of
 * accent next to a bar of muted, which is every colour the palette has.
 *
 * Selection is a heavier border rather than a tint or a tick, for the same reason
 * everything else here is: a tick would have to be drawn in some colour, and on a
 * tile whose whole point is its own colours there is no colour left to use.
 */
@Composable
private fun PaletteTiles(
    selectedId: String,
    light: Boolean,
    onPick: (String) -> Unit
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val line = MaterialTheme.colorScheme.outline
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IknaPalettes.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { spec ->
                    val p = spec.palette(light)
                    val selected = spec.id == selectedId
                    // The tap zone used to be this whole column: a third of
                    // the row wide, tile plus gap plus name, including all the
                    // empty space to the right of a name as short as "НОЛЬ". A
                    // tap aimed at nothing repainted the entire app, which is
                    // the one change here nobody asks for by accident. Only the
                    // tile and its own name answer now.
                    Column(modifier = Modifier.weight(1f)) {
                        Column(
                            modifier = Modifier
                                .clickable(
                                    role = Role.RadioButton,
                                    onClickLabel = S.t(spec.nameKey),
                                    onClick = { onPick(spec.id) }
                                )
                                .fillMaxWidth()
                                .height(64.dp)
                                .background(p.background)
                                .border(if (selected) 2.dp else 1.dp, if (selected) ink else line)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = WORDMARK,
                                style = MaterialTheme.typography.bodySmall,
                                color = p.ink,
                                maxLines = 1
                            )
                            Spacer(Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(
                                    modifier = Modifier
                                        .weight(2f)
                                        .height(8.dp)
                                        .background(p.accent)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(8.dp)
                                        .background(p.muted)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = S.t(spec.nameKey),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) ink else muted,
                            maxLines = 1,
                            // The name is part of the target, the space beside it
                            // is not. A Text is exactly as wide as its text.
                            modifier = Modifier.clickable { onPick(spec.id) }
                        )
                    }
                }
                // A last row of two must not stretch its tiles to the width of
                // three, or the grid stops being a grid.
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The one word in the app that is never translated. */
private const val WORDMARK = "ikna"

private const val ID_LOAD = "load"
private const val ID_LOOK = "look"
private const val ID_LANGUAGE = "language"
private const val ID_SPEECH = "speech"
private const val ID_FONT = "font"
private const val ID_REMINDER = "reminder"
private const val ID_DATA = "data"
private const val ID_UPDATE = "update"
private const val ID_ADVANCED = "advanced"

/**
 * Order is by how often a thing is actually touched, not by how important it
 * sounds. The load and the look are changed in the first week; the wipe button
 * is changed once, by someone who came looking for it.
 */
private val JUMPS = listOf(
    ID_LOAD to "set.091",
    ID_LOOK to "set.092",
    ID_LANGUAGE to "set.093",
    ID_SPEECH to "set.094",
    ID_FONT to "set.095",
    ID_REMINDER to "set.096",
    ID_DATA to "set.097",
    ID_UPDATE to "set.140",
    ID_ADVANCED to "set.098"
)

/**
 * Interface languages. English and Polish are here because the app is used in
 * both directions: the person learning Polish from Russian today is the person
 * learning English tomorrow, and an interface stuck in one language turns the
 * other deck into homework in translation.
 */
private val LANGUAGES = listOf(
    LANGUAGE_SYSTEM, "ru", "en", "pl", "es", "fr", "de"
)

/**
 * How many language chips are shown before the rest go behind one tap.
 *
 * Six is three rows of two, which is the most this section can hold and still
 * read as a setting rather than as a list of everything the app was ever
 * translated into.
 */
private const val LANGUAGE_FOLD = 6

/**
 * Language names stay in their own language: a person looking for Polish looks
 * for "POLSKI", not for its translation. Only "as in the system" is translated,
 * because it is a sentence about the phone rather than a name.
 */
private fun languageLabel(code: String): String = when (code) {
    LANGUAGE_SYSTEM -> S.t("set.099")
    "ru" -> S.t("set.100")
    "en" -> "ENGLISH"
    "pl" -> "POLSKI"
    "es" -> "ESPAÑOL"
    "fr" -> "FRANÇAIS"
    "de" -> "DEUTSCH"
    else -> code.uppercase(Locale.getDefault())
}

private val REMINDER_TIMES = listOf(9 to 0, 13 to 0, 20 to 0, 22 to 0)

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.DARK -> S.t("set.101")
    ThemeMode.LIGHT -> S.t("set.102")
    // Reuses the language section's "как в системе": it is the same promise about
    // the same phone setting, and two different wordings for it would read as two
    // different behaviours.
    ThemeMode.SYSTEM -> S.t("set.099")
    ThemeMode.CUSTOM -> S.t("set.103")
}

private fun timeText(hour: Int, minute: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", hour, minute)

private fun langLabel(lang: String): String = when (lang.substringBefore('-').lowercase()) {
    "pl" -> S.t("set.104")
    "ru" -> S.t("set.105")
    "en" -> S.t("set.106")
    "de" -> S.t("set.107")
    "es" -> S.t("set.108")
    "fr" -> S.t("set.109")
    else -> lang.uppercase()
}

/**
 * Opens the phone's speech settings. Not every ROM has this screen, so the
 * caller is told whether it worked instead of the app throwing.
 */
private fun openTtsSettings(context: Context): Boolean = runCatching {
    context.startActivity(
        Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    true
}.getOrDefault(false)

/** Asks the installed engine to fetch its voice data. Same caveat as above. */
private fun installVoices(context: Context): Boolean = runCatching {
    context.startActivity(
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    true
}.getOrDefault(false)

/** The picked file's own name, for showing which font is in use. */
private fun displayNameOf(context: Context, uri: Uri): String {
    runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    val name = cursor.getString(index)
                    if (!name.isNullOrBlank()) return name
                }
            }
    }
    return uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { S.t("set.110") }
}
