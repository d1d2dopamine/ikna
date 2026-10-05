package dev.ikna.ui.theme

/**
 * Dash rhythm for a Signal Frame, expressed in dp so geometry stays stable
 * across desktop scale factors and Android densities.
 *
 * Small controls need shorter gaps and marks than a whole deck row. Reusing the
 * large-row rhythm on a 32dp toggle can leave most of the perimeter inside one
 * gap, which makes the interaction affordance look as if it disappeared.
 */
internal fun signalFrameIntervalsDp(widthDp: Float, heightDp: Float): FloatArray {
    val shortest = minOf(widthDp, heightDp)
    val longest = maxOf(widthDp, heightDp)
    return when {
        shortest <= 34f || longest <= 64f -> floatArrayOf(8f, 5f, 4f, 5f, 12f, 5f)
        shortest <= 48f || longest <= 96f -> floatArrayOf(12f, 7f, 5f, 7f, 18f, 7f)
        shortest <= 72f || longest <= 180f -> floatArrayOf(18f, 10f, 6f, 10f, 26f, 10f)
        else -> floatArrayOf(24f, 14f, 8f, 14f, 36f, 14f)
    }
}

/** Smallest hovered target owns feedback; newest entry breaks equal-area ties. */
internal fun chooseSignalFrameHoverOwner(
    hovered: Map<Any, Long>,
    areas: Map<Any, Float>
): Any? {
    var best: Any? = null
    var bestArea = Float.MAX_VALUE
    var bestSequence = Long.MIN_VALUE
    for ((id, sequence) in hovered) {
        val area = areas[id] ?: Float.MAX_VALUE
        if (area < bestArea || (area == bestArea && sequence > bestSequence)) {
            best = id
            bestArea = area
            bestSequence = sequence
        }
    }
    return best
}

/** Clipped input bounds, separate from the unmodified outer drawing bounds. */
internal data class SignalFrameHoverBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun contains(x: Float, y: Float): Boolean =
        x >= left && x < right && y >= top && y < bottom
}

/**
 * Hover arbitration follows the current pointer, not just historical Enter/Exit
 * notifications. A stale small target must never suppress an unrelated button.
 * No palette or animation state lives here, so a repaint cannot reset input.
 */
internal class SignalFrameHoverState {
    private val areas = mutableMapOf<Any, Float>()
    private val bounds = mutableMapOf<Any, SignalFrameHoverBounds>()
    private val hovered = mutableMapOf<Any, Long>()
    private var sequence = 0L
    private var pointerX = Float.NaN
    private var pointerY = Float.NaN

    val owner: Any?
        get() = chooseSignalFrameHoverOwner(
            hovered.filterKeys { bounds[it]?.contains(pointerX, pointerY) == true },
            areas
        )

    fun setArea(id: Any, area: Float) {
        if (area > 0f) areas[id] = area
    }

    fun setBounds(id: Any, value: SignalFrameHoverBounds) {
        bounds[id] = value
    }

    fun move(id: Any, x: Float, y: Float) {
        pointerX = x
        pointerY = y
        if (!hovered.containsKey(id)) hovered[id] = ++sequence
    }

    fun exit(id: Any) {
        hovered.remove(id)
        if (hovered.isEmpty()) {
            pointerX = Float.NaN
            pointerY = Float.NaN
        }
    }

    fun detach(id: Any) {
        exit(id)
        bounds.remove(id)
    }

    fun remove(id: Any) {
        detach(id)
        areas.remove(id)
    }
}
