package dev.ikna.ui.session

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ikna.domain.fsrs.Rating
import dev.ikna.ui.theme.IknaAgain
import dev.ikna.ui.theme.IknaGood
import dev.ikna.ui.theme.IknaMuted
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val THRESHOLD = 140f
private const val EXIT_X = 1400f
private const val EXIT_Y = 1800f

/**
 * One card, draggable in four directions.
 *
 * Two independent float animatables instead of an Offset one: the Offset vector
 * converter is not in scope here, and two floats compose exactly as well.
 */
@Composable
fun SwipeableCard(
    key: String,
    enabled: Boolean,
    animations: Boolean,
    haptics: Boolean,
    onRate: (Rating) -> Unit,
    content: @Composable (progressX: Float, progressY: Float) -> Unit
) {
    val offsetX = remember(key) { Animatable(0f) }
    val offsetY = remember(key) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = offsetX.value
                translationY = offsetY.value
                rotationZ = offsetX.value / 60f
            }
            .pointerInput(key, enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDrag = { _, delta ->
                        scope.launch {
                            offsetX.snapTo(offsetX.value + delta.x)
                            offsetY.snapTo(offsetY.value + delta.y)
                        }
                    },
                    onDragCancel = {
                        scope.launch { offsetX.animateTo(0f, tween(180)) }
                        scope.launch { offsetY.animateTo(0f, tween(180)) }
                    },
                    onDragEnd = {
                        val rating = ratingFor(offsetX.value, offsetY.value)
                        if (rating == null) {
                            scope.launch { offsetX.animateTo(0f, tween(180)) }
                            scope.launch { offsetY.animateTo(0f, tween(180)) }
                        } else {
                            if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                if (animations) {
                                    val targetX = when (rating) {
                                        Rating.AGAIN -> -EXIT_X
                                        Rating.GOOD -> EXIT_X
                                        else -> 0f
                                    }
                                    val targetY = when (rating) {
                                        Rating.EASY -> -EXIT_Y
                                        Rating.HARD -> EXIT_Y
                                        else -> 0f
                                    }
                                    coroutineScope {
                                        launch { offsetX.animateTo(targetX, tween(160)) }
                                        launch { offsetY.animateTo(targetY, tween(160)) }
                                    }
                                }
                                onRate(rating)
                                offsetX.snapTo(0f)
                                offsetY.snapTo(0f)
                            }
                        }
                    }
                )
            }
    ) {
        content(
            (offsetX.value / THRESHOLD).coerceIn(-1f, 1f),
            (offsetY.value / THRESHOLD).coerceIn(-1f, 1f)
        )
    }
}

private fun ratingFor(x: Float, y: Float): Rating? = when {
    abs(x) >= abs(y) && x <= -THRESHOLD -> Rating.AGAIN
    abs(x) >= abs(y) && x >= THRESHOLD -> Rating.GOOD
    abs(y) > abs(x) && y <= -THRESHOLD -> Rating.EASY
    abs(y) > abs(x) && y >= THRESHOLD -> Rating.HARD
    else -> null
}

/**
 * The card itself. The whole surface is the reveal button now — the old version
 * had a small "show" link, which is a precise tap target for something the user
 * does on literally every card.
 */
@Composable
fun ChunkCard(
    label: String,
    prompt: String,
    answer: String,
    hint: String?,
    revealed: Boolean,
    showTapHint: Boolean,
    fromAmnesty: Boolean,
    progressX: Float,
    progressY: Float,
    onReveal: () -> Unit
) {
    val tint = when {
        progressX < -0.35f -> IknaAgain.copy(alpha = 0.16f)
        progressX > 0.35f -> IknaGood.copy(alpha = 0.16f)
        progressY < -0.35f -> IknaGood.copy(alpha = 0.10f)
        progressY > 0.35f -> IknaAgain.copy(alpha = 0.10f)
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = !revealed, onClick = onReveal)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(tint)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (fromAmnesty) label + " · вернулась" else label,
                style = MaterialTheme.typography.labelMedium,
                color = IknaMuted
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = prompt,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!revealed) {
                if (showTapHint) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "нажми на карточку",
                        style = MaterialTheme.typography.bodySmall,
                        color = IknaMuted
                    )
                }
            } else {
                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(IknaMuted.copy(alpha = 0.25f))
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = answer,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!hint.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = IknaMuted
                    )
                }
            }
        }
    }
}
