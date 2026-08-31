package dev.ikna.desktop.anki

/** A rendered Anki card after templates and cloze markers have been resolved. */
data class AnkiRenderedCard(
    val question: String,
    val answer: String,
    val usedFallback: Boolean,
    val hadMedia: Boolean
)

/** A cloze deletion located inside its own sentence. */
data class ClozeTarget(
    val context: String,
    val start: Int,
    val end: Int
)

/** What kind of cloze card a note and a cloze number turn out to describe. */
enum class ClozeShape {
    /** One deletion with this number: the sentence and the gap are both known. */
    SINGLE,

    /** The same number used more than once, so the card has several gaps. */
    MULTI_GAP,

    /** A deletion inside another deletion. */
    NESTED,

    /** Nothing with this number, or nothing readable left after stripping. */
    ABSENT
}

/** The result of reading a cloze note: its shape, and its span when it has one. */
data class ClozeReading(
    val shape: ClozeShape,
    val target: ClozeTarget?
)

/**
 * Small, deterministic subset of the Anki template language.
 *
 * Ikna stores a question and an answer, not executable HTML. Field replacement,
 * conditional sections, text filters and cloze deletions are kept; CSS,
 * JavaScript and media tags are deliberately reduced to readable text. That is
 * safer than running code out of an imported package, and it makes unsupported
 * cards detectable before anything is written to the database.
 */
object AnkiText {
    private val tag = Regex("<[^>]+>")
    private val script = Regex("(?is)<(script|style)[^>]*>.*?</\\1>")
    private val comment = Regex("(?s)<!--.*?-->")
    private val breakTag = Regex("(?i)<br\\s*/?>")
    private val blockEnd = Regex("(?i)</(?:div|p|li|tr|h[1-6])\\s*>")
    private val imageTag = Regex("(?is)<img\\b[^>]*>")
    private val sound = Regex("(?i)\\[sound:[^]]+]")
    private val moustache = Regex("\\{\\{([^{}]+)\\}\\}")
    private val cloze = Regex("\\{\\{c(\\d+)::(.*?)(?:::(.*?))?\\}\\}", RegexOption.DOT_MATCHES_ALL)
    private val whitespace = Regex("[ \\t\\x0B\\f\\r]+")
    private val blankLines = Regex("\\n{3,}")
    private val numericEntity = Regex("&#(x?[0-9A-Fa-f]+);")

    /**
     * A tag standing between a word and the punctuation after it.
     *
     * Tags normally become a space, so that "one</b><b>two" does not turn into
     * "onetwo". In front of punctuation that space is wrong -- "Bruder</b>."
     * reads as "Bruder ." -- and it cannot be tidied up afterwards, because by
     * then the cloze span has already been measured against the text. Dropping
     * the tag instead leaves whatever spacing the author typed alone, which
     * matters in French, where a space before a colon or an exclamation mark is
     * correct rather than a mistake.
     */
    private val tagBeforePunctuation =
        Regex("<[^>]+>(?=[.,;:!?\u2026\u3002\uFF01\uFF1F)\\]}\u00BB\u201D])")

    /**
     * Private-use characters standing in for the edges of a cloze deletion
     * while the field is being stripped of HTML.
     *
     * Offsets cannot be measured in the raw field and carried over, because
     * stripping tags, decoding entities and collapsing whitespace all move text
     * around. Marking the edges and reading their positions back out afterwards
     * is the only way to keep the span pointing at the same word. They come from
     * the private use area, which real decks do not contain, and they are
     * removed before anything is stored.
     */
    private const val MARK_START = '\uE000'
    private const val MARK_END = '\uE001'

    /**
     * The field a cloze notetype uses for notes rather than for a translation.
     *
     * Anki own cloze notetype names it Extra; newer templates say Back Extra.
     * Whatever is in there is the closest thing the note has to a meaning, so it
     * is the only reason a card with several gaps can still be imported.
     */
    private val EXTRA_FIELDS = listOf("extra", "back extra")

    fun render(
        questionTemplate: String,
        answerTemplate: String,
        fields: Map<String, String>,
        clozeNumber: Int
    ): AnkiRenderedCard {
        val rawHasMedia = fields.values.any { hasMedia(it) } ||
            hasMedia(questionTemplate) || hasMedia(answerTemplate)

        var question = renderSide(
            template = questionTemplate,
            fields = fields,
            clozeNumber = clozeNumber,
            answerSide = false
        )
        var answer = renderSide(
            template = answerTemplate.replace("{{FrontSide}}", ""),
            fields = fields,
            clozeNumber = clozeNumber,
            answerSide = true
        )

        var fallback = false
        val values = fields.values.map(::plain).filter(::usable)
        if (!usable(question)) {
            question = values.firstOrNull().orEmpty()
            fallback = true
        }
        if (!usable(answer)) {
            answer = values.drop(1).firstOrNull()
                ?: values.firstOrNull { it != question }
                ?: ""
            fallback = true
        }

        return AnkiRenderedCard(
            question = question.take(MAX_SIDE_CHARS).trim(),
            answer = answer.take(MAX_SIDE_CHARS).trim(),
            usedFallback = fallback,
            hadMedia = rawHasMedia
        )
    }

    /**
     * Reads a cloze note as a sentence with one span cut out of it.
     *
     * This is what makes an imported cloze card a real chunk instead of a
     * question and an answer that happen to sit next to each other: the gap is
     * already the exact phrase somebody chose to learn, which is the same thing
     * this app own packs mark by hand.
     *
     * Returns null when the note is not a cloze note at all. Otherwise the shape
     * is always reported, even when there is no usable span, because the
     * importer has to be able to refuse a card for a stated reason rather than
     * silently write a mangled one.
     */
    fun readCloze(fields: Map<String, String>, number: Int): ClozeReading? {
        val field = fields.values.firstOrNull { it.contains("{{c") } ?: return null
        val mine = cloze.findAll(field)
            .filter { it.groupValues[1].toIntOrNull() == number }
            .toList()
        if (mine.isEmpty()) return ClozeReading(ClozeShape.ABSENT, null)
        // A deletion inside a deletion defeats the non-greedy pattern: the body
        // comes back cut in half, so the sentence cannot be put back together.
        if (mine.any { it.groupValues[2].contains("{{c") }) {
            return ClozeReading(ClozeShape.NESTED, null)
        }
        if (mine.size > 1) return ClozeReading(ClozeShape.MULTI_GAP, null)

        val marked = cloze.replace(field) { match ->
            val body = match.groupValues[2]
            if (match.groupValues[1].toIntOrNull() == number) {
                MARK_START + body + MARK_END
            } else {
                body
            }
        }
        val located = locate(plain(marked)) ?: return ClozeReading(ClozeShape.ABSENT, null)
        // A deletion wrapped around a picture leaves "[image]", which is made of
        // letters and would otherwise pass for a phrase.
        if (!meaningful(located.context.substring(located.start, located.end))) {
            return ClozeReading(ClozeShape.ABSENT, null)
        }
        return ClozeReading(ClozeShape.SINGLE, located)
    }

    /**
     * The whole sentence with every deletion filled back in.
     *
     * For cards this file refuses to turn into a gap. A refused card has to
     * arrive whole or not at all: showing somebody a sentence with its words
     * already hidden, and nothing on the back to explain them, is worse than not
     * importing it, because it looks like a real card.
     */
    fun filledIn(fields: Map<String, String>): String {
        val field = fields.values.firstOrNull { it.contains("{{c") } ?: return ""
        val filled = plain(cloze.replace(field) { it.groupValues[2] })
        if (!meaningful(filled)) return ""
        return filled.take(MAX_SIDE_CHARS)
    }

    /** Whatever the note Extra field says, as plain text. */
    fun extraMeaning(fields: Map<String, String>): String {
        for ((name, value) in fields) {
            val key = name.trim()
            if (EXTRA_FIELDS.none { key.equals(it, ignoreCase = true) }) continue
            val text = plain(value)
            if (meaningful(text)) return text.take(MAX_SIDE_CHARS)
        }
        return ""
    }

    /**
     * Whether the note is an image occlusion card.
     *
     * Anki builds these on top of cloze: the deletions describe rectangles over
     * a picture. Without the picture there is nothing to answer, so they are
     * refused rather than imported as text about coordinates.
     */
    fun isImageOcclusion(fields: Map<String, String>): Boolean =
        fields.keys.any { it.trim().equals("Occlusion", ignoreCase = true) }

    fun usable(text: String): Boolean = text.any { it.isLetterOrDigit() }

    /** Usable, and not just a placeholder left behind by a stripped picture. */
    private fun meaningful(text: String): Boolean =
        usable(text.replace("[image]", " ").replace("[audio]", " "))

    fun hasMedia(text: String): Boolean =
        text.contains("[sound:", ignoreCase = true) ||
            text.contains("<img", ignoreCase = true)

    /**
     * Takes the marks back out and reports where they were.
     *
     * One pass, because the obvious two-step version -- drop the marks, then
     * tidy the whitespace -- moves the text out from under the offsets it has
     * just measured. Stripping a tag between a word and its deletion can also
     * leave a space stranded inside the span, so that space is absorbed here
     * rather than becoming part of the phrase.
     */
    private fun locate(rendered: String): ClozeTarget? {
        val builder = StringBuilder(rendered.length)
        var start = -1
        var end = -1
        var index = 0
        while (index < rendered.length) {
            when (rendered[index]) {
                MARK_START -> {
                    var next = index + 1
                    while (next < rendered.length && rendered[next] == ' ' &&
                        (builder.isEmpty() || builder.last() == ' ')
                    ) {
                        next++
                    }
                    start = builder.length
                    index = next
                }
                MARK_END -> {
                    while (builder.isNotEmpty() && builder.last() == ' ') {
                        builder.setLength(builder.length - 1)
                    }
                    end = builder.length
                    index++
                }
                else -> {
                    builder.append(rendered[index])
                    index++
                }
            }
        }
        if (start < 0 || end < 0) return null

        var text = builder.toString()
        var shift = 0
        while (text.startsWith(" ")) {
            text = text.substring(1)
            shift++
        }
        text = text.trimEnd()
        val a = (start - shift).coerceIn(0, text.length)
        val b = (end - shift).coerceIn(a, text.length)
        if (b <= a) return null
        if (text.length > MAX_SIDE_CHARS) return null
        return ClozeTarget(context = text, start = a, end = b)
    }

    private fun renderSide(
        template: String,
        fields: Map<String, String>,
        clozeNumber: Int,
        answerSide: Boolean
    ): String {
        var value = template
        for ((name, fieldValue) in fields) {
            val escaped = Regex.escape(name)
            val positive = Regex(
                "\\{\\{#\\s*" + escaped + "\\s*\\}\\}([\\s\\S]*?)" +
                    "\\{\\{/\\s*" + escaped + "\\s*\\}\\}"
            )
            val negative = Regex(
                "\\{\\{\\^\\s*" + escaped + "\\s*\\}\\}([\\s\\S]*?)" +
                    "\\{\\{/\\s*" + escaped + "\\s*\\}\\}"
            )
            value = positive.replace(value) { if (plain(fieldValue).isNotBlank()) it.groupValues[1] else "" }
            value = negative.replace(value) { if (plain(fieldValue).isBlank()) it.groupValues[1] else "" }
        }

        value = moustache.replace(value) { match ->
            val token = match.groupValues[1].trim()
            if (token.equals("FrontSide", ignoreCase = true)) return@replace ""
            if (token.startsWith("type:", ignoreCase = true)) return@replace ""
            val fieldName = token.substringAfterLast(':').trim()
            val raw = fields[fieldName]
                ?: fields.entries.firstOrNull { it.key.equals(fieldName, ignoreCase = true) }?.value
                ?: return@replace ""
            when {
                token.contains("cloze:", ignoreCase = true) ->
                    renderCloze(raw, clozeNumber, answerSide)
                token.startsWith("text:", ignoreCase = true) -> plain(raw)
                else -> raw
            }
        }
        return plain(value)
    }

    private fun renderCloze(raw: String, number: Int, answerSide: Boolean): String =
        cloze.replace(raw) { match ->
            val thisNumber = match.groupValues[1].toIntOrNull() ?: -1
            val answer = match.groupValues[2]
            val hint = match.groupValues.getOrNull(3).orEmpty()
            when {
                answerSide -> answer
                thisNumber == number && hint.isNotBlank() -> "[$hint]"
                thisNumber == number -> "[…]"
                else -> answer
            }
        }

    fun plain(raw: String): String {
        var value = raw
        value = script.replace(value, " ")
        value = comment.replace(value, " ")
        value = imageTag.replace(value, " [image] ")
        value = sound.replace(value, " [audio] ")
        value = breakTag.replace(value, "\n")
        value = blockEnd.replace(value, "\n")
        value = tagBeforePunctuation.replace(value, "")
        value = tag.replace(value, " ")
        value = decodeEntities(value)
        value = moustache.replace(value, "")
        value = whitespace.replace(value, " ")
        value = value.lineSequence().joinToString("\n") { it.trim() }
        value = blankLines.replace(value, "\n\n")
        return value.trim()
    }

    private fun decodeEntities(raw: String): String {
        var value = raw
            .replace("&nbsp;", " ", ignoreCase = true)
            .replace("&amp;", "&", ignoreCase = true)
            .replace("&lt;", "<", ignoreCase = true)
            .replace("&gt;", ">", ignoreCase = true)
            .replace("&quot;", "\"", ignoreCase = true)
            .replace("&#39;", "'", ignoreCase = true)
        value = numericEntity.replace(value) { match ->
            val body = match.groupValues[1]
            val code = if (body.startsWith("x", ignoreCase = true)) {
                body.drop(1).toIntOrNull(16)
            } else {
                body.toIntOrNull()
            }
            code?.takeIf { it in 1..0x10FFFF }
                ?.let { String(Character.toChars(it)) }
                ?: match.value
        }
        return value
    }

    const val MAX_SIDE_CHARS = 2_000
}
