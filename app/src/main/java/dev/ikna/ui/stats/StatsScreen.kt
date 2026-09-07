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
 * or the material instead. Retention says whether the intervals fit. The hours
 * say when answering is cheap. The minutes replace "how many cards" with the
 * only unit anyone plans an evening in. The last block names the phrases that
 * are not working, which is a fact about the phrases.
 *
 * Nothing here can be broken or lost, and every figure without enough data
 * behind it says so in words instead of printing a confident zero.
 *
 * Why the words are hidden now. Every figure on this screen had its explanation
 * printed under it permanently, and there are nine figures: the screen came out
 * as thirty-odd sentences of small grey prose that has to be read in order,
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
    var norm by remember { mutableStateOf(0) }
    var measured by remember { mutableStateOf(true) }
    var known by remember { mutableStateOf(0) }
    var answered by remember { mutableStateOf(0) }
    var forecast by remember { mutableStateOf(emptyList<Int>()) }
    var digest by remember { mutableStateOf(StatsDigest()) }

    LaunchedEffect(Unit) {
        days = container.learningRepository.activityMap()
        norm = container.learningRepository.currentDailyTarget()
        measured = container.learningRepository.normIsMeasured()
        known = container.components.knownWordCount()
        answered = container.learningRepository.answeredToday()
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
            IknaStatsContent(days, norm, measured, known, answered, forecast, digest)

            // Room for the bar to sit over nothing but background.
            Spacer(Modifier.height(96.dp))
        }

        IknaBottomBar(modifier = Modifier.align(Alignment.BottomCenter)) {
            IknaIconButton(glyph = IknaGlyph.BACK, onClick = onBack, label = S.t("a11y.001"))
        }
    }
}
