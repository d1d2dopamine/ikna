package dev.ikna.desktop

import dev.ikna.data.db.IknaDatabase
import dev.ikna.data.db.openIknaDatabase
import dev.ikna.data.pack.PackLoader
import dev.ikna.data.repo.ComponentRepository
import dev.ikna.data.repo.DeckRepository
import dev.ikna.data.repo.LearningRepository
import dev.ikna.domain.fsrs.FsrsParams
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.ChunkSelector
import dev.ikna.domain.governor.GovernorConfig
import dev.ikna.platform.ClasspathAssets
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Captures the current Kotlin study behavior for comparison with a Rust study session. */
fun main(args: Array<String>) = runBlocking {
    val output = File(args.single())
    val snapshots = listOf(Rating.GOOD, Rating.AGAIN).map { captureStudy(it) }
    output.parentFile.mkdirs()
    output.writeText(snapshots.joinToString(separator = "\n", postfix = "\n"))
}

private val studyTime = Instant.parse("2026-10-20T18:00:00Z").toEpochMilli()
private val config = GovernorConfig.load(ClasspathAssets)
private const val STARTER = """
read | I read the article. | Look at and understand written words.
write | I write a short note. | Put words on paper.
listen | I listen to the radio. | Pay attention to a sound.
"""

private suspend fun captureStudy(rating: Rating): JsonObject {
    val home = Files.createTempDirectory("ikna-study-reference-").toFile()
    val database = home.resolve("profile.db")
    try {
        val first = openIknaDatabase(database)
        val recorded = try {
            val decks = DeckRepository(first, PackLoader(ClasspathAssets, first.chunkDao()))
            val imported = decks.importText("starter.txt", STARTER, "Starter", lang = "en")
            check(imported.installed == 3)
            val installed = requireNotNull(first.chunkDao().pack(imported.packId))
            first.chunkDao().upsertPack(installed.copy(installedAt = studyTime))
            val repository = repository(first)
            val plan = repository.ensureDailyPlan(studyTime)
            val queue = repository.buildSession(now = studyTime)
            val target = queue.cards.first()
            repository.answer(target, rating, durationMs = 2_000, now = studyTime + 2_000)
            buildJsonObject {
                put("schema", 1)
                put("timezone", "UTC")
                put("time", studyTime)
                put("starter", STARTER.trim())
                put("inputRating", rating.name)
                put("answerTime", studyTime + 2_000)
                put("capacity", plan.capacity)
                put("allowedNew", plan.allowedNew)
                put("planReason", plan.reason)
                putJsonArray("queue") { queue.cards.forEach { add(JsonPrimitive(it.card.key)) } }
                put("chunkId", target.card.chunkId)
                put("level", target.card.level)
                val card = requireNotNull(first.cardDao().card(target.card.chunkId, target.card.level))
                put("dueAt", card.dueAt)
                put("storedRating", first.reviewDao().all().single().rating)
            }
        } finally {
            first.close()
        }
        // Open the persisted database independently of the first repository.
        val reopened = openIknaDatabase(database)
        try {
            val review = reopened.reviewDao().all().single()
            val card = requireNotNull(reopened.cardDao().card(review.chunkId, review.level))
            val resume = repository(reopened).buildSession(now = studyTime + 2_001)
            return buildJsonObject {
                recorded.forEach { (key, value) -> put(key, value) }
                put("reopenedDueAt", card.dueAt)
                put("reopenedRating", review.rating)
                put("remaining", resume.remaining)
                put("sessionDone", resume.sessionDone)
                val nextDue = resume.nextDueAt
                if (nextDue == null) put("nextDueAt", JsonNull) else put("nextDueAt", nextDue)
            }
        } finally {
            reopened.close()
        }
    } finally {
        home.deleteRecursively()
    }
}

private fun repository(db: IknaDatabase) = LearningRepository(
    db, db.cardDao(), db.chunkDao(), db.reviewDao(), db.statsDao(),
    db.governorDao(), db.planDao(), db.browseDao(),
    ComponentRepository(db.componentDao(), db.chunkDao(), db.reviewDao(), db),
    Scheduler(FsrsParams(desiredRetention = config.desiredRetention), config.dayStartHour),
    ChunkSelector(), config
)
