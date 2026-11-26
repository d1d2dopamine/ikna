package dev.ikna.desktop

import dev.ikna.data.repo.prepareFirstSession

import kotlinx.coroutines.cancelAndJoin
import dev.ikna.data.db.openIknaDatabase
import dev.ikna.data.db.wipeAllData
import dev.ikna.data.dev.DATA_PROFILE_FILE
import dev.ikna.data.dev.DEVELOPER_SETTINGS_FILE
import dev.ikna.data.dev.DataProfileStore
import dev.ikna.data.dev.DeveloperAccess
import dev.ikna.data.dev.DeveloperSandboxSeeder
import dev.ikna.data.dev.DeveloperScenario
import dev.ikna.data.dev.IknaDataProfile
import dev.ikna.data.dev.databaseFileName
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
class DesktopContainer(
    val home: File,
    val dataProfile: IknaDataProfile = DataProfileStore(File(home, DATA_PROFILE_FILE)).current()
) {

    val profileStore = DataProfileStore(File(home, DATA_PROFILE_FILE))
    val isDeveloperMode: Boolean get() = dataProfile == IknaDataProfile.DEVELOPER

    val config: GovernorConfig = GovernorConfig.load(ClasspathAssets)

    private val legacyDatabaseFile = File(home, databaseFileName(dataProfile))
    val syncProfileSlots = dev.ikna.sync.ProfileSlotStore(home, legacyDatabaseFile)
    private val selectedSyncProfile = if (isDeveloperMode) null else syncProfileSlots.selection()
    private val activeDatabaseFile = selectedSyncProfile?.active ?: legacyDatabaseFile
    val profileRestartGate = dev.ikna.sync.ProfileRestartGate()
    internal val db = openIknaDatabase(activeDatabaseFile)

    val settings = SettingsStore(
        createSettingsDataStore(
            File(home, if (isDeveloperMode) DEVELOPER_SETTINGS_FILE else SETTINGS_DATASTORE_FILE)
        )
    )
    private val optimizerScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
    val optimizer = dev.ikna.data.repo.LocalOptimizer(settings,
        FsrsParams(desiredRetention = config.desiredRetention), optimizerScope,
        loadHistory = {
            val cutoff = db.historyBoundaryDao().current()?.afterLocalId ?: 0L
            db.reviewDao().optimizerHistoryAfter(cutoff, dev.ikna.domain.fsrs.FsrsOptimizer.MAX_ANSWERS, System.currentTimeMillis())
        })

    val packLoader = PackLoader(ClasspathAssets, db.chunkDao())

    suspend fun importCatalogDeck(packId: String, title: String, lang: String, text: String) =
        dev.ikna.data.catalog.importCatalogDeck(db, packLoader, packId, title, lang, text)

    val componentRepository = ComponentRepository(
        componentDao = db.componentDao(),
        chunkDao = db.chunkDao(),
        reviewDao = db.reviewDao(),
        db = db
    )

    private val scheduler = Scheduler(
        FsrsParams(desiredRetention = config.desiredRetention),
        dayStartHour = config.dayStartHour,
        paramsProvider = optimizer::parameters
    )

    val learningRepository = LearningRepository(
        db = db,
        cardDao = db.cardDao(),
        chunkDao = db.chunkDao(),
        reviewDao = db.reviewDao(),
        statsDao = db.statsDao(),
        governorDao = db.governorDao(),
        planDao = db.planDao(),
        browseDao = db.browseDao(),
        components = componentRepository,
        scheduler = scheduler,
        selector = ChunkSelector(),
        baseConfig = config
    )

    val sessionChanges = dev.ikna.domain.session.CommittedSessionChanges()

    val studyStatsRepository = dev.ikna.data.repo.StudyStatsRepository(db, config.dayStartHour)

    val deckRepository = DeckRepository(
        database = db,
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
        db = db,
        cardDao = db.cardDao(),
        reviewDao = db.reviewDao(),
        statsDao = db.statsDao(),
        planDao = db.planDao(),
        components = componentRepository,
        scheduler = scheduler,
        config = config,
        beforeExternalRestore = {
            if (!isDeveloperMode) {
                dev.ikna.data.export.LocalSafetySnapshot.capture(home, db.reviewDao(), "restore")
            }
            deviceSync.beforeExternalRestore()
        },
        afterExternalRestore = { generation -> deviceSync.afterExternalRestore(generation) }
    )

    val activeDatabaseSizeKb: Long
        get() = runCatching { activeDatabaseFile.length() / 1024L }.getOrDefault(-1L)

    val safetySnapshotCount: Int
        get() = dev.ikna.data.export.LocalSafetySnapshot.count(home)

    suspend fun totalReviewCount(): Int = db.reviewDao().total()

    val deviceSync: dev.ikna.sync.DeviceSyncController by lazy {
        dev.ikna.sync.DeviceSyncController(
            File(home, "device-sync"), restoreRepository,
            onMerged = { sessionChanges.notifyCommitted() }, available = !isDeveloperMode,
            currentDatabaseSlot = syncProfileSlots.slotId(activeDatabaseFile),
            canRecoverProfile = selectedSyncProfile?.recovery != null,
            currentSlotOperationId = selectedSyncProfile?.operationId,
            currentSlotOperationDigest = selectedSyncProfile?.operationDigest)
    }

    private val profileAdoption by lazy {
        dev.ikna.sync.ProfileAdoptionService(
            slots = syncProfileSlots,
            liveRepository = restoreRepository,
            openNewDatabase = { openIknaDatabase(it) },
            createRestoreRepository = { dev.ikna.sync.profileRestoreRepository(it, config) },
            withLiveWriteGuard = { block -> learningRepository.withJournalWriteLock(block) },
            quiesceAndCloseLive = {
                profileRestartGate.awaitUiQuiescence()
                optimizerScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin()
                db.close()
            },
            prepareActivation = { candidate, opId, digest ->
                deviceSync.prepareProfileActivation(syncProfileSlots.slotId(candidate), opId, digest)
            },
            prepareRecovery = { recoveryOperation ->
                val recovered = checkNotNull(syncProfileSlots.selection().recovery)
                deviceSync.prepareProfileRecovery(syncProfileSlots.slotId(recovered), recoveryOperation)
            }
        )
    }

    init {
        deviceSync.isRestartRequired = { profileRestartGate.required.value }
        deviceSync.recoveryHandler = {
            try { profileAdoption.recover(java.util.UUID.randomUUID().toString()) }
            finally { profileRestartGate.finish() }
        }
        deviceSync.adoptionHandler = { request ->
            try {
                profileAdoption.adopt(request.incoming, request.preview.localRevision, request.proposal.operationId,
                    dev.ikna.sync.ProfileConsentProtocol.tupleDigest(request.proposal), request.afterLocalId, request.boundaryToken, request.journalAuthorization)
            } finally { profileRestartGate.finish() }
        }
        learningRepository.syncHistoryBoundary = { deviceSync.undoBoundary() }
        if (!isDeveloperMode) learningRepository.historyBoundaryToken = { deviceSync.boundaryToken() }
        learningRepository.ensureHistoryWritable = { deviceSync.ensureHistoryWritable() }
        restoreRepository.journalMergeGuard = { block -> learningRepository.withJournalWriteLock(block) }
        restoreRepository.historyBoundary = { deviceSync.historyBoundary() }
        componentRepository.historyBoundary = { deviceSync.historyBoundary() }
    }

    suspend fun resetProgress() {
        if (!isDeveloperMode) {
            dev.ikna.data.export.LocalSafetySnapshot.capture(home, db.reviewDao(), "reset")
        }
        val mutation = deviceSync.beforeHistoryChange()
        val boundary = learningRepository.resetProgressWithBoundary()
        deviceSync.afterProgressReset(boundary, mutation)
        sessionChanges.notifyCommitted()
    }

    val developerTools: dev.ikna.data.dev.DeveloperTools? by lazy {
        if (isDeveloperMode) dev.ikna.data.dev.DeveloperTools(dataProfile, learningRepository, settings) else null
    }

    val developerSandbox: DeveloperSandboxSeeder? = if (isDeveloperMode) {
        DeveloperSandboxSeeder(
            db = db,
            settings = settings,
            components = componentRepository,
            config = config
        )
    } else null

    private val scenarioRequests = dev.ikna.data.dev.PendingDeveloperScenarioStore(
        File(home, dev.ikna.data.dev.DEVELOPER_SCENARIO_REQUEST_FILE)
    )
    fun requestDeveloperScenario(scenario: DeveloperScenario) {
        check(isDeveloperMode)
        scenarioRequests.set(scenario)
    }
    fun cancelDeveloperScenarioRequest() = scenarioRequests.clear()

    fun requestDataProfile(profile: IknaDataProfile) {
        profileStore.set(profile)
    }

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
        // The shared hooks AppContainer installs. Without them the session
        // builder cannot see hidden cards, the load setting or Browse credit.
        learningRepository.suppressedChunks = {
            suppressedOf(settings.flow.first().suppressed).toSet()
        }
        learningRepository.onSuppress = { chunkId -> settings.suppressChunk(chunkId) }
        learningRepository.settleBrowseCreditPoints = { day, completed, exposures ->
            settings.settleBrowseCredits(day, completed, exposures).availablePoints
        }
        learningRepository.clearBrowseCredits = { settings.clearBrowseCredits() }
        learningRepository.developerAccess = {
            DeveloperAccess.forProfile(dataProfile, settings.current().developerApplyProductLimits)
        }
        learningRepository.derivedGradingEnabled = { dev.ikna.domain.optimizer.AutomaticLearningPolicy.DERIVED_WHEN_READY }
        learningRepository.loadSettings = {
            val s = settings.flow.first()
            LearningRepository.LoadSetting(auto = s.autoLoad, manual = s.manualLoad)
        }
    }

    /**
     * Finishes the same first-run contract as Android: make sure the bundled
     * deck exists, build the first finite plan, and only then dismiss the
     * introduction. Safe after a full wipe because bundled packs are upserts.
     */
    suspend fun completeOnboarding() {
        prepareFirstSession(
            installPacks = {
                if (!isDeveloperMode || settings.current().developerScenario != DeveloperScenario.EMPTY.id) {
                    packLoader.installBundledPacks()
                }
            },
            preparePlan = { learningRepository.ensureDailyPlan() },
            markComplete = { settings.setOnboardingDone(true) }
        )
    }

    /**
     * Full wipe, as if the desktop app had just been installed.
     *
     * Deleting decks one by one deliberately preserves the append-only review
     * log, so it can never implement "erase everything". The shared KMP wipe
     * DAO clears learner data transactionally per database, preserving only slot
     * provenance. Multiple retained databases are not one atomic wipe. Preferences
     * are cleared after success so first-run returns to onboarding. The
     * caller keeps the operation off the UI thread.
     */
    suspend fun wipeAllData() {
        if (!isDeveloperMode) {
            dev.ikna.data.export.LocalSafetySnapshot.capture(home, db.reviewDao(), "wipe")
        }
        val mutation = deviceSync.beforeHistoryChange()
        optimizerScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin()
        learningRepository.withJournalWriteLock {
            if (isDeveloperMode) db.wipeAllData()
            else dev.ikna.sync.ProfileRetainedDataWiper(
                syncProfileSlots, activeDatabaseFile, db,
                openDatabase = { openIknaDatabase(it) }
            ).wipeAllProfiles()
        }
        deviceSync.afterProgressReset(0, mutation)
        settings.clearAll()
    }

    // Main installs the decks before the window opens and the shell asks again
    // from the composition. The second ask is a no-op rather than a second
    // pass over the manifest.
    private var installed = false

    /** Installs the decks shipped inside the application. Safe to call twice. */
    suspend fun install() {
        if (installed) return
        if (!isDeveloperMode) deviceSync.initializeProfileSlot()
        if (isDeveloperMode) {
            val requested = scenarioRequests.current()
            if (requested != null) {
                requireNotNull(developerSandbox).seed(requested)
                scenarioRequests.clear()
            } else if (!settings.current().onboardingDone) {
                developerSandbox?.seed(DeveloperScenario.MATURE_HISTORY)
            }
        }
        optimizer.initialize()
        if (!isDeveloperMode || settings.current().developerScenario != DeveloperScenario.EMPTY.id) {
            packLoader.installBundledPacks()
        }
        installed = true
        optimizer.startAutomatic(db.reviewDao().observeOptimizerChanges())
    }
}
