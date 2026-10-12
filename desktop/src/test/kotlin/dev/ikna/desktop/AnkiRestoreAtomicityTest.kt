package dev.ikna.desktop

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.ikna.data.db.*
import dev.ikna.data.pack.PackLoader
import dev.ikna.data.repo.ComponentRepository
import dev.ikna.data.repo.RestoreRepository
import dev.ikna.desktop.anki.AnkiImporter
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.GovernorConfig
import dev.ikna.platform.Assets
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Minimal locally generated APKG: no remote files or learner profiles. */
class AnkiRestoreAtomicityTest {
    @Test fun `Anki outer transaction cancellation rolls back nested restore and pack writes`() = runBlocking {
        verify(cancel = true)
    }
    @Test fun `Anki outer transaction failure rolls back nested restore and pack writes`() = runBlocking {
        verify(cancel = false)
    }

    private suspend fun verify(cancel: Boolean) = coroutineScope {
        val home = Files.createTempDirectory("ikna-dev-anki-atomic-").toFile()
        val file = home.resolve("disposable.db")
        var db = openIknaDatabase(file)
        try {
            val source = home.resolve("collection.anki2")
            BundledSQLiteDriver().open(source.path).use { connection ->
                for (sql in listOf(
                    "CREATE TABLE col (crt INTEGER, scm INTEGER, models TEXT, decks TEXT)",
                    "INSERT INTO col VALUES (42, 0, '{}', '{}')",
                    "CREATE TABLE notes (id INTEGER, mid INTEGER, flds TEXT)",
                    "INSERT INTO notes VALUES (1, 1, 'sample' || char(31) || 'meaning')",
                    "CREATE TABLE cards (id INTEGER, nid INTEGER, ord INTEGER, did INTEGER, odid INTEGER, queue INTEGER)",
                    "INSERT INTO cards VALUES (7, 1, 0, 9, 0, 0)",
                    "CREATE TABLE revlog (id INTEGER, cid INTEGER, ease INTEGER, time INTEGER)",
                    "INSERT INTO revlog VALUES (1577880000000, 7, 3, 1200)"
                )) connection.prepare(sql).use { it.step() }
            }
            val archive = home.resolve("synthetic.apkg")
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("collection.anki2")); source.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
            val reached = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val dao = db.componentDao()
            val hook: suspend () -> Unit = {
                if (cancel) { reached.complete(Unit); release.await() } else error("injected final rebuild")
            }
            val restore = RestoreRepository(db, db.cardDao(), db.reviewDao(), db.statsDao(), db.planDao(),
                ComponentRepository(object : ComponentDao by dao {
                    override suspend fun clear() { dao.clear(); hook() }
                }, db.chunkDao(), db.reviewDao()), Scheduler(), GovernorConfig())
            val importer = AnkiImporter(db, db.chunkDao(), PackLoader(Assets { error("No assets needed") }, db.chunkDao()), restore, home)
            if (cancel) {
                val operation = launch { importer.importPackage(archive, "en") }
                try {
                    withTimeout(20_000) { reached.await() }
                    operation.cancel(); release.complete(Unit)
                    withTimeout(20_000) { operation.join() }
                    assertTrue(operation.isCancelled)
                } finally { release.complete(Unit); operation.cancelAndJoin() }
            } else {
                val failure = runCatching { importer.importPackage(archive, "en") }.exceptionOrNull()
                assertNotNull(failure)
                assertEquals("injected final rebuild", failure!!.cause?.message)
            }
            db.close(); db = openIknaDatabase(file)
            assertTrue(db.reviewDao().all().isEmpty())
            assertTrue(db.cardDao().all().isEmpty())
            assertTrue(db.componentDao().all().isEmpty())
            assertTrue(db.statsDao().lastDays(10).isEmpty())
            assertTrue(db.chunkDao().packs().isEmpty())
            assertEquals(0, db.chunkDao().chunkCount())
            val freshRestore = RestoreRepository(db, db.cardDao(), db.reviewDao(), db.statsDao(), db.planDao(),
                ComponentRepository(db.componentDao(), db.chunkDao(), db.reviewDao()), Scheduler(), GovernorConfig())
            val fresh = AnkiImporter(db, db.chunkDao(), PackLoader(Assets { error("No assets needed") }, db.chunkDao()), freshRestore, home)
            val success = fresh.importPackage(archive, "en")
            assertEquals(1, success.cards)
            assertEquals(1, success.reviewEventsImported)
            assertEquals(0, fresh.importPackage(archive, "en").reviewEventsImported)
            db.close(); db = openIknaDatabase(file)
            assertEquals(1, db.reviewDao().all().size)
            assertEquals(1, db.chunkDao().packs().size)
        } finally { db.close(); home.deleteRecursively() }
    }
}
