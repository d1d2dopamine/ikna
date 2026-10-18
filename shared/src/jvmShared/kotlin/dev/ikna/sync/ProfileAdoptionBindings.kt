package dev.ikna.sync

import dev.ikna.data.db.IknaDatabase
import dev.ikna.data.repo.ComponentRepository
import dev.ikna.data.repo.RestoreRepository
import dev.ikna.domain.governor.GovernorConfig
import dev.ikna.domain.fsrs.FsrsParams
import dev.ikna.domain.fsrs.Scheduler

/** A staged database has no live UI/optimizer and uses recorded review parameters on replay. */
fun profileRestoreRepository(db: IknaDatabase, config: GovernorConfig): RestoreRepository = RestoreRepository(
    db, db.cardDao(), db.reviewDao(), db.statsDao(), db.planDao(),
    ComponentRepository(db.componentDao(), db.chunkDao(), db.reviewDao(), db),
    Scheduler(FsrsParams(desiredRetention = config.desiredRetention), dayStartHour = config.dayStartHour),
    config,
)
