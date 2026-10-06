package dev.ikna.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.window.WindowDecorationDefaults
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowState
import java.awt.Cursor
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle

/** Same invisible edge zones as Compose 1.8.2; each drag step writes bounds once. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun FrameWindowScope.IknaWindowResizeOverlay(state: WindowState) {
    if (state.placement != WindowPlacement.Floating) return
    val sides = listOf(
        WindowBoundsEdits.LEFT to Cursor.W_RESIZE_CURSOR,
        WindowBoundsEdits.RIGHT to Cursor.E_RESIZE_CURSOR,
        WindowBoundsEdits.TOP to Cursor.N_RESIZE_CURSOR,
        WindowBoundsEdits.BOTTOM to Cursor.S_RESIZE_CURSOR,
        (WindowBoundsEdits.LEFT or WindowBoundsEdits.TOP) to Cursor.NW_RESIZE_CURSOR,
        (WindowBoundsEdits.RIGHT or WindowBoundsEdits.TOP) to Cursor.NE_RESIZE_CURSOR,
        (WindowBoundsEdits.LEFT or WindowBoundsEdits.BOTTOM) to Cursor.SW_RESIZE_CURSOR,
        (WindowBoundsEdits.RIGHT or WindowBoundsEdits.BOTTOM) to Cursor.SE_RESIZE_CURSOR
    )
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            for ((edge, cursor) in sides) {
                Box(Modifier.pointerHoverIcon(PointerIcon(Cursor(cursor)))
                    .pointerInput(window, state.placement, edge) {
                        awaitPointerEventScope {
                            var start: Rectangle? = null
                            var pointer: Point? = null
                            while (true) {
                                val event = awaitPointerEvent()
                                val floating = state.placement == WindowPlacement.Floating &&
                                    window.placement == WindowPlacement.Floating && !window.isMinimized
                                if (!floating || !event.buttons.isPrimaryPressed) {
                                    start = null
                                    pointer = null
                                    continue
                                }
                                val location = MouseInfo.getPointerInfo()?.location ?: continue
                                if (event.type == PointerEventType.Press) {
                                    start = window.bounds
                                    pointer = location
                                    event.changes.forEach { it.consume() }
                                } else if (event.type == PointerEventType.Move) {
                                    val bounds = start ?: continue
                                    val origin = pointer ?: continue
                                    WindowBoundsEdits.applyIfChanged(window, WindowBoundsEdits.resize(
                                        bounds, location.x - origin.x, location.y - origin.y,
                                        edge, window.minimumSize
                                    ))
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    })
            }
        }
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val edge = WindowDecorationDefaults.ResizerThickness.roundToPx()
        val sizes = listOf(
            edge to (height - 2 * edge), edge to (height - 2 * edge),
            (width - 2 * edge) to edge, (width - 2 * edge) to edge,
            edge to edge, edge to edge, edge to edge, edge to edge
        )
        val measured = measurables.mapIndexed { index, measurable ->
            val (w, h) = sizes[index]
            measurable.measure(Constraints.fixed(w.coerceAtLeast(0), h.coerceAtLeast(0)))
        }
        layout(width, height) {
            measured[0].place(0, edge)
            measured[1].place(width - edge, edge)
            measured[2].place(edge, 0)
            measured[3].place(edge, height - edge)
            measured[4].place(0, 0)
            measured[5].place(width - edge, 0)
            measured[6].place(0, height - edge)
            measured[7].place(width - edge, height - edge)
        }
    }
}
