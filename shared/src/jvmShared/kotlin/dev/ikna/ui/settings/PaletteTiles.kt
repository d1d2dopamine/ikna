package dev.ikna.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaPalettes
import dev.ikna.ui.theme.IknaSoundCue
import dev.ikna.ui.theme.LocalIknaMotionEnabled
import dev.ikna.ui.theme.LocalIknaSoundFeedback
import dev.ikna.ui.theme.SignalFramePlacement
import dev.ikna.ui.theme.iknaFrameRadius
import dev.ikna.ui.theme.iknaKnobShape
import dev.ikna.ui.theme.iknaPressScale
import dev.ikna.ui.theme.iknaSignalFrame
import dev.ikna.ui.theme.iknaTileShape

/**
 * Compact palette swatches rendered in Ikna's own visual grammar.
 *
 * Each swatch wears its palette background, a 2x2 pixel colour cluster
 * (ink, muted, accent), and the palette name in clean Geologica inside
 * one quiet surface — no redundant wordmark, heavy outer borders, or
 * separate caption underneath.
 */
@Composable
fun IknaPaletteTiles(
    selectedId: String,
    grey: Boolean,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    tileHeight: Dp = 58.dp,
    gap: Dp = 10.dp
) {
    val motionEnabled = LocalIknaMotionEnabled.current
    val sound = LocalIknaSoundFeedback.current
    val perRow = if (columns < 1) 1 else columns
    val shape = iknaTileShape()
    val dotShape = iknaKnobShape()

    Column(modifier = modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(gap)) {
        IknaPalettes.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { spec ->
                    val p = spec.palette(grey = grey)
                    val selected = spec.id == selectedId
                    val tileInteraction = remember(spec.id) { MutableInteractionSource() }
                    val tileFocused by tileInteraction.collectIsFocusedAsState()
                    val tileHovered by tileInteraction.collectIsHoveredAsState()
                    val tilePressed by tileInteraction.collectIsPressedAsState()
                    val pressScale = iknaPressScale(tilePressed, motionEnabled, pressedScale = 0.97f)
                    val borderColor = when {
                        selected || tileFocused -> p.accent
                        tileHovered -> p.line
                        else -> p.line.copy(alpha = 0.50f)
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(tileHeight)
                            .graphicsLayer {
                                scaleX = pressScale
                                scaleY = pressScale
                            }
                            .clip(shape)
                            .background(p.background)
                            .border(1.dp, borderColor, shape)
                            .hoverable(tileInteraction)
                            .semantics { this.selected = selected }
                            .iknaSignalFrame(
                                tileInteraction,
                                cornerRadius = iknaFrameRadius(rounded = 12.dp, angular = 8.dp),
                                placement = SignalFramePlacement.Outer
                            )
                            .clickable(
                                interactionSource = tileInteraction,
                                indication = null,
                                role = Role.RadioButton,
                                onClickLabel = S.t(spec.nameKey),
                                onClick = {
                                    sound.play(IknaSoundCue.TAP)
                                    onPick(spec.id)
                                }
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Canvas(modifier = Modifier.size(14.dp)) {
                                val gapPx = 2.dp.toPx()
                                val cell = (size.minDimension - gapPx) / 2f
                                drawRect(p.ink, Offset(0f, 0f), Size(cell, cell))
                                drawRect(p.accent, Offset(cell + gapPx, 0f), Size(cell, cell))
                                drawRect(p.muted, Offset(0f, cell + gapPx), Size(cell, cell))
                                drawRect(p.ink.copy(alpha = 0.45f), Offset(cell + gapPx, cell + gapPx), Size(cell, cell))
                            }
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(dotShape)
                                        .background(p.accent)
                                )
                            }
                        }
                        Text(
                            text = S.t(spec.nameKey),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = p.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
