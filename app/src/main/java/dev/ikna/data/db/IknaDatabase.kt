package dev.ikna.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChunkEntity::class,
        ChunkTokenEntity::class,
        PackEntity::class,
        CardEntity::class,
        ReviewEntity::class,
        ComponentEntity::class,
        DailyStatEntity::class,
        GovernorLogEntity::class
    ],
    version = 1,
    // Schemas are committed under app/schemas so migrations stay reproducible.
    exportSchema = true
)
abstract class IknaDatabase : RoomDatabase() {
    abstract fun chunkDao(): ChunkDao
    abstract fun cardDao(): CardDao
    abstract fun reviewDao(): ReviewDao
    abstract fun componentDao(): ComponentDao
    abstract fun statsDao(): StatsDao
    abstract fun governorDao(): GovernorDao

    companion object {
        fun build(context: Context): IknaDatabase =
            Room.databaseBuilder(context, IknaDatabase::class.java, "ikna.db")
                .addMigrations(*IknaMigrations.ALL)
                // NOTE: fallbackToDestructiveMigration() is banned in this project.
                // It is the one line that silently deletes the user history on a
                // schema change. If a migration is missing the app must crash
                // loudly in development instead of losing data in production.
                .build()
    }
}
