package dev.ikna.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaPalette

/**
 * The numbers, read straight from the learning repository.
 *
 * Only the counts that come back as plain integers. The richer digest has a
 * shape of its own and drawing it properly is stage three work; a wrong number
 * would be worse than a missing one.
 */
@Composable
fun StatsPane(container: DesktopContainer, palette: IknaPalette) {
    var answered by remember { mutableStateOf(0) }
    var target by remember { mutableStateOf(0) }
    var minimum by remember { mutableStateOf(0) }
    var activeDays by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        answered = runCatching { container.learningRepository.answeredToday() }.getOrDefault(0)
        target = runCatching { container.learningRepository.currentDailyTarget() }.getOrDefault(0)
        minimum = runCatching { container.learningRepository.dailyMinimum() }.getOrDefault(0)
        activeDays = runCatching { container.learningRepository.activeDaysLast30() }.getOrDefault(0)
    }

    Column(Modifier.fillMaxSize().padding(40.dp)) {
        Text(S.t("stats.001"), color = palette.ink, fontSize = 20.sp)

        Spacer(Modifier.height(28.dp))
        StatLine(S.t("stats.008"), answered.toString(), palette)
        Spacer(Modifier.height(18.dp))
        StatLine(S.t("stats.004"), target.toString(), palette)
        Spacer(Modifier.height(18.dp))
        StatLine(S.t("onb.005"), minimum.toString(), palette)
        Spacer(Modifier.height(18.dp))
        StatLine(S.t("stats.002"), activeDays.toString(), palette)
    }
}

@Composable
private fun StatLine(label: String, value: String, palette: IknaPalette) {
    Column {
        Text(label, color = palette.muted, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = palette.ink, fontSize = 26.sp)
    }
}
