package dev.ikna.data.pack

/*
 * The format a person can actually produce, read on the phone.
 *
 * The packs this app ships are JSONL with a character span, a frequency rank and
 * a morphological analysis of every word in every sentence. That file is an
 * artefact of tools/genpack, and it is not something a human writes: asking for
 * one is asking someone to install Python before their first card. Anki's real
 * cost is not its algorithm, it is that the deck is homework, and homework is
 * exactly what the person this app is for cannot start.
 *
 * So there is a second way in, and it is three columns:
 *
 *     phrase | a sentence containing that phrase | what the phrase means
 *
 * Same three columns tools/genpack takes as its seed, so nothing new was
 * invented here and a file made for one path works on the other. The separator is
 * a pipe rather than a tab because this text arrives by paste, and a phone
 * keyboard has no tab key. Tabs are still accepted, since text pasted out of a
 * spreadsheet has them.
 *
 * What the phone does and does not do with it:
 *
 * - It resolves the target span, splits the sentence into words, and stops there.
 * - It does no lemmatisation and no part-of-speech tagging. Those need the tables
 *   that live in the generator, and a guessed lemma is worse than no lemma: it
 *   merges unrelated words in the component layer, so credit for an answer leaks
 *   to words that were never involved. The Polish pack is shipped under exactly
 *   this rule.
 * - Therefore only the words inside the trained phrase carry weight in a deck
 *   made this way. The surrounding sentence is still shown, still read aloud and
 *   still what makes the phrase memorable; it just does not earn credit it cannot
 *   prove. A deck that quietly rewarded "the" would be worse than one that
 *   rewards nothing.
 *
 * Everything below is deliberately forgiving about decoration and unforgiving
 * about content. Text written by a language model arrives numbered, bulleted,
 * wrapped in a code fence or laid out as a Markdown table, and none of that is
 * the user's mistake to fix by hand. A row whose sentence does not contain its
 * phrase, on the other hand, is refused and reported by line number, because that
 * row would teach a span that is not there.
 */

/** Why a line was refused. The screen turns these into sentences; this file has no language. */
enum class SeedProblem {
    NOT_THREE_COLUMNS,
    EMPTY_FIELD,
    PHRASE_NOT_IN_SENTENCE,
    TOO_LONG,
    DUPLICATE
}

/** One accepted line. */
data class SeedRow(val phrase: String, val sentence: String, val translation: String)

/** One refused line, kept with its number so the screen can quote it back. */
data class SeedLineProblem(val line: Int, val text: String, val problem: SeedProblem)

data class SeedParse(val rows: List<SeedRow>, val problems: List<SeedLineProblem>)

object SeedFormat {

    /**
     * Caps, not preferences. A phrase longer than this is a sentence, a sentence
     * longer than this does not fit a card at display size, and a paste larger
     * than MAX_ROWS is a mistake rather than a deck — the whole shipped English
     * pack is 121 lines.
     */
    const val MAX_PHRASE = 80
    const val MAX_SENTENCE = 300
    const val MAX_TRANSLATION = 160
    const val MAX_ROWS = 5000

    /**
     * Splits a paste into rows and problems. Never throws: a bad line is data
     * about a bad line, and an import that aborts on line 40 of 300 wastes the
     * other 299.
     */
    fun parse(text: String): SeedParse {
        val rows = ArrayList<SeedRow>()
        val problems = ArrayList<SeedLineProblem>()
        val seen = HashSet<String>()
        var number = 0

        for (raw in text.lineSequence()) {
            number++
            if (rows.size >= MAX_ROWS) break
            val line = undecorate(raw)
            if (line.isEmpty()) continue

            val parts = columns(line)
            if (parts.size != 3) {
                problems += SeedLineProblem(number, line, SeedProblem.NOT_THREE_COLUMNS)
                continue
            }

            val phrase = parts[0].trim()
            val sentence = parts[1].trim()
            // Tidied before it is measured or stored: what arrives in this column
            // is a translation with the model's habits attached to it.
            val translation = tidyTranslation(phrase, parts[2].trim())

            if (phrase.isEmpty() || sentence.isEmpty() || translation.isEmpty()) {
                problems += SeedLineProblem(number, line, SeedProblem.EMPTY_FIELD)
                continue
            }
            if (phrase.length > MAX_PHRASE ||
                sentence.length > MAX_SENTENCE ||
                translation.length > MAX_TRANSLATION
            ) {
                problems += SeedLineProblem(number, line, SeedProblem.TOO_LONG)
                continue
            }
            if (spanOf(sentence, phrase) == null) {
                problems += SeedLineProblem(number, line, SeedProblem.PHRASE_NOT_IN_SENTENCE)
                continue
            }
            // Case-folded, because the same phrase twice in two capitalisations is
            // one card being scheduled twice against itself.
            if (!seen.add(phrase.lowercase())) {
                problems += SeedLineProblem(number, line, SeedProblem.DUPLICATE)
                continue
            }

            rows += SeedRow(phrase = phrase, sentence = sentence, translation = translation)
        }

        return SeedParse(rows = rows, problems = problems)
    }

    /**
     * Which of the two formats a paste or a file is, decided on its first real
     * line. Sniffed rather than taken from the file extension: a file picked out
     * of a chat app arrives called "document", and text pasted from a keyboard has
     * no name at all.
     */
    fun looksLikeJsonl(text: String): Boolean = firstRealLine(text).startsWith("{")

    fun looksLikeSeed(text: String): Boolean {
        val line = firstRealLine(text)
        if (line.isEmpty() || line.startsWith("{")) return false
        return line.contains('|') || line.contains('\t')
    }

    /**
     * Accepted rows as chunks the rest of the app already knows how to store.
     *
     * Ids are the pack id plus the row's position, so re-importing a corrected
     * file updates the same cards instead of doubling the deck: chunks are
     * upserted by id, and a person fixing line 12 of their own deck should not
     * lose the history of the other 200.
     */
    fun chunks(packId: String, rows: List<SeedRow>): List<PackChunk> =
        rows.mapIndexed { index, row ->
            val start = spanOf(row.sentence, row.phrase) ?: 0
            val end = start + row.phrase.length
            PackChunk(
                id = packId + "-" + (index + 1).toString().padStart(4, '0'),
                text = row.phrase,
                context = row.sentence,
                translation = row.translation,
                targetStart = start,
                targetEnd = end,
                // Order is the only ranking available here. It is not nothing: a
                // person writing their own deck puts what they care about first,
                // and a list from a language model comes out roughly in frequency
                // order anyway.
                freqRank = index + 1,
                tokens = tokens(row.sentence, start, end),
                audioRef = null
            )
        }

    /**
     * Words, Unicode-aware. `[A-Za-z]` was tried once in the generator and it ate
     * every diacritic, turning "\u015bniadaniem" into "niadaniem".
     *
     * isContent is true only inside the trained span. It is the flag that decides
     * whether a word earns partial credit in the component layer, and outside the
     * span this file has no way to tell a content word from "the" — so it claims
     * nothing. PackLoader gives the span itself full weight regardless.
     */
    fun tokens(sentence: String, targetStart: Int, targetEnd: Int): List<PackToken> =
        WORD.findAll(sentence).map { match ->
            val inTarget = match.range.first >= targetStart && match.range.last < targetEnd
            PackToken(
                surface = match.value,
                lemma = match.value.lowercase(),
                pos = "WORD",
                isContent = inTarget
            )
        }.toList()

    /**
     * Where the phrase sits in its sentence.
     *
     * Exact match first, then case-insensitive, because a phrase at the start of
     * its own example sentence is capitalised there and lower case in the column,
     * which is correct writing rather than a mistake. The span keeps the
     * sentence's own characters either way, since both matches have the same
     * length.
     */
    fun spanOf(sentence: String, phrase: String): Int? {
        val exact = sentence.indexOf(phrase)
        if (exact >= 0) return exact
        val loose = sentence.lowercase().indexOf(phrase.lowercase())
        return if (loose >= 0) loose else null
    }

    /**
     * Strips what a language model wraps an answer in, and nothing else.
     *
     * Code fences, list bullets, "1." numbering and the pipes and dashes of a
     * Markdown table are all decoration around a correct row. Making the person
     * delete them by hand, on a phone, is the moment they close the app.
     */
    private fun undecorate(raw: String): String {
        var line = raw.trim()
        if (line.isEmpty()) return ""
        // A fence, or a comment line in a file written by hand.
        if (line.startsWith("```") || line.startsWith("#")) return ""
        // A Markdown table row: | a | b | c |
        //
        // Both ends at once, deliberately. A trailing pipe on its own is not
        // decoration: "phrase | sentence |" is a line whose third field is empty,
        // and stripping that pipe reported a missing translation as "this line
        // has not got three fields" - a different mistake, with a different fix
        // printed under it. A row that really came out of a table has the leading
        // pipe too, so that is what identifies one.
        if (line.startsWith("|")) {
            line = line.removePrefix("|").trim()
            if (line.endsWith("|")) line = line.removeSuffix("|").trim()
        }
        // After the pipes, not before. A model that answers with a numbered table
        // puts the number inside the first cell - "| 1. hang on | ..." - where a
        // pattern anchored to the start of the line cannot see it. The number
        // stayed glued to the phrase, the phrase was then not found in its own
        // sentence, and a correct row was refused for a reason that read like
        // nonsense to the person who wrote it.
        line = LIST_MARKER.replace(line, "")
        // The rule under a table header: |---|:---:|---|
        if (line.isNotEmpty() && line.all { it == '-' || it == ':' || it == '|' || it == ' ' }) return ""
        return line
    }

    /**
     * The translation column as it should have arrived.
     *
     * Two habits are corrected, both of them a model's and neither of them the
     * writer's fault:
     *
     * The phrase asks something and the translation states it. "how are you?"
     * comes back without its question mark, or "watch out!" without the one that
     * carries the whole tone of it, and the card then teaches a question or a
     * warning that does not look like one. The phrase's own ending is copied
     * over, and only the mark: nothing else about the wording is touched.
     *
     * The translation brings friends: "tired, weary, worn out", or "tired / worn
     * out", or "tired (colloquial)". Three wordings on the back of a card is
     * three things to check an answer against, and the answer is graded by the
     * person reading it, so a list quietly turns every recall into a
     * multiple-choice question. The first wording is kept.
     *
     * Deliberately conservative: it never invents words, and when a line does
     * not clearly show one of these two habits it is left exactly as written.
     * A wrong translation is the writer's to fix; a mangled one would be ours.
     */
    fun tidyTranslation(phrase: String, translation: String): String {
        val cut = oneWording(phrase, translation)
        return mirrorEnding(phrase, cut).trim()
    }

    /** Closing quotes and brackets, which sit outside the punctuation we read. */
    private val CLOSERS = charArrayOf('"', '\'', '\u00bb', '\u201d', '\u2019', ')', ']')

    private val ENDINGS = charArrayOf('?', '!', '.', '\u2026', '\u061f')

    /** What a line ends with: "?", "?!", "..." or nothing. */
    private fun endingOf(text: String): String {
        var end = text.trimEnd().trimEnd(*CLOSERS).length
        val body = text.trimEnd().trimEnd(*CLOSERS)
        var start = end
        while (start > 0 && body[start - 1] in ENDINGS) start--
        return body.substring(start, end)
    }

    /**
     * The sentence's ending, on the translation.
     *
     * Only the mark is copied, and only when the two differ. A translation that
     * already ends the right way is untouched, which is the common case and must
     * stay free.
     */
    private fun mirrorEnding(phrase: String, translation: String): String {
        val want = endingOf(phrase)
        val have = endingOf(translation)
        if (want == have) return translation
        val closers = translation.trimEnd().takeLastWhile { it in CLOSERS }
        val bare = translation.trimEnd().dropLast(closers.length)
        val body = bare.trimEnd().dropLast(have.length).trimEnd()
        if (body.isEmpty()) return translation
        return body + want + closers
    }

    /**
     * One wording, not a list of them.
     *
     * Brackets and slashes are cut without hesitation: nothing that belongs in a
     * translation is written that way. A comma is different - a phrase can
     * legitimately contain one - so a comma is only treated as a synonym list
     * when the phrase being translated has no comma of its own and every piece
     * after the split is short enough to be a single word or two.
     */
    private fun oneWording(phrase: String, translation: String): String {
        var out = translation

        // "tired (colloquial)" -> "tired". Only a tail, never a bracket that
        // opens the line: that one is somebody's deliberate note.
        val open = out.lastIndexOf('(')
        if (open > 0 && out.trimEnd().trimEnd(*CLOSERS).length <= out.length) {
            val closed = out.indexOf(')', open)
            if (closed > open && out.substring(closed + 1).isBlank()) {
                out = out.substring(0, open).trimEnd()
            }
        }

        // A slash separates wordings and nothing else here. Guarded against
        // dates and fractions, which are digits on both sides.
        val slash = out.indexOf('/')
        if (slash > 0 && slash < out.length - 1) {
            val before = out[slash - 1]
            val after = out[slash + 1]
            if (!(before.isDigit() && after.isDigit())) {
                out = out.substring(0, slash).trimEnd()
            }
        }

        // A semicolon in a one-line translation is a list separator.
        val semi = out.indexOf(';')
        if (semi > 0) out = out.substring(0, semi).trimEnd()

        // The careful one.
        if (!phrase.contains(',') && out.contains(',')) {
            val parts = out.split(',').map { it.trim() }
            val shortEnough = parts.all { part ->
                part.isNotEmpty() && WORD.findAll(part).count() <= 3
            }
            if (shortEnough && parts.size >= 2) out = parts.first()
        }

        return out.trim().ifEmpty { translation }
    }

    private fun columns(line: String): List<String> = when {
        line.contains('|') -> line.split('|')
        line.contains('\t') -> line.split('\t')
        else -> emptyList()
    }

    private fun firstRealLine(text: String): String {
        for (raw in text.lineSequence()) {
            val line = undecorate(raw)
            if (line.isNotEmpty()) return line
        }
        return ""
    }

    private val LIST_MARKER = Regex("^(?:[-*\u2022\u2013]\\s+|\\d+[.)]\\s+)")

    private val WORD = Regex("[\\p{L}\\p{M}\\p{N}\u2019']+")
}
