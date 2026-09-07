package dev.ikna.data.repo
import dev.ikna.data.db.ReviewEntity
import dev.ikna.data.prefs.SettingsStore
import dev.ikna.data.prefs.suppressedOf
import dev.ikna.domain.fsrs.*
import dev.ikna.domain.optimizer.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Local CPU worker. Automatic policy is separate from fitting and the answer path. */
class LocalOptimizer(private val settings: SettingsStore, private val defaults: FsrsParams,
    private val scope: CoroutineScope, private val loadHistory: suspend () -> List<ReviewEntity>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val fit: (List<ReviewSample>, Double, () -> Unit) -> Optimisation = { samples, retention, cancel ->
        FsrsOptimizer.optimise(samples, retention, cancel)
    }) {
    private val active = AtomicReference(defaults)
    private val runtimeLock = Any()
    private val epoch = AtomicLong()
    private val commands = Mutex()
    private val _state = MutableStateFlow(OptimizerUiState())
    val state: StateFlow<OptimizerUiState> = _state.asStateFlow()
    @Volatile private var work: Job? = null
    private var automation: Job? = null
    private val automaticCommands = Mutex()
    private var automaticRetryNotBefore = 0L
    init { settings.onOptimizerReset = { restoreDefaults() } }
    /** Atomic read only; never wait for fitting or read disk while answering. */
    fun parameters(): FsrsParams = active.get()
    private fun policy(result: FitAttempt) = result.parameters!!.parameters().copy(desiredRetention = defaults.desiredRetention)
    suspend fun initialize() = commands.withLock {
        if (_state.value.ready) return@withLock
        val generation = epoch.get()
        val stored = OptimizerStateCodec.decode(settings.optimizerRaw.first())
        synchronized(runtimeLock) {
            val enabled = generation == epoch.get() && stored?.enabled == true
            if (enabled) active.set(policy(stored!!.applied!!))
            _state.value = OptimizerUiState(ready = true, usingOptimized = enabled,
                stored = stored ?: StoredOptimizer(), issue = if (stored == null) OptimizerIssue.STORAGE else null)
        }
        scope.launch {
            settings.optimizerRaw.distinctUntilChanged().collect { raw ->
                val decoded = OptimizerStateCodec.decode(raw)
                _state.update { it.copy(stored = decoded ?: StoredOptimizer(),
                    issue = if (decoded == null) OptimizerIssue.STORAGE else it.issue) }
            }
        }
    }
    /** Reset and activation share a tiny critical section, never held during disk/CPU work. */
    fun restoreDefaults() {
        synchronized(runtimeLock) {
            epoch.incrementAndGet(); active.set(defaults)
            _state.update { it.copy(usingOptimized = false) }
        }
        work?.cancel()
    }
    @Synchronized fun start(): Job? {
        if (!_state.value.ready || work?.isActive == true) return null
        if (clock() < (_state.value.stored.latest?.nextFitAt ?: 0L)) {
            _state.update { it.copy(issue = OptimizerIssue.COOLDOWN) }; return null
        }
        val generation = epoch.get()
        _state.update { it.copy(running = true, issue = null) }
        return scope.launch(start = CoroutineStart.LAZY) { runFit(generation) }.also { work = it; it.start() }
    }
    fun cancel() { work?.cancel() }
    private suspend fun input(): OptimizerInput = withContext(Dispatchers.IO) {
        optimizerInput(loadHistory(), clock(), suppressedOf(settings.current().suppressed).toSet())
    }
    private suspend fun runFit(generation: Long) {
        try {
            val persisted = OptimizerStateCodec.decode(settings.optimizerRaw.first())
            if (clock() < (persisted?.latest?.nextFitAt ?: 0L)) {
                _state.update { it.copy(issue = OptimizerIssue.COOLDOWN) }; return
            }
            val source = input()
            val result = withContext(Dispatchers.Default) {
                val context = currentCoroutineContext()
                fit(source.samples, defaults.desiredRetention) {
                    context.ensureActive()
                    if (generation != epoch.get()) throw CancellationException("Profile reset")
                }
            }
            currentCoroutineContext().ensureActive()
            if (generation != epoch.get()) throw CancellationException("Profile reset")
            if (source.fingerprint != input().fingerprint) {
                _state.update { it.copy(issue = OptimizerIssue.STALE_HISTORY) }; return
            }
            val record = FitAttempt(attemptedAt = clock(), sourceFingerprint = source.fingerprint,
                sourceAnswers = source.samples.size, firstReviewAt = source.samples.firstOrNull()?.ts,
                lastReviewAt = source.samples.lastOrNull()?.ts, scoredAnswers = result.scoredAnswers,
                verdict = result.verdict, heldOutLossDefaults = result.heldOutLossDefaults.takeIf { it.isFinite() },
                heldOutLossOptimised = result.heldOutLossOptimised.takeIf { it.isFinite() },
                parameters = result.params?.let(FsrsSnapshot::of))
            require(record.isValid())
            commands.withLock {
                currentCoroutineContext().ensureActive()
                val saved = settings.updateOptimizer { old ->
                    if (generation != epoch.get()) throw CancellationException("Profile reset")
                    old.copy(latest = record, candidate = if (record.accepted) record else old.candidate)
                }
                // Publishing evidence is separate from activation; automatic policy validates the result next.
                _state.update { it.copy(stored = saved) }
            }
        } catch (cancelled: CancellationException) {
            _state.update { it.copy(issue = OptimizerIssue.CANCELLED) }; throw cancelled
        } catch (_: Exception) { _state.update { it.copy(issue = OptimizerIssue.FAILED) } }
        finally { _state.update { it.copy(running = false) } }
    }
    private fun activate(generation: Long, params: FsrsParams, saved: StoredOptimizer) {
        synchronized(runtimeLock) {
            if (generation == epoch.get()) {
                active.set(params)
                _state.update { it.copy(stored = saved, usingOptimized = saved.enabled, issue = null) }
            }
        }
    }
    suspend fun applyCandidate() = applyCandidateAt(epoch.get())
    private suspend fun applyCandidateAt(generation: Long) = commands.withLock {
        try {
            val saved = settings.updateOptimizer { old ->
                if (generation != epoch.get()) throw CancellationException("Profile reset")
                require(old.candidate?.accepted == true)
                old.copy(applied = old.candidate, enabled = true)
            }
            activate(generation, policy(saved.applied!!), saved)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { _state.update { it.copy(issue = OptimizerIssue.STORAGE) } }
    }
    suspend fun setEnabled(enabled: Boolean) {
        if (!enabled) restoreDefaults()
        commands.withLock {
            val generation = epoch.get()
            try {
                val saved = settings.updateOptimizer { old ->
                    if (generation != epoch.get()) throw CancellationException("Profile reset")
                    require(!enabled || old.applied?.accepted == true)
                    old.copy(enabled = enabled)
                }
                activate(generation, if (enabled) policy(saved.applied!!) else defaults, saved)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _state.update { it.copy(issue = OptimizerIssue.STORAGE) } }
        }
    }

    /** One monitor per application, not per settings composition or card answer. */
    @Synchronized fun startAutomatic(historyChanges: Flow<Long>): Job {
        automation?.takeIf { it.isActive }?.let { return it }
        return scope.launch {
            coroutineScope {
                val wakeups = Channel<Unit>(Channel.CONFLATED)
                launch { historyChanges.distinctUntilChanged().collect { wakeups.trySend(Unit) } }
                launch {
                    while (isActive) { delay(3_600_000L); wakeups.trySend(Unit) }
                }
                for (signal in wakeups) {
                    // Wait for a quiet gap rather than competing with a rapid sequence of answers.
                    while (withTimeoutOrNull(20_000L) { wakeups.receive(); true } == true) { }
                    try { runAutomaticCycle() }
                    catch (cancelled: CancellationException) { currentCoroutineContext().ensureActive() }
                    catch (_: Exception) { _state.update { it.copy(issue = OptimizerIssue.FAILED) } }
                }
            }
        }.also { automation = it }
    }

    /** Public for deterministic regression tests; not a user-facing command. */
    suspend fun runAutomaticCycle() = automaticCommands.withLock {
        if (!_state.value.ready || _state.value.running || clock() < automaticRetryNotBefore) return@withLock
        automaticRetryNotBefore = clock() + 3_600_000L
        val generation = epoch.get()
        val before = OptimizerStateCodec.decode(settings.optimizerRaw.first()) ?: return@withLock
        val candidate = before.candidate
        // An accepted result left by the former manual UI may be used only for the same history.
        if (candidate?.accepted == true && (candidate != before.applied || !before.enabled)) {
            if (candidate.sourceFingerprint == input().fingerprint) applyCandidateAt(generation)
        }
        if (generation != epoch.get() || clock() < AutomaticLearningPolicy.nextFitAt(before.latest)) return@withLock
        val job = start() ?: return@withLock
        job.join()
        currentCoroutineContext().ensureActive()
        if (job.isCancelled || generation != epoch.get()) return@withLock
        val after = OptimizerStateCodec.decode(settings.optimizerRaw.first()) ?: return@withLock
        val fresh = after.latest
        if (fresh != before.latest) automaticRetryNotBefore = 0L
        if (fresh != before.latest && fresh?.accepted == true && after.candidate == fresh) {
            applyCandidateAt(generation)
        }
    }
}
