package dev.ikna.ui.theme

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * A bounded reading viewport that keeps its ordinary spacing until text needs
 * more room. Intrinsic height gives weighted spacers a finite column to divide;
 * overflow remains scrollable instead of squeezing or clipping the last action.
 * Use only with ordinary layouts, never a lazy list or other subcomposed content.
 */
@Composable
fun IknaScrollableColumn(
    modifier: Modifier = Modifier,
    scrollKey: Any? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit
) {
    val scroll = remember(scrollKey) { ScrollState(0) }
    BoxWithConstraints(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .heightIn(min = maxHeight)
                .height(IntrinsicSize.Min),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = verticalArrangement,
            content = content
        )
    }
}
