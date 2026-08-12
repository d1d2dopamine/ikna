package dev.ikna.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The folder someone picked, judged without copying it.
 *
 * These are the real folders people end up with: a sherpa release unpacked into
 * its own folder, a Piper voice taken straight from its own project, a download
 * that stopped halfway. Every one of them used to end the same way -- a card that
 * does not talk and no explanation -- so each has a test and each has a sentence.
 */
class VoiceModelTest {

    @Test
    fun kokoroReleaseIsRecognised() {
        val report = VoiceModelLayout.inspect(
            "kokoro-int8-en-v0_19",
            listOf(
                VoiceEntry("model.int8.onnx", false, 92_000_000L),
                VoiceEntry("voices.bin", false, 5_500_000L),
                VoiceEntry("tokens.txt", false, 1_100L),
                VoiceEntry("espeak-ng-data", true),
            ),
        )

        assertTrue(report.usable)
        assertEquals(VoiceModelKind.KOKORO, report.kind)
        assertEquals("model.int8.onnx", report.model)
        assertEquals("en", report.lang)
        assertTrue(report.quantised)
    }

    @Test
    fun piperVoiceIsRecognised() {
        val report = VoiceModelLayout.inspect(
            "vits-piper-ru_RU-dmitri-medium",
            listOf(
                VoiceEntry("ru_RU-dmitri-medium.onnx", false, 63_000_000L),
                VoiceEntry("tokens.txt", false, 900L),
                VoiceEntry("espeak-ng-data", true),
            ),
        )

        assertTrue(report.usable)
        assertEquals(VoiceModelKind.VITS, report.kind)
        assertEquals("ru", report.lang)
        assertFalse(report.quantised)
    }

    @Test
    fun theQuantisedNetIsPreferredWhenSeveralArePresent() {
        // Same voice at three precisions in one folder. The small one is the only
        // one a mid-range phone loads without being killed for memory.
        val report = VoiceModelLayout.inspect(
            "kokoro-multi-lang-v1_0",
            listOf(
                VoiceEntry("model.onnx", false, 310_000_000L),
                VoiceEntry("model.int8.onnx", false, 88_000_000L),
                VoiceEntry("model.fp16.onnx", false, 160_000_000L),
                VoiceEntry("voices.bin", false, 26_000_000L),
                VoiceEntry("tokens.txt", false, 1_200L),
                VoiceEntry("lexicon-zh.txt", false, 6_000_000L),
            ),
        )

        assertEquals("model.int8.onnx", report.model)
    }

    @Test
    fun twoUnrelatedNetsAreRefusedRatherThanGuessedAt() {
        val report = VoiceModelLayout.inspect(
            "voices",
            listOf(
                VoiceEntry("dmitri.onnx", false, 63_000_000L),
                VoiceEntry("irina.onnx", false, 63_000_000L),
                VoiceEntry("tokens.txt", false, 900L),
            ),
        )

        assertFalse(report.usable)
        assertEquals(VoiceModelProblem.TOO_MANY_MODELS, report.problem)
    }

    @Test
    fun theFolderAboveTheModelIsCalledOutSeparately() {
        // The archive was unpacked into its own folder and that is what got picked.
        // The commonest mistake there is, and the fix is one tap -- so it gets its
        // own message instead of "this is not a model".
        val report = VoiceModelLayout.inspect(
            "Download",
            listOf(VoiceEntry("kokoro-int8-en-v0_19", true)),
        )

        assertEquals(VoiceModelProblem.NESTED, report.problem)
    }

    @Test
    fun anOrdinaryFolderIsNotAModel() {
        val report = VoiceModelLayout.inspect(
            "Pictures",
            listOf(
                VoiceEntry("IMG_0001.jpg", false, 2_000_000L),
                VoiceEntry("IMG_0002.jpg", false, 2_000_000L),
            ),
        )

        assertEquals(VoiceModelProblem.NOT_A_MODEL, report.problem)
        assertNull(report.kind)
    }

    @Test
    fun aHalfDownloadedKokoroIsRefused() {
        // The net is there and voices.bin is not: Kokoro cannot say a word without
        // it, and the honest reading is that the download stopped early.
        val report = VoiceModelLayout.inspect(
            "kokoro-en-v0_19",
            listOf(
                VoiceEntry("model.onnx", false, 330_000_000L),
                VoiceEntry("tokens.txt", false, 1_100L),
                VoiceEntry("espeak-ng-data", true),
            ),
        )

        assertEquals(VoiceModelProblem.NO_VOICES, report.problem)
    }

    @Test
    fun aRawPiperDownloadWithoutTokensIsRefused() {
        val report = VoiceModelLayout.inspect(
            "pl_PL-gosia-medium",
            listOf(
                VoiceEntry("pl_PL-gosia-medium.onnx", false, 63_000_000L),
                VoiceEntry("pl_PL-gosia-medium.onnx.json", false, 4_000L),
            ),
        )

        assertEquals(VoiceModelProblem.NO_TOKENS, report.problem)
    }

    @Test
    fun nothingToTurnLettersIntoSoundsIsRefused() {
        val report = VoiceModelLayout.inspect(
            "vits-something",
            listOf(
                VoiceEntry("model.onnx", false, 40_000_000L),
                VoiceEntry("tokens.txt", false, 800L),
            ),
        )

        assertEquals(VoiceModelProblem.NO_PHONEMES, report.problem)
    }

    @Test
    fun aLexiconCountsInsteadOfEspeakData() {
        val report = VoiceModelLayout.inspect(
            "vits-zh-something",
            listOf(
                VoiceEntry("model.onnx", false, 40_000_000L),
                VoiceEntry("tokens.txt", false, 800L),
                VoiceEntry("lexicon.txt", false, 2_000_000L),
            ),
        )

        assertTrue(report.usable)
    }

    @Test
    fun aMultiLanguageReleaseNamesNoLanguageAndIsNotGuessedAt() {
        val report = VoiceModelLayout.inspect(
            "kokoro-multi-lang-v1_0",
            listOf(
                VoiceEntry("model.onnx", false, 310_000_000L),
                VoiceEntry("voices.bin", false, 26_000_000L),
                VoiceEntry("tokens.txt", false, 1_200L),
                VoiceEntry("espeak-ng-data", true),
            ),
        )

        assertTrue(report.usable)
        assertNull(report.lang)
    }

    @Test
    fun theLanguageIsReadFromTheNameAndNotFromItsNumbers() {
        assertEquals("pl", VoiceModelLayout.languageOf("vits-piper-pl_PL-gosia-medium"))
        assertEquals("en", VoiceModelLayout.languageOf("kokoro-int8-en-v0_19"))
        assertNull(VoiceModelLayout.languageOf("kokoro-int8-multi-lang-v1_1"))
        assertNull(VoiceModelLayout.languageOf("model-v0_19"))
    }

    @Test
    fun theTopLevelSizeIsReportedForThePreview() {
        // espeak-ng-data holds thousands of tiny files and is not walked for a
        // preview: the number on screen is the weights, which is what is asked.
        val report = VoiceModelLayout.inspect(
            "kokoro-int8-en-v0_19",
            listOf(
                VoiceEntry("model.int8.onnx", false, 92_000_000L),
                VoiceEntry("voices.bin", false, 5_500_000L),
                VoiceEntry("tokens.txt", false, 1_100L),
                VoiceEntry("espeak-ng-data", true, 40_000_000L),
            ),
        )

        assertEquals(97_501_100L, report.bytes)
    }
}
