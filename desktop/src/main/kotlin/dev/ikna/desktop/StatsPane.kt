package dev.ikna.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import dev.ikna.data.repo.StatsDigest
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaLatticePlaceholder
import dev.ikna.ui.theme.IknaPalette
import dev.ikna.ui.theme.IknaRule
import dev.ikna.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Sixteen percent of the phone screen was a stub here; this is the whole thing. */
private const val HOUR_CONFIDENT = 12

/**
 * The statistics screen, ported whole from the phone.
 *
 * The window used to show three numbers and stop. The phone shows the month as
 * a grid of days, the day against its norm, retention with the sample size that
 * earns it, minutes today and this week, the hours when recall is best, and the
 * cards that refuse to stick -- each with the sentence that says what the number
 * means and when not to trust it. Numbers without those sentences are decoration,
 * so they came across too.
 */
@Composable
fun StatsPane(
    container: DesktopContainer,
    palette: IknaPalette,
    onBack: () -> Unit = {}
) {
    var digest by remember { mutableStateOf<StatsDigest?>(null) }
    var days by remember { mutableStateOf<List<Boolean>>(emptyList()) }
    var forecast by remember { mutableStateOf<List<Int>>(emptyList()) }
    var answered by remember { mutableStateOf(0) }
    var target by remember { mutableStateOf(0) }
    var measured by remember { mutableStateOf(false) }
    var words by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                val learning = container.learningRepository
                digest = learning.statsDigest()
                days = learning.activityMap()
                forecast = learning.forecast(days = 14)
                answered = learning.answeredToday()
                target = learning.currentDailyTarget()
                measured = learning.normIsMeasured()
                words = container.componentRepository.knownWordCount()
            }.onFailure { error -> logLine("stats failed: " + error) }
        }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(40.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            IknaIconButton(
                glyph = IknaGlyph.BACK,
                onClick = onBack,
                label = S.t("stats.001")
            )
        }
        Spacer(Modifier.height(Space.md))
        Column(modifier = Modifier.widthIn(max = 720.dp)) {
            SectionTitle(S.t("stats.001"), palette)
            Spacer(Modifier.height(Space.lg))

            if (loading && digest == null) {
                Text(
                    text = S.t("stats.032"),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.muted
                )
                return@Column
            }

            StatsBlock(S.t("stats.002"), palette, note = S.t("stats.003")) {
                StatsActivityMap(days, palette)
            }

            StatsDividerLine(palette)

            Row(modifier = Modifier.fillMaxWidth()) {
                StatsFigure(
                    caption = S.t("stats.004"),
                    value = target.toString(),
                    palette = palette,
                    modifier = Modifier.weight(1f)
                )
                StatsFigure(
                    caption = S.t("stats.008"),
                    value = answered.toString(),
                    palette = palette,
                    modifier = Modifier.weight(1f)
                )
                StatsFigure(
                    caption = S.t("stats.007"),
                    value = words.toString(),
                    palette = palette,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(Space.sm))
            StatsBlock(
                title = null,
                palette = palette,
                note = (if (measured) S.t("stats.005") else S.t("stats.006")) + "\n" +
                    S.t("stats.009")
            ) {}

            StatsDividerLine(palette)

            StatsBlock(S.t("stats.010"), palette, note = S.t("stats.011")) {
                StatsForecastBars(forecast, palette)
            }

            val measuredDigest = digest
            if (measuredDigest != null) {
                StatsDividerLine(palette)
                StatsRetention(measuredDigest, palette)
                StatsDividerLine(palette)
                StatsMinutes(measuredDigest, palette)
                StatsDividerLine(palette)
                StatsBestHours(measuredDigest, palette)
                if (measuredDigest.leeches.isNotEmpty()) {
                    StatsDividerLine(palette)
                    StatsLeeches(measuredDigest, palette)
                }
            }
        }
    }
}

/**
 * One figure, and the reason for it kept behind a tap.
 *
 * The window printed every sentence at once, so a screen the phone reads as six
 * numbers read here as six paragraphs of explanation. The phone puts the
 * sentence behind a mark at the end of the title -- "?" when there is something
 * to read, "\u2212" while it is open -- and the whole block is the target, not the
 * mark itself. Same block, same mark, same place in every one of them.
 */
@Composable
private fun StatsBlock(
    title: String?,
    palette: IknaPalette,
    note: String? = null,
    content: @Composable () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val explained = note != null && note.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (explained) {
                    Modifier.clickable(onClickLabel = S.t("a11y.007")) { open = !open }
                } else {
                    Modifier
                }
            )
    ) {
        if (title != null || explained) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.muted
                    )
                }
                Spacer(Modifier.weight(1f))
                if (explained) {
                    Text(
                        text = if (open) "\u2212" else "?",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted
                    )
                }
            }
            Spacer(Modifier.height(Space.sm))
        }
        content()
        if (open && note != null) {
            Spacer(Modifier.height(Space.sm))
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted
            )
        }
    }
}

@Composable
private fun StatsDividerLine(palette: IknaPalette) {
    Spacer(Modifier.height(Space.lg))
    IknaRule(color = palette.line)
    Spacer(Modifier.height(Space.lg))
}

/** A caption and a number, in the type sizes the phone uses. */
@Composable
private fun StatsFigure(
    caption: String,
    value: String,
    palette: IknaPalette,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = caption,
            style = MaterialTheme.typography.labelMedium,
            color = palette.muted
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = palette.ink
        )
    }
}

/**
 * Thirty marks in a row, oldest on the left, today on the right.
 *
 * This used to be fourteen-pixel squares with four pixels between them, which on
 * a monitor is a short strip of tiles that stops nowhere near the width of
 * anything else on the screen. The phone draws the month as one canvas: thirty
 * columns share the full width, a day with a session is full height, a day
 * without one is a low mark on the same centre line. Nothing is red, nothing is
 * counted out loud.
 */
@Composable
private fun StatsActivityMap(days: List<Boolean>, palette: IknaPalette) {
    // The repository hands back the most recent day first; drawing runs the
    // other way round, so today lands on the right where the phone puts it.
    val ordered = days.reversed()
    val accent = palette.accent
    val idle = palette.line

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
    ) {
        val count = 30
        val gap = size.width * 0.012f
        val cell = (size.width - gap * (count - 1)) / count
        for (index in 0 until count) {
            val active = ordered.getOrNull(index) == true
            val markHeight = if (active) size.height else size.height * 0.28f
            val top = (size.height - markHeight) / 2f
            drawRect(
                color = if (active) accent else idle,
                topLeft = Offset(index * (cell + gap), top),
                size = Size(cell, markHeight)
            )
        }
    }
}

/** Fourteen days ahead: how much comes back, day by day. */
@Composable
private fun StatsForecastBars(forecast: List<Int>, palette: IknaPalette) {
    val peak = (forecast.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        forecast.forEach { count ->
            val share = count.toFloat() / peak.toFloat()
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .height((share * 92f).dp.coerceAtLeast(2.dp))
                        .background(palette.accent)
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted
                )
            }
        }
    }
}

/** Retention, and the sample size that earns the right to print it. */
@Composable
private fun StatsRetention(digest: StatsDigest, palette: IknaPalette) {
    val value = digest.retention
    if (value == null || digest.retentionSample < 20) {
        StatsBlock(
            title = S.t("stats.012"),
            palette = palette,
            note = S.t("stats.013") + digest.retentionSample.toString()
        ) {
            // The same unfinished lattice the phone draws. A dash reads as a
            // number that failed to load; this reads as not enough answers yet.
            IknaLatticePlaceholder()
        }
        return
    }

    val percent = (value * 100.0).roundToInt()
    val verdict = when {
        percent < 80 -> S.t("stats.017")
        percent > 95 -> S.t("stats.018")
        else -> S.t("stats.019")
    }

    // The sample size and the verdict are one thought, so they open together.
    StatsBlock(
        title = S.t("stats.012"),
        palette = palette,
        note = S.t("stats.014") + digest.retentionSample.toString() +
            S.t("stats.015") + percent.toString() + S.t("stats.016") + "\n" + verdict
    ) {
        Text(
            text = percent.toString() + "%",
            style = MaterialTheme.typography.headlineMedium,
            color = palette.ink
        )
    }
}

/** Minutes today, minutes this week, and how long one card takes. */
@Composable
private fun StatsMinutes(digest: StatsDigest, palette: IknaPalette) {
    Row(modifier = Modifier.fillMaxWidth()) {
        StatsFigure(
            caption = S.t("stats.020"),
            value = digest.minutesToday.toString(),
            palette = palette,
            modifier = Modifier.weight(1f)
        )
        StatsFigure(
            caption = S.t("stats.021"),
            value = digest.minutesLast7.toString(),
            palette = palette,
            modifier = Modifier.weight(1f)
        )
        val median = digest.medianSeconds
        StatsFigure(
            caption = S.t("stats.022"),
            value = if (median == null) S.t("stats.024") else median.toString() + S.t("stats.023"),
            palette = palette,
            modifier = Modifier.weight(1f)
        )
    }
}

/** When recall goes best -- silent until an hour has enough answers to mean it. */
@Composable
private fun StatsBestHours(digest: StatsDigest, palette: IknaPalette) {
    val best = digest.bestHour
    val confident = digest.hours.any { it.answers >= HOUR_CONFIDENT }
    if (best == null || !confident) {
        StatsBlock(
            title = S.t("stats.025"),
            palette = palette,
            note = S.t("stats.028")
        ) {
            IknaLatticePlaceholder()
        }
        return
    }

    StatsBlock(
        title = S.t("stats.025"),
        palette = palette,
        note = S.t("stats.026") + statsHourText(best) + S.t("stats.027")
    ) {
        StatsHourBars(digest, palette)
    }
}

@Composable
private fun StatsHourBars(digest: StatsDigest, palette: IknaPalette) {
    Row(
        modifier = Modifier.fillMaxWidth().height(96.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        (0 until 24).forEach { hour ->
            val slice = digest.hours.firstOrNull { it.hour == hour }
            val ready = slice != null && slice.answers >= HOUR_CONFIDENT
            val share = if (ready && slice != null) slice.accuracy.toFloat() else 0f
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 1.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((share * 88f).dp.coerceAtLeast(2.dp))
                        .background(if (ready) palette.accent else palette.line)
                )
            }
        }
    }
}

/** The cards that will not stick, named plainly. */
@Composable
private fun StatsLeeches(digest: StatsDigest, palette: IknaPalette) {
    StatsBlock(S.t("stats.029"), palette, note = S.t("stats.031")) {
        digest.leeches.forEach { item ->
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyLarge,
                color = palette.ink
            )
            val meaning = item.translation
            if (meaning.isNotBlank()) {
                Spacer(Modifier.height(Space.hair))
                Text(
                    text = meaning,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted
                )
            }
            Spacer(Modifier.height(Space.hair))
            Text(
                text = S.t("stats.030") + item.lapses.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = palette.muted
            )
            Spacer(Modifier.height(Space.md))
        }
    }
}

/** An hour of the day, written as the phone writes it. */
private fun statsHourText(hour: Int): String {
    val normalized = ((hour % 24) + 24) % 24
    return normalized.toString() + ":00"
}
