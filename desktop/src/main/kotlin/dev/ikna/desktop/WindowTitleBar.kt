package dev.ikna.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.Edge
import dev.ikna.ui.theme.IknaPalette
import dev.ikna.ui.theme.IknaWordmark
import dev.ikna.ui.theme.LocalIknaControlColors
import java.awt.Toolkit

internal const val WINDOWS_TITLE_BAR_HEIGHT = 45
private enum class WindowMark { MINIMIZE, MAXIMIZE, RESTORE, CLOSE }

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun WindowScope.IknaWindowTitleBar(state: WindowState, palette: IknaPalette, onClose: () -> Unit) {
    val active = LocalWindowInfo.current.isWindowFocused
    val ink = if (active) palette.ink else palette.muted
    val maximize: () -> Unit = {
        state.placement = if (state.placement == WindowPlacement.Maximized) {
            WindowPlacement.Floating
        } else {
            WindowPlacement.Maximized
        }
    }
    val clickInterval = remember {
        (Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval") as? Int)?.toLong() ?: 500L
    }
    Column(Modifier.fillMaxWidth().background(palette.background)) {
        Row(Modifier.fillMaxWidth().height((WINDOWS_TITLE_BAR_HEIGHT - 1).dp)) {
            // Controls are siblings, not children of the draggable surface.
            WindowDraggableArea(
                modifier = Modifier.weight(1f).fillMaxHeight().pointerInput(state, clickInterval) {
                    val clicks = TitleBarClicks(clickInterval, 4.dp.toPx())
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val point = event.changes.firstOrNull() ?: continue
                            when (event.type) {
                                PointerEventType.Press -> clicks.press(
                                    point.uptimeMillis, point.position.x, point.position.y,
                                    window.x, window.y, event.buttons.isPrimaryPressed
                                )
                                PointerEventType.Move -> clicks.move(point.position.x, point.position.y, window.x, window.y)
                                PointerEventType.Release -> if (clicks.release(
                                    point.uptimeMillis, point.position.x, point.position.y, window.x, window.y
                                )) maximize()
                                else -> Unit
                            }
                            // Observe only; consuming would break the drag handler.
                        }
                    }
                }
            ) {
                Box(Modifier.fillMaxWidth().fillMaxHeight().padding(start = Edge),
                    contentAlignment = Alignment.CenterStart) {
                    IknaWordmark(height = 16.dp, ink = ink, dot = if (active) palette.accent else palette.muted)
                }
            }
            WindowButton(WindowMark.MINIMIZE, S.t("pc.017"), ink, palette) { state.isMinimized = true }
            val maximized = state.placement == WindowPlacement.Maximized
            WindowButton(if (maximized) WindowMark.RESTORE else WindowMark.MAXIMIZE,
                S.t(if (maximized) "pc.019" else "pc.018"), ink, palette, maximize)
            WindowButton(WindowMark.CLOSE, S.t("pc.020"), ink, palette, onClose)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.line))
    }
}

@Composable
private fun WindowButton(mark: WindowMark, label: String, ink: Color, palette: IknaPalette, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val colors = LocalIknaControlColors.current
    val fill = if (pressed) colors.pressed
        else if (hovered) colors.hover else Color.Transparent
    Box(Modifier.size(44.dp).background(fill)
        .border(1.dp, if (focused) colors.mark else Color.Transparent)
        .semantics { contentDescription = label }
        .hoverable(interaction)
        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(14.dp)) {
            val line = 1.dp.toPx()
            val color = if (focused) colors.mark else ink
            val w = size.width
            val h = size.height
            when (mark) {
                WindowMark.MINIMIZE -> drawLine(color, Offset(line, h / 2), Offset(w - line, h / 2), line)
                WindowMark.MAXIMIZE -> drawRect(color, Offset(line, line), Size(w - 2 * line, h - 2 * line), style = Stroke(line))
                WindowMark.RESTORE -> {
                    drawRect(color, Offset(4 * line, line), Size(w - 5 * line, h - 5 * line), style = Stroke(line))
                    drawRect(palette.background, Offset(line, 4 * line), Size(w - 5 * line, h - 5 * line))
                    drawRect(color, Offset(line, 4 * line), Size(w - 5 * line, h - 5 * line), style = Stroke(line))
                }
                WindowMark.CLOSE -> {
                    drawLine(color, Offset(line, line), Offset(w - line, h - line), line)
                    drawLine(color, Offset(w - line, line), Offset(line, h - line), line)
                }
            }
        }
    }
}
