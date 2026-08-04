package dev.ikna.ui.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import dev.ikna.AppContainer
import dev.ikna.domain.governor.GovernorReason
import dev.ikna.ui.theme.IknaAgain
import dev.ikna.ui.theme.IknaGood
import dev.ikna.ui.theme.IknaMuted

@Composable
fun SessionScreen(container: AppContainer, onOpenStats: () -> Unit) {
    val vm = remember {
        SessionViewModel(container.learningRepository, container.config.dailyMinimumCards)
    }
    val state by vm.state.collectAsState()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SessionHeader(
                remaining = state.remaining,
                minimumMet = state.minimumMet,
                onOpenStats = onOpenStats
            )

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when {
                    state.loading -> Text("...", color = IknaMuted)
                    state.finished -> SessionDone(state.reason, state.answeredThisSession)
                    else -> {
                        val card = state.current
                        if (card != null) {
                            SwipeableCard(
                                key = card.card.chunkId + card.level.value,
                                enabled = state.revealed,
                                onRate = { vm.answer(it) }
                            ) { px, py ->
                                ChunkCard(
                                    prompt = card.prompt,
                                    answer = card.answer,
                                    hint = card.level.name.lowercase(),
                                    revealed = state.revealed,
                                    progressX = px,
                                    progressY = py,
                                    onReveal = { vm.reveal() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionHeader(remaining: Int, minimumMet: Boolean, onOpenStats: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // No streak counter anywhere in this app, by design.
        Text(
            text = if (minimumMet) "today: done" else "today: 1 card",
            style = MaterialTheme.typography.labelSmall,
            color = if (minimumMet) IknaGood else IknaMuted
        )
        Text(
            text = remaining.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = IknaMuted
        )
        TextButton(onClick = onOpenStats) { Text("progress") }
    }
}

@Composable
private fun ChunkCard(
    prompt: String,
    answer: String,
    hint: String,
    revealed: Boolean,
    progressX: Float,
    progressY: Float,
    onReveal: () -> Unit
) {
    val tint = when {
        progressX < -0.35f -> IknaAgain.copy(alpha = 0.16f)
        progressX > 0.35f -> IknaGood.copy(alpha = 0.16f)
        progressY != 0f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else -> Color.Transparent
    }

    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(tint)
                .padding(28.dp)
        ) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = IknaMuted,
                modifier = Modifier.align(Alignment.TopStart)
            )

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                AnimatedVisibility(visible = revealed) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = answer,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (!revealed) {
                TextButton(
                    onClick = onReveal,
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) { Text("show") }
            } else {
                Text(
                    text = "swipe: left again  |  right good  |  up easy  |  down hard",
                    style = MaterialTheme.typography.labelSmall,
                    color = IknaMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .alpha(0.7f)
                )
            }
        }
    }
}

@Composable
private fun SessionDone(reason: GovernorReason, answered: Int) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("lottie/session_done.json")
    )
    val progress by animateLottieCompositionAsState(composition, iterations = 1)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.size(160.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (answered > 0) "done for today" else "nothing due",
            style = MaterialTheme.typography.displaySmall
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = explain(reason),
            style = MaterialTheme.typography.bodyMedium,
            color = IknaMuted,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * The user never sees governor internals, only its intent. Language matters
 * here: nothing on this screen may read as a failure or a debt.
 */
private fun explain(reason: GovernorReason): String = when (reason) {
    GovernorReason.OK, GovernorReason.FIRST_RUN -> "new phrases will arrive tomorrow"
    GovernorReason.NO_HEADROOM -> "holding off on new phrases while this week settles"
    GovernorReason.BACKLOG_LIMIT -> "clearing the older phrases first"
    GovernorReason.POST_SKIP_WARMUP -> "a little more review unlocks new phrases"
    GovernorReason.LOW_ACCURACY -> "consolidating what you already have"
    GovernorReason.RETURN_MODE -> "easing back in, familiar phrases only"
    GovernorReason.SAFETY_VALVE -> "one new phrase, to keep things moving"
}
