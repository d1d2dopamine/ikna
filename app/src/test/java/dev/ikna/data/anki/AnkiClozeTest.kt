package dev.ikna.data.anki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading Anki cloze notes.
 *
 * A cloze card already contains what this app marks by hand: a sentence and the
 * exact phrase somebody chose to learn. The cases below are the ones that break
 * that assumption -- several gaps on one card, a deletion inside another, a
 * deletion wrapped around a picture -- because each of them used to produce a
 * card that looked real and could not be answered.
 */
class AnkiClozeTest {

    private fun fields(vararg pairs: Pair<String, String>) = mapOf(*pairs)

    @Test
    fun `a single deletion becomes a sentence with a span`() {
        val reading = AnkiText.readCloze(
            fields("Text" to "Canberra was founded in {{c1::1913}}."),
            1
        )
        assertEquals(ClozeShape.SINGLE, reading?.shape)
        val target = reading?.target!!
        assertEquals("Canberra was founded in 1913.", target.context)
        assertEquals("1913", target.context.substring(target.start, target.end))
    }

    @Test
    fun `deletions with other numbers are filled back in`() {
        val reading = AnkiText.readCloze(
            fields("Text" to "{{c1::Berlin}} is the capital of {{c2::Germany}}."),
            2
        )
        val target = reading?.target!!
        assertEquals("Berlin is the capital of Germany.", target.context)
        assertEquals("Germany", target.context.substring(target.start, target.end))
    }

    @Test
    fun `a hint is not mistaken for the sentence`() {
        val reading = AnkiText.readCloze(
            fields("Text" to "Der {{c1::Hund::noun}} schläft."),
            1
        )
        val target = reading?.target!!
        assertEquals("Der Hund schläft.", target.context)
        assertEquals("Hund", target.context.substring(target.start, target.end))
    }

    @Test
    fun `html around the deletion does not drag the span off the word`() {
        val reading = AnkiText.readCloze(
            fields("Text" to "<div>Ich habe einen <b>{{c1::Hund}}</b>.</div>"),
            1
        )
        val target = reading?.target!!
        assertEquals("Ich habe einen Hund.", target.context)
        assertEquals("Hund", target.context.substring(target.start, target.end))
    }

    @Test
    fun `a stripped tag leaves no space in front of the full stop`() {
        val reading = AnkiText.readCloze(
            fields("Text" to "Sie hat <b>einen {{c1::Bruder}}</b>."),
            1
        )
        val target = reading?.target!!
        assertEquals("Sie hat einen Bruder.", target.context)
        assertEquals("Bruder", target.context.substring(target.start, target.end))
    }

    @Test
    fun `the span never keeps whitespace left behind by a stripped tag`() {
        val reading = AnkiText.readCloze(
            fields("Text" to "Ich habe <i> </i>{{c1:: Hund }} gern."),
            1
        )
        val target = reading?.target!!
        val span = target.context.substring(target.start, target.end)
        assertEquals(span.trim(), span)
        assertEquals("Hund", span)
    }

    @Test
    fun `the same number twice is a card with several gaps`() {
        val reading = AnkiText.readCloze(
            fields("Text" to "{{c1::This}} is a {{c1::sentence}}."),
            1
        )
        assertEquals(ClozeShape.MULTI_GAP, reading?.shape)
        assertNull(reading?.target)
    }

    @Test
    fun `a deletion inside another deletion is refused`() {
        val reading = AnkiText.readCloze(
            fields("Text" to "Ich habe {{c1::einen {{c2::Hund}}}}."),
            1
        )
        assertEquals(ClozeShape.NESTED, reading?.shape)
        assertNull(reading?.target)
    }

    @Test
    fun `a number that is not in the note is refused`() {
        val reading = AnkiText.readCloze(
            fields("Text" to "Ich habe einen {{c1::Hund}}."),
            3
        )
        assertEquals(ClozeShape.ABSENT, reading?.shape)
        assertNull(reading?.target)
    }

    @Test
    fun `a deletion wrapped around a picture is refused`() {
        val reading = AnkiText.readCloze(
            fields("Text" to "Here: {{c1::<img src=\"dog.jpg\">}}"),
            1
        )
        assertEquals(ClozeShape.ABSENT, reading?.shape)
        assertNull(reading?.target)
    }

    @Test
    fun `a note without deletions is not a cloze note`() {
        assertNull(AnkiText.readCloze(fields("Front" to "Hund", "Back" to "dog"), 1))
    }

    @Test
    fun `a refused card is put back together whole`() {
        val filled = AnkiText.filledIn(
            fields("Text" to "{{c1::This}} is a {{c1::sentence}}.")
        )
        assertEquals("This is a sentence.", filled)
    }

    @Test
    fun `the extra field is the only thing read as a meaning`() {
        assertEquals(
            "a demonstration",
            AnkiText.extraMeaning(fields("Text" to "x", "Extra" to "a demonstration"))
        )
        assertEquals(
            "usage notes",
            AnkiText.extraMeaning(fields("Text" to "x", "Back Extra" to "usage notes"))
        )
        assertEquals(
            "",
            AnkiText.extraMeaning(fields("Text" to "x", "Notes" to "my own scribbles"))
        )
    }

    @Test
    fun `an image occlusion note is recognised by its field`() {
        assertTrue(AnkiText.isImageOcclusion(fields("Occlusion" to "x", "Image" to "y")))
        assertTrue(AnkiText.isImageOcclusion(fields("occlusion" to "x")))
        assertTrue(!AnkiText.isImageOcclusion(fields("Text" to "x", "Extra" to "y")))
    }
}
