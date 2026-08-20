package dev.ikna.data.anki

/** A rendered Anki card after templates and cloze markers have been resolved. */
data class AnkiRenderedCard(
    val question: String,
    val answer: String,
    val usedFallback: Boolean,
    val hadMedia: Boolean
)

/**
 * Small, deterministic subset of Anki's template language.
 *
 * Ikna stores a question and an answer, not executable HTML. Field replacement,
 * conditional sections, text filters and cloze deletions are kept; CSS,
 * JavaScript and media tags are deliberately reduced to readable text. This is
 * safer than running code from an imported package and makes unsupported cards
 * detectable before anything is written to the database.
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

    fun usable(text: String): Boolean = text.any { it.isLetterOrDigit() }

    fun hasMedia(text: String): Boolean =
        text.contains("[sound:", ignoreCase = true) ||
            text.contains("<img", ignoreCase = true)

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
