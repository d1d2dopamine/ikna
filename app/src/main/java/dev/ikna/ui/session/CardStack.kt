package dev.ikna.ui.session

import dev.ikna.ui.text.S

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.ikna.domain.fsrs.Rating
import dev.ikna.ui.theme.Motion
import dev.ikna.ui.theme.Space
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

private const val EXIT_X = 1400f
private const val EXIT_Y = 1800f

/** How far the card leans as it travels. Larger divisor, calmer rotation. */
private const val ROTATION_DIVISOR = 64f

/**
 * One card, thrown in four directions.
 *
 * Three things changed here and all three are about the hand rather than the eye.
 *
 * A spring, not a line. Letting go of a card that did not travel far enough used
 * to run a 180ms linear tween back to centre — the motion of an object on a
 * string. A slightly underdamped spring is the motion of an object with mass, and
 * the difference is the whole difference between an interface that feels made and
 * one that feels assembled.
 *
 * Speed counts as an answer. See [decideRating]: a flick that travels a third of
 * the threshold but leaves the finger at 900px/s is an answer, and the card goes
 * with it at the speed it was thrown.
 *
 * A tick at the line. The moment the drag crosses into an answer, one short
 * haptic pulse — before the finger lifts, while the decision can still be taken
 * back. This is the part that closes the loop for an ADHD user without a single
 * number on screen: the hand knows the answer landed, so the eye does not have to
 * go looking for confirmation.
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
    // 0 while the card is still arriving, 1 once it has taken its place.
    val arrival = remember(key) { Animatable(if (animations) 0f else 1f) }
    val armed = remember(key) { mutableStateOf<Rating?>(null) }
    val tracker = remember(key) { VelocityTracker() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(key, animations) {
        if (animations) arrival.animateTo(1f, Motion.arrive) else arrival.snapTo(1f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val landed = arrival.value.coerceIn(0f, 1f)
                translationX = offsetX.value
                translationY = offsetY.value + (1f - landed) * 16.dp.toPx()
                rotationZ = offsetX.value / ROTATION_DIVISOR
                val grow = 0.972f + 0.028f * landed
                scaleX = grow
                scaleY = grow
                alpha = landed
            }
            .pointerInput(key, enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = {
                        tracker.resetTracking()
                        armed.value = null
                    },
                    onDrag = { change, delta ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val nextX = offsetX.value + delta.x
                        val nextY = offsetY.value + delta.y
                        scope.launch {
                            offsetX.snapTo(nextX)
                            offsetY.snapTo(nextY)
                        }
                        // One tick when the gesture becomes an answer, one when it
                        // stops being one. Never a stream of them.
                        val candidate = armedRating(nextX, nextY)
                        if (candidate != armed.value) {
                            if (candidate != null && haptics) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            armed.value = candidate
                        }
                    },
                    onDragCancel = {
                        armed.value = null
                        scope.launch { settle(offsetX, offsetY, animations) }
                    },
                    onDragEnd = {
                        val velocity = tracker.calculateVelocity()
                        val rating = decideRating(offsetX.value, offsetY.value, velocity.x, velocity.y)
                        armed.value = null
                        if (rating == null) {
                            scope.launch { settle(offsetX, offsetY, animations, velocity) }
                        } else {
                            if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                if (animations) throwOut(offsetX, offsetY, rating, velocity)
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
            (offsetX.value / SWIPE_THRESHOLD).coerceIn(-1f, 1f),
            (offsetY.value / SWIPE_THRESHOLD).coerceIn(-1f, 1f)
        )
    }
}

/** Back to rest, carrying whatever speed the finger left behind. */
private suspend fun settle(
    x: Animatable<Float, AnimationVector1D>,
    y: Animatable<Float, AnimationVector1D>,
    animations: Boolean,
    velocity: Velocity = Velocity.Zero
) {
    if (!animations) {
        x.snapTo(0f)
        y.snapTo(0f)
        return
    }
    coroutineScope {
        launch { x.animateTo(0f, Motion.settle, initialVelocity = velocity.x) }
        launch { y.animateTo(0f, Motion.settle, initialVelocity = velocity.y) }
    }
}

/** Off the screen in the direction it was thrown, at the speed it was thrown. */
private suspend fun throwOut(
    x: Animatable<Float, AnimationVector1D>,
    y: Animatable<Float, AnimationVector1D>,
    rating: Rating,
    velocity: Velocity
) {
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
    val speed = max(abs(velocity.x), abs(velocity.y))
    val spec = Motion.thrown(speed)
    coroutineScope {
        launch { x.animateTo(targetX, spec, initialVelocity = velocity.x) }
        launch { y.animateTo(targetY, spec, initialVelocity = velocity.y) }
    }
}

/**
 * The card itself: a rectangle that takes the whole screen.
 *
 * Not a small rounded tile floating in padding, and not a panel on a background
 * either — there is no window around the text and nothing behind it. The card is
 * the screen, and the only thing separating it from the edges is the text inset.
 *
 * The chunk is set large and left-aligned like a line in a book, because that is
 * how it will be read in the wild, and because centred text of unpredictable
 * length moves its own first letter around between cards.
 *
 * [tapEnabled] rather than a look at `revealed`: the same surface means "show me
 * the answer" on a question and "understood" on an introduction, and the
 * introduction is revealed from the start.
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
    onTap: () -> Unit,
    tapEnabled: Boolean,
    modifier: Modifier = Modifier,
    showSwipeLegend: Boolean = false
) {
    // Which way the card is going, as a wash of colour rather than a verdict.
    // Accent for the two "kept it" directions, muted for the two "lost it" ones:
    // a red tint on a card you just failed is a punishment animation, and this
    // app does not have those.
    val kept = MaterialTheme.colorScheme.primary
    val lost = MaterialTheme.colorScheme.onSurfaceVariant
    val tint = when {
        progressX < -0.35f -> lost.copy(alpha = 0.10f)
        progressX > 0.35f -> kept.copy(alpha = 0.10f)
        progressY < -0.35f -> kept.copy(alpha = 0.06f)
        progressY > 0.35f -> lost.copy(alpha = 0.06f)
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .clickable(enabled = tapEnabled, onClick = onTap)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(tint)
        )

        // The edge the card is heading for, drawn as a bar that fills as the
        // gesture commits. Colour doing the work a word would otherwise do.
        if (revealed) {
            CommitEdge(progressX = progressX, progressY = progressY, kept = kept, lost = lost)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Space.lg, vertical = Space.lg)
        ) {
            Text(
                text = if (fromAmnesty) label.uppercase() + S.t("card.001") else label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = prompt,
                style = promptStyle(prompt),
                color = MaterialTheme.colorScheme.onBackground
            )

            if (revealed) {
                Spacer(Modifier.height(Space.lg))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(Modifier.height(Space.lg))
                Text(
                    text = answer,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (!hint.isNullOrBlank()) {
                    Spacer(Modifier.height(Space.md))
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (showTapHint) {
                Spacer(Modifier.height(Space.lg))
                Text(
                    text = S.t("card.002"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.weight(1f))
        }

        if (revealed) {
            SwipeLegend(
                progressX = progressX,
                progressY = progressY,
                base = if (showSwipeLegend) 0.30f else 0f
            )
        }
    }
}

/**
 * A bar on the edge the card is being thrown at.
 *
 * It grows with the gesture and reaches full strength exactly where the answer
 * registers, so the edge is a progress bar for the decision itself. Four bars
 * would be noise; only the live one is drawn.
 */
@Composable
private fun BoxScope.CommitEdge(
    progressX: Float,
    progressY: Float,
    kept: Color,
    lost: Color
) {
    val ax = abs(progressX)
    val ay = abs(progressY)
    val horizontal = ax >= ay
    val strength = if (horizontal) ax else ay
    if (strength <= 0.05f) return

    val color = when {
        horizontal && progressX > 0f -> kept
        horizontal -> lost
        progressY < 0f -> kept
        else -> lost
    }
    val alignment = when {
        horizontal && progressX > 0f -> Alignment.CenterEnd
        horizontal -> Alignment.CenterStart
        progressY < 0f -> Alignment.TopCenter
        else -> Alignment.BottomCenter
    }
    val band = (4.dp.value * strength).dp

    Box(
        modifier = Modifier
            .align(alignment)
            .then(
                if (horizontal) Modifier.fillMaxHeight().width(band)
                else Modifier.fillMaxWidth().height(band)
            )
            .background(color.copy(alpha = 0.35f + 0.65f * strength))
    )
}

/**
 * The four directions, written at the four edges they belong to.
 *
 * The gesture has always understood four directions while the buttons offered
 * two, so half of the answers were reachable only by accident. This is the map,
 * drawn where the map actually is.
 *
 * [base] is how visible it is at rest: faint while the gesture is still being
 * learned, invisible afterwards. Dragging always brings the matching edge up to
 * full strength, so the label under your thumb tells you what will happen if you
 * let go — and, more importantly, what will happen if you do not.
 */
@Composable
private fun BoxScope.SwipeLegend(progressX: Float, progressY: Float, base: Float) {
    val ax = abs(progressX)
    val ay = abs(progressY)
    val left = if (progressX < 0f && ax >= ay) ax else 0f
    val right = if (progressX > 0f && ax >= ay) ax else 0f
    val up = if (progressY < 0f && ay > ax) ay else 0f
    val down = if (progressY > 0f && ay > ax) ay else 0f

    val kept = MaterialTheme.colorScheme.primary
    val lost = MaterialTheme.colorScheme.onSurfaceVariant

    LegendLabel(
        text = S.t("card.003"),
        active = up,
        base = base,
        activeColor = kept,
        modifier = Modifier.align(Alignment.TopCenter).padding(top = Space.md)
    )
    LegendLabel(
        text = S.t("card.004"),
        active = down,
        base = base,
        activeColor = lost,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Space.md)
    )
    LegendLabel(
        text = S.t("card.005"),
        active = left,
        base = base,
        activeColor = lost,
        modifier = Modifier.align(Alignment.CenterStart).padding(start = Space.md)
    )
    LegendLabel(
        text = S.t("card.006"),
        active = right,
        base = base,
        activeColor = kept,
        modifier = Modifier.align(Alignment.CenterEnd).padding(end = Space.md)
    )
}

@Composable
private fun LegendLabel(
    text: String,
    active: Float,
    base: Float,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    val alpha = max(base, active)
    if (alpha <= 0.01f) return
    val resting = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = (if (active > 0.45f) activeColor else resting).copy(alpha = alpha),
        maxLines = 1,
        modifier = modifier
    )
}

/**
 * Big by default, smaller only when the chunk is genuinely long.
 *
 * Three steps and no auto-fitting: a size that changes by one point per card is
 * a size that never looks deliberate.
 */
@Composable
private fun promptStyle(prompt: String): TextStyle = when {
    prompt.length <= 20 -> MaterialTheme.typography.displayMedium
    prompt.length <= 42 -> MaterialTheme.typography.displaySmall
    else -> MaterialTheme.typography.headlineMedium
}
