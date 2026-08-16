package dev.ikna.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File

/**
 * The voice number, which is the one number in this app that can end the process.
 *
 * Kokoro addresses its voices by index. sherpa-onnx checks that index down in
 * C++ and its answer to one past the last voice is to exit, so there is nothing
 * to catch and nothing in a log afterwards -- the app is simply gone, which is
 * what a user reported. The rule these tests hold in place is that a number the
 * runtime has not confirmed never reaches the runtime.
 */
class VoiceNumberTest {

    private fun model(
        speaker: Int,
        speakers: Int = 0,
        rate: Int = DEFAULT_RATE,
    ) = VoiceModelInstall(
        kind = VoiceModelKind.KOKORO,
        name = "kokoro-multi-lang-v1_0",
        lang = null,
        model = "model.int8.onnx",
        speaker = speaker,
        bytes = 88_000_000L,
        dir = File("/nowhere/kokoro-multi-lang-v1_0"),
        speakers = speakers,
        rate = rate,
    )

    @Test
    fun `a number nothing has confirmed is not passed on`() {
        // No load has happened, so the count is unknown. Voice 0 is the one voice
        // every model has, and it is the only one offered until the net answers.
        assertEquals(0, model(speaker = 7).voice)
    }

    @Test
    fun `a number past the last voice is pulled back to the last one`() {
        // This is the manifest of somebody who used the old screen, where "+" had
        // no end: the stored number outlives the version that allowed it.
        assertEquals(10, model(speaker = 40, speakers = 11).voice)
    }

    @Test
    fun `a number inside the range is left exactly as chosen`() {
        assertEquals(3, model(speaker = 3, speakers = 11).voice)
    }

    @Test
    fun `the audio cache follows the voice and the speed, the loaded net does not`() {
        val chosen = model(speaker = 1, speakers = 11)

        // Different rendering, different file: otherwise a card met yesterday
        // keeps playing back in the voice and at the pace just moved away from.
        assertNotEquals(chosen.id, chosen.copy(speaker = 2).id)
        assertNotEquals(chosen.id, chosen.copy(rate = 120).id)

        // The same net, though. Both are arguments to one generate() call, and
        // keying the resident engine by them is what made every tap on "+" free a
        // hundred megabytes of model and read it straight back in.
        assertEquals(chosen.engineKey, chosen.copy(speaker = 2, rate = 120).engineKey)
    }

    @Test
    fun `speed stays inside what the runtime accepts`() {
        assertEquals(1.0f, model(speaker = 0).speed, 0.001f)
        assertEquals(1.2f, model(speaker = 0, rate = 120).speed, 0.001f)
        // A hand-edited manifest is not allowed to produce speech nobody can
        // follow, in either direction.
        assertEquals(0.5f, model(speaker = 0, rate = 5).speed, 0.001f)
        assertEquals(1.5f, model(speaker = 0, rate = 400).speed, 0.001f)
    }
}
