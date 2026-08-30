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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.LANGUAGE_SYSTEM
import dev.ikna.data.prefs.ThemeMode
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaPalette
import dev.ikna.ui.theme.IknaPalettes
import kotlinx.coroutines.launch

/**
 * Application settings.
 *
 * Speech and Anki import appear here with their real headings and a line saying
 * they are not available on the desktop yet. Leaving them out entirely would
 * read as "this build has fewer features and does not say which"; saying so is
 * shorter than being asked.
 */
@Composable
fun SettingsPane(
    container: DesktopContainer,
    settings: IknaSettings,
    palette: IknaPalette
) {
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(40.dp)
            .widthIn(max = 720.dp)
    ) {
        Text(S.t("set.012"), color = palette.ink, fontSize = 20.sp)

        Spacer(Modifier.height(28.dp))
        SectionTitle(S.t("set.091"), palette)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IknaButton(S.t("set.015"), palette, filled = settings.autoLoad) {
                scope.launch { container.settings.setAutoLoad(true) }
            }
            IknaButton(S.t("set.016"), palette, filled = !settings.autoLoad) {
                scope.launch { container.settings.setAutoLoad(false) }
            }
        }
        if (!settings.autoLoad) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IknaButton("-5", palette) {
                    scope.launch {
                        container.settings.setManualLoad(maxOf(5, settings.manualLoad - 5))
                    }
                }
                Box(Modifier.padding(horizontal = 8.dp, vertical = 9.dp)) {
                    Text(
                        settings.manualLoad.toString() + "  " + S.t("set.018"),
                        color = palette.ink,
                        fontSize = 11.sp
                    )
                }
                IknaButton("+5", palette) {
                    scope.launch {
                        container.settings.setManualLoad(settings.manualLoad + 5)
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        SectionTitle(S.t("set.122"), palette)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IknaButton(S.t("set.101"), palette, filled = settings.theme == ThemeMode.DARK) {
                scope.launch { container.settings.setTheme(ThemeMode.DARK) }
            }
            IknaButton(S.t("set.102"), palette, filled = settings.theme == ThemeMode.LIGHT) {
                scope.launch { container.settings.setTheme(ThemeMode.LIGHT) }
            }
        }

        Spacer(Modifier.height(28.dp))
        SectionTitle(S.t("set.114"), palette)
        Spacer(Modifier.height(10.dp))
        for (row in IknaPalettes.chunked(4)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (spec in row) {
                    IknaButton(
                        S.t(spec.nameKey),
                        palette,
                        filled = settings.paletteId == spec.id
                    ) {
                        scope.launch { container.settings.setPalette(spec.id) }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(22.dp))
        SectionTitle(S.t("set.092"), palette)
        Spacer(Modifier.height(10.dp))
        IknaButton(S.t("set.020"), palette, filled = settings.animations) {
            scope.launch { container.settings.setAnimations(!settings.animations) }
        }

        Spacer(Modifier.height(28.dp))
        SectionTitle(S.t("set.093"), palette)
        Spacer(Modifier.height(10.dp))
        val languages = listOf(
            LANGUAGE_SYSTEM to "set.099",
            "ru" to "set.105",
            "en" to "set.106",
            "pl" to "set.104",
            "es" to "set.108",
            "fr" to "set.109",
            "de" to "set.107"
        )
        for (row in languages.chunked(4)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (pair in row) {
                    IknaButton(
                        S.t(pair.second),
                        palette,
                        filled = settings.language == pair.first
                    ) {
                        scope.launch { container.settings.setLanguage(pair.first) }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(28.dp))
        SectionTitle(S.t("set.094"), palette)
        Spacer(Modifier.height(6.dp))
        Text(S.t("pc.001"), color = palette.muted, fontSize = 11.sp)

        Spacer(Modifier.height(22.dp))
        SectionTitle(S.t("anki.001"), palette)
        Spacer(Modifier.height(6.dp))
        Text(S.t("pc.001"), color = palette.muted, fontSize = 11.sp)

        Spacer(Modifier.height(40.dp))
    }
}
