package dev.ikna.data.db

import android.content.Context
import androidx.room.deferredTransaction
import androidx.room.useReaderConnection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real AndroidSQLiteDriver regressions for upstream Room b/415006268.
 * Run against both dependency versions on an isolated emulator. A coroutine timeout alone
 * cannot interrupt a native SQLite wait: the instrumentation invocation also needs an
 * external process deadline. These tests never open the application's learner database.
 */
@RunWith(AndroidJUnit4::class)
class AndroidDriverTransactionRegressionTest {
    private class ExpectedRollback : RuntimeException()

    private fun isolated(block: suspend (IknaDatabase) -> Unit) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "driver-transaction-${UUID.randomUUID()}.db"
        val db = openIknaDatabase(context, name)
        try { block(db) }
        finally { db.close(); context.deleteDatabase(name) }
    }

    @Test(timeout = 30_000)
    fun readerTransactionRetainsConnectionAcrossExplicitThreadHop() = isolated { db ->
        db.historyBoundaryDao().put(HistoryBoundaryEntity(afterLocalId = 7, token = "original"))
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { other ->
            db.useReaderConnection { connection ->
                connection.deferredTransaction {
                    assertEquals("original", db.historyBoundaryDao().current()?.token)
                    withContext(other) {
                        assertEquals(7L, db.historyBoundaryDao().current()?.afterLocalId)
                    }
                    assertEquals("original", db.historyBoundaryDao().current()?.token)
                }
            }
        }
    }

    @Test(timeout = 30_000)
    fun writerTransactionCommitsAcrossExplicitThreadHop() = isolated { db ->
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { other ->
            db.inTransaction {
                db.historyBoundaryDao().put(HistoryBoundaryEntity(afterLocalId = 1, token = "first"))
                withContext(other) {
                    db.historyBoundaryDao().put(HistoryBoundaryEntity(afterLocalId = 2, token = "second"))
                }
                assertEquals("second", db.historyBoundaryDao().current()?.token)
            }
        }
        assertEquals(2L, db.historyBoundaryDao().current()?.afterLocalId)
    }

    @Test(timeout = 30_000)
    fun writerTransactionRollsBackAcrossExplicitThreadHop() = isolated { db ->
        db.historyBoundaryDao().put(HistoryBoundaryEntity(afterLocalId = 7, token = "original"))
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { other ->
            try {
                db.inTransaction {
                    withContext(other) {
                        db.historyBoundaryDao().put(HistoryBoundaryEntity(afterLocalId = 9, token = "uncommitted"))
                    }
                    throw ExpectedRollback()
                }
                fail("Expected rollback")
            } catch (_: ExpectedRollback) { }
        }
        assertEquals("original", db.historyBoundaryDao().current()?.token)
        assertEquals(7L, db.historyBoundaryDao().current()?.afterLocalId)
    }
}
