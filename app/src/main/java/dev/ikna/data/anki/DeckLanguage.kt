package dev.ikna.data.anki

import dev.ikna.data.repo.NO_LANG

/** One card as language detection needs it: what is asked, and what it means. */
internal data class LanguageSample(val text: String, val meaning: String)

/**
 * What language a deck is in, decided from the deck instead of from a question.
 *
 * This used to be eleven chips on the import screen, asked before the file had
 * been opened, about a file whose contents nobody remembers card by card. One
 * wrong tap and a German deck was Polish for good, silently, in every session
 * after it. The cards already say what they are in.
 *
 * The rules, in order:
 *
 *  1. If the deck name names a language, that is the language: somebody typed
 *     that name on purpose. Exam names count too -- JLPT, HSK, IELTS, TestDaF.
 *  2. Otherwise the cards decide. A non-Latin script answers by itself. Latin
 *     script is decided by accented letters and by the short words a language
 *     cannot write around.
 *  3. A deck whose cards and whose meanings are in the same language, and that
 *     language is the one the app is read in, is a subject and not a language:
 *     chemistry, anatomy, case law. That is [NO_LANG], which keeps a voice from
 *     reading definitions aloud in a guessed accent and keeps the ladder from
 *     demanding a definition back word for word.
 *  4. Cards this cannot name, with meanings in the reader's own language, are
 *     still a language deck -- an unnamed one, [UNDECIDED]. The deck's own page
 *     has the language chips, so naming it is one tap where the deck already is.
 *  5. When nothing can be told apart, [NO_LANG]. It is the smaller mistake: it
 *     withholds one step of the ladder, while a wrong language puts a voice and
 *     the wrong alphabet behind every card in the deck.
 */
internal object DeckLanguage {

    /** A deck plainly in some language, when the language cannot be named. */
    const val UNDECIDED = "und"

    /** Enough cards to tell a language from. More is slower and no wiser. */
    const val MAX_SAMPLES = 300

    private const val MIN_LETTERS = 24
    private const val MIN_SCORE = 4

    fun of(deckName: String, samples: List<LanguageSample>, appLanguage: String): String {
        // A name is a statement, not a guess, so it outranks the subject test:
        // a deck called "English idioms" read in English is still English.
        val named = named(deckName)
        if (named != null) return named

        val use = if (samples.size > MAX_SAMPLES) samples.subList(0, MAX_SAMPLES) else samples
        val cards = use.joinToString("\n") { it.text }
        val meanings = use.joinToString("\n") { it.meaning }
        val app = base(appLanguage)

        val cardLanguage = language(cards)
        val meaningLanguage = language(meanings)

        if (cardLanguage != null) {
            val translated = meaningLanguage != null && meaningLanguage != cardLanguage
            return if (!translated && cardLanguage == app) NO_LANG else cardLanguage
        }
        if (meaningLanguage == app && cards.any { it.isLetter() }) return UNDECIDED
        return NO_LANG
    }

    internal fun language(text: String): String? = script(text) ?: latin(text)

    /** The language a deck name says it is in, or null. */
    internal fun named(deckName: String): String? {
        val lower = deckName.lowercase()
        var earliest = Int.MAX_VALUE
        var code: String? = null
        for ((hint, language) in NAMES) {
            val at = lower.indexOf(hint)
            if (at in 0 until earliest) {
                earliest = at
                code = language
            }
        }
        return code
    }

    /** The language of a writing system, when the writing system is enough. */
    internal fun script(text: String): String? {
        var latin = 0
        var cyrillic = 0
        var kana = 0
        var han = 0
        var hangul = 0
        var greek = 0
        var arabic = 0
        var hebrew = 0
        var devanagari = 0
        var thai = 0
        var georgian = 0
        var armenian = 0
        for (ch in text) {
            when (ch.code) {
                in 0x0041..0x005A, in 0x0061..0x007A, in 0x00C0..0x024F -> latin++
                in 0x0400..0x04FF -> cyrillic++
                in 0x3040..0x30FF, in 0xFF66..0xFF9D -> kana++
                in 0x3400..0x4DBF, in 0x4E00..0x9FFF -> han++
                in 0x1100..0x11FF, in 0x3130..0x318F, in 0xAC00..0xD7AF -> hangul++
                in 0x0370..0x03FF, in 0x1F00..0x1FFF -> greek++
                in 0x0600..0x06FF, in 0x0750..0x077F -> arabic++
                in 0x0590..0x05FF -> hebrew++
                in 0x0900..0x097F -> devanagari++
                in 0x0E00..0x0E7F -> thai++
                in 0x10A0..0x10FF -> georgian++
                in 0x0530..0x058F -> armenian++
            }
        }
        // Japanese is written in three scripts at once and the kanji in a
        // sentence usually outnumber the kana. Any kana beside Han means
        // Japanese rather than Chinese.
        if (kana > 0 && kana * 20 >= han) return "ja"
        val counted = listOf(
            "ko" to hangul, "zh" to han, "ru" to cyrillic, "el" to greek,
            "ar" to arabic, "he" to hebrew, "hi" to devanagari, "th" to thai,
            "ka" to georgian, "hy" to armenian
        )
        val best = counted.maxByOrNull { it.second } ?: return null
        if (best.second == 0 || best.second <= latin) return null
        if (best.first == "ru") return cyrillicLanguage(text)
        return best.first
    }

    /** Which Cyrillic language: a few letters separate them. */
    private fun cyrillicLanguage(text: String): String = when {
        text.any { it == 'ў' || it == 'Ў' } -> "be"
        text.any { it in "іїєґІЇЄҐ" } -> "uk"
        else -> "ru"
    }

    /**
     * Which Latin-alphabet language, by accents and by function words.
     *
     * A deck of bare words -- "Hund", "Katze", "Haus" -- carries neither, and
     * this returns null rather than a guess. Null is not a failure here: the
     * caller still has the meanings and the deck name to go on.
     */
    internal fun latin(text: String): String? {
        val lower = text.lowercase()
        if (lower.count { it.isLetter() } < MIN_LETTERS) return null
        val words = WORD.findAll(lower).map { it.value }.toList()
        if (words.isEmpty()) return null

        var best: Profile? = null
        var bestScore = 0
        var runnerUp = 0
        for (profile in PROFILES) {
            var score = 0
            for (ch in lower) if (ch in profile.letters) score += 3
            for (word in words) if (word in profile.words) score += 2
            when {
                score > bestScore -> {
                    runnerUp = bestScore
                    bestScore = score
                    best = profile
                }
                score > runnerUp -> runnerUp = score
            }
        }
        val winner = best ?: return null
        if (bestScore < MIN_SCORE || bestScore == runnerUp) return null
        return winner.code
    }

    private fun base(code: String): String {
        val trimmed = code.trim().lowercase()
        val cut = trimmed.indexOfFirst { it == '-' || it == '_' }
        return if (cut > 0) trimmed.substring(0, cut) else trimmed
    }

    private class Profile(val code: String, val letters: String, val words: Set<String>)

    private val WORD = Regex("[\\p{L}]+")

    private val PROFILES = listOf(
        Profile(
            "en", "",
            setOf("the", "and", "of", "to", "is", "you", "it", "that", "with", "was", "this", "are")
        ),
        Profile(
            "de", "äöüß",
            setOf("der", "die", "das", "und", "nicht", "ich", "ein", "eine", "mit", "ist", "sich")
        ),
        Profile(
            "fr", "éèêàçùûôœî",
            setOf("les", "des", "une", "est", "pas", "vous", "avec", "dans", "pour", "elle")
        ),
        Profile(
            "es", "ñáíóú¿¡",
            setOf("los", "las", "una", "que", "con", "para", "muy", "pero", "este", "esta")
        ),
        Profile(
            "pl", "ąćęłńśźż",
            setOf("nie", "jest", "się", "tak", "oraz", "tego", "jako", "która", "jestem")
        ),
        Profile(
            "it", "àìòù",
            setOf("che", "non", "per", "sono", "gli", "della", "come", "anche", "questo")
        ),
        Profile(
            "pt", "ãõçêú",
            setOf("não", "uma", "com", "para", "você", "mais", "muito", "isso", "está")
        ),
        Profile("nl", "ëï", setOf("het", "een", "niet", "van", "zijn", "voor", "maar", "ook")),
        Profile("sv", "åäö", setOf("och", "att", "det", "inte", "som", "med", "för", "jag")),
        Profile("tr", "ışğçöü", setOf("bir", "için", "değil", "çok", "daha", "olarak", "ile")),
        Profile("cs", "čřšžěů", setOf("není", "jsem", "jako", "které", "nebo", "také"))
    )

    private val NAMES = listOf(
        "english" to "en", "ielts" to "en", "toefl" to "en", "английск" to "en",
        "angielski" to "en", "inglés" to "en", "ingles" to "en", "anglais" to "en",
        "englisch" to "en",
        "deutsch" to "de", "german" to "de", "немецк" to "de", "niemieck" to "de",
        "alemán" to "de", "aleman" to "de", "allemand" to "de", "testdaf" to "de",
        "french" to "fr", "français" to "fr", "francais" to "fr", "французск" to "fr",
        "francuski" to "fr", "francés" to "fr", "franzö" to "fr", "delf" to "fr",
        "spanish" to "es", "español" to "es", "espanol" to "es", "испанск" to "es",
        "hiszpań" to "es", "espagnol" to "es", "spanisch" to "es",
        "polish" to "pl", "polski" to "pl", "польск" to "pl", "polaco" to "pl",
        "polonais" to "pl", "polnisch" to "pl",
        "russian" to "ru", "русск" to "ru", "rosyjski" to "ru", "ruso" to "ru",
        "russe" to "ru", "russisch" to "ru",
        "italian" to "it", "italiano" to "it", "итальянск" to "it", "włoski" to "it",
        "italien" to "it",
        "portug" to "pt", "португальск" to "pt", "portugalski" to "pt",
        "japanese" to "ja", "日本語" to "ja", "японск" to "ja", "japoński" to "ja",
        "japanisch" to "ja", "nihongo" to "ja", "jlpt" to "ja",
        "chinese" to "zh", "中文" to "zh", "汉语" to "zh", "mandarin" to "zh",
        "китайск" to "zh", "chiński" to "zh", "chino" to "zh", "chinois" to "zh",
        "hsk" to "zh",
        "korean" to "ko", "한국" to "ko", "корейск" to "ko", "coreano" to "ko",
        "coréen" to "ko", "koreanisch" to "ko", "topik" to "ko",
        "ukrainian" to "uk", "українськ" to "uk", "украинск" to "uk",
        "greek" to "el", "греческ" to "el", "ελλην" to "el",
        "arabic" to "ar", "арабск" to "ar", "العربية" to "ar",
        "hebrew" to "he", "иврит" to "he", "עברית" to "he",
        "turkish" to "tr", "türkçe" to "tr", "турецк" to "tr"
    )
}
