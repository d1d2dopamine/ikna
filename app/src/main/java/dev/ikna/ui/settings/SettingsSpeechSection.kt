package dev.ikna.ui.settings

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ikna.AppContainer
import dev.ikna.audio.SpeakerStatus
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.IknaWideButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun SettingsSpeechSectionContent(
    context: Context,
    container: AppContainer,
    settings: IknaSettings,
    speechStatus: SpeakerStatus,
    scope: CoroutineScope,
    onOpenVoice: () -> Unit,
    onMessage: (String) -> Unit
) {
    IknaSettingsToggleRow(
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

    Spacer(Modifier.height(12.dp))
    IknaWideButton(
        label = S.t("voice.001"),
        onClick = onOpenVoice
    )

    if (settings.speechEnabled) {
        Spacer(Modifier.height(12.dp))
        IknaSettingsToggleRow(
            title = S.t("set.130"),
            subtitle = S.t("set.131"),
            checked = settings.phoneVoice,
            onCheckedChange = { on ->
                scope.launch {
                    container.settings.setPhoneVoice(on)
                    container.speaker.setPhoneVoice(on)
                    if (!on) container.speaker.stop()
                }
            }
        )
        IknaSettingsToggleRow(
            title = S.t("set.132"),
            subtitle = if (settings.autoSpeakEvery) S.t("set.133") else S.t("set.134"),
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
                            onMessage(S.t("set.033"))
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
                            onMessage(S.t("set.036"))
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
                            onMessage(S.t("set.033"))
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

internal fun openTtsSettings(context: Context): Boolean = runCatching {
    context.startActivity(
        Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    true
}.getOrDefault(false)

internal fun installVoices(context: Context): Boolean = runCatching {
    context.startActivity(
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    true
}.getOrDefault(false)
