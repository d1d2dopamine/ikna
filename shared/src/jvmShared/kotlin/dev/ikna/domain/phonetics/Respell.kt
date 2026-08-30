package dev.ikna.domain.phonetics

import java.util.Locale

/**
 * IPA turned into something an English reader can say out loud on sight.
 *
 * The problem this solves is not "how does this word sound". The deck already
 * knows that, in IPA, written by the catalogue pipeline. The problem is that
 * almost nobody reads IPA. A learner who sees `d\u0361\u0291\u025b\u014b\u02c8kuj\u025b` under a Polish
 * phrase has been handed a second thing to learn, and the pronunciation they
 * came for is still hidden behind it.
 *
 * So the IPA is rewritten in English spelling conventions -- the same move
 * Wikipedia makes with its respelling key, and for the same reason: English
 * spelling is the one notation a very large number of people can already read,
 * whatever language they happen to be learning. `jeng-KOO-yeh` is not precise,
 * and it is not supposed to be. It gets somebody close enough to be understood,
 * immediately, with nothing to study first.
 *
 * ## What this deliberately gets wrong
 *
 * Wikipedia's own style guide says, in as many words, that respelling a foreign
 * pronunciation into English is "inadequate and misleading". That is a fair
 * description and it is worth writing down rather than hiding.
 *
 * English has no way to write the Polish `\u0279`, the French `\u0281`, or the Russian
 * palatalised `s\u02b2`. Every one of those is flattened here into the nearest
 * English sound. Vowel length mostly disappears. Tone is not represented at
 * all. Somebody who learns only from this line will have an accent.
 *
 * That trade is made on purpose, and the alternative is what makes it
 * defensible: the alternative is not "perfect pronunciation", it is *nothing*.
 * The choice is between an approximation the reader can use today and an IPA
 * string they will skip over, and this app is for people learning a language
 * rather than people studying phonetics. Anybody who does read IPA can switch
 * the deck to it -- that is what the third setting is for, and it shows the
 * stored string untouched.
 *
 * ## One deviation from Wikipedia
 *
 * Wikipedia writes the reduced vowel of `about` as `\u0259`, keeping the one IPA
 * symbol its key otherwise avoids. This renders it `uh` instead, which makes
 * every output pure ASCII.
 *
 * The reason is a font. This app ships its own typefaces and lets the reader
 * choose between them, and a chosen font that has no glyph for `\u0259` draws a
 * tofu box in the middle of the one line that is supposed to be easier to read
 * than the IPA above it. `uh` cannot fail to render in any font, ever. There is
 * no collision: `\u028c`, the vowel of `cut`, is written `u`.
 *
 * ## Worked examples
 *
 *     d\u0361\u0291\u025b\u014b\u02c8kuj\u025b   ->  jeng-KOO-yeh
 *     sp\u0250\u02c8s\u02b2ib\u0259     ->  spuh-SEE-buh
 *     ob\u027ei\u02c8\u0261adu      ->  ob-ree-GAH-doo
 *     b\u0254\u0303\u02c8\u0292u\u0281        ->  bong-ZHOOR
 *     \u02c8fs\u0282\u0268stk\u0254      ->  FSHIST-koh
 *
 * Everything here is a pure function of its input. Nothing is loaded, nothing
 * is looked up over the network, and the same string always gives the same
 * answer -- which is what lets the result be cached by the caller and tested
 * without a device.
 */
object Respell {

    // ---- marks that carry meaning ------------------------------------------

    /** Primary stress. The syllable after it is set in capitals. */
    private const val PRIMARY = '\u02C8'

    /**
     * Secondary stress, also set in capitals.
     *
     * Wikipedia does the same: `pr\u0259-NUN-see-AY-sh\u0259n` capitalises both. Trying to
     * show two strengths of stress in one line of plain text means inventing a
     * third case, and the reader has to be told what it means before it helps.
     */
    private const val SECONDARY = '\u02CC'

    // ---- marks that are removed before anything else -----------------------

    /** Tie bar above, joining the halves of an affricate. */
    private const val TIE_ABOVE = '\u0361'

    /** Tie bar below, same job. */
    private const val TIE_BELOW = '\u035C'

    /** Half-long. English spelling has no way to be half of anything. */
    private const val HALF_LONG = '\u02D1'

    /** Syllabic consonant. The syllable count is worked out from vowels. */
    private const val SYLLABIC = '\u0329'

    /** Non-syllabic, the mark under the second half of a written diphthong. */
    private const val NON_SYLLABIC = '\u032F'

    private const val NO_STRESS = 0
    private const val STRESS_PRIMARY = 1
    private const val STRESS_SECONDARY = 2

    /**
     * One sound and how to write it.
     *
     * Vowels carry two spellings because English spelling reads the same letter
     * differently depending on what follows it. The `e` of `jeng` and the `eh`
     * of `yeh` are the same Polish vowel; written `e` at the end of a syllable
     * it would be read as `ee`, and written `eh` in the middle it would look
     * like a typing mistake. [closed] is used when a consonant follows in the
     * same syllable, [open] when the vowel ends the syllable.
     *
     * Consonants set both to the same string, which costs nothing and keeps one
     * code path instead of two.
     */
    private class Phone(val closed: String, val open: String, val vowel: Boolean)

    /** A consonant. */
    private fun c(spelling: String) = Phone(spelling, spelling, false)

    /** A vowel, closed and open forms. */
    private fun v(closed: String, open: String = closed) = Phone(closed, open, true)

    /**
     * Every symbol this renderer knows, and how to write it in English.
     *
     * Order in this map is irrelevant -- lookup is by longest match, computed
     * from [LONGEST] -- but the groups below are kept in a deliberate order for
     * whoever has to read it. Multi-symbol entries come first because they are
     * the ones that are easy to get wrong: `t\u0283` has to be found before `t`, or
     * `chair` comes out as `t-shair`.
     *
     * A symbol that is not here is dropped rather than passed through. Passing
     * it through would put a character the reader cannot pronounce into the one
     * line whose entire purpose is that it can be pronounced, which is worse
     * than a slightly wrong word.
     */
    private val TABLE: Map<String, Phone> = mapOf(
        // ---- affricates, before the stops and fricatives they are made of ----
        // The tie bar is stripped first, so these arrive as two plain symbols
        // sitting next to each other and have to be matched as a pair.
        "t\u0283" to c("ch"),   // church
        "d\u0292" to c("j"),    // judge
        "t\u0255" to c("ch"),   // Polish c-acute, Mandarin q
        "d\u0291" to c("j"),    // Polish dz-acute
        "t\u0282" to c("ch"),   // Polish cz
        "d\u0290" to c("j"),    // Polish dz-caron
        "ts" to c("ts"),        // German z, Russian ts
        "dz" to c("dz"),
        "pf" to c("pf"),        // German Pferd
        "t\u026c" to c("tl"),

        // ---- long vowels, before the short ones they are built from --------
        // English spelling distinguishes these, so they are worth keeping even
        // though the length mark itself is thrown away everywhere else.
        "i\u02D0" to v("ee"),
        "u\u02D0" to v("oo"),
        "\u0251\u02D0" to v("ah"),
        "\u0254\u02D0" to v("aw"),
        "\u025C\u02D0" to v("ur"),
        "\u025B\u02D0" to v("air"),
        "a\u02D0" to v("ah"),
        "e\u02D0" to v("ay"),
        "o\u02D0" to v("oh"),
        "y\u02D0" to v("ew"),
        "\u00F8\u02D0" to v("ur"),

        // ---- diphthongs ----------------------------------------------------
        "a\u026A" to v("y"),      // price
        "a\u028A" to v("ow"),     // mouth
        "e\u026A" to v("ay"),     // face
        "o\u028A" to v("oh"),     // goat
        "\u0259\u028A" to v("oh"),
        "\u0254\u026A" to v("oy"),
        "\u026A\u0259" to v("eer"),
        "e\u0259" to v("air"),
        "\u028A\u0259" to v("oor"),
        "\u0254\u028F" to v("oy"),   // German euch
        "\u0254y" to v("oy"),
        "ai" to v("y"),
        "au" to v("ow"),
        "ei" to v("ay"),
        "oi" to v("oy"),
        "ou" to v("oh"),

        // ---- nasal vowels --------------------------------------------------
        // Written with a trailing `ng` rather than left bare. An English reader
        // given `ah` for the French `\u0251\u0303` says the wrong word; given `ahng`
        // they say something a French speaker recognises. It is the same
        // approximation English already makes in `restaurant`.
        "\u0251\u0303" to v("ahng"),
        "a\u0303" to v("ahng"),
        "\u0250\u0303" to v("ung"),   // Brazilian Portuguese -\u00e3o, first half
        "\u025B\u0303" to v("ang"),
        "e\u0303" to v("ang"),
        "\u0254\u0303" to v("ong"),
        "o\u0303" to v("ong"),
        "\u0153\u0303" to v("urng"),
        "u\u0303" to v("oong"),
        "i\u0303" to v("eeng"),

        // ---- plain consonants ----------------------------------------------
        "p" to c("p"),
        "b" to c("b"),
        "t" to c("t"),
        "d" to c("d"),
        "k" to c("k"),
        "g" to c("g"),
        "\u0261" to c("g"),       // the IPA script g, which is not ASCII g
        "c" to c("ky"),
        "\u025F" to c("gy"),
        "q" to c("k"),
        "m" to c("m"),
        "\u0271" to c("m"),
        "n" to c("n"),
        "\u014B" to c("ng"),
        "\u0272" to c("ny"),      // Spanish \u00f1
        "\u0273" to c("n"),
        "f" to c("f"),
        "v" to c("v"),
        "\u03B8" to c("th"),      // thin
        "\u00F0" to c("dh"),      // this
        "s" to c("s"),
        "z" to c("z"),
        "\u0283" to c("sh"),
        "\u0292" to c("zh"),
        "\u0282" to c("sh"),      // Polish sz
        "\u0290" to c("zh"),      // Polish \u017c
        "\u0255" to c("sh"),      // Polish \u015b
        "\u0291" to c("zh"),      // Polish \u017a
        "\u00E7" to c("kh"),      // German ich
        "x" to c("kh"),           // Russian \u0445, German ach
        "\u03C7" to c("kh"),
        "\u0263" to c("gh"),
        "h" to c("h"),
        "\u0266" to c("h"),
        "\u0278" to c("f"),
        "\u03B2" to c("v"),
        "l" to c("l"),
        "\u026B" to c("l"),
        "\u026D" to c("l"),
        "\u028E" to c("ly"),      // Italian gli
        "r" to c("r"),
        "\u027E" to c("r"),       // Spanish single r
        "\u0279" to c("r"),       // English r
        "\u0281" to c("r"),       // French r
        "\u0280" to c("r"),
        "\u027D" to c("r"),
        "\u027B" to c("r"),
        "j" to c("y"),
        "w" to c("w"),
        "\u028B" to c("v"),
        "\u0265" to c("w"),

        // ---- consonant modifiers that are simply dropped -------------------
        // Russian palatalisation is the one that matters here. `s\u02b2i` written as
        // `syee` is harder to read and no closer to right than `see`: an
        // English reader saying `see` before an `i` is already palatalising it
        // without being told to.
        "\u02B2" to c(""),
        "\u02B7" to c(""),
        "\u02B0" to c(""),
        "\u0294" to c(""),        // glottal stop
        "\u0295" to c(""),
        "\u02D0" to c(""),        // a length mark not caught by a pair above

        // ---- vowels --------------------------------------------------------
        "i" to v("ee"),
        "\u026A" to v("i"),
        "y" to v("ew"),           // French u, German \u00fc
        "\u028F" to v("uu"),
        "e" to v("e", "eh"),
        "\u025B" to v("e", "eh"),
        "\u00F8" to v("ur"),
        "\u0153" to v("ur"),
        "\u0259" to v("uh"),      // the deviation from Wikipedia, see above
        "\u0264" to v("uh"),
        "\u025C" to v("ur"),
        "\u025D" to v("ur"),
        "\u025A" to v("ur"),
        "\u025E" to v("ur"),
        "a" to v("a", "ah"),
        "\u00E6" to v("a"),
        "\u0250" to v("u", "uh"), // Russian unstressed a
        "\u0251" to v("ah"),
        "\u0252" to v("o"),
        "\u028C" to v("u"),
        "\u0254" to v("o", "oh"),
        "o" to v("o", "oh"),
        "u" to v("oo"),
        "\u028A" to v("uu"),
        "\u0289" to v("oo"),
        "\u026F" to v("oo"),
        "\u0268" to v("i"),       // Polish y, Russian \u044b
        "\u0275" to v("uh")
    )

    /**
     * The longest key in [TABLE], so the matcher knows how far ahead to look.
     *
     * Computed rather than written down, because a written-down number stops
     * being true the moment somebody adds a three-symbol entry, and it fails
     * silently -- the new entry is simply never matched.
     */
    private val LONGEST: Int = TABLE.keys.maxOfOrNull { it.length } ?: 1

    /** One matched sound, with any stress mark that came immediately before it. */
    private class Seg(val phone: Phone, val stress: Int)

    /** One syllable, written out, and the strongest stress inside it. */
    private class Syllable(val text: String, val stress: Int)

    /**
     * Render an IPA string.
     *
     * Returns an empty string when there is nothing to say -- blank input, or
     * input made entirely of symbols this renderer does not know. Never throws:
     * this runs while a card is being drawn, and a phrase whose transcription
     * happens to contain something unexpected must show no pronunciation rather
     * than take the session down.
     */
    fun render(ipa: String): String {
        if (ipa.isBlank()) return ""
        val words = clean(ipa).split(' ', '\u00A0', '\t')
        val out = ArrayList<String>(words.size)
        for (raw in words) {
            if (raw.isBlank()) continue
            val text = word(raw)
            if (text.isNotEmpty()) out.add(text)
        }
        return out.joinToString(" ")
    }

    /**
     * Strip everything that is punctuation around a transcription rather than
     * part of it, plus the marks that carry no weight in English spelling.
     *
     * The tie bars go first and that ordering matters: an affricate arrives as
     * `t\u0361\u0283` and is looked up as `t\u0283`, so if the tie survived, the pair would
     * never match and `church` would come out as `t-shurch`.
     */
    private fun clean(raw: String): String = raw.filter { ch ->
        ch != TIE_ABOVE && ch != TIE_BELOW &&
            ch != HALF_LONG && ch != SYLLABIC && ch != NON_SYLLABIC &&
            ch != '/' && ch != '[' && ch != ']' && ch != '(' && ch != ')' &&
            !ch.isISOControl()
    }

    /** One word: matched, split into syllables, and capitalised where stressed. */
    private fun word(raw: String): String {
        val segs = parse(raw)
        if (segs.isEmpty()) return ""
        val syllables = syllabify(segs)
        if (syllables.isEmpty()) return ""

        // A word of one syllable is never capitalised, however it was marked.
        // Wikipedia writes `cat` as `kat`, not `KAT`: capitals here mean "this
        // syllable and not the others", and with one syllable there are no
        // others, so all they would do is shout.
        if (syllables.size == 1) return syllables[0].text

        return syllables.joinToString("-") { syllable ->
            if (syllable.stress > NO_STRESS) syllable.text.uppercase(Locale.ROOT)
            else syllable.text
        }
    }

    /**
     * Walk the string, longest match first, collecting sounds.
     *
     * A stress mark does not produce a sound of its own; it is held and handed
     * to the next sound that does. That is why a modifier which renders as
     * nothing -- Russian palatalisation, a glottal stop -- must not consume it:
     * `\u02c8s\u02b2i` has to put the stress on `see`, not lose it.
     */
    private fun parse(raw: String): List<Seg> {
        val out = ArrayList<Seg>(raw.length)
        var pending = NO_STRESS
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (ch == PRIMARY) {
                pending = STRESS_PRIMARY
                i++
                continue
            }
            if (ch == SECONDARY) {
                pending = STRESS_SECONDARY
                i++
                continue
            }
            // Syllable dots and hyphens: this works syllables out from the
            // vowels, so somebody else's idea of where the breaks go is not
            // needed and would only conflict.
            if (ch == '.' || ch == '-' || ch == '\u203F') {
                i++
                continue
            }
            val hit = longestAt(raw, i)
            if (hit == null) {
                // Unknown symbol. Dropped, not passed through.
                i++
                continue
            }
            val (len, phone) = hit
            if (phone.closed.isNotEmpty() || phone.vowel) {
                out.add(Seg(phone, pending))
                pending = NO_STRESS
            }
            i += len
        }
        return out
    }

    /** The longest entry in [TABLE] that starts at [from], or null. */
    private fun longestAt(text: String, from: Int): Pair<Int, Phone>? {
        var len = minOf(LONGEST, text.length - from)
        while (len >= 1) {
            val phone = TABLE[text.substring(from, from + len)]
            if (phone != null) return len to phone
            len--
        }
        return null
    }

    /**
     * One syllable per vowel.
     *
     * Where two vowels are separated by consonants, the last of those
     * consonants starts the next syllable and the rest stay with the previous
     * one. That is a simplification of what languages actually do, and it is
     * the right simplification for this job: it is never badly wrong, it needs
     * no knowledge of the language, and the reader is being shown where the
     * stress falls rather than being taught syllable structure.
     *
     * A string with no vowel at all -- an abbreviation, or something that lost
     * its vowels to unknown symbols -- becomes a single syllable holding
     * whatever survived, rather than nothing.
     */
    private fun syllabify(segs: List<Seg>): List<Syllable> {
        val nuclei = ArrayList<Int>()
        for (i in segs.indices) if (segs[i].phone.vowel) nuclei.add(i)

        if (nuclei.isEmpty()) {
            val text = segs.joinToString("") { it.phone.closed }
            if (text.isEmpty()) return emptyList()
            return listOf(Syllable(text, segs.maxOfOrNull { it.stress } ?: NO_STRESS))
        }

        val starts = IntArray(nuclei.size)
        starts[0] = 0
        for (k in 1 until nuclei.size) {
            val previous = nuclei[k - 1]
            val here = nuclei[k]
            // Vowels side by side: the break goes between them. Otherwise the
            // last consonant of the run opens this syllable.
            starts[k] = if (here - previous <= 1) here else here - 1
        }

        val out = ArrayList<Syllable>(nuclei.size)
        for (k in nuclei.indices) {
            val from = starts[k]
            val to = if (k == nuclei.lastIndex) segs.lastIndex else starts[k + 1] - 1
            if (to < from) continue
            val text = StringBuilder()
            var stress = NO_STRESS
            for (i in from..to) {
                val seg = segs[i]
                if (seg.stress > stress) stress = seg.stress
                // The open form only when the vowel is the last thing in the
                // syllable. This is what makes `\u025b` come out as `e` in `jeng`
                // and as `eh` in `yeh`.
                text.append(
                    if (seg.phone.vowel && i == to) seg.phone.open else seg.phone.closed
                )
            }
            if (text.isNotEmpty()) out.add(Syllable(text.toString(), stress))
        }
        return out
    }
}
