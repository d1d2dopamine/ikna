package dev.ikna.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * The file somebody dropped on the window, waiting to be claimed.
 *
 * A window that cannot be dropped on is a window that makes you find the file
 * twice: once in the folder you dragged it from, and again in a file dialog.
 * This holds one file at a time -- the last one dropped -- and whichever screen
 * knows what to do with that extension takes it and clears it.
 *
 * Deliberately not a queue. Dropping eight files is not a request to run eight
 * imports at once, and the honest reading of it is "this one".
 */
object DesktopDrop {

    var pending: File? by mutableStateOf(null)

    /** Extensions a screen exists for. Anything else is ignored on purpose. */
    fun accepts(file: File): Boolean {
        val name = file.name.lowercase()
        return KNOWN.any { name.endsWith(it) }
    }

    // Only what a screen can actually read today. A comma-separated file is
    // deliberately absent: the deck parser splits on pipes and tabs, so a .csv
    // would be accepted and then reported as unreadable, which is worse than
    // not being accepted.
    private val KNOWN = listOf(".apkg", ".colpkg", ".txt", ".tsv", ".jsonl", ".ikna")
}
