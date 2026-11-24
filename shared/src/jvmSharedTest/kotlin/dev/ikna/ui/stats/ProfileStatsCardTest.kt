package dev.ikna.ui.stats

import androidx.compose.ui.graphics.toArgb
import dev.ikna.ui.theme.paletteSpec
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileStatsCardTest {

    @Test
    fun formatsRecallPercentWhenAtLeastFiveReviewsAndDashOtherwise() {
        val moss = paletteSpec("cobalt").dark
        val base = ProfileStatsCardData(
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
            bgArgb = moss.background.toArgb(),
            panelArgb = moss.panel.toArgb(),
            lineArgb = moss.line.toArgb(),
            inkArgb = moss.ink.toArgb(),
            mutedArgb = moss.muted.toArgb(),
            accentArgb = moss.accent.toArgb()
        )
        assertEquals("90%", base.recallPercentText)
        assertEquals("—", base.copy(repeatAnswers = 4, recalledRepeatAnswers = 4).recallPercentText)
    }
}
