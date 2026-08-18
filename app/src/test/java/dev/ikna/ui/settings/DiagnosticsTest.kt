package dev.ikna.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {
    @Test
    fun `report contains support facts and no private content`() {
        val text = diagnosticsText(
            DiagnosticsSnapshot(
                versionName = "0.8.0 press",
                versionCode = 200080000,
                androidRelease = "15",
                androidSdk = 35,
                abi = "arm64-v8a",
                databaseVersion = 3,
                schedulerVersion = 6,
                interfaceLanguage = "de",
                deckCount = 4,
                activeDeckCount = 2,
                chunkCount = 1200,
                introducedCount = 80,
                knownCount = 25
            )
        )
        assertTrue(text.contains("version=0.8.0 press"))
        assertTrue(text.contains("scheduler=FSRS-6"))
        assertTrue(text.contains("chunks=1200"))
        assertFalse(text.contains("deckName"))
        assertFalse(text.contains("cardText"))
        assertFalse(text.contains("reviewHistory"))
        assertFalse(text.contains("deviceId"))
    }
}
