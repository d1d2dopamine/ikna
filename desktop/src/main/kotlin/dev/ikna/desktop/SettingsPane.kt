package dev.ikna.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import dev.ikna.data.prefs.FontStore
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.LANGUAGE_SYSTEM
import dev.ikna.data.prefs.MANUAL_LOAD_MAX
import dev.ikna.data.prefs.MANUAL_LOAD_MIN
import dev.ikna.data.prefs.MANUAL_LOAD_STEP
import dev.ikna.data.prefs.ThemeMode
import dev.ikna.data.update.UpdateCheck
import dev.ikna.ui.settings.IknaPaletteTiles
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaChip
import dev.ikna.ui.theme.IknaHexField
import dev.ikna.ui.theme.IknaPalette
import dev.ikna.ui.theme.IknaSwatch
import dev.ikna.ui.theme.IknaToggle
import dev.ikna.ui.theme.MIN_READABLE_CONTRAST
import dev.ikna.ui.theme.contrastRatio
import dev.ikna.ui.theme.hexOf
import dev.ikna.ui.theme.isLight
import dev.ikna.ui.theme.parseHexColor
import dev.ikna.ui.theme.ratioText
import kotlinx.coroutines.launch
import java.io.File

/**
 * Application settings.
 *
 * The same controls the phone has, in the same order, drawn by the same code
 * wherever there is code to share: the palettes are the phone's own tiles, the
 * lighting is the phone's own chips, and the custom colours are the phone's own
 * swatch-and-hex rows. A desktop settings screen that offered a list of palette
 * names instead of the palettes would be a different app wearing the same name.
 *
 * Speech and Anki import appear with their real headings and a line saying they
 * are not available on the desktop yet. Leaving them out entirely would read as
 * "this build has fewer features and does not say which".
 */
@Composable
fun SettingsPane(
    container: DesktopContainer,
    settings: IknaSettings,
    palette: IknaPalette,
    onBack: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    // Every setting is written through here.
    //
    // Storing a preference is the one thing this screen does, and it is the one
    // thing that reaches outside the process: a file, a folder, a filesystem
    // that may be read-only, locked by a backup agent, or -- as it turned out --
    // served by a runtime image with no sun.misc.Unsafe in it. A raw
    // scope.launch turns any of that into an uncaught exception on the UI
    // thread. The choice is either written down or it is not, and the log says
    // which; the window stays.
    val save: (suspend () -> Unit) -> Unit = { block ->
        scope.launch {
            runCatching { block() }
                .onFailure { error -> logLine("settings write failed: " + error) }
        }
    }

    // Notes and armed states for the sections that touch the disk.
    var dataNote by remember { mutableStateOf<String?>(null) }
    var updateNote by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var advancedOpen by remember { mutableStateOf(false) }
    var resetAsking by remember { mutableStateOf(false) }
    var wipeArmed by remember { mutableStateOf(false) }
    var diagOpen by remember { mutableStateOf(false) }
    var diagText by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(40.dp)
            .widthIn(max = 720.dp)
    ) {
        Text(
            text = S.t("set.012"),
            style = MaterialTheme.typography.titleLarge,
            color = palette.ink
        )

        // -- load ------------------------------------------------------------
        Spacer(Modifier.height(28.dp))
        SectionTitle(S.t("set.091"), palette)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IknaChip(
                label = S.t("set.015"),
                selected = settings.autoLoad,
                onClick = { save { container.settings.setAutoLoad(true) } }
            )
            IknaChip(
                label = S.t("set.016"),
                selected = !settings.autoLoad,
                onClick = { save { container.settings.setAutoLoad(false) } }
            )
        }
        if (!settings.autoLoad) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IknaButton("-", palette, enabled = settings.manualLoad > MANUAL_LOAD_MIN) {
                    save {
                        container.settings.setManualLoad(
                            (settings.manualLoad - MANUAL_LOAD_STEP).coerceAtLeast(MANUAL_LOAD_MIN)
                        )
                    }
                }
                Text(
                    text = settings.manualLoad.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.ink,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                IknaButton("+", palette, enabled = settings.manualLoad < MANUAL_LOAD_MAX) {
                    save {
                        container.settings.setManualLoad(
                            (settings.manualLoad + MANUAL_LOAD_STEP).coerceAtMost(MANUAL_LOAD_MAX)
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    text = S.t("set.018"),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.muted
                )
            }
        }

        // -- look -------------------------------------------------------------
        Spacer(Modifier.height(30.dp))
        SectionTitle(S.t("set.019"), palette)
        Spacer(Modifier.height(14.dp))
        Text(
            text = S.t("set.114"),
            style = MaterialTheme.typography.labelMedium,
            color = palette.muted
        )
        Spacer(Modifier.height(12.dp))
        // Four to a row rather than the phone's three: the window is wider, and
        // twelve tiles at three to a row would need scrolling to be compared,
        // which is the one thing a grid of colours exists to avoid.
        IknaPaletteTiles(
            selectedId = settings.paletteId,
            // Drawn in the lighting the window is in right now, read off the
            // background rather than asked of the system: with a custom scheme
            // the two can disagree, and what matters is what the eye is
            // currently adapted to.
            light = isLight(palette.background),
            columns = 4,
            onPick = { id -> save { container.settings.setPalette(id) } }
        )

        Spacer(Modifier.height(22.dp))
        Text(
            text = S.t("set.122"),
            style = MaterialTheme.typography.labelMedium,
            color = palette.muted
        )
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { mode ->
                        IknaChip(
                            label = themeLabel(mode),
                            selected = settings.theme == mode,
                            modifier = Modifier.width(180.dp),
                            onClick = { save { container.settings.setTheme(mode) } }
                        )
                    }
                }
            }
        }

        if (settings.theme == ThemeMode.CUSTOM) {
            Spacer(Modifier.height(16.dp))
            CustomColors(settings = settings, palette = palette) { bg, ink, muted, accent ->
                save { container.settings.setCustomColors(bg, ink, muted, accent) }
            }
        }

        Spacer(Modifier.height(18.dp))
        ToggleRow(S.t("set.020"), settings.animations, palette) {
            save { container.settings.setAnimations(it) }
        }
        ToggleRow(S.t("bar.001"), settings.showWordmark, palette) {
            save { container.settings.setShowWordmark(it) }
        }

        // -- language ----------------------------------------------------------
        Spacer(Modifier.height(30.dp))
        SectionTitle(S.t("set.024"), palette)
        Spacer(Modifier.height(12.dp))
        val languages = listOf(
            LANGUAGE_SYSTEM to "set.099",
            "ru" to "set.105",
            "en" to "set.106",
            "pl" to "set.104",
            "es" to "set.108",
            "fr" to "set.109",
            "de" to "set.107"
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            languages.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { pair ->
                        IknaChip(
                            label = S.t(pair.second),
                            selected = settings.language == pair.first,
                            modifier = Modifier.width(140.dp),
                            onClick = { save { container.settings.setLanguage(pair.first) } }
                        )
                    }
                }
            }
        }

        // -- font ------------------------------------------------------------
        Spacer(Modifier.height(30.dp))
        SectionTitle(S.t("set.040"), palette)
        Spacer(Modifier.height(8.dp))
        Text(
            text = S.t("set.041"),
            style = MaterialTheme.typography.labelMedium,
            color = palette.muted
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (settings.fontName.isEmpty()) S.t("set.042")
            else S.t("set.043") + settings.fontName,
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IknaButton(label = S.t("set.044"), palette = palette) {
                val picked = pickFileForRead(S.t("set.040"))
                if (picked != null) {
                    val problem = runCatching {
                        picked.inputStream().use { stream -> FontStore.install(stream) }
                    }.getOrElse { S.t("set.057") }
                    if (problem == null) {
                        save { container.settings.setFontName(picked.name) }
                        dataNote = null
                    } else {
                        dataNote = problem
                    }
                }
            }
            IknaButton(label = S.t("set.045"), palette = palette) {
                FontStore.clear()
                save { container.settings.setFontName("") }
                dataNote = S.t("set.046")
            }
        }

        // -- updates ---------------------------------------------------------
        Spacer(Modifier.height(30.dp))
        SectionTitle(S.t("set.138"), palette)
        Spacer(Modifier.height(8.dp))
        Text(
            text = S.t("set.139"),
            style = MaterialTheme.typography.labelMedium,
            color = palette.muted
        )
        Spacer(Modifier.height(10.dp))
        ToggleRow(S.t("upd.008"), settings.updateCheck, palette) {
            save { container.settings.setUpdateCheck(it) }
        }
        Text(
            text = S.t("upd.009"),
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = S.t("upd.011") + APP_VERSION,
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted
        )
        if (settings.updateSkipped.isNotEmpty()) {
            Text(
                text = S.t("upd.015") + settings.updateSkipped,
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IknaButton(
                label = if (checking) S.t("upd.014") else S.t("upd.010"),
                palette = palette,
                enabled = !checking
            ) {
                scope.launch {
                    checking = true
                    val release = runCatching {
                        UpdateCheck(APP_VERSION, true).latest()
                    }.getOrNull()
                    checking = false
                    save { container.settings.markUpdateChecked(System.currentTimeMillis()) }
                    updateNote = if (release == null) S.t("upd.012")
                    else S.t("upd.016") + release.version
                }
            }
            IknaButton(label = S.t("upd.013"), palette = palette) {
                openInBrowser(UpdateCheck.RELEASES_PAGE)
            }
        }
        updateNote?.let { line ->
            Spacer(Modifier.height(10.dp))
            Text(
                text = line,
                style = MaterialTheme.typography.labelMedium,
                color = palette.ink
            )
        }

        // -- data ------------------------------------------------------------
        Spacer(Modifier.height(30.dp))
        SectionTitle(S.t("set.052"), palette)
        Spacer(Modifier.height(8.dp))
        Text(
            text = S.t("set.053"),
            style = MaterialTheme.typography.labelMedium,
            color = palette.muted
        )
        Spacer(Modifier.height(10.dp))
        ToggleRow(S.t("set.054"), settings.autoExport, palette) {
            save { container.settings.setAutoExport(it) }
        }
        Text(
            text = container.home.absolutePath,
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IknaButton(label = S.t("set.056"), palette = palette) {
                scope.launch {
                    val target = File(container.home, "export")
                    val written = runCatching {
                        target.mkdirs()
                        var count = 0
                        container.deckRepository.decks().forEach { deck ->
                            val body = container.deckRepository.exportText(deck.id)
                            if (body.isNotBlank()) {
                                File(target, fileNameFor(deck.title)).writeText(body)
                                count += 1
                            }
                        }
                        count
                    }.getOrNull()
                    dataNote = when {
                        written == null -> S.t("set.057")
                        written == 0 -> S.t("set.058")
                        else -> target.absolutePath
                    }
                }
            }
            IknaButton(label = S.t("pc.011"), palette = palette) {
                openFolder(container.home)
            }
        }

        val hidden = settings.suppressed.split(",").count { it.isNotBlank() }
        if (hidden > 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = S.t("set.135") + hidden,
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted
            )
            Spacer(Modifier.height(8.dp))
            IknaButton(label = S.t("set.136"), palette = palette) {
                save { container.settings.clearSuppressed() }
                dataNote = S.t("set.137")
            }
        }

        // -- diagnostics -----------------------------------------------------
        Spacer(Modifier.height(22.dp))
        SectionTitle(S.t("diag.001"), palette)
        Spacer(Modifier.height(8.dp))
        Text(
            text = S.t("diag.002"),
            style = MaterialTheme.typography.labelMedium,
            color = palette.muted
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IknaButton(
                label = if (diagOpen) S.t("diag.004") else S.t("diag.003"),
                palette = palette
            ) {
                if (diagOpen) {
                    diagOpen = false
                } else {
                    diagOpen = true
                    diagText = S.t("diag.005")
                    scope.launch {
                        diagText = runCatching { diagnosticsText(container) }
                            .getOrElse { S.t("diag.008") }
                    }
                }
            }
            if (diagOpen) {
                IknaButton(label = S.t("diag.006"), palette = palette) {
                    writeClipboardText(diagText)
                    dataNote = S.t("diag.007")
                }
            }
        }
        if (diagOpen) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = diagText,
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted
            )
        }

        // -- rare ------------------------------------------------------------
        Spacer(Modifier.height(30.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(S.t("set.063"), palette)
            Spacer(Modifier.width(12.dp))
            IknaButton(
                label = if (advancedOpen) S.t("set.065") else S.t("set.066"),
                palette = palette
            ) { advancedOpen = !advancedOpen }
        }

        if (advancedOpen) {
            Spacer(Modifier.height(12.dp))
            IknaButton(label = S.t("set.067"), palette = palette) {
                scope.launch {
                    runCatching { container.componentRepository.rebuildFromReviews() }
                        .onFailure { error -> logLine("rebuild failed: " + error) }
                    dataNote = S.t("set.068")
                }
            }

            Spacer(Modifier.height(18.dp))
            if (!resetAsking) {
                IknaButton(label = S.t("set.070"), palette = palette) { resetAsking = true }
            } else {
                Text(
                    text = S.t("set.078"),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.ink
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = S.t("set.079"),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IknaButton(label = S.t("set.080"), palette = palette, filled = true) {
                        scope.launch {
                            runCatching { container.learningRepository.resetProgress() }
                                .onFailure { error -> logLine("reset failed: " + error) }
                            resetAsking = false
                            dataNote = S.t("set.081")
                        }
                    }
                    IknaButton(label = S.t("set.082"), palette = palette) { resetAsking = false }
                }
            }
        }

        if (advancedOpen) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = S.t("set.071"),
                style = MaterialTheme.typography.labelMedium,
                color = palette.ink
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = S.t("set.072"),
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted
            )
            Spacer(Modifier.height(10.dp))
            IknaButton(
                label = if (wipeArmed) S.t("set.073") else S.t("set.074"),
                palette = palette
            ) {
                if (!wipeArmed) {
                    wipeArmed = true
                    dataNote = S.t("set.075")
                } else {
                    scope.launch {
                        runCatching {
                            val target = File(container.home, "export")
                            target.mkdirs()
                            container.deckRepository.decks().forEach { deck ->
                                val body = container.deckRepository.exportText(deck.id)
                                if (body.isNotBlank()) {
                                    File(target, fileNameFor(deck.title)).writeText(body)
                                }
                            }
                            container.deckRepository.decks().forEach { deck ->
                                container.deckRepository.delete(deck.id)
                            }
                            container.componentRepository.clearAll()
                            container.learningRepository.invalidatePlan()
                            container.settings.clearAll()
                        }.onFailure { error -> logLine("wipe failed: " + error) }
                        wipeArmed = false
                        dataNote = null
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (wipeArmed) S.t("set.076") else S.t("set.077"),
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted
            )
        }

        dataNote?.let { line ->
            Spacer(Modifier.height(16.dp))
            Text(
                text = line,
                style = MaterialTheme.typography.labelMedium,
                color = palette.ink
            )
        }

        // -- keyboard --------------------------------------------------------
        Spacer(Modifier.height(30.dp))
        SectionTitle(S.t("pc.010"), palette)
        Spacer(Modifier.height(8.dp))
        Text(
            text = S.t("pc.002"),
            style = MaterialTheme.typography.labelMedium,
            color = palette.muted
        )

        // -- not here yet --------------------------------------------------------
        Spacer(Modifier.height(30.dp))
        SectionTitle(S.t("set.094"), palette)
        Spacer(Modifier.height(8.dp))
        Text(
            text = S.t("pc.001"),
            style = MaterialTheme.typography.labelMedium,
            color = palette.muted
        )

        Spacer(Modifier.height(22.dp))
        SectionTitle(S.t("anki.001"), palette)
        Spacer(Modifier.height(8.dp))
        Text(
            text = S.t("pc.001"),
            style = MaterialTheme.typography.labelMedium,
            color = palette.muted
        )

        Spacer(Modifier.height(40.dp))
    }
}

/** A name on the left, a switch on the right, the whole row a click target. */
@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    palette: IknaPalette,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.ink,
            modifier = Modifier.weight(1f)
        )
        IknaToggle(checked = checked, onCheckedChange = onCheckedChange, label = title)
    }
}

/** The four colours of a custom scheme, with the contrast they produce. */
@Composable
private fun CustomColors(
    settings: IknaSettings,
    palette: IknaPalette,
    onChange: (Int, Int, Int, Int) -> Unit
) {
    val background = Color(settings.customBackground)
    val ink = Color(settings.customInk)
    val muted = Color(settings.customMuted)
    val accent = Color(settings.customAccent)

    ColorRow(S.t("set.083"), background, palette) {
        onChange(it, settings.customInk, settings.customMuted, settings.customAccent)
    }
    ColorRow(S.t("set.084"), ink, palette) {
        onChange(settings.customBackground, it, settings.customMuted, settings.customAccent)
    }
    ColorRow(S.t("set.085"), muted, palette) {
        onChange(settings.customBackground, settings.customInk, it, settings.customAccent)
    }
    ColorRow(S.t("set.086"), accent, palette) {
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
        color = palette.muted
    )

    if (minOf(inkRatio, mutedRatio, accentRatio) < MIN_READABLE_CONTRAST) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = S.t("set.090"),
            style = MaterialTheme.typography.bodySmall,
            color = palette.accent
        )
    }
}

@Composable
private fun ColorRow(
    label: String,
    color: Color,
    palette: IknaPalette,
    onColor: (Int) -> Unit
) {
    // Seeded from the stored colour and written back only when the text is a
    // complete one, so half-typed hex never repaints the whole app.
    var text by remember(color) { mutableStateOf(hexOf(color)) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IknaSwatch(color = color)
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = palette.muted,
            modifier = Modifier.weight(1f)
        )
        Box(Modifier.width(104.dp)) {
            IknaHexField(
                value = text,
                onValueChange = { typed ->
                    text = typed
                    parseHexColor(typed)?.let { onColor(it.toArgb()) }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.DARK -> S.t("set.101")
    ThemeMode.LIGHT -> S.t("set.102")
    ThemeMode.SYSTEM -> S.t("set.099")
    ThemeMode.CUSTOM -> S.t("set.103")
}

/**
 * What this build calls itself.
 *
 * The phone reads its version out of the package manager. There is no package
 * manager here, so the number lives next to the code that shows it and next to
 * the code that asks the release feed whether anything newer exists.
 */
private const val APP_VERSION = "0.10.0 press"

/**
 * The technical summary behind the diagnostics button.
 *
 * Deliberately dull: versions, sizes, counts. No deck titles, no card text, no
 * answer history -- the heading promises that and the promise has to hold, since
 * the whole point is that a person can paste this into a bug report without
 * reading it first.
 */
private suspend fun diagnosticsText(container: DesktopContainer): String {
    val decks = container.deckRepository.decks()
    val known = runCatching { container.componentRepository.knownWordCount() }.getOrNull()
    val runtime = Runtime.getRuntime()
    val megabyte = 1024L * 1024L
    return buildString {
        appendLine("ikna " + APP_VERSION + " desktop")
        appendLine(
            "os: " + System.getProperty("os.name") + " " +
                System.getProperty("os.version") + " " + System.getProperty("os.arch")
        )
        appendLine(
            "java: " + System.getProperty("java.version") + " " +
                System.getProperty("java.vm.name")
        )
        appendLine("home: " + container.home.absolutePath)
        appendLine("decks: " + decks.size)
        appendLine("cards: " + decks.sumOf { it.total })
        appendLine("introduced: " + decks.sumOf { it.introduced })
        appendLine("known: " + (known ?: -1))
        appendLine(
            "heap: " + (runtime.totalMemory() / megabyte) + " of " +
                (runtime.maxMemory() / megabyte) + " mb"
        )
        append("font: " + (if (FontStore.exists()) "file" else "system"))
    }
}
