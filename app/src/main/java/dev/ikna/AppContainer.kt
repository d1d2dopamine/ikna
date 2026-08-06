package dev.ikna

import android.content.Context
import dev.ikna.data.db.IknaDatabase
import dev.ikna.audio.Speaker
import dev.ikna.data.export.JsonExporter
import dev.ikna.data.pack.PackLoader
import dev.ikna.data.prefs.SettingsStore
import dev.ikna.data.repo.ComponentRepository
import dev.ikna.data.repo.DeckRepository
import dev.ikna.data.repo.LearningRepository
import dev.ikna.data.repo.RestoreRepository
import dev.ikna.domain.fsrs.FsrsParams
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.ChunkSelector
import dev.ikna.domain.governor.GovernorConfig
import dev.ikna.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manual dependency container.
 *
 * No Hilt on purpose: with a dozen dependencies a KSP graph costs a minute of CI
 * time per build and buys nothing. This is the whole DI system.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val config: GovernorConfig = GovernorConfig.load(context)

    private val db = IknaDatabase.build(context)

    val settings = SettingsStore(context)

    val packLoader = PackLoader(context, db.chunkDao())

    private val componentRepository = ComponentRepository(
        componentDao = db.componentDao(),
        chunkDao = db.chunkDao(),
        reviewDao = db.reviewDao()
    )

    private val scheduler = Scheduler(FsrsParams(desiredRetention = config.desiredRetention))

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

    val restoreRepository = RestoreRepository(
        cardDao = db.cardDao(),
        reviewDao = db.reviewDao(),
        statsDao = db.statsDao(),
        planDao = db.planDao(),
        components = componentRepository,
        scheduler = scheduler,
        config = config
    )

    val components: ComponentRepository get() = componentRepository

    /**
     * Full wipe, as if the app had just been installed.
     *
     * Room's own clearAllTables does it in one transaction, so a wipe cannot
     * leave half a database behind. Blocking call — keep it off the main thread.
     * The caller is expected to clear settings and restart the process too:
     * singletons and in-memory session state outlive the tables otherwise.
     */
    fun wipeDatabase() {
        db.clearAllTables()
    }

    val jsonExporter = JsonExporter(context, db.reviewDao())

    /**
     * Speech, through whatever engine the phone has. Held here because starting
     * one takes seconds and a per-screen instance would pay that price again on
     * every navigation.
     */
    val speaker = Speaker(context)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // Settings mirror into the repository, so a switch takes effect on the
        // next plan without any screen having to wire anything up. Breaks are
        // deliberately absent from here: the algorithm infers them from the
        // review log, and there is nothing for the user to switch on.
        scope.launch {
            var lastReminder: Triple<Boolean, Int, Int>? = null
            settings.flow.collect { s ->
                learningRepository.autoLoad = s.autoLoad
                if (!s.autoLoad) learningRepository.dailyTargetOverride = s.manualLoad

                // The reminder is scheduled here rather than from the settings
                // screen, so it exists after a reinstall or a reboot even if the
                // user never opens settings. Rescheduling only on an actual
                // change keeps the daily delay from being reset on every emit.
                val reminder = Triple(s.reminderEnabled, s.reminderHour, s.reminderMinute)
                if (reminder != lastReminder) {
                    lastReminder = reminder
                    WorkScheduler.scheduleReminder(
                        appContext,
                        s.reminderEnabled,
                        s.reminderHour,
                        s.reminderMinute
                    )
                }
            }
        }
    }
}
