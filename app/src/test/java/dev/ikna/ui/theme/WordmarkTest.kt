package dev.ikna.ui.theme

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The one bitmap in the interface, checked as a file.
 *
 * IknaWordmark draws the accent square over the i itself and sizes the mark from
 * WORDMARK_ASPECT, so a re-export at different proportions puts the square next to
 * the letter instead of over it. Nothing would throw; the mark would simply be
 * wrong in every screenshot from then on.
 *
 * The header is read by hand rather than through an image library. Unit tests in
 * an Android module compile against android.jar, which shadows the JDK and does
 * not carry javax.imageio, so anything that decodes an image here fails to compile
 * on CI while looking perfectly fine in an ordinary JVM project.
 */
private const val ASSET = "src/main/res/drawable-nodpi/ikna_wordmark.png"
private const val ONBOARDING = "src/main/java/dev/ikna/ui/onboarding/OnboardingScreen.kt"

/** PNG signature, then the IHDR chunk: width and height are its first eight bytes. */
private val PNG_MAGIC = byteArrayOf(
	0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
)

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

	@Test
	fun `the first launch uses the wordmark artwork not typed letters`() {
		val source = onboardingSource()?.readText() ?: return
		assertTrue("Onboarding no longer draws IknaWordmark", "IknaWordmark(" in source)
		assertTrue("Onboarding fell back to typed ikna", "text = \"ikna\"" !in source)
	}

	@Test
	fun `the asset is a png and not a renamed something else`() {
		val bytes = (asset() ?: return).readBytes()
		assertTrue(
			"The asset does not start with the PNG signature. A JPEG or a WebP saved " +
				"under a .png name loads on some Android versions and not others.",
			bytes.size > 24 && bytes.copyOfRange(0, 8).contentEquals(PNG_MAGIC)
		)
	}

	/**
	 * The composable takes a height and works the width out from WORDMARK_ASPECT.
	 * If the file disagrees, the letters are stretched and the square drifts away
	 * from the stem it belongs to.
	 */
	@Test
	fun `the asset has the proportions the composable sizes it by`() {
		val bytes = (asset() ?: return).readBytes()
		val width = intAt(bytes, 16)
		val height = intAt(bytes, 20)
		assertTrue(
			"The header reads " + width + "x" + height + ", which is not a size any " +
				"export would produce.",
			width > 0 && height > 0
		)
		val actual = width.toFloat() / height.toFloat()
		assertTrue(
			"The asset is " + width + "x" + height + ", an aspect of " + actual +
				", but WORDMARK_ASPECT is " + WORDMARK_ASPECT + ". One of the two was " +
				"changed without the other.",
			kotlin.math.abs(actual - WORDMARK_ASPECT) < 0.01f
		)
	}

	/**
	 * The square is drawn by the app in the accent colour, over a rectangle of the
	 * asset that has to stay inside it. These four numbers are hand-measured, and a
	 * typo in any of them paints somewhere the mark is not.
	 */
	@Test
	fun `the accent square lands inside the mark`() {
		assertTrue(
			"The dot rectangle starts outside the asset.",
			WORDMARK_DOT_LEFT >= 0f && WORDMARK_DOT_TOP >= 0f
		)
		assertTrue(
			"The dot rectangle runs off the asset: " +
				(WORDMARK_DOT_LEFT + WORDMARK_DOT_WIDTH) + " wide, " +
				(WORDMARK_DOT_TOP + WORDMARK_DOT_HEIGHT) + " tall, of 1.0.",
			WORDMARK_DOT_LEFT + WORDMARK_DOT_WIDTH <= 1f &&
				WORDMARK_DOT_TOP + WORDMARK_DOT_HEIGHT <= 1f
		)
		assertTrue(
			"The dot rectangle has no area, so the accent colour would never appear.",
			WORDMARK_DOT_WIDTH > 0f && WORDMARK_DOT_HEIGHT > 0f
		)
		assertTrue(
			"The dot is a square in the logo, but the rectangle it is drawn in is " +
				"nothing like one once the aspect is taken into account.",
			kotlin.math.abs(
				WORDMARK_DOT_WIDTH * WORDMARK_ASPECT - WORDMARK_DOT_HEIGHT
			) < 0.05f
		)
	}

	/** Big-endian four bytes, the only number format inside a PNG header. */
	private fun intAt(bytes: ByteArray, offset: Int): Int =
		((bytes[offset].toInt() and 0xFF) shl 24) or
			((bytes[offset + 1].toInt() and 0xFF) shl 16) or
			((bytes[offset + 2].toInt() and 0xFF) shl 8) or
			(bytes[offset + 3].toInt() and 0xFF)

	/**
	 * Unit tests run from the module directory in one place and the project root in
	 * another, and this file has to be found in both. Same approach as SchemaTest.
	 */
	private fun onboardingSource(): File? =
		listOf(File(ONBOARDING), File("app/$ONBOARDING")).firstOrNull { it.isFile }

	private fun asset(): File? =
		listOf(File(ASSET), File("app/$ASSET")).firstOrNull { it.isFile }
}
