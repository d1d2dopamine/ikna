package dev.ikna.ui.session
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ikna.ui.text.S
import dev.ikna.ui.text.quantityWord
import dev.ikna.ui.theme.*
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.text.style.TextAlign
import dev.ikna.domain.governor.GovernorReason
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
private val BAR_HEIGHT = 44.dp
private val UNDO_HEIGHT = 48.dp
private val EDGE = 20.dp

@Composable
fun IknaSessionTopBar(state: SessionUiState) {
    val minimumJustMet = state.minimumMet && state.answeredToday == state.dailyMinimum
    val text = when {
        state.index == 0 && !state.revealed && state.remaining > 0 -> startEstimate(state)
        minimumJustMet -> S.t("sess.003")
        else -> ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (minimumJustMet) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        )

        if (state.deckTitle != null) {
            Text(
                text = state.deckTitle.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(end = 16.dp)
            )
        }
    }
}

/** Names the short-term bar and states its finite daily scope. */
@Composable
fun IknaTodayProgress(state: SessionUiState) {
    val total = state.sessionTotal.coerceAtLeast(0)
    val done = state.sessionDone.coerceIn(0, total)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = S.t("progress.001"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "$done / $total",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IknaProgress(fraction = state.progress)
    }
}

@Composable
fun IknaSessionEmptyState(
    state: SessionUiState,
    animations: Boolean,
    onExtra: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = EDGE),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The app's own mark, drawn once when the day closes. This was a Lottie
        // file: an animation runtime plus a JSON asset, shipped to play one shape
        // once a day. The shape is now the launcher icon itself, in the user's own
        // accent colour, so the object that ends a session is the object that
        // started it.
        if (state.answeredToday > 0) {
            val appear = remember { Animatable(if (animations) 0f else 1f) }
            LaunchedEffect(animations) {
                if (animations) appear.animateTo(1f, Motion.reveal) else appear.snapTo(1f)
            }
            IknaSpark(
                color = MaterialTheme.colorScheme.primary,
                size = 120.dp,
                progress = appear.value
            )
            Spacer(Modifier.height(Space.lg))
        }

        Text(
            text = when {
                state.reason == GovernorReason.PACK_EXHAUSTED -> S.t("sess.004")
                state.answeredToday > 0 -> S.t("sess.005")
                else -> S.t("sess.006")
            },
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = emptyExplanation(state),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        nextDueLabel(state.nextDueAt)?.let { label ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(28.dp))

        if (state.noMoreExtra) {
            Text(
                text = S.t("sess.007"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            // Never a dead end: the same request again, in case something came due
            // in the meantime.
            IknaTextButton(label = S.t("sess.008"), onClick = onExtra)
        } else {
            // "A bit more" is an offer, not the way out. It used to be a
            // full-width 56dp slab — the loudest object on a screen whose entire
            // message is that you are finished, arguing with the sentence above it.
            IknaTextButton(label = S.t("sess.009"), onClick = onExtra)
            Spacer(Modifier.height(Space.md))
            Text(
                text = S.t("sess.010"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun IknaSessionUndoBar(
    visible: Boolean,
    failed: Boolean,
    wrong: Boolean,
    wrongSourceId: String?,
    reportCopied: Boolean,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSource: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(UNDO_HEIGHT)
            .padding(horizontal = EDGE),
        contentAlignment = Alignment.CenterStart
    ) {
        when {
            failed -> Text(
                text = S.t("sess.011"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Said once, without an undo beside it: the card is gone on
            // purpose, and it can be brought back in settings if that was a
            // mistake, which is the right amount of friction for a decision
            // about the deck rather than about an answer.
            wrong -> Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (reportCopied) S.t("src.006") else S.t("sess.045"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (wrongSourceId != null) {
                    IknaTextButton(
                        label = S.t("src.007"),
                        onClick = { onOpenSource(wrongSourceId) }
                    )
                }
            }

            visible -> Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = S.t("sess.012"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                IknaTextButton(label = S.t("sess.013"), onClick = onUndo)
                Spacer(Modifier.width(12.dp))
                IknaTextButton(label = S.t("sess.014"), onClick = onDismiss, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun emptyExplanation(state: SessionUiState): String = when {
    state.reason == GovernorReason.PACK_EXHAUSTED -> reasonText(state.reason)
    state.answeredToday > 0 -> S.t("sess.018")
    else -> reasonText(state.reason)
}

private fun reasonText(reason: GovernorReason): String = when (reason) {
    GovernorReason.FIRST_RUN -> S.t("sess.019")
    GovernorReason.OK -> S.t("sess.020")
    GovernorReason.NO_HEADROOM -> S.t("sess.021")
    GovernorReason.BACKLOG_LIMIT -> S.t("sess.022")
    GovernorReason.POST_SKIP_WARMUP -> S.t("sess.023")
    GovernorReason.LOW_ACTIVITY -> S.t("sess.024")
    GovernorReason.LATE_NIGHT -> S.t("sess.025")
    GovernorReason.LOW_ACCURACY -> S.t("sess.026")
    GovernorReason.RETURN_MODE -> S.t("sess.027")
    GovernorReason.SAFETY_VALVE -> S.t("sess.028")
    GovernorReason.OVERHEATED -> S.t("sess.043")
    GovernorReason.PACK_EXHAUSTED ->
        S.t("sess.029")
}

private fun nextDueLabel(nextDueAt: Long?): String? {
    if (nextDueAt == null) return null
    val now = System.currentTimeMillis()
    if (nextDueAt <= now) return null
    val zone = ZoneId.systemDefault()
    val then = Instant.ofEpochMilli(nextDueAt).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val time = then.format(DateTimeFormatter.ofPattern("HH:mm"))
    return when (val days = ChronoUnit.DAYS.between(today, then.toLocalDate())) {
        0L -> S.t("sess.030") + time
        1L -> S.t("sess.031") + time
        else -> S.t("sess.032") + days + " " + dayWord(days)
    }
}

private fun dayWord(days: Long): String {
    return quantityWord(days, "sess.033", "sess.034", "sess.035", "sess.036")
}

// ---- how long this will take ----------------------------------------------

/**
 * "12 КАРТОЧЕК · ~3 МИН", shown once, before the first answer.
 *
 * A number of cards is not a unit anyone can plan with, and "some cards" is
 * exactly the shape of task that gets postponed: the cost is unknown, so the
 * brain prices it as expensive. Minutes are a unit you can decide about while
 * the kettle boils. The figure comes from the user's own recent answers rather
 * than a constant, and while there is not enough history to measure it, nothing
 * is shown — an estimate that turns out to be a lie costs more trust than it
 * buys.
 *
 * It disappears the moment the first card is opened, because after the decision
 * to start has been made the same figure is just a countdown.
 */
private fun startEstimate(state: SessionUiState): String {
    val count = state.remaining
    val base = (count.toString() + " " + cardWord(count)).uppercase()
    val perCard = state.perCardMs ?: return base
    val totalMs = count * perCard
    if (totalMs < 45_000L) return base + S.t("sess.037")
    val minutes = ((totalMs + 30_000L) / 60_000L).toInt().coerceAtLeast(1)
    return base + " · ~" + minutes + S.t("sess.038")
}

private fun cardWord(count: Int): String {
    return quantityWord(count.toLong(), "sess.039", "sess.040", "sess.041", "sess.042")
}
