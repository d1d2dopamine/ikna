package dev.ikna.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.ikna.data.prefs.FontStore
import java.io.File

/**
 * Where the database, the settings and an installed font live.
 *
 * On Windows that is the roaming application data folder, which Windows backs up
 * and moves with a profile. Anywhere else it is a dot folder in the home
 * directory, so the same code on Linux or macOS lands somewhere unsurprising.
 */
fun iknaHome(): File {
    val appData = System.getenv("APPDATA")
    val base = if (!appData.isNullOrBlank()) {
        File(appData, "Ikna")
    } else {
        File(System.getProperty("user.home") ?: ".", ".ikna")
    }
    base.mkdirs()
    return base
}

fun main() {
    val home = iknaHome()

    // FontStore is a singleton with no constructor, so the folder it writes an
    // installed font into has to be set before anything reads a font. IknaApp
    // does the same thing in onCreate on Android.
    FontStore.baseDir = home

    val container = DesktopContainer(home)

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Ikna",
            state = rememberWindowState(size = DpSize(1180.dp, 800.dp))
        ) {
            IknaDesktopApp(container)
        }
    }
}
