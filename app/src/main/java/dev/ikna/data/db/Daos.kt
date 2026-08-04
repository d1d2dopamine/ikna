package dev.ikna.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ChunkDao {
    @Upsert suspend fun upsertChunks(chunks: List<ChunkEntity>)
    @Upsert suspend fun upsertTokens(tokens: List<ChunkTokenEntity>)
    @Upsert suspend fun upsertPack(pack: PackEntity)

    @Query("SELECT * FROM packs WHERE id = :id")
    suspend fun pack(id: String): PackEntity?

    @Query("SELECT * FROM chunks WHERE id = :id")
    suspend fun chunk(id: String): ChunkEntity?

    @Query("SELECT * FROM chunks WHERE id IN (:ids)")
    suspend fun chunks(ids: List<String>): List<ChunkEntity>

    @Query("SELECT * FROM chunk_tokens WHERE chunkId IN (:ids)")
    suspend fun tokensFor(ids: List<String>): List<ChunkTokenEntity>

    // Candidate pool: chunks with no card yet, cheapest first by frequency.
    @Query(
        "SELECT * FROM chunks WHERE id NOT IN (SELECT DISTINCT chunkId FROM cards) " +
            "ORDER BY freqRank ASC LIMIT :limit"
    )
    suspend fun uintroducedByFrequency(limit: Int): List<ChunkEntity>

    @Query("SELECT COUNT(*) FROM chunks")
    suspend fun chunkCount(): Int
}

@Dao
interface CardDao {
    @Upsert suspend fun upsert(card: CardEntity)
    @Upsert suspend fun upsertAll(cards: List<CardEntity>)

    @Query("SELECT * FROM cards WHERE chunkId = :chunkId AND level = :level")
    suspend fun card(chunkId: String, level: Int): CardEntity?

    @Query("SELECT COUNT(*) FROM cards WHERE inAmnesty = 0 AND dueAt <= :until")
    suspend fun dueCount(until: Long): Int

    @Query("SELECT COUNT(*) FROM cards WHERE inAmnesty = 1")
    suspend fun amnestyCount(): Int

    @Query(
        "SELECT * FROM cards WHERE inAmnesty = 0 AND dueAt <= :until " +
            "ORDER BY dueAt ASC LIMIT :limit"
    )
    suspend fun dueCards(until: Long, limit: Int): List<CardEntity>

    @Query("SELECT * FROM cards WHERE inAmnesty = 1 ORDER BY dueAt ASC LIMIT :limit")
    suspend fun amnestyCards(limit: Int): List<CardEntity>

    // Forecast: how many cards fall due on each of the next days.
    @Query(
        "SELECT COUNT(*) FROM cards WHERE inAmnesty = 0 " +
            "AND dueAt > :from AND dueAt <= :to"
    )
    suspend fun dueBetween(from: Long, to: Long): Int

    @Query("UPDATE cards SET inAmnesty = 1 WHERE inAmnesty = 0 AND dueAt < :threshold")
    suspend fun moveOverdueToAmnesty(threshold: Long): Int

    @Query("SELECT COUNT(*) FROM cards")
    fun cardCountFlow(): Flow<Int>
}

@Dao
interface ReviewDao {
    // Deliberately INSERT-only: no update, no delete, anywhere in the codebase.
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(review: ReviewEntity): Long

    @Query("SELECT * FROM reviews ORDER BY ts ASC")
    suspend fun all(): List<ReviewEntity>

    @Query("SELECT * FROM reviews ORDER BY ts DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ReviewEntity>

    @Query("SELECT COUNT(*) FROM reviews WHERE ts >= :from")
    suspend fun countSince(from: Long): Int

    @Query("SELECT MAX(ts) FROM reviews")
    suspend fun lastReviewTs(): Long?

    @Query("SELECT COUNT(*) FROM reviews")
    suspend fun total(): Int
}

@Dao
interface ComponentDao {
    @Upsert suspend fun upsertAll(items: List<ComponentEntity>)

    @Query("SELECT * FROM components WHERE lemma IN (:lemmas)")
    suspend fun byLemmas(lemmas: List<String>): List<ComponentEntity>

    @Query("SELECT * FROM components")
    suspend fun all(): List<ComponentEntity>

    @Query("SELECT COUNT(*) FROM components WHERE stabilityEst >= :minStability")
    suspend fun knownCount(minStability: Double): Int

    @Query("DELETE FROM components")
    suspend fun clear()
}

@Dao
interface StatsDao {
    @Upsert suspend fun upsert(stat: DailyStatEntity)

    @Query("SELECT * FROM daily_stats WHERE day = :day")
    suspend fun day(day: String): DailyStatEntity?

    @Query("SELECT * FROM daily_stats ORDER BY day DESC LIMIT :limit")
    suspend fun lastDays(limit: Int): List<DailyStatEntity>

    @Query("SELECT * FROM daily_stats ORDER BY day DESC LIMIT 30")
    fun last30Flow(): Flow<List<DailyStatEntity>>

    @Query("SELECT SUM(newIntroduced) FROM daily_stats WHERE day >= :fromDay")
    suspend fun newIntroducedSince(fromDay: String): Int?
}

@Dao
interface GovernorDao {
    @Insert suspend fun insert(entry: GovernorLogEntity)

    @Query("SELECT * FROM governor_log ORDER BY ts DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<GovernorLogEntity>

    @Query("SELECT * FROM governor_log WHERE day = :day ORDER BY ts DESC LIMIT 1")
    suspend fun forDay(day: String): GovernorLogEntity?
}
