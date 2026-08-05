package dev.ikna.ui.session

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.ikna.domain.fsrs.Rating
import dev.ikna.ui.theme.IknaAgain
import dev.ikna.ui.theme.IknaGood
import dev.ikna.ui.theme.IknaMuted
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

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
            .fillMaxSize()
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
 * The card itself: a rectangle that takes the whole screen.
 *
 * Not a small rounded tile floating in padding. That is what the comment said
 * before as well, while the code drew a panel-coloured fill and a 1dp outline
 * inside a 20dp margin — a window with the card inside it. The fill and the
 * outline are gone: the card is the screen, and the only thing separating it
 * from the edges is the text inset.
 *
 * The chunk is set large and left-aligned like a line in a book, because that is
 * how it will be read in the wild, and because centred text of unpredictable
 * length moves its own first letter around between cards.
 *
 * The whole surface is the reveal target, so there is nothing small to aim at.
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
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
    showSwipeLegend: Boolean = false
) {
    val tint = when {
        progressX < -0.35f -> IknaAgain.copy(alpha = 0.10f)
        progressX > 0.35f -> IknaGood.copy(alpha = 0.10f)
        progressY < -0.35f -> IknaGood.copy(alpha = 0.06f)
        progressY > 0.35f -> IknaAgain.copy(alpha = 0.06f)
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .clickable(enabled = !revealed, onClick = onReveal)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(tint)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 24.dp)
        ) {
            Text(
                text = if (fromAmnesty) label.uppercase() + " · ВЕРНУЛАСЬ" else label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = IknaMuted
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = prompt,
                style = promptStyle(prompt),
                color = MaterialTheme.colorScheme.onBackground
            )

            if (revealed) {
                Spacer(Modifier.height(22.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(Modifier.height(22.dp))
                Text(
                    text = answer,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (!hint.isNullOrBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = IknaMuted
                    )
                }
            } else if (showTapHint) {
                Spacer(Modifier.height(22.dp))
                Text(
                    text = "тап в любом месте",
                    style = MaterialTheme.typography.labelSmall,
                    color = IknaMuted
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

    LegendLabel(
        text = "↑ ЛЕГКО",
        active = up,
        base = base,
        activeColor = IknaGood,
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
    )
    LegendLabel(
        text = "↓ ТРУДНО",
        active = down,
        base = base,
        activeColor = IknaAgain,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
    )
    LegendLabel(
        text = "← НЕ ПОМНЮ",
        active = left,
        base = base,
        activeColor = IknaAgain,
        modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp)
    )
    LegendLabel(
        text = "ПОМНЮ →",
        active = right,
        base = base,
        activeColor = IknaGood,
        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp)
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
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = (if (active > 0.45f) activeColor else IknaMuted).copy(alpha = alpha),
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
