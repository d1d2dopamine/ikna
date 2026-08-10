package dev.ikna.data.pack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser behind the deck-making button.
 *
 * Most of these tests are about text written by a language model, because that is
 * where this text will come from. The user's job ends at pasting; every bullet,
 * number, fence and table pipe that survives the paste is our problem, not
 * theirs. The refusals are tested just as hard: a silent skip is how a deck ends
 * up with 40 cards instead of 300 and no one finds out why.
 */
class SeedParserTest {

	@Test
	fun cleanPasteBecomesRows() {
		val parse = SeedFormat.parse(
			"""
			hang on | Just hang on, I'll grab my keys. | погоди
			make it | I don't think I can make it tonight. | добраться
			""".trimIndent()
		)
		assertEquals(2, parse.rows.size)
		assertEquals(0, parse.problems.size)
		assertEquals("hang on", parse.rows[0].phrase)
		assertEquals("Just hang on, I'll grab my keys.", parse.rows[0].sentence)
		assertEquals("погоди", parse.rows[0].translation)
	}

	@Test
	fun tabsAreAcceptedToo() {
		// Text pasted out of a spreadsheet, or a seed file made for tools/genpack.
		val parse = SeedFormat.parse("hang on\tJust hang on.\tпогоди")
		assertEquals(1, parse.rows.size)
		assertEquals("Just hang on.", parse.rows[0].sentence)
	}

	@Test
	fun modelDecorationIsIgnored() {
		// Every one of these wrappers is something a chat model actually returns.
		val parse = SeedFormat.parse(
			"""
			```
			| phrase | sentence | translation |
			|---|---|---|
			| 1. hang on | Just hang on. | погоди |
			- make it | I can't make it. | добраться

			2) give up | Don't give up yet. | сдаваться
			```
			""".trimIndent()
		)
		// The header row is refused for a real reason: "sentence" does not contain
		// "phrase". Everything the person meant to send survives.
		assertEquals(3, parse.rows.size)
		assertEquals(listOf("hang on", "make it", "give up"), parse.rows.map { it.phrase })
		assertEquals(1, parse.problems.size)
		assertEquals(SeedProblem.PHRASE_NOT_IN_SENTENCE, parse.problems[0].problem)
	}

	@Test
	fun wrongColumnCountIsReportedWithItsLineNumber() {
		val parse = SeedFormat.parse(
			"""
			hang on | Just hang on. | погоди
			make it | I can't make it.
			""".trimIndent()
		)
		assertEquals(1, parse.rows.size)
		assertEquals(1, parse.problems.size)
		// Line 2 of what the user is looking at, not index 1 of what survived.
		assertEquals(2, parse.problems[0].line)
		assertEquals(SeedProblem.NOT_THREE_COLUMNS, parse.problems[0].problem)
		assertTrue(parse.problems[0].text.contains("make it"))
	}

	@Test
	fun emptyColumnIsRefused() {
		val parse = SeedFormat.parse("hang on | Just hang on. |   ")
		assertEquals(0, parse.rows.size)
		assertEquals(SeedProblem.EMPTY_FIELD, parse.problems[0].problem)
	}

	@Test
	fun phraseMustAppearInItsSentence() {
		// The whole card is a phrase highlighted inside a sentence. Without the span
		// there is nothing to highlight, so this row cannot be quietly kept.
		val parse = SeedFormat.parse("hang on | Wait for me here. | погоди")
		assertEquals(0, parse.rows.size)
		assertEquals(SeedProblem.PHRASE_NOT_IN_SENTENCE, parse.problems[0].problem)
	}

	@Test
	fun spanSurvivesCapitalisationAtTheStartOfASentence() {
		val parse = SeedFormat.parse("hang on | Hang on, I'm coming. | погоди")
		assertEquals(1, parse.rows.size)
		assertEquals(0, SeedFormat.spanOf("Hang on, I'm coming.", "hang on"))
		assertNull(SeedFormat.spanOf("Wait here.", "hang on"))
	}

	@Test
	fun tooLongIsRefused() {
		val long = "a".repeat(SeedFormat.MAX_SENTENCE + 1)
		val parse = SeedFormat.parse("a | " + long + " | буква")
		assertEquals(0, parse.rows.size)
		assertEquals(SeedProblem.TOO_LONG, parse.problems[0].problem)
	}

	@Test
	fun duplicatePhraseKeepsTheFirstRow() {
		val parse = SeedFormat.parse(
			"""
			hang on | Just hang on. | погоди
			Hang On | Hang on a second. | подожди
			""".trimIndent()
		)
		assertEquals(1, parse.rows.size)
		assertEquals("Just hang on.", parse.rows[0].sentence)
		assertEquals(SeedProblem.DUPLICATE, parse.problems[0].problem)
	}

	@Test
	fun onlyTheTrainedWordsCarryWeight() {
		val tokens = SeedFormat.tokens("Just hang on, I'll grab my keys.", 5, 12)
		val content = tokens.filter { it.isContent }.map { it.surface }
		// "hang on" earns credit. "Just", "my", "keys" do not, because nothing here
		// can prove they were what the person recalled.
		assertEquals(listOf("hang", "on"), content)
		assertTrue(tokens.all { it.lemma == it.surface.lowercase() })
		assertFalse(tokens.any { it.surface.contains(',') })
	}

	@Test
	fun diacriticsSurviveTokenisation() {
		val tokens = SeedFormat.tokens("Jem śniadanie z żoną.", 0, 3)
		assertEquals(listOf("Jem", "śniadanie", "z", "żoną"), tokens.map { it.surface })
		assertEquals("śniadanie", tokens[1].lemma)
	}

	@Test
	fun chunksArePaddedAndStable() {
		val parse = SeedFormat.parse("hang on | Just hang on. | погоди")
		val chunks = SeedFormat.chunks("user-mine", parse.rows)
		assertEquals(1, chunks.size)
		val chunk = chunks[0]
		// Re-importing a corrected file has to land on the same id, or the deck
		// doubles and every card starts its schedule over.
		assertEquals("user-mine-0001", chunk.id)
		assertEquals("hang on", chunk.text)
		assertEquals("Just hang on. ".trim(), chunk.context)
		assertEquals(1, chunk.freqRank)
		assertEquals("hang on", chunk.context.substring(chunk.targetStart, chunk.targetEnd))
		assertNull(chunk.audioRef)
		assertEquals(SeedFormat.chunks("user-mine", parse.rows).map { it.id }, chunks.map { it.id })
	}

	@Test
	fun theTwoFormatsAreToldApartByContent() {
		// Not by file name: a file picked out of a chat app is called "document".
		val jsonl = "{\"id\":\"a\",\"text\":\"hang on\"}"
		assertTrue(SeedFormat.looksLikeJsonl(jsonl))
		assertFalse(SeedFormat.looksLikeSeed(jsonl))

		val seed = "hang on | Just hang on. | погоди"
		assertTrue(SeedFormat.looksLikeSeed(seed))
		assertFalse(SeedFormat.looksLikeJsonl(seed))

		// A photo or a song opened as text is neither, and must not be imported.
		val junk = "\u0000\u0001binary rubbish"
		assertFalse(SeedFormat.looksLikeSeed(junk))
		assertFalse(SeedFormat.looksLikeJsonl(junk))
	}
}
