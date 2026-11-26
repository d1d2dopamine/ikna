package dev.ikna.ui.settings

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ikna.AppContainer
import dev.ikna.data.prefs.FontMode
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaChip
import dev.ikna.ui.theme.IknaWideButton
import dev.ikna.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

private val REMINDER_TIMES = listOf(9 to 0, 13 to 0, 20 to 0, 22 to 0)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsFontSectionContent(
    container: AppContainer,
    settings: IknaSettings,
    busy: Boolean,
    scope: CoroutineScope,
    onPickFont: () -> Unit
) {
    Text(
        text = when (settings.fontMode) {
            FontMode.GEOLOGICA -> S.t("set.042")
            FontMode.CUSTOM -> S.t("set.043") + settings.fontName.uppercase()
        },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(12.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IknaChip(
            label = S.t("set.150"),
            selected = settings.fontMode == FontMode.GEOLOGICA,
            onClick = { scope.launch { container.settings.setFontMode(FontMode.GEOLOGICA) } }
        )
        if (settings.fontName.isNotBlank()) {
            IknaChip(
                label = settings.fontName.uppercase(),
                selected = settings.fontMode == FontMode.CUSTOM,
                onClick = { scope.launch { container.settings.setFontMode(FontMode.CUSTOM) } }
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    IknaWideButton(
        label = S.t("set.044"),
        modifier = Modifier.fillMaxWidth(),
        height = 52.dp,
        enabled = !busy,
        onClick = onPickFont
    )
}

@Composable
internal fun SettingsReminderSectionContent(
    context: Context,
    container: AppContainer,
    settings: IknaSettings,
    scope: CoroutineScope,
    onRequestNotificationPermission: (String) -> Unit
) {
    IknaSettingsToggleRow(
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
                    !container.isDeveloperMode && enabled,
                    settings.reminderHour,
                    settings.reminderMinute
                )
            }
            if (enabled && !container.isDeveloperMode && Build.VERSION.SDK_INT >= 33) {
                onRequestNotificationPermission(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    )
    if (settings.reminderEnabled) {
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            REMINDER_TIMES.forEach { (hour, minute) ->
                IknaChip(
                    label = timeText(hour, minute),
                    selected = settings.reminderHour == hour && settings.reminderMinute == minute,
                    onClick = {
                        scope.launch {
                            container.settings.setReminder(true, hour, minute)
                            WorkScheduler.scheduleReminder(
                                context,
                                !container.isDeveloperMode,
                                hour,
                                minute
                            )
                        }
                    }
                )
            }
        }
    }
}

internal fun timeText(hour: Int, minute: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", hour, minute)

internal fun displayNameOf(context: Context, uri: Uri): String {
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
