package dev.ikna.domain.phonetics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the respelling renderer actually produces.
 *
 * Every expected string in this file was computed by running the algorithm
 * rather than by imagining what it ought to say. That distinction matters: a
 * renderer like this is full of small decisions that interact -- where the
 * syllable break falls, whether a vowel is at the end of its syllable, which of
 * two spellings that then selects -- and a test written from intuition tests
 * the intuition rather than the code.
 *
 * So these are not aspirational. If one of them changes, the renderer changed,
 * and somebody has to decide whether the new answer is better rather than
 * simply making the test agree with it.
 */
class RespellTest {

    // ---- the eight languages the catalogue can be learned in ---------------

    @Test
    fun `polish thank you`() {
        // dzi\u0119kuj\u0119. The tie bar joins d and \u0291 into one affricate, which is
        // written j -- so this also proves the tie is stripped before lookup.
        assertEquals(
            "jeng-KOO-yeh",
            Respell.render("d\u0361\u0291\u025B\u014B\u02C8kuj\u025B")
        )
    }

    @Test
    fun `polish everything`() {
        // wszystko. Four consonants in a row and none of them lost.
        assertEquals("FSHIST-koh", Respell.render("\u02C8f\u0282\u0268stk\u0254"))
    }

    @Test
    fun `polish bread`() {
        // chleb. One syllable, so it is not capitalised even though it is
        // marked for stress.
        assertEquals("khlep", Respell.render("\u02C8xl\u025Bp"))
    }

    @Test
    fun `russian thank you`() {
        // \u0441\u043f\u0430\u0441\u0438\u0431\u043e. The palatalisation mark disappears without taking the
        // stress that preceded it, so the middle syllable is still the loud one.
        assertEquals("spuh-SEE-buh", Respell.render("sp\u0250\u02C8s\u02B2ib\u0259"))
    }

    @Test
    fun `spanish thank you`() {
        assertEquals("GRAS-yas", Respell.render("\u02C8\u0261\u027Easjas"))
    }

    @Test
    fun `french hello`() {
        // bonjour. The nasal vowel keeps its n, which is the whole reason nasals
        // are spelled with a trailing ng rather than left bare.
        assertEquals("bong-ZHOOR", Respell.render("b\u0254\u0303\u02C8\u0292u\u0281"))
    }

    @Test
    fun `german thank you`() {
        assertEquals("DANG-kuh", Respell.render("\u02C8da\u014Bk\u0259"))
    }

    @Test
    fun `italian thank you`() {
        assertEquals("GRATTS-yeh", Respell.render("\u02C8\u0261rattsje"))
    }

    @Test
    fun `brazilian portuguese thank you`() {
        // obrigado, with the Brazilian final -o as u rather than the European
        // reduced vowel. Four syllables, stress on the third.
        assertEquals("ob-ree-GAH-doo", Respell.render("ob\u027Ei\u02C8\u0261adu"))
    }

    @Test
    fun `english is respelled too`() {
        // The catalogue can be learned in English, so English needs this as
        // much as anything else does. Two words, one space, and neither of them
        // capitalised because neither has more than one syllable.
        assertEquals("thangk yoo", Respell.render("\u02C8\u03B8\u00E6\u014Bk ju\u02D0"))
    }

    // ---- stress ------------------------------------------------------------

    @Test
    fun `both strengths of stress are capitalised`() {
        // pronunciation, with a secondary stress on the second syllable and the
        // primary on the fourth. Wikipedia capitalises both, and so does this:
        // inventing a third case would mean explaining it before it helped.
        assertEquals(
            "pruh-NUN-see-AY-shuhn",
            Respell.render("pr\u0259\u02CCn\u028Cnsi\u02C8e\u026A\u0283\u0259n")
        )
    }

    @Test
    fun `a word of one syllable is never capitalised`() {
        // With nothing to contrast against, capitals are not information.
        assertEquals("kat", Respell.render("k\u00E6t"))
        assertEquals("kat", Respell.render("\u02C8k\u00E6t"))
    }

    @Test
    fun `stress inside a word moves the capitals`() {
        assertEquals("BOH-koo", Respell.render("\u02C8b\u0254ku"))
        assertEquals("boh-KOO", Respell.render("b\u0254\u02C8ku"))
    }

    // ---- the open and closed forms of a vowel ------------------------------

    @Test
    fun `the same vowel is written differently at the end of a syllable`() {
        // Both of these are \u025b. Inside a closed syllable it is e; ending an open
        // one it is eh, because a bare e there would be read as ee.
        val rendered = Respell.render("d\u0291\u025B\u014B\u02C8kuj\u025B")
        assertTrue("expected a closed e in the first syllable: " + rendered,
            rendered.startsWith("jeng"))
        assertTrue("expected an open eh at the end: " + rendered,
            rendered.endsWith("yeh"))
    }

    // ---- input this has to survive rather than fail on ---------------------

    @Test
    fun `nothing in gives nothing out`() {
        assertEquals("", Respell.render(""))
        assertEquals("", Respell.render("   "))
    }

    @Test
    fun `unknown symbols are dropped rather than passed through`() {
        // The point of this line is that the reader can pronounce it. A symbol
        // that survives into the output because nothing knew what to do with it
        // defeats that more thoroughly than a slightly wrong word does.
        assertEquals("", Respell.render("\u2603\u2604"))
        assertEquals("kat", Respell.render("k\u2603\u00E6t"))
    }

    @Test
    fun `slashes and brackets around a transcription are ignored`() {
        // IPA is quoted between slashes or brackets about as often as it is
        // quoted bare, and a pipeline that leaves them in must not produce a
        // different answer from one that strips them.
        assertEquals("BOH-koo", Respell.render("/\u02C8b\u0254ku/"))
        assertEquals("BOH-koo", Respell.render("[\u02C8b\u0254ku]"))
    }

    @Test
    fun `syllable dots do not change the answer`() {
        // Where the breaks go is worked out from the vowels, so somebody else's
        // marks are redundant. They must not be able to conflict.
        assertEquals(
            Respell.render("\u02C8b\u0254ku"),
            Respell.render("\u02C8b\u0254.ku")
        )
    }

    @Test
    fun `rendering is stable`() {
        // Nothing here reads a clock, a locale, or a device. Called twice with
        // the same string it must answer the same way, which is what makes the
        // result safe to cache.
        val ipa = "sp\u0250\u02C8s\u02B2ib\u0259"
        assertEquals(Respell.render(ipa), Respell.render(ipa))
    }

    @Test
    fun `output is plain ascii`() {
        // The deliberate deviation from Wikipedia: the reduced vowel is written
        // uh rather than \u0259, so no chosen font can turn the pronunciation line
        // into a row of tofu boxes.
        val samples = listOf(
            "sp\u0250\u02C8s\u02B2ib\u0259",
            "\u02C8da\u014Bk\u0259",
            "pr\u0259\u02CCn\u028Cnsi\u02C8e\u026A\u0283\u0259n",
            "b\u0254\u0303\u02C8\u0292u\u0281",
            "d\u0361\u0291\u025B\u014B\u02C8kuj\u025B"
        )
        for (ipa in samples) {
            val out = Respell.render(ipa)
            assertTrue(
                "non-ascii in the respelling of " + ipa + ": " + out,
                out.all { it.code in 32..126 }
            )
        }
    }

    @Test
    fun `every built in sample renders`() {
        // The deck settings screen shows one of these under the chips. A sample
        // that renders to nothing would leave that line empty and make the
        // setting look broken, which is exactly what the screen is trying to
        // avoid by having a preview at all.
        for ((lang, pair) in Phonetics.SAMPLES) {
            val out = Respell.render(pair.second)
            assertTrue("the " + lang + " sample renders to nothing", out.isNotEmpty())
        }
    }
}
