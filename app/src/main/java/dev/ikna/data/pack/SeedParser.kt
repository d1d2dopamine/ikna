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
    DUPLICATE,
    /**
     * The whole text arrived as one line carrying hundreds of separators: a
     * paste whose line breaks were eaten on the way in, which [unglue] could
     * not put back. A different thing from a line with a stray pipe in it, and
     * it needs a different sentence printed under it.
     */
    ONE_LONG_LINE
}

/**
 * Why an accepted line is worth a second look.
 *
 * A deck can now be written by a model in half a minute, and nothing inside this
 * app can check whether what it wrote is true -- there is no network permission
 * and there never will be, and a second model would only be a second guess. What
 * can be checked is whether a line carries the marks of text that was produced to
 * fill a quota rather than to teach something:
 *
 *  - a definition that just repeats the term it defines teaches nothing;
 *  - a hedge ("probably", "кажется") is the model saying it does not know;
 *  - the same meaning under two different terms is padding;
 *  - numbers and dates are what a model invents most confidently and most often.
 *
 * None of these refuses a line. They are the difference between an import that
 * says "200 cards" and one that says "200 cards, and these seven are worth
 * reading before you learn them" -- which is the only honest thing an offline app
 * can say about generated material.
 */
enum class SeedWarning {
    DEFINITION_REPEATS_TERM,
    HEDGED,
    SAME_MEANING,
    HAS_NUMBERS
}

/**
 * One accepted line.
 *
 * [source] is the optional fourth column: where the card came from -- a chapter, a
 * paper, a lecture. It is the cheapest defence there is against a wrong card,
 * because it turns "is this true?" into a place to go and look, and it is the one
 * thing a model can be asked for that it cannot fake without becoming obviously
 * wrong. Empty for every deck that does not use it, which is every deck written
 * before this version.
 */
data class SeedRow(
    val phrase: String,
    val sentence: String,
    val translation: String,
    val source: String = ""
)

/** One refused line, kept with its number so the screen can quote it back. */
data class SeedLineProblem(val line: Int, val text: String, val problem: SeedProblem)

/** One accepted but suspicious line, kept the same way. */
data class SeedLineWarning(val line: Int, val text: String, val warning: SeedWarning)

data class SeedParse(
    val rows: List<SeedRow>,
    val problems: List<SeedLineProblem>,
    val warnings: List<SeedLineWarning> = emptyList()
)

object SeedFormat {

    /**
     * Caps, not preferences. A phrase longer than this is a sentence, and a
     * sentence longer than this does not fit a card at display size.
     *
     * MAX_ROWS is the one that grew: 5000 to 10000. It is not a target. Nobody
     * learns ten thousand cards, and nothing in this app asks them to — the
     * governor still lets in a handful of new cards a day and refuses to be
     * hurried. What the old cap did was punish the opposite habit: bringing a
     * whole book, or a year of a course, once, and then being fed from it for
     * as long as it lasts. A deck is storage; the day is what is rationed.
     *
     * Ten thousand rows is roughly a megabyte and a half of text and thirty
     * thousand cards' worth of rows, which imports in seconds from a file and
     * is well past what anyone will paste by hand.
     */
    const val MAX_PHRASE = 80
    const val MAX_SENTENCE = 300
    const val MAX_TRANSLATION = 160
    const val MAX_ROWS = 10000

    /**
     * The optional fourth column: a pointer, not a paragraph. Sixty characters
     * hold "Kandel ch. 65" or "arXiv:2103.00020" and refuse an essay.
     */
    const val MAX_SOURCE = 60

    /** How a source is joined to the meaning on the card itself. */
    const val SOURCE_MARK = "\n\u2014 "

    /**
     * How many suspicious lines are remembered. The count is what the screen
     * shows; the list only has to be long enough to name the first one.
     */
    const val MAX_WARNINGS = 500

    /**
     * Splits a paste into rows and problems. Never throws: a bad line is data
     * about a bad line, and an import that aborts on line 40 of 300 wastes the
     * other 299.
     */
    fun parse(text: String): SeedParse {
        val rows = ArrayList<SeedRow>()
        val problems = ArrayList<SeedLineProblem>()
        val warnings = ArrayList<SeedLineWarning>()
        val seen = HashSet<String>()
        // Meaning -> the line that said it first. Two terms with one definition is
        // padding, and it can only be seen by remembering what came before.
        val meanings = HashMap<String, Int>()
        var number = 0

        for (raw in unglue(text).lineSequence()) {
            number++
            if (rows.size >= MAX_ROWS) break
            val line = undecorate(raw)
            if (line.isEmpty()) continue

            val parts = columns(line)
            // Three columns, or four when the deck names where each card came
            // from. A fourth column used to fail the whole line, which meant the
            // one habit worth encouraging -- citing a source -- was the one thing
            // the importer punished.
            if (parts.size != 3 && parts.size != 4) {
                // Seven fields or more on one line, after the rescue above has
                // already tried and failed to split it: that is a deck that lost
                // its line breaks, not a row with a stray pipe. Reporting it as
                // "line 1 has not got three fields" was true and useless, said
                // about three hundred rows that were all correct.
                //
                // It does not have to be the only line in the text. A keyboard
                // flattens in patches, and a text of four such lines was being
                // told about a stray pipe four times.
                val flattened = parts.size >= 7
                problems += SeedLineProblem(
                    number,
                    line,
                    if (flattened) SeedProblem.ONE_LONG_LINE
                    else SeedProblem.NOT_THREE_COLUMNS
                )
                continue
            }

            val phrase = parts[0].trim()
            val sentence = parts[1].trim()
            // Tidied before it is measured or stored: what arrives in this column
            // is a translation with the model's habits attached to it.
            val translation = tidyTranslation(phrase, parts[2].trim())
            val source = if (parts.size == 4) parts[3].trim().take(MAX_SOURCE) else ""

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

            rows += SeedRow(
                phrase = phrase,
                sentence = sentence,
                translation = translation,
                source = source
            )

            // Accepted, and still worth reading before it is learned.
            if (warnings.size < MAX_WARNINGS) {
                val key = normalisedMeaning(translation)
                val repeated = key.isNotEmpty() && meanings.containsKey(key)
                if (key.isNotEmpty()) meanings.putIfAbsent(key, number)
                val warning =
                    if (repeated) SeedWarning.SAME_MEANING else suspicion(phrase, translation)
                if (warning != null) warnings += SeedLineWarning(number, line, warning)
            }
        }

        return SeedParse(rows = rows, problems = problems, warnings = warnings)
    }

    /**
     * Words a model reaches for when it is guessing. Kept short and literal on
     * purpose: this is a hint, and a hint that fires on every second card is
     * noise that gets ignored, which is worse than no hint at all.
     */
    private val HEDGES = listOf(
        "maybe", "perhaps", "probably", "i think", "as far as i know", "not sure",
        "возможно", "вероятно", "кажется", "по-видимому", "наверное",
        "chyba", "prawdopodobnie", "byc moze"
    )

    /** Three digits or more: a year, a constant, a dose. Not "two" or "5%". */
    private val LONG_NUMBER = Regex("[0-9]{3,}")

    /**
     * The first thing wrong with an otherwise valid line, or null.
     *
     * One warning per line, in order of how much it matters. A line can be all
     * three at once, and a screen that says three things about one line says
     * nothing about the other 199.
     */
    private fun suspicion(phrase: String, translation: String): SeedWarning? {
        val meaning = translation.lowercase()
        val term = phrase.lowercase()
        if (term.length >= 3 && meaning.contains(term)) {
            return SeedWarning.DEFINITION_REPEATS_TERM
        }
        if (HEDGES.any { meaning.contains(it) }) return SeedWarning.HEDGED
        if (LONG_NUMBER.containsMatchIn(meaning)) return SeedWarning.HAS_NUMBERS
        return null
    }

    /** Case, punctuation and spacing removed, so two paddings match. */
    private fun normalisedMeaning(translation: String): String =
        translation.lowercase()
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .trim()
            .replace(WHITESPACE, " ")

    private val WHITESPACE = Regex("\\s+")

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
    fun chunks(
        packId: String,
        rows: List<SeedRow>,
        startIndex: Int = 0
    ): List<PackChunk> =
        rows.mapIndexed { index, row ->
            // Where this batch begins. Zero when a deck is imported whole, and
            // the size of the deck when cards are added to one that exists: ids
            // continue instead of restarting, so a second portion cannot
            // overwrite the first card by card.
            val position = startIndex + index + 1
            val start = spanOf(row.sentence, row.phrase) ?: 0
            val end = start + row.phrase.length
            PackChunk(
                id = packId + "-" + position.toString().padStart(4, '0'),
                text = row.phrase,
                context = row.sentence,
                // The source travels with the meaning, because there is
                // nowhere else to put it without a schema migration -- and a
                // citation the learner can see while doubting a card is worth
                // far more than a tidy column would be.
                translation = if (row.source.isEmpty()) {
                    row.translation
                } else {
                    row.translation + SOURCE_MARK + row.source
                },
                targetStart = start,
                targetEnd = end,
                // Order is the only ranking available here. It is not nothing: a
                // person writing their own deck puts what they care about first,
                // and a list from a language model comes out roughly in frequency
                // order anyway.
                freqRank = position,
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
    /**
     * Puts back the line breaks a paste can lose on the way in.
     *
     * A phone keyboard handed a hundred kilobytes of table sometimes delivers
     * it as one line, every break flattened away. The importer then read the
     * whole deck as row one with six hundred columns and said "line 1 has not
     * got three fields" -- the most confusing thing it could possibly say about
     * a paste that was, field for field, perfectly correct.
     *
     * Every line is looked at on its own, and that is the whole of the fix this
     * function needed. A keyboard does not always flatten the entire text: it
     * keeps a break here and there and glues everything between them, so three
     * hundred rows arrive as four enormous lines rather than as one. Requiring a
     * single line meant that case was left exactly as broken as before -- every
     * glued line refused for having six hundred fields, and the one line that
     * happened to hold three became the entire import.
     *
     * A line that is not several rows glued together is returned untouched, so a
     * deck that arrived whole passes through this function unchanged.
     */
    private fun unglue(text: String): String {
        if (text.none { it == '|' || it == '\t' }) return text
        return text.lineSequence().joinToString("\n") { unglueLine(it) }
    }

    /**
     * One glued line as the rows it was written as, or unchanged.
     *
     * The width is not decided by counting fields: twelve divide by three and by
     * four, and guessing wrong there does not cost a line -- it shifts every
     * field by one place and every card in the deck becomes false in the same
     * quiet way, a source read as a phrase, a phrase as a sentence, a sentence as
     * a meaning. The deck settles it instead. A card's phrase appears inside its
     * own sentence, always, and that is the rule this parser already refuses
     * lines over, so the width that satisfies it on more rows is the width the
     * text was written in.
     *
     * The same rule says when not to split at all. A line with six fields and a
     * stray bar in it is one refused line; cutting it into two rows of three
     * would import half a sentence as a meaning instead of saying what was
     * wrong. So a split has to read like cards on at least two rows and on at
     * least half of them, or the line is left as it stands and reported.
     */
    private fun unglueLine(line: String): String {
        val separator = when {
            line.contains('|') -> '|'
            line.contains('\t') -> '\t'
            else -> return line
        }
        val fields = line.split(separator).map { it.trim() }.filter { it.isNotEmpty() }
        if (fields.size < 6) return line
        val asThrees = reads(fields, 3)
        val asFours = reads(fields, 4)
        val width = if (asFours > asThrees) 4 else 3
        val rows = fields.chunked(width)
        val whole = rows.count { it.size == width }
        val readable = if (width == 4) asFours else asThrees
        if (readable < 2 || readable * 2 < whole) return line
        return rows.joinToString("\n") { it.joinToString(" | ") }
    }

    /**
     * How many rows of this width read like cards rather than like an accident:
     * the first field found inside the second, which is the one thing every
     * line of every deck in this app has in common.
     */
    private fun reads(fields: List<String>, width: Int): Int =
        fields.chunked(width)
            .count { it.size == width && spanOf(it[1], it[0]) != null }

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
