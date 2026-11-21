package dev.ikna.ui.theme

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IknaSoundFeedbackTest {

    @Test
    fun everyCueSynthesizesBoundedClickFreePcm() {
        for (cue in IknaSoundCue.entries) {
            val bytes = synthesizeIknaCuePcm16Le(cue)
            assertTrue(bytes.size >= 200, "Cue $cue should produce non-trivial PCM")
            assertEquals(0, bytes.size % 2, "Cue $cue PCM must be 16-bit aligned")
            assertContentEquals(bytes, synthesizeIknaCuePcm16Le(cue), "Cue $cue must be deterministic")

            val sampleCount = bytes.size / 2
            var peak = 0
            fun sampleAt(index: Int): Int {
                val lo = bytes[index * 2].toInt() and 0xFF
                val hi = bytes[index * 2 + 1].toInt()
                return (hi shl 8) or lo
            }
            for (i in 0 until sampleCount) {
                peak = maxOf(peak, abs(sampleAt(i)))
            }
            assertTrue(peak in 500..16_000, "Cue $cue peak $peak should be audible yet restrained")
            assertTrue(abs(sampleAt(0)) < 50, "Cue $cue should start quietly to avoid clicks")
            assertTrue(abs(sampleAt(sampleCount - 1)) < 50, "Cue $cue should end quietly to avoid clicks")
        }
    }
}
