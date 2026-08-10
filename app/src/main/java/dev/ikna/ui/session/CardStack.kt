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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.ikna.domain.fsrs.Rating
import dev.ikna.ui.theme.Motion
import dev.ikna.ui.theme.Space
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

private const val EXIT_X = 1400f

/** How far the card leans as it travels. Larger divisor, calmer rotation. */
private const val ROTATION_DIVISOR = 64f

/** The strip at the bottom that the two words live in, and never leave. */
private val RAIL_HEIGHT = 40.dp

/**
 * One card, thrown left or right.
 *
 * The layer that moves and the layer that explains are now two different things,
 * and that is the whole fix.
 *
 * The words "know" and "do not know" used to be drawn *inside* the card, at its
 * left and right edges. The card is the object that travels: pull it to the
 * right and the word that tells you what the right side means travels with it,
 * off the screen, at exactly the moment you are looking for it. The label under
 * the thumb was the first thing to disappear. Both words now belong to the
 * screen instead: the card slides underneath them and they do not move by a
 * pixel, so the map stays where the map was.
 *
 * A card whose back you have not seen cannot be graded. Pulling one sideways
 * shows the answer -- the same act as tapping it, done with the hand that was
 * already moving -- and then springs back. The grade needs a second, deliberate
 * throw. Without that rule the peek and the answer are the same gesture, and
 * every look at the back would record something.
 *
 * A spring, not a line. Letting go of a card that did not travel far enough runs
 * a slightly underdamped spring, which is the motion of an object with mass.
 *
 * Speed counts as an answer. See [decideRating]: a flick that travels a third of
 * the threshold but leaves the finger at 900px/s is an answer, and the card goes
 * with it at the speed it was thrown.
 *
 * A tick at the line. The moment the drag crosses into an answer, one short
 * haptic pulse -- before the finger lifts, while the decision can still be taken
 * back. This is the part that closes the loop without a single number on screen:
 * the hand knows the answer landed, so the eye does not have to go looking for
 * confirmation.
 *
 * And the whole thing has a keyboard-and-screen-reader twin: two custom
 * accessibility actions carrying the same two verbs, because an interface whose
 * only verb is a swipe is an interface that cannot be used with TalkBack on.
 */
@Composable
fun SwipeableCard(
    key: String,
    revealed: Boolean,
    animations: Boolean,
    haptics: Boolean,
    railsAtRest: Boolean,
    onReveal: () -> Unit,
    onRate: (Rating) -> Unit,
    content: @Composable (progress: () -> Float) -> Unit
) {
    val offsetX = remember(key) { Animatable(0f) }
    // 0 while the card is still arriving, 1 once it has taken its place.
    val arrival = remember(key) { Animatable(if (animations) 0f else 1f) }
    val armed = remember(key) { mutableStateOf<Rating?>(null) }
    // Was the back of the card visible when this gesture began? Read once, at
    // the start, so a pull that reveals the answer cannot also grade it.
    val gradable = remember(key) { mutableStateOf(false) }
    val tracker = remember(key) { VelocityTracker() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // The gesture detector outlives recomposition, so everything it reads has to
    // be read through a handle that stays current. Capturing the values directly
    // would freeze the card in the state it had when the drag loop started.
    val revealedNow = rememberUpdatedState(revealed)
    val revealNow = rememberUpdatedState(onReveal)
    val rateNow = rememberUpdatedState(onRate)

    LaunchedEffect(key, animations) {
        if (animations) arrival.animateTo(1f, Motion.arrive) else arrival.snapTo(1f)
    }

    val progress: () -> Float = { (offsetX.value / SWIPE_THRESHOLD).coerceIn(-1f, 1f) }

    val missAction = S.t("a11y.008")
    val keepAction = S.t("a11y.009")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(keepAction) {
                        rateNow.value(Rating.GOOD)
                        true
                    },
                    CustomAccessibilityAction(missAction) {
                        rateNow.value(Rating.AGAIN)
                        true
                    }
                )
            }
            // On the whole screen rather than on the card: the card moves, and a
            // touch area that moves with it stops accepting the second half of a
            // long drag.
            .pointerInput(key) {
                detectDragGestures(
                    onDragStart = {
                        tracker.resetTracking()
                        armed.value = null
                        gradable.value = revealedNow.value
                    },
                    onDrag = { change, delta ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val next = offsetX.value + delta.x
                        scope.launch { offsetX.snapTo(next) }
                        if (gradable.value) {
                            // One tick when the gesture becomes an answer, one when
                            // it stops being one. Never a stream of them.
                            val candidate = armedRating(next)
                            if (candidate != armed.value) {
                                if (candidate != null && haptics) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                armed.value = candidate
                            }
                        } else if (!revealedNow.value && abs(next) >= PEEK_TRAVEL) {
                            revealNow.value()
                        }
                    },
                    onDragCancel = {
                        armed.value = null
                        scope.launch { settle(offsetX, animations) }
                    },
                    onDragEnd = {
                        val velocity = tracker.calculateVelocity()
                        val rating =
                            if (gradable.value) decideRating(offsetX.value, 0f, velocity.x) else null
                        armed.value = null
                        if (rating == null) {
                            scope.launch { settle(offsetX, animations, velocity) }
                        } else {
                            if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                if (animations) throwOut(offsetX, rating, velocity)
                                rateNow.value(rating)
                                offsetX.snapTo(0f)
                            }
                        }
                    }
                )
            }
    ) {
        // The travelling layer.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val landed = arrival.value.coerceIn(0f, 1f)
                    translationX = offsetX.value
                    translationY = (1f - landed) * 16.dp.toPx()
                    rotationZ = offsetX.value / ROTATION_DIVISOR
                    val grow = 0.972f + 0.028f * landed
                    scaleX = grow
                    scaleY = grow
                    alpha = landed
                }
        ) {
            content(progress)
        }

        // The fixed layer. Nothing here ever moves.
        if (revealed) {
            CommitEdge(progress = progress)
            AnswerRails(progress = progress, atRest = if (railsAtRest) 0.32f else 0f)
        }
    }
}

/** Back to rest, carrying whatever speed the finger left behind. */
private suspend fun settle(
    x: Animatable<Float, AnimationVector1D>,
    animations: Boolean,
    velocity: Velocity = Velocity.Zero
) {
    if (!animations) {
        x.snapTo(0f)
        return
    }
    x.animateTo(0f, Motion.settle, initialVelocity = velocity.x)
}

/** Off the screen in the direction it was thrown, at the speed it was thrown. */
private suspend fun throwOut(
    x: Animatable<Float, AnimationVector1D>,
    rating: Rating,
    velocity: Velocity
) {
    val target = if (rating == Rating.AGAIN) -EXIT_X else EXIT_X
    x.animateTo(target, Motion.thrown(abs(velocity.x)), initialVelocity = velocity.x)
}

/**
 * The card itself: a rectangle that takes the whole screen.
 *
 * Not a small rounded tile floating in padding, and not a panel on a background
 * either -- there is no window around the text and nothing behind it. The card is
 * the screen, and the only thing separating it from the edges is the text inset.
 *
 * The chunk is set large and left-aligned like a line in a book, because that is
 * how it will be read in the wild, and because centred text of unpredictable
 * length moves its own first letter around between cards.
 *
 * One card, one look. There used to be a second variant for a card returning
 * from the amnesty pool -- the same card with " - CAME BACK" appended to its
 * label. It marked the user's absence on the material they were in the middle of
 * remembering, which is the one place this app had promised never to keep score.
 *
 * [tapEnabled] rather than a look at [revealed]: turning the card over is a tap
 * on the card itself, and that surface has to stop answering once the answer is
 * already on screen.
 */
@Composable
fun ChunkCard(
    label: String,
    prompt: String,
    answer: String,
    hint: String?,
    revealed: Boolean,
    showTapHint: Boolean,
    progress: () -> Float,
    onTap: () -> Unit,
    tapEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    // Which way the card is going, as a wash of colour rather than a verdict.
    // Accent for "kept it", muted for "lost it": a red tint on a card you just
    // failed is a punishment animation, and this app does not have those.
    val kept = MaterialTheme.colorScheme.primary
    val lost = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .clickable(enabled = tapEnabled, onClick = onTap)
    ) {
        // Drawn rather than recomposed: the wash follows the finger frame by
        // frame, and rebuilding the card's text on every one of those frames to
        // change one alpha would be the most expensive way to tint a rectangle.
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    val p = progress()
                    val strength = abs(p)
                    if (strength <= 0.05f) return@drawBehind
                    val color = if (p > 0f) kept else lost
                    drawRect(color = color.copy(alpha = 0.10f * strength))
                }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Space.lg)
                .padding(top = Space.lg, bottom = RAIL_HEIGHT)
        ) {
            Text(
                text = label.uppercase(),
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
    }
}

/**
 * A bar on the edge the card is being thrown at.
 *
 * It grows with the gesture and reaches full strength exactly where the answer
 * registers, so the edge is a progress bar for the decision itself. It is drawn
 * on the screen, not on the card, which is why it is still there when the card
 * has travelled halfway out of the frame.
 */
@Composable
private fun BoxScope.CommitEdge(progress: () -> Float) {
    val kept = MaterialTheme.colorScheme.primary
    val lost = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .matchParentSize()
            .drawBehind {
                val p = progress()
                val strength = abs(p)
                if (strength <= 0.05f) return@drawBehind
                val band = 4.dp.toPx() * strength
                val color = if (p > 0f) kept else lost
                val left = if (p > 0f) size.width - band else 0f
                drawRect(
                    color = color.copy(alpha = 0.35f + 0.65f * strength),
                    topLeft = Offset(left, 0f),
                    size = Size(band, size.height)
                )
            }
    )
}

/**
 * The two words, at the bottom corners they belong to.
 *
 * They sit in the screen's frame, below the reading area and above the bar with
 * the way out, which is the one band of the screen a thumb passes through on
 * every single answer. Left is muted, right is the accent colour, always -- the
 * colour is part of the word rather than a reaction to the gesture, so the two
 * sides can be told apart before anything has been touched.
 *
 * [atRest] is how visible they are when the card is still: present while the
 * movement is being learned, and gone afterwards. Dragging always brings the
 * matching word up to full strength and fades the other one out, so the label
 * under the thumb says what will happen if the finger lifts here -- and, more
 * usefully, what will happen if it does not.
 */
@Composable
private fun BoxScope.AnswerRails(progress: () -> Float, atRest: Float) {
    RailLabel(
        text = S.t("card.003"),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        alpha = { max(atRest * (1f - max(0f, progress())), max(0f, -progress())) },
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = Space.lg, bottom = Space.md)
    )
    RailLabel(
        text = S.t("card.004"),
        color = MaterialTheme.colorScheme.primary,
        alpha = { max(atRest * (1f + min0(progress())), max(0f, progress())) },
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = Space.lg, bottom = Space.md)
    )
}

/** Negative part of a signed progress, as a value in -1..0. */
private fun min0(v: Float): Float = if (v < 0f) v else 0f

@Composable
private fun RailLabel(
    text: String,
    color: Color,
    alpha: () -> Float,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        modifier = modifier.graphicsLayer { this.alpha = alpha().coerceIn(0f, 1f) }
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
