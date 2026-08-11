package dev.ikna.audio

import android.content.Context

/*
 * The plain build: no model, no runtime, nothing to load.
 *
 * This file is the entire difference on this side of the fork. Speaker asks for
 * a bundled voice once, gets null, and uses the phone's own speech engine for
 * everything -- which is what it did before any of this existed.
 *
 * Keeping the flavour source set this small is the point. If the neural path
 * ever grows a dependency that leaks into shared code, this file stops
 * compiling, and that is a much better way to find out than shipping an APK
 * that turned out to be 130MB.
 */
object NeuralSpeechFactory {

    /** Always null here. See app/src/voice for the build that returns a voice. */
    fun create(context: Context): NeuralSpeech? = null
}
