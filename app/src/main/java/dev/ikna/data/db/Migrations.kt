package dev.ikna.data.db

import androidx.room.migration.Migration

/**
 * Every schema change gets an explicit migration here.
 *
 * Rules:
 *  - `reviews` may only gain columns. Never rewrite, never drop.
 *  - Derived tables (`components`, `daily_stats`, `governor_log`) may be dropped
 *    and recreated; they are rebuilt from `reviews` by
 *    [dev.ikna.data.repo.ComponentRepository.rebuildFromReviews].
 *  - Content tables (`chunks`, `chunk_tokens`) may be dropped and recreated;
 *    packs are reinstalled from assets on next launch.
 */
object IknaMigrations {
    val ALL: Array<Migration> = arrayOf()
}
