package dev.ikna.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.BarHeight
import dev.ikna.ui.theme.Edge
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaRule
import dev.ikna.ui.theme.IknaWordmark
import dev.ikna.ui.theme.Space
import dev.ikna.ui.theme.iknaInspect
import dev.ikna.ui.theme.iknaTileShape

enum class HomeDestination(val glyph: IknaGlyph, val labelKey: String) {
    STATS(IknaGlyph.BARS, "a11y.003"),
    SETTINGS(IknaGlyph.GEAR, "a11y.002"),
    SEARCH(IknaGlyph.SEARCH, "a11y.011"),
    ADD(IknaGlyph.PLUS, "a11y.004"),
    SUPPORT(IknaGlyph.SUPPORT, "support.title")
}

/** The upper dock belongs between Today and the deck list. */
@Composable
fun IknaHomeChrome(
    dockAtTop: Boolean,
    leftHanded: Boolean,
    selected: HomeDestination? = null,
    showWordmark: Boolean = true,
    fullWidthDock: Boolean = false,
    dockInWindow: Boolean = false,
    today: @Composable ColumnScope.() -> Unit = {},
    onNavigate: (HomeDestination) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        if (!dockAtTop) HomeHeading(showWordmark)
        today()
        if (dockAtTop) {
            IknaHomeDock(leftHanded, selected, fullWidthDock, onNavigate)
            Spacer(Modifier.height(Space.sm))
        }
        content()
        if (dockAtTop) {
            HomeHeading(showWordmark)
        } else if (!dockInWindow) {
            IknaHomeDock(leftHanded, selected, fullWidthDock, onNavigate)
        }
    }
}

@Composable
private fun HomeHeading(showWordmark: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = BarHeight)
            .padding(start = Edge, end = Space.sm)
            .iknaInspect("Home heading"),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            if (showWordmark) {
                IknaWordmark(modifier = Modifier.alignBy { it.measuredHeight }, height = 16.dp)
            }
            Text(
                text = S.t("deck.004"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.alignByBaseline(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun IknaHomeDock(
    leftHanded: Boolean,
    selected: HomeDestination?,
    fullWidth: Boolean,
    onNavigate: (HomeDestination) -> Unit,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val rim = RoundedCornerShape(22.dp)
    val order = if (leftHanded) HomeDestination.entries.asReversed() else HomeDestination.entries
    val fill = lerp(colors.surface, colors.outlineVariant, if (fullWidth) 0.08f else 0.12f)
    if (fullWidth) {
        // Match IknaBottomBar exactly (1dp rule + BarHeight row) so the divider aligns
        // pixel-for-pixel with adjacent panes, and host unified Back/session actions
        // when the desktop shell spans the dock across the full window.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
        ) {
            IknaRule()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BarHeight)
                    .padding(horizontal = Space.sm)
            ) {
                if (leading != null) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterStart),
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        content = leading
                    )
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .iknaInspect("Home dock")
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (destination in order) {
                        val active = destination == selected
                        IknaIconButton(
                            glyph = destination.glyph,
                            onClick = { onNavigate(destination) },
                            modifier = if (active) Modifier.background(
                                colors.primary.copy(alpha = 0.14f), iknaTileShape()
                            ) else Modifier,
                            selected = active,
                            color = if (active) colors.primary else colors.onSurface,
                            label = S.t(destination.labelKey)
                        )
                    }
                }
                if (trailing != null) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        content = trailing
                    )
                }
            }
        }
        return
    }
    Box(
        modifier = Modifier.fillMaxWidth().padding(Space.sm),
        contentAlignment = Alignment.Center
    ) {
        val dock = Modifier
            .shadow(3.dp, rim)
            .background(fill, rim)
            .border(1.dp, colors.outlineVariant.copy(alpha = 0.76f), rim)
        Row(
            modifier = dock
                .iknaInspect("Home dock")
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (destination in order) {
                val active = destination == selected
                IknaIconButton(
                    glyph = destination.glyph,
                    onClick = { onNavigate(destination) },
                    modifier = if (active) Modifier.background(
                        colors.primary.copy(alpha = 0.14f), iknaTileShape()
                    ) else Modifier,
                    selected = active,
                    color = if (active) colors.primary else colors.onSurface,
                    label = S.t(destination.labelKey)
                )
            }
        }
    }
}
