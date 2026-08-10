package dev.ikna.ui.theme

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * The one bitmap in the interface, checked as a file.
 *
 * IknaWordmark draws the accent square over the i itself and relies on two things
 * about the asset it draws it on: that the mark has the proportions the composable
 * sizes it by, and that the space the square lands in is empty. Both are
 * properties of a PNG produced by a tool, which means both are one careless
 * re-export away from being wrong — and neither would throw. A stale aspect ratio
 * puts the square next to the letter instead of over it; a dot left in the bitmap
 * puts a cream square under a coloured one, which on a dark palette reads as a
 * halo and on a light one as a mistake.
 *
 * Nothing here can be checked on a device, because by the time it is on a device
 * it is a texture. It can be checked here, in the seconds before a release.
 */
private const val ASSET = "src/main/res/drawable-nodpi/ikna_wordmark.png"

/** Anything above this is a pixel that would be visible once tinted. */
private const val VISIBLE_ALPHA = 40

class WordmarkTest {

	@Test
	fun `the asset the wordmark is drawn from is on disk`() {
		assertNotNull(
			"app/$ASSET is missing. The wordmark is the letterforms of the logo as " +
				"drawn; it is never traced into paths, so without this file there is " +
				"no mark to draw.",
			asset()
		)
	}

	/**
	 * The composable takes a height and works the width out from WORDMARK_ASPECT.
	 * If the file disagrees, the letters are stretched and the square drifts away
	 * from the stem it belongs to.
	 */
	@Test
	fun `the asset has the proportions the composable sizes it by`() {
		val image = ImageIO.read(asset() ?: return)
		val actual = image.width.toFloat() / image.height.toFloat()
		assertTrue(
			"The asset is " + image.width + "x" + image.height + ", an aspect of " +
				actual + ", but WORDMARK_ASPECT is " + WORDMARK_ASPECT + ". One of " +
				"the two was changed without the other.",
			kotlin.math.abs(actual - WORDMARK_ASPECT) < 0.01f
		)
	}

	/**
	 * The dot is drawn by the app, in the accent colour, which only works if it was
	 * taken out of the bitmap first.
	 */
	@Test
	fun `the space the accent square lands in is empty`() {
		val image = ImageIO.read(asset() ?: return)
		val x0 = (image.width * WORDMARK_DOT_LEFT).toInt()
		val y0 = (image.height * WORDMARK_DOT_TOP).toInt()
		val x1 = (image.width * (WORDMARK_DOT_LEFT + WORDMARK_DOT_WIDTH)).toInt()
		val y1 = (image.height * (WORDMARK_DOT_TOP + WORDMARK_DOT_HEIGHT)).toInt()
		var worst = 0
		for (y in y0 until y1) {
			for (x in x0 until x1) {
				val alpha = (image.getRGB(x, y) ushr 24) and 0xFF
				if (alpha > worst) worst = alpha
			}
		}
		assertTrue(
			"The dot is still in the asset: the strongest pixel where the accent " +
				"square goes has alpha " + worst + ". The app would draw its square " +
				"on top of the drawn one, leaving a rim of the original colour.",
			worst == 0
		)
	}

	/** And the erase took the dot only. */
	@Test
	fun `the letters survived`() {
		val image = ImageIO.read(asset() ?: return)
		var visible = 0
		for (y in 0 until image.height) {
			for (x in 0 until image.width) {
				if (((image.getRGB(x, y) ushr 24) and 0xFF) > VISIBLE_ALPHA) visible++
			}
		}
		val fraction = visible.toFloat() / (image.width * image.height).toFloat()
		assertTrue(
			"Only " + visible + " pixels of the asset are visible, " + fraction +
				" of it. Four letters cover far more than that: the file is blank, " +
				"cropped to nothing, or was saved without its alpha channel.",
			fraction > 0.2f
		)
	}

	/**
	 * Unit tests run from the module directory in one place and the project root in
	 * another, and this file has to be found in both. Same approach as SchemaTest.
	 */
	private fun asset(): File? =
		listOf(File(ASSET), File("app/$ASSET")).firstOrNull { it.isFile }
}
