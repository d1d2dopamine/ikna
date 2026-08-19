package dev.ikna.ui.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import dev.ikna.ui.theme.Motion

/*
 * Navigation should disappear behind the decision to navigate.
 *
 * The first 0.8 draft tried to turn every route change into a bright field scan.
 * That made an ordinary tab change louder than the screen it opened. The final
 * motion is deliberately classic: one quiet cross-fade, no moving edge, no
 * geometric crop, no flash and no direction to decode.
 */

internal fun classicEnter(enabled: Boolean): EnterTransition {
    if (!enabled) return EnterTransition.None
    return fadeIn(
        animationSpec = tween(
            durationMillis = Motion.screenFadeInDurationMillis,
            delayMillis = 12,
            easing = LinearOutSlowInEasing
        ),
        initialAlpha = 0f
    )
}

internal fun classicExit(enabled: Boolean): ExitTransition {
    if (!enabled) return ExitTransition.None
    return fadeOut(
        animationSpec = tween(
            durationMillis = Motion.screenFadeOutDurationMillis,
            easing = FastOutLinearInEasing
        ),
        targetAlpha = 0f
    )
}
