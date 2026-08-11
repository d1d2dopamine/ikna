package dev.ikna.audio

import java.io.File

/*
 * A voice that lives inside the APK.
 *
 * Everything else about speech in this app goes through the platform engine
 * (see Speaker): whatever the phone has installed, zero bytes added, every
 * language the user already owns. That stays the default, and in the plain
 * build it stays the only thing that exists.
 *
 * This interface is the one seam where a synthesiser shipped with the app can
 * be plugged in instead. It is present in every build; what differs is whether
 * anything implements it. The `lite` flavour has no implementation --
 * NeuralSpeechFactory.create returns null, no runtime is linked, and the APK is
 * the size it always was. The `voice` flavour carries the runtime and the
 * model and returns a real one.
 *
 * The contract is deliberately narrow -- render this text into this file -- for
 * two reasons. Caching, playback, speed, the fallback to the platform engine
 * and the cache key stay in one place instead of two; and swapping the model
 * later (a smaller one, a per-language one) means writing five members and
 * moving nothing else.
 */
interface NeuralSpeech {

    /**
     * Identifies the model and its settings, and is mixed into the name of the
     * cached audio file. Two engines must never share an id, or a card rendered
     * by one gets played back from disk for the other.
     */
    val id: String

    /**
     * Whether this model can speak the language at all.
     *
     * A model that covers eight languages covers eight, not all of them; a deck
     * in a ninth falls back to the platform engine instead of being read out
     * with the wrong phonemes.
     */
    fun supports(lang: String): Boolean

    /**
     * Whether the model is present and loadable. Called off the main thread and
     * slow the first time, because that is when the model is read in. Returning
     * false has to leave the app working: the platform engine takes over and the
     * user hears speech either way.
     */
    suspend fun isReady(): Boolean

    /**
     * Synthesise [text] into [target] as a playable file.
     *
     * [speed] is the same number the speed setting gives the platform engine,
     * where 1.0 is the model's own pace. Pitch has no equivalent here and is
     * ignored: a neural voice has one pitch, its own.
     *
     * Implementations write to a temporary name and rename into place, so a
     * player looking in the cache directory can never open a half-written file.
     *
     * @return true only if [target] now exists and holds audio.
     */
    suspend fun render(text: String, lang: String, speed: Float, target: File): Boolean

    /** Release the model. Called when speech is shut down. */
    fun shutdown()
}
