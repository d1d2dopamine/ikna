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
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.LANGUAGE_SYSTEM
import dev.ikna.data.prefs.MANUAL_LOAD_MAX
import dev.ikna.data.prefs.MANUAL_LOAD_MIN
import dev.ikna.data.prefs.MANUAL_LOAD_STEP
import dev.ikna.data.prefs.ThemeMode
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
    palette: IknaPalette
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
