package dev.ikna.ui.theme

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.EnumMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private object AndroidSoundEngine {
    private val cache: Map<IknaSoundCue, ByteArray> by lazy {
        EnumMap<IknaSoundCue, ByteArray>(IknaSoundCue::class.java).apply {
            IknaSoundCue.entries.forEach { put(it, synthesizeIknaCuePcm16Le(it)) }
        }
    }
    private val lastTapAt = AtomicLong(0L)
    private val pendingCue = AtomicReference<IknaSoundCue?>(null)
    private val workerActive = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ikna-ui-sound").apply { isDaemon = true }
    }

    fun play(cue: IknaSoundCue) {
        if (!iknaUiSoundsLiveGate.get()) {
            pendingCue.set(null)
            return
        }
        if (cue == IknaSoundCue.TAP && !tryAcquireTapSlot()) return
        pendingCue.set(cue)
        if (workerActive.compareAndSet(false, true)) {
            executor.execute(::drainLatest)
        }
    }

    private fun tryAcquireTapSlot(): Boolean {
        val now = System.nanoTime()
        while (true) {
            val prev = lastTapAt.get()
            if (now - prev < 18_000_000L) return false
            if (lastTapAt.compareAndSet(prev, now)) return true
        }
    }

    private fun drainLatest() {
        try {
            while (true) {
                val cue = pendingCue.getAndSet(null) ?: break
                if (!iknaUiSoundsLiveGate.get()) {
                    pendingCue.set(null)
                    break
                }
                playNow(cue)
            }
        } finally {
            workerActive.set(false)
            if (pendingCue.get() != null && iknaUiSoundsLiveGate.get() && workerActive.compareAndSet(false, true)) {
                executor.execute(::drainLatest)
            }
        }
    }

    private fun playNow(cue: IknaSoundCue) {
        runCatching {
            val pcm = cache[cue] ?: return
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(IKNA_SOUND_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(pcm.size.coerceAtLeast(2))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            try {
                track.write(pcm, 0, pcm.size)
                track.play()
                val durationMs = (pcm.size / 2L * 1000L) / IKNA_SOUND_SAMPLE_RATE
                Thread.sleep(durationMs + 25L)
            } finally {
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        }
    }
}

actual fun playIknaSoundCue(cue: IknaSoundCue) {
    AndroidSoundEngine.play(cue)
}
