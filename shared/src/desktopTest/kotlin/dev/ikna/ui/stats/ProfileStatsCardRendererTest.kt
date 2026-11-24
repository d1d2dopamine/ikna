package dev.ikna.ui.stats

import androidx.compose.ui.graphics.toArgb
import dev.ikna.ui.theme.paletteSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfileStatsCardRendererTest {

    @Test
    fun rendersValid1200x720PngInMossAndZeroPalettes() {
        for (paletteId in listOf("cobalt", "zero")) {
            val palette = paletteSpec(paletteId).dark
            val data = ProfileStatsCardData(
                profileName = "d1d2dopamine",
                headerText = "IKNA / LOCAL STUDY SNAPSHOT",
                subtitle = "Moss · 30 days",
                periodLabel = "30 days",
                generatedDate = "2026-11-24",
                targets = 142,
                firstRecorded = 38,
                answers = 610,
                repeatAnswers = 420,
                recalledRepeatAnswers = 378,
                recoveredCount = 19,
                labelTargets = "Targets practiced",
                labelNew = "First recorded",
                labelAnswers = "Total answers recorded",
                labelRecall = "Recalled on review",
                labelRecallLowSample = "Need 5 reviews",
                footerText = "ikna · local-first · 2026-11-24",
                recoveredText = "recovered: 19",
                bgArgb = palette.background.toArgb(),
                panelArgb = palette.panel.toArgb(),
                lineArgb = palette.line.toArgb(),
                inkArgb = palette.ink.toArgb(),
                mutedArgb = palette.muted.toArgb(),
                accentArgb = palette.accent.toArgb()
            )
            val png = renderProfileStatsCardPng(data)
            assertTrue(png.size > 4_000, "Rendered PNG should contain full card artwork")
            assertEquals(0x89.toByte(), png[0])
            assertEquals('P'.code.toByte(), png[1])
            assertEquals('N'.code.toByte(), png[2])
            assertEquals('G'.code.toByte(), png[3])
            val width = ((png[16].toInt() and 0xFF) shl 24) or ((png[17].toInt() and 0xFF) shl 16) or
                ((png[18].toInt() and 0xFF) shl 8) or (png[19].toInt() and 0xFF)
            val height = ((png[20].toInt() and 0xFF) shl 24) or ((png[21].toInt() and 0xFF) shl 16) or
                ((png[22].toInt() and 0xFF) shl 8) or (png[23].toInt() and 0xFF)
            assertEquals(1200, width)
            assertEquals(720, height)
        }
    }
}
