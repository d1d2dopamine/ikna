package dev.ikna.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

const val IKNA_DATABASE_VERSION = 5

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
    // v4: the nine `governor_log` signals that were being thrown away, and the
    //     full-text index over `chunks` (see ChunkFtsIndex -- it is not a Room
    //     entity on purpose).
    // v5: the two transcription columns on `chunks`.
    // Kept as a literal because SchemaTest deliberately reads this source line:
    // changing it must force a migration and a committed Room schema.
    version = 5,
    // KSP writes the schema history into app/schemas (see the ksp block in
    // shared/build.gradle.kts -- it stays under app/ so that the workflow step
    // that uploads it and the one that checks it is committed do not move).
    // Commit whatever appears there after a build: it is the only way to diff
    // two versions of this database, and fallbackToDestructiveMigration is
    // banned below, so a migration that turns out to be wrong cannot be
    // recovered from by deleting the data.
    exportSchema = true
)
@ConstructedBy(IknaDatabaseConstructor::class)
abstract class IknaDatabase : RoomDatabase() {
    abstract fun chunkDao(): ChunkDao
    abstract fun cardDao(): CardDao
    abstract fun reviewDao(): ReviewDao
    abstract fun componentDao(): ComponentDao
    abstract fun statsDao(): StatsDao
    abstract fun governorDao(): GovernorDao
    abstract fun planDao(): PlanDao
}

/**
 * Room generates the actual object for each platform.
 *
 * The database used to be built by a companion `build(context)` that named
 * Android's Room.databaseBuilder directly. A database that two applications open
 * cannot know what a Context is, so the construction moved out: the shape is
 * declared here, the driver and the file path are decided per platform in
 * IknaDatabase.android.kt and IknaDatabase.desktop.kt, and everything they have
 * in common -- the migrations, the search index callback, the ban on destructive
 * fallback -- is in IknaDatabaseFactory.kt, once.
 */
@Suppress("KotlinNoActualForExpect")
expect object IknaDatabaseConstructor : RoomDatabaseConstructor<IknaDatabase> {
    override fun initialize(): IknaDatabase
}
