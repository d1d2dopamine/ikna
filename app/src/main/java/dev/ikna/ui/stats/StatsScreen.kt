package dev.ikna.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ikna.AppContainer
import dev.ikna.ui.theme.IknaMuted

/**
 * Progress.
 *
 * No streaks anywhere on this screen, on purpose: a streak turns one missed day
 * into a loss, and a loss is what makes people stop opening the app. Days active
 * out of thirty cannot be broken by a single bad day.
 *
 * Charts are drawn with Canvas rather than a chart library: a forecast histogram
 * is less code by hand than it is configuration, and it stays out of the
 * dependency list.
 */
@Composable
fun StatsScreen(container: AppContainer) {
    var activeDays by remember { mutableStateOf(0) }
    var knownWords by remember { mutableStateOf(0) }
    var answeredToday by remember { mutableStateOf(0) }
    var forecast by remember { mutableStateOf(listOf<Int>()) }
    var dailyTarget by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        dailyTarget = container.learningRepository.currentDailyTarget()
        activeDays = container.learningRepository.activeDaysLast30()
        knownWords = container.components.knownWordCount()
        answeredToday = container.learningRepository.answeredToday()
        forecast = container.learningRepository.forecast(14)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Прогресс",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(24.dp))
        Text(
            text = activeDays.toString() + " из 30",
            style = MaterialTheme.typography.displaySmall
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "дней с занятиями за последний месяц. Один пропущенный день это число не ломает — серий здесь нет.",
            style = MaterialTheme.typography.bodyMedium,
            color = IknaMuted
        )

        Spacer(Modifier.height(20.dp))
        Text(
            text = "Норма дня сейчас: " + dailyTarget,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "приложение считает её само по последним двум неделям и само сдвигает сроки за дни без занятий, поэтому долгов не бывает.",
            style = MaterialTheme.typography.bodySmall,
            color = IknaMuted
        )

        Spacer(Modifier.height(28.dp))
        Text(
            text = knownWords.toString(),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "слов держатся в памяти — считаются по всем фразам, где ты их узнавал",
            style = MaterialTheme.typography.bodyMedium,
            color = IknaMuted
        )

        Spacer(Modifier.height(28.dp))
        Text(
            text = "Сегодня отвечено: " + answeredToday,
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(Modifier.height(32.dp))
        Text(
            text = "Сколько карточек вернётся в ближайшие 14 дней",
            style = MaterialTheme.typography.bodySmall,
            color = IknaMuted
        )
        Spacer(Modifier.height(10.dp))
        ForecastChart(forecast)
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Если где-то впереди столбик высокий, новые чанки в те дни добавляться не будут.",
            style = MaterialTheme.typography.bodySmall,
            color = IknaMuted
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ForecastChart(values: List<Int>) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        if (values.isEmpty()) return@Canvas
        val maxValue = (values.maxOrNull() ?: 1).coerceAtLeast(1)
        val gap = 6f
        val barWidth = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, v ->
            val h = size.height * (v.toFloat() / maxValue)
            drawRect(
                color = color,
                topLeft = Offset(i * (barWidth + gap), size.height - h),
                size = Size(barWidth, h)
            )
        }
    }
}
