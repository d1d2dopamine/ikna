package dev.ikna.desktop

import dev.ikna.data.update.DesktopUpdatePackage
import dev.ikna.data.update.UpdateAsset
import dev.ikna.data.update.pickDesktopAssets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopUpdateAssetsTest {
    private fun asset(name: String) = UpdateAsset(
        name, "https://github.com/d1d2dopamine/ikna/releases/download/v0.12.0-press/$name", 100
    )

    @Test
    fun `Windows offers installer and portable for both release naming styles`() {
        val setup = asset("ikna-windows-x64-setup.exe")
        for (portable in listOf(asset("ikna-windows-x64-portable.zip"), asset("ikna-v0.11.0-press-windows-x64.zip"))) {
            assertEquals(
                mapOf(DesktopUpdatePackage.WINDOWS_SETUP to setup, DesktopUpdatePackage.WINDOWS_PORTABLE to portable),
                pickDesktopAssets(listOf(asset("ikna.apk"), portable, setup), "Windows 11", "amd64")
            )
        }
    }

    @Test
    fun `Linux offers its own trusted package on supported hardware`() {
        val image = asset("ikna-linux-x86_64.AppImage")
        val wrongOrigin = image.copy(url = "https://github.com/another/ikna/releases/download/version/ikna-linux-x86_64.AppImage")
        val assets = listOf(wrongOrigin, asset("ikna-windows-x64-setup.exe"), image)
        assertEquals(mapOf(DesktopUpdatePackage.LINUX_APPIMAGE to image), pickDesktopAssets(assets, "Linux", "x86_64"))
        assertTrue(pickDesktopAssets(assets, "Linux", "aarch64").isEmpty())
    }
}
