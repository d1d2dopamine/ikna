package dev.ikna.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Coordinates every visible Signal Frame in one IknaTheme.
 *
 * Two details matter here:
 *  - phase is shared, so moving the pointer quickly between controls never
 *    restarts a local animation from zero;
 *  - when nested interactive regions are hovered at the same time, the smallest
 *    region owns hover feedback. A button inside a deck row therefore suppresses
 *    the row frame until the pointer leaves that button.
 */
@Stable
internal class SignalFrameCoordinator {
    val phase = Animatable(0f)

    private val areas = mutableMapOf<Any, Float>()
    private val hovered = mutableMapOf<Any, Long>()
    private val moving = mutableSetOf<Any>()
    private val outerFrames = mutableStateMapOf<Any, SignalFrameOverlayEntry>()
    private var hoverSequence = 0L

    var activeHoverOwner by mutableStateOf<Any?>(null)
        private set

    var movingCount by mutableIntStateOf(0)
        private set

    fun setArea(id: Any, area: Float) {
        if (area <= 0f) return
        areas[id] = area
        if (hovered.containsKey(id)) chooseHoverOwner()
    }

    fun setHovered(id: Any, value: Boolean) {
        if (value) {
            if (!hovered.containsKey(id)) {
                hoverSequence += 1
                hovered[id] = hoverSequence
            }
        } else {
            hovered.remove(id)
        }
        chooseHoverOwner()
    }

    fun setMoving(id: Any, value: Boolean) {
        val changed = if (value) moving.add(id) else moving.remove(id)
        if (changed) movingCount = moving.size
    }

    fun registerOuter(id: Any, entry: SignalFrameOverlayEntry) {
        outerFrames[id] = entry
    }

    fun outerEntries(): Collection<SignalFrameOverlayEntry> = outerFrames.values

    fun unregister(id: Any) {
        areas.remove(id)
        hovered.remove(id)
        outerFrames.remove(id)
        if (moving.remove(id)) movingCount = moving.size
        if (activeHoverOwner === id) chooseHoverOwner()
    }

    private fun chooseHoverOwner() {
        activeHoverOwner = chooseSignalFrameHoverOwner(hovered, areas)
    }
}

@Stable
internal class SignalFrameOverlayEntry {
    var boundsInRoot by mutableStateOf<Rect?>(null)
    var alphaState by mutableStateOf<State<Float>?>(null)
    var pausedPhaseState by mutableStateOf<State<Float>?>(null)
    var moving by mutableStateOf(false)
    var pressed by mutableStateOf(false)
    var cornerRadius by mutableStateOf(8.dp)
}

internal val LocalSignalFrameCoordinator = staticCompositionLocalOf { SignalFrameCoordinator() }

/** One phase clock for every Signal Frame. It sleeps when no frame needs motion. */
@Composable
internal fun rememberSignalFrameCoordinator(motionEnabled: Boolean): SignalFrameCoordinator {
    val coordinator = remember { SignalFrameCoordinator() }
    val hasMovingFrames = coordinator.movingCount > 0

    LaunchedEffect(coordinator, motionEnabled, hasMovingFrames) {
        if (!motionEnabled || !hasMovingFrames) return@LaunchedEffect
        while (true) {
            if (coordinator.phase.value >= 0.9999f) coordinator.phase.snapTo(0f)
            val remaining = (1f - coordinator.phase.value).coerceIn(0.001f, 1f)
            coordinator.phase.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = (Motion.signalFrameCycleDurationMillis * remaining)
                        .roundToInt()
                        .coerceAtLeast(1),
                    easing = LinearEasing
                )
            )
            coordinator.phase.snapTo(0f)
        }
    }
    return coordinator
}

enum class SignalFramePlacement {
    Inner,
    Outer
}

private val SIGNAL_FRAME_OUTER_GAP = 2.dp

/**
 * Draws every OUTER Signal Frame above the themed content.
 *
 * Drawing an outer frame in the control's own DrawScope lets any ancestor clip
 * it: LazyColumn clips its viewport, animated panes clip their transition, and
 * tight rows can clip a switch on one side. Registering the control's full
 * bounds and painting the frame here keeps the exact same geometry and motion
 * while escaping those unrelated layout clips. INNER frames remain local.
 */
@Composable
internal fun BoxScope.SignalFrameOverlay(coordinator: SignalFrameCoordinator) {
    val colors = LocalIknaControlColors.current
    val motionEnabled = LocalIknaMotionEnabled.current
    var overlayOriginInRoot by remember { mutableStateOf(Offset.Zero) }

    Canvas(
        modifier = Modifier
            .matchParentSize()
            .onGloballyPositioned { coordinates ->
                overlayOriginInRoot = coordinates.positionInRoot()
            }
    ) {
        val stroke = 1.dp.toPx()
        val edge = SIGNAL_FRAME_OUTER_GAP.toPx() + stroke / 2f

        coordinator.outerEntries().forEach { entry ->
            val bounds = entry.boundsInRoot ?: return@forEach
            val alpha = entry.alphaState?.value ?: 0f
            if (alpha <= 0f || bounds.width <= 0f || bounds.height <= 0f) return@forEach

            val left = bounds.left - overlayOriginInRoot.x - edge
            val top = bounds.top - overlayOriginInRoot.y - edge
            val width = bounds.width + edge * 2f
            val height = bounds.height + edge * 2f
            val radius = minOf(entry.cornerRadius.toPx(), width / 2f, height / 2f)
            val intervalsDp = signalFrameIntervalsDp(
                widthDp = if (density == 0f) 0f else width / density,
                heightDp = if (density == 0f) 0f else height / density
            )
            val dash = FloatArray(intervalsDp.size) { index -> intervalsDp[index] * density }
            val cycle = dash.sum().coerceAtLeast(1f)
            val phase = when {
                !motionEnabled -> 0f
                entry.moving -> coordinator.phase.value
                entry.pressed -> entry.pausedPhaseState?.value ?: 0f
                else -> 0f
            }

            drawRoundRect(
                color = colors.mark.copy(alpha = alpha),
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = CornerRadius(radius, radius),
                style = Stroke(
                    width = stroke,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(
                        intervals = dash,
                        phase = phase * cycle
                    )
                )
            )
        }
    }
}

/**
 * Pointer/focus affordance shared by interactive Ikna controls.
 *
 * The control itself keeps Ikna's flat square geometry. Borderless hit targets
 * keep the one-pixel signal just inside their bounds; controls that already
 * own a visible boundary place the same signal just outside it. Hover and
 * selected navigation may travel around the perimeter; keyboard focus remains
 * visible but still, pressing freezes the current phase, and disabling
 * animations keeps the same frame static.
 *
 * Focus on a bordered (Outer) control is the exception: the overlay escapes
 * every viewport clip on purpose, so a focus ring painted there would trail a
 * scrolled-away control over unrelated text. Those controls carry focus in
 * their own boundary instead, and the overlay answers only to the pointer.
 *
 * [selected] is reserved for persistent navigation state on borderless (Inner)
 * controls: it says "you are here", while hover says "you can act here".
 * Ordinary selected toggles/chips already have their own static state and
 * should not set it.
 */
@Composable
fun Modifier.iknaSignalFrame(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    selected: Boolean = false,
    cornerRadius: Dp = 8.dp,
    placement: SignalFramePlacement = SignalFramePlacement.Inner
): Modifier {
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val motionEnabled = LocalIknaMotionEnabled.current
    val colors = LocalIknaControlColors.current
    val coordinator = LocalSignalFrameCoordinator.current
    val frameId = remember { Any() }
    val outerEntry = remember(frameId) { SignalFrameOverlayEntry() }

    val hoverOwner by remember(coordinator, frameId) {
        derivedStateOf { coordinator.activeHoverOwner === frameId }
    }
    val suppressedByNestedHover = enabled && hovered && !hoverOwner && !selected
    // Bordered (Outer) controls show keyboard focus through their own boundary;
    // the root overlay carries only pointer presence. Overlay frames
    // intentionally escape viewport clipping, so a focus ring that lived there
    // would keep painting over unrelated text after the control scrolls away.
    val active = enabled && when (placement) {
        SignalFramePlacement.Outer -> hoverOwner || pressed
        SignalFramePlacement.Inner -> hoverOwner || focused || pressed || selected
    }
    val moving = enabled && motionEnabled && !pressed && when (placement) {
        SignalFramePlacement.Outer -> hoverOwner
        SignalFramePlacement.Inner -> hoverOwner || selected
    }

    SideEffect {
        coordinator.setHovered(frameId, enabled && hovered)
        coordinator.setMoving(frameId, moving)
    }
    DisposableEffect(coordinator, frameId, placement) {
        if (placement == SignalFramePlacement.Outer) {
            coordinator.registerOuter(frameId, outerEntry)
        }
        onDispose { coordinator.unregister(frameId) }
    }

    val pausedPhaseState = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(pressed) {
        if (pressed) pausedPhaseState.floatValue = coordinator.phase.value
    }

    val alphaState = animateFloatAsState(
        targetValue = when {
            !active -> 0f
            pressed -> 1f
            hoverOwner && selected -> 1f
            hoverOwner -> 0.98f
            selected -> 0.58f
            else -> 0.82f
        },
        animationSpec = when {
            !motionEnabled || suppressedByNestedHover -> snap()
            // An Outer frame that lost the pointer must not linger at a root
            // position the control has already scrolled away from: even a
            // 55ms fade paints the ring over unrelated text. Cut it at once.
            !active && placement == SignalFramePlacement.Outer -> snap()
            active -> tween(durationMillis = Motion.signalFrameFadeInDurationMillis)
            else -> tween(durationMillis = Motion.signalFrameFadeOutDurationMillis)
        },
        label = "signal-frame-alpha"
    )

    if (placement == SignalFramePlacement.Outer) {
        SideEffect {
            outerEntry.alphaState = alphaState
            outerEntry.pausedPhaseState = pausedPhaseState
            outerEntry.moving = moving
            outerEntry.pressed = pressed
            outerEntry.cornerRadius = cornerRadius
        }
    }

    return this
        .onSizeChanged { size ->
            coordinator.setArea(frameId, size.width.toFloat() * size.height.toFloat())
        }
        .then(
            if (placement == SignalFramePlacement.Outer) {
                Modifier.onGloballyPositioned { coordinates ->
                    // positionInRoot + the unmodified layout size intentionally
                    // ignores ancestor clipping. The frame belongs to the whole
                    // control even when a scroll/transition viewport clips paint.
                    val topLeft = coordinates.positionInRoot()
                    outerEntry.boundsInRoot = Rect(
                        left = topLeft.x,
                        top = topLeft.y,
                        right = topLeft.x + coordinates.size.width,
                        bottom = topLeft.y + coordinates.size.height
                    )
                }
            } else {
                Modifier
            }
        )
        .drawWithCache {
            if (placement == SignalFramePlacement.Outer) {
                onDrawWithContent { drawContent() }
            } else {
                val stroke = 1.dp.toPx()
                val edgeOffset = stroke / 2f + 1.dp.toPx()
                val width = size.width - edgeOffset * 2f
                val height = size.height - edgeOffset * 2f
                val radius = minOf(cornerRadius.toPx(), width / 2f, height / 2f)
                val intervalsDp = signalFrameIntervalsDp(
                    widthDp = if (density == 0f) 0f else width / density,
                    heightDp = if (density == 0f) 0f else height / density
                )
                val dash = FloatArray(intervalsDp.size) { index -> intervalsDp[index] * density }
                val cycle = dash.sum().coerceAtLeast(1f)

                onDrawWithContent {
                    drawContent()
                    val alpha = alphaState.value
                    if (alpha <= 0f || width <= 0f || height <= 0f) return@onDrawWithContent

                    val phase = when {
                        !motionEnabled -> 0f
                        moving -> coordinator.phase.value
                        pressed -> pausedPhaseState.floatValue
                        else -> 0f
                    }
                    drawRoundRect(
                        color = colors.mark.copy(alpha = alpha),
                        topLeft = Offset(edgeOffset, edgeOffset),
                        size = Size(width, height),
                        cornerRadius = CornerRadius(radius, radius),
                        style = Stroke(
                            width = stroke,
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(
                                intervals = dash,
                                phase = phase * cycle
                            )
                        )
                    )
                }
            }
        }
}
