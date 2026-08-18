package dev.ikna.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * The arithmetic behind the band, and the name the file is given.
 *
 * The download itself needs a socket and the installer needs a phone, so neither
 * is tested here. What is tested is everything that can lie without either: a
 * percentage that reads 100 before the file is whole, a name that came off the
 * network with a slash in it, a total the server never declared.
 */
class UpdateDownloadTest {

    @Test
    fun `percent runs from nothing to whole`() {
        assertEquals(0, progressPercent(0L, 100L))
        assertEquals(47, progressPercent(47L, 100L))
        assertEquals(100, progressPercent(100L, 100L))
    }

    @Test
    fun `unknown total draws nothing rather than everything`() {
        // A server that declares no length must not produce a full band; an
        // empty one is honest and a full one is a lie the person acts on.
        assertEquals(0f, progressFraction(1_000L, 0L), 0f)
        assertEquals(0, progressPercent(1_000L, 0L))
        assertEquals(0f, progressFraction(1_000L, -1L), 0f)
    }

    @Test
    fun `a longer file than declared still stops at whole`() {
        assertEquals(1f, progressFraction(300L, 100L), 0f)
        assertEquals(100, progressPercent(300L, 100L))
    }

    @Test
    fun `ninety nine point nine per cent is not a hundred`() {
        // The moment that matters: the last kilobyte is not yet written, so the
        // number must not yet say it is done.
        assertTrue(progressPercent(9_999_999L, 10_000_000L) < 100)
    }

    @Test
    fun `the release keeps its own file name`() {
        assertEquals(
            "ikna-v0.4.0-press.apk",
            apkFileName(
                "v0.4.0-press",
                "https://github.com/d1d2dopamine/ikna/releases/download/" +
                    "v0.4.0-press/ikna-v0.4.0-press.apk"
            )
        )
    }

    @Test
    fun `a query string is not part of the name`() {
        assertEquals(
            "ikna-v0.4.0-press.apk",
            apkFileName("v0.4.0-press", "https://example.test/ikna-v0.4.0-press.apk?token=1&x=2")
        )
    }

    @Test
    fun `a name that is not an apk falls back to the tag`() {
        assertEquals("ikna-v0.4.0-press.apk", apkFileName("v0.4.0-press", "https://example.test/"))
        assertEquals(
            "ikna-v0.4.0-press.apk",
            apkFileName("v0.4.0-press", "https://example.test/download")
        )
    }

    @Test
    fun `nothing usable still produces a usable name`() {
        assertEquals("ikna-update.apk", apkFileName("", ""))
        assertEquals("ikna-update.apk", apkFileName("../", "https://example.test/../"))
    }

    @Test
    fun `a name from the network cannot climb out of the folder`() {
        // Everything that is not a letter, a digit, a dot, a dash or an
        // underscore is dropped, so no separator survives to be walked.
        val name = apkFileName("tag", "https://example.test/..%2f..%2fetc%2fpasswd.apk")
        assertTrue(name.none { it == '/' || it == '\\' })
        assertTrue(!name.startsWith("."))
        assertTrue(name.endsWith(".apk"))
    }
}
