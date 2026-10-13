package dev.ikna.data.export

import java.io.BufferedWriter
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** A backup-stage failure; no destructive callback has run. */
class PreWipeBackupException(cause: Exception) : IOException("Pre-wipe backup failed", cause)

/** Empty review history needs no journal, but settings must still be saved. */
suspend fun backupJournalThenWipe(
    exportJournal: suspend () -> String?,
    exportSettings: suspend () -> String?,
    wipe: suspend () -> Unit
) {
    try {
        exportJournal()
        if (exportSettings().isNullOrBlank()) throw IOException("Settings backup was not written")
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        throw PreWipeBackupException(failure)
    }
    coroutineContext.ensureActive()
    wipe()
}

/** A provider's null document/stream is a storage failure, never an empty log. */
// Public because the Android app module supplies its MediaStore adapter.
fun <T : Any> writeExportDocument(
    create: () -> T?,
    open: (T) -> OutputStream?,
    body: (BufferedWriter) -> Unit
) {
    val document = create() ?: throw IOException("Could not create export document")
    val stream = open(document) ?: throw IOException("Could not open export document")
    stream.bufferedWriter().use(body)
}
