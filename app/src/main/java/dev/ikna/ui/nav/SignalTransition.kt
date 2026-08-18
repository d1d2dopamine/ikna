package dev.ikna.ui.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import dev.ikna.ui.theme.Motion

/*
 * Navigation as a signal rewriting a field.
 *
 * A stock horizontal slide treats every screen as a sheet of paper. Ikna is
 * already made from rules, cells and hard edges, so its navigation follows that
 * vocabulary: the old field closes, the next opens from the same edge, and one
 * bright scan line marks the boundary. No blur, no bounce, no particles.
 */

internal fun signalEnter(direction: Int, enabled: Boolean): EnterTransition {
    if (!enabled) return EnterTransition.None
    val origin = if (direction >= 0) Alignment.Start else Alignment.End
    return expandHorizontally(
        animationSpec = tween(
            durationMillis = Motion.signalDurationMillis,
            easing = LinearOutSlowInEasing
        ),
        expandFrom = origin,
        clip = true
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = 90,
            delayMillis = 24,
            easing = LinearEasing
        ),
        initialAlpha = 0.72f
    )
}

internal fun signalExit(direction: Int, enabled: Boolean): ExitTransition {
    if (!enabled) return ExitTransition.None
    val destination = if (direction >= 0) Alignment.End else Alignment.Start
    return shrinkHorizontally(
        animationSpec = tween(
            durationMillis = Motion.signalExitDurationMillis,
            easing = FastOutLinearInEasing
        ),
        shrinkTowards = destination,
        clip = true
    ) + fadeOut(
        animationSpec = tween(durationMillis = 110, easing = FastOutLinearInEasing),
        targetAlpha = 0.68f
    )
}

/** The bright boundary travelling between the old field and the new one. */
@Composable
internal fun SignalSweep(
    routeKey: String?,
    direction: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(1f) }
    var previousRoute by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(routeKey) {
        val next = routeKey ?: return@LaunchedEffect
        val hadRoute = previousRoute != null
        previousRoute = next
        if (!enabled || !hadRoute) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = Motion.signalDurationMillis,
                easing = LinearOutSlowInEasing
            )
        )
    }
    LaunchedEffect(enabled) {
        if (!enabled) progress.snapTo(1f)
    }

    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.fillMaxSize()) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        val x = if (direction >= 0) size.width * p else size.width * (1f - p)
        val line = 2.dp.toPx()
        val trail = size.width * 0.075f
        if (direction >= 0) {
            val left = (x - trail).coerceAtLeast(0f)
            drawRect(
                color = accent.copy(alpha = 0.065f),
                topLeft = Offset(left, 0f),
                size = Size((x - left).coerceAtLeast(0f), size.height)
            )
        } else {
            val right = (x + trail).coerceAtMost(size.width)
            drawRect(
                color = accent.copy(alpha = 0.065f),
                topLeft = Offset(x, 0f),
                size = Size((right - x).coerceAtLeast(0f), size.height)
            )
        }
        drawRect(
            color = accent.copy(alpha = 0.92f),
            topLeft = Offset((x - line / 2f).coerceIn(0f, size.width - line), 0f),
            size = Size(line, size.height)
        )
    }
}
