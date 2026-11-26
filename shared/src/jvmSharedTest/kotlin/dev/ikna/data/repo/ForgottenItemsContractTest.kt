package dev.ikna.data.repo

import dev.ikna.data.catalog.CatalogDeck
import dev.ikna.data.catalog.catalogSha256Hex
import dev.ikna.data.catalog.decodeCatalogDeckBytes
import dev.ikna.domain.grading.INPUT_KEYBOARD
import dev.ikna.domain.session.ReviewSignalTracker
import dev.ikna.domain.session.TimingDiscardReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ForgottenItemsContractTest {

    @Test
    fun `pre-reveal retrieval latency is captured and TTS excludes timing`() {
        var nowMs = 1_000L
        val tracker = ReviewSignalTracker(nowNanos = { nowMs * 1_000_000L })
        tracker.shown()
        nowMs += 1_450L
        tracker.reveal(INPUT_KEYBOARD)
        nowMs += 600L
        tracker.keyboardStarted()
        val clean = tracker.snapshot(inputMethod = INPUT_KEYBOARD)
        assertEquals(1_450L, clean.retrievalLatencyMs)
        assertEquals(2_050L, clean.latencyMs)
        assertNull(clean.timingDiscardReason)

        val spokenTracker = ReviewSignalTracker(nowNanos = { nowMs * 1_000_000L })
        spokenTracker.shown()
        nowMs += 900L
        spokenTracker.speechStarted()
        spokenTracker.reveal(INPUT_KEYBOARD)
        nowMs += 500L
        spokenTracker.keyboardStarted()
        val spoken = spokenTracker.snapshot(inputMethod = INPUT_KEYBOARD)
        assertEquals(TimingDiscardReason.TTS_ACTIVE, spoken.timingDiscardReason)
        assertEquals(900L, spoken.retrievalLatencyMs)
    }

    @Test
    fun `catalog deck decode verifies SHA-256 digest when published and rejects corruption`() {
        val payload = "{\"id\":\"en-1\",\"text\":\"hello\",\"translation\":\"привет\"}\n".toByteArray(Charsets.UTF_8)
        val validDigest = catalogSha256Hex(payload)
        val deckWithHash = CatalogDeck(
            id = "en-test",
            title = "English",
            lang = "en",
            meaningLang = "ru",
            file = "en-test.jsonl",
            sha256 = validDigest
        )
        assertNotNull(decodeCatalogDeckBytes(payload, deckWithHash))

        val tampered = payload.copyOf().also { it[0] = 'X'.code.toByte() }
        assertNull(decodeCatalogDeckBytes(tampered, deckWithHash))

        val legacyWithoutHash = deckWithHash.copy(sha256 = "")
        assertNotNull(decodeCatalogDeckBytes(payload, legacyWithoutHash))
    }
}
