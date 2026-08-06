package dev.ikna.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import dev.ikna.AppContainer
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaRule

/*
 * Progress without a score.
 *
 * The old screen printed "N of 30" above a sentence promising there were no
 * streaks, which is a streak with extra steps: one number that goes up while you
 * show up and drops the moment you miss. This one draws the month as separate
 * marks. A gap is a gap, nothing breaks, and there is no record to protect.
 */

private val EDGE = 20.dp

@Composable
fun StatsScreen(container: AppContainer, onBack: () -> Unit) {
    var days by remember { mutableStateOf(emptyList<Boolean>()) }
    var norm by remember { mutableStateOf(0) }
    var measured by remember { mutableStateOf(true) }
    var known by remember { mutableStateOf(0) }
    var answered by remember { mutableStateOf(0) }
    var forecast by remember { mutableStateOf(emptyList<Int>()) }

    LaunchedEffect(Unit) {
        days = container.learningRepository.activityMap()
        norm = container.learningRepository.currentDailyTarget()
        measured = container.learningRepository.normIsMeasured()
        known = container.components.knownWordCount()
        answered = container.learningRepository.answeredToday()
        forecast = container.learningRepository.forecast(14)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = EDGE)
    ) {
        // The glyph box is 44dp with a 19dp mark in the middle, so it is pulled
        // back by half the difference to stand on the same left margin as every
        // line of text below it.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .offset(x = (-12).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IknaIconButton(glyph = IknaGlyph.BACK, onClick = onBack)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Прогресс",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(30.dp))
        Label("ПОСЛЕДНИЕ 30 ДНЕЙ")
        Spacer(Modifier.height(12.dp))
        ActivityMap(days = days)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Каждая метка — день с занятием, справа сегодня. " +
                "Пропуск ничего не обнуляет и не обрывает: цепочки здесь нет, ломать нечего.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(28.dp))
        IknaRule()
        Spacer(Modifier.height(28.dp))

        Label("НОРМА ДНЯ")
        Spacer(Modifier.height(10.dp))
        Text(
            text = norm.toString(),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            // Honesty about where the figure comes from. Until there are enough of
            // your own days behind it, it is a starting guess and says so.
            text = if (measured) {
                "Карточек в день. Посчитано по твоим последним дням, пересчитывается само."
            } else {
                "Ориентир на первые дни, а не измерение. " +
                    "Свою цифру посчитаю, когда наберётся хотя бы три дня с занятиями — до того это было бы выдумкой."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(28.dp))
        IknaRule()
        Spacer(Modifier.height(28.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Label("СЛОВ В ПАМЯТИ")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = known.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Label("ОТВЕЧЕНО СЕГОДНЯ")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = answered.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Слова считаются отдельно от чанков: одно слово встречается в разных фразах и держится крепче любой из них.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(28.dp))
        IknaRule()
        Spacer(Modifier.height(28.dp))

        Label("ВЕРНЁТСЯ ЗА 14 ДНЕЙ")
        Spacer(Modifier.height(14.dp))
        ForecastBars(values = forecast)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Сколько карточек подошлёт по сроку. Если где-то вырастает гора — новые чанки в те дни добавляться не будут.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(36.dp))
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Thirty marks in a row, oldest on the left, today on the right.
 *
 * Filled means there was a session. Hollow means there was not. Nothing is
 * coloured red, nothing is counted out loud.
 */
@Composable
private fun ActivityMap(days: List<Boolean>) {
    val accent = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
    // Repository order is most recent first; drawing goes the other way round.
    val ordered = days.reversed()

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
    ) {
        val count = 30
        val gap = size.width * 0.012f
        val cell = (size.width - gap * (count - 1)) / count
        for (i in 0 until count) {
            val active = ordered.getOrNull(i) == true
            val markHeight = if (active) size.height else size.height * 0.28f
            val top = (size.height - markHeight) / 2f
            drawRect(
                color = if (active) accent else idle,
                topLeft = Offset(i * (cell + gap), top),
                size = Size(cell, markHeight)
            )
        }
    }
}

@Composable
private fun ForecastBars(values: List<Int>) {
    val accent = MaterialTheme.colorScheme.primary
    val base = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            if (values.isEmpty()) return@Canvas
            val peak = (values.maxOrNull() ?: 0).coerceAtLeast(1)
            val gap = size.width * 0.014f
            val cell = (size.width - gap * (values.size - 1)) / values.size
            values.forEachIndexed { i, value ->
                val barHeight = size.height * (value.toFloat() / peak)
                drawRect(
                    color = if (value > 0) accent else base,
                    topLeft = Offset(
                        i * (cell + gap),
                        size.height - barHeight.coerceAtLeast(2f)
                    ),
                    size = Size(cell, barHeight.coerceAtLeast(2f))
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Label("СЕГОДНЯ")
            Label("+7")
            Label("+14")
        }
    }
}
