package dev.ikna.audio

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/*
 * Kokoro-82M, running on the phone, through sherpa-onnx.
 *
 * This file only exists in the voice flavour. Nothing in the plain build
 * imports it, links the runtime, or carries a byte of the model.
 *
 * Three things are worth knowing before changing anything here.
 *
 * The model is not read from assets. It is copied out of the APK into the app's
 * own storage the first time speech is used, and loaded from there. That is not
 * a preference: Kokoro needs espeak-ng's data directory to turn letters into
 * phonemes, espeak opens those files by path through libc, and libc cannot see
 * inside an APK. Since one of the four inputs has to be on disk anyway, all of
 * them are, which keeps the configuration honest -- absolute paths, no asset
 * manager, the same code path the upstream sample uses.
 *
 * The copy costs storage. The model is the biggest thing in the app by two
 * orders of magnitude, so unpacking it means the install takes roughly twice
 * its size until the phone reclaims the APK's copy, which it never does. That
 * is the price of the voice build and the reason there is a plain one.
 *
 * Failure is never fatal. A model that is absent, truncated, or built against a
 * different runtime version makes every method here return false or null, and
 * Speaker goes on to the platform engine as though this class had never been
 * compiled in. The user hears a different voice, not silence, and never sees an
 * error.
 */
class KokoroSpeech(context: Context) : NeuralSpeech {

    private val app = context.applicationContext
    private val lock = Mutex()

    private var engine: OfflineTts? = null
    private var attempted = false

    /** Speaker id per language, read from the model directory when present. */
    private var speakers: Map<String, Int> = emptyMap()

    /*
     * The give-up switch.
     *
     * A model can be present, correct and still too slow for the phone it
     * landed on -- and a synthesiser that takes eight seconds per card is not a
     * feature, it is a session where every card is silent and the battery is
     * warm. So renders are timed, and after a few slow ones in a row this stops
     * offering itself for the rest of the run. Speaker then uses the platform
     * engine, which is instant, and the user gets a plainer voice instead of a
     * broken app.
     *
     * Volatile and not synchronised on purpose: two threads racing to give up
     * at the same moment reach the same answer.
     */
    @Volatile
    private var slowRenders = 0

    @Volatile
    private var givenUp = false

    override val id: String = MODEL_ID

    /**
     * What this release is documented to speak, and nothing beyond it.
     *
     * Kokoro's weights were trained on more languages than this, but the
     * sherpa-onnx packaging ships pronunciation dictionaries for English and
     * Chinese and is described in terms of those two. Claiming the rest on the
     * strength of the weights alone would not produce bad audio -- it would
     * produce confident nonsense, an English mouth reading foreign letters,
     * which is worse than the platform voice this would have replaced.
     *
     * Russian and Polish are absent either way. The model does not have them.
     */
    override fun supports(lang: String): Boolean = languageOf(lang) in SUPPORTED

    override suspend fun isReady(): Boolean = !givenUp && engine() != null

    override suspend fun render(
        text: String,
        lang: String,
        speed: Float,
        target: File
    ): Boolean {
        if (text.isBlank() || givenUp || !supports(lang)) return false
        val tts = engine() ?: return false

        return withContext(Dispatchers.IO) {
            val startedAt = System.nanoTime()
            val done = runCatching {
                target.parentFile?.mkdirs()
                val tmp = File(target.parentFile, target.name + ".part")
                tmp.delete()

                // The whole phrase at once, into a file, and only then is it
                // played. Nothing is streamed to the speaker while it is still
                // being computed, which is the difference between this and the
                // browser demos: audio that arrives late cannot underrun a
                // playing buffer if nothing is playing yet.
                val audio = tts.generate(
                    text = text,
                    sid = speakers[languageOf(lang)] ?: DEFAULT_SPEAKER,
                    speed = speed.coerceIn(MIN_SPEED, MAX_SPEED)
                )

                val saved = audio.save(tmp.absolutePath)
                if (saved && tmp.length() > 0 && tmp.renameTo(target)) {
                    true
                } else {
                    tmp.delete()
                    false
                }
            }.getOrDefault(false)

            noteDuration(System.nanoTime() - startedAt)
            done
        }
    }

    /**
     * Counts slow renders, and stops the model after a few in a row.
     *
     * In a row, not in total: one slow card is a phone that was busy doing
     * something else, three in a row is a phone that cannot do this at all. The
     * model is released when that happens, because the memory it holds is worth
     * more to the rest of the app than a voice nobody is waiting for any more.
     */
    private fun noteDuration(nanos: Long) {
        val ms = nanos / 1_000_000
        if (ms < SLOW_MS) {
            slowRenders = 0
            return
        }
        slowRenders += 1
        if (slowRenders >= MAX_SLOW_RENDERS) {
            givenUp = true
            shutdown()
        }
    }

    override fun shutdown() {
        runCatching { engine?.release() }
        engine = null
    }

    // ---- internals ---------------------------------------------------------

    /**
     * Loads the model once, and remembers a failure as firmly as a success:
     * retrying a load that takes seconds and cannot work is how a session ends
     * up stuttering on every single card.
     */
    private suspend fun engine(): OfflineTts? = lock.withLock {
        if (attempted) return@withLock engine
        attempted = true
        engine = runCatching { withContext(Dispatchers.IO) { load() } }.getOrNull()
        engine
    }

    private fun load(): OfflineTts? {
        val dir = unpack() ?: return null

        val model = File(dir, MODEL)
        val voices = File(dir, VOICES)
        val tokens = File(dir, TOKENS)
        val data = File(dir, DATA_DIR)
        if (!model.isFile || !voices.isFile || !tokens.isFile || !data.isDirectory) return null

        speakers = readSpeakers(File(dir, SPEAKERS))

        // Optional extras: the multi-language release ships pronunciation
        // dictionaries, older single-language ones do not. Passing a path that
        // is not there makes the runtime refuse to start, so each is only named
        // if it exists.
        val lexicon = LEXICONS
            .map { File(dir, it) }
            .filter { it.isFile }
            .joinToString(",") { it.absolutePath }
        val dict = File(dir, DICT_DIR)

        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = model.absolutePath,
                    voices = voices.absolutePath,
                    tokens = tokens.absolutePath,
                    dataDir = data.absolutePath,
                    dictDir = if (dict.isDirectory) dict.absolutePath else "",
                    lexicon = lexicon
                ),
                numThreads = THREADS,
                debug = false,
                provider = "cpu"
            ),
            maxNumSentences = 1
        )

        return OfflineTts(config = config)
    }

    /**
     * Copies the model out of the APK on first use.
     *
     * The marker file holds the model id, so replacing the model in a later
     * version of the app replaces the unpacked copy too instead of quietly
     * loading last version's weights forever.
     */
    private fun unpack(): File? = runCatching {
        val target = File(app.filesDir, UNPACK_DIR)
        val marker = File(target, MARKER)
        if (marker.isFile && marker.readText().trim() == id) return@runCatching target

        target.deleteRecursively()
        target.mkdirs()
        copyTree(ASSET_DIR, target)

        // Written last, and only after everything else is in place: a copy
        // interrupted by the user killing the app must not look finished.
        marker.writeText(id)
        target
    }.getOrNull()

    private fun copyTree(assetPath: String, dest: File) {
        val children = app.assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            dest.parentFile?.mkdirs()
            app.assets.open(assetPath).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }
        dest.mkdirs()
        for (child in children) copyTree(assetPath + "/" + child, File(dest, child))
    }

    /**
     * Which of the model's voices to use per language, as `en=0` lines.
     *
     * Kokoro carries dozens of voices in one file and they are addressed by
     * number, not by name; which number is which language is a property of the
     * release, not of this code. So the mapping is data, written next to the
     * model by tools/voice/fetch-voice.sh, and a missing or unreadable file
     * just means voice zero.
     */
    private fun readSpeakers(file: File): Map<String, Int> = runCatching {
        if (!file.isFile) return@runCatching emptyMap()
        file.readLines()
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
            .mapNotNull { line ->
                val lang = line.substringBefore('=').trim().lowercase(Locale.ROOT)
                val sid = line.substringAfter('=').trim().toIntOrNull()
                if (lang.isEmpty() || sid == null) null else lang to sid
            }
            .toMap()
    }.getOrDefault(emptyMap())

    private fun languageOf(lang: String): String {
        val tag = Locale.forLanguageTag(lang).language
        return if (tag.isNotBlank()) tag.lowercase(Locale.ROOT)
        else lang.take(2).lowercase(Locale.ROOT)
    }

    private companion object {
        /** Bumped whenever the packed model changes; invalidates unpacked copies and cached audio. */
        const val MODEL_ID = "kokoro-multi-lang-v1_0"

        const val ASSET_DIR = "kokoro"
        const val UNPACK_DIR = "kokoro"
        const val MARKER = ".ready"

        const val MODEL = "model.onnx"
        const val VOICES = "voices.bin"
        const val TOKENS = "tokens.txt"
        const val DATA_DIR = "espeak-ng-data"
        const val DICT_DIR = "dict"
        const val SPEAKERS = "speakers.txt"

        val LEXICONS = listOf("lexicon-us-en.txt", "lexicon-zh.txt")

        /**
         * English and Chinese: what the sherpa-onnx Kokoro release documents
         * and ships dictionaries for. Not ru, not pl -- see supports().
         */
        val SUPPORTED = setOf("en", "zh")

        const val DEFAULT_SPEAKER = 0

        /** Two threads is the sweet spot on a mid-range phone; four is not twice as fast. */
        const val THREADS = 2

        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 1.5f

        /**
         * A card is a phrase and a short sentence. Anything past this is not
         * slow, it is unusable: the next card arrives before the sound does.
         */
        const val SLOW_MS = 4_000L
        const val MAX_SLOW_RENDERS = 3
    }
}
