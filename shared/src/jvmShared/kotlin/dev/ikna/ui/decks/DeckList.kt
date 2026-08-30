package dev.ikna.ui.decks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ikna.data.prefs.DeckLook
import dev.ikna.data.repo.DeckSummary
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaProgress
import dev.ikna.ui.theme.IknaToggle
import dev.ikna.ui.theme.Space
import dev.ikna.ui.theme.deckTintColor

/*
 * The deck list, compiled once and drawn on both machines.
 *
 * These are the phone's own three pieces -- the figure for the day, the row for
 * a deck, and the square that marks it -- moved here so the window cannot grow
 * its own version of them. Everything about them is a decision that was already
 * made on the phone: the number is set at display size with no frame around it,
 * the row has no outline and is separated by space, the square is filled when the
 * deck owes work and hollow when it does not, and the percentage sits beside
 * today's figure rather than under the bar so that every row is the same height.
 */

/**
 * Everything due today, as a number and nothing else.
 *
 * On a finished day it drops to the muted colour and the arrow goes away -- but
 * it still opens, because "nothing is waiting" is an answer and the session
 * screen already knows how to hand out a few extra cards to somebody who asks
 * anyway.
 */
@Composable
fun IknaTodayBlock(total: Int, onClick: () -> Unit) {
    val enabled = total > 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Space.sm)
    ) {
        Text(
            text = S.t("deck.007"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Space.sm))
        if (enabled) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = total.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.alignByBaseline()
                )
                Spacer(Modifier.width(Space.md))
                Text(
                    text = iknaCardWord(total),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline()
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "\u2192",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.alignByBaseline()
                )
            }
        } else {
            Text(
                text = S.t("deck.008"),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** One deck: its mark, what it owes today, and how far through it you are. */
@Composable
fun IknaDeckRow(
    deck: DeckSummary,
    look: DeckLook,
    dueToday: Int,
    perCardMs: Long?,
    onOpen: () -> Unit,
    onOpenDeck: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val owes = dueToday > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        verticalAlignment = Alignment.Top
    ) {
        IknaDeckMark(deck = deck, owes = owes, look = look)
        Spacer(Modifier.width(Space.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = deck.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground.copy(
                            alpha = if (deck.isActive) 1f else 0.55f
                        )
                    )
                    Spacer(Modifier.height(Space.xs))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (owes) {
                                S.t("deck.009") + dueToday + iknaMinutesTail(dueToday, perCardMs)
                            } else {
                                S.t("deck.010")
                            },
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (owes) accent else muted
                        )
                        if (deck.isActive) {
                            Text(
                                text = " \u00B7 " + iknaPercentDone(deck.introduced, deck.total),
                                maxLines = 1,
                                style = MaterialTheme.typography.labelMedium,
                                color = muted
                            )
                        }
                    }
                }
                IknaIconButton(
                    glyph = IknaGlyph.DOTS,
                    onClick = onOpenDeck,
                    size = 32.dp,
                    glyphSize = 18.dp,
                    color = muted,
                    label = S.t("a11y.010")
                )
                Spacer(Modifier.width(Space.sm))
                IknaToggle(
                    checked = deck.isActive,
                    onCheckedChange = onToggle,
                    label = S.t("a11y.006")
                )
            }
            if (deck.isActive) {
                Spacer(Modifier.height(Space.md))
                IknaProgress(
                    fraction = if (deck.total == 0) 0f else deck.introduced.toFloat() / deck.total,
                    height = 4.dp,
                    color = if (owes) accent else muted,
                    track = true,
                    segments = 18
                )
            }
        }
    }
}

/**
 * Two letters in a square, and the square is the whole signal.
 *
 * Filled means this deck wants something today. Hollow means it is done or
 * resting. Faint means it is switched off.
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
    val edge = when {
        owes -> accent
        deck.isActive -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }
    val patternCells = remember(deck.lang) { languageSealCells(deck.lang) }
    val highlightCells = remember(deck.id) { deckSealHighlights(deck.id) }
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
            .size(52.dp)
            .background(fill)
            .border(Space.hair, edge),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val inset = 4.dp.toPx()
            val step = (size.minDimension - inset * 2f) / LANGUAGE_SEAL_SIDE
            val cell = step * 0.56f
            fun drawCell(index: Int, color: Color) {
                if (isDeckSealLetterZone(index)) return
                val column = index % LANGUAGE_SEAL_SIDE
                val row = index / LANGUAGE_SEAL_SIDE
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
    if (total <= 0) return "0%"
    return (introduced * 100 / total).toString() + "%"
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
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> S.t("deck.014")
        mod10 == 1 -> S.t("deck.015")
        mod10 in 2..4 -> S.t("deck.016")
        else -> S.t("deck.017")
    }
}
