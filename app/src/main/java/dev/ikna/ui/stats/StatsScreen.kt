package dev.ikna.ui.stats

import dev.ikna.ui.text.S

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import dev.ikna.data.repo.HourSlice
import dev.ikna.data.repo.LeechItem
import dev.ikna.data.repo.StatsDigest
import dev.ikna.ui.theme.IknaBottomBar
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaRule
import java.util.Locale

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
            Block(label = S.t("stats.002"), note = S.t("stats.003")) {
                ActivityMap(days = days)
            }

            StatsDivider()

            Block(
                label = S.t("stats.004"),
                // Honesty about where the figure comes from. Until there are enough
                // of your own days behind it, it is a starting guess and says so.
                note = if (measured) S.t("stats.005") else S.t("stats.006")
            ) {
                Text(
                    text = norm.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            StatsDivider()

            // Two figures that belong to one question, so they share one block and
            // one explanation instead of being asked about twice.
            Block(note = S.t("stats.009")) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Label(S.t("stats.007"))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = known.toString(),
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Label(S.t("stats.008"))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = answered.toString(),
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            StatsDivider()

            Retention(digest)

            StatsDivider()

            Minutes(digest)

            StatsDivider()

            BestHours(digest)

            StatsDivider()

            Leeches(digest.leeches)

            StatsDivider()

            Block(label = S.t("stats.010"), note = S.t("stats.011")) {
                ForecastBars(values = forecast)
            }

            // Room for the bar to sit over nothing but background.
            Spacer(Modifier.height(96.dp))
        }

        IknaBottomBar(modifier = Modifier.align(Alignment.BottomCenter)) {
            IknaIconButton(glyph = IknaGlyph.BACK, onClick = onBack, label = S.t("a11y.001"))
        }
    }
}

/**
 * One figure, and the reason for it kept behind a tap.
 *
 * The mark on the right is the whole affordance: "?" when there is something to
 * read, "−" while it is open. It sits in the same place in every block, so the
 * gesture is learned once and never hunted for. The entire block is the target,
 * not the mark, because a 12sp question mark is not something a thumb aims at.
 */
@Composable
private fun Block(
    label: String? = null,
    note: String,
    content: @Composable () -> Unit
) {
    var open by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = S.t("a11y.007")) { open = !open }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (label != null) Label(label)
            Spacer(Modifier.weight(1f))
            Text(
                text = if (open) "\u2212" else "?",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))
        content()
        if (open) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Share of reviews recalled.
 *
 * The one number here that could be read as a grade, so it is worded as a
 * property of the schedule: the scheduler aims at nine out of ten, and both
 * sides of that are stated as adjustments rather than as good and bad. Both
 * sentences live behind the tap together — the sample size and the verdict are
 * one thought, and splitting them left the verdict looking like a score.
 */
@Composable
private fun Retention(digest: StatsDigest) {
    val retention = digest.retention
    if (retention == null) {
        Block(
            label = S.t("stats.012"),
            note = S.t("stats.013") + digest.retentionSample + "."
        ) {
            Text(
                text = "—",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val percent = Math.round(retention * 100).toInt()
    val verdict = when {
        percent < 80 -> S.t("stats.017")
        percent > 95 -> S.t("stats.018")
        else -> S.t("stats.019")
    }

    Block(
        label = S.t("stats.012"),
        note = S.t("stats.014") + digest.retentionSample + S.t("stats.015") + percent +
            S.t("stats.016") + " " + verdict
    ) {
        Text(
            text = percent.toString() + "%",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/** Time, because "сколько карточек" is not a unit anyone plans an evening in. */
@Composable
private fun Minutes(digest: StatsDigest) {
    Block(
        note = if (digest.medianSeconds != null) {
            S.t("stats.022") + digest.medianSeconds + S.t("stats.023")
        } else {
            S.t("stats.024")
        }
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Label(S.t("stats.020"))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = digest.minutesToday.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Label(S.t("stats.021"))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = digest.minutesLast7.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

/**
 * When answering actually goes well.
 *
 * Not "when you study most" — that would only show the habit back. Each column
 * is how much came back at that hour. Hours with too little behind them are
 * drawn faintly and kept out of the verdict rather than hidden, so a pale column
 * reads as "not enough yet" instead of "bad hour".
 */
@Composable
private fun BestHours(digest: StatsDigest) {
    val best = digest.bestHour
    Block(
        label = S.t("stats.025"),
        note = if (best != null) {
            S.t("stats.026") + hourText(best) + S.t("stats.027")
        } else {
            S.t("stats.028")
        }
    ) {
        HourBars(digest.hours)
    }
}

/**
 * Phrases that keep being forgotten.
 *
 * Anki calls these leeches and suspends them silently. Nothing is suspended
 * here: the list exists so a phrase that will not stick can be recognised as a
 * bad phrase — too long, or translated in a way that does not match how the word
 * is actually used — instead of being read as a personal failure.
 */
@Composable
private fun Leeches(items: List<LeechItem>) {
    Block(label = S.t("stats.029"), note = S.t("stats.031")) {
        if (items.isEmpty()) {
            Text(
                text = S.t("stats.030"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = item.translation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = item.lapses.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsDivider() {
    Spacer(Modifier.height(28.dp))
    IknaRule()
    Spacer(Modifier.height(28.dp))
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
            .height(36.dp)
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

/** Twenty-four columns, midnight to midnight; height is recall inside that hour. */
@Composable
private fun HourBars(hours: List<HourSlice>) {
    val accent = MaterialTheme.colorScheme.primary
    val faint = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
    val empty = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    val byHour = hours.associateBy { it.hour }

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
        ) {
            val count = 24
            val gap = size.width * 0.010f
            val cell = (size.width - gap * (count - 1)) / count
            for (hour in 0 until count) {
                val slice = byHour[hour]
                val fraction = slice?.accuracy ?: 0.0
                val barHeight = (size.height * fraction.toFloat()).coerceAtLeast(2f)
                drawRect(
                    color = when {
                        slice == null -> empty
                        slice.answers >= HOUR_CONFIDENT -> accent
                        else -> faint
                    },
                    topLeft = Offset(hour * (cell + gap), size.height - barHeight),
                    size = Size(cell, barHeight)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Label("00")
            Label("06")
            Label("12")
            Label("18")
            Label("23")
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
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Label(S.t("stats.032"))
            Label("+7")
            Label("+14")
        }
    }
}

/** Mirrors the repository's floor for calling an hour measured rather than guessed. */
private const val HOUR_CONFIDENT = 12

private fun hourText(hour: Int): String =
    String.format(Locale.getDefault(), "%02d:00", hour)
