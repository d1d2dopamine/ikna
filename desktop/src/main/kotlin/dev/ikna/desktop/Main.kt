package dev.ikna.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
// Wildcard on purpose: Window, WindowState, application and the rest of the
// window vocabulary all live in this one package.
import androidx.compose.ui.window.*
import dev.ikna.data.prefs.FontStore
import dev.ikna.ui.text.S
import kotlinx.coroutines.runBlocking
import java.awt.Desktop
import java.io.File
import java.io.PrintWriter
import java.io.RandomAccessFile
import java.io.StringWriter
import java.nio.channels.FileLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import javax.swing.JOptionPane
import kotlin.system.exitProcess

/**
 * Where the database, the settings, the log and an installed font live.
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

// ---------------------------------------------------------------------------
// The log.
//
// A packaged desktop application has nowhere to print to: there is no console
// behind the window, and jpackage's launcher answers a crash with a message box
// containing one line of text and no way to copy it. That is how the palette
// bug arrived -- a box that said "sun/misc/Unsafe" and nothing else. Everything
// that goes wrong from now on is written down first, in a file the user can be
// asked for, and only then shown.
// ---------------------------------------------------------------------------

private const val LOG_BYTES_KEPT = 256 * 1024

/** Set once at startup so the crash handlers can find the folder. */
private var logHome: File? = null

fun iknaLogFile(home: File): File {
    val folder = File(home, "logs")
    folder.mkdirs()
    return File(folder, "ikna-desktop.log")
}

fun logLine(text: String) {
    val home = logHome ?: return
    runCatching {
        val file = iknaLogFile(home)
        // Trimmed rather than rotated: one file is one thing to ask for, and a
        // quarter of a megabyte is several sessions of history.
        if (file.length() > LOG_BYTES_KEPT) {
            val kept = file.readText().takeLast(LOG_BYTES_KEPT / 2)
            file.writeText(kept)
        }
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())
        file.appendText(stamp + "  " + text + "\n")
    }
}

private fun traceOf(error: Throwable): String {
    val writer = StringWriter()
    error.printStackTrace(PrintWriter(writer))
    return writer.toString()
}

/**
 * What the user sees when something escapes.
 *
 * The name of the failure and the path of the log, in the interface language.
 * Not a stack trace: the trace is in the file, and a wall of frames in a modal
 * box tells nobody anything.
 */
fun reportCrash(where: String, error: Throwable) {
    val home = logHome
    logLine("CRASH in " + where + "\n" + traceOf(error))
    runCatching {
        val path = if (home == null) "" else "\n\n" + iknaLogFile(home).absolutePath
        val name = error::class.java.simpleName + ": " + (error.message ?: "")
        JOptionPane.showMessageDialog(
            null,
            S.t("pc.013") + path + "\n\n" + name.trim().take(300),
            "Ikna",
            JOptionPane.ERROR_MESSAGE
        )
    }
}

/**
 * The AWT event thread's own escape hatch.
 *
 * Swing does not route an exception thrown inside an event to the thread's
 * default handler; it looks for a class named by this property and calls
 * handle(Throwable) on it, reflectively, which is why the class is public, has
 * a constructor taking nothing, and is kept by name in compose-desktop.pro.
 */
class AwtCrashHandler {
    fun handle(error: Throwable) {
        reportCrash("awt-event-thread", error)
    }
}

private fun installCrashHandlers(home: File) {
    logHome = home
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
        reportCrash(thread.name, error)
        previous?.uncaughtException(thread, error)
    }
    System.setProperty("sun.awt.exception.handler", AwtCrashHandler::class.java.name)
}

// ---------------------------------------------------------------------------
// One window per computer.
// ---------------------------------------------------------------------------

/** Held for the lifetime of the process on purpose; releasing it frees the lock. */
private var instanceLock: FileLock? = null

private fun claimSingleInstance(home: File): Boolean = runCatching {
    val channel = RandomAccessFile(File(home, "ikna.lock"), "rw").channel
    val lock = channel.tryLock()
    if (lock == null) {
        channel.close()
        false
    } else {
        instanceLock = lock
        true
    }
    // A filesystem that cannot lock is not a reason to refuse to start.
}.getOrDefault(true)

// ---------------------------------------------------------------------------
// Window geometry.
//
// A desktop application that opens at the same size in the middle of the screen
// every morning is a phone application in a window. Where it was put and how big
// it was made is a decision the user already took.
// ---------------------------------------------------------------------------

private class Geometry(
    val size: DpSize,
    val position: WindowPosition,
    val placement: WindowPlacement
)

private fun geometryFile(home: File) = File(home, "window.properties")

private fun loadGeometry(home: File): Geometry {
    val fallback = Geometry(
        size = DpSize(1180.dp, 800.dp),
        position = WindowPosition.PlatformDefault,
        placement = WindowPlacement.Floating
    )
    val file = geometryFile(home)
    if (!file.exists()) return fallback
    return runCatching {
        val properties = Properties()
        file.inputStream().use { properties.load(it) }
        val width = properties.getProperty("width")?.toFloatOrNull() ?: 1180f
        val height = properties.getProperty("height")?.toFloatOrNull() ?: 800f
        val x = properties.getProperty("x")?.toFloatOrNull()
        val y = properties.getProperty("y")?.toFloatOrNull()
        val maximized = properties.getProperty("maximized") == "true"
        Geometry(
            // Clamped: a window restored at 200 by 100, or at a position on a
            // screen that is no longer attached, is a window nobody can use.
            size = DpSize(width.coerceIn(720f, 6000f).dp, height.coerceIn(560f, 4000f).dp),
            position = if (x == null || y == null || x < -3000f || y < -3000f) {
                WindowPosition.PlatformDefault
            } else {
                WindowPosition(x.dp, y.dp)
            },
            placement = if (maximized) WindowPlacement.Maximized else WindowPlacement.Floating
        )
    }.getOrDefault(fallback)
}

private fun saveGeometry(home: File, state: WindowState) {
    runCatching {
        val properties = Properties()
        properties.setProperty("width", state.size.width.value.toString())
        properties.setProperty("height", state.size.height.value.toString())
        val position = state.position
        if (position.isSpecified) {
            properties.setProperty("x", position.x.value.toString())
            properties.setProperty("y", position.y.value.toString())
        }
        properties.setProperty(
            "maximized",
            (state.placement == WindowPlacement.Maximized).toString()
        )
        geometryFile(home).outputStream().use { properties.store(it, "Ikna window") }
    }
}

// ---------------------------------------------------------------------------
// The self test.
//
// Run by CI on the packaged application, with --selftest, before anything is
// uploaded. It does the two things that only fail once the runtime image has
// been trimmed: it opens the database, and it writes a setting. The palette
// crash was a write on a runtime image with no jdk.unsupported in it -- this
// would have caught it on the build machine, in the job that produced it.
// ---------------------------------------------------------------------------

/**
 * Said twice: once to the console for a person running this by hand, once to
 * the log for the packaged launcher, which has no console to say it to.
 */
private fun say(text: String) {
    println(text)
    logLine(text)
}

private fun selfTest(home: File): Int = try {
    logHome = home
    // The class that was missing. Named here so a broken runtime image fails
    // with a sentence rather than with a message box the CI machine has nobody
    // to show to.
    Class.forName("sun.misc.Unsafe")
    say("selftest: sun.misc.Unsafe present")

    runBlocking {
        val container = DesktopContainer(home)
        container.install()
        val before = container.settings.current()
        // A real write through DataStore, protobuf and Unsafe. Same value, so
        // running the test changes nothing.
        container.settings.setPalette(before.paletteId)
        container.settings.setAnimations(before.animations)
        val after = container.settings.current()
        check(after.paletteId == before.paletteId) { "settings did not round-trip" }

        val decks = container.deckRepository.decks()
        val plan = container.learningRepository.buildSession(deckId = null)
        say(
            "selftest: decks=" + decks.size +
                " cards=" + plan.cards.size +
                " palette=" + after.paletteId
        )
    }
    say("selftest: ok")
    0
} catch (error: Throwable) {
    System.err.println("selftest: FAILED")
    error.printStackTrace()
    logLine("selftest: FAILED\n" + traceOf(error))
    1
}

fun main(args: Array<String>) {
    if (args.any { it == "--selftest" }) {
        // Where the test runs is an input, because the packaged launcher is a
        // windowed program: nothing it prints reaches a console, so the build
        // machine reads the result out of the log file and has to be told where
        // that file will be.
        val chosen = System.getenv("IKNA_SELFTEST_HOME")?.takeIf { it.isNotBlank() }
        val sandbox =
            if (chosen != null) File(chosen)
            else File(System.getProperty("java.io.tmpdir"), "ikna-selftest")
        sandbox.mkdirs()
        FontStore.baseDir = sandbox
        exitProcess(selfTest(sandbox))
    }

    val home = iknaHome()
    installCrashHandlers(home)

    // The window is drawn before any setting has been read, and the two dialogs
    // below can happen before that too, so the language starts as the computer's
    // own. IknaDesktopApp switches it to the chosen one as soon as the settings
    // arrive.
    S.apply("system")

    if (!claimSingleInstance(home)) {
        logLine("second instance refused")
        runCatching {
            JOptionPane.showMessageDialog(
                null,
                S.t("pc.012"),
                "Ikna",
                JOptionPane.INFORMATION_MESSAGE
            )
        }
        exitProcess(0)
    }

    logLine(
        "start  java=" + System.getProperty("java.version") +
            "  os=" + System.getProperty("os.name") +
            "  home=" + home.absolutePath
    )

    // FontStore is a singleton with no constructor, so the folder it writes an
    // installed font into has to be set before anything reads a font. IknaApp
    // does the same thing in onCreate on Android.
    FontStore.baseDir = home

    val container = DesktopContainer(home)
    // Plain object rather than remembered state: it is created once, before the
    // composition exists, because the menu bar and the window's key handler both
    // need to reach the same screen state the shell is drawing from.
    val ui = DesktopUi()
    val geometry = loadGeometry(home)

    application {
        val windowState = rememberWindowState(
            size = geometry.size,
            position = geometry.position,
            placement = geometry.placement
        )

        Window(
            onCloseRequest = {
                saveGeometry(home, windowState)
                logLine("exit")
                exitApplication()
            },
            title = "Ikna",
            // The icon on the window and in the taskbar of a running instance.
            // Separate from the .ico jpackage puts on the executable: that one
            // is what Windows shows before the application starts, this one is
            // what it shows afterwards, and both have to be set to match.
            icon = painterResource("icon.png"),
            state = windowState,
            onKeyEvent = { event -> handleWindowKey(event, ui, windowState) }
        ) {
            IknaDesktopApp(container, ui)
        }
    }
}

fun openFolder(home: File) {
    runCatching {
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(home)
    }.onFailure { error -> logLine("could not open the data folder: " + error) }
}

private fun toggleFullScreen(state: WindowState) {
    state.placement = if (state.placement == WindowPlacement.Fullscreen) {
        WindowPlacement.Floating
    } else {
        WindowPlacement.Fullscreen
    }
}

/**
 * Window-level keys.
 *
 * Only the ones that belong to the application as a whole. Everything a card
 * responds to -- space, the number keys, the arrows -- stays inside the session,
 * where it can be read next to the thing it acts on.
 */
private fun handleWindowKey(event: KeyEvent, ui: DesktopUi, state: WindowState): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (event.key == Key.F11) {
        toggleFullScreen(state)
        return true
    }
    if (event.key == Key.F1) {
        ui.showShortcuts = true
        return true
    }
    if (!event.isCtrlPressed) return false
    return when (event.key) {
        Key.One -> { ui.show(Pane.SESSION); true }
        Key.Two -> { ui.show(Pane.DECK); true }
        Key.Three -> { ui.show(Pane.STATS); true }
        Key.Four -> { ui.show(Pane.SETTINGS); true }
        Key.Comma -> { ui.show(Pane.SETTINGS); true }
        else -> false
    }
}
