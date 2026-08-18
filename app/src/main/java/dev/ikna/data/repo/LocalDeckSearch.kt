package dev.ikna.data.repo

data class LocalSearchTerms(
    val text: String,
    val contains: String,
    val prefix: String
)

/**
 * Escapes LIKE metacharacters instead of allowing a typed `%` to mean every
 * card. Two visible characters is the smallest useful search and prevents a
 * one-letter query from scanning a large collection for almost no information.
 */
fun localSearchTerms(raw: String): LocalSearchTerms? {
    val text = raw.trim().replace(Regex("\\s+"), " ").take(80)
    if (text.length < 2) return null
    val escaped = buildString(text.length) {
        for (char in text) {
            if (char == '\\' || char == '%' || char == '_') append('\\')
            append(char)
        }
    }
    return LocalSearchTerms(text, "%$escaped%", "$escaped%")
}
