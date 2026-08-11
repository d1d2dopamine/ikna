package dev.ikna.audio

import android.content.Context

/*
 * The build that carries a voice.
 *
 * Nothing decides here whether the model is usable -- that costs seconds and
 * happens on a background thread the first time something is spoken. This only
 * hands back the implementation; KokoroSpeech.isReady is where the model is
 * actually looked for, and a missing or broken one simply falls back to the
 * phone's engine.
 */
object NeuralSpeechFactory {

    fun create(context: Context): NeuralSpeech? = KokoroSpeech(context)
}
