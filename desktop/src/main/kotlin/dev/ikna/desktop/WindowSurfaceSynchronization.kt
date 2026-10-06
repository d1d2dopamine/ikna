package dev.ikna.desktop

import androidx.compose.ui.awt.ComposeWindow
import java.awt.Component
import java.awt.Container
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowStateListener
import java.io.File
import javax.swing.SwingUtilities
import org.jetbrains.skia.Canvas
import org.jetbrains.skiko.ExperimentalSkikoApi
import org.jetbrains.skiko.GraphicsApi
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate

/**
 * Compose 1.8.2's SkiaLayer may immediately record a resized Direct3D picture
 * before its doLayout updates the child Canvas and ComposeScene constraints.
 * Prepare that layout in the same recording callback, before the scene draws.
 */
@OptIn(ExperimentalSkikoApi::class)
internal fun installWindowSurfaceSynchronization(
    window: ComposeWindow,
    layout: WindowFrameLayout,
    traceHome: File?
): AutoCloseable? {
    check(SwingUtilities.isEventDispatchThread())
    val layer = findWindowSkiaLayer(window.contentPane)
    val original = layer?.renderDelegate
    if (layer == null || original == null) {
        logLine("window surface-sync=unavailable; main SkiaLayer/delegate not found")
        return null
    }
    layout.invalidate()
    val trace = traceHome?.let {
        WindowFrameTrace(File(it, "logs/window-surface.log").toPath()) { error ->
            logLine("window surface trace failed: " + error.javaClass.simpleName)
        }
    }
    val wrapper = object : SkikoRenderDelegate {
        private var followupSamples = 0
        private var picture = 0L

        override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
            val scale = layer.contentScale
            val direct3D = layer.renderApi == GraphicsApi.DIRECT3D
            val needed = direct3D && layout.needsPreparation(width, height, scale)
            val sample = trace?.isRecording == true && (needed || followupSamples > 0)
            val childBefore = if (sample) layer.canvas.bounds else null
            val rootBefore = if (sample) "${layout.composeWidth}x${layout.composeHeight}" else null
            val started = if (sample) System.nanoTime() else 0L
            var prepared = false
            var completed = false
            try {
                if (direct3D) {
                    prepared = layout.render(width, height, scale,
                        { layer.doLayout() },
                        { original.onRender(canvas, width, height, nanoTime) }
                    )
                } else {
                    // Do not change fallback renderer behaviour. Prepare again
                    // if a future frame switches back to the Direct3D path.
                    layout.invalidate()
                    original.onRender(canvas, width, height, nanoTime)
                }
                completed = true
            } finally {
                picture++
                if (sample) {
                    trace?.record("picture=$picture tNs=$nanoTime framePx=${width}x$height scale=$scale" +
                        " window=${boundsText(window.bounds)} layer=${boundsText(layer.bounds)}" +
                        " childBefore=${boundsText(childBefore!!)} childAfter=${boundsText(layer.canvas.bounds)}" +
                        " rootBeforePx=$rootBefore rootAfterPx=${layout.composeWidth}x${layout.composeHeight}" +
                        " prepared=$prepared completed=$completed recordUs=${(System.nanoTime() - started) / 1000}")
                }
                if (needed) followupSamples = 2 else if (followupSamples > 0) followupSamples--
            }
        }
    }
    layer.renderDelegate = wrapper
    val boundsListener = object : ComponentAdapter() {
        override fun componentResized(event: ComponentEvent) {
            layout.invalidate()
            if (trace?.isRecording == true) {
                trace.record("native-resize tNs=${System.nanoTime()} window=${boundsText(window.bounds)}" +
                    " layer=${boundsText(layer.bounds)} child=${boundsText(layer.canvas.bounds)}")
            }
        }
    }
    val stateListener = WindowStateListener { event ->
        layout.invalidate()
        if (trace?.isRecording == true) {
            trace.record("native-state tNs=${System.nanoTime()} old=${event.oldState} new=${event.newState}" +
                " window=${boundsText(window.bounds)}")
        }
    }
    window.addComponentListener(boundsListener)
    window.addWindowStateListener(stateListener)
    logLine("window surface-sync=before-picture-v1 renderer=" + layer.renderApi +
        " trace=" + if (trace != null) "logs/window-surface.log" else "off")
    return AutoCloseable {
        check(SwingUtilities.isEventDispatchThread())
        window.removeComponentListener(boundsListener)
        window.removeWindowStateListener(stateListener)
        if (layer.renderDelegate === wrapper) layer.renderDelegate = original
        trace?.close()
    }
}

private fun findWindowSkiaLayer(component: Component): SkiaLayer? {
    if (component is SkiaLayer) return component
    if (component is Container) {
        for (child in component.components) findWindowSkiaLayer(child)?.let { return it }
    }
    return null
}

private fun boundsText(bounds: Rectangle): String =
    "${bounds.x},${bounds.y},${bounds.width},${bounds.height}"
