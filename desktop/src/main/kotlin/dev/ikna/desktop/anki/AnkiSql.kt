package dev.ikna.desktop.anki

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement

/**
 * Row reading over androidx.sqlite, which is what a window has instead of
 * android.database.
 *
 * The phone reads an Anki collection through SQLiteDatabase.rawQuery and a
 * Cursor. Neither exists off Android, so the same queries run here against the
 * bundled SQLite that Room already uses for ikna's own database -- the driver is
 * compiled into the application, so a machine with no SQLite of its own is not a
 * special case.
 *
 * Every read is defensive on purpose. This file is pointed at a database written
 * by another program, several versions of it, and a column that is missing, null
 * or of the wrong type has to end as an empty value rather than as an exception
 * three layers up: the import either refuses the file with a reason or reads
 * what it can, and a NullPointerException is neither.
 */
internal class AnkiSql(private val connection: SQLiteConnection) {

    /** Every row the statement yields, mapped, skipping rows that map to null. */
    fun <T : Any> rows(
        sql: String,
        args: List<String> = emptyList(),
        limit: Int = Int.MAX_VALUE,
        read: (SQLiteStatement) -> T?
    ): List<T> {
        val out = ArrayList<T>()
        val statement = connection.prepare(sql)
        try {
            args.forEachIndexed { index, value -> statement.bindText(index + 1, value) }
            var seen = 0
            while (seen < limit && statement.step()) {
                seen++
                val row = runCatching { read(statement) }.getOrNull()
                if (row != null) out += row
            }
        } finally {
            runCatching { statement.close() }
        }
        return out
    }

    /** The first column of the first row as a number, or zero. */
    fun scalarLong(sql: String): Long {
        val statement = connection.prepare(sql)
        try {
            return if (statement.step()) long(statement, 0) else 0L
        } finally {
            runCatching { statement.close() }
        }
    }

    fun hasTable(name: String): Boolean = rows(
        sql = "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
        args = listOf(name)
    ) { 1 }.isNotEmpty()

    companion object {

        fun text(statement: SQLiteStatement, index: Int): String =
            if (statement.isNull(index)) ""
            else runCatching { statement.getText(index) }.getOrDefault("")

        fun long(statement: SQLiteStatement, index: Int): Long =
            if (statement.isNull(index)) 0L
            else runCatching { statement.getLong(index) }.getOrDefault(0L)

        fun int(statement: SQLiteStatement, index: Int): Int = long(statement, index).toInt()

        fun blob(statement: SQLiteStatement, index: Int): ByteArray =
            if (statement.isNull(index)) ByteArray(0)
            else runCatching { statement.getBlob(index) }.getOrDefault(ByteArray(0))
    }
}
