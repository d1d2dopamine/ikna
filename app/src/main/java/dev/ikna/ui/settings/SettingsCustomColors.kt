package dev.ikna.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.ThemeMode
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaHexField
import dev.ikna.ui.theme.IknaSwatch
import dev.ikna.ui.theme.MIN_READABLE_CONTRAST
import dev.ikna.ui.theme.contrastRatio
import dev.ikna.ui.theme.hexOf
import dev.ikna.ui.theme.parseHexColor
import dev.ikna.ui.theme.ratioText

@Composable
internal fun SettingsCustomColors(
    settings: IknaSettings,
    onChange: (Int, Int, Int, Int) -> Unit
) {
    val background = Color(settings.customBackground)
    val ink = Color(settings.customInk)
    val muted = Color(settings.customMuted)
    val accent = Color(settings.customAccent)

    SettingsColorRow(S.t("set.083"), background) {
        onChange(it, settings.customInk, settings.customMuted, settings.customAccent)
    }
    SettingsColorRow(S.t("set.084"), ink) {
        onChange(settings.customBackground, it, settings.customMuted, settings.customAccent)
    }
    SettingsColorRow(S.t("set.085"), muted) {
        onChange(settings.customBackground, settings.customInk, it, settings.customAccent)
    }
    SettingsColorRow(S.t("set.086"), accent) {
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
private fun SettingsColorRow(label: String, color: Color, onColor: (Int) -> Unit) {
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

internal fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.DARK -> S.t("set.101")
    ThemeMode.GREY -> S.t("set.102")
    ThemeMode.SYSTEM -> S.t("set.099")
    ThemeMode.CUSTOM -> S.t("set.103")
}
