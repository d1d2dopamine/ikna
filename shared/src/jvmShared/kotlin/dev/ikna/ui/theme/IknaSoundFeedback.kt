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
 * every cue uses a warm low-pass filtered percussive or wooden envelope
 * with smooth attack and decay.
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
        IknaSoundCue.TAP -> lowPass(
            renderWarmTap(
                durationMs = 13,
                startHz = 440.0,
                endHz = 230.0,
                gain = 0.19,
                decay = 14.0
            ),
            cutoffHz = 1050.0
        )
        IknaSoundCue.TOGGLE_ON -> lowPass(
            renderMarimbaStep(
                frequencies = doubleArrayOf(360.0, 490.0),
                strideMs = 15,
                noteMs = 24,
                gain = 0.13,
                attackMs = 3.5,
                decay = 9.5
            ),
            cutoffHz = 1150.0
        )
        IknaSoundCue.TOGGLE_OFF -> lowPass(
            renderMarimbaStep(
                frequencies = doubleArrayOf(450.0, 325.0),
                strideMs = 15,
                noteMs = 24,
                gain = 0.12,
                attackMs = 3.5,
                decay = 9.5
            ),
            cutoffHz = 1050.0
        )
        IknaSoundCue.REVEAL -> lowPass(
            renderPaperReveal(durationMs = 36, gain = 0.13),
            cutoffHz = 1250.0
        )
        IknaSoundCue.KNOW -> lowPass(
            renderWarmTap(
                durationMs = 34,
                startHz = 285.0,
                endHz = 385.0,
                gain = 0.18,
                decay = 8.6
            ),
            cutoffHz = 960.0
        )
        IknaSoundCue.DONT_KNOW -> lowPass(
            renderWarmTap(
                durationMs = 40,
                startHz = 215.0,
                endHz = 152.0,
                gain = 0.135,
                decay = 8.2
            ),
            cutoffHz = 700.0
        )
        IknaSoundCue.COMPLETE -> lowPass(
            renderMarimbaStep(
                frequencies = doubleArrayOf(392.0, 493.88, 587.33),
                strideMs = 56,
                noteMs = 118,
                gain = 0.14,
                attackMs = 7.0,
                decay = 5.4
            ),
            cutoffHz = 1280.0
        )
    }
    applyEdgeFades(samples)
    return pcm16LeBytes(samples)
}

private fun renderWarmTap(
    durationMs: Int,
    startHz: Double,
    endHz: Double,
    gain: Double,
    decay: Double
): DoubleArray {
    val count = (IKNA_SOUND_SAMPLE_RATE * durationMs) / 1000
    val out = DoubleArray(count)
    val attackSamples = (IKNA_SOUND_SAMPLE_RATE * 0.0025).coerceAtLeast(1.0)
    var phase = 0.0
    for (i in 0 until count) {
        val u = i.toDouble() / count.coerceAtLeast(1)
        val hz = startHz + (endHz - startHz) * u
        phase += 2.0 * PI * hz / IKNA_SOUND_SAMPLE_RATE
        val attack = if (i < attackSamples) 0.5 * (1.0 - cos(PI * i / attackSamples)) else 1.0
        val env = attack * exp(-decay * u) * (1.0 - u)
        val body = sin(phase) + 0.12 * sin(phase * 2.0)
        out[i] = body * env * gain
    }
    return out
}

private fun renderPaperReveal(durationMs: Int, gain: Double): DoubleArray {
    val count = (IKNA_SOUND_SAMPLE_RATE * durationMs) / 1000
    val out = DoubleArray(count)
    val attack = (IKNA_SOUND_SAMPLE_RATE * 0.005).coerceAtLeast(1.0)
    var phase = 0.0
    var noiseState = 0x5EED_1234
    for (i in 0 until count) {
        val u = i.toDouble() / count.coerceAtLeast(1)
        val hz = 310.0 + 85.0 * u
        phase += 2.0 * PI * hz / IKNA_SOUND_SAMPLE_RATE
        noiseState = noiseState xor (noiseState shl 13)
        noiseState = noiseState xor (noiseState ushr 17)
        noiseState = noiseState xor (noiseState shl 5)
        val noise = ((noiseState and 0xFFFF) / 32768.0) - 1.0
        val a = if (i < attack) 0.5 * (1.0 - cos(PI * i / attack)) else 1.0
        val env = a * exp(-6.8 * u) * (1.0 - u)
        out[i] = (0.78 * sin(phase) + 0.22 * noise) * env * gain
    }
    return out
}

private fun renderMarimbaStep(
    frequencies: DoubleArray,
    strideMs: Int,
    noteMs: Int,
    gain: Double,
    attackMs: Double,
    decay: Double
): DoubleArray {
    val stride = (IKNA_SOUND_SAMPLE_RATE * strideMs) / 1000
    val noteCount = (IKNA_SOUND_SAMPLE_RATE * noteMs) / 1000
    val total = stride * (frequencies.size - 1) + noteCount
    val out = DoubleArray(total)
    val attack = (IKNA_SOUND_SAMPLE_RATE * (attackMs / 1000.0)).coerceAtLeast(1.0)
    frequencies.forEachIndexed { idx, hz ->
        val start = idx * stride
        val noteGain = gain * (0.86 + 0.08 * idx)
        for (i in 0 until noteCount) {
            val t = i.toDouble() / IKNA_SOUND_SAMPLE_RATE
            val u = i.toDouble() / noteCount.coerceAtLeast(1)
            val a = if (i < attack) 0.5 * (1.0 - cos(PI * i / attack)) else 1.0
            val env = a * exp(-decay * u) * (1.0 - u)
            // Soft marimba-like fundamental plus a quiet, fast-fading woody overtone.
            val overtoneEnv = exp(-decay * 2.4 * u)
            val wave = sin(2.0 * PI * hz * t) + 0.14 * overtoneEnv * sin(2.0 * PI * hz * 2.0 * t)
            out[start + i] += wave * env * noteGain
        }
    }
    return out
}

/** Smooth one-pole low-pass filter to remove harsh digital sheen. */
private fun lowPass(samples: DoubleArray, cutoffHz: Double): DoubleArray {
    if (samples.isEmpty()) return samples
    val rc = 1.0 / (2.0 * PI * cutoffHz)
    val dt = 1.0 / IKNA_SOUND_SAMPLE_RATE
    val alpha = dt / (rc + dt)
    val out = DoubleArray(samples.size)
    var y = samples[0]
    out[0] = y
    for (i in 1 until samples.size) {
        y += alpha * (samples[i] - y)
        out[i] = y
    }
    return out
}

private fun applyEdgeFades(samples: DoubleArray) {
    val edge = minOf(samples.size / 6, 48)
    if (edge <= 1) return
    for (i in 0 until edge) {
        val factor = 0.5 * (1.0 - cos(PI * i / edge))
        samples[i] *= factor
        samples[samples.size - 1 - i] *= factor
    }
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
