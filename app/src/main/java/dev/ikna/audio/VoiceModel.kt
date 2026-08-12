package dev.ikna.audio

import java.util.Locale

/*
 * A speech model the user brought, described without touching Android.
 *
 * The app ships no weights. Someone downloads a folder from the sherpa-onnx
 * release page -- Kokoro, or a Piper voice -- and points the app at it. Before a
 * single byte is copied, this file decides three things from the folder listing
 * alone: whether it is a model at all, which of the two shapes it has, and which
 * language it speaks. Getting that wrong is the difference between "added" and
 * twenty seconds of copying followed by silence.
 *
 * It is deliberately free of Android and of the speech runtime, so the rules can
 * be read and tested as what they are -- string handling -- and so the plain
 * build can display them without linking anything.
 */

/** One entry of the folder the user picked. */
data class VoiceEntry(
    val name: String,
    val isDirectory: Boolean,
    val bytes: Long = 0L,
)

/**
 * The two folder shapes worth supporting.
 *
 * KOKORO carries one big net and a separate file of voices addressed by number.
 * VITS is one net per voice, which is what every Piper download is.
 */
enum class VoiceModelKind { KOKORO, VITS }

/** Why a folder cannot be used, in words the screen can turn into a sentence. */
enum class VoiceModelProblem {
    /** No weights anywhere: a screenshot folder, a download folder, the wrong pick. */
    NOT_A_MODEL,

    /** The model is one level down -- the user picked the folder that contains it. */
    NESTED,

    /** Several nets and none of them named model.onnx: which one is anybody's guess. */
    TOO_MANY_MODELS,

    /** tokens.txt missing. Raw Piper downloads have this instead of the sherpa build. */
    NO_TOKENS,

    /** A Kokoro folder without voices.bin, which means the download stopped early. */
    NO_VOICES,

    /** Nothing to turn letters into sounds: no espeak-ng-data, no lexicon. */
    NO_PHONEMES,
}

/**
 * What a folder turned out to be. [bytes] counts the top-level files only --
 * espeak-ng-data holds thousands of small ones and is not walked for a preview.
 */
data class VoiceModelReport(
    val kind: VoiceModelKind? = null,
    val problem: VoiceModelProblem? = null,
    val model: String? = null,
    val lang: String? = null,
    val bytes: Long = 0L,
    val quantised: Boolean = false,
) {
    val usable: Boolean get() = kind != null && problem == null && model != null
}

object VoiceModelLayout {

    /**
     * Reads a folder listing and says what it is.
     *
     * [folderName] matters as much as the files: it is where the language
     * usually hides (`vits-piper-ru_RU-dmitri-medium`), and the files inside are
     * often named nothing but `model.onnx`.
     */
    fun inspect(folderName: String, entries: List<VoiceEntry>): VoiceModelReport {
        val files = entries.filterNot { it.isDirectory }
        val dirs = entries.filter { it.isDirectory }.map { it.name.lowercase(Locale.ROOT) }
        val names = files.map { it.name }
        val bytes = files.sumOf { it.bytes }

        val weights = names.filter { it.lowercase(Locale.ROOT).endsWith(".onnx") }

        if (weights.isEmpty()) {
            // One subfolder and nothing else is the commonest mistake by far: the
            // archive was unpacked into its own folder and that is what got
            // picked. Worth its own message, because the fix is one tap.
            val nested = dirs.size == 1 &&
                names.none { it.lowercase(Locale.ROOT).endsWith(".bin") }
            return VoiceModelReport(
                problem = if (nested) VoiceModelProblem.NESTED else VoiceModelProblem.NOT_A_MODEL,
                bytes = bytes,
            )
        }

        val model = pick(weights)
            ?: return VoiceModelReport(problem = VoiceModelProblem.TOO_MANY_MODELS, bytes = bytes)

        val hasVoices = names.any { it.equals(VOICES, ignoreCase = true) }
        val callsItselfKokoro = folderName.lowercase(Locale.ROOT).contains("kokoro")
        val kind = if (hasVoices) VoiceModelKind.KOKORO else VoiceModelKind.VITS

        val hasTokens = names.any { it.equals(TOKENS, ignoreCase = true) }
        val hasPhonemes = dirs.contains(DATA_DIR) || names.any {
            val low = it.lowercase(Locale.ROOT)
            low.startsWith("lexicon") && low.endsWith(".txt")
        }

        val problem = when {
            callsItselfKokoro && !hasVoices -> VoiceModelProblem.NO_VOICES
            !hasTokens -> VoiceModelProblem.NO_TOKENS
            !hasPhonemes -> VoiceModelProblem.NO_PHONEMES
            else -> null
        }

        return VoiceModelReport(
            kind = if (problem == null) kind else null,
            problem = problem,
            model = model.takeIf { problem == null },
            lang = languageOf(folderName, model),
            bytes = bytes,
            quantised = model.lowercase(Locale.ROOT).contains("int8") ||
                folderName.lowercase(Locale.ROOT).contains("int8"),
        )
    }

    /**
     * Which net to load.
     *
     * A sherpa release folder can hold the same voice three times over at
     * different precisions. The quantised one is chosen on purpose: it is a
     * quarter of the size, and on a phone that is the difference between a model
     * that loads and one that is killed for memory.
     */
    fun pick(weights: List<String>): String? {
        for (preferred in PREFERRED) {
            val hit = weights.firstOrNull { it.equals(preferred, ignoreCase = true) }
            if (hit != null) return hit
        }
        return weights.singleOrNull()
    }

    /**
     * The language a name admits to, or null when it does not.
     *
     * Only a two-letter code standing on its own counts, so `int8` and `v0_19`
     * are not mistaken for languages, and a multi-language release -- which names
     * no language at all -- comes back null and is asked about instead of guessed
     * at.
     */
    fun languageOf(vararg names: String?): String? {
        for (name in names) {
            val text = name?.lowercase(Locale.ROOT)?.replace('.', '-') ?: continue
            val hit = TAG.find(text)?.groupValues?.getOrNull(1) ?: continue
            if (hit !in NOISE) return hit
        }
        return null
    }

    const val VOICES = "voices.bin"
    const val TOKENS = "tokens.txt"
    const val DATA_DIR = "espeak-ng-data"
    const val DICT_DIR = "dict"

    private val PREFERRED = listOf("model.int8.onnx", "model.onnx", "model.fp16.onnx")

    private val TAG = Regex("(?:^|[-_])([a-z]{2})(?:_[a-z]{2})?(?=[-_]|${'$'})")

    /** Two-letter fragments that turn up in model names and are not languages. */
    private val NOISE = setOf("v0", "v1", "v2", "hf", "ml", "nn", "tt")
}
