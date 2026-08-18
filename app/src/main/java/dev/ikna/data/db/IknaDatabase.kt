package dev.ikna.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

const val IKNA_DATABASE_VERSION = 3

@Database(
    entities = [
        ChunkEntity::class,
        ChunkTokenEntity::class,
        PackEntity::class,
        CardEntity::class,
        ReviewEntity::class,
        ComponentEntity::class,
        DailyStatEntity::class,
        GovernorLogEntity::class,
        DailyPlanEntity::class
    ],
    // v2: undo snapshots on `reviews`, deck switches on `packs`, `daily_plan`.
    // v3: `daily_stats.correctCount`, so the day's accuracy is a count rather
    //     than a stored average edited in place.
    version = IKNA_DATABASE_VERSION,
    // KSP writes the schema history into app/schemas (see the ksp block in
    // app/build.gradle.kts). Commit whatever appears there after a build: it is
    // the only way to diff two versions of this database, and
    // fallbackToDestructiveMigration is banned below, so a migration that turns
    // out to be wrong cannot be recovered from by deleting the data.
    exportSchema = true
)
abstract class IknaDatabase : RoomDatabase() {
    abstract fun chunkDao(): ChunkDao
    abstract fun cardDao(): CardDao
    abstract fun reviewDao(): ReviewDao
    abstract fun componentDao(): ComponentDao
    abstract fun statsDao(): StatsDao
    abstract fun governorDao(): GovernorDao
    abstract fun planDao(): PlanDao

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
