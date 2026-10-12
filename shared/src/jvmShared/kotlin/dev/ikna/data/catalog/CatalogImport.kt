package dev.ikna.data.catalog

import dev.ikna.data.db.IknaDatabase
import dev.ikna.data.db.inTransaction
import dev.ikna.data.pack.ImportResult
import dev.ikna.data.pack.PackLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** A screen-bound install: content and pack metadata either commit together or roll back. */
suspend fun importCatalogDeck(
    database: IknaDatabase,
    packs: PackLoader,
    packId: String,
    title: String,
    lang: String,
    text: String
): ImportResult = withContext(Dispatchers.IO) {
    val callerContext = currentCoroutineContext()
    callerContext.ensureActive()
    database.inTransaction {
        val result = packs.importJsonl(packId, title, lang, text)
        require(result.installed > 0) { "Catalogue deck contains no installable targets" }
        // Room runs the writer block in its database scope. Check the captured
        // screen context, not Room's still-active job, before committing.
        callerContext.ensureActive()
        result
    }
}
