package dev.ikna

import android.content.Context
import dev.ikna.data.db.IknaDatabase
import dev.ikna.data.export.JsonExporter
import dev.ikna.data.pack.PackLoader
import dev.ikna.data.repo.ComponentRepository
import dev.ikna.data.repo.LearningRepository
import dev.ikna.domain.fsrs.FsrsParams
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.ChunkSelector
import dev.ikna.domain.governor.GovernorConfig

/**
 * Manual dependency container.
 *
 * No Hilt on purpose: with eight dependencies a KSP graph costs a minute of CI
 * time per build and buys nothing. This is the whole DI system.
 */
class AppContainer(context: Context) {

    val config: GovernorConfig = GovernorConfig.load(context)

    private val db = IknaDatabase.build(context)

    val packLoader = PackLoader(context, db.chunkDao())

    private val componentRepository = ComponentRepository(
        componentDao = db.componentDao(),
        chunkDao = db.chunkDao(),
        reviewDao = db.reviewDao()
    )

    val learningRepository = LearningRepository(
        cardDao = db.cardDao(),
        chunkDao = db.chunkDao(),
        reviewDao = db.reviewDao(),
        statsDao = db.statsDao(),
        governorDao = db.governorDao(),
        components = componentRepository,
        scheduler = Scheduler(FsrsParams(desiredRetention = config.desiredRetention)),
        selector = ChunkSelector(),
        config = config
    )

    val components: ComponentRepository get() = componentRepository

    val jsonExporter = JsonExporter(context, db.reviewDao())
}
