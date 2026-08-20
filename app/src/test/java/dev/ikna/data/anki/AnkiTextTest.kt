package dev.ikna.data.anki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiTextTest {
    @Test
    fun `basic templates become one question and one answer`() {
        val card = AnkiText.render(
            questionTemplate = "<div>{{Front}}</div>",
            answerTemplate = "{{FrontSide}}<hr id=answer><b>{{Back}}</b>",
            fields = mapOf("Front" to "bonjour", "Back" to "hello"),
            clozeNumber = 1
        )
        assertEquals("bonjour", card.question)
        assertEquals("hello", card.answer)
        assertFalse(card.usedFallback)
    }

    @Test
    fun `the selected cloze is hidden only on the question`() {
        val card = AnkiText.render(
            questionTemplate = "{{cloze:Text}}",
            answerTemplate = "{{cloze:Text}}",
            fields = mapOf("Text" to "Je {{c1::suis::verb}} ici, tu {{c2::es}} là."),
            clozeNumber = 1
        )
        assertEquals("Je [verb] ici, tu es là.", card.question)
        assertEquals("Je suis ici, tu es là.", card.answer)
    }

    @Test
    fun `conditional fields and entities stay readable`() {
        val card = AnkiText.render(
            questionTemplate = "{{#Hint}}<i>{{Hint}}</i><br>{{/Hint}}{{Word}}",
            answerTemplate = "{{Meaning}}",
            fields = mapOf("Hint" to "formal", "Word" to "&eacute;lan", "Meaning" to "energy &amp; style"),
            clozeNumber = 1
        )
        assertTrue(card.question.contains("formal"))
        assertTrue(card.question.contains("élan") || card.question.contains("&eacute;lan"))
        assertEquals("energy & style", card.answer)
    }

    @Test
    fun `media is reported and executable html is removed`() {
        val card = AnkiText.render(
            questionTemplate = "{{Front}}",
            answerTemplate = "{{Back}}",
            fields = mapOf(
                "Front" to "word [sound:word.mp3]",
                "Back" to "<script>alert(1)</script><img src='word.png'> meaning"
            ),
            clozeNumber = 1
        )
        assertTrue(card.hadMedia)
        assertFalse(card.answer.contains("alert"))
        assertTrue(card.answer.contains("meaning"))
    }
}
