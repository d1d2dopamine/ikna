package dev.ikna.ui.theme

import androidx.compose.ui.graphics.Color
import dev.ikna.data.prefs.DEFAULT_PALETTE_ID
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The palettes, checked as numbers.
 *
 * A palette is the one kind of change that looks finished the moment it looks
 * good on the screen it was designed on. It is not: the same four colours have to
 * hold up as small monospaced labels, in the other lighting, and next to the one
 * control that must never be misread. Everything asserted here is something the
 * eye approves of and the arithmetic does not.
 *
 * MIN_READABLE_CONTRAST is 4.5:1, the WCAG line for body text. The accent is held
 * to it too, and deliberately: it is not decoration, it carries the day's number
 * *and* the small mono line under a deck title.
 */
class PaletteTest {

	@Test
	fun `the chooser contains twelve authored palettes`() {
		assertEquals(12, IknaPalettes.size)
		assertEquals("ink", DEFAULT_PALETTE_ID)
		assertEquals(listOf("ink", "library", "ember"), IknaPalettes.take(3).map { it.id })
		assertEquals(
			listOf("ultraviolet", "lagoon", "cobalt"),
			IknaPalettes.takeLast(3).map { it.id }
		)
	}

	@Test
	fun `ultraviolet is actually a purple palette`() {
		val ultraviolet = paletteSpec("ultraviolet")
		listOf(ultraviolet.dark.accent, ultraviolet.grey.accent).forEach { accent ->
			val (hue, saturation) = hueAndSaturation(accent)
			assertTrue("ultraviolet hue drifted to $hue", hue in 250f..290f)
			val minimum = if (accent == ultraviolet.dark.accent) 0.35f else 0.25f
			assertTrue("ultraviolet became grey", saturation > minimum)
		}
	}

	@Test
	fun `every palette is readable in both lightings`() {
		IknaPalettes.forEach { spec ->
			listOf("dark" to spec.dark, "grey" to spec.grey).forEach { (lighting, p) ->
				val where = spec.id + " " + lighting + " "
				assertReadable(where + "ink", p.ink, p.background)
				assertReadable(where + "muted", p.muted, p.background)
				assertReadable(where + "accent", p.accent, p.background)
				assertReadable(where + "danger", dangerFor(p), p.background)
			}
		}
	}

	/**
	 * The rule that makes grey and dark one app rather than two: both are dark
	 * rooms, the grey one a step lighter, with coloured accents and free of
	 * white surfaces. The bounds keep the system-bar choice unambiguous.
	 */
	@Test
	fun `each lighting is the lighting it claims to be`() {
		IknaPalettes.forEach { spec ->
			assertFalse(spec.id + " grey version reads as light", spec.grey.light)
			assertTrue(spec.id + " dark version reads as dark", !spec.dark.light)
			assertTrue(
				spec.id + " grey background must sit between near black and light",
				relativeLuminance(spec.grey.background) in 0.012..0.05
			)
			assertTrue(
				spec.id + " dark background is near black",
				relativeLuminance(spec.dark.background) < 0.013
			)
		}
	}

	@Test
	fun `grey lighting keeps each coloured accent recognisable`() {
		IknaPalettes.filter { it.id != "zero" }.forEach { spec ->
			val (darkHue, _) = hueAndSaturation(spec.dark.accent)
			val (greyHue, saturation) = hueAndSaturation(spec.grey.accent)
			val difference = kotlin.math.abs(darkHue - greyHue)
			assertTrue(spec.id + " hue", minOf(difference, 360f - difference) < 8f)
			assertTrue(spec.id + " saturation", saturation > 0.25f)
		}
		assertEquals(12, IknaPalettes.map { it.grey.accent }.distinct().size)
	}

	/**
	 * Recognition is the whole reason these exist, so no two of them are allowed to
	 * be the same colour with a different name.
	 */
	@Test
	fun `no two palettes are the same palette`() {
		val ids = IknaPalettes.map { it.id }
		assertEquals("duplicate palette ids", ids.distinct().size, ids.size)

		val names = IknaPalettes.map { it.nameKey }
		assertEquals("duplicate name keys", names.distinct().size, names.size)
		assertTrue("a palette has no name", names.none { it.isBlank() })

		val backgrounds = IknaPalettes.map { it.dark.background }
		assertEquals("two palettes share a dark background", backgrounds.distinct().size, backgrounds.size)
	}

	/**
	 * The default has to exist, and an id from a build that is not this one has to
	 * land on it rather than throw. A palette dropped in a future version would
	 * otherwise crash on the first frame after the update — with no screen left to
	 * change the setting from.
	 */
	@Test
	fun `an unknown palette resolves to the default`() {
		assertEquals(DEFAULT_PALETTE_ID, paletteSpec(DEFAULT_PALETTE_ID).id)
		assertEquals(DEFAULT_PALETTE_ID, paletteSpec("a palette from 2029").id)
		assertEquals(DEFAULT_PALETTE_ID, paletteSpec("").id)
		assertEquals(DefaultPaletteSpec.dark, DarkPalette)
		assertEquals(DefaultPaletteSpec.grey, GreyPalette)
	}

	/**
	 * Two independent choices: which palette, and how it is lit. The mode must not
	 * be able to change the palette, and only SYSTEM is allowed to look at the
	 * phone at all.
	 */
	@Test
	fun `the mode chooses the lighting and the palette chooses the colours`() {
		val settings = IknaSettings(paletteId = "plum")
		val plum = paletteSpec("plum")

		assertEquals(plum.dark, paletteFor(settings.copy(theme = ThemeMode.DARK), systemDark = false))
		assertEquals(plum.grey, paletteFor(settings.copy(theme = ThemeMode.GREY), systemDark = true))
		assertEquals(plum.dark, paletteFor(settings.copy(theme = ThemeMode.SYSTEM), systemDark = true))
		assertEquals(plum.grey, paletteFor(settings.copy(theme = ThemeMode.SYSTEM), systemDark = false))
	}

	/** A custom scheme still wins over the palette, whatever is stored next to it. */
	@Test
	fun `four colours of your own outrank the palette`() {
		val settings = IknaSettings(
			theme = ThemeMode.CUSTOM,
			paletteId = "zero",
			customBackground = 0xFF102030.toInt(),
			customInk = 0xFFF0F0F0.toInt(),
			customMuted = 0xFF909090.toInt(),
			customAccent = 0xFF40A0FF.toInt()
		)
		assertEquals(Color(0xFF102030), paletteFor(settings).background)
	}

	/**
	 * Danger is the one meaning colour is not allowed to carry alone, and this is
	 * where that is enforced: when the accent is close enough to the warning red to
	 * be mistaken for it at a glance, the warning stops being red and the word and
	 * the frame do the work.
	 */
	@Test
	fun `danger steps aside when the accent is already that colour`() {
		val ember = paletteSpec("ember")
		assertEquals(ember.dark.ink, dangerFor(ember.dark))

		// Warm accents retain their warning guard in both lightings.
		val plum = paletteSpec("plum")
		assertTrue(dangerFor(plum.dark) != plum.dark.ink)
		assertEquals(ember.grey.ink, dangerFor(ember.grey))
		assertTrue(dangerFor(plum.grey) != plum.grey.ink)
	}

	/**
	 * The old single #B44A34 is what this replaces. It was 3.5:1 on every dark
	 * background the app has ever shipped — the least readable colour in the
	 * product, on the one button that cannot be undone.
	 */
	@Test
	fun `the colour it replaced was the unreadable one`() {
		val old = Color(0xFFB44A34)
		IknaPalettes.forEach { spec ->
			val ratio = contrastRatio(old, spec.dark.background)
			assertTrue(
				spec.id + " would have been fine after all: " + ratioText(ratio),
				ratio < MIN_READABLE_CONTRAST
			)
		}
	}

	@Test
	fun `hue is measured the way a colour wheel is drawn`() {
		assertEquals(0f, hueAndSaturation(Color(0xFFFF0000)).first, 0.5f)
		assertEquals(120f, hueAndSaturation(Color(0xFF00FF00)).first, 0.5f)
		assertEquals(240f, hueAndSaturation(Color(0xFF0000FF)).first, 0.5f)

		// Grey has no hue and, more importantly, no saturation — which is what keeps
		// a monochrome palette from being treated as a red one.
		val grey = hueAndSaturation(Color(0xFF888888))
		assertEquals(0f, grey.second, 0.001f)
		assertTrue(!clashesWithDanger(Color(0xFF888888)))
		assertTrue(!clashesWithDanger(Color(0xFFFFFFFF)))

		// Warm oranges and reds do clash; brass and mint do not.
		assertTrue(clashesWithDanger(Color(0xFFF2683C)))
		assertTrue(clashesWithDanger(Color(0xFFFF7A5C)))
		assertTrue(!clashesWithDanger(Color(0xFFE3B45E)))
		assertTrue(!clashesWithDanger(Color(0xFF45D6A6)))
	}

	private fun assertReadable(what: String, fg: Color, bg: Color) {
		val ratio = contrastRatio(fg, bg)
		assertTrue(
			what + " is " + ratioText(ratio) + ", below " + MIN_READABLE_CONTRAST,
			ratio >= MIN_READABLE_CONTRAST
		)
	}
}
