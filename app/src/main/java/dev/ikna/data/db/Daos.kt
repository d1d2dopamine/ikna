package dev.ikna.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// A retracted answer is one whose id appears in some row's `undoOf`. Every
// query that reads "what did the user actually answer" has to exclude those,
// so the condition lives in one place as a string constant.
private const val NOT_RETRACTED =
    "rating > 0 AND id NOT IN (SELECT undoOf FROM reviews WHERE undoOf IS NOT NULL)"

@Dao
interface ChunkDao {
    @Upsert suspend fun upsertChunks(chunks: List<ChunkEntity>)
    @Upsert suspend fun upsertTokens(tokens: List<ChunkTokenEntity>)
    @Upsert suspend fun upsertPack(pack: PackEntity)

    @Query("SELECT * FROM packs WHERE id = :id")
    suspend fun pack(id: String): PackEntity?

    @Query("SELECT * FROM packs ORDER BY installedAt ASC")
    suspend fun packs(): List<PackEntity>

    @Query("UPDATE packs SET isActive = :active WHERE id = :id")
    suspend fun setPackActive(id: String, active: Boolean)

    @Query("SELECT * FROM chunks WHERE id = :id")
    suspend fun chunk(id: String): ChunkEntity?

    @Query("SELECT * FROM chunks WHERE id IN (:ids)")
    suspend fun chunks(ids: List<String>): List<ChunkEntity>

    @Query("SELECT * FROM chunk_tokens WHERE chunkId IN (:ids)")
    suspend fun tokensFor(ids: List<String>): List<ChunkTokenEntity>

    // Candidate pool: chunks with no card yet, cheapest first by frequency.
    // Only active packs contribute, which is what the deck switch controls.
    @Query(
        "SELECT * FROM chunks WHERE packId IN (SELECT id FROM packs WHERE isActive = 1) " +
            "AND id NOT IN (SELECT DISTINCT chunkId FROM cards) " +
            "ORDER BY freqRank ASC LIMIT :limit"
    )
    suspend fun uintroducedByFrequency(limit: Int): List<ChunkEntity>

    @Query("SELECT COUNT(*) FROM chunks")
    suspend fun chunkCount(): Int

    // How much is left to meet for the first time. The session screen needs this
    // to tell "nothing is due yet" apart from "there is nothing left" — the two
    // look identical in an empty plan, and only one of them means new cards are
    // coming tomorrow.
    @Query(
        "SELECT COUNT(*) FROM chunks WHERE packId IN (SELECT id FROM packs WHERE isActive = 1) " +
            "AND id NOT IN (SELECT DISTINCT chunkId FROM cards)"
    )
    suspend fun untouchedCount(): Int

    @Query(
        "SELECT COUNT(*) FROM chunks WHERE packId = :packId " +
            "AND id NOT IN (SELECT DISTINCT chunkId FROM cards)"
    )
    suspend fun untouchedCountFor(packId: String): Int

    // ---- per deck counters, for the Decks screen -------------------------

    @Query("SELECT COUNT(*) FROM chunks WHERE packId = :packId")
    suspend fun chunkCountFor(packId: String): Int

    @Query(
        "SELECT COUNT(DISTINCT c.chunkId) FROM cards c " +
            "JOIN chunks ch ON ch.id = c.chunkId WHERE ch.packId = :packId"
    )
    suspend fun introducedCountFor(packId: String): Int

    @Query(
        "SELECT COUNT(*) FROM cards c JOIN chunks ch ON ch.id = c.chunkId " +
            "WHERE ch.packId = :packId AND c.level = 0 AND c.stability >= :minStability"
    )
    suspend fun knownCountFor(packId: String, minStability: Double): Int
}

@Dao
interface CardDao {
    @Upsert suspend fun upsert(card: CardEntity)
    @Upsert suspend fun upsertAll(cards: List<CardEntity>)

    @Query("SELECT * FROM cards WHERE chunkId = :chunkId AND level = :level")
    suspend fun card(chunkId: String, level: Int): CardEntity?

    /** Loads exactly the questions the daily plan committed to. */
    @Query("SELECT * FROM cards WHERE (chunkId || ':' || level) IN (:keys)")
    suspend fun byKeys(keys: List<String>): List<CardEntity>

    @Query("SELECT COUNT(*) FROM cards WHERE inAmnesty = 0 AND dueAt <= :until")
    suspend fun dueCount(until: Long): Int

    @Query("SELECT COUNT(*) FROM cards WHERE inAmnesty = 1")
    suspend fun amnestyCount(): Int

    /**
     * Cards that keep coming back: [minLapses] forgettings or more.
     *
     * Anki calls these leeches and suspends them behind the user's back. Here
     * they are only listed, because a phrase that will not stick is information
     * about the phrase, not a verdict on the person answering it.
     */
    @Query(
        "SELECT * FROM cards WHERE lapses >= :minLapses " +
            "ORDER BY lapses DESC, dueAt ASC LIMIT :limit"
    )
    suspend fun leeches(minLapses: Int, limit: Int): List<CardEntity>

    @Query(
        "SELECT * FROM cards WHERE inAmnesty = 0 AND dueAt <= :until " +
            "ORDER BY dueAt ASC LIMIT :limit"
    )
    suspend fun dueCards(until: Long, limit: Int): List<CardEntity>

    @Query("SELECT * FROM cards WHERE inAmnesty = 1 ORDER BY dueAt ASC LIMIT :limit")
    suspend fun amnestyCards(limit: Int): List<CardEntity>

    // "ещё немного": more of what is already due, never anything new.
    @Query(
        "SELECT * FROM cards WHERE inAmnesty = 0 AND dueAt <= :until " +
            "AND (chunkId || ':' || level) NOT IN (:exclude) ORDER BY dueAt ASC LIMIT :limit"
    )
    suspend fun dueCardsExcluding(until: Long, exclude: List<String>, limit: Int): List<CardEntity>

    @Query(
        "SELECT * FROM cards WHERE inAmnesty = 1 " +
            "AND (chunkId || ':' || level) NOT IN (:exclude) ORDER BY dueAt ASC LIMIT :limit"
    )
    suspend fun amnestyCardsExcluding(exclude: List<String>, limit: Int): List<CardEntity>

    // Fuel for "ещё немного" when nothing is due any more: the soonest cards
    // from the future, never unseen ones. Answering a card a day early costs a
    // little scheduling precision and nothing else, which is a fair price for a
    // button that is supposed to always do something.
    @Query(
        "SELECT * FROM cards WHERE inAmnesty = 0 AND isNew = 0 AND dueAt > :after " +
            "AND (chunkId || ':' || level) NOT IN (:exclude) ORDER BY dueAt ASC LIMIT :limit"
    )
    suspend fun upcomingCardsExcluding(
        after: Long,
        exclude: List<String>,
        limit: Int
    ): List<CardEntity>

    // Forecast: how many cards fall due on each of the next days.
    @Query(
        "SELECT COUNT(*) FROM cards WHERE inAmnesty = 0 " +
            "AND dueAt > :from AND dueAt <= :to"
    )
    suspend fun dueBetween(from: Long, to: Long): Int

    /** For the empty state: when does the next card actually come back. */
    @Query("SELECT MIN(dueAt) FROM cards WHERE inAmnesty = 0 AND dueAt > :after")
    suspend fun nextDueAt(after: Long): Long?

    @Query("UPDATE cards SET inAmnesty = 1 WHERE inAmnesty = 0 AND dueAt < :threshold")
    suspend fun moveOverdueToAmnesty(threshold: Long): Int

    /**
     * Moves every schedule forward. This is how an absence is absorbed: unused
     * days are added to every due date, so on return the queue is exactly the
     * queue that was left behind, not weeks of accumulated overdue. Nobody has
     * to announce a break for this to happen — it is measured, not declared.
     */
    @Query(
        "UPDATE cards SET dueAt = dueAt + :ms, " +
            "lastReviewAt = CASE WHEN lastReviewAt IS NULL THEN NULL ELSE lastReviewAt + :ms END"
    )
    suspend fun shiftSchedules(ms: Long)

    @Query("DELETE FROM cards WHERE chunkId = :chunkId AND level = :level")
    suspend fun delete(chunkId: String, level: Int)

    @Query("DELETE FROM cards")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM cards")
    fun cardCountFlow(): Flow<Int>
}

@Dao
interface ReviewDao {
    // Deliberately INSERT-only: no update, no delete, anywhere in the codebase.
    // Undo is itself an insert (see ReviewEntity.undoOf).
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(review: ReviewEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(reviews: List<ReviewEntity>)

    /** Raw log, retractions included. Used by the exporter only. */
    @Query("SELECT * FROM reviews ORDER BY ts ASC")
    suspend fun all(): List<ReviewEntity>

    /** Real answers, newest first. Retracted ones are gone. */
    @Query("SELECT * FROM reviews WHERE " + NOT_RETRACTED + " ORDER BY ts DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ReviewEntity>

    @Query("SELECT * FROM reviews WHERE " + NOT_RETRACTED + " ORDER BY ts ASC")
    suspend fun allAnswers(): List<ReviewEntity>

    /**
     * Every real answer since a moment, oldest first.
     *
     * Retention, minutes spent and the hour of the day are all derived from this
     * one read instead of three SQL aggregates. SQLite would have to be told
     * that a day here starts at 04:00 and that the phone has a timezone; Kotlin
     * already knows both.
     */
    @Query("SELECT * FROM reviews WHERE ts >= :from AND " + NOT_RETRACTED + " ORDER BY ts ASC")
    suspend fun since(from: Long): List<ReviewEntity>

    @Query("SELECT COUNT(*) FROM reviews WHERE ts >= :from AND " + NOT_RETRACTED)
    suspend fun countSince(from: Long): Int

    @Query("SELECT MAX(ts) FROM reviews WHERE " + NOT_RETRACTED)
    suspend fun lastReviewTs(): Long?

    @Query("SELECT COUNT(*) FROM reviews WHERE " + NOT_RETRACTED)
    suspend fun total(): Int

    /**
     * The questions already answered today. This is what makes the session
     * counter monotonic: today's remaining set is the day's plan minus these,
     * so it is identical after a rotation, a tab switch or a process death.
     */
    @Query(
        "SELECT DISTINCT chunkId || ':' || level FROM reviews " +
            "WHERE ts >= :from AND " + NOT_RETRACTED
    )
    suspend fun answeredKeysSince(from: Long): List<String>

    @Query("SELECT * FROM reviews WHERE " + NOT_RETRACTED + " ORDER BY ts DESC LIMIT 1")
    suspend fun lastAnswer(): ReviewEntity?

    /** Recent answer times, newest first: turns "12 left" into "~3 min". */
    @Query(
        "SELECT durationMs FROM reviews WHERE " + NOT_RETRACTED +
            " AND durationMs > 0 ORDER BY ts DESC LIMIT :limit"
    )
    suspend fun recentDurations(limit: Int): List<Long>

    /** Identity used to skip duplicates when restoring from an export file. */
    @Query("SELECT chunkId || ':' || level || ':' || ts FROM reviews")
    suspend fun signatures(): List<String>

    @Query("SELECT * FROM reviews WHERE chunkId = :chunkId AND " + NOT_RETRACTED + " ORDER BY ts DESC")
    suspend fun forChunk(chunkId: String): List<ReviewEntity>
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

    /**
     * The very first day with any recorded activity.
     *
     * Used to keep the load flat while the habit is still forming: a routine
     * takes months to become automatic, so the first two months are not the
     * time to raise the ceiling just because a good week happened.
     */
    @Query("SELECT MIN(day) FROM daily_stats")
    suspend fun firstDay(): String?

    @Query("DELETE FROM daily_stats")
    suspend fun clear()
}

@Dao
interface GovernorDao {
    @Insert suspend fun insert(entry: GovernorLogEntity)

    @Query("SELECT * FROM governor_log ORDER BY ts DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<GovernorLogEntity>

    @Query("SELECT * FROM governor_log WHERE day = :day ORDER BY ts DESC LIMIT 1")
    suspend fun forDay(day: String): GovernorLogEntity?
}

@Dao
interface PlanDao {
    @Upsert suspend fun upsert(plan: DailyPlanEntity)

    @Query("SELECT * FROM daily_plan WHERE day = :day")
    suspend fun plan(day: String): DailyPlanEntity?

    /**
     * The last day a plan was built. Only one row is kept, so this doubles as
     * the marker for "days up to here have already been accounted for", which is
     * what keeps an absence from being repaid twice.
     */
    @Query("SELECT * FROM daily_plan ORDER BY day DESC LIMIT 1")
    suspend fun latest(): DailyPlanEntity?

    @Query("DELETE FROM daily_plan WHERE day <> :day")
    suspend fun clearOtherThan(day: String)

    @Query("DELETE FROM daily_plan")
    suspend fun clear()
}
