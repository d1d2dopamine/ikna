package dev.ikna.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ikna.AppContainer
import dev.ikna.MainActivity
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.LoadPreset
import dev.ikna.data.prefs.ThemeMode
import dev.ikna.ui.theme.IknaAgain
import dev.ikna.ui.theme.IknaDanger
import dev.ikna.ui.theme.IknaMuted
import dev.ikna.ui.theme.IknaWideButton
import dev.ikna.work.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Settings.
 *
 * Every switch here is either reversible or explains its consequence in the same
 * sentence. The load switch is the important one: it is the only place where the
 * user can tell the app "this is too much", instead of the app finding out by
 * being abandoned.
 */
@Composable
fun SettingsScreen(
    container: AppContainer,
    settings: IknaSettings,
    onOpenDebug: () -> Unit
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

    // The measured norm is shown rather than asked for: the number on the "Авто"
    // chip is what the app has decided the day looks like, so the setting stays
    // readable without turning into another decision.
    var measuredNorm by remember { mutableStateOf(0) }
    var normMeasured by remember { mutableStateOf(true) }
    LaunchedEffect(settings.autoLoad, settings.load) {
        measuredNorm = container.learningRepository.currentDailyTarget()
        normMeasured = container.learningRepository.normIsMeasured()
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) message = "Без разрешения на уведомления напоминание не придёт"
    }

    val restorePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                container.restoreRepository.restoreFromJsonl(text)
            }
            busy = false
            message = "Добавлено ответов: " + result.imported +
                " · пересчитано " + result.replayed +
                (if (result.skipped > 0) " · пропущено " + result.skipped else "")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Настройки",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )

        Section(
            "Нагрузка",
            "Сколько повторений в день считать нормой. От этого зависит, сколько новых чанков придёт завтра. По умолчанию норма считается сама — по тому, сколько ты реально отвечал за последние две недели."
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.autoLoad,
                    onClick = { scope.launch { container.settings.setAutoLoad(true) } },
                    label = {
                        Text(
                            when {
                                // Never print a figure as if it were measured when it
                                // is still the cold-start default.
                                !settings.autoLoad -> "Авто"
                                !normMeasured -> "Авто · измеряю"
                                measuredNorm > 0 -> "Авто · " + measuredNorm
                                else -> "Авто"
                            }
                        )
                    }
                )
                LoadPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = !settings.autoLoad && settings.load == preset,
                        onClick = { scope.launch { container.settings.setLoad(preset) } },
                        label = { Text(loadLabel(preset)) }
                    )
                }
            }
        }

        Section("Вид", null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.theme == mode,
                        onClick = { scope.launch { container.settings.setTheme(mode) } },
                        label = { Text(themeLabel(mode)) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            ToggleRow(
                title = "Анимации",
                subtitle = "Карточки улетают, конец дня с анимацией",
                checked = settings.animations,
                onCheckedChange = { scope.launch { container.settings.setAnimations(it) } }
            )
            ToggleRow(
                title = "Вибрация",
                subtitle = "Короткий отклик на свайп",
                checked = settings.haptics,
                onCheckedChange = { scope.launch { container.settings.setHaptics(it) } }
            )
        }

        Section(
            "Напоминание",
            "Одно в день, и только если минимум ещё не сделан. Никаких серий и укоров."
        ) {
            ToggleRow(
                title = "Напоминать",
                subtitle = if (settings.reminderEnabled)
                    "в " + timeText(settings.reminderHour, settings.reminderMinute)
                else "выключено",
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
                        FilterChip(
                            selected = settings.reminderHour == hour &&
                                settings.reminderMinute == minute,
                            onClick = {
                                scope.launch {
                                    container.settings.setReminder(true, hour, minute)
                                    WorkScheduler.scheduleReminder(context, true, hour, minute)
                                }
                            },
                            label = { Text(timeText(hour, minute)) }
                        )
                    }
                }
            }
        }

        Section(
            "Перерывы",
            "Настраивать нечего и включать нечего. Приложение смотрит, сколько ты реально занимался, и само сдвигает сроки: день без заходов — на день, день вполсилы — на полдня. Новые чанки не добавляются, пока темп не вернётся. Долгов не копится."
        ) {}

        Section(
            "Данные",
            "Журнал ответов — единственное, что нельзя восстановить. Всё остальное считается из него заново."
        ) {
            ToggleRow(
                title = "Авто-выгрузка раз в неделю",
                subtitle = "В папку Документы/Ikna",
                checked = settings.autoExport,
                onCheckedChange = { scope.launch { container.settings.setAutoExport(it) } }
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            val ok = withContext(Dispatchers.IO) {
                                runCatching { container.jsonExporter.export() }.isSuccess
                            }
                            busy = false
                            message = if (ok) "Файл сохранён в Документы/Ikna"
                            else "Не удалось сохранить файл"
                        }
                    }
                ) { Text("Выгрузить сейчас") }
                OutlinedButton(
                    enabled = !busy,
                    onClick = { restorePicker.launch(arrayOf("*/*")) }
                ) { Text("Восстановить") }
            }
        }

        Section("Если что-то пошло не так", null) {
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        withContext(Dispatchers.IO) { container.components.rebuildFromReviews() }
                        busy = false
                        message = "Слой слов пересчитан по журналу"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Пересчитать слой слов") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpenDebug,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Технический экран") }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { confirmReset = true }) {
                Text("Начать заново", color = IknaAgain)
            }
        }

        Section(
            "Стереть всё",
            "Полный сброс: карточки, сроки, статистика, журнал ответов и сами настройки. " +
                "Приложение станет таким, как сразу после установки, и перезапустится. " +
                "Это для тестов, когда данные старой версии мешают новой. " +
                "Перед стиранием журнал выгружается в Документы/Ikna — его потом можно восстановить."
        ) {
            IknaWideButton(
                label = if (wipeArmed) "ТОЧНО СТЕРЕТЬ ВСЁ" else "СТЕРЕТЬ ДАННЫЕ",
                enabled = !busy,
                height = 58.dp,
                onClick = {
                    if (!wipeArmed) {
                        wipeArmed = true
                        message = "Нажми ещё раз, если правда стереть. Отмены не будет."
                    } else {
                        wipeArmed = false
                        busy = true
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                // Export first: if it fails, the wipe still proceeds,
                                // because the user asked for a wipe, not for a backup.
                                runCatching { container.jsonExporter.export() }
                                container.wipeDatabase()
                                container.settings.clearAll()
                            }
                            // Restart the process: repositories, workers and session
                            // state all outlive the tables otherwise, and a half-empty
                            // app in memory looks exactly like a bug.
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
                text = if (wipeArmed) "второе нажатие стирает сразу" else "нажать надо дважды",
                style = MaterialTheme.typography.bodySmall,
                color = IknaDanger
            )
        }

        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = IknaMuted
            )
        }
        Spacer(Modifier.height(28.dp))
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Начать заново?") },
            text = {
                Text(
                    "Сроки карточек, статистика и слой слов обнулятся. Журнал ответов останется — из него можно будет всё вернуть кнопкой «Восстановить»."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch {
                        withContext(Dispatchers.IO) { container.learningRepository.resetProgress() }
                        message = "Прогресс обнулён"
                    }
                }) { Text("Начать заново", color = IknaAgain) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun Section(
    title: String,
    subtitle: String?,
    content: @Composable () -> Unit
) {
    Spacer(Modifier.height(22.dp))
    HorizontalDivider(color = IknaMuted.copy(alpha = 0.18f))
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
            color = IknaMuted
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
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = IknaMuted
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private val REMINDER_TIMES = listOf(9 to 0, 13 to 0, 20 to 0, 22 to 0)

private fun loadLabel(preset: LoadPreset): String = when (preset) {
    LoadPreset.CALM -> "Спокойно · 25"
    LoadPreset.NORMAL -> "Обычно · 40"
    LoadPreset.DENSE -> "Плотно · 60"
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "Как в системе"
    ThemeMode.DARK -> "Тёмная"
    ThemeMode.LIGHT -> "Светлая"
}

private fun timeText(hour: Int, minute: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", hour, minute)

