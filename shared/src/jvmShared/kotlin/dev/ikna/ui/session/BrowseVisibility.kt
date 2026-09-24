package dev.ikna.ui.session

import kotlin.math.max
import kotlin.math.min

/** A viewport item small enough to unit-test without Compose lazy-list internals. */
data class BrowseViewportItem(
    val index: Int,
    val offset: Int,
    val size: Int
)

/**
 * Items count as seen only after a meaningful part is actually inside the
 * viewport. Lazy lists may compose/prefetch items outside the screen, so
 * composition itself must never spend Browse allowance or create an exposure.
 *
 * Half of the smaller of item-height and viewport-height is enough. This also
 * works for a card taller than the viewport, where half of the entire card could
 * never be visible at once.
 */
fun browseMeaningfullyVisibleIndices(
    viewportStart: Int,
    viewportEnd: Int,
    items: Iterable<BrowseViewportItem>
): List<Int> {
    val viewportSize = (viewportEnd - viewportStart).coerceAtLeast(0)
    if (viewportSize == 0) return emptyList()
    return items.mapNotNull { item ->
        if (item.size <= 0) return@mapNotNull null
        val itemStart = item.offset
        val itemEnd = item.offset + item.size
        val visible = (min(itemEnd, viewportEnd) - max(itemStart, viewportStart)).coerceAtLeast(0)
        val reference = min(item.size, viewportSize)
        if (visible * 2 >= reference) item.index else null
    }.distinct().sorted()
}
