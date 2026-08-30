package dev.ikna.data.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * The full-text index over installed content, as plain SQL.
 *
 * Deck search used to be `LIKE '%term%'` over every installed chunk. That is
 * the right amount of machinery for a few thousand phrases and the wrong shape
 * for the catalogue this app now offers: a leading wildcard cannot use an index,
 * so the query reads every row and gets slower in proportion to how much the
 * user has installed. Searching is also the one place where the app asks
 * somebody to wait while typing.
 *
 * Why this is hand-written SQL rather than a Room `@Fts4` entity:
 *
 * Room validates the schema it finds against the schema it generated, at open
 * time, and refuses to open the database when they disagree. A virtual table
 * created by a migration is DDL that Room did not generate, and the shape SQLite
 * reports back for it does not match what Room expects, so the app crashes on
 * launch for everybody who upgrades -- and `fallbackToDestructiveMigration` is
 * banned here, correctly, so that crash has no recovery except a new release.
 *
 * The price of doing it by hand is that the search query needs
 * `@SkipQueryVerification` and that this file, not Room, is responsible for
 * keeping the index in step. The triggers below do that, the read path treats a
 * missing or unparseable index as "fall back to the scan", and the scan is still
 * there. So the worst case is slow search rather than an app that will not open.
 */
object ChunkFtsIndex {

    const val TABLE = "chunks_fts"

    /**
     * `content=chunks` makes this an external-content index: the text is not
     * duplicated, only the terms are. `unicode61` is what makes case and accent
     * folding work for the six interface languages and for the content ones.
     */
    private const val CREATE_TABLE =
        "CREATE VIRTUAL TABLE IF NOT EXISTS chunks_fts USING fts4(" +
            "text, contextSentence, translation, " +
            "tokenize=unicode61, content=chunks)"

    /**
     * An external-content index is not maintained by SQLite. Without these,
     * installing a deck would leave it unsearchable and deleting one would leave
     * its phrases in the results forever. Delete-before and insert-after is the
     * documented pattern: an UPDATE has to remove the old terms before adding
     * the new ones.
     */
    private val TRIGGERS = listOf(
        "CREATE TRIGGER IF NOT EXISTS chunks_fts_before_update " +
            "BEFORE UPDATE ON chunks BEGIN " +
            "DELETE FROM chunks_fts WHERE docid = OLD.rowid; END",
        "CREATE TRIGGER IF NOT EXISTS chunks_fts_before_delete " +
            "BEFORE DELETE ON chunks BEGIN " +
            "DELETE FROM chunks_fts WHERE docid = OLD.rowid; END",
        "CREATE TRIGGER IF NOT EXISTS chunks_fts_after_update " +
            "AFTER UPDATE ON chunks BEGIN " +
            "INSERT INTO chunks_fts(docid, text, contextSentence, translation) " +
            "VALUES (NEW.rowid, NEW.text, NEW.contextSentence, NEW.translation); END",
        "CREATE TRIGGER IF NOT EXISTS chunks_fts_after_insert " +
            "AFTER INSERT ON chunks BEGIN " +
            "INSERT INTO chunks_fts(docid, text, contextSentence, translation) " +
            "VALUES (NEW.rowid, NEW.text, NEW.contextSentence, NEW.translation); END"
    )

    /**
     * Creates the index, its triggers, and fills it from whatever is already
     * installed. Called from the 3 -> 4 migration and from Room's onCreate, so
     * an upgrade and a fresh install end up in the same state.
     */
    fun create(db: SQLiteConnection) {
        db.execSQL(CREATE_TABLE)
        TRIGGERS.forEach { db.execSQL(it) }
        rebuild(db)
    }

    /**
     * Rebuilds every term from the `chunks` table. This is the repair operation:
     * it is correct to call at any time and it is what makes a half-written
     * index recoverable without touching user data.
     */
    fun rebuild(db: SQLiteConnection) {
        db.execSQL("INSERT INTO chunks_fts(chunks_fts) VALUES('rebuild')")
    }
}
