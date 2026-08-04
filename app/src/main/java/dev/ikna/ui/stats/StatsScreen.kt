package dev.ikna.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import dev.ikna.AppContainer
import dev.ikna.ui.theme.IknaMuted

/**
 * Charts are drawn with Canvas rather than a chart library: a heatmap and a
 * forecast histogram are less code by hand than they are configuration, and
 * they stay out of the dependency list.
 */
@Composable
fun StatsScreen(container: AppContainer, onBack: () -> Unit, onOpenDebug: () -> Unit) {
    var activeDays by remember { mutableStateOf(0) }
    var knownWords by remember { mutableStateOf(0) }
    var forecast by remember { mutableStateOf(listOf<Int>()) }

    LaunchedEffect(Unit) {
        activeDays = container.learningRepository.activeDaysLast30()
        knownWords = container.components.knownWordCount()
        forecast = container.learningRepository.forecast(14)
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onBack) { Text("back") }
                TextButton(onClick = onOpenDebug) { Text("debug") }
            }

            Spacer(Modifier.height(16.dp))

            // A metric that a single missed day cannot break.
            Text(
                text = activeDays.toString() + " / 30 days active",
                style = MaterialTheme.typography.displaySmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = knownWords.toString() + " words held",
                style = MaterialTheme.typography.bodyMedium,
                color = IknaMuted
            )

            Spacer(Modifier.height(32.dp))
            Text("next 14 days", style = MaterialTheme.typography.labelSmall, color = IknaMuted)
            Spacer(Modifier.height(8.dp))
            ForecastChart(forecast)
        }
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
