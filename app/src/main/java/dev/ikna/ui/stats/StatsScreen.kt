package dev.ikna.ui.stats

import dev.ikna.ui.text.S

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import dev.ikna.AppContainer
import dev.ikna.data.repo.StatsDigest
import dev.ikna.ui.theme.IknaBottomBar
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaIconButton

/*
 * Statistics without a score.
 *
 * The old screen printed "N of 30" above a sentence promising there were no
 * streaks, which is a streak with extra steps: one number that climbs while you
 * show up and drops the moment you miss. Everything here measures the schedule
 * or the material instead. The history block reports only accumulated facts,
 * retention says whether the intervals fit, the hours wait for enough evidence
 * before naming a pattern, and the last blocks show troublesome targets and the
 * scheduler's near-term forecast.
 *
 * Nothing here can be broken or lost, and every figure without enough data
 * behind it says so in words instead of printing a confident zero.
 *
 * Why the words are hidden now. Every figure on this screen once had its explanation
 * printed under it permanently, and the screen came out as thirty-odd sentences
 * of small grey prose that had to be read in order,
 * which is precisely the thing this app exists to avoid. The explanations were
 * not wrong, they were just always on. Each block keeps its own "?" and hands
 * the sentence over when it is asked for, so the default state of the screen is
 * numbers and shapes and the reasoning is one tap away, in the same place, every
 * time.
 */

private val EDGE = 20.dp

@Composable
fun StatsScreen(container: AppContainer, onBack: () -> Unit) {
    var days by remember { mutableStateOf(emptyList<Boolean>()) }
    var forecast by remember { mutableStateOf(emptyList<Int>()) }
    var digest by remember { mutableStateOf(StatsDigest()) }

    LaunchedEffect(Unit) {
        days = container.learningRepository.activityMap()
        forecast = container.learningRepository.forecast(14)
        digest = container.learningRepository.statsDigest()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = EDGE)
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = S.t("stats.001"),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(32.dp))
            IknaStatsContent(days, forecast, digest)

            // Room for the bar to sit over nothing but background.
            Spacer(Modifier.height(96.dp))
        }

        IknaBottomBar(modifier = Modifier.align(Alignment.BottomCenter)) {
            IknaIconButton(glyph = IknaGlyph.BACK, onClick = onBack, label = S.t("a11y.001"))
        }
    }
}
