package dev.ikna.ui.decks

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Sending a deck to another person.
 *
 * This app has no account, no sync and no permission to open a socket, so the
 * only way content moves between two people is the one the phone already has:
 * the system share sheet. The deck is written out as the same three columns the
 * importer accepts, which means the person receiving it can read it in any text
 * app, correct a line, or hand it to a model — rather than receive an opaque
 * file that only this app can open.
 *
 * The file is written into `cache/share`, the single directory the provider in
 * the manifest can see. Nothing else in the app's storage is reachable through
 * it, and the directory is emptied before each share so a deck sent last month
 * is not still sitting on disk.
 */
object DeckShare {

    private const val DIR = "share"

    /**
     * Writes [body] to a temporary file and opens the share sheet for it.
     *
     * @return true if some app accepted the intent. False means the phone has
     *   nothing that can receive a text file, or the write failed — either way
     *   the caller has to say so, because from the user's side the button simply
     *   did nothing.
     */
    fun shareText(
        context: Context,
        fileName: String,
        body: String,
        chooserTitle: String
    ): Boolean {
        return try {
            val dir = File(context.cacheDir, DIR)
            dir.mkdirs()
            dir.listFiles()?.forEach { it.delete() }

            val file = File(dir, fileName)
            file.writeText(body)

            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".files",
                file
            )

            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                // Some targets show the subject rather than the file name.
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(send, chooserTitle)
            // Compose hands us the activity in practice, but a context that is
            // not one cannot start an activity without its own task.
            if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            // Cache can be full, or cleared underneath us mid-write. A failed
            // share is a message, never a crash.
            false
        }
    }

    /**
     * A file name a human can recognise in a chat: the deck's own title, with
     * everything a file system dislikes removed.
     */
    fun fileNameFor(title: String): String {
        val slug = title.lowercase()
            .map { ch -> if (ch.isLetterOrDigit()) ch else '-' }
            .joinToString("")
            .replace(Regex("-+"), "-")
            .trim('-')
            .take(40)
            .ifEmpty { "deck" }
        return "$slug.txt"
    }
}
