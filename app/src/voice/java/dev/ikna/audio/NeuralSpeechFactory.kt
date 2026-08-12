package dev.ikna.audio

import android.content.Context

/**
 * The voice build: the sherpa-onnx runtime, and no weights at all.
 *
 * An earlier version of this build shipped Kokoro inside the APK. That was a
 * mistake twice over -- half a gigabyte for one language, and a model that never
 * matched an imported deck, so the extra weight bought most people silence. The
 * runtime stays, the model comes from the person using the app, and the voice
 * screen says at all times which of the two is speaking.
 */
object NeuralSpeechFactory {
    fun create(context: Context): NeuralSpeech? = SherpaSpeech(context)
}
