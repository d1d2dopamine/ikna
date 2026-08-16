package dev.ikna.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.ikna.audio.Speaker
import dev.ikna.audio.SpeakerStatus
import dev.ikna.data.prefs.SettingsStore
import dev.ikna.data.repo.LearningRepository
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.governor.GovernorReason
import dev.ikna.domain.session.Level
import dev.ikna.domain.session.SessionCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SessionUiState(
    val loading: Boolean = true,
    val queue: List<SessionCard> = emptyList(),
    val index: Int = 0,
    val revealed: Boolean = false,
    /** Distinct questions still owed in this session. Falls only. */
    val remaining: Int = 0,
    /** Answers recorded today across every deck. The daily minimum reads this. */
    val answeredToday: Int = 0,
    /**
     * Progress within this session: how many of its cards are done out of how
     * many it had. Separate from [answeredToday] because a Polish session should
     * not show a band that is already half full because of English this morning.
     */
    val sessionDone: Int = 0,
    val sessionTotal: Int = 0,
    /** Null when the session covers every deck. */
    val deckTitle: String? = null,
    val dailyMinimum: Int = 1,
    /**
     * Measured median answer time. Null until there is enough history to
     * say anything honest about it, in which case no estimate is shown.
     */
    val perCardMs: Long? = null,
    val reason: GovernorReason = GovernorReason.OK,
    val nextDueAt: Long? = null,
    val canUndo: Boolean = false,
    val undoVisible: Boolean = false,
    val undoFailed: Boolean = false,
    val showRevealHint: Boolean = false,
    val extraAdded: Int = 0,
    val noMoreExtra: Boolean = false,
    /**
     * True once enough answers have been given by swiping. The two words at the
     * bottom of the card then stop being drawn at rest and only appear under the
     * thumb during a gesture. Read once per session on purpose: chrome that
     * disappears mid-session changes the screen while it is being used.
     */
    val swipeFluent: Boolean = false,
    /**
     * A speech engine answered and has an offline voice for this material. Until
     * it does, the speaker mark is not drawn at all: a control that does nothing
     * when pressed is worse than a missing one.
     */
    val speechReady: Boolean = false,
    val finished: Boolean = false
) {
    val current: SessionCard? get() = queue.getOrNull(index)
    val minimumMet: Boolean get() = answeredToday >= dailyMinimum

    /** 0f..1f for the band at the top of the screen. */
    val progress: Float
        get() = if (sessionTotal <= 0) 0f else sessionDone.toFloat() / sessionTotal

    /** The level of the next question, so the next step is never a surprise. */
    val nextCard: SessionCard? get() = queue.getOrNull(index + 1)

    /**
     * Whether saying it out loud right now would hand over the answer.
     *
     * A recognition card already shows the sentence it lives in, so hearing it
     * adds pronunciation to something already visible. A cloze with a gap in
     * it, or a production card showing only the translation, would be the answer
     * itself — those wait until the card is turned.
     */
    val speakable: Boolean
        get() = current?.let { card ->
            card.level == Level.RECOGNITION || revealed
        } == true
}

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
    private val deckId: String? = null
) : ViewModel() {

    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    /** Today's plan as loaded. The counter is derived from this, nothing else. */
    private var planned: List<SessionCard> = emptyList()
    private val answeredKeys = HashSet<String>()

    /**
     * Chunks that already had their extra pass. A brand new chunk is asked twice
     * a few minutes apart on the day it appears: that second pass is where most
     * of the encoding actually happens, it costs one card, and it never touches
     * the day's counter.
     */
    private val drilled = HashSet<String>()

    private var shownAt = 0L

    /**
     * Which languages can be spoken at all. Asked once each: the first answer is
     * where the model is read into memory, and it is needed on every card.
     */
    private val langReady = HashMap<String, Boolean>()
    private var undoToken = 0
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
     * under it — the undo countdown, speech and prefetching stay on their own
     * coroutines below, or a six second timer would freeze the screen.
     */
    private val work = Mutex()

    /**
     * Cards of this session's scope that were already answered when it was last
     * loaded. The progress band is this plus what has been answered since, which
     * is why it never counts the same question twice.
     */
    private var doneAtLoad = 0

    private fun serially(block: suspend () -> Unit) {
        viewModelScope.launch { work.withLock { block() } }
    }

    init {
        load()
        warmUpSpeech()
    }

    /**
     * Starting a speech engine is the slow part — seconds on some phones — and
     * saying six words afterwards is not. So it starts while the first card is
     * still being read, never on the press of the speaker mark.
     */
    private fun warmUpSpeech() {
        viewModelScope.launch {
            val prefs = settings.current()
            if (!prefs.speechEnabled) return@launch
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
            val prefs = settings.current()
            if (!prefs.speechEnabled) return@launch
            // Cheap when nothing changed, and it is what makes the switch in
            // settings take effect on the very next press, without leaving the
            // session to do it.
            speaker.setPhoneVoice(prefs.phoneVoice)
            speaker.speak(spokenText(card), card.chunk.lang)
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
        viewModelScope.launch {
            val s = _state.value
            val card = s.current ?: return@launch
            val prefs = settings.current()
            if (!prefs.speechEnabled) return@launch

            // Whether this card can be spoken is a question about its language.
            // It used to be asked once, about the phone's engine, and never
            // about the model -- so a deck in the model's language stayed silent
            // whenever the phone had no voice for it, and a deck the model does
            // not speak showed a mark that did nothing when pressed.
            val lang = card.chunk.lang
            val ready = canSpeak(lang)
            if (ready != _state.value.speechReady) {
                _state.value = _state.value.copy(speechReady = ready)
            }
            if (!ready) return@launch

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
            if (speaks && _state.value.current?.card?.key == key) {
                speaker.speakIfReady(spokenText(card), lang)
            }

            s.nextCard?.let { ahead -> speaker.prefetch(spokenText(ahead), ahead.chunk.lang) }
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

    fun load(showLoading: Boolean = true) {
        serially {
            if (showLoading) _state.value = _state.value.copy(loading = true)
            val plan = repo.buildSession(deckId)
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
            _state.value = SessionUiState(
                loading = false,
                queue = plan.cards,
                index = 0,
                revealed = false,
                remaining = plan.cards.size,
                answeredToday = plan.answeredToday,
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
            )
            shownAt = System.currentTimeMillis()
            onCardShown()
        }
    }

    /** Tap anywhere on the card. */
    fun reveal() {
        val s = _state.value
        if (s.revealed || s.current == null) return
        _state.value = s.copy(revealed = true, showRevealHint = false)
        if (hintsShown < HINT_LIMIT) {
            hintsShown++
            viewModelScope.launch { settings.bumpRevealHint() }
        }
    }

    /**
     * Two outcomes, both of them from one horizontal axis: [Rating.AGAIN] for a
     * chunk that is not known and [Rating.GOOD] for one that is. The other two
     * grades stay in the model for the sake of the log, which holds years of
     * answers given when the card could still be thrown up and down.
     *
     * [viaSwipe] is counted, not just logged: it is the only evidence that the
     * gesture has actually been found. Until there is enough of it, the two
     * words stay on screen.
     */
    fun rate(rating: Rating, viaSwipe: Boolean = false) {
        val s = _state.value
        val card = s.current ?: return
        val now = System.currentTimeMillis()
        val duration = (now - shownAt).coerceIn(0L, 120_000L)

        if (viaSwipe && swipesDone < SWIPE_FLUENCY) {
            swipesDone++
            viewModelScope.launch { settings.bumpSwipe() }
        }

        answeredKeys += card.card.key

        // "Again" comes back at the end of this session, in memory only. It is
        // not a new obligation, so it does not touch the counter.
        //
        // A first contact comes back once for the same reason: a single pass on
        // the day a chunk appears is the weakest point of any spaced system, and
        // a second pass minutes later is nearly free.
        val secondPass = card.isFirstContact && card.card.key !in drilled
        if (secondPass) drilled += card.card.key
        val queue = if (rating == Rating.AGAIN || secondPass) s.queue + card else s.queue
        val nextIndex = s.index + 1
        undoToken++
        val token = undoToken

        _state.value = s.copy(
            queue = queue,
            index = nextIndex,
            revealed = false,
            remaining = planned.count { it.card.key !in answeredKeys },
            answeredToday = s.answeredToday + 1,
            // Distinct questions of this session that are done, never "answers
            // given". A card rated "again" and a first contact both come back in
            // the same session, and counting those as progress used to fill the
            // band ahead of the work: a session of ten questions could show ten
            // out of ten with three of them still to come.
            sessionDone = (doneAtLoad + planned.count { it.card.key in answeredKeys })
                .coerceAtMost(s.sessionTotal),
            canUndo = true,
            undoVisible = true,
            undoFailed = false,
            showRevealHint = hintsShown < HINT_LIMIT,
            extraAdded = 0,
            finished = nextIndex >= queue.size
        )
        shownAt = now
        onCardShown()

        serially { repo.answer(card, rating, duration, now) }
        viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            if (token == undoToken) _state.value = _state.value.copy(undoVisible = false)
        }
    }

    /**
     * Takes back the last answer and puts that card in front again. A misswipe
     * is the single most common way to lose trust in a swipe interface, so it
     * has to be reversible without thinking.
     */
    fun undo() {
        serially {
            val key = repo.undoLast()
            if (key == null) {
                _state.value = _state.value.copy(undoVisible = false, canUndo = false, undoFailed = true)
                return@serially
            }
            undoToken++
            val plan = repo.buildSession(deckId)
            planned = plan.cards
            answeredKeys.clear()
            doneAtLoad = plan.sessionDone
            val ordered = plan.cards.sortedBy { if (it.card.key == key) 0 else 1 }
            _state.value = _state.value.copy(
                loading = false,
                queue = ordered,
                index = 0,
                revealed = false,
                remaining = ordered.size,
                answeredToday = plan.answeredToday,
                sessionDone = plan.sessionDone,
                sessionTotal = plan.sessionTotal,
                reason = plan.reason,
                nextDueAt = plan.nextDueAt,
                undoVisible = false,
                undoFailed = false,
                canUndo = plan.answeredToday > 0,
                extraAdded = 0,
                finished = ordered.isEmpty()
            )
            shownAt = System.currentTimeMillis()
            onCardShown()
        }
    }

    fun dismissUndo() {
        undoToken++
        _state.value = _state.value.copy(undoVisible = false)
    }

    /**
     * "Ещё немного": five more cards that are already due, from this session's
     * deck when it has one. Never new ones, so a good mood today cannot buy a
     * heavier queue tomorrow.
     */
    fun addExtra() {
        serially {
            val added = repo.addExtra(EXTRA_BATCH, deckId)
            if (added == 0) {
                _state.value = _state.value.copy(noMoreExtra = true)
                return@serially
            }
            val plan = repo.buildSession(deckId)
            planned = plan.cards
            answeredKeys.clear()
            doneAtLoad = plan.sessionDone
            _state.value = _state.value.copy(
                queue = plan.cards,
                index = 0,
                revealed = false,
                remaining = plan.cards.size,
                answeredToday = plan.answeredToday,
                sessionDone = plan.sessionDone,
                sessionTotal = plan.sessionTotal,
                nextDueAt = plan.nextDueAt,
                extraAdded = added,
                noMoreExtra = false,
                undoVisible = false,
                finished = plan.cards.isEmpty()
            )
            shownAt = System.currentTimeMillis()
            onCardShown()
        }
    }

    companion object {
        private const val UNDO_WINDOW_MS = 6_000L
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
            deckId: String? = null
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SessionViewModel(repo, settings, speaker, deckId) as T
            }
    }
}
