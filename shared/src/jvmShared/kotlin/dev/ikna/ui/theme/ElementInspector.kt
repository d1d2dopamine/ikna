package dev.ikna.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/** One concrete node, not just a label that several repeated controls may share. */
private data class InspectedElement(val token: Any, val name: String)

@Stable
private class ElementInspectorState {
    private val hoveredElements = mutableListOf<InspectedElement>()

    var current by mutableStateOf<InspectedElement?>(null)
        private set

    /** Last entered nested element wins; removing it reveals its parent again. */
    fun show(token: Any, name: String) {
        hoveredElements.removeAll { it.token === token }
        hoveredElements += InspectedElement(token, name)
        current = hoveredElements.lastOrNull()
    }

    fun hide(token: Any) {
        hoveredElements.removeAll { it.token === token }
        current = hoveredElements.lastOrNull()
    }

    fun clear() {
        hoveredElements.clear()
        current = null
    }
}

private val LocalElementInspector = staticCompositionLocalOf<ElementInspectorState?> { null }

/**
 * Development-only overlay controlled from Settings → Rare.
 *
 * It does not walk private Compose internals. Named modifiers report the exact
 * node under the cursor, draw a one-pixel boundary around it and place its
 * stable UI name in the bottom-right corner. With the switch off the local is
 * null, so no hover interaction or extra drawing is attached to the interface.
 */
@Composable
fun IknaElementInspector(
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    val state = remember { ElementInspectorState() }
    LaunchedEffect(enabled) {
        if (!enabled) state.clear()
    }

    CompositionLocalProvider(LocalElementInspector provides state.takeIf { enabled }) {
        Box(Modifier.fillMaxSize()) {
            content()
            val inspected = state.current
            if (enabled && inspected != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .widthIn(max = 460.dp)
                        .zIndex(100f)
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
                        .border(1.dp, MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "UI · ${inspected.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Gives a visual node a stable name without changing its size or input role. */
@Composable
fun Modifier.iknaInspect(name: String): Modifier {
    val state = LocalElementInspector.current ?: return this
    val token = remember { Any() }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val active = state.current?.token === token
    val outline = MaterialTheme.colorScheme.primary

    LaunchedEffect(hovered, name, state, token) {
        if (hovered) state.show(token, name) else state.hide(token)
    }
    DisposableEffect(state, token) {
        onDispose { state.hide(token) }
    }

    return this
        // This shared Foundation primitive already powers the app's desktop
        // button hover states and also compiles for Android. A desktop-only
        // pointer extension here would break :shared's Android target.
        .hoverable(interactionSource = interaction)
        .drawWithContent {
            drawContent()
            if (active) {
                drawRect(
                    color = outline.copy(alpha = 0.82f),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
}
