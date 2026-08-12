package dev.ikna.audio

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Speech from the model the user added.
 *
 * This build carries the runtime and no weights -- about ten megabytes of
 * sherpa-onnx and nothing else. Whatever speaks here was copied in from the
 * phone by hand, which means everything about it is unknown until it is loaded:
 * its shape, its language, whether it fits in memory on this particular phone.
 *
 * So the rules are: load late, hold on to what worked, and give up loudly. The
 * previous version of this file failed silently and left people staring at a
 * card that was supposed to talk, which is worse than a voice that never claimed
 * to exist.
 */
class SherpaSpeech(context: Context) : NeuralSpeech {

    private val app = context.applicationContext
    private val store = VoiceModelStore(app)
    private val lock = Mutex()

    private var tts: OfflineTts? = null

    /** Which model the loaded engine belongs to, so a swap is noticed. */
    private var loadedId: String? = null

    /** A model that already failed to load. Retried on the next swap, not before. */
    private var failedId: String? = null

    /**
     * How many renderings took long enough to be useless. Three and the model is
     * dropped: on a weak phone a big net does not fail, it just takes twenty
     * seconds a card, which feels exactly like a broken app.
     */
    private var slowRenders = 0

    override val id: String
        get() = store.installed()?.id ?: NONE

    /**
     * A model whose name never said which language it speaks -- every
     * multi-language release -- is offered for anything, because refusing is the
     * one answer that is certainly wrong. A model that did say is held to it.
     */
    override fun supports(lang: String): Boolean {
        val install = store.installed() ?: return false
        val declared = install.lang ?: return true
        // Decks made by the user are stored as "custom": no language was ever
        // declared for them. There is one model on this phone and it was put
        // there deliberately, so an unknown language is answered with it.
        if (lang.isEmpty() || lang.equals(UNKNOWN, ignoreCase = true)) return true
        return lang.take(2).equals(declared.take(2), ignoreCase = true)
    }

    override suspend fun isReady(): Boolean = engine() != null

    override suspend fun render(
        text: String,
        lang: String,
        speed: Float,
        target: File,
    ): Boolean {
        val install = store.installed() ?: return false
        val engine = engine() ?: return false
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false

        return withContext(Dispatchers.Default) {
            val started = System.currentTimeMillis()
            val ok = runCatching {
                val audio = engine.generate(
                    text = trimmed,
                    sid = install.speaker,
                    speed = speed.coerceIn(MIN_SPEED, MAX_SPEED),
                )
                // Written beside the target and moved, so a rendering cut short
                // never leaves a truncated wav to be played back forever after.
                val tmp = File(target.parentFile, target.name + ".part")
                val saved = runCatching { audio.save(tmp.absolutePath) }.isSuccess
                runCatching { audio.release() }
                saved && tmp.isFile && tmp.length() > 0L && tmp.renameTo(target)
            }.getOrDefault(false)

            val took = System.currentTimeMillis() - started
            if (took > SLOW_MS) {
                slowRenders += 1
                if (slowRenders >= MAX_SLOW_RENDERS) {
                    // Too slow to be worth waiting for on this phone. The phone's
                    // own voice takes over and the voice screen says so.
                    failedId = install.id
                    shutdown()
                }
            } else if (ok) {
                slowRenders = 0
            }

            ok
        }
    }

    override fun shutdown() {
        runCatching { tts?.release() }
        tts = null
        loadedId = null
    }

    // ---- loading -----------------------------------------------------------

    private suspend fun engine(): OfflineTts? = lock.withLock {
        val install = store.installed()
        if (install == null) {
            if (tts != null) shutdown()
            return@withLock null
        }
        if (install.id == failedId) return@withLock null

        val loaded = tts
        if (loaded != null && install.id == loadedId) return@withLock loaded

        // A different model than the one in memory: the old one goes first, or
        // both nets sit in memory at once and neither survives it.
        if (loaded != null) shutdown()
        failedId = null
        slowRenders = 0

        withContext(Dispatchers.IO) {
            runCatching { build(install) }
                .onFailure { failedId = install.id }
                .getOrNull()
        }?.also {
            tts = it
            loadedId = install.id
        }
    }

    private fun build(install: VoiceModelInstall): OfflineTts {
        val dir = install.dir
        val model = install.file.absolutePath
        val tokens = File(dir, VoiceModelLayout.TOKENS).absolutePath
        val data = File(dir, VoiceModelLayout.DATA_DIR)
        val dict = File(dir, VoiceModelLayout.DICT_DIR)

        // Every lexicon the folder brought, in the order sherpa expects them.
        val lexicons = dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("lexicon") && it.name.endsWith(".txt") }
            ?.sortedBy { it.name }
            ?.joinToString(",") { it.absolutePath }
            .orEmpty()

        val modelConfig = when (install.kind) {
            VoiceModelKind.KOKORO -> OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = model,
                    voices = File(dir, VoiceModelLayout.VOICES).absolutePath,
                    tokens = tokens,
                    dataDir = if (data.isDirectory) data.absolutePath else "",
                    dictDir = if (dict.isDirectory) dict.absolutePath else "",
                    lexicon = lexicons,
                ),
                numThreads = THREADS,
                debug = false,
                provider = PROVIDER,
            )

            VoiceModelKind.VITS -> OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = model,
                    tokens = tokens,
                    // espeak data and a lexicon are two ways of doing the same
                    // job, and sherpa wants exactly one of them: passing both
                    // makes a Piper voice read out its own phonemes.
                    dataDir = if (data.isDirectory) data.absolutePath else "",
                    lexicon = if (data.isDirectory) "" else lexicons,
                ),
                numThreads = THREADS,
                debug = false,
                provider = PROVIDER,
            )
        }

        return OfflineTts(
            config = OfflineTtsConfig(
                model = modelConfig,
                // One sentence at a time. A whole card rendered in one go holds
                // its buffers for the length of the card; a phone notices.
                maxNumSentences = 1,
            )
        )
    }

    private companion object {
        /** Two threads. Four render no faster on a phone and heat it instead. */
        const val THREADS = 2
        const val PROVIDER = "cpu"

        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 1.5f

        const val SLOW_MS = 4_000L
        const val MAX_SLOW_RENDERS = 3

        const val NONE = "no-model"

        /** What the importer writes when a deck never said what language it is. */
        const val UNKNOWN = "custom"
    }
}
