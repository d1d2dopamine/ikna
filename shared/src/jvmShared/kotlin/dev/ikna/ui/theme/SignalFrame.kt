package dev.ikna.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pointer/focus affordance shared by interactive Ikna controls.
 *
 * The control itself keeps Ikna's flat square geometry. This one-pixel signal
 * floats just inside it: a few uneven rounded segments rather than a Material
 * outline. Hover is the only state that travels around the perimeter; keyboard
 * focus stays visible but still, and pressing pauses the movement. When motion
 * is disabled the exact same frame appears without changing phase.
 *
 * The caller must use [interactionSource] for its hover/focus/click/toggle
 * interactions. Keeping one source is what makes the frame describe the actual
 * interactive object rather than a decorative wrapper around it.
 */
@Composable
fun Modifier.iknaSignalFrame(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    cornerRadius: Dp = 8.dp
): Modifier {
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val motionEnabled = LocalIknaMotionEnabled.current
    val colors = LocalIknaControlColors.current

    val active = enabled && (hovered || focused || pressed)
    val moving = enabled && hovered && !pressed && motionEnabled
    val phase = remember { Animatable(0f) }

    LaunchedEffect(moving) {
        if (!moving) {
            phase.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            phase.snapTo(0f)
            phase.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = Motion.signalFrameCycleDurationMillis,
                    easing = LinearEasing
                )
            )
        }
    }

    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = when {
            !active -> 0f
            pressed -> 1f
            hovered -> 0.94f
            else -> 0.82f
        },
        animationSpec = if (motionEnabled) tween(
            durationMillis = Motion.signalFrameFadeDurationMillis
        ) else snap(),
        label = "signal-frame-alpha"
    )

    return drawWithContent {
        drawContent()
        if (alpha <= 0f || size.width <= 0f || size.height <= 0f) return@drawWithContent

        val stroke = 1.dp.toPx()
        val inset = stroke / 2f + 1.dp.toPx()
        val width = size.width - inset * 2f
        val height = size.height - inset * 2f
        if (width <= 0f || height <= 0f) return@drawWithContent

        val dash = floatArrayOf(
            24.dp.toPx(), 14.dp.toPx(),
            8.dp.toPx(), 14.dp.toPx(),
            36.dp.toPx(), 14.dp.toPx()
        )
        val cycle = dash.sum()
        val radius = minOf(cornerRadius.toPx(), width / 2f, height / 2f)

        drawRoundRect(
            color = colors.mark.copy(alpha = alpha),
            topLeft = Offset(inset, inset),
            size = Size(width, height),
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(
                width = stroke,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(
                    intervals = dash,
                    phase = phase.value * cycle
                )
            )
        )
    }
}
