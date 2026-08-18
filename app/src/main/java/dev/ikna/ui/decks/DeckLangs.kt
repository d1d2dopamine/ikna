package dev.ikna.ui.decks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.ikna.data.repo.NO_LANG
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaChip
import dev.ikna.ui.theme.Space

/**
 * The languages a deck can be said to be in.
 *
 * Ten languages and "no voice". Not a list of everything the speech engine can
 * read -- that list depends on which models are installed and would change under
 * the user's feet -- but the ten a deck is plausibly in, plus the honest answer
 * for a deck that is a list of chemistry terms and should not be read aloud at
 * all.
 */
internal val DECK_LANGS = listOf(
    "en", "pl", "ru", "es",
    "fr", "de", "it", "pt",
    "zh", "ja", NO_LANG
)

/**
 * The language chips, asked in two places.
 *
 * They used to exist only on the deck's own page, which meant a deck arrived
 * from a file with no language, could not be read aloud, and the one screen that
 * could fix that was two taps away behind a deck nobody had opened yet. So the
 * same row is now also on the screen that imports the deck, where the question
 * is being answered anyway.
 *
 * Four to a row instead of one strip that scrolls sideways: a sideways strip
 * hides half of its options behind a gesture nobody is told about, and there are
 * only eleven of them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LangChips(current: String, onPick: (String) -> Unit) {
    // Four chips to a row was a guess about how wide a word is, and the guess
    // was wrong in Russian: «продвинутый» is wider than a quarter of the screen and the
    // last chip in the row lost its final letters inside its own border. The row
    // wraps by measured width now, so the layout is a question for the text and
    // the screen instead of a number typed here, in all three languages.
    FlowRow(
    	modifier = Modifier.fillMaxWidth(),
    	horizontalArrangement = Arrangement.spacedBy(Space.sm),
    	verticalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
    	DECK_LANGS.forEach { code ->
    		IknaChip(
    		    label = if (code == NO_LANG) S.t("dp.005") else code.uppercase(),
    		    selected = current == code,
    		    onClick = { onPick(code) }
    		)
    	}
    }
}
