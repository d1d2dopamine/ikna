package dev.ikna.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.compositeOver
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Production Compose Color arithmetic, in addition to the offline source audit. */
class ControlColorsTest {
    private fun eachPalette(block: (String, IknaPalette) -> Unit) {
        IknaPalettes.forEach { spec ->
            block(spec.id + " dark", spec.dark)
            block(spec.id + " light", spec.light)
        }
    }

    private fun surfaces(p: IknaPalette, c: IknaControlColors) =
        listOf(p.background, c.idle, c.fill, c.hover, c.pressed)

    @Test
    fun enabledLabelsStayReadableInEveryControlState() {
        eachPalette { name, p ->
            val c = controlColors(p)
            surfaces(p, c).forEach { surface ->
                listOf(c.label, c.quietLabel).forEach { label ->
                    assertTrue(name + " label", contrastRatio(label, surface) >= MIN_READABLE_CONTRAST)
                }
            }
        }
    }

    @Test
    fun boundariesAndSmallThumbsRemainRecognisable() {
        eachPalette { name, p ->
            val c = controlColors(p)
            surfaces(p, c).forEach { surface ->
                assertTrue(name + " outline", contrastRatio(c.outline, surface) >= 3.0)
                assertTrue(name + " thumb", contrastRatio(c.mark, surface) >= 3.0)
            }
            assertTrue(name + " thumb must not be the idle fill", c.mark != c.idle)
        }
    }

    @Test
    fun darkControlSurfacesAreNotInvertedInk() {
        IknaPalettes.forEach { spec ->
            val p = spec.dark
            val c = controlColors(p)
            surfaces(p, c).forEach { surface ->
                assertTrue(spec.id + " luminous control", relativeLuminance(surface) < 0.06)
                assertTrue(spec.id + " large fill contrast", contrastRatio(surface, p.background) <= 1.8)
                assertTrue(spec.id + " ink fill", surface != p.ink)
            }
            assertTrue(spec.id + " near-white thumb", relativeLuminance(c.mark) < 0.25)
        }
    }

    @Test
    fun lightLightingIsGreyAcrossEveryPalette() {
        IknaPalettes.forEach { spec ->
            val p = spec.light
            assertTrue(spec.id, relativeLuminance(p.background) in 0.55..0.70)
            assertTrue(spec.id + " status icons", p.light)
            val spread = maxOf(p.background.red, p.background.green, p.background.blue) -
                minOf(p.background.red, p.background.green, p.background.blue)
            assertTrue(spec.id + " saturated paper", spread < 0.09f)
            surfaces(p, controlColors(p)).forEach { surface ->
                assertTrue(spec.id + " white control", relativeLuminance(surface) <= 0.70)
            }
        }
    }

    @Test
    fun lightPanelLabelsKeepTheirReadabilityAfterDimmingThePaper() {
        IknaPalettes.forEach { spec ->
            val p = spec.light
            listOf(p.ink, p.muted, p.accent).forEach { color ->
                assertTrue(spec.id, contrastRatio(color, p.panel) >= MIN_READABLE_CONTRAST)
            }
        }
    }

    @Test
    fun hoverAndPressColourInterpolationDoesNotHideTheLabel() {
        eachPalette { name, p ->
            val c = controlColors(p)
            val states = listOf(c.fill.copy(alpha = 0f), p.background, c.idle, c.fill, c.hover, c.pressed)
            states.forEach { a ->
                states.forEach { b ->
                    for (step in 0..10) {
                        val surface = lerp(a, b, step / 10f).compositeOver(p.background)
                        assertTrue(name, contrastRatio(c.label, surface) >= MIN_READABLE_CONTRAST)
                        assertTrue(name + " quiet", contrastRatio(c.quietLabel, surface) >= MIN_READABLE_CONTRAST)
                    }
                }
            }
        }
    }

    @Test
    fun validCustomColoursAreNotOverwrittenAndDoNotLoseContrast() {
        listOf(
            listOf("102030", "F0F0F0", "40A0FF"),
            listOf("D2D2D2", "242424", "343434"),
            listOf("FFFFFF", "767676", "FF0000"),
            listOf("000000", "757575", "FFFFFF"),
            listOf("888888", "111111", "222222"),
            listOf("FFEEEE", "501020", "772244")
        ).forEach { values ->
            val (background, ink, accent) = values.map { parseHexColor(it)!! }
            val p = IknaPalette(background, ink, ink, accent)
            val c = controlColors(p)
            assertEquals(ink, c.label)
            assertEquals(background, p.background)
            surfaces(p, c).forEach { surface ->
                assertTrue(values.toString(), contrastRatio(c.label, surface) >= MIN_READABLE_CONTRAST)
            }
        }
    }

    @Test
    fun lightAndSystemModesSelectTheNewGreyWithoutChangingIdentity() {
        IknaPalettes.forEach { spec ->
            val settings = IknaSettings(paletteId = spec.id, theme = ThemeMode.LIGHT)
            assertEquals(spec.light, paletteFor(settings, systemDark = true))
            assertEquals(spec.light, paletteFor(settings.copy(theme = ThemeMode.SYSTEM), systemDark = false))
            assertEquals(spec.dark, paletteFor(settings.copy(theme = ThemeMode.DARK), systemDark = false))
        }
        val zero = paletteSpec("zero").light.background
        assertEquals(zero.red, zero.green, 0f)
        assertEquals(zero.red, zero.blue, 0f)
        assertTrue(zero != Color.White)
    }
}
