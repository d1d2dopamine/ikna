package dev.ikna.ui.session

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import dev.ikna.AppContainer
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.governor.GovernorReason
import dev.ikna.domain.session.Level
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaProgress
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.IknaWideButton
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
 */

private val BAR_HEIGHT = 44.dp
private val UNDO_HEIGHT = 46.dp
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
            deckId
        )
    )
    val state by vm.state.collectAsState()
    val settings by container.settings.flow.collectAsState(initial = IknaSettings())

    val card = state.current

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(state = state, onBack = onBack)
        IknaProgress(fraction = state.progress)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when {
                state.loading -> Text(
                    text = "секунду…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // First contact: shown, not asked. Nothing to grade, nothing to fail.
                card != null && state.encoding -> ChunkCard(
                    label = "знакомство",
                    prompt = card.chunk.text,
                    answer = card.chunk.translation,
                    hint = card.chunk.contextSentence,
                    revealed = true,
                    showTapHint = false,
                    fromAmnesty = false,
                    progressX = 0f,
                    progressY = 0f,
                    onTap = vm::acknowledgeEncoding,
                    tapEnabled = true,
                    modifier = Modifier.fillMaxSize(),
                    showSwipeLegend = false
                )

                card != null -> SwipeableCard(
                    key = card.card.key + ":" + state.index,
                    enabled = state.revealed,
                    animations = settings.animations,
                    haptics = settings.haptics,
                    onRate = { rating -> vm.rate(rating, viaSwipe = true) }
                ) { progressX, progressY ->
                    ChunkCard(
                        label = levelLabel(card.level),
                        prompt = card.prompt,
                        answer = card.answer,
                        hint = if (card.level == Level.CLOZE) card.chunk.translation else null,
                        revealed = state.revealed,
                        showTapHint = false,
                        fromAmnesty = card.fromAmnesty,
                        progressX = progressX,
                        progressY = progressY,
                        onTap = vm::reveal,
                        tapEnabled = !state.revealed,
                        modifier = Modifier.fillMaxSize(),
                        showSwipeLegend = false
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
            onUndo = vm::undo,
            onDismiss = vm::dismissUndo
        )
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
private fun TopBar(state: SessionUiState, onBack: () -> Unit) {
    val minimumJustMet = state.minimumMet && state.answeredToday == state.dailyMinimum
    val text = when {
        state.index == 0 && !state.revealed && state.remaining > 0 -> startEstimate(state)
        minimumJustMet -> "МИНИМУМ СДЕЛАН"
        else -> ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IknaIconButton(glyph = IknaGlyph.BACK, onClick = onBack)
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (minimumJustMet) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        if (state.deckTitle != null) {
            Text(
                text = state.deckTitle.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(end = 14.dp)
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
        if (animations && state.answeredToday > 0) {
            val composition by rememberLottieComposition(
                LottieCompositionSpec.Asset("lottie/session_done.json")
            )
            val progress by animateLottieCompositionAsState(composition, iterations = 1)
            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier.size(140.dp)
            )
        }

        Text(
            text = if (state.answeredToday > 0) "На сегодня всё" else "Сегодня карточек нет",
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(14.dp))

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
                text = "Повторять больше нечего — остальное ещё не подошло по сроку",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            // Never a dead end: the same request again, in case something came due
            // in the meantime.
            IknaTextButton(label = "ПРОВЕРИТЬ ЕЩЁ РАЗ", onClick = onExtra)
        } else {
            IknaWideButton(
                label = "ЕЩЁ НЕМНОГО  +5",
                onClick = onExtra,
                height = 56.dp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "только повторения, новые чанки от этого не добавятся",
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
                text = "этот ответ отменить уже нельзя",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            visible -> Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ответ записан",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                IknaTextButton(label = "ОТМЕНИТЬ", onClick = onUndo)
                Spacer(Modifier.width(10.dp))
                IknaTextButton(label = "ОК", onClick = onDismiss, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun levelLabel(level: Level): String = when (level) {
    Level.RECOGNITION -> "узнать"
    Level.CLOZE -> "вставить"
    Level.PRODUCTION -> "сказать"
}

private fun emptyExplanation(state: SessionUiState): String = when {
    state.answeredToday > 0 -> "План дня закрыт. Новые чанки придут сами завтра."
    else -> reasonText(state.reason)
}

private fun reasonText(reason: GovernorReason): String = when (reason) {
    GovernorReason.FIRST_RUN -> "Первый день — берём совсем немного."
    GovernorReason.OK -> "Всё повторено, срок следующих ещё не наступил."
    GovernorReason.NO_HEADROOM -> "Повторений впереди и так много — новые слова подождут."
    GovernorReason.BACKLOG_LIMIT -> "Сначала разбираем накопившееся, потом новое."
    GovernorReason.POST_SKIP_WARMUP -> "После перерыва сначала разогрев на старом."
    GovernorReason.LOW_ACTIVITY -> "Неделя вышла тихая — новое подождёт, сроки уже сдвинуты, долгов нет."
    GovernorReason.LATE_NIGHT -> "Для новых чанков поздно — познакомимся утром, повторения на месте."
    GovernorReason.LOW_ACCURACY -> "Пока старое не закрепится — без новых."
    GovernorReason.RETURN_MODE -> "Режим возвращения: несколько коротких дней, без долгов."
    GovernorReason.SAFETY_VALVE -> "Добавлю хотя бы один новый чанк, чтобы не стоять на месте."
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
        0L -> "следующие — сегодня в " + time
        1L -> "следующие — завтра в " + time
        else -> "следующие — через " + days + " " + dayWord(days)
    }
}

private fun dayWord(days: Long): String {
    val mod100 = days % 100
    val mod10 = days % 10
    return when {
        mod100 in 11..14 -> "дней"
        mod10 == 1L -> "день"
        mod10 in 2..4 -> "дня"
        else -> "дней"
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
    if (totalMs < 45_000L) return base + " · <1 МИН"
    val minutes = ((totalMs + 30_000L) / 60_000L).toInt().coerceAtLeast(1)
    return base + " · ~" + minutes + " МИН"
}

private fun cardWord(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> "карточек"
        mod10 == 1 -> "карточка"
        mod10 in 2..4 -> "карточки"
        else -> "карточек"
    }
}
