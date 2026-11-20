package dev.ikna.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
    ADD(IknaGlyph.PLUS, "a11y.004")
}

/** The content keeps its available height when the heading and dock trade places. */
@Composable
fun IknaHomeChrome(
    dockAtTop: Boolean,
    leftHanded: Boolean,
    selected: HomeDestination? = null,
    onNavigate: (HomeDestination) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        if (dockAtTop) HomeDock(leftHanded, selected, onNavigate) else HomeHeading()
        content()
        if (dockAtTop) HomeHeading() else HomeDock(leftHanded, selected, onNavigate)
    }
}

@Composable
private fun HomeHeading() {
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = BarHeight)
            .padding(start = Edge, end = Space.sm)
            .iknaInspect("Home heading"),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IknaWordmark(height = 16.dp)
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
private fun HomeDock(
    leftHanded: Boolean,
    selected: HomeDestination?,
    onNavigate: (HomeDestination) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val rim = RoundedCornerShape(22.dp)
    val order = if (leftHanded) HomeDestination.entries.asReversed() else HomeDestination.entries
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.sm, vertical = Space.sm),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .iknaInspect("Home dock")
                .shadow(3.dp, rim)
                .background(lerp(colors.surface, colors.outlineVariant, 0.12f), rim)
                .border(1.dp, colors.outlineVariant.copy(alpha = 0.76f), rim)
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
