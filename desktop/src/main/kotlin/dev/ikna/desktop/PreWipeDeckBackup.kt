package dev.ikna.desktop

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

internal class PreWipeDeckBackupException(cause: Exception) :
    IOException("Pre-wipe deck-text export failed", cause)

/** Deck texts only, deliberately not a promise of journal/settings restoration. */
internal suspend fun <T> backupDeckTextsThenWipe(
    exportRoot: File,
    decks: suspend () -> List<T>,
    fileName: (T) -> String,
    exportText: suspend (T) -> String,
    wipe: suspend () -> Unit
): File {
    val directory = try {
        Files.createDirectories(exportRoot.toPath())
        // A retry cannot overwrite an earlier backup. Ordinals prevent equal titles
        // (or titles which sanitize to the same filename) from losing a deck.
        val target = Files.createTempDirectory(exportRoot.toPath(), "pre-wipe-").toFile()
        decks().forEachIndexed { index, deck ->
            val destination = File(target, "${index + 1}-${File(fileName(deck)).name}")
            val body = exportText(deck)
            // Empty decks are legitimate: write their empty text file as well.
            destination.writeText(body)
            if (destination.readText() != body) throw IOException("Deck export verification failed")
        }
        target
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        throw PreWipeDeckBackupException(failure)
    }
    coroutineContext.ensureActive()
    wipe()
    return directory
}
