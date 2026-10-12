package dev.ikna.ui.theme

internal data class SignalFrameFeedback(
    val frameVisible: Boolean,
    val frameMoving: Boolean,
    val pressVisible: Boolean
)

/** Press acknowledges the action locally; only hover/focus/location owns a frame. */
internal fun signalFrameFeedback(
    enabled: Boolean,
    placement: SignalFramePlacement,
    hoverOwner: Boolean,
    focused: Boolean,
    pressed: Boolean,
    selected: Boolean,
    motionEnabled: Boolean,
    pressFeedback: Boolean
): SignalFrameFeedback {
    val inner = placement == SignalFramePlacement.Inner
    return SignalFrameFeedback(
        frameVisible = enabled && (hoverOwner || (inner && (focused || selected))),
        frameMoving = enabled && motionEnabled && !pressed &&
            (hoverOwner || (inner && selected)),
        pressVisible = enabled && pressed && pressFeedback
    )
}
