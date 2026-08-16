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
 * Speech from the models the user added.
 *
 * This build carries the runtime and no weights -- about ten megabytes of
 * sherpa-onnx and nothing else. Whatever speaks here was copied in from the
 * phone by hand, which means everything about it is unknown until it is loaded:
 * its shape, its language, whether it fits in memory on this particular phone.
 *
 * So the rules are: load late, hold on to what worked, and give up loudly. An
 * earlier version of this file failed silently and left people staring at a card
 * that was supposed to talk, which is worse than a voice that never claimed to
 * exist.
 *
 * From 0.5.0 there can be several models installed at once, and this class holds
 * exactly one of them in memory: the one whose language came up last. Two nets
 * resident at the same time is how a mid-range phone runs out of memory, and
 * loading is fast enough that swapping on a language change costs less than
 * being killed. Which model reads which language is not decided here -- see
 * VoiceModelStore.forLanguage, where "switched on", "declared this language" and
 * "declared nothing" are weighed against each other in one place.
 */
class SherpaSpeech(context: Context) : NeuralSpeech {

    private val app = context.applicationContext
    private val store = VoiceModelStore(app)
    private val lock = Mutex()

    private var tts: OfflineTts? = null

    /**
     * Which net is in memory, keyed by the folder and the file it was read from.
     *
     * Not by the model's cache id. That id carries the voice number and the
     * speed, and neither is a property of the net -- both are arguments to a
     * single rendering. Keying it by the id meant every tap on the voice number
     * released a hundred megabytes and read them straight back in, which was
     * slow, and which is also the window where a rendering already running was
     * left holding native memory that had just been freed.
     */
    private var loadedKey: String? = null

    /**
     * True while generate() is inside the native runtime.
     *
     * The only reason this exists is release(): freeing a net that a rendering is
     * still reading from is a native crash with nothing to catch, so a shutdown
     * arriving mid-rendering drops the reference now and frees the memory when
     * the rendering comes back.
     */
    private var rendering = false
    private var releaseWanted = false

    /**
     * Models that already failed to load, or were too slow to be worth waiting
     * for. Remembered per model rather than as one flag: with several installed,
     * one net being too big for the phone says nothing about the next one.
     */
    private val failed = mutableSetOf<String>()

    /**
     * How many renderings took long enough to be useless. Three and the model is
     * dropped: on a weak phone a big net does not fail, it just takes twenty
     * seconds a card, which feels exactly like a broken app.
     */
    private var slowRenders = 0

    override fun supports(lang: String): Boolean = installFor(lang) != null

    override fun idFor(lang: String): String = installFor(lang)?.id ?: NONE

    override fun hasAnyModel(): Boolean = store.enabled().isNotEmpty()

    override suspend fun isReady(lang: String): Boolean {
        val install = installFor(lang) ?: return false
        return lock.withLock { engineLocked(install) } != null
    }

    override suspend fun warmUp(): Boolean {
        // Whichever model is switched on, without asking about a language: one
        // net is resident at a time, and having it resident is the whole point.
        val install = store.enabled().firstOrNull() ?: return false
        return lock.withLock { engineLocked(install) } != null
    }

    override suspend fun render(
        text: String,
        lang: String,
        target: File,
    ): Boolean {
        val install = installFor(lang) ?: return false
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false

        // Loading and rendering happen under the same lock, and that is what the
        // lock is for. Two coroutines do arrive here -- the mark somebody pressed
        // and the next card being rendered ahead -- and the second one used to be
        // able to swap the net out from under the first. Two nets in memory is a
        // phone killed for memory; a freed net still being read is a native
        // crash. Taking turns costs a queued card and nothing else.
        return lock.withLock {
            val engine = engineLocked(install) ?: return@withLock false
            rendering = true
            try {
                renderWith(engine, install, trimmed, target)
            } finally {
                rendering = false
                if (releaseWanted) releaseNow()
            }
        }
    }

    private suspend fun renderWith(
        engine: OfflineTts,
        install: VoiceModelInstall,
        text: String,
        target: File,
    ): Boolean = withContext(Dispatchers.Default) {
        val started = System.currentTimeMillis()
        val ok = runCatching {
            val audio = engine.generate(
                text = text,
                // install.voice, never install.speaker: a number the runtime has
                // not confirmed is exactly the crash this release closes.
                // sherpa-onnx does not return an error for a voice past the last
                // one, it ends the process, so nothing unconfirmed is handed to
                // it -- and voice 0 exists in every model there is.
                sid = install.voice,
                // The model's own speed, from its own manifest.
                speed = install.speed,
            )
            // Written beside the target and moved, so a rendering cut short
            // never leaves a truncated wav to be played back forever after.
            val tmp = File(target.parentFile, target.name + ".part")
            val saved = runCatching { audio.save(tmp.absolutePath) }.isSuccess
            // Nothing to free here: what generate() returns is a plain array
            // of samples, collected like any other object. The engine itself
            // holds the native memory, and releaseNow() is what frees that.
            saved && tmp.isFile && tmp.length() > 0L && tmp.renameTo(target)
        }.getOrDefault(false)

        val took = System.currentTimeMillis() - started
        if (took > SLOW_MS) {
            slowRenders += 1
            if (slowRenders >= MAX_SLOW_RENDERS) {
                // Too slow to be worth waiting for on this phone. The phone's
                // own voice takes over and the voice screen says so. Only this
                // model is written off; another one may be small enough. Safe to
                // free here: generate() has already come back.
                failed.add(install.engineKey)
                releaseNow()
            }
        } else if (ok) {
            slowRenders = 0
        }

        ok
    }

    override fun shutdown() {
        // A rendering in flight is holding this net inside native code. Dropping
        // the reference is safe, freeing the memory is not, so the freeing waits
        // for the rendering to return -- see the finally in render().
        if (rendering) {
            releaseWanted = true
            return
        }
        releaseNow()
    }

    private fun releaseNow() {
        runCatching { tts?.release() }
        tts = null
        loadedKey = null
        releaseWanted = false
    }

    // ---- loading -----------------------------------------------------------

    /** Whichever switched-on model claims this language, or none. */
    private fun installFor(lang: String): VoiceModelInstall? = store.forLanguage(lang)

    /**
     * The net for this model, read in when it is not the one already resident.
     *
     * Assumes [lock] is held. Every caller either takes it around this call or
     * renders under the same one, and that is what stops a load from freeing a
     * net another rendering is still using.
     */
    private suspend fun engineLocked(install: VoiceModelInstall): OfflineTts? {
        if (install.engineKey in failed) return null

        val loaded = tts
        if (loaded != null && install.engineKey == loadedKey) return loaded

        // A different model than the one in memory: the old one goes first, or
        // both nets sit in memory at once and neither survives it.
        if (loaded != null) releaseNow()
        slowRenders = 0

        val built = withContext(Dispatchers.IO) {
            runCatching { build(install) }
                .onFailure { failed.add(install.engineKey) }
                .getOrNull()
        } ?: return null

        tts = built
        loadedKey = install.engineKey

        // The one moment the number of voices can be learned honestly. Written
        // down so the voice screen can bound its own buttons without loading
        // anything, and so a number left behind by an older version is brought
        // back into range before it is ever used.
        val voices = voiceCount(built)
        if (voices > 0 && voices != install.speakers) {
            withContext(Dispatchers.IO) { store.setSpeakerCount(install.slug, voices) }
        }

        return built
    }

    /**
     * How many voices the loaded net has, asked of the runtime itself.
     *
     * Read reflectively, deliberately. This number is the difference between
     * choosing a Kokoro voice and killing the process, so it has to come from
     * the net rather than from a constant somebody wrote down -- but the accessor
     * for it is not the same shape in every sherpa-onnx release, and a name that
     * turned out not to exist would be a build failure in the one file that
     * exists to prevent a crash. A name missing at runtime costs one voice; a
     * name missing at compile time costs the release.
     *
     * Zero means the runtime did not answer, and zero is read everywhere else as
     * "voice 0 only" -- the voice every model has.
     */
    private fun voiceCount(engine: OfflineTts): Int {
        for (name in ACCESSORS) {
            val found = runCatching {
                engine.javaClass.getMethod(name).invoke(engine) as? Int
            }.getOrNull()
            if (found != null && found > 0) return found
        }
        return 0
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

        /**
         * What the number of voices might be called. Tried in order, and the
         * speed range that used to live here now travels with the model instead:
         * see MIN_RATE in VoiceModelStore.kt.
         */
        val ACCESSORS = listOf("numSpeakers", "getNumSpeakers")

        const val SLOW_MS = 4_000L
        const val MAX_SLOW_RENDERS = 3

        const val NONE = "no-model"
    }
}
