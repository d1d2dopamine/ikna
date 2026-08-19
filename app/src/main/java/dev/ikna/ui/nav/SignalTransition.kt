package dev.ikna.ui.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import dev.ikna.ui.theme.Motion

/*
 * Route changes use Material Shared Axis X, but at a deliberately small scale.
 *
 * A forward destination arrives from the right while the old one gives way to
 * the left; Back mirrors both movements. Fourteen dp is enough to make the
 * relationship legible without turning an ordinary tab change into a carousel.
 * Opacity has one hand-off: the old route reaches zero before the new route
 * gains any opacity. Two complete interfaces are therefore never readable at the
 * same time. There is no hard edge, full-width shove, scan, crop, blur or spring.
 */

internal fun sharedAxisEnterOffset(forward: Boolean, travelPx: Int): Int =
    if (forward) travelPx else -travelPx

internal fun sharedAxisExitOffset(forward: Boolean, travelPx: Int): Int =
    -sharedAxisEnterOffset(forward, travelPx)

internal fun sharedAxisEnter(
    enabled: Boolean,
    forward: Boolean,
    travelPx: Int
): EnterTransition {
    if (!enabled) return EnterTransition.None
    val distance = sharedAxisEnterOffset(forward, travelPx.coerceAtLeast(0))
    return slideInHorizontally(
        animationSpec = tween(
            durationMillis = Motion.sharedAxisDurationMillis,
            easing = FastOutSlowInEasing
        ),
        initialOffsetX = { distance }
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = Motion.sharedAxisFadeInDurationMillis,
            delayMillis = Motion.sharedAxisFadeInDelayMillis,
            easing = LinearOutSlowInEasing
        ),
        initialAlpha = 0f
    )
}

internal fun sharedAxisExit(
    enabled: Boolean,
    forward: Boolean,
    travelPx: Int
): ExitTransition {
    if (!enabled) return ExitTransition.None
    val distance = sharedAxisExitOffset(forward, travelPx.coerceAtLeast(0))
    return slideOutHorizontally(
        animationSpec = tween(
            durationMillis = Motion.sharedAxisDurationMillis,
            easing = FastOutSlowInEasing
        ),
        targetOffsetX = { distance }
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = Motion.sharedAxisFadeOutDurationMillis,
            easing = FastOutLinearInEasing
        ),
        targetAlpha = 0f
    )
}
