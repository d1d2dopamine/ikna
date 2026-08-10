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
            val translation = parts[2].trim()

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
        line = LIST_MARKER.replace(line, "")
        // A Markdown table row: | a | b | c |
        if (line.startsWith("|")) line = line.removePrefix("|").trim()
        if (line.endsWith("|")) line = line.removeSuffix("|").trim()
        // The rule under a table header: |---|:---:|---|
        if (line.isNotEmpty() && line.all { it == '-' || it == ':' || it == '|' || it == ' ' }) return ""
        return line
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
