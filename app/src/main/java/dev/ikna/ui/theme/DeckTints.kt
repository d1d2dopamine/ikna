package dev.ikna.ui.theme

import androidx.compose.ui.graphics.Color
import dev.ikna.data.prefs.NO_TINT

/**
 * Eight colours a deck's square can be given, and nothing else.
 *
 * A free colour picker was the obvious alternative and it is the wrong one here.
 * The square is read against nine different palettes in two lighting modes, and
 * a hand-picked hex is one slider away from a deck that is invisible on the
 * screen its owner actually uses. These eight are all mid-tone: none of them
 * disappears into a near-black background or into a paper-white one, and the
 * letters drawn on top stay legible on every one of them.
 *
 * The order is the order of a rainbow rather than of some internal list, because
 * this is picked by eye from a row of squares and nobody scans that row by name.
 *
 * Stored as an index, never as a colour value. An index survives the palette
 * being retuned in a later version; a stored ARGB would freeze today's shade
 * into next year's theme.
 */
val DeckTints: List<Color> = listOf(
    Color(0xFFE5484D), // red
    Color(0xFFF2683C), // ember, the app's own accent
    Color(0xFFF5A524), // amber
    Color(0xFF46A758), // green
    Color(0xFF12A594), // teal
    Color(0xFF3E9BFF), // blue
    Color(0xFF8E7BFF), // violet
    Color(0xFFE93D82)  // pink
)

/**
 * The colour for a stored index, or [fallback] when the deck was never given
 * one.
 *
 * Out-of-range indexes fall back too rather than crashing: a settings file from
 * a build with more colours than this one is a decoration that did not survive,
 * not a reason to refuse to draw the home screen.
 */
fun deckTintColor(index: Int, fallback: Color): Color =
    if (index == NO_TINT || index !in DeckTints.indices) fallback else DeckTints[index]

/**
 * The icons offered for a deck.
 *
 * A grid of these rather than the system emoji keyboard. The keyboard offers
 * several thousand pictures, half of which are unreadable at 52dp, and it asks
 * someone who came to decorate a deck to go shopping first. Twenty-four is a
 * screenful, chosen for how well each one reads as a silhouette rather than for
 * what it means: languages, places, study, and a few plain shapes for people who
 * just want to tell two decks apart.
 */
val DeckIcons: List<String> = listOf(
    "\uD83D\uDCD8", // blue book
    "\uD83D\uDCDA", // books
    "\uD83D\uDDFA\uFE0F", // map
    "\uD83C\uDF0D", // globe
    "\uD83D\uDDE3\uFE0F", // speaking head
    "\uD83D\uDC42", // ear
    "\uD83D\uDCAC", // speech balloon
    "\u270D\uFE0F", // writing hand
    "\uD83C\uDFAC", // clapper board
    "\uD83C\uDFB5", // musical note
    "\uD83C\uDFAE", // game pad
    "\uD83D\uDCBC", // briefcase
    "\uD83C\uDF74", // fork and knife
    "\u2708\uFE0F", // aeroplane
    "\uD83C\uDFE0", // house
    "\uD83E\uDDEA", // test tube
    "\uD83E\uDDE0", // brain
    "\u2764\uFE0F", // heart
    "\u2B50", // star
    "\u26A1", // lightning
    "\uD83D\uDD25", // fire
    "\uD83C\uDF31", // seedling
    "\uD83D\uDD37", // blue diamond
    "\u2B1B" // black square
)
