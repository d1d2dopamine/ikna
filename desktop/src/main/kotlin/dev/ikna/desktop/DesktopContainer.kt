package dev.ikna.desktop

import dev.ikna.data.db.openIknaDatabase
import dev.ikna.data.pack.PackLoader
import dev.ikna.data.prefs.SETTINGS_DATASTORE_FILE
import dev.ikna.data.prefs.SettingsStore
import dev.ikna.data.prefs.createSettingsDataStore
import dev.ikna.data.prefs.suppressedOf
import dev.ikna.data.repo.ComponentRepository
import dev.ikna.data.repo.DeckRepository
import dev.ikna.data.repo.LearningRepository
import dev.ikna.domain.fsrs.FsrsParams
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.ChunkSelector
import dev.ikna.domain.governor.GovernorConfig
import dev.ikna.platform.ClasspathAssets
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * The desktop dependency container.
 *
 * The same graph AppContainer builds on Android, minus the parts that are an
 * Android implementation rather than a feature: speech, Anki import, the
 * reminder scheduler and the widget. Everything that decides what a card is and
 * when it comes back is the identical object on both platforms, which is the
 * point of the module split -- the scheduler cannot drift between the phone and
 * the computer because there is only one of it.
 *
 * No scheduler migration here: that re-derives FSRS state for histories written
 * by older versions, and a desktop database is created by this version.
 */
class DesktopContainer(val home: File) {

    val config: GovernorConfig = GovernorConfig.load(ClasspathAssets)

    private val db = openIknaDatabase(File(home, "ikna.db"))

    val settings = SettingsStore(createSettingsDataStore(File(home, SETTINGS_DATASTORE_FILE)))

    val packLoader = PackLoader(ClasspathAssets, db.chunkDao())

    val componentRepository = ComponentRepository(
        componentDao = db.componentDao(),
        chunkDao = db.chunkDao(),
        reviewDao = db.reviewDao()
    )

    private val scheduler = Scheduler(
        FsrsParams(desiredRetention = config.desiredRetention),
        dayStartHour = config.dayStartHour
    )

    val learningRepository = LearningRepository(
        cardDao = db.cardDao(),
        chunkDao = db.chunkDao(),
        reviewDao = db.reviewDao(),
        statsDao = db.statsDao(),
        governorDao = db.governorDao(),
        planDao = db.planDao(),
        components = componentRepository,
        scheduler = scheduler,
        selector = ChunkSelector(),
        baseConfig = config
    )

    val deckRepository = DeckRepository(
        chunkDao = db.chunkDao(),
        packLoader = packLoader
    )

    init {
        // The three hooks AppContainer installs. Without them the session
        // builder cannot see hidden cards or the daily load setting.
        learningRepository.suppressedChunks = {
            suppressedOf(settings.flow.first().suppressed).toSet()
        }
        learningRepository.onSuppress = { chunkId -> settings.suppressChunk(chunkId) }
        learningRepository.loadSettings = {
            val s = settings.flow.first()
            LearningRepository.LoadSetting(auto = s.autoLoad, manual = s.manualLoad)
        }
    }

    /** Installs the decks shipped inside the application. Safe to call twice. */
    suspend fun install() {
        packLoader.installBundledPacks()
    }
}
