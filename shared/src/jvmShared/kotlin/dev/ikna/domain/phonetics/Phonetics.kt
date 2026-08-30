package dev.ikna.domain.phonetics

/**
 * How one deck writes down the way its phrases sound.
 *
 * Three states rather than a switch, because the two people who want this
 * feature want different things from it. Most readers want to be told how to
 * say the phrase and have no interest in the notation it came from; a much
 * smaller number read IPA fluently and find any respelling a step backwards.
 * A switch would serve one of them and annoy the other.
 */
enum class PhoneticsMode {
    /** No pronunciation line at all. */
    OFF,

    /** IPA rewritten in English spelling: `jeng-KOO-yeh`. See [Respell]. */
    RESPELL,

    /** The stored IPA, untouched: `d\u0361\u0291\u025b\u014b\u02c8kuj\u025b`. */
    IPA;

    /**
     * What goes in the preference string.
     *
     * Written out rather than using [name] or [ordinal]. `ordinal` breaks the
     * moment somebody reorders the constants; `name` breaks the moment somebody
     * renames one. Both would fail by silently changing what an existing
     * preference means, which is the worst way for a stored value to fail.
     */
    val stored: String
        get() = when (this) {
            OFF -> "off"
            RESPELL -> "respell"
            IPA -> "ipa"
        }

    companion object {
        /**
         * What a deck shows when nobody has said otherwise.
         *
         * On, and this is a considered choice rather than an oversight -- the
         * app's other pronunciation feature, speech, is off by default.
         *
         * The two are not the same kind of thing. Speech makes noise, and a
         * phone that starts talking in a quiet room is a reason to put the app
         * down; defaulting it off costs a curious reader one trip to settings
         * and costs an unlucky one nothing at all. A line of text under a phrase
         * cannot embarrass anybody. It also cannot appear where it is not
         * wanted: a deck with no transcription in it, and a language the
         * renderer does not know, both draw nothing regardless of this value.
         *
         * So the default is the answer to "what does somebody who downloaded a
         * Polish deck want to see", and the answer is: how to say it.
         */
        val DEFAULT = RESPELL

        /**
         * Read a stored value. Anything unrecognised gives [DEFAULT].
         *
         * Never throws. This parses a string that may have been written by an
         * older build, edited by hand in an exported backup, or truncated, and
         * none of those is a reason to fail to open a deck.
         */
        fun of(raw: String?): PhoneticsMode = when (raw?.trim()?.lowercase()) {
            OFF.stored -> OFF
            RESPELL.stored -> RESPELL
            IPA.stored -> IPA
            else -> DEFAULT
        }
    }
}

/**
 * The one place the rest of the app asks for a pronunciation line.
 *
 * Everything that decides whether a line appears is gathered here rather than
 * spread across the screen that draws it: the deck's setting, whether the deck
 * has any transcription, and whether the renderer knows the language. The
 * screen asks one question and gets one answer, and all three ways of having
 * nothing to show arrive as the same `null`.
 */
object Phonetics {

    /**
     * Languages the catalogue pipeline transcribes and the renderer handles.
     *
     * The same eight the catalogue can be learned in. Chinese and Japanese are
     * absent on purpose: both are meaning languages in this app rather than
     * learnable ones, so nothing would ever be transcribed in them, and both
     * would need work this renderer does not do -- tone for one, and a
     * pronunciation that cannot be read off the writing for the other.
     */
    val SUPPORTED: Set<String> = setOf("en", "ru", "pl", "es", "fr", "de", "it", "pt")

    /**
     * How many rendered strings are kept.
     *
     * A session is a few hundred cards, and each card is drawn many times over
     * -- every frame of a swipe, every recomposition. Rendering is cheap but it
     * is not free, and it happens on the frame thread.
     */
    const val CACHE_LIMIT = 256

    private val cache = HashMap<String, String>(128)

    /**
     * The line to draw under a phrase, or null when there is nothing to draw.
     *
     * Null rather than an empty string, deliberately. The caller uses it to
     * decide whether the line exists at all, and an empty string that still
     * reserves height leaves a gap under the phrase that reads as a layout
     * fault on every deck in the world that has no transcription.
     */
    fun line(ipa: String?, lang: String, mode: PhoneticsMode): String? {
        if (mode == PhoneticsMode.OFF) return null
        val raw = ipa?.trim().orEmpty()
        if (raw.isEmpty()) return null
        if (lang !in SUPPORTED) return null
        return when (mode) {
            PhoneticsMode.OFF -> null
            PhoneticsMode.IPA -> raw
            PhoneticsMode.RESPELL -> respell(raw)
        }
    }

    /**
     * [Respell.render] with a cache in front of it.
     *
     * Null when the renderer had nothing to say -- input made entirely of
     * symbols it does not know. That empty result is cached too, so a phrase
     * that cannot be rendered is not re-attempted on every frame.
     */
    fun respell(ipa: String): String? {
        val key = ipa.trim()
        if (key.isEmpty()) return null

        synchronized(cache) {
            val hit = cache[key]
            if (hit != null) return hit.ifEmpty { null }
        }

        val value = Respell.render(key)

        synchronized(cache) {
            // Emptied rather than evicted one entry at a time. A least-recently
            // used list would be the textbook answer and it is not worth its
            // own bug surface here: the cost of being wrong is re-rendering the
            // handful of cards currently on screen, and reaching this limit at
            // all takes a session longer than anybody has.
            if (cache.size >= CACHE_LIMIT) cache.clear()
            cache[key] = value
        }

        return value.ifEmpty { null }
    }

    /**
     * One familiar word per language, with its IPA, for showing what a setting
     * does before anybody commits to it.
     *
     * Written out here rather than taken from the deck, for two reasons. A deck
     * is read on a background thread and this line is drawn while the screen is
     * being laid out; and the first card of a deck is whatever the frequency
     * list put there, which may well be `the` -- a word whose transcription
     * teaches the reader nothing about the choice in front of them. A greeting
     * everybody recognises shows the difference between the two notations
     * immediately.
     *
     * The rendering still goes through [line], so the sample cannot drift out
     * of agreement with the card: it is the same code, not a description of it.
     */
    val SAMPLES: Map<String, Pair<String, String>> = mapOf(
        "en" to ("thank you" to "\u02C8\u03B8\u00E6\u014Bk ju\u02D0"),
        "ru" to ("\u0441\u043F\u0430\u0441\u0438\u0431\u043E" to "sp\u0250\u02C8s\u02B2ib\u0259"),
        "pl" to ("dzi\u0119kuj\u0119" to "d\u0361\u0291\u025B\u014B\u02C8kuj\u025B"),
        "es" to ("gracias" to "\u02C8\u0261\u027Easjas"),
        "fr" to ("bonjour" to "b\u0254\u0303\u02C8\u0292u\u0281"),
        "de" to ("danke" to "\u02C8da\u014Bk\u0259"),
        "it" to ("grazie" to "\u02C8\u0261rattsje"),
        "pt" to ("obrigado" to "ob\u027Ei\u02C8\u0261adu")
    )

    /**
     * The sample for one language in one mode, as `word \u00b7 sound`.
     *
     * Null when there is nothing to show: a language with no sample, or the
     * mode that shows nothing. The caller draws its own words in that case,
     * rather than being handed an empty string it has to test.
     */
    fun sample(lang: String, mode: PhoneticsMode): String? {
        val pair = SAMPLES[lang] ?: return null
        val rendered = line(pair.second, lang, mode) ?: return null
        return pair.first + "  \u00B7  " + rendered
    }

    /** Drops everything remembered. For tests, and for a language change. */
    fun clearCache() {
        synchronized(cache) { cache.clear() }
    }
}
