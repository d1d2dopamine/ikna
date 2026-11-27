package dev.ikna.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.ikna.audio.Speaker
import dev.ikna.audio.SpeakerStatus
import dev.ikna.data.prefs.SettingsStore
import dev.ikna.data.repo.LearningRepository
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.grading.INPUT_SWIPE
import dev.ikna.domain.session.ReviewDuration
import dev.ikna.domain.session.SessionCard
import dev.ikna.domain.session.SessionObservation
import dev.ikna.domain.session.SessionPresentationGate
import dev.ikna.domain.session.SessionObservationPolicy
import dev.ikna.domain.session.SessionPlanChange
import kotlinx.coroutines.channels.Channel
import dev.ikna.domain.session.ReviewSignals
import dev.ikna.domain.session.ReviewSignalTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock



/**
 * A real ViewModel, not an object remembered inside a composable.
 *
 * The old version was recreated on every rotation and every tab switch, and
 * each recreation rebuilt the day's plan — which is how the "cards left" number
 * used to grow while the user was answering. Session state now survives
 * configuration changes, and the plan itself lives in the database.
 *
 * [deckId] is a filter over that one plan, never a plan of its own. The day has
 * a single measured capacity; letting each deck bring its own budget would let
 * two decks quietly authorise twice the load.
 */
class SessionViewModel(
    private val repo: LearningRepository,
    private val settings: SettingsStore,
    private val speaker: Speaker,
    private val deckId: String? = null,
    private val sessionChanges: StateFlow<Long> = MutableStateFlow(0L)
) : ViewModel() {

    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    /** Today's plan as loaded. The counter is derived from this, nothing else. */
    private var planned: List<SessionCard> = emptyList()
    private val answeredKeys = HashSet<String>()

    /**
     * First-contact prompts already offered a transient second pass. These
     * repeats stop at the global required-plan boundary; further hard review is
     * explicit and optional. They never create another required question.
     */
    private val drilled = HashSet<String>()

    private var shownNanos = 0L

    var reviewSignals = ReviewSignalTracker()
        private set

    /**
     * Which languages can be spoken at all. Asked once each: the first answer is
     * where the model is read into memory, and it is needed on every card.
     */
    private val langReady = HashMap<String, Boolean>()
    private var hintsShown = 0
    private var swipesDone = 0

    /**
     * Everything this screen asks the repository to do happens alone, and in the
     * order the user asked for it.
     *
     * Each of the four actions below used to be its own `launch`, so a swipe, a
     * press on "ещё немного" and a press on undo could all be inside the
     * repository at the same time. The repository serialises its own writes, so
     * the database never corrupted — but the read that decides WHAT to write
     * happened before the lock was taken. Undo pressed while an answer was still
     * in flight retracted the answer before it; "ещё немного" pressed on the same
     * beat rebuilt the queue from a plan that was one answer out of date, and the
     * card that had just been answered came back.
     *
     * A mutex rather than a flag: the point is not to drop the second action, it
     * is to run it after the first one has finished. Nothing slow is ever held
     * under it: speech and prefetching stay on their own coroutines below.
     * Undo feedback has no timer; it stays available until dismissed or replaced
     * by a later action.
     */
    private val presentation = SessionPresentationGate<SessionObservation>()
    private val presentationVersion: Long get() = presentation.generation
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)
    private var refreshRequired = false
    private var observedCommitGeneration = sessionChanges.value
    private val work = Mutex()
    private val loadGate = CardLoadGate()

    /**
     * Cards of this session's scope that were already answered when it was last
     * loaded. The progress band is this plus what has been answered since, which
     * is why it never counts the same question twice.
     */
    private var doneAtLoad = 0

    private fun publishState(version: Long, value: SessionUiState): Boolean {
        if (!presentation.isCurrent(version)) return false
        _state.value = value
        return true
    }

    // Accessed only while work is held; every suspended action publication checks
    // both its generation and the expected local plan mutation before showing a card.
    private var actionChange: SessionPlanChange = SessionPlanChange.Stable
    private var actionBaseline: SessionObservation? = null
    private var actionReloaded = false

    private suspend fun rememberOwnAction(version: Long): Boolean {
        if (version != presentationVersion) return false
        val fresh = repo.inspectSession(deckId)
        if (version != presentationVersion) return false
        val baseline = if (actionReloaded) presentation.observation else actionBaseline
        if (!SessionObservationPolicy.compatible(baseline, fresh.observation, actionChange)) {
            requestCommittedRefresh()
            return false
        }
        return presentation.remember(version, fresh.observation)
    }

    private suspend fun publishActionState(version: Long, value: SessionUiState): Boolean {
        if (!rememberOwnAction(version)) return false
        return publishState(version, value)
    }

    private fun serially(
        change: SessionPlanChange = SessionPlanChange.Stable,
        reloaded: Boolean = false,
        block: suspend (Long) -> Unit
    ) {
        val version = presentationVersion
        viewModelScope.launch {
            work.withLock {
                actionBaseline = presentation.observation
                actionChange = change
                actionReloaded = reloaded
                cardLoadResult {
                    block(version)
                    rememberOwnAction(version)
                }.onFailure {
                    if (version != presentationVersion) return@onFailure
                    _state.value = _state.value.failedLoad()
                }
            }
        }
    }

    private fun requestCommittedRefresh() {
        presentation.invalidate()
        refreshRequired = true
        _state.value = _state.value.beginLoad()
        refreshRequests.trySend(Unit)
    }

    /** A real lifecycle resume probes saved state without admitting any new plan. */
    fun checkSessionOnResume() { refreshRequests.trySend(Unit) }

    private fun changedState(version: Long) = publishState(version,
        _state.value.failedLoad().copy(loadFailed = false, sessionChanged = true))

    private suspend fun refreshFromHistory() = work.withLock {
        val version = presentationVersion
        val forced = refreshRequired
        var failureVersion = version
        cardLoadResult {
            val snapshot = repo.inspectSession(deckId)
            if (version != presentationVersion) return@cardLoadResult
            if (!forced && !presentation.changed(snapshot.observation)) return@cardLoadResult
            val previous = presentation.observation
            val refreshVersion = if (forced) version else presentation.invalidate()
            failureVersion = refreshVersion
            _state.value = _state.value.beginLoad()
            val plan = snapshot.plan
            if (plan == null || !SessionObservationPolicy.compatible(previous, snapshot.observation, SessionPlanChange.Stable)) {
                changedState(refreshVersion)
            } else {
                planned = plan.cards
                answeredKeys.clear()
                doneAtLoad = plan.sessionDone
                publishState(refreshVersion, _state.value.copy(loading = false, loadFailed = false, sessionChanged = false,
                    queue = plan.cards, index = 0, remaining = plan.cards.size, revealed = false,
                    answeredToday = snapshot.answeredToday, goalAnswersToday = snapshot.goalAnswersToday,
                    sessionDone = plan.sessionDone, sessionTotal = plan.sessionTotal, deckTitle = plan.deckTitle,
                    reason = plan.reason, nextDueAt = plan.nextDueAt, finished = plan.cards.isEmpty(),
                    canUndo = false, undoVisible = false))
                shownNanos = System.nanoTime(); onCardShown()
            }
            presentation.remember(refreshVersion, snapshot.observation)
            refreshRequired = false
        }.onFailure {
            // No successful read means no stale card may remain answerable.
            if (failureVersion == presentationVersion) {
                _state.value = _state.value.failedLoad()
                refreshRequired = false
            }
        }
    }

    init {
        load()
        viewModelScope.launch {
            sessionChanges.collect { generation ->
                if (generation != observedCommitGeneration) {
                    observedCommitGeneration = generation
                    requestCommittedRefresh()
                }
            }
        }
        viewModelScope.launch { for (request in refreshRequests) refreshFromHistory() }
        warmUpSpeech()
    }

    /**
     * Starting a speech engine is the slow part — seconds on some phones — and
     * saying six words afterwards is not. So it starts while the first card is
     * still being read, never on the press of the speaker mark.
     */
    private fun warmUpSpeech() {
        viewModelScope.launch {
            cardLoadResult {
                val prefs = settings.current()
                if (!prefs.speechEnabled) return@cardLoadResult
                // Who is allowed to speak is settled before a single card is
                // rendered, and never changes for the rest of the session. Speed
                // and pitch used to be written here instead, and they were part of
                // the name of every cached file -- which is what made the first
                // card of a session silent until its mark was pressed.
                speaker.setPhoneVoice(prefs.phoneVoice)
                // Whether the mark is drawn is decided per card, by the language in
                // front of the user; this call only pays the cost of starting an
                // engine while there is still time to spare.
                speaker.warmUp()
                onCardShown()
            }
        }
    }

    /**
     * The speaker mark. Plays the cached audio when there is some, otherwise asks
     * the engine directly rather than letting the press do nothing visible while
     * a file is written.
     */
    fun speakCurrent() {
        val s = _state.value
        val card = s.current ?: return
        if (!s.speakable) return
        viewModelScope.launch {
            cardLoadResult {
                val prefs = settings.current()
                if (!prefs.speechEnabled) return@cardLoadResult
                // Cheap when nothing changed, and it is what makes the switch in
                // settings take effect on the very next press, without leaving the
                // session to do it.
                speaker.setPhoneVoice(prefs.phoneVoice)
                reviewSignals.speechStarted()
                speaker.speak(spokenText(card), card.chunk.lang)
            }
        }
    }

    /**
     * Runs whenever the card in front changes.
     *
     * Two quiet things happen: the next card is synthesised in the background so
     * its mark answers instantly, and a first contact speaks by itself — but only
     * if its audio is already waiting. Sound that arrives on its own two seconds
     * late, over a card being read, is worse than no sound, so nothing here ever
     * makes the card wait.
     */
    private fun onCardShown() {
        val version = presentationVersion
        reviewSignals = ReviewSignalTracker()
        viewModelScope.launch {
            cardLoadResult {
                val s = _state.value
                val card = s.current ?: return@cardLoadResult
                val prefs = settings.current()
                if (!prefs.speechEnabled) return@cardLoadResult

                // Whether this card can be spoken is a question about its language.
                // It used to be asked once, about the phone's engine, and never
                // about the model -- so a deck in the model's language stayed silent
                // whenever the phone had no voice for it, and a deck the model does
                // not speak showed a mark that did nothing when pressed.
                val lang = card.chunk.lang
                val ready = canSpeak(lang)
                if (version != presentationVersion) return@cardLoadResult
                if (ready != _state.value.speechReady) {
                    _state.value = _state.value.copy(speechReady = ready)
                }
                if (!ready) return@cardLoadResult

                val key = card.card.key
                // Rendered first, played second. The old order asked for audio that
                // nothing had written yet, so the one card meant to speak by itself
                // -- a phrase met for the first time -- was the one that never did.
                speaker.prefetch(spokenText(card), lang)
                // Every card, or only a phrase being met for the first time. Either
                // way the card has to still be the card in front: rendering can take
                // a moment, and sound that arrives over the next card is worse than
                // no sound at all.
                val speaks = prefs.autoSpeakEvery || card.isFirstContact
                if (version == presentationVersion && speaks && _state.value.current?.card?.key == key) {
                    reviewSignals.speechStarted()
                    speaker.speakIfReady(spokenText(card), lang)
                }

                s.nextCard?.let { ahead -> speaker.prefetch(spokenText(ahead), ahead.chunk.lang) }
            }
        }
    }

    /**
     * Remembered per language, because the first answer loads the model.
     *
     * Only a yes is kept. A no is often the answer of a model that had not
     * finished loading yet, and remembering it meant one unlucky first card
     * silenced that language for the whole session -- with a working voice
     * sitting right there.
     */
    private suspend fun canSpeak(lang: String): Boolean {
        langReady[lang]?.let { return it }
        val ok = speaker.status(lang) == SpeakerStatus.READY
        if (ok) langReady[lang] = true
        return ok
    }

    /**
     * Always the whole sentence in the target language, never the bare chunk and
     * never the translation. A chunk pronounced alone loses the rhythm it has
     * inside a sentence, and that rhythm is most of what makes it stick.
     */
    private fun spokenText(card: SessionCard): String =
        card.chunk.contextSentence.ifBlank { card.chunk.text }

    override fun onCleared() {
        super.onCleared()
        speaker.stop()
    }

    fun load() {
        if (!loadGate.tryStart()) return
        presentation.invalidate()
        _state.value = _state.value.beginLoad()
        serially(reloaded = true) { version ->
            try {
                val loaded = repo.buildSession(deckId) // Explicit entry/Continue is the only admission path here.
                val snapshot = repo.inspectSession(deckId)
                // Forced Sandbox practice is an explicit developer-only build result,
                // never manufactured by the automatic committed-history observer.
                val plan = (if (loaded.forcedByDeveloper) loaded else snapshot.plan) ?: run { changedState(version); return@serially }
                if (!presentation.remember(version, snapshot.observation)) return@serially
                planned = plan.cards
                answeredKeys.clear()
                doneAtLoad = plan.sessionDone
                val prefs = settings.current()
                hintsShown = prefs.revealHintsShown
                swipesDone = prefs.swipesDone

                // Measured if there is enough history, remembered otherwise. The
                // fresh measurement is also written down, so the next session that
                // cannot measure anything still knows what an answer costs.
                val measured = repo.medianAnswerMs()
                if (measured != null) settings.setAnswerMs(measured.toInt())
                val remembered = prefs.answerMs.takeIf { it > 0 }?.toLong()
                if (!publishActionState(version, SessionUiState(
                    loading = false,
                    queue = plan.cards,
                    index = 0,
                    revealed = false,
                    remaining = plan.cards.size,
                    answeredToday = plan.answeredToday,
                    goalAnswersToday = plan.goalAnswersToday,
                    sessionDone = plan.sessionDone,
                    sessionTotal = plan.sessionTotal,
                    deckTitle = plan.deckTitle,
                    dailyMinimum = repo.dailyMinimum(),
                    perCardMs = measured ?: remembered,
                    reason = plan.reason,
                    nextDueAt = plan.nextDueAt,
                    canUndo = plan.answeredToday > 0,
                    showRevealHint = hintsShown < HINT_LIMIT,
                    swipeFluent = swipesDone >= SWIPE_FLUENCY,
                    finished = plan.cards.isEmpty()
                ))) return@serially
                shownNanos = System.nanoTime()
                onCardShown()
            } finally { loadGate.finish() }
        }
    }

    /** Show the mandatory answer and remember which input modality did it. */
    fun reveal(inputMethod: String = INPUT_SWIPE) {
        val s = _state.value
        if (s.revealed || s.current == null) return
        reviewSignals.reveal(inputMethod)
        _state.value = s.copy(revealed = true, showRevealHint = false)
        if (hintsShown < HINT_LIMIT) {
            hintsShown++
            viewModelScope.launch { settings.bumpRevealHint() }
        }
    }

    /**
     * Two outcomes, both of them from one horizontal axis: [Rating.AGAIN] for a
     * chunk that is not known and [Rating.GOOD] for one that is. After the
     * mandatory reveal, private per-modality timing may conservatively refine a
     * mature known answer to HARD/EASY without asking another question.
     *
     * [viaSwipe] is counted, not just logged: it is the only evidence that the
     * gesture has actually been found. Until there is enough of it, the two
     * words stay on screen.
     */
    /**
     * "This card is wrong."
     *
     * The answer that was missing. A deck can be written by a model in thirty
     * seconds, and some of what it writes is false -- and the only two answers on
     * offer were "knew it" and "did not", the second of which asks the scheduler
     * to show the false card more often and costs the day some accuracy on the
     * way. So the honest reaction gets its own action: the chunk leaves this
     * session and every future one, nothing is logged as an error, and the day is
     * not made to look worse than it was. See [LearningRepository.markWrong].
     *
     * Every level of the chunk goes at once, including the copies queued for a
     * second pass: what is wrong is the fact, not the way it was asked.
     */
    fun markWrong(reportCopied: Boolean = false) {
        val s = _state.value
        val card = s.current ?: return
        val chunkId = card.chunk.id

        val queue = s.queue.filterNot { it.chunk.id == chunkId }
        // Cards of the same chunk sitting behind the cursor were already answered
        // in this session, and dropping them shifts everything after them.
        val removedBefore = s.queue.take(s.index).count { it.chunk.id == chunkId }
        val index = (s.index - removedBefore).coerceIn(0, queue.size)

        val owedGone = planned.count { it.chunk.id == chunkId && it.card.key !in answeredKeys }
        planned = planned.filterNot { it.chunk.id == chunkId }
        answeredKeys.removeAll { it.substringBeforeLast(':') == chunkId }

        // Undo belongs to answers. There is nothing to take back here, and
        // leaving the offer up would suggest the card came back.

        _state.value = s.copy(
            queue = queue,
            index = index,
            revealed = false,
            remaining = planned.count { it.card.key !in answeredKeys },
            sessionTotal = (s.sessionTotal - owedGone).coerceAtLeast(s.sessionDone),
            undoVisible = false,
            undoFailed = false,
            wrongMarked = true,
            wrongSourceId = card.sourceId,
            wrongReportCopied = reportCopied,
            finished = index >= queue.size
        )
        shownNanos = System.nanoTime()
        onCardShown()

        serially(change = SessionPlanChange.RemoveTarget(card.chunk.id)) { _ ->
            repo.markWrong(card)
        }
    }

    fun rate(
        rating: Rating,
        viaSwipe: Boolean = false,
        signals: ReviewSignals = reviewSignals.snapshot()
    ) {
        val s = _state.value
        val card = s.current ?: return
        val now = System.currentTimeMillis()
        val duration = ReviewDuration.elapsedMillis(shownNanos, System.nanoTime())

        if (viaSwipe && swipesDone < SWIPE_FLUENCY) {
            swipesDone++
            viewModelScope.launch { settings.bumpSwipe() }
        }

        _state.value = s.copy(loading = true)
        serially(change = SessionPlanChange.AppendExtras) { version ->
            // The global boundary is observed only after this answer is durable.
            // No optimistic next prompt can receive another answer meanwhile.
            repo.answer(card, rating, duration, now, signals)
            val requiredComplete = repo.requiredPlanComplete(now)
            if (version != presentationVersion) return@serially
            answeredKeys += card.card.key
            val secondPass = card.isFirstContact && card.card.key !in drilled
            if (secondPass) drilled += card.card.key
            val step = MainLessonQueue.afterAnswer(s.queue, s.index,
                repeatCurrent = rating == Rating.AGAIN || secondPass,
                answeredKeys = answeredKeys, requiredComplete = requiredComplete,
                key = { it.card.key })
            // load() may have queued a newer presentation during either read.
            // Its reload will reflect this committed answer without re-answering it.
            if (version != presentationVersion) return@serially
            if (!publishActionState(version, s.copy(
                loading = false,
                queue = step.queue,
                index = step.index,
                revealed = false,
                remaining = planned.count { it.card.key !in answeredKeys },
                answeredToday = s.answeredToday + 1,
                goalAnswersToday = s.goalAnswersToday + 1,
                sessionDone = (doneAtLoad + planned.count { it.card.key in answeredKeys })
                    .coerceAtMost(s.sessionTotal),
                canUndo = true, undoVisible = true, undoFailed = false,
                wrongMarked = false, wrongSourceId = null, wrongReportCopied = false,
                showRevealHint = hintsShown < HINT_LIMIT,
                extraAdded = 0, finished = step.finished
            ))) return@serially
            shownNanos = System.nanoTime()
            onCardShown()
        }
    }

    /**
     * Takes back the last answer and puts that card in front again. A misswipe
     * is the single most common way to lose trust in a swipe interface, so it
     * has to be reversible without thinking.
     */
    fun undo() {
        if (_state.value.loading || _state.value.loadFailed || _state.value.sessionChanged) return
        _state.value = _state.value.copy(loading = true)
        serially { version ->
            val key = repo.undoLast()
            if (key == null) {
                if (!publishActionState(version, _state.value.copy(loading = false, undoVisible = false, canUndo = false, undoFailed = true))) return@serially
                return@serially
            }

            val plan = repo.buildSession(deckId)
            planned = plan.cards
            answeredKeys.clear()
            doneAtLoad = plan.sessionDone
            val ordered = plan.cards.sortedBy { if (it.card.key == key) 0 else 1 }
            if (!publishActionState(version, _state.value.copy(
                loading = false,
                loadFailed = false,
                queue = ordered,
                index = 0,
                revealed = false,
                remaining = ordered.size,
                answeredToday = plan.answeredToday,
                goalAnswersToday = plan.goalAnswersToday,
                sessionDone = plan.sessionDone,
                sessionTotal = plan.sessionTotal,
                reason = plan.reason,
                nextDueAt = plan.nextDueAt,
                undoVisible = false,
                undoFailed = false,
                canUndo = plan.answeredToday > 0,
                extraAdded = 0,
                finished = ordered.isEmpty()
            ))) return@serially
            shownNanos = System.nanoTime()
            onCardShown()
        }
    }

    fun dismissUndo() {
        _state.value = _state.value.copy(undoVisible = false)
    }

    /**
     * Extra review: due, amnesty, then future established cards,
     * filtered to this session's deck.
     */
    fun addExtra() {
        if (_state.value.loading || _state.value.loadFailed || _state.value.sessionChanged) return
        _state.value = _state.value.copy(loading = true)
        serially(change = SessionPlanChange.AppendExtras) { version ->
            val added = repo.addExtra(EXTRA_BATCH, deckId)
            if (added == 0) {
                if (!publishActionState(version, _state.value.copy(loading = false, noMoreExtra = true,
                    finished = _state.value.index >= _state.value.queue.size))) return@serially
                return@serially
            }
            val plan = repo.buildSession(deckId)
            planned = plan.cards
            answeredKeys.clear()
            doneAtLoad = plan.sessionDone
            if (!publishActionState(version, _state.value.copy(
                loading = false,
                loadFailed = false,
                queue = plan.cards,
                index = 0,
                revealed = false,
                remaining = plan.cards.size,
                answeredToday = plan.answeredToday,
                goalAnswersToday = plan.goalAnswersToday,
                sessionDone = plan.sessionDone,
                sessionTotal = plan.sessionTotal,
                nextDueAt = plan.nextDueAt,
                extraAdded = added,
                noMoreExtra = added == 0,
                undoVisible = false,
                finished = plan.cards.isEmpty()
            ))) return@serially
            shownNanos = System.nanoTime()
            onCardShown()
        }
    }

    companion object {
        private const val EXTRA_BATCH = 5
        /** The tap hint disappears once, after five cards. No permanent chrome. */
        private const val HINT_LIMIT = 5

        /**
         * A dozen answers given by swiping is roughly two short sessions, which
         * is where the movement stops being a thing you decide to do.
         */
        private const val SWIPE_FLUENCY = 12

        fun factory(
            repo: LearningRepository,
            settings: SettingsStore,
            speaker: Speaker,
            deckId: String? = null,
            sessionChanges: StateFlow<Long> = MutableStateFlow(0L)
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SessionViewModel(repo, settings, speaker, deckId, sessionChanges) as T
            }
    }
}
