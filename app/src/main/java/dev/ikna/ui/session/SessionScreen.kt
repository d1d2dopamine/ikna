package dev.ikna.ui.session

import dev.ikna.ui.text.S

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.ikna.AppContainer
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.repo.NO_LANG
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.governor.GovernorReason
import dev.ikna.domain.session.Level
import dev.ikna.ui.theme.IknaBottomBar
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaProgress
import dev.ikna.ui.theme.IknaSpark
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.Motion
import dev.ikna.ui.theme.Space
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/*
 * The whole screen is the card.
 *
 * Fixed geometry is the point of this layout: the status line, the hint line and
 * the button row all keep their height no matter what is inside them. Nothing
 * appears, disappears or resizes while you answer, so a thumb already moving
 * towards a button never lands on something else — which is exactly what used to
 * happen when the hint above the buttons showed up only on the speaking level.
 *
 * The card itself now runs edge to edge. It used to sit in a 20dp margin with an
 * outline around it, which turned the one thing you are supposed to be reading
 * into a small window in the middle of a screen full of nothing.
 *
 * One bar at the top instead of two rows. It carries the way out, the cost of
 * the session before it starts, and — when the session belongs to one deck —
 * which deck that is.
 *
 * Two answers, and both of them are written down where they cannot leave. See
 * [SwipeableCard]: the words used to live on the card and travelled off screen
 * with it, so the side you were pulling towards was the side whose meaning had
 * just disappeared.
 */

private val BAR_HEIGHT = 44.dp
private val UNDO_HEIGHT = 48.dp
private val EDGE = 20.dp

@Composable
fun SessionScreen(
    container: AppContainer,
    deckId: String?,
    onBack: () -> Unit
) {
    // Keyed by deck: opening Polish and then English must not hand the second
    // session the first one's queue.
    val vm: SessionViewModel = viewModel(
        key = "session:" + (deckId ?: "all"),
        factory = SessionViewModel.factory(
            container.learningRepository,
            container.settings,
            container.speaker,
            deckId
        )
    )
    val state by vm.state.collectAsState()
    val settings by container.settings.flow.collectAsState(initial = IknaSettings())

    val card = state.current

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(state = state)
        IknaProgress(fraction = state.progress)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when {
                state.loading -> Text(
                    text = S.t("sess.001"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                card != null -> SwipeableCard(
                    key = card.card.key + ":" + state.index,
                    revealed = state.revealed,
                    animations = settings.animations,
                    haptics = settings.haptics,
                    // The two words stay on screen until the movement is learned,
                    // then leave it. Read once per session, so nothing appears or
                    // disappears under a thumb that is already moving.
                    railsAtRest = !state.swipeFluent,
                    onReveal = vm::reveal,
                    onRate = { rating -> vm.rate(rating, viaSwipe = true) }
                ) { progress ->
                    ChunkCard(
                        label = levelLabel(card.level, card.chunk.lang == NO_LANG),
                        prompt = card.prompt,
                        answer = card.answer,
                        // Which part of the sentence the card is actually
                        // asking about. Null at the levels where the front
                        // is not a sentence, which the card handles.
                        promptTarget = card.promptTarget,
                        answerTarget = card.answerTarget,
                        // On a subject deck the third field IS the meaning of the term, so
                        // showing it beside a definition with the term blanked out
                        // would simply print the answer.
                        hint = if (card.level == Level.CLOZE && card.chunk.lang != NO_LANG) {
                            card.chunk.translation
                        } else {
                            null
                        },
                        revealed = state.revealed,
                        // The one line that says how to turn a card over. It was
                        // computed for every session and then never drawn, so the
                        // first card of a first session explained nothing at all.
                        showTapHint = state.showRevealHint,
                        progress = progress,
                        onTap = vm::reveal,
                        tapEnabled = !state.revealed,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> EmptyState(
                    state = state,
                    animations = settings.animations,
                    onExtra = vm::addExtra
                )
            }
        }

        UndoBar(
            visible = state.undoVisible,
            failed = state.undoFailed,
            wrong = state.wrongMarked,
            onUndo = vm::undo,
            onDismiss = vm::dismissUndo
        )

        // The way out and the loudspeaker, both within reach of the thumb that is
        // already swiping cards. They used to sit in the top bar, which on this
        // screen meant the exit was the furthest point from the hand doing the
        // work.
        IknaBottomBar {
            IknaIconButton(glyph = IknaGlyph.BACK, onClick = onBack, label = S.t("a11y.001"))
            Spacer(Modifier.weight(1f))
            // Only on a turned card, and never as a third rating button: the two
            // words under the thumb are about memory, this is about the deck
            // being wrong. Hidden before the reveal because a card nobody has
            // read yet cannot be judged, and because a mistap here would throw
            // away material instead of grading it.
            if (state.current != null && state.revealed) {
                IknaTextButton(
                    label = S.t("sess.044"),
                    onClick = vm::markWrong,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
            }
            // Sound is a control, not part of the card. The card is the one thing on
            // this screen that must never grow a control, and the mark is only drawn
            // when pressing it would not give the answer away.
            if (state.speechReady && state.speakable) {
                IknaIconButton(
                    glyph = IknaGlyph.SOUND,
                    onClick = vm::speakCurrent,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    label = S.t("a11y.005")
                )
            }
        }
    }
}

/**
 * Way out on the left, deck on the right, one line of service text between them.
 *
 * The way out is a real 44dp target. It used to be a 20dp invisible strip at the
 * screen edge that opened a drawer — the same strip Android 10 and later claims
 * for its own back gesture, so the app lost that fight roughly every other try,
 * and on this screen the strip was disabled outright, which left the session with
 * no exit at all.
 *
 * The service text used to carry a live "осталось 12 · ~3 мин" and "дальше:
 * вставить" beside the card for the whole session. Both answer questions nobody
 * asks in the middle of recalling something, and a counter ticking down next to
 * the thing you are reading is an invitation to watch the counter instead. So the
 * cost of the session is shown once, before the first answer, where it is a
 * decision — and then the line goes quiet. The one thing that earns a word back
 * is the minimum being reached, for one card only.
 */
@Composable
private fun TopBar(state: SessionUiState) {
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

@Composable
private fun EmptyState(
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
                if (animations) appear.animateTo(1f, Motion.reveal)
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

/** Fixed strip, so an answer never shifts the buttons under your thumb. */
@Composable
private fun UndoBar(
    visible: Boolean,
    failed: Boolean,
    wrong: Boolean,
    onUndo: () -> Unit,
    onDismiss: () -> Unit
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
            wrong -> Text(
                text = S.t("sess.045"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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

/**
 * What the question is asking for.
 *
 * A subject deck says it differently at the same two levels: a neuroscience card
 * shows a term and then its definition with the term missing, and calling that
 * "recognition" and "a gap in a sentence" describes a phrasebook instead. The
 * third level never appears there at all -- see [dev.ikna.domain.session.LevelPromotion].
 */
private fun levelLabel(level: Level, subject: Boolean): String = when (level) {
    Level.RECOGNITION -> if (subject) S.t("sess.046") else S.t("sess.015")
    Level.CLOZE -> if (subject) S.t("sess.047") else S.t("sess.016")
    Level.PRODUCTION -> S.t("sess.017")
}

/**
 * The end of a deck outranks the end of a day.
 *
 * "Новые чанки придут сами завтра" is true on almost every empty screen and
 * false on exactly one: the deck with nothing left in it. There, tomorrow brings
 * nothing new, and the app would go on promising it every single day.
 */
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
    val mod100 = days % 100
    val mod10 = days % 10
    return when {
        mod100 in 11..14 -> S.t("sess.033")
        mod10 == 1L -> S.t("sess.034")
        mod10 in 2..4 -> S.t("sess.035")
        else -> S.t("sess.036")
    }
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
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> S.t("sess.039")
        mod10 == 1 -> S.t("sess.040")
        mod10 in 2..4 -> S.t("sess.041")
        else -> S.t("sess.042")
    }
}
