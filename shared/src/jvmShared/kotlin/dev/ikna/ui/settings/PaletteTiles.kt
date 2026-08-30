package dev.ikna.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaPalettes

private const val WORDMARK = "ikna"

/**
 * The palettes, shown as themselves.
 *
 * A list of names is not a choice of colours -- nobody knows what "Cliva" is
 * until they have already switched to it and switched back. Each tile is
 * painted in the palette it offers: its own background, the wordmark in its
 * ink, and a bar of accent next to a bar of muted, which is every colour the
 * palette has. The name sits under the tile, in the app's own ink.
 *
 * Selection is a heavier border rather than a tint or a tick, for the same
 * reason everything else here is: a tick would have to be drawn in some colour,
 * and on a tile whose whole point is its own colours there is no colour left
 * to use.
 *
 * Lifted out of the phone's settings screen unchanged so that the desktop shows
 * the same grid rather than a second design that drifts from it. The only thing
 * a window is allowed to change is how many tiles fit on a row.
 */
@Composable
fun IknaPaletteTiles(
    selectedId: String,
    light: Boolean,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    tileHeight: Dp = 64.dp,
    gap: Dp = 12.dp
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val line = MaterialTheme.colorScheme.outline
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val perRow = if (columns < 1) 1 else columns

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        IknaPalettes.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { spec ->
                    val p = spec.palette(light)
                    val selected = spec.id == selectedId
                    // The tap zone is the tile and its own name, never the empty
                    // space beside a name as short as "NULL": a click aimed at
                    // nothing must not repaint the entire app.
                    Column(modifier = Modifier.weight(1f)) {
                        Column(
                            modifier = Modifier
                                .clickable(
                                    role = Role.RadioButton,
                                    onClickLabel = S.t(spec.nameKey),
                                    onClick = { onPick(spec.id) }
                                )
                                .fillMaxWidth()
                                .height(tileHeight)
                                .background(p.background)
                                .border(if (selected) 2.dp else 1.dp, if (selected) ink else line)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = WORDMARK,
                                style = MaterialTheme.typography.bodySmall,
                                color = p.ink,
                                maxLines = 1
                            )
                            Spacer(Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(
                                    modifier = Modifier
                                        .weight(2f)
                                        .height(8.dp)
                                        .background(p.accent)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(8.dp)
                                        .background(p.muted)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = S.t(spec.nameKey),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) ink else muted,
                            maxLines = 1,
                            modifier = Modifier.clickable { onPick(spec.id) }
                        )
                    }
                }
                // A last row of two must not stretch its tiles to the width of
                // three, or the grid stops being a grid.
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
