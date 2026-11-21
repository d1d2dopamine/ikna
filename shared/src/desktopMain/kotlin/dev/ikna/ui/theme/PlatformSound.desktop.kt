package dev.ikna.ui.theme

import java.util.EnumMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

private object DesktopSoundEngine {
    private val format = AudioFormat(IKNA_SOUND_SAMPLE_RATE.toFloat(), 16, 1, true, false)
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
            val line = AudioSystem.getSourceDataLine(format)
            try {
                line.open(format, pcm.size.coerceAtLeast(1024))
                line.start()
                line.write(pcm, 0, pcm.size)
                line.drain()
            } finally {
                runCatching { line.stop() }
                runCatching { line.close() }
            }
        }
    }
}

actual fun playIknaSoundCue(cue: IknaSoundCue) {
    DesktopSoundEngine.play(cue)
}
