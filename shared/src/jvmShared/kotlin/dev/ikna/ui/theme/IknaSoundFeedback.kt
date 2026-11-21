package dev.ikna.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Restrained tactile sound cues synthesized offline at runtime.
 *
 * No bundled audio files, no network calls, and no punishment buzzers:
 * every cue uses a short, soft envelope with smooth attack and decay.
 */
enum class IknaSoundCue {
    TAP,
    TOGGLE_ON,
    TOGGLE_OFF,
    REVEAL,
    KNOW,
    DONT_KNOW,
    COMPLETE
}

internal const val IKNA_SOUND_SAMPLE_RATE = 22050

internal val iknaUiSoundsLiveGate = AtomicBoolean(true)

@Immutable
fun interface IknaSoundFeedback {
    fun play(cue: IknaSoundCue)
}

internal object NoOpIknaSoundFeedback : IknaSoundFeedback {
    override fun play(cue: IknaSoundCue) = Unit
}

internal object LiveIknaSoundFeedback : IknaSoundFeedback {
    override fun play(cue: IknaSoundCue) {
        if (iknaUiSoundsLiveGate.get()) {
            playIknaSoundCue(cue)
        }
    }
}

val LocalIknaSoundFeedback = staticCompositionLocalOf<IknaSoundFeedback> { NoOpIknaSoundFeedback }

/** Platform audio playback for synthesized 16-bit mono PCM cues. */
expect fun playIknaSoundCue(cue: IknaSoundCue)

/** Deterministic 16-bit signed little-endian mono PCM synthesis for every cue. */
internal fun synthesizeIknaCuePcm16Le(cue: IknaSoundCue): ByteArray {
    val samples = when (cue) {
        IknaSoundCue.TAP -> renderSweep(
            durationMs = 16,
            startHz = 760.0,
            endHz = 390.0,
            gain = 0.14,
            decay = 16.0,
            harmonicMix = 0.18
        )
        IknaSoundCue.TOGGLE_ON -> renderTwoStep(
            firstHz = 520.0,
            secondHz = 740.0,
            stepMs = 15,
            gain = 0.15
        )
        IknaSoundCue.TOGGLE_OFF -> renderTwoStep(
            firstHz = 640.0,
            secondHz = 440.0,
            stepMs = 15,
            gain = 0.14
        )
        IknaSoundCue.REVEAL -> renderSweep(
            durationMs = 36,
            startHz = 440.0,
            endHz = 587.33,
            gain = 0.15,
            decay = 9.5,
            harmonicMix = 0.14
        )
        IknaSoundCue.KNOW -> renderChord(
            durationMs = 64,
            frequencies = doubleArrayOf(523.25, 783.99),
            weights = doubleArrayOf(0.65, 0.35),
            gain = 0.17,
            decay = 7.2
        )
        IknaSoundCue.DONT_KNOW -> renderSweep(
            durationMs = 46,
            startHz = 275.0,
            endHz = 205.0,
            gain = 0.16,
            decay = 8.5,
            harmonicMix = 0.10
        )
        IknaSoundCue.COMPLETE -> renderArpeggio(
            frequencies = doubleArrayOf(523.25, 659.25, 783.99),
            strideMs = 52,
            noteMs = 110,
            gain = 0.16
        )
    }
    return pcm16LeBytes(samples)
}

private fun renderSweep(
    durationMs: Int,
    startHz: Double,
    endHz: Double,
    gain: Double,
    decay: Double,
    harmonicMix: Double
): DoubleArray {
    val count = (IKNA_SOUND_SAMPLE_RATE * durationMs) / 1000
    val out = DoubleArray(count)
    val attackSamples = (IKNA_SOUND_SAMPLE_RATE * 0.002).coerceAtLeast(1.0)
    var phase = 0.0
    for (i in 0 until count) {
        val u = i.toDouble() / count.coerceAtLeast(1)
        val hz = startHz + (endHz - startHz) * u
        phase += 2.0 * PI * hz / IKNA_SOUND_SAMPLE_RATE
        val attack = if (i < attackSamples) 0.5 * (1.0 - cos(PI * i / attackSamples)) else 1.0
        val release = exp(-decay * u) * (1.0 - u)
        val wave = sin(phase) + harmonicMix * sin(phase * 2.0)
        out[i] = wave * attack * release * gain
    }
    return out
}

private fun renderTwoStep(
    firstHz: Double,
    secondHz: Double,
    stepMs: Int,
    gain: Double
): DoubleArray {
    val stepSamples = (IKNA_SOUND_SAMPLE_RATE * stepMs) / 1000
    val total = stepSamples * 2
    val out = DoubleArray(total)
    val attack = (IKNA_SOUND_SAMPLE_RATE * 0.002).coerceAtLeast(1.0)
    for (segment in 0..1) {
        val hz = if (segment == 0) firstHz else secondHz
        val offset = segment * stepSamples
        val segGain = if (segment == 0) gain * 0.78 else gain
        var phase = 0.0
        for (i in 0 until stepSamples) {
            val u = i.toDouble() / stepSamples.coerceAtLeast(1)
            phase += 2.0 * PI * hz / IKNA_SOUND_SAMPLE_RATE
            val a = if (i < attack) 0.5 * (1.0 - cos(PI * i / attack)) else 1.0
            val env = a * exp(-8.0 * u) * (1.0 - u)
            out[offset + i] = sin(phase) * env * segGain
        }
    }
    return out
}

private fun renderChord(
    durationMs: Int,
    frequencies: DoubleArray,
    weights: DoubleArray,
    gain: Double,
    decay: Double
): DoubleArray {
    val count = (IKNA_SOUND_SAMPLE_RATE * durationMs) / 1000
    val out = DoubleArray(count)
    val attack = (IKNA_SOUND_SAMPLE_RATE * 0.003).coerceAtLeast(1.0)
    for (i in 0 until count) {
        val t = i.toDouble() / IKNA_SOUND_SAMPLE_RATE
        val u = i.toDouble() / count.coerceAtLeast(1)
        val a = if (i < attack) 0.5 * (1.0 - cos(PI * i / attack)) else 1.0
        val env = a * exp(-decay * u) * (1.0 - u)
        var sum = 0.0
        for (k in frequencies.indices) {
            sum += weights[k] * sin(2.0 * PI * frequencies[k] * t)
        }
        out[i] = sum * env * gain
    }
    return out
}

private fun renderArpeggio(
    frequencies: DoubleArray,
    strideMs: Int,
    noteMs: Int,
    gain: Double
): DoubleArray {
    val stride = (IKNA_SOUND_SAMPLE_RATE * strideMs) / 1000
    val noteCount = (IKNA_SOUND_SAMPLE_RATE * noteMs) / 1000
    val total = stride * (frequencies.size - 1) + noteCount
    val out = DoubleArray(total)
    val attack = (IKNA_SOUND_SAMPLE_RATE * 0.004).coerceAtLeast(1.0)
    frequencies.forEachIndexed { idx, hz ->
        val start = idx * stride
        val noteGain = gain * (0.85 + 0.075 * idx)
        for (i in 0 until noteCount) {
            val t = i.toDouble() / IKNA_SOUND_SAMPLE_RATE
            val u = i.toDouble() / noteCount.coerceAtLeast(1)
            val a = if (i < attack) 0.5 * (1.0 - cos(PI * i / attack)) else 1.0
            val env = a * exp(-5.8 * u) * (1.0 - u)
            out[start + i] += sin(2.0 * PI * hz * t) * env * noteGain
        }
    }
    return out
}

private fun pcm16LeBytes(samples: DoubleArray): ByteArray {
    val bytes = ByteArray(samples.size * 2)
    for (i in samples.indices) {
        val clamped = samples[i].coerceIn(-0.98, 0.98)
        val pcm = (clamped * Short.MAX_VALUE).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        bytes[i * 2] = (pcm and 0xFF).toByte()
        bytes[i * 2 + 1] = ((pcm shr 8) and 0xFF).toByte()
    }
    return bytes
}
