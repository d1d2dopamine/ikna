package dev.ikna.desktop

import dev.ikna.data.db.ChunkDao
import dev.ikna.data.db.PackEntity
import dev.ikna.data.db.openIknaDatabase
import dev.ikna.data.pack.PackLoader
import dev.ikna.data.repo.DeckRepository
import dev.ikna.platform.ClasspathAssets
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StagedDeckImportTest {
    @Test
    fun failedBatchRollsBackAndCanBeRetried() = runBlocking {
        val home = Files.createTempDirectory("ikna-staged-deck-").toFile()
        val database = openIknaDatabase(home.resolve("profile.db"))
        try {
            val dao = database.chunkDao()
            val decks = DeckRepository(database, PackLoader(ClasspathAssets, dao))
            val initial = decks.importText("starter.txt", "write | I write at home. | Put words on paper.", "Starter")
            val pack = requireNotNull(dao.pack(initial.packId))
            val cardsBefore = dao.chunksForPack(pack.id)
            val staged = "# Ikna MCP v1: starter\nread | I read at home. | Look at written words."
            val cannotCommit = object : ChunkDao by dao {
                override suspend fun upsertPack(pack: PackEntity) {
                    throw IllegalStateException("forced metadata failure")
                }
            }
            val failing = DeckRepository(database, PackLoader(ClasspathAssets, cannotCommit))
            assertFalse(runCatching { failing.importText("batch.txt", staged, "Starter", appendTo = pack.id) }.isSuccess)
            assertEquals(pack, dao.pack(pack.id))
            assertEquals(cardsBefore, dao.chunksForPack(pack.id))

            val imported = decks.importText("batch.txt", staged, "Starter", appendTo = pack.id)
            assertTrue(imported.stagedAiImport)
            assertEquals(1, imported.installed)
            assertEquals(2, dao.chunksForPack(pack.id).size)
            val retry = decks.importText("batch.txt", staged, "Starter", appendTo = pack.id)
            assertEquals(0, retry.installed)
            assertEquals(2, dao.chunksForPack(pack.id).size)
        } finally {
            database.close()
            home.deleteRecursively()
        }
    }
}
