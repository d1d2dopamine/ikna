package dev.ikna.data.repo

/**
 * One typed query, in the three shapes the two search paths need.
 *
 * [contains] and [prefix] are LIKE patterns for the scan; [match] is the same
 * words as an FTS query. It is derived rather than stored so that the three can
 * never describe different searches.
 */
data class LocalSearchTerms(
    val text: String,
    val contains: String,
    val prefix: String
) {
    /**
     * The typed words as an FTS4 `MATCH` query: every word a prefix term, all of
     * them required.
     *
     * Prefix terms are what make search feel like search while typing -- "used"
     * has to find "used to" before the space is typed. Requiring every word
     * (FTS4's implicit AND) keeps a two-word query from returning everything
     * containing either half.
     */
    val match: String get() = ftsMatch(text)
}

/** Letters and digits in any script; everything else is a separator. */
private val WORD = Regex("[\\p{L}\\p{N}]+")

/**
 * Builds the `MATCH` query, dropping anything that is not a word.
 *
 * This is also the escaping: FTS4 query syntax has operators of its own -- `"`,
 * `*`, `-`, `NEAR`, `(` -- and a typed quote or dash would otherwise be a syntax
 * error rather than a search. Keeping only word characters means nothing typed
 * into the box can be read as an operator.
 *
 * Empty when the query has no words at all, which the caller treats as "the
 * index cannot answer this" and sends to the scan.
 */
internal fun ftsMatch(text: String): String =
    WORD.findAll(text).joinToString(" ") { it.value + "*" }

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
