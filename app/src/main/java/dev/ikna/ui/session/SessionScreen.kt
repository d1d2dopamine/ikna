package dev.ikna.ui.session

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import dev.ikna.domain.session.SessionCard
import dev.ikna.ui.theme.IknaGood
import dev.ikna.ui.theme.IknaMuted
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun SessionScreen(container: AppContainer) {
    val vm: SessionViewModel = viewModel(
        factory = SessionViewModel.factory(container.learningRepository, container.settings)
    )
    val state by vm.state.collectAsState()
    val settings by container.settings.flow.collectAsState(initial = IknaSettings())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        SessionHeader(state)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val card = state.current
            when {
                state.loading -> Text("секунду…", color = IknaMuted)

                card != null && state.encoding -> EncodingCard(
                    card = card,
                    onDone = vm::acknowledgeEncoding
                )

                card != null -> Column(modifier = Modifier.fillMaxWidth()) {
                    SwipeableCard(
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
                            onReveal = vm::reveal
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    if (!state.revealed && card.level == Level.PRODUCTION) {
                        Text(
                            text = "скажи вслух, потом открой",
                            style = MaterialTheme.typography.bodySmall,
                            color = IknaMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    AnimatedVisibility(visible = state.revealed) {
                        RatingRow(onRate = vm::rate)
                    }
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
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Two numbers, both explained in words. The previous header showed three bare
 * numerals with no labels, one of which went up while you answered.
 */
@Composable
private fun SessionHeader(state: SessionUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (state.remaining > 0) "Осталось " + state.remaining
                else "На сегодня всё",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            if (state.minimumMet) {
                Text(
                    text = "минимум сделан",
                    style = MaterialTheme.typography.labelMedium,
                    color = IknaGood
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        val total = (state.answeredToday + state.remaining).coerceAtLeast(1)
        LinearProgressIndicator(
            progress = { state.answeredToday.toFloat() / total },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = IknaGood,
            trackColor = IknaMuted.copy(alpha = 0.22f)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = headerFooter(state),
            style = MaterialTheme.typography.bodySmall,
            color = IknaMuted
        )
    }
}

/**
 * Binary buttons for the two answers that matter, swipes for all four. Rating a
 * card should not feel like filling in a form.
 */
@Composable
private fun RatingRow(onRate: (Rating) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { onRate(Rating.AGAIN) },
                modifier = Modifier.weight(1f)
            ) { Text("Не помню") }
            Button(
                onClick = { onRate(Rating.GOOD) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = IknaGood)
            ) { Text("Помню") }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "свайп вверх — легко, вниз — трудно",
            style = MaterialTheme.typography.bodySmall,
            color = IknaMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
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
                modifier = Modifier.size(160.dp)
            )
        }

        Text(
            text = when {
                state.answeredToday > 0 -> "На сегодня всё"
                else -> "Сегодня карточек нет"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = emptyExplanation(state),
            style = MaterialTheme.typography.bodyMedium,
            color = IknaMuted,
            textAlign = TextAlign.Center
        )

        nextDueLabel(state.nextDueAt)?.let { label ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = IknaMuted,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(24.dp))
        if (state.noMoreExtra) {
            Text(
                text = "Повторять больше нечего — остальное ещё не подошло по сроку",
                style = MaterialTheme.typography.bodySmall,
                color = IknaMuted,
                textAlign = TextAlign.Center
            )
        } else {
            OutlinedButton(onClick = onExtra) { Text("Ещё немного (+5)") }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "только повторения, новые чанки от этого не добавятся",
                style = MaterialTheme.typography.bodySmall,
                color = IknaMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun UndoBar(
    visible: Boolean,
    failed: Boolean,
    onUndo: () -> Unit,
    onDismiss: () -> Unit
) {
    if (failed) {
        Text(
            text = "Этот ответ отменить уже нельзя",
            style = MaterialTheme.typography.bodySmall,
            color = IknaMuted,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        return
    }
    AnimatedVisibility(visible = visible) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Ответ записан",
                style = MaterialTheme.typography.bodySmall,
                color = IknaMuted
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onUndo) { Text("Отменить") }
            TextButton(onClick = onDismiss) { Text("Ок") }
        }
    }
}

/**
 * A first contact — shown, not asked.
 *
 * Everything is on screen at once: the chunk, what it means, the sentence it
 * lives in. There is nothing to grade, so there is nothing to fail. The one
 * instruction is to say it out loud once, which is about the cheapest reliable
 * memory gain that exists and costs a second here.
 */
@Composable
private fun EncodingCard(card: SessionCard, onDone: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ChunkCard(
            label = "знакомство",
            prompt = card.chunk.text,
            answer = card.chunk.translation,
            hint = card.chunk.contextSentence,
            revealed = true,
            showTapHint = false,
            fromAmnesty = false,
            progressX = 0f,
            progressY = 0f,
            onReveal = {}
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "скажи вслух один раз — дальше спрошу",
            style = MaterialTheme.typography.bodySmall,
            color = IknaMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = IknaGood)
        ) { Text("Понятно") }
    }
}

/** Answered today, plus what the next question is going to be. */
private fun headerFooter(state: SessionUiState): String {
    val done = "сегодня отвечено " + state.answeredToday
    val next = state.nextCard ?: return done
    return done + " · дальше: " + levelLabel(next.level)
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
