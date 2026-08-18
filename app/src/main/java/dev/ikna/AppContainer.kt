package dev.ikna

import android.content.Context
import dev.ikna.data.db.IknaDatabase
import dev.ikna.audio.Speaker
import dev.ikna.audio.VoiceInstaller
import dev.ikna.audio.VoiceModelStore
import dev.ikna.data.export.JsonExporter
import dev.ikna.data.pack.PackLoader
import dev.ikna.data.prefs.SettingsStore
import dev.ikna.data.repo.ComponentRepository
import dev.ikna.data.repo.DeckRepository
import dev.ikna.data.repo.LearningRepository
import dev.ikna.data.repo.RestoreRepository
import dev.ikna.data.repo.SchedulerMigration
import dev.ikna.data.repo.SchedulerMigrationState
import dev.ikna.domain.fsrs.FsrsParams
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.ChunkSelector
import dev.ikna.domain.governor.GovernorConfig
import dev.ikna.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import dev.ikna.data.prefs.suppressedOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
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

    // The day start hour is passed in so that intervals land on study days
    // rather than on the clock time an answer happened to be given -- see
    // Scheduler.dueAt.
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

    val restoreRepository = RestoreRepository(
        cardDao = db.cardDao(),
        reviewDao = db.reviewDao(),
        statsDao = db.statsDao(),
        planDao = db.planDao(),
        components = componentRepository,
        scheduler = scheduler,
        config = config
    )

    private val schedulerMigrator = SchedulerMigration(
        db = db,
        cardDao = db.cardDao(),
        reviewDao = db.reviewDao(),
        planDao = db.planDao(),
        settings = settings,
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

    /** The models on disk. One instance, shared by the screen and the installer. */
    val voiceModels = VoiceModelStore(context)

    /**
     * Adding a model, held here rather than by the screen that started it.
     *
     * Unpacking a Kokoro release takes minutes, and until this existed those
     * minutes belonged to the voice screen's composition: a back press
     * cancelled the copy, deleted what had been written, and reported
     * nothing.
     */
    val voiceInstaller = VoiceInstaller(voiceModels)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _schedulerMigration = MutableStateFlow<SchedulerMigrationState>(
        SchedulerMigrationState.Running
    )
    val schedulerMigration: StateFlow<SchedulerMigrationState> =
        _schedulerMigration.asStateFlow()
    private var schedulerMigrationJob: Job? = null

    init {
        // No repository is allowed to expose a card until its state belongs to
        // FSRS-6. The activity draws a small launch gate while this runs, and
        // workers await the same state; there is one migration and no race
        // between a cold-start screen and the nightly plan.
        startSchedulerMigration()

        // The load switch, readable on demand instead of waited for. The mirror
        // below still exists, because a switch flipped while a session is open
        // should reach the next plan without anyone asking; this is what makes a
        // plan built before any screen is alive -- the nightly worker, a cold
        // start -- use the user's own norm rather than the file default.
        // The learner's own corrections, read straight from storage for the same
        // reason the load switch is: a plan can be built in a process where no
        // screen has collected anything yet.
        learningRepository.suppressedChunks = {
            suppressedOf(settings.flow.first().suppressed).toSet()
        }
        learningRepository.onSuppress = { chunkId -> settings.suppressChunk(chunkId) }

        learningRepository.loadSettings = {
            val stored = settings.flow.first()
            LearningRepository.LoadSetting(
                auto = stored.autoLoad,
                manual = stored.manualLoad
            )
        }

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

    /** Retry is offered only after failure; an active migration is never doubled. */
    @Synchronized
    fun startSchedulerMigration() {
        if (schedulerMigrationJob?.isActive == true) return
        _schedulerMigration.value = SchedulerMigrationState.Running
        schedulerMigrationJob = scope.launch(Dispatchers.IO) {
            _schedulerMigration.value = runCatching { schedulerMigrator.runIfNeeded() }
                .fold(
                    onSuccess = { SchedulerMigrationState.Ready(it.migratedCards) },
                    onFailure = {
                        SchedulerMigrationState.Failed(
                            it.message ?: it::class.java.simpleName
                        )
                    }
                )
        }
    }

    /** Background work uses the same gate as the UI. */
    suspend fun awaitSchedulerReady() {
        when (val state = schedulerMigration.first {
            it is SchedulerMigrationState.Ready || it is SchedulerMigrationState.Failed
        }) {
            is SchedulerMigrationState.Ready -> Unit
            is SchedulerMigrationState.Failed -> error(state.reason)
            SchedulerMigrationState.Running -> error("unreachable")
        }
    }
}
