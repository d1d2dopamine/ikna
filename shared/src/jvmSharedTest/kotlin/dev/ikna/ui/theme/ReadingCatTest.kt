package dev.ikna.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingCatTest {
    @Test
    fun artworkRolesFollowEveryLightingAndPreserveAlpha() {
        val custom = IknaPalette(Color(0xFFF0E8DF), Color(0xFF211A31), Color.Gray, Color(0xFF3159AB))
        for (palette in IknaPalettes.flatMap { listOf(it.dark, it.grey) } + custom) {
            val matrix = readingCatColorMatrix(palette.ink, palette.accent, palette.background)
            for ((source, expected) in listOf(
                Color.Black to palette.ink,
                Color.Red to palette.accent,
                Color.White to palette.background
            )) {
                val input = floatArrayOf(source.red * 255f, source.green * 255f, source.blue * 255f, 255f)
                val output = FloatArray(4) { row ->
                    (0..3).sumOf { column -> (matrix[row, column] * input[column]).toDouble() }.toFloat() +
                        matrix[row, 4]
                }
                assertEquals(expected.red * 255f, output[0], 0.001f)
                assertEquals(expected.green * 255f, output[1], 0.001f)
                assertEquals(expected.blue * 255f, output[2], 0.001f)
                assertEquals(255f, output[3], 0.001f)
            }
            assertEquals(1f, matrix[3, 3])
            for (column in listOf(0, 1, 2, 4)) assertEquals(0f, matrix[3, column])
        }
    }
}
