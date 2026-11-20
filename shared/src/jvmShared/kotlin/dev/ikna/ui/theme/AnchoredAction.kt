package dev.ikna.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** One popup anchored to its trigger, with a row that wraps choice groups when needed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IknaAnchoredAction(
    label: String,
    confirmLabel: String,
    enabled: Boolean,
    onConfirm: () -> Unit,
    choices: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var availableHeight by remember { mutableIntStateOf(Int.MAX_VALUE) }
    val trigger = remember { FocusRequester() }
    val open = expanded && enabled
    val close: () -> Unit = {
        expanded = false
        trigger.requestFocus()
    }
    val density = LocalDensity.current
    val gap = with(density) { 6.dp.roundToPx() }
    val edge = with(density) { 8.dp.roundToPx() }
    val position = remember(gap, edge) {
        AnchoredActionPosition(gap, edge) { availableHeight = it }
    }
    val maxPanelHeight = with(density) { availableHeight.toDp() }.coerceAtMost(380.dp)
    Box {
        IknaTextButton(
            label = label,
            onClick = { expanded = !expanded },
            enabled = enabled,
            modifier = Modifier.focusRequester(trigger).semantics {
                if (open) {
                    collapse {
                        close()
                        true
                    }
                } else if (enabled) {
                    expand {
                        expanded = true
                        true
                    }
                }
            }
        )
        if (open) {
            Popup(
                popupPositionProvider = position,
                onDismissRequest = close,
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = 470.dp),
                    shape = iknaCardShape(),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 4.dp
                ) {
                    Column(
                        Modifier.heightIn(max = maxPanelHeight).verticalScroll(rememberScrollState()).padding(Space.sm),
                        verticalArrangement = Arrangement.spacedBy(Space.xs)
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Space.xs),
                            verticalArrangement = Arrangement.spacedBy(Space.xs)
                        ) { choices() }
                        IknaTextButton(confirmLabel, modifier = Modifier.align(Alignment.End), onClick = {
                            close()
                            onConfirm()
                        })
                    }
                }
            }
        }
    }
}

internal class AnchoredActionPosition(
    private val gap: Int,
    private val edge: Int,
    private val onAvailableHeight: (Int) -> Unit = {}
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val start = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left
            else anchorBounds.right - popupContentSize.width
        val below = anchorBounds.bottom + gap
        val above = anchorBounds.top - gap
        val roomBelow = windowSize.height - edge - below
        val roomAbove = above - edge
        onAvailableHeight(maxOf(roomAbove, roomBelow, 0))
        val top = if (popupContentSize.height <= roomBelow || roomBelow >= roomAbove) below
            else above - popupContentSize.height
        val rightLimit = (windowSize.width - edge - popupContentSize.width).coerceAtLeast(0)
        val bottomLimit = (windowSize.height - edge - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(
            start.coerceIn(edge.coerceAtMost(rightLimit), rightLimit),
            top.coerceIn(edge.coerceAtMost(bottomLimit), bottomLimit)
        )
    }
}
