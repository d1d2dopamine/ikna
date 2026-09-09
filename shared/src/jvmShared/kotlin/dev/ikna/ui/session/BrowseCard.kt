package dev.ikna.ui.session

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.ikna.domain.session.SessionCard
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.Motion
import dev.ikna.ui.theme.Space
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val BROWSE_EXIT_X = 1400f
private const val BROWSE_FLING_SPEED = 800f
private const val BROWSE_ROTATION_DIVISOR = 72f

/** Pure gesture decision: no left movement can become a Browse advance. */
fun browseAdvances(
    x: Float,
    velocityX: Float = 0f,
    threshold: Float = SWIPE_THRESHOLD
): Boolean {
    val line = if (threshold > 0f) threshold else SWIPE_THRESHOLD
    return x >= line || (x > line / 3f && velocityX >= BROWSE_FLING_SPEED)
}

/**
 * A neutral one-way card gesture.
 *
 * Right advances; left always settles. There are no grade rails, success/error
 * colours, haptics or review signals because Browse records only visibility.
 */
@Composable
fun BrowseableCard(
    key: String,
    animations: Boolean,
    threshold: Float = SWIPE_THRESHOLD,
    onNext: () -> Unit,
    content: @Composable () -> Unit
) {
    val offsetX = remember(key) { Animatable(0f) }
    val drag = remember(key) { mutableStateOf(0f) }
    val flying = remember(key) { mutableStateOf(false) }
    val arrival = remember(key) { Animatable(if (animations) 0f else 1f) }
    val tracker = remember(key) { VelocityTracker() }
    val scope = rememberCoroutineScope()
    val nextNow = rememberUpdatedState(onNext)
    val shift: () -> Float = { if (flying.value) offsetX.value else drag.value }
    val line = if (threshold > 0f) threshold else SWIPE_THRESHOLD

    LaunchedEffect(key, animations) {
        if (animations) arrival.animateTo(1f, Motion.arrive) else arrival.snapTo(1f)
    }

    val nextLabel = S.t("a11y.013")
    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(nextLabel) {
                        nextNow.value()
                        true
                    }
                )
            }
            .pointerInput(key, animations) {
                detectDragGestures(
                    onDragStart = {
                        if (!flying.value) tracker.resetTracking()
                    },
                    onDrag = { change, delta ->
                        if (!flying.value) {
                            tracker.addPosition(change.uptimeMillis, change.position)
                            drag.value += delta.x
                        }
                    },
                    onDragCancel = {
                        if (!flying.value) {
                            scope.launch { browseSettle(offsetX, drag, flying, animations) }
                        }
                    },
                    onDragEnd = {
                        if (!flying.value) {
                            val velocity = tracker.calculateVelocity()
                            val advances = browseAdvances(drag.value, velocity.x, line)
                            scope.launch {
                                flying.value = true
                                offsetX.snapTo(drag.value)
                                if (advances) {
                                    if (animations) {
                                        offsetX.animateTo(
                                            BROWSE_EXIT_X,
                                            Motion.thrown(abs(velocity.x)),
                                            initialVelocity = velocity.x
                                        )
                                    }
                                    nextNow.value()
                                    offsetX.snapTo(0f)
                                } else if (animations) {
                                    offsetX.animateTo(0f, Motion.settle, initialVelocity = velocity.x)
                                } else {
                                    offsetX.snapTo(0f)
                                }
                                drag.value = 0f
                                flying.value = false
                            }
                        }
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val landed = arrival.value.coerceIn(0f, 1f)
                    val travelled = shift()
                    translationX = travelled
                    translationY = (1f - landed) * 16.dp.toPx()
                    rotationZ = travelled / BROWSE_ROTATION_DIVISOR
                    val grow = 0.972f + 0.028f * landed
                    scaleX = grow
                    scaleY = grow
                    alpha = landed
                }
        ) {
            content()
        }
    }
}

private suspend fun browseSettle(
    x: Animatable<Float, AnimationVector1D>,
    drag: androidx.compose.runtime.MutableState<Float>,
    flying: androidx.compose.runtime.MutableState<Boolean>,
    animations: Boolean,
    velocity: Velocity = Velocity.Zero
) {
    flying.value = true
    x.snapTo(drag.value)
    if (animations) x.animateTo(0f, Motion.settle, initialVelocity = velocity.x) else x.snapTo(0f)
    drag.value = 0f
    flying.value = false
}

/** The always-revealed card used by both Android and desktop Browse screens. */
@Composable
fun BrowseChunkCard(
    card: SessionCard,
    transcription: String?,
    sourceLabel: String?,
    onSource: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        ChunkCard(
            label = S.t("browse.005"),
            prompt = card.prompt,
            answer = card.answer,
            promptTarget = card.promptTarget,
            answerTarget = null,
            promptTranscription = transcription,
            answerTranscription = null,
            hint = null,
            sourceLabel = sourceLabel,
            onSource = onSource,
            revealed = true,
            showTapHint = false,
            progress = { 0f },
            onTap = {},
            tapEnabled = false,
            modifier = Modifier.fillMaxSize()
        )
        Text(
            text = S.t("browse.002"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = Space.lg, vertical = Space.md)
        )
    }
}
