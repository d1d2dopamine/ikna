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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import dev.ikna.ui.theme.IknaProgress
import dev.ikna.ui.theme.IknaToggle
import dev.ikna.ui.theme.LocalIknaControlColors
import dev.ikna.ui.theme.Space
import dev.ikna.ui.theme.SignalFramePlacement
import dev.ikna.ui.theme.deckTintColor
import dev.ikna.ui.theme.iknaFrameRadius
import dev.ikna.ui.theme.iknaInspect
import dev.ikna.ui.theme.iknaSignalFrame
import dev.ikna.ui.theme.iknaTileShape
import dev.ikna.ui.theme.iknaNumberStyle

private val DECK_MARK_SIZE = 52.dp
private val DECK_PROGRESS_HEIGHT = 14.dp

@Composable
fun IknaTodayBlock(summary: TodayStudySummary, perCardMs: Long?, onClick: () -> Unit) {
    val palette = MaterialTheme.colorScheme
    val total = summary.remaining
    Column(
        modifier = Modifier.fillMaxWidth().iknaInspect("IknaTodayBlock"),
        verticalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        Text(S.t("deck.007"), style = MaterialTheme.typography.labelMedium, color = palette.onSurfaceVariant)
        Text(
            text = if (total > 0) "$total ${iknaCardWord(total)}${iknaMinutesTail(total, perCardMs)}"
                else S.t("deck.008"),
            style = MaterialTheme.typography.titleMedium,
            color = palette.onSurface
        )
        if (summary.reviewOnly) {
            val reason = when (val reason = summary.reviewOnlyReason) {
                null, GovernorReason.OK, GovernorReason.FIRST_RUN, GovernorReason.SAFETY_VALVE ->
                    S.t("today.reviews")
                else -> reasonText(reason)
            }
            Text(reason, style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
        }
        if (total > 0) {
            IknaWideButton(
                label = S.t(if (summary.started) "today.resume" else "today.start"),
                onClick = onClick,
                filled = true,
                height = 48.dp
            )
        } else {
            IknaTextButton(label = S.t("a11y.019"), onClick = onClick)
        }
    }
}

/** Deck selection opens its details, where study and management live. */
@Composable
fun IknaDeckRow(
    deck: DeckSummary,
    look: DeckLook,
    dueToday: Int,
    onOpen: () -> Unit
) {
    val palette = MaterialTheme.colorScheme
    val owes = dueToday > 0
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interaction)
            .clipToBounds()
            .iknaSignalFrame(
                interaction,
                cornerRadius = iknaFrameRadius(rounded = 10.dp, angular = 8.dp),
                placement = SignalFramePlacement.Outer
            )
            .clickable(interactionSource = interaction, indication = null,
                onClickLabel = S.t("a11y.010"), onClick = onOpen)
            .iknaInspect("IknaDeckRow[${deck.title}]"),
        verticalAlignment = Alignment.Top
    ) {
        IknaDeckMark(deck = deck, owes = owes, look = look)
        Spacer(Modifier.width(Space.md))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(
                text = deck.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = if (deck.isActive || owes) palette.onSurface else palette.onSurfaceVariant
            )
            Text(
                text = if (owes) S.t("deck.009") + dueToday else S.t("deck.010"),
                style = MaterialTheme.typography.labelSmall,
                color = if (owes) palette.primary else palette.onSurfaceVariant
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
 * Two letters in a square, and the square is the whole signal.
 *
 * Filled means this deck wants something today. Hollow means it is done or
 * resting: the letters and the faint seal carry the shape without a border --
 * the owner removed the frame from the marks entirely.
 */
@Composable
fun IknaDeckMark(deck: DeckSummary, owes: Boolean, look: DeckLook) {
    val accent = deckTintColor(look.tint, MaterialTheme.colorScheme.primary)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val background = MaterialTheme.colorScheme.background

    val fill = if (owes) accent else background
    val ink = when {
        owes -> background
        deck.isActive -> MaterialTheme.colorScheme.onBackground
        else -> muted.copy(alpha = 0.6f)
    }
    val patternCells = remember(deck.id, deck.installedAt) { deckSealCells(deck.id, deck.installedAt) }
    val highlightCells = remember(deck.id, deck.installedAt) { deckSealHighlights(deck.id, deck.installedAt) }
    val pattern = when {
        owes -> background.copy(alpha = 0.16f)
        deck.isActive -> accent.copy(alpha = 0.18f)
        else -> muted.copy(alpha = 0.10f)
    }
    val highlight = when {
        owes -> background.copy(alpha = 0.34f)
        deck.isActive -> accent.copy(alpha = 0.40f)
        else -> muted.copy(alpha = 0.20f)
    }

    Box(
        modifier = Modifier
            .size(DECK_MARK_SIZE)
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
            style = MaterialTheme.typography.titleMedium,
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
