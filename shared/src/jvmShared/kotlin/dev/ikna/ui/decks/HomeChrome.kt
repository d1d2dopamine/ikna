package dev.ikna.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import dev.ikna.ui.theme.IknaReadingCat
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
        if (showWordmark) IknaWordmark(height = 16.dp)
        Text(
            text = S.t("deck.004"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IknaReadingCat()
    }
}

@Composable
fun IknaHomeDock(
    leftHanded: Boolean,
    selected: HomeDestination?,
    fullWidth: Boolean,
    onNavigate: (HomeDestination) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val rim = RoundedCornerShape(22.dp)
    val order = if (leftHanded) HomeDestination.entries.asReversed() else HomeDestination.entries
    val fill = lerp(colors.surface, colors.outlineVariant, if (fullWidth) 0.08f else 0.12f)
    val shell = if (fullWidth) Modifier.background(fill) else Modifier.padding(Space.sm)
    Box(
        modifier = Modifier.fillMaxWidth().then(shell),
        contentAlignment = Alignment.Center
    ) {
        if (fullWidth) {
            HorizontalDivider(Modifier.align(Alignment.TopCenter), color = colors.outlineVariant)
        }
        val dock = if (fullWidth) Modifier else Modifier
            .shadow(3.dp, rim)
            .background(fill, rim)
            .border(1.dp, colors.outlineVariant.copy(alpha = 0.76f), rim)
        Row(
            modifier = dock
                .iknaInspect("Home dock")
                .padding(horizontal = 6.dp, vertical = if (fullWidth) 6.dp else 4.dp),
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
