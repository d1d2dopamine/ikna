package dev.ikna.ui.decks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ikna.data.prefs.DeckLook
import dev.ikna.data.repo.DeckSummary
import dev.ikna.data.repo.TodayStudySummary
import dev.ikna.domain.governor.GovernorReason
import dev.ikna.ui.session.reasonText
import dev.ikna.ui.theme.IknaWideButton
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.text.S
import dev.ikna.ui.text.quantityWord
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaProgress
import dev.ikna.ui.theme.IknaReadingCat
import dev.ikna.ui.theme.IknaSoundCue
import dev.ikna.ui.theme.IknaToggle
import dev.ikna.ui.theme.LocalIknaControlColors
import dev.ikna.ui.theme.LocalIknaMotionEnabled
import dev.ikna.ui.theme.LocalIknaSoundFeedback
import dev.ikna.ui.theme.Space
import dev.ikna.ui.theme.SignalFramePlacement
import dev.ikna.ui.theme.deckTintColor
import dev.ikna.ui.theme.iknaCardShape
import dev.ikna.ui.theme.iknaControlShape
import dev.ikna.ui.theme.iknaFrameRadius
import dev.ikna.ui.theme.iknaInspect
import dev.ikna.ui.theme.iknaPressScale
import dev.ikna.ui.theme.iknaSignalFrame
import dev.ikna.ui.theme.iknaTileShape
import dev.ikna.ui.theme.iknaNumberStyle

private val DECK_MARK_SIZE = 52.dp
private val DECK_PROGRESS_HEIGHT = 14.dp

@Composable
fun IknaTodayBlock(
    summary: TodayStudySummary,
    perCardMs: Long?,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val palette = MaterialTheme.colorScheme
    val total = summary.remaining
    val catPaper = if (compact) palette.surfaceVariant else palette.background
    val surfaceModifier = if (compact) {
        Modifier
            .fillMaxWidth()
            .clip(iknaCardShape())
            .background(palette.surfaceVariant)
            .padding(Space.md)
    } else {
        Modifier.fillMaxWidth()
    }
    Column(
        modifier = surfaceModifier.iknaInspect("IknaTodayBlock"),
        verticalArrangement = Arrangement.spacedBy(if (compact) Space.xs else Space.sm)
    ) {
        if (total > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(if (compact) Space.xs else Space.sm)
                ) {
                    Text(S.t("deck.007"), style = MaterialTheme.typography.labelMedium, color = palette.onSurfaceVariant)
                    Text(
                        text = "$total ${iknaCardWord(total)}${iknaMinutesTail(total, perCardMs)}",
                        style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.onSurface
                    )
                    if (summary.returning) {
                        Text(S.t("today.return"), style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
                    }
                    if (summary.reviewOnly) {
                        val reason = when (val reason = summary.reviewOnlyReason) {
                            null, GovernorReason.OK, GovernorReason.FIRST_RUN, GovernorReason.SAFETY_VALVE, GovernorReason.RETURN_MODE ->
                                S.t("today.reviews")
                            else -> reasonText(reason)
                        }
                        Text(reason, style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
                    }
                }
                IknaReadingCat(size = 64.dp, paper = catPaper)
            }
            if (compact) Spacer(Modifier.height(Space.xs))
            IknaWideButton(
                label = S.t(if (summary.started || summary.returning) "today.resume" else "today.start"),
                onClick = onClick,
                filled = true,
                height = 48.dp
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(if (compact) Space.xs else Space.sm)
                ) {
                    Text(S.t("deck.007"), style = MaterialTheme.typography.labelMedium, color = palette.onSurfaceVariant)
                    Text(
                        text = S.t("deck.008"),
                        style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.onSurface
                    )
                    if (summary.reviewOnly) {
                        val reason = when (val reason = summary.reviewOnlyReason) {
                            null, GovernorReason.OK, GovernorReason.FIRST_RUN, GovernorReason.SAFETY_VALVE, GovernorReason.RETURN_MODE ->
                                S.t("today.reviews")
                            else -> reasonText(reason)
                        }
                        Text(reason, style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
                    }
                    if (compact) Spacer(Modifier.height(Space.xs))
                    IknaTextButton(label = S.t("a11y.019"), onClick = onClick, horizontalPadding = 0.dp)
                }
                IknaReadingCat(size = 68.dp, paper = catPaper)
            }
        }
    }
}

/** Clicking the deck opens study; the trailing icon opens deck details and settings. */
@Composable
fun IknaDeckRow(
    deck: DeckSummary,
    look: DeckLook,
    dueToday: Int,
    onOpen: () -> Unit,
    onManage: (() -> Unit)? = null,
    compact: Boolean = false,
    selected: Boolean = false
) {
    val palette = MaterialTheme.colorScheme
    val controlColors = LocalIknaControlColors.current
    val motionEnabled = LocalIknaMotionEnabled.current
    val sound = LocalIknaSoundFeedback.current
    val owes = dueToday > 0
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val pressScale = iknaPressScale(pressed, motionEnabled, pressedScale = 0.986f)
    val rowTint = when {
        pressed -> controlColors.pressed.copy(alpha = 0.72f)
        selected -> controlColors.fill
        hovered -> controlColors.idle.copy(alpha = 0.90f)
        else -> Color.Transparent
    }
    LaunchedEffect(interaction, sound) {
        interaction.interactions.collect { event ->
            if (event is PressInteraction.Release) {
                sound.play(IknaSoundCue.TAP)
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .hoverable(interaction)
            .clipToBounds()
            .background(rowTint, iknaControlShape())
            .iknaSignalFrame(
                interaction,
                cornerRadius = iknaFrameRadius(rounded = 10.dp, angular = 8.dp),
                placement = SignalFramePlacement.Outer
            )
            .clickable(interactionSource = interaction, indication = null,
                onClickLabel = S.t("today.start"), onClick = onOpen)
            .padding(
                start = if (compact) Space.sm else 0.dp,
                end = if (onManage != null) 0.dp else if (compact) Space.sm else 0.dp,
                top = if (compact) Space.xs else Space.xs,
                bottom = if (compact) Space.xs else Space.xs
            )
            .iknaInspect("IknaDeckRow[${deck.title}]"),
        verticalAlignment = Alignment.Top
    ) {
        if (compact) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .size(width = 3.dp, height = 22.dp)
                    .background(if (selected) palette.primary else Color.Transparent, iknaControlShape())
            )
            Spacer(Modifier.width(Space.sm))
        }
        Box(modifier = if (compact) Modifier.align(Alignment.CenterVertically) else Modifier) {
            IknaDeckMark(deck = deck, owes = owes, look = look, compact = compact)
        }
        Spacer(Modifier.width(Space.md))
        Column(
            modifier = Modifier
                .weight(1f)
                .then(if (compact) Modifier.align(Alignment.CenterVertically) else Modifier),
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else Space.xs)
        ) {
            Text(
                text = deck.title,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected || deck.isActive || owes) palette.onSurface else palette.onSurfaceVariant
            )
            Text(
                text = if (owes) S.t("deck.009") + dueToday else S.t("deck.010"),
                style = MaterialTheme.typography.labelSmall,
                color = if (owes) palette.primary else palette.onSurfaceVariant
            )
        }
        if (onManage != null) {
            IknaIconButton(
                glyph = IknaGlyph.DOTS,
                onClick = onManage,
                modifier = Modifier.align(Alignment.CenterVertically),
                size = 48.dp,
                glyphSize = 18.dp,
                color = palette.onSurfaceVariant,
                label = S.t("a11y.010")
            )
        }
    }
}

@Composable
fun IknaDeckActions(
    active: Boolean,
    changing: Boolean,
    onStudy: () -> Unit,
    onBrowse: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        IknaWideButton(label = S.t("a11y.019"), onClick = onStudy, filled = true, height = 48.dp, enabled = !changing)
        IknaWideButton(label = S.t("a11y.012"), onClick = onBrowse, height = 48.dp, enabled = !changing)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            Text(
                S.t("a11y.006"),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            IknaToggle(checked = active, onCheckedChange = onToggle, enabled = !changing, label = S.t("a11y.006"))
        }
    }
}

/** Makes the long-term bar impossible to mistake for today's session. */
@Composable
fun IknaDeckProgress(
    introduced: Int,
    total: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = DECK_PROGRESS_HEIGHT).iknaInspect("IknaDeckProgress[$introduced/$total]"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = S.t("progress.002"),
            style = MaterialTheme.typography.labelSmall,
            color = muted,
            maxLines = 1
        )
        Spacer(Modifier.width(Space.sm))
        IknaProgress(
            fraction = if (total <= 0) 0f else introduced.toFloat() / total,
            modifier = Modifier.weight(1f),
            height = 4.dp,
            color = color,
            track = true,
            segments = 18
        )
        Spacer(Modifier.width(Space.sm))
        Text(
            text = iknaPercentDone(introduced, total),
            style = MaterialTheme.typography.labelSmall,
            color = muted,
            maxLines = 1
        )
    }
}

/**
 * Two letters in a dark square with a deterministic pixel-seal pattern.
 *
 * Never floods the tile with a solid white/accent block (especially important in Palette 0 "Zero").
 * Changing a deck's tint color always tints the inner seal in both active and resting states:
 * - Active (owes cards today): subtly elevated dark tinted obsidian tile, luminous colored pixel-seal,
 *   and full-contrast letters.
 * - Resting (done for today): pure background tile, softer colored pixel-seal, and calmer letters.
 */
@Composable
fun IknaDeckMark(deck: DeckSummary, owes: Boolean, look: DeckLook, compact: Boolean = false) {
    val accent = deckTintColor(look.tint, MaterialTheme.colorScheme.primary)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val background = MaterialTheme.colorScheme.background
    val onBackground = MaterialTheme.colorScheme.onBackground

    val fill = if (owes) accent.copy(alpha = 0.09f).compositeOver(background) else background
    val ink = when {
        owes -> onBackground
        deck.isActive -> onBackground.copy(alpha = 0.72f)
        else -> muted.copy(alpha = 0.55f)
    }
    val patternCells = remember(deck.id, deck.installedAt) { deckSealCells(deck.id, deck.installedAt) }
    val highlightCells = remember(deck.id, deck.installedAt) { deckSealHighlights(deck.id, deck.installedAt) }
    val pattern = when {
        owes -> accent.copy(alpha = 0.26f)
        deck.isActive -> accent.copy(alpha = 0.13f)
        else -> muted.copy(alpha = 0.08f)
    }
    val highlight = when {
        owes -> accent.copy(alpha = 0.62f)
        deck.isActive -> accent.copy(alpha = 0.30f)
        else -> muted.copy(alpha = 0.16f)
    }

    Box(
        modifier = Modifier
            .then(if (compact) Modifier.size(32.dp) else Modifier.size(DECK_MARK_SIZE))
            .testTag("ikna-deck-mark")
            .iknaInspect("IknaDeckMark[${deck.title}]")
            .clip(iknaTileShape())
            .background(fill),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val inset = 4.dp.toPx()
            val step = (size.minDimension - inset * 2f) / DECK_SEAL_SIDE
            val cell = step * 0.56f
            fun drawCell(index: Int, color: Color) {
                if (isDeckSealLetterZone(index)) return
                val column = index % DECK_SEAL_SIDE
                val row = index / DECK_SEAL_SIDE
                val x = inset + column * step + (step - cell) / 2f
                val y = inset + row * step + (step - cell) / 2f
                drawRect(color, Offset(x, y), Size(cell, cell))
            }
            patternCells.forEach { index -> drawCell(index, pattern) }
            highlightCells.forEach { index -> drawCell(index, highlight) }
        }
        Text(
            text = look.label.ifEmpty { monogramOf(deck.lang, deck.title) },
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = ink,
            maxLines = 1
        )
    }
}

/** How far into the deck, as the one figure the progress bar cannot state. */
fun iknaPercentDone(introduced: Int, total: Int): String {
    if (total <= 0 || introduced <= 0) return "0%"
    val percent = introduced.coerceAtMost(total).toLong() * 100L / total.toLong()
    return if (percent == 0L) "<1%" else percent.toString() + "%"
}

/** "~4 min" beside what a deck owes today, when there is a measurement for it. */
fun iknaMinutesTail(count: Int, perCardMs: Long?): String {
    if (perCardMs == null || count <= 0) return ""
    val totalMs = count * perCardMs
    if (totalMs < 45_000L) return S.t("deck.018")
    val minutes = ((totalMs + 30_000L) / 60_000L).toInt().coerceAtLeast(1)
    return " \u00B7 ~" + minutes + S.t("deck.019")
}

/** The word after the figure, declined the way the language needs. */
fun iknaCardWord(count: Int): String {
    return quantityWord(count.toLong(), "deck.014", "deck.015", "deck.016", "deck.017")
}
