package dev.ikna.ui.session

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
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
 *
 * Two Float animatables instead of one Offset animatable: an Offset animation
 * needs a TwoWayConverter that only exists as an extension on Offset.Companion,
 * and two independent floats read exactly as well here.
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
    val offsetX = remember(key) { Animatable(0f) }
    val offsetY = remember(key) { Animatable(0f) }
    val threshold = 140f
    // Plain state, not a local var: the drag callback outlives recomposition.
    val crossed = remember(key) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .graphicsLayer {
                translationX = offsetX.value
                translationY = offsetY.value
                rotationZ = (offsetX.value / 60f).coerceIn(-6f, 6f)
            }
            .pointerInput(key, enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDrag = { _, delta ->
                        scope.launch {
                            offsetX.snapTo(offsetX.value + delta.x)
                            offsetY.snapTo(offsetY.value + delta.y)
                            val past = abs(offsetX.value) > threshold ||
                                abs(offsetY.value) > threshold
                            if (past && !crossed.value) {
                                crossed.value = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else if (!past) {
                                crossed.value = false
                            }
                        }
                    },
                    onDragEnd = {
                        val x = offsetX.value
                        val y = offsetY.value
                        val rating = when {
                            x < -threshold -> Rating.AGAIN
                            x > threshold -> Rating.GOOD
                            y < -threshold -> Rating.EASY
                            y > threshold -> Rating.HARD
                            else -> null
                        }
                        scope.launch {
                            if (rating != null) {
                                // Smooth slide out, no throw physics: the card
                                // leaves calmly and the next one is simply there.
                                val exitX = when (rating) {
                                    Rating.AGAIN -> -1400f
                                    Rating.GOOD -> 1400f
                                    else -> 0f
                                }
                                val exitY = when (rating) {
                                    Rating.EASY -> -1800f
                                    Rating.HARD -> 1800f
                                    else -> 0f
                                }
                                if (exitX != 0f) {
                                    offsetX.animateTo(exitX, tween(180))
                                } else {
                                    offsetY.animateTo(exitY, tween(180))
                                }
                                onRate(rating)
                                offsetX.snapTo(0f)
                                offsetY.snapTo(0f)
                            } else {
                                offsetX.animateTo(0f, tween(160))
                                offsetY.animateTo(0f, tween(160))
                            }
                            crossed.value = false
                        }
                    }
                )
            }
    ) {
        content(
            (offsetX.value / threshold).coerceIn(-1f, 1f),
            (offsetY.value / threshold).coerceIn(-1f, 1f)
        )
    }
}
