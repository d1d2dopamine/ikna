package dev.ikna.ui.decks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The deck square, drawn from a palette rather than from MaterialTheme.
 *
 * The phone draws exactly this mark: two letters over the pixel seal of the
 * deck's language, filled when the deck owes something today, hollow when it is
 * done, faint when it is switched off. The one on the phone reads its colours
 * out of the Material scheme, which the desktop shell does not build; this one
 * is handed the four palette colours instead, so both platforms produce the same
 * square from the same [languageSealCells] and [monogramOf] as before -- the
 * seal of a language cannot drift between the two applications, because there is
 * still only one function that generates it.
 */
@Composable
fun IknaDeckSeal(
    lang: String,
    deckId: String,
    title: String,
    accent: Color,
    ink: Color,
    muted: Color,
    background: Color,
    line: Color,
    label: String = "",
    owes: Boolean = false,
    active: Boolean = true,
    side: Dp = 52.dp
) {
    val fill = if (owes) accent else background
    val letters = when {
        owes -> background
        active -> ink
        else -> muted.copy(alpha = 0.6f)
    }
    val edge = when {
        owes -> accent
        active -> line
        else -> line.copy(alpha = 0.5f)
    }
    val patternCells = remember(lang) { languageSealCells(lang) }
    val highlightCells = remember(deckId) { deckSealHighlights(deckId) }
    val pattern = when {
        owes -> background.copy(alpha = 0.16f)
        active -> accent.copy(alpha = 0.18f)
        else -> muted.copy(alpha = 0.10f)
    }
    val highlight = when {
        owes -> background.copy(alpha = 0.34f)
        active -> accent.copy(alpha = 0.40f)
        else -> muted.copy(alpha = 0.20f)
    }

    Box(
        modifier = Modifier
            .size(side)
            .background(fill)
            .border(1.dp, edge),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val inset = this.size.minDimension * 0.077f
            val step = (this.size.minDimension - inset * 2f) / LANGUAGE_SEAL_SIDE
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
            text = label.ifEmpty { monogramOf(lang, title) },
            color = letters,
            fontSize = (side.value / 3.6f).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
