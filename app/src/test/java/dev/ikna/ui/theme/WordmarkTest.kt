package dev.ikna.ui.theme

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The in-app logo is the supplied 0.11 raster wordmark split into two transparent
 * masks. Compose tints the letterforms and the accent cap separately so the mark
 * follows the active palette without changing its geometry.
 *
 * The PNG header is read by hand rather than through an image library. Unit tests
 * in an Android module compile against android.jar, which shadows the JDK and does
 * not carry javax.imageio.
 */
private const val ASSET = "shared/src/androidMain/res/drawable-nodpi/ikna_wordmark.png"
private const val ACCENT_ASSET =
    "shared/src/androidMain/res/drawable-nodpi/ikna_wordmark_accent.png"
private const val ONBOARDING = "src/main/java/dev/ikna/ui/onboarding/OnboardingScreen.kt"
private const val ONBOARDING_TITLE =
    "shared/src/jvmShared/kotlin/dev/ikna/ui/onboarding/OnboardingTitle.kt"

/** PNG signature, then the IHDR chunk: width and height are its first eight bytes. */
private val PNG_MAGIC = byteArrayOf(
    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
)

class WordmarkTest {

    @Test
    fun `both wordmark layers are on disk`() {
        assertNotNull(
            "app/$ASSET is missing. The supplied wordmark letterforms must remain a raster mask.",
            asset(ASSET)
        )
        assertNotNull(
            "app/$ACCENT_ASSET is missing. The accent cap needs its own tintable raster mask.",
            asset(ACCENT_ASSET)
        )
    }

    @Test
    fun `the first launch uses the wordmark artwork not typed letters`() {
        val screen = onboardingSource()?.readText() ?: return
        val title = onboardingTitleSource()?.readText() ?: return
        assertTrue(
            "Onboarding no longer uses the shared branded title",
            "IknaOnboardingTitle(" in screen
        )
        assertTrue(
            "The inline first-slide wordmark is no longer text-sized",
            "IknaWordmark(height = 22.dp" in title
        )
        assertTrue(
            "Later onboarding slides lost their centred wordmark",
            "IknaWordmark(height = 44.dp" in title
        )
        assertTrue(
            "Onboarding fell back to typed ikna",
            "text = \"ikna\"" !in screen && "text = \"ikna\"" !in title
        )
    }

    @Test
    fun `both layers are png files`() {
        for (path in listOf(ASSET, ACCENT_ASSET)) {
            val bytes = (asset(path) ?: return).readBytes()
            assertTrue(
                "$path does not start with the PNG signature.",
                bytes.size > 24 && bytes.copyOfRange(0, 8).contentEquals(PNG_MAGIC)
            )
        }
    }

    @Test
    fun `the two layers share the same proportions`() {
        val base = dimensions(asset(ASSET) ?: return)
        val accent = dimensions(asset(ACCENT_ASSET) ?: return)
        assertTrue(
            "The two tint layers must occupy the same canvas, but are ${base.first}x${base.second} " +
                "and ${accent.first}x${accent.second}.",
            base == accent
        )
        val actual = base.first.toFloat() / base.second.toFloat()
        assertTrue(
            "The masks are ${base.first}x${base.second}, aspect $actual, but WORDMARK_ASPECT is " +
                "$WORDMARK_ASPECT. One changed without the other.",
            kotlin.math.abs(actual - WORDMARK_ASPECT) < 0.01f
        )
    }

    private fun dimensions(file: File): Pair<Int, Int> {
        val bytes = file.readBytes()
        return intAt(bytes, 16) to intAt(bytes, 20)
    }

    /** Big-endian four bytes, the only number format inside a PNG header. */
    private fun intAt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun onboardingSource(): File? =
        listOf(File(ONBOARDING), File("app/$ONBOARDING")).firstOrNull { it.isFile }

    private fun onboardingTitleSource(): File? =
        listOf(File(ONBOARDING_TITLE), File("../$ONBOARDING_TITLE"))
            .firstOrNull { it.isFile }

    private fun asset(path: String): File? =
        listOf(File(path), File("../$path")).firstOrNull { it.isFile }
}
