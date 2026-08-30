package dev.ikna.data.update

/*
 * Everything about an update that can be decided without a network.
 *
 * The check itself is four lines of HTTP in UpdateCheck; the parts that can be
 * wrong in a way the user would notice are here, where a JVM test can reach
 * them. Comparing versions, choosing which of the two APKs belongs on this
 * phone and turning release notes into something readable are all pure
 * functions of their input.
 */

/** One file attached to a release. */
data class UpdateAsset(
    val name: String,
    val url: String,
    val sizeBytes: Long
)

/** A release that is newer than the one running. */
data class UpdateRelease(
    /** The version as the tag names it, e.g. "0.5.0 press". */
    val version: String,
    val tag: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long
)

/**
 * The three numbers in a tag or a version name, or null if there are not three.
 *
 * Both shapes have to parse: the tag is "v0.5.0-press" and the installed version
 * calls itself "0.5.0 press". The word is not part of the comparison -- it names
 * the epoch, and an epoch never appears twice on the same phone.
 */
fun versionNumbers(text: String): List<Int>? {
    val digits = StringBuilder()
    for (c in text) {
        if (c.isDigit() || c == '.') digits.append(c) else if (digits.isNotEmpty()) break
    }
    val parts = digits.toString().split('.').filter { it.isNotEmpty() }
    if (parts.size < 2 || parts.size > 4) return null
    val numbers = parts.mapNotNull { it.toIntOrNull() }
    if (numbers.size != parts.size) return null
    return numbers
}

/**
 * The word after the numbers, lowercased, or empty if there is none.
 *
 * "0.4.0 press" and "v0.4.0-press" both give "press". A leading "v" is not it:
 * only letters that come after the first digit count, and the first thing that
 * is neither a letter nor a separator ends the word.
 */
fun epochWord(text: String): String {
    val letters = StringBuilder()
    var seenDigit = false
    for (c in text) {
        if (c.isDigit()) {
            seenDigit = true
            continue
        }
        if (!seenDigit) continue
        if (c.isLetter()) letters.append(c.lowercaseChar())
        else if (letters.isNotEmpty()) break
    }
    return letters.toString()
}

/**
 * Whether [found] is a later version than [installed].
 *
 * Unreadable on either side means no: an app that cannot tell must not be able
 * to nag. Equal means no, and older means no -- a release pulled back to fix
 * something must not offer itself as an upgrade to the build that replaced it.
 *
 * Two epochs share the page and their numbers restarted, so the word has to
 * agree before the numbers are read at all: 0.5.0 proof is a larger number than
 * 0.4.0 press and is not an update to it. Where either side has no word, the
 * numbers decide on their own.
 */
fun isNewer(installed: String, found: String): Boolean {
    val here = versionNumbers(installed) ?: return false
    val there = versionNumbers(found) ?: return false
    val hereWord = epochWord(installed)
    val thereWord = epochWord(found)
    if (hereWord.isNotEmpty() && thereWord.isNotEmpty() && hereWord != thereWord) return false
    for (i in 0 until maxOf(here.size, there.size)) {
        val h = here.getOrElse(i) { 0 }
        val t = there.getOrElse(i) { 0 }
        if (t != h) return t > h
    }
    return false
}

/**
 * Which of the release's files this phone can run.
 *
 * A release carries two APKs, and handing a 64-bit-only build to a 32-bit
 * phone produces an install that fails at the first sentence it tries to
 * speak: the speech runtime is native code. A phone with no 64-bit ABI gets the
 * file whose name says legacy32 (the name produced by release.yml), everything
 * else gets the ordinary APK. "32bit" is accepted as well for older releases.
 *
 * This deliberately matches the workflow's real asset names rather than their
 * order in GitHub's API. The API once returned legacy32 first; because the old
 * check only recognised "32bit", both files looked ordinary and a modern phone
 * was handed the compatibility build.
 */
fun pickAsset(assets: List<UpdateAsset>, has64Bit: Boolean): UpdateAsset? {
    val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
    if (apks.isEmpty()) return null
    val legacy32 = apks.filter { asset ->
        asset.name.contains("legacy32", ignoreCase = true) ||
            asset.name.contains("32bit", ignoreCase = true)
    }
    val regular = apks.filterNot { it in legacy32 }
    return if (has64Bit) regular.firstOrNull() ?: legacy32.firstOrNull()
    else legacy32.firstOrNull() ?: regular.firstOrNull()
}

/** Megabytes with one decimal, as text, so the caller can name the unit. */
fun megabytes(bytes: Long): String {
    if (bytes <= 0L) return "?"
    val tenths = (bytes * 10 + 524_288) / 1_048_576
    return (tenths / 10).toString() + "." + (tenths % 10).toString()
}

/**
 * Release notes as prose.
 *
 * What the API returns is the markdown of the release page: a centred block of
 * HTML with two download badges in it, headings, bold runs, links written as
 * [text](url) and a rule between the badges and the text. Shown raw in a dialog
 * that is a plain rectangle it reads as a broken page, and the first thing the
 * reader sees is an image tag.
 *
 * So: HTML dropped entirely, rules dropped, heading and list marks dropped, a
 * link left as its text, and the whole thing cut to [maxChars] on a line break
 * rather than mid-word. This is presentation, not parsing -- the full notes are
 * one tap away on the release page.
 */
fun tidyNotes(body: String, maxChars: Int = NOTES_CHARS): String {
    val out = StringBuilder()
    var inHtml = false
    for (raw in body.lineSequence()) {
        var line = raw.trim()
        if (inHtml) {
            if (line.contains('>')) inHtml = false
            continue
        }
        if (line.startsWith("<")) {
            if (!line.contains('>')) inHtml = true
            continue
        }
        if (line.isEmpty() || line.all { it == '-' || it == '=' } && line.length >= 3) {
            if (out.isNotEmpty() && !out.endsWith("\n\n")) out.append('\n')
            continue
        }
        line = line.trimStart('#', '>', ' ')
        if (line.startsWith("* ")) line = "\u2014 " + line.removePrefix("* ")
        if (line.startsWith("- ")) line = "\u2014 " + line.removePrefix("- ")
        line = unlink(line)
        line = line.replace("**", "").replace("`", "")
        if (line.isEmpty()) continue
        out.append(line).append('\n')
        if (out.length >= maxChars) break
    }
    val text = out.toString().trim()
    if (text.length <= maxChars) return text
    val cut = text.take(maxChars)
    val end = cut.lastIndexOf('\n')
    return (if (end > maxChars / 2) cut.take(end) else cut).trimEnd() + "\u2026"
}

/** [text](url) becomes text. An image link becomes nothing. */
private fun unlink(line: String): String {
    val out = StringBuilder()
    var i = 0
    while (i < line.length) {
        val open = line.indexOf('[', i)
        if (open < 0) {
            out.append(line.substring(i))
            break
        }
        val close = line.indexOf(']', open)
        if (close < 0 || close + 1 >= line.length || line[close + 1] != '(') {
            out.append(line.substring(i, open + 1))
            i = open + 1
            continue
        }
        val tail = line.indexOf(')', close)
        if (tail < 0) {
            out.append(line.substring(i))
            break
        }
        val image = open > 0 && line[open - 1] == '!'
        out.append(line.substring(i, if (image) open - 1 else open))
        if (!image) out.append(line.substring(open + 1, close))
        i = tail + 1
    }
    return out.toString().trim()
}

/** Enough for the sections of a release, not enough to be a second page. */
const val NOTES_CHARS: Int = 1_400
