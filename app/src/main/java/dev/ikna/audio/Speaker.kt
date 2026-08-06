package dev.ikna.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/*
 * Speech, and where it deliberately does not come from.
 *
 * Nothing here synthesises anything itself and nothing here downloads a model.
 * The app talks to whatever speech engine is installed on the phone through the
 * platform API, which is the one decision that makes the rest of this cheap:
 *
 *  - a user on a normal phone already has an engine with Polish, Russian and
 *    English and pays nothing;
 *  - a user on a de-googled phone installs an engine from F-Droid — one built on
 *    Piper models, or RHVoice — picks whatever voices they like inside it, and
 *    this app speaks with them without knowing they exist;
 *  - the APK does not grow by a byte, where bundling a neural runtime would add
 *    15-25MB per architecture before a single voice.
 *
 * Nothing may leave the phone. Several engines expose cloud voices next to local
 * ones, so every voice whose own descriptor admits it needs a network is dropped
 * in [voices] rather than merely discouraged in a setting. A voice that cannot be
 * selected cannot be used by accident.
 *
 * Latency: the slow part is starting the engine and loading its model, not saying
 * six words. So the engine is started when a session opens, and the next card is
 * synthesised to a file while the current one is still on screen. Playing a
 * cached file is immediate, and the cache outlives the session, so a chunk met a
 * second time never waits at all.
 */

/** One selectable voice, already known to work offline. */
data class SpeakerVoice(
    val name: String,
    val label: String,
    val lang: String
)

enum class SpeakerStatus {
    /** Not asked yet. */
    UNKNOWN,

    /** There is an offline voice for this language. */
    READY,

    /** No speech engine on the phone at all. Common on de-googled builds. */
    NO_ENGINE,

    /** An engine is there, but it has nothing installed for this language. */
    NO_VOICE
}

class Speaker(context: Context) {

    private val app = context.applicationContext
    private val dir = File(app.cacheDir, "speech")
    private val lock = Mutex()

    private var engine: TextToSpeech? = null
    private var startAttempted = false
    private var initStatus = TextToSpeech.ERROR

    /** utteranceId -> (temporary file, final file). See the progress listener. */
    private val inFlight = ConcurrentHashMap<String, Pair<File, File>>()

    private var player: MediaPlayer? = null

    /**
     * Start the engine ahead of time. Called when a session opens, because this
     * is the part that can take seconds; everything after it is fast.
     */
    suspend fun warmUp(): Boolean = engine() != null

    private suspend fun engine(): TextToSpeech? = lock.withLock {
        if (startAttempted) {
            return@withLock if (initStatus == TextToSpeech.SUCCESS) engine else null
        }
        startAttempted = true

        val ready = CompletableDeferred<Int>()
        val tts = runCatching {
            withContext(Dispatchers.IO) {
                TextToSpeech(app) { status -> ready.complete(status) }
            }
        }.getOrNull() ?: return@withLock null

        // A broken engine can simply never call back. Waiting forever would leave
        // every later call suspended, so the wait has an end.
        initStatus = withTimeoutOrNull(INIT_TIMEOUT_MS) { ready.await() } ?: TextToSpeech.ERROR

        if (initStatus != TextToSpeech.SUCCESS) {
            runCatching { tts.shutdown() }
            return@withLock null
        }

        // Synthesis writes to a temporary name and is renamed into place only
        // once the engine says it finished. Without this the player could open a
        // file that is still being written and play half a word.
        runCatching {
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    val pair = inFlight.remove(utteranceId ?: return) ?: return
                    val tmp = pair.first
                    val dst = pair.second
                    if (tmp.exists() && tmp.length() > 0) {
                        if (!tmp.renameTo(dst)) tmp.delete()
                    } else {
                        tmp.delete()
                    }
                }

                @Deprecated("Required by the platform base class")
                override fun onError(utteranceId: String?) {
                    inFlight.remove(utteranceId ?: return)?.first?.delete()
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    inFlight.remove(utteranceId ?: return)?.first?.delete()
                }
            })
        }

        engine = tts
        tts
    }

    /**
     * Offline voices for a language, best quality first.
     *
     * Two filters do the work: a voice that declares it needs a network is
     * dropped outright, and so is one the engine lists but has not downloaded.
     */
    suspend fun voices(lang: String): List<SpeakerVoice> {
        val tts = engine() ?: return emptyList()
        val language = languageOf(lang)
        val all = runCatching { tts.voices }.getOrNull() ?: return emptyList()
        return all
            .asSequence()
            .filterNotNull()
            .filter { it.locale?.language.equals(language, ignoreCase = true) }
            .filterNot { it.isNetworkConnectionRequired }
            .filterNot { voice ->
                voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true
            }
            .sortedByDescending { it.quality }
            .map { SpeakerVoice(name = it.name, label = labelOf(it), lang = lang) }
            .toList()
    }

    /** Whether this language can be spoken at all, and if not, why. */
    suspend fun status(lang: String): SpeakerStatus {
        engine() ?: return SpeakerStatus.NO_ENGINE
        return if (voices(lang).isEmpty()) SpeakerStatus.NO_VOICE else SpeakerStatus.READY
    }

    /**
     * Say it now. Uses the cached file when there is one, which is the whole
     * point of [prefetch]; otherwise asks the engine directly rather than making
     * the user wait for a file to be written first.
     */
    suspend fun speak(text: String, lang: String, voiceName: String?) {
        if (text.isBlank()) return
        val tts = engine() ?: return
        val cached = cacheFile(text, lang, voiceName)
        if (cached.exists() && cached.length() > 0) {
            play(cached)
            return
        }
        withContext(Dispatchers.IO) {
            applyVoice(tts, lang, voiceName)
            runCatching {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "live-" + text.hashCode())
            }
        }
    }

    /**
     * Play only if the audio is already on disk.
     *
     * Used for the one place speech starts by itself — a chunk being met for the
     * first time. Sound that arrives on its own two seconds late, over a card the
     * user has already started reading, is worse than no sound, so this never
     * waits and never synthesises.
     *
     * @return true if something was played.
     */
    suspend fun speakIfReady(text: String, lang: String, voiceName: String?): Boolean {
        if (text.isBlank()) return false
        val cached = cacheFile(text, lang, voiceName)
        if (!cached.exists() || cached.length() <= 0) return false
        engine() ?: return false
        play(cached)
        return true
    }

    /**
     * Synthesise in the background so the next card is instant. Does nothing when
     * the file is already there or already being written.
     */
    suspend fun prefetch(text: String, lang: String, voiceName: String?) {
        if (text.isBlank()) return
        val target = cacheFile(text, lang, voiceName)
        if (target.exists() && target.length() > 0) return
        val tts = engine() ?: return

        withContext(Dispatchers.IO) {
            runCatching {
                dir.mkdirs()
                trim()
                val id = "pre-" + target.name
                if (!inFlight.containsKey(id)) {
                    val tmp = File(dir, target.name + ".part")
                    tmp.delete()
                    inFlight[id] = tmp to target
                    applyVoice(tts, lang, voiceName)
                    val result = tts.synthesizeToFile(text, Bundle(), tmp, id)
                    if (result != TextToSpeech.SUCCESS) {
                        inFlight.remove(id)
                        tmp.delete()
                    }
                }
            }
        }
    }

    fun stop() {
        runCatching { player?.release() }
        player = null
        runCatching { engine?.stop() }
    }

    fun shutdown() {
        stop()
        runCatching { engine?.shutdown() }
        engine = null
        startAttempted = false
        initStatus = TextToSpeech.ERROR
    }

    /** Drops synthesised audio. Called when the chosen voice changes. */
    fun clearCache() {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }

    // ---- internals ---------------------------------------------------------

    private fun applyVoice(tts: TextToSpeech, lang: String, voiceName: String?) {
        val locale = Locale.forLanguageTag(lang)
        val chosen: Voice? = if (voiceName.isNullOrBlank()) null else
            runCatching { tts.voices }.getOrNull()
                ?.firstOrNull { it != null && it.name == voiceName && !it.isNetworkConnectionRequired }

        if (chosen != null) {
            runCatching { tts.voice = chosen }
        } else {
            runCatching { tts.language = locale }
        }
    }

    private fun play(file: File) {
        runCatching {
            player?.release()
            player = null
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener { done ->
                runCatching { done.release() }
                if (player === done) player = null
            }
            mp.prepare()
            mp.start()
            player = mp
        }
    }

    /**
     * Name of the cached file. The voice is part of the key: changing the voice
     * must not keep playing the old one back from disk.
     */
    private fun cacheFile(text: String, lang: String, voiceName: String?): File {
        val key = lang + "|" + (voiceName ?: "") + "|" + text
        val name = Integer.toHexString(key.hashCode()) + "-" + text.length + ".wav"
        return File(dir, name)
    }

    /** Keeps the cache small enough to be forgettable. Oldest go first. */
    private fun trim() {
        val files = dir.listFiles() ?: return
        if (files.size <= CACHE_LIMIT) return
        files.sortedBy { it.lastModified() }
            .take(files.size - CACHE_LIMIT)
            .forEach { it.delete() }
    }

    private fun languageOf(lang: String): String {
        val tag = Locale.forLanguageTag(lang).language
        return if (tag.isNotBlank()) tag else lang.take(2).lowercase(Locale.ROOT)
    }

    /**
     * Engine voice names are machine-shaped: "pl-pl-x-oda-local". The region and
     * a quality word are the only parts worth reading, so that is what is shown.
     */
    private fun labelOf(voice: Voice): String {
        val country = voice.locale?.country.orEmpty()
        val head = if (country.isNotBlank()) country.uppercase(Locale.ROOT) else
            voice.locale?.language.orEmpty().uppercase(Locale.ROOT)
        val quality = when {
            voice.quality >= Voice.QUALITY_VERY_HIGH -> "ОТЛИЧНОЕ"
            voice.quality >= Voice.QUALITY_HIGH -> "ХОРОШЕЕ"
            voice.quality >= Voice.QUALITY_NORMAL -> "ОБЫЧНОЕ"
            else -> "НИЗКОЕ"
        }
        val tail = voice.name.substringAfterLast('-', "").uppercase(Locale.ROOT)
        return listOfNotNull(head, tail.takeIf { it.isNotBlank() && it != head }, quality)
            .joinToString(" · ")
    }

    private companion object {
        const val INIT_TIMEOUT_MS = 8_000L
        const val CACHE_LIMIT = 240
    }
}
