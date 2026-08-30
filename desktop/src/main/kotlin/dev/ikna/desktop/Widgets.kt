package dev.ikna.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ikna.data.repo.DeckSummary
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaPalette

/**
 * The app draws square, bordered, text-only controls. These are the desktop
 * equivalents rather than Material buttons, so the window looks like the phone
 * instead of like a generic Compose sample.
 */
@Composable
fun IknaButton(
    label: String,
    palette: IknaPalette,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    onClick: () -> Unit
) {
    val ink = if (!enabled) palette.muted else if (filled) palette.background else palette.ink
    Box(
        modifier
            .background(if (filled && enabled) palette.accent else palette.background)
            .border(BorderStroke(1.dp, palette.line))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = ink, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun PaneButton(
    label: String,
    selected: Boolean,
    palette: IknaPalette,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(if (selected) palette.panel else palette.background)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (selected) palette.ink else palette.muted,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
fun DeckRow(
    deck: DeckSummary,
    due: Int,
    selected: Boolean,
    palette: IknaPalette,
    onStudy: () -> Unit,
    onOpen: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (selected) palette.panel else palette.background)
            .clickable { onStudy() }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                deck.title,
                color = if (deck.isActive) palette.ink else palette.muted,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            if (due > 0) {
                Text(due.toString(), color = palette.accent, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(
                deck.lang.uppercase() + "  " + deck.known + " / " + deck.total,
                color = palette.muted,
                fontSize = 10.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                S.t("a11y.010"),
                color = palette.muted,
                fontSize = 10.sp,
                modifier = Modifier.clickable { onOpen() }
            )
        }
    }
}

@Composable
fun Centered(text: String, palette: IknaPalette) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = palette.muted, fontSize = 13.sp)
    }
}

@Composable
fun SectionTitle(text: String, palette: IknaPalette) {
    Text(text, color = palette.muted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
}
