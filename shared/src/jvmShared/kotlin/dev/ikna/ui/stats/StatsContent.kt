package dev.ikna.ui.stats
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.ikna.data.repo.*
import java.util.Locale

@Composable
fun IknaStatsContent(days: List<Boolean>, norm: Int, measured: Boolean, known: Int,
    answered: Int, forecast: List<Int>, digest: StatsDigest) {
    Column(Modifier.fillMaxWidth()) {
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
            // An unfinished piece of the same lattice used by real charts. A
            // dash looked like a value that failed to load; this says that the
            // structure exists and does not have enough observations yet.
            IknaLatticePlaceholder()
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
