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

    @Query("UPDATE packs SET lang = :lang WHERE id = :id")
    suspend fun setPackLang(id: String, lang: String)

    // Renaming touches the title column and nothing else. The id was derived
    // from the file name once, at import, and every chunk, card and review row
    // hangs off it -- a rename that regenerated the id would look like a
    // rename and behave like deleting the deck and importing an empty copy.
    @Query("UPDATE packs SET title = :title WHERE id = :id")
    suspend fun setPackTitle(id: String, title: String)

    // Deleting a deck, in the order the rows depend on each other: cards, then
    // tokens, then chunks, then the pack itself. The `reviews` table is never
    // touched by any of this - it is append-only and it is what the statistics
    // are computed from, so a deck deleted in a tidying mood must not take
    // months of history with it.
    @Query(
        "DELETE FROM cards WHERE chunkId IN " +
            "(SELECT id FROM chunks WHERE packId = :packId)"
    )
    suspend fun deleteCardsForPack(packId: String)

    @Query(
        "DELETE FROM chunk_tokens WHERE chunkId IN " +
            "(SELECT id FROM chunks WHERE packId = :packId)"
    )
    suspend fun deleteTokensForPack(packId: String)

    @Query("DELETE FROM chunks WHERE packId = :packId")
    suspend fun deleteChunksForPack(packId: String)

    @Query("DELETE FROM packs WHERE id = :id")
    suspend fun deletePack(id: String)

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
    suspend fun unintroducedByFrequency(limit: Int): List<ChunkEntity>

    // The same pool inside one deck, and deliberately without the isActive
    // filter. This is only ever asked because the user opened that deck and
    // asked it for more, which is a plainer statement of intent than the switch
    // on the list is.
    @Query(
        "SELECT * FROM chunks WHERE packId = :packId " +
            "AND id NOT IN (SELECT DISTINCT chunkId FROM cards) " +
            "ORDER BY freqRank ASC LIMIT :limit"
    )
    suspend fun unintroducedByFrequencyFor(packId: String, limit: Int): List<ChunkEntity>

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

    // Same question as untouchedCount(), asked about one deck. It has to apply
    // the same isActive filter, or the deck list and the session screen disagree
    // about whether a switched-off deck still has anything left in it.
    @Query(
        "SELECT COUNT(*) FROM chunks WHERE packId = :packId " +
            "AND packId IN (SELECT id FROM packs WHERE isActive = 1) " +
            "AND id NOT IN (SELECT DISTINCT chunkId FROM cards)"
    )
    suspend fun untouchedCountFor(packId: String): Int

    // ---- per deck counters, for the Decks screen -------------------------

    /**
     * Every chunk of one deck, in the order the deck itself is in. Used by the
     * share sheet: a deck leaves this phone as the same three columns it
     * arrived as, so what one person sends another person can import.
     */
    @Query("SELECT * FROM chunks WHERE packId = :packId ORDER BY freqRank ASC")
    suspend fun chunksForPack(packId: String): List<ChunkEntity>

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

    /** Every scheduled question, for an algorithm migration performed in memory. */
    @Query("SELECT * FROM cards ORDER BY chunkId ASC, level ASC")
    suspend fun all(): List<CardEntity>

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

    // The same three questions, asked inside one deck.
    //
    // Pressed inside a deck, "a few more" used to take fifty candidates from the
    // whole collection and then throw away everything belonging to another deck.
    // Whenever those fifty came from elsewhere the button returned nothing and
    // reported that nothing was due - in a deck that was full of it.
    @Query(
        "SELECT c.* FROM cards c JOIN chunks ch ON ch.id = c.chunkId " +
            "WHERE ch.packId = :packId AND c.inAmnesty = 0 AND c.dueAt <= :until " +
            "AND (c.chunkId || ':' || c.level) NOT IN (:exclude) " +
            "ORDER BY c.dueAt ASC LIMIT :limit"
    )
    suspend fun dueCardsForPackExcluding(
        packId: String,
        until: Long,
        exclude: List<String>,
        limit: Int
    ): List<CardEntity>

    @Query(
        "SELECT c.* FROM cards c JOIN chunks ch ON ch.id = c.chunkId " +
            "WHERE ch.packId = :packId AND c.inAmnesty = 1 " +
            "AND (c.chunkId || ':' || c.level) NOT IN (:exclude) " +
            "ORDER BY c.dueAt ASC LIMIT :limit"
    )
    suspend fun amnestyCardsForPackExcluding(
        packId: String,
        exclude: List<String>,
        limit: Int
    ): List<CardEntity>

    @Query(
        "SELECT c.* FROM cards c JOIN chunks ch ON ch.id = c.chunkId " +
            "WHERE ch.packId = :packId AND c.inAmnesty = 0 AND c.isNew = 0 " +
            "AND c.dueAt > :after " +
            "AND (c.chunkId || ':' || c.level) NOT IN (:exclude) " +
            "ORDER BY c.dueAt ASC LIMIT :limit"
    )
    suspend fun upcomingCardsForPackExcluding(
        packId: String,
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

    // There is deliberately no "shift every schedule forward" query here any
    // more. An absence is absorbed by the amnesty pool instead: overdue cards
    // leave the visible queue and are drip-fed back a fifth of a session at a
    // time. Rewriting dueAt in bulk also rewrote lastReviewAt, which is the
    // input FSRS uses to work out how much time really passed — so the
    // scheduler was being told a lie about the one thing it measures, and the
    // lie was nowhere in the review log, which meant a restore from that log
    // produced different dates than the phone had.

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

    /**
     * Bulk insert for restore, returning the id assigned to each row in order.
     *
     * Rows are passed in with `id = 0` and numbered by SQLite, because the ids
     * in an export file belong to the phone that wrote it. The returned ids are
     * what lets a retraction from that file be re-pointed at the right row here.
     * ABORT rather than IGNORE: with generated ids there is nothing left to
     * collide, and silently dropping rows from the one irreplaceable table is
     * not an acceptable failure mode.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(reviews: List<ReviewEntity>): List<Long>

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

    /**
     * The same identity, with the id it belongs to here.
     *
     * A restore needs both: the signature to recognise an answer it already
     * has, and this database's id so a retraction that arrives in the same file
     * can be attached to it.
     */
    @Query("SELECT id, (chunkId || ':' || level || ':' || ts) AS signature FROM reviews")
    suspend fun keys(): List<ReviewKey>

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

/** One row's identity, for restore. See [ReviewDao.keys]. */
data class ReviewKey(val id: Long, val signature: String)

@Dao
interface StatsDao {
    @Upsert suspend fun upsert(stat: DailyStatEntity)

    /** Bulk form, for the replay that rebuilds this table from the log. */
    @Upsert suspend fun upsertAll(stats: List<DailyStatEntity>)

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
