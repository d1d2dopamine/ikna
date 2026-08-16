package dev.ikna.audio

import java.io.File

/*
 * A voice that lives in the app's own files.
 *
 * Everything else about speech in this app goes through the platform engine
 * (see Speaker): whatever the phone has installed, zero bytes added, every
 * language the user already owns. That stays the default and the fallback.
 *
 * This interface is the one seam where a synthesiser the user added can be
 * plugged in instead. Every question on it takes a language, because from 0.5.0
 * there is not one model but a list of them: a Russian voice and an English one
 * are two files, two languages, and two different answers to "can you read this
 * card". Asking without saying which language is what made the 0.4.0 bug
 * possible -- the test button spoke and the cards stayed silent, because one
 * global "ready" was standing in for a per-language question.
 *
 * The contract is deliberately narrow -- render this text into this file -- so
 * that caching, playback, speed and the fallback to the platform engine stay in
 * one place instead of two.
 */
interface NeuralSpeech {

    /**
     * Whether a model that speaks this language is installed and switched on.
     *
     * A model that covers eight languages covers eight, not all of them; a deck
     * in a ninth falls back to the platform engine instead of being read out with
     * the wrong phonemes.
     */
    fun supports(lang: String): Boolean

    /**
     * Identifies whichever model would read this language, and is mixed into the
     * name of the cached audio file. Two models must never share an id, or a card
     * rendered by one gets played back from disk for the other -- which is
     * exactly what happens when somebody switches voices to compare them.
     */
    fun idFor(lang: String): String

    /**
     * Whether any model at all is installed and switched on.
     *
     * Cheap, and asked before anything expensive: it reads a folder listing and
     * loads nothing. Warm-up needs it, because "there is a model, so getting the
     * speech machinery ready is worth it" is a different question from "can this
     * particular language be read right now".
     */
    fun hasAnyModel(): Boolean

    /**
     * Load whichever model is switched on, before anybody asks it to speak.
     *
     * This is the call that pays for reading a net in -- seconds on a mid-range
     * phone -- and it exists because [hasAnyModel] was standing in for it:
     * warm-up answered "there is a model" in a millisecond, loaded nothing, and
     * the first card of a session paid for the load and the rendering while it
     * was already on screen. That was the couple of seconds before the first
     * word. Takes no language on purpose: one net is resident at a time, and the
     * point is to have it resident, not to guess which deck comes first.
     *
     * @return true if a model is now loaded and able to render.
     */
    suspend fun warmUp(): Boolean

    /**
     * Whether the model for this language is present and loadable. Called off the
     * main thread and slow the first time, because that is when the model is read
     * in. Returning false has to leave the app working: the platform engine takes
     * over and the user hears speech either way.
     */
    suspend fun isReady(lang: String): Boolean

    /**
     * Synthesise [text] into [target] as a playable file.
     *
     * Speed is not passed in. From this version every model carries its own, in
     * its own manifest, because a Piper voice recorded slowly and a Kokoro voice
     * that already hurries do not want the same number -- and one control for
     * all of them was a compromise that suited none. Pitch has no equivalent
     * here at all, so there is none to pass: a neural voice has one pitch, its
     * own. The phone's engine keeps both numbers in settings, where both work.
     *
     * Implementations write to a temporary name and rename into place, so a
     * player looking in the cache directory can never open a half-written file.
     *
     * @return true only if [target] now exists and holds audio.
     */
    suspend fun render(text: String, lang: String, target: File): Boolean

    /** Release the model. Called when speech is shut down. */
    fun shutdown()
}
