package dev.ikna.ui.session

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import dev.ikna.domain.fsrs.Rating
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Swipe-to-rate.
 *
 * Deliberately hand-rolled rather than pulled from a card-stack library: the
 * behaviour needed here is narrow (no throw physics, no fling, no rotation
 * beyond a few degrees) and every published Compose stack library is either
 * abandoned or built around Tinder-style dismissal.
 *
 * The point of the gesture is initiation cost. A swipe is roughly 200ms and
 * almost no effort; choosing between four buttons is a decision, and decisions
 * are the expensive part of starting.
 *
 *   left  -> Again      right -> Good
 *   down  -> Hard       up    -> Easy
 */
@Composable
fun SwipeableCard(
    key: Any,
    enabled: Boolean,
    onRate: (Rating) -> Unit,
    content: @Composable (progressX: Float, progressY: Float) -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val offset = remember(key) { Animatable(Offset.Zero, Offset.VectorConverter) }
    val threshold = remember { 140f }
    var crossed = remember(key) { false }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .graphicsLayer {
                translationX = offset.value.x
                translationY = offset.value.y
                rotationZ = (offset.value.x / 60f).coerceIn(-6f, 6f)
            }
            .pointerInput(key, enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDrag = { _, delta ->
                        scope.launch {
                            offset.snapTo(offset.value + delta)
                            val past = abs(offset.value.x) > threshold || abs(offset.value.y) > threshold
                            if (past && !crossed) {
                                crossed = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else if (!past) {
                                crossed = false
                            }
                        }
                    },
                    onDragEnd = {
                        val o = offset.value
                        val rating = when {
                            o.x < -threshold -> Rating.AGAIN
                            o.x > threshold -> Rating.GOOD
                            o.y < -threshold -> Rating.EASY
                            o.y > threshold -> Rating.HARD
                            else -> null
                        }
                        scope.launch {
                            if (rating != null) {
                                // Smooth slide out, no throw physics: the card
                                // leaves calmly and the next one is simply there.
                                val exit = when (rating) {
                                    Rating.AGAIN -> Offset(-1400f, 0f)
                                    Rating.GOOD -> Offset(1400f, 0f)
                                    Rating.EASY -> Offset(0f, -1800f)
                                    Rating.HARD -> Offset(0f, 1800f)
                                }
                                offset.animateTo(exit, tween(180))
                                onRate(rating)
                                offset.snapTo(Offset.Zero)
                            } else {
                                offset.animateTo(Offset.Zero, tween(160))
                            }
                            crossed = false
                        }
                    }
                )
            }
    ) {
        content(
            (offset.value.x / threshold).coerceIn(-1f, 1f),
            (offset.value.y / threshold).coerceIn(-1f, 1f)
        )
    }
}

val MaterialCardElevation = 2.dp
private val unusedTheme = MaterialTheme
