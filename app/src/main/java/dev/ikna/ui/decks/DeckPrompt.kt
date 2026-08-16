package dev.ikna.ui.decks

import dev.ikna.data.repo.NO_LANG

/**
 * The answers the prompt asks for, written into the prompt.
 *
 * The prompt ends with six blank lines a person is supposed to fill in by hand:
 * which language they are learning, which language the meanings should be in,
 * how many cards, the topic, the level. Filling those in inside a chat window,
 * on a phone, before anything has been learned, is exactly the kind of setup
 * this app exists to take away -- and a prompt sent with the lines still blank
 * comes back as a deck in the wrong language, or as a question instead of a
 * deck.
 *
 * So the screen asks, and this file writes the answers into the text before it
 * reaches the clipboard. The prompt itself is untouched otherwise: it is an
 * asset addressed to a model, not interface text, and if a line ever changes
 * name here nothing breaks -- an answer with nowhere to go is simply dropped.
 */
internal fun fillPrompt(
	base: String,
	learning: String,
	meanings: String,
	count: Int,
	topic: String,
	level: String
): String {
	if (base.isEmpty()) return base
	val answers = listOf(
		PROMPT_LEARNING to languageName(learning),
		PROMPT_MEANINGS to languageName(meanings),
		PROMPT_COUNT to count.toString(),
		PROMPT_TOPIC to topic.trim().replace("\n", " "),
		PROMPT_LEVEL to level
	)
	return base.lines().joinToString("\n") { line ->
		val answer = answers.firstOrNull { line.startsWith(it.first) }?.second
		// A line the person left empty stays empty rather than saying "none":
		// a model reads "none" as an instruction and an empty line as silence.
		if (answer.isNullOrEmpty()) line else line + " " + answer
	}
}

/** English names, because the prompt is in English and the model reads it. */
internal fun languageName(code: String): String = when (code) {
	"en" -> "English"
	"pl" -> "Polish"
	"ru" -> "Russian"
	"es" -> "Spanish"
	"fr" -> "French"
	"de" -> "German"
	"it" -> "Italian"
	"pt" -> "Portuguese"
	"zh" -> "Chinese"
	"ja" -> "Japanese"
	else -> ""
}

/**
 * How many cards to ask for.
 *
 * Not a free number. A deck may hold ten thousand rows, but a model asked for a
 * thousand cards in one answer starts repeating itself around three hundred and
 * pads the rest, and the app only lets a handful of them out per day anyway.
 * Asking twice for two hundred good cards beats asking once for four hundred.
 */
internal val PROMPT_COUNTS = listOf(50, 100, 200, 300)

/** The wordings the prompt itself offers. Sent as written, in English. */
internal const val LEVEL_BEGINNER = "beginner"
internal const val LEVEL_TALKING = "can hold a conversation"
internal const val LEVEL_ADVANCED = "advanced"

internal val PROMPT_LEVELS = listOf(LEVEL_BEGINNER, LEVEL_TALKING, LEVEL_ADVANCED)

internal const val PROMPT_LEARNING = "Language I am learning:"
internal const val PROMPT_MEANINGS = "Language of the translations:"
internal const val PROMPT_COUNT = "Number of cards:"
internal const val PROMPT_TOPIC = "Topic or situation:"
internal const val PROMPT_LEVEL = "My level ("

/** The deck languages minus the honest "no voice", which is not a language. */
internal val MEANING_LANGS = DECK_LANGS.filter { it != NO_LANG }
