package dev.ikna.desktop

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Publish only a closed, complete sibling file. No delete/copy fallback: a filesystem
 * without atomic replacement fails without deliberately removing the previous backup.
 * Cancellation observed before the move preserves the destination. The move is the
 * commit point; cancellation after it may leave the complete new file in place.
 * This is per-file replacement, not a multi-file transaction or a power-loss guarantee.
 */
internal suspend fun writeBackupAtomically(target: File, write: suspend (File) -> Unit) {
    val caller = currentCoroutineContext()
    caller.ensureActive()
    val destination = target.absoluteFile.toPath()
    Files.createDirectories(destination.parent)
    val temporary = Files.createTempFile(destination.parent, ".ikna-backup-", ".tmp")
    var failure: Throwable? = null
    try {
        write(temporary.toFile())
        caller.ensureActive()
        Files.move(temporary, destination, ATOMIC_MOVE, REPLACE_EXISTING)
    } catch (error: Throwable) {
        failure = error
        throw error
    } finally {
        try {
            Files.deleteIfExists(temporary)
        } catch (cleanup: Throwable) {
            if (failure != null) failure.addSuppressed(cleanup) else throw cleanup
        }
    }
}
