package dev.ikna.ui.theme

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.SystemClock
import java.util.EnumMap
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private object AndroidSoundEngine {
    private const val BYTES_PER_FRAME = 2
    private const val IDLE_MS = 3_000L
    private const val STALL_MS = 5_000L
    private const val TAIL_FRAMES = IKNA_SOUND_SAMPLE_RATE * 110 / 1_000
    private val silence = ByteArray(IKNA_SOUND_SAMPLE_RATE / 100 * BYTES_PER_FRAME)
    private val cache: Map<IknaSoundCue, ByteArray> by lazy {
        EnumMap<IknaSoundCue, ByteArray>(IknaSoundCue::class.java).apply {
            IknaSoundCue.entries.forEach { put(it, synthesizeIknaCuePcm16Le(it)) }
        }
    }
    private val lastTapAt = AtomicLong(0L)
    private val queue = LinkedBlockingQueue<IknaSoundCue>(4)
    private val workerActive = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ikna-ui-sound").apply { isDaemon = true }
    }

    // The worker owns the track, including pause, flush and recovery.
    private var track: AudioTrack? = null

    fun play(cue: IknaSoundCue) {
        if (!iknaUiSoundsLiveGate.get()) {
            queue.clear()
            return
        }
        if (cue == IknaSoundCue.TAP && !tryAcquireTapSlot()) return
        // Keep recent cues if taps arrive faster than playback.
        while (!queue.offer(cue)) {
            queue.poll()
        }
        if (workerActive.compareAndSet(false, true)) {
            executor.execute(::runSession)
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

    private fun runSession() {
        try {
            if (!iknaUiSoundsLiveGate.get()) {
                queue.clear()
                return
            }
            val cue = queue.poll() ?: return
            runCatching {
                val output = track ?: createTrack().also { track = it }
                check(output.state == AudioTrack.STATE_INITIALIZED)
                stream(output, cue)
                output.pause()
                output.flush()
            }.onFailure {
                track?.let { output -> runCatching { output.release() } }
                track = null
            }
        } finally {
            workerActive.set(false)
            if (queue.isNotEmpty() && iknaUiSoundsLiveGate.get() && workerActive.compareAndSet(false, true)) {
                executor.execute(::runSession)
            }
        }
    }

    private fun stream(output: AudioTrack, firstCue: IknaSoundCue) {
        var pcm: ByteArray? = cache.getValue(firstCue)
        var offset = 0
        var writtenFrames = 0L
        var lastCueEnd = 0L
        var playedFrames = 0L
        var lastCueAt = SystemClock.elapsedRealtime()
        var lastProgressAt = lastCueAt
        output.play()
        while (iknaUiSoundsLiveGate.get()) {
            val now = SystemClock.elapsedRealtime()
            val head = output.playbackHeadPosition.toUInt().toLong()
            if (head != playedFrames) {
                playedFrames = head
                lastProgressAt = now
            }
            check(now - lastProgressAt < STALL_MS) { "UI sound playback stalled" }

            if (pcm == null) {
                // A pending cue takes the next write slot ahead of unwritten silence.
                queue.poll()?.let {
                    pcm = cache.getValue(it)
                    lastCueAt = now
                }
                if (pcm == null && now - lastCueAt >= IDLE_MS && head >= lastCueEnd + TAIL_FRAMES) {
                    return
                }
            }

            // Silence primes the stream for short cues and keeps it warm for
            // subsequent taps. Native buffering bounds the already queued silence.
            val samples = pcm ?: silence
            val written = output.write(samples, offset, samples.size - offset, AudioTrack.WRITE_NON_BLOCKING)
            check(written >= 0) { "UI sound write failed ($written)" }
            writtenFrames += written / BYTES_PER_FRAME
            if (pcm != null) {
                offset += written
                if (offset == samples.size) {
                    lastCueEnd = writtenFrames
                    pcm = null
                    offset = 0
                }
            }
            if (written == 0) Thread.sleep(5)
        }
        queue.clear()
    }

    private fun createTrack(): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            IKNA_SOUND_SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        check(minBuf > 0)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
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
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                }
            }
            .build()
    }
}

actual fun playIknaSoundCue(cue: IknaSoundCue) {
    AndroidSoundEngine.play(cue)
}
