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
import dev.ikna.data.repo.RestoreRepository
import dev.ikna.desktop.anki.AnkiImporter
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
 * Android implementation rather than a feature: speech, the reminder scheduler
 * and the widget. Anki import is no longer on that list -- it was there because
 * the reader was written against android.database, not because importing a file
 * is a phone activity, and an .apkg is nearly always already on a computer.
 * Everything that decides what a card is and
 * when it comes back is the identical object on both platforms, which is the
 * point of the module split -- the scheduler cannot drift between the phone and
 * the computer because there is only one of it.
 *
 * No scheduler migration here: that re-derives FSRS state for histories written
 * by older versions, and a desktop database is created by this version.
 */
class DesktopContainer(val home: File) {

    val config: GovernorConfig = GovernorConfig.load(ClasspathAssets)

    internal val db = openIknaDatabase(File(home, "ikna.db"))

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

    /**
     * Replaying a review log, which an Anki import is a special case of.
     *
     * Anki's own intervals are not carried across -- its scheduler is not this
     * one -- so the history is replayed through ikna's scheduler instead, and
     * the schedule that comes out is the schedule this app would have produced
     * had the answers been given here.
     */
    val restoreRepository = RestoreRepository(
        cardDao = db.cardDao(),
        reviewDao = db.reviewDao(),
        statsDao = db.statsDao(),
        planDao = db.planDao(),
        components = componentRepository,
        scheduler = scheduler,
        config = config
    )

    val ankiImporter = AnkiImporter(
        db = db,
        chunkDao = db.chunkDao(),
        packs = packLoader,
        restore = restoreRepository,
        // Unpacked next to the database rather than in the system temporary
        // folder: a 300 MB package should fail on the disk the user chose for
        // ikna's data, not on whichever partition holds /tmp.
        cache = File(home, "cache").also { it.mkdirs() }
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

    // Main installs the decks before the window opens and the shell asks again
    // from the composition. The second ask is a no-op rather than a second
    // pass over the manifest.
    private var installed = false

    /** Installs the decks shipped inside the application. Safe to call twice. */
    suspend fun install() {
        if (installed) return
        packLoader.installBundledPacks()
        installed = true
    }
}
