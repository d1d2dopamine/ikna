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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.ikna.ui.theme.IknaMuted
import dev.ikna.ui.theme.IknaProgress
import dev.ikna.ui.theme.IknaRule
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
 */

private val STATUS_HEIGHT = 36.dp
private val HINT_HEIGHT = 26.dp
private val ACTION_HEIGHT = 96.dp
private val UNDO_HEIGHT = 46.dp
private val EDGE = 20.dp

@Composable
fun SessionScreen(container: AppContainer) {
    val vm: SessionViewModel = viewModel(
        factory = SessionViewModel.factory(container.learningRepository, container.settings)
    )
    val state by vm.state.collectAsState()
    val settings by container.settings.flow.collectAsState(initial = IknaSettings())

    val card = state.current
    val total = (state.answeredToday + state.remaining).coerceAtLeast(1)

    Column(modifier = Modifier.fillMaxSize()) {
        IknaProgress(fraction = state.answeredToday.toFloat() / total)
        StatusLine(state)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = EDGE),
            contentAlignment = Alignment.Center
        ) {
            when {
                state.loading -> Text(
                    text = "секунду…",
                    style = MaterialTheme.typography.labelMedium,
                    color = IknaMuted
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
                    onReveal = {},
                    modifier = Modifier.fillMaxSize()
                )

                card != null -> SwipeableCard(
                    key = card.card.key + ":" + state.index,
                    enabled = state.revealed,
                    animations = settings.animations,
                    haptics = settings.haptics,
                    onRate = vm::rate
                ) { progressX, progressY ->
                    ChunkCard(
                        label = levelLabel(card.level),
                        prompt = card.prompt,
                        answer = card.answer,
                        hint = if (card.level == Level.CLOZE) card.chunk.translation else null,
                        revealed = state.revealed,
                        showTapHint = state.showRevealHint,
                        fromAmnesty = card.fromAmnesty,
                        progressX = progressX,
                        progressY = progressY,
                        onReveal = vm::reveal,
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

        if (card != null && !state.loading) {
            HintLine(state = state, level = card.level)
            IknaRule()
            ActionArea(
                encoding = state.encoding,
                revealed = state.revealed,
                onAcknowledge = vm::acknowledgeEncoding,
                onReveal = vm::reveal,
                onRate = vm::rate
            )
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
 * One line of service text, always the same height.
 *
 * Two values at most, both named. No streak, no day counter, nothing that turns
 * into a record worth protecting — a number you can break is a reason to quit.
 */
@Composable
private fun StatusLine(state: SessionUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(STATUS_HEIGHT)
            .padding(horizontal = EDGE),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (state.remaining > 0) "ОСТАЛОСЬ " + state.remaining
            else "НА СЕГОДНЯ ВСЁ",
            style = MaterialTheme.typography.labelMedium,
            color = IknaMuted,
            maxLines = 1
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = statusRight(state),
            style = MaterialTheme.typography.labelSmall,
            color = if (state.minimumMet && state.remaining == 0) {
                MaterialTheme.colorScheme.primary
            } else {
                IknaMuted
            },
            maxLines = 1
        )
    }
}

/** Reserved room for one hint. Empty is a valid state and takes the same space. */
@Composable
private fun HintLine(state: SessionUiState, level: Level) {
    val text = when {
        state.encoding -> "скажи вслух один раз — дальше спрошу"
        state.revealed -> "свайп вверх — легко, вниз — трудно"
        level == Level.PRODUCTION -> "скажи вслух, потом открой"
        state.showRevealHint -> "тап по карточке — или кнопка ниже"
        else -> ""
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HINT_HEIGHT)
            .padding(horizontal = EDGE),
        contentAlignment = Alignment.Center
    ) {
        if (text.isNotEmpty()) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = IknaMuted,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

/**
 * The thumb zone: one slot, three contents, one height.
 *
 * Before the answer there is a wide reveal button as well as tap-anywhere, since
 * two ways in cost nothing and one of them is always the one you remember.
 */
@Composable
private fun ActionArea(
    encoding: Boolean,
    revealed: Boolean,
    onAcknowledge: () -> Unit,
    onReveal: () -> Unit,
    onRate: (Rating) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ACTION_HEIGHT)
            .padding(horizontal = EDGE, vertical = 8.dp)
    ) {
        when {
            encoding -> IknaWideButton(
                label = "ПОНЯТНО",
                onClick = onAcknowledge,
                filled = true,
                height = 80.dp
            )

            revealed -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IknaWideButton(
                    label = "НЕ ПОМНЮ",
                    onClick = { onRate(Rating.AGAIN) },
                    modifier = Modifier.weight(1f),
                    height = 80.dp
                )
                IknaWideButton(
                    label = "ПОМНЮ",
                    onClick = { onRate(Rating.GOOD) },
                    modifier = Modifier.weight(1f),
                    filled = true,
                    height = 80.dp
                )
            }

            else -> IknaWideButton(
                label = "ПОКАЗАТЬ",
                onClick = onReveal,
                height = 80.dp
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
        modifier = Modifier.fillMaxWidth(),
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
            color = IknaMuted,
            textAlign = TextAlign.Center
        )

        nextDueLabel(state.nextDueAt)?.let { label ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = IknaMuted,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(28.dp))

        if (state.noMoreExtra) {
            Text(
                text = "Повторять больше нечего — остальное ещё не подошло по сроку",
                style = MaterialTheme.typography.bodySmall,
                color = IknaMuted,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            // Never a dead end: the same request again, in case something came due
            // in the meantime.
            TextButton(onClick = onExtra) { Text("ПРОВЕРИТЬ ЕЩЁ РАЗ") }
        } else {
            IknaWideButton(
                label = "ЕЩЁ НЕМНОГО  +5",
                onClick = onExtra,
                height = 64.dp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "только повторения, новые чанки от этого не добавятся",
                style = MaterialTheme.typography.bodySmall,
                color = IknaMuted,
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
                color = IknaMuted
            )

            visible -> Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ответ записан",
                    style = MaterialTheme.typography.bodySmall,
                    color = IknaMuted
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onUndo) { Text("ОТМЕНИТЬ") }
                TextButton(onClick = onDismiss) { Text("ОК") }
            }
        }
    }
}

private fun statusRight(state: SessionUiState): String {
    val next = state.nextCard
    return when {
        state.minimumMet && state.remaining == 0 -> "МИНИМУМ СДЕЛАН"
        next != null -> "ДАЛЬШЕ: " + levelLabel(next.level).uppercase()
        else -> "СЕГОДНЯ " + state.answeredToday
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
