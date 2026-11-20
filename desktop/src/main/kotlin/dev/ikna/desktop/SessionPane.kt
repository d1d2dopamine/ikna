package dev.ikna.desktop
import androidx.compose.runtime.produceState
import dev.ikna.data.repo.DeckSummary
import dev.ikna.data.prefs.lookFor
import dev.ikna.ui.session.CardShortcuts
import dev.ikna.ui.session.cardLoadResult
import dev.ikna.ui.session.IknaCardLoadFailure
import dev.ikna.ui.session.SessionUiState
import dev.ikna.ui.session.IknaSessionTopBar
import dev.ikna.ui.session.IknaTodayProgress
import dev.ikna.ui.session.IknaSessionEmptyState
import dev.ikna.ui.session.IknaSessionUndoBar
import dev.ikna.ui.theme.IknaBottomBar
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.IknaDialog
import dev.ikna.data.catalog.catalogCardReport
import dev.ikna.data.catalog.tatoebaSentenceUrl
import dev.ikna.domain.session.ReviewDuration
import dev.ikna.domain.session.SessionCard
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalWindowInfo
import dev.ikna.ui.session.IknaSessionChanged
import dev.ikna.domain.session.SessionPlanChange
import dev.ikna.domain.session.SessionObservationPolicy
import dev.ikna.domain.session.SessionObservation
import dev.ikna.domain.session.SessionPresentationGate
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import dev.ikna.data.prefs.HotkeyAction
import dev.ikna.data.prefs.HotkeyBindings
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.phoneticsFor
import dev.ikna.data.repo.NO_LANG
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.grading.INPUT_KEYBOARD
import dev.ikna.domain.grading.INPUT_SWIPE
import dev.ikna.domain.phonetics.Phonetics
import dev.ikna.domain.session.Ask
import dev.ikna.domain.session.OptionalHardBatch
import dev.ikna.domain.session.SessionPlan
import dev.ikna.domain.session.ReviewSignalTracker
import dev.ikna.domain.session.ReviewSignals
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import dev.ikna.ui.session.ChunkCard
import dev.ikna.ui.session.ProgrammaticSwipe
import dev.ikna.ui.session.SwipeableCard
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaPalette
import dev.ikna.ui.theme.IknaProgress
import kotlinx.coroutines.launch

/**
 * The cards -- the phone's cards, not a desktop retelling of them.
 *
 * The card itself, the drag that grades it, the wash of colour that follows the
 * pointer, the two words at the edges: all of it is the phone's own
 * [SwipeableCard] and [ChunkCard], moved into the shared module rather than
 * reimplemented here. A mouse press and drag is the same gesture as a thumb,
 * so the interaction survived the move unchanged.
 *
 * What the window adds is what a keyboard can offer and a thumb cannot: space
 * to turn a card over, the arrow keys for the same two answers, and Z to take
 * the last one back. HARD/GOOD/EASY remain the app's decision, not extra keys.
 */
@Composable
fun SessionPane(
    container: DesktopContainer,
    settings: IknaSettings,
    palette: IknaPalette,
    deckId: String?,
    onChanged: () -> Unit,
    onBack: () -> Unit = {},
    /** False in the wide window, where the session pane is the app's home and back has nowhere to go. */
    showBack: Boolean = true,
    decksCollapsed: Boolean = false,
    onToggleDecks: (() -> Unit)? = null
) {
    // A deck switch disposes the whole session, including its asynchronous work.
    // An old answer must never advance a newly selected deck's queue.
    key(deckId) {
        SessionPaneContent(container, settings, palette, deckId, onChanged, onBack, showBack, decksCollapsed, onToggleDecks)
    }
}

@Composable
private fun SessionPaneContent(
    container: DesktopContainer,
    settings: IknaSettings,
    palette: IknaPalette,
    deckId: String?,
    onChanged: () -> Unit,
    onBack: () -> Unit,
    showBack: Boolean,
    decksCollapsed: Boolean,
    onToggleDecks: (() -> Unit)?
) {
    val scope = rememberCoroutineScope()
    val committedGeneration by container.sessionChanges.generation.collectAsState()
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val presentationGate = remember { SessionPresentationGate<SessionObservation>() }
    var observedGeneration by remember { mutableStateOf(container.sessionChanges.generation.value) }
    var sessionChanged by remember { mutableStateOf(false) }
    var inspecting by remember { mutableStateOf(false) }
    val invalidated = committedGeneration != observedGeneration
    fun acceptsInput(): Boolean = !sessionChanged && !inspecting &&
        container.sessionChanges.generation.value == observedGeneration

    var plan by remember(deckId) { mutableStateOf<SessionPlan?>(null) }
    var index by remember { mutableStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    var loading by remember(deckId) { mutableStateOf(true) }
    var loadFailed by remember(deckId) { mutableStateOf(false) }
    var shownNanos by remember { mutableStateOf(System.nanoTime()) }
    var reload by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val heldReviewKeys = remember { mutableSetOf<Key>() }
    var programmaticSwipe by remember { mutableStateOf<ProgrammaticSwipe?>(null) }
    var programmaticSwipeToken by remember { mutableStateOf(0) }
    val clipboard = LocalClipboardManager.current
    var saving by remember { mutableStateOf(false) }
    var answeredHere by remember { mutableStateOf(0) }
    var undoVisible by remember { mutableStateOf(false) }
    var undoFailed by remember { mutableStateOf(false) }
    var wrongMarked by remember { mutableStateOf(false) }
    var wrongSourceId by remember { mutableStateOf<String?>(null) }
    var reportCopied by remember { mutableStateOf(false) }
    var reportCard by remember { mutableStateOf<SessionCard?>(null) }
    var noMoreExtra by remember { mutableStateOf(false) }
    var optionalBatch by remember(deckId) { mutableStateOf<OptionalHardBatch?>(null) }
    var optionalCards by remember(deckId) { mutableStateOf<List<SessionCard>>(emptyList()) }
    var optionalAvailable by remember(deckId) { mutableStateOf(false) }
    var optionalEmpty by remember(deckId) { mutableStateOf(false) }
    // Keep the same quiet Undo strip through a pause, until a later action or dismissal.

    suspend fun rememberActionObservation(generation: Long, change: SessionPlanChange = SessionPlanChange.Stable): Boolean {
        if (container.sessionChanges.generation.value != generation) return false
        val snapshot = cardLoadResult { container.learningRepository.inspectSession(deckId) }.getOrNull()
        if (container.sessionChanges.generation.value != generation) return false
        if (snapshot == null) { loadFailed = true; undoVisible = false; return false }
        if (!SessionObservationPolicy.compatible(presentationGate.observation, snapshot.observation, change)) {
            sessionChanged = true
            undoVisible = false
            revealed = false
            reportCard = null
            return false
        }
        return presentationGate.remember(presentationGate.generation, snapshot.observation)
    }

    LaunchedEffect(deckId, reload) {
        val generation = container.sessionChanges.generation.value
        if (generation != observedGeneration) {
            sessionChanged = true
            undoVisible = false
            observedGeneration = generation
            loading = false
            return@LaunchedEffect
        }
        val token = presentationGate.invalidate()
        sessionChanged = false
        loading = true
        loadFailed = false
        plan = null
        reportCard = null
        heldReviewKeys.clear()
        cardLoadResult {
            optionalBatch?.let {
                container.learningRepository.cancelOptionalHardReview(it)
                undoVisible = false
                undoFailed = false
            }
            optionalBatch = null
            optionalCards = emptyList()
            val loaded = container.learningRepository.buildSession(deckId = deckId)
            val snapshot = container.learningRepository.inspectSession(deckId)
            // Explicit developer sandbox entry keeps its pre-existing forced practice.
            // Automatic inspection never creates forced work, nor does normal learner entry.
            if (container.isDeveloperMode && loaded.forcedByDeveloper) snapshot.copy(plan = loaded) else snapshot
        }.fold(
            onSuccess = {
                if (generation == container.sessionChanges.generation.value && presentationGate.isCurrent(token)) {
                    plan = it.plan
                    sessionChanged = it.plan == null
                    optionalAvailable = !sessionChanged && it.optionalHardAvailable
                    presentationGate.remember(token, it.observation)
                    observedGeneration = generation
                } else {
                    sessionChanged = true
                    observedGeneration = container.sessionChanges.generation.value
                }
            },
            onFailure = {
                undoVisible = false
                if (generation != container.sessionChanges.generation.value) {
                    sessionChanged = true
                    observedGeneration = container.sessionChanges.generation.value
                } else loadFailed = true
            }
        )
        index = 0
        answeredHere = 0
        revealed = false
        programmaticSwipe = null
        shownNanos = System.nanoTime()
        loading = false
        runCatching { focus.requestFocus() }
    }

    // This observer never builds a plan or admits material. Accepted writes finish first.
    LaunchedEffect(committedGeneration, windowFocused, saving, loading) {
        if (saving || loading || (!windowFocused && !invalidated) || presentationGate.observation == null) return@LaunchedEffect
        val generation = container.sessionChanges.generation.value
        val token = presentationGate.invalidate()
        inspecting = true
        try {
            val result = cardLoadResult { container.learningRepository.inspectSession(deckId) }
            if (!presentationGate.isCurrent(token) || generation != container.sessionChanges.generation.value) return@LaunchedEffect
            result.fold(onSuccess = { snapshot ->
                val previous = presentationGate.observation
                if (presentationGate.changed(snapshot.observation) || generation != observedGeneration) {
                    // A different day or replaced/missing persisted plan requires explicit admission.
                    sessionChanged = snapshot.plan == null || !SessionObservationPolicy.compatible(previous,
                        snapshot.observation, SessionPlanChange.Stable)
                    optionalBatch?.let { container.learningRepository.cancelOptionalHardReview(it) }
                    optionalBatch = null
                    optionalCards = emptyList()
                    undoVisible = false
                    undoFailed = false
                    reportCard = null
                    revealed = false
                    programmaticSwipe = null
                    heldReviewKeys.clear()
                    index = 0
                    answeredHere = 0
                    plan = if (sessionChanged) null else snapshot.plan
                    optionalAvailable = !sessionChanged && snapshot.optionalHardAvailable
                    optionalEmpty = false
                    shownNanos = System.nanoTime()
                    loadFailed = false
                }
                presentationGate.remember(token, snapshot.observation)
                observedGeneration = generation
            }, onFailure = {
                loadFailed = true
                undoVisible = false
                plan = null
                observedGeneration = generation
            })
        } finally { inspecting = false }
    }

    val cards = if (optionalBatch != null) optionalCards else plan?.cards.orEmpty()
    val current = if (loading || loadFailed || invalidated || sessionChanged || inspecting) null else cards.getOrNull(index)
    // A clickable card can become the focused Compose node. Reclaim the
    // session's keyboard target after every reveal so A/D work identically
    // whether the answer was opened with Space, a click or a short pull.
    LaunchedEffect(revealed, current?.card?.key) {
        if (revealed) runCatching { focus.requestFocus() }
    }
    val reviewSignals = remember(deckId, reload, index, cards.getOrNull(index)?.card?.key, loading) {
        ReviewSignalTracker()
    }
    val reveal: (String) -> Unit = { inputMethod ->
        if (current != null && !loading && !loadFailed && !saving && acceptsInput()) {
            reviewSignals.reveal(inputMethod)
            revealed = true
        }
    }

    val advance: () -> Unit = {
        revealed = false
        programmaticSwipe = null
        shownNanos = System.nanoTime()
        if (optionalBatch != null || index + 1 < cards.size) index += 1
        else { loading = true; reload += 1 }
        onChanged()
    }

    val gradeWithSignals: (Rating, ReviewSignals) -> Unit = { rating, signals ->
        val card = current
        if (card != null && !loading && !saving && acceptsInput() && reportCard == null) {
            saving = true
            val took = ReviewDuration.elapsedMillis(shownNanos, System.nanoTime())
            val actionGeneration = container.sessionChanges.generation.value
            scope.launch {
                val result = cardLoadResult {
                    val batch = optionalBatch
                    if (batch != null) {
                        container.learningRepository.answerOptionalHard(batch, card, rating, took,
                            now = System.currentTimeMillis(), signals = signals)
                    } else {
                        container.learningRepository.answer(card, rating, took,
                            now = System.currentTimeMillis(), signals = signals)
                    }
                }
                if (!rememberActionObservation(actionGeneration, if (optionalBatch == null) SessionPlanChange.AppendExtras else SessionPlanChange.Stable)) { saving = false; return@launch }
                if (result.isSuccess) {
                    if (signals.inputMethod == "swipe") cardLoadResult { container.settings.bumpSwipe() }
                    if (actionGeneration != container.sessionChanges.generation.value) { saving = false; return@launch }
                    answeredHere += 1
                    wrongMarked = false
                    undoFailed = false
                    undoVisible = true
                    note = null
                    advance()
                } else {
                    logLine("answer failed: " + result.exceptionOrNull())
                    note = S.t("set.057")
                    if (optionalBatch != null) {
                        loadFailed = true
                        optionalAvailable = false
                        undoVisible = false
                    }
                }
                saving = false
            }
        }
    }

    val requestKeyboardSwipe: (Rating) -> Unit = { rating ->
        if (current != null && !loading && !saving && acceptsInput() && programmaticSwipe == null) {
            programmaticSwipeToken += 1
            programmaticSwipe = ProgrammaticSwipe(programmaticSwipeToken, rating)
        }
    }

    val undo: () -> Unit = {
        if (!loading && !loadFailed && !saving && acceptsInput()) {
            saving = true
            val actionGeneration = container.sessionChanges.generation.value
            scope.launch {
                val batch = optionalBatch
                if (batch != null) {
                    val restored = cardLoadResult { container.learningRepository.undoOptionalHard(batch) }.getOrNull()
                    if (!rememberActionObservation(actionGeneration)) { saving = false; return@launch }
                    undoFailed = restored == null
                    if (restored != null) {
                        val previous = (index - 1).coerceAtLeast(0)
                        optionalCards = optionalCards.toMutableList().also { it[previous] = restored }
                        index = previous
                        answeredHere = (answeredHere - 1).coerceAtLeast(0)
                        revealed = false
                        programmaticSwipe = null
                        shownNanos = System.nanoTime()
                        onChanged()
                    }
                } else {
                    val restored = cardLoadResult { container.learningRepository.undoLast() }.getOrNull()
                    if (!rememberActionObservation(actionGeneration)) { saving = false; return@launch }
                    undoFailed = restored == null
                    if (restored != null) { loading = true; reload += 1; onChanged() }
                }
                undoVisible = false
                wrongMarked = false
                saving = false
            }
        }
    }

    val addMore: () -> Unit = {
        if (!loading && !loadFailed && !saving && acceptsInput()) {
            saving = true
            val actionGeneration = container.sessionChanges.generation.value
            scope.launch {
                cardLoadResult {
                    optionalBatch?.let {
                        container.learningRepository.cancelOptionalHardReview(it)
                        undoVisible = false
                        undoFailed = false
                    }
                    optionalBatch = null
                    optionalEmpty = false
                    container.learningRepository.addExtra(count = 5, deckId = deckId)
                }.fold(
                    onSuccess = { added ->
                        if (!rememberActionObservation(actionGeneration, SessionPlanChange.AppendExtras)) { saving = false; return@launch }
                        noMoreExtra = added == 0
                        loading = true
                        reload += 1
                        onChanged()
                    },
                    onFailure = {
                        loadFailed = true
                        undoVisible = false
                    }
                )
                saving = false
            }
        }
    }

    val startOptional: () -> Unit = {
        if (!loading && !loadFailed && !saving && acceptsInput()) {
            saving = true
            val actionGeneration = container.sessionChanges.generation.value
            scope.launch {
                cardLoadResult {
                    val batch = container.learningRepository.startOptionalHardReview(deckId)
                    val answered = container.learningRepository.answeredToday()
                    Triple(batch, answered, container.learningRepository.goalAnswersToday())
                }.fold(
                    onSuccess = { (batch, answered, goalAnswered) ->
                        if (!rememberActionObservation(actionGeneration)) { saving = false; return@launch }
                        optionalBatch = batch
                        optionalCards = batch?.cards.orEmpty()
                        optionalEmpty = batch == null
                        optionalAvailable = batch != null
                        if (batch != null) {
                            plan = plan?.copy(answeredToday = answered, goalAnswersToday = goalAnswered)
                            index = 0
                            answeredHere = 0
                            revealed = false
                            programmaticSwipe = null
                            shownNanos = System.nanoTime()
                            undoVisible = false
                            undoFailed = false
                            wrongMarked = false
                            noMoreExtra = false
                            note = null
                        } else {
                            // A finished/replaced optional batch cannot become normal
                            // goal credit through its transient answeredHere counter.
                            undoVisible = false
                            undoFailed = false
                            loading = true
                            reload += 1
                        }
                    },
                    onFailure = { loadFailed = true; optionalAvailable = false; undoVisible = false }
                )
                saving = false
            }
        }
    }
    val finishOptional: () -> Unit = {
        if (!loading && !saving && acceptsInput() && optionalBatch != null) {
            // The normal reload cancels the ephemeral batch; saved answers remain.
            loading = true
            reload += 1
        }
    }

    val wrong: () -> Unit = {
        val card = current
        if (card != null && !loading && !saving && acceptsInput()) {
            saving = true
            val actionGeneration = container.sessionChanges.generation.value
            scope.launch {
                val result = cardLoadResult { container.learningRepository.markWrong(card) }
                if (!rememberActionObservation(actionGeneration, SessionPlanChange.RemoveTarget(card.chunk.id))) { saving = false; return@launch }
                if (result.isSuccess) {
                    wrongMarked = true
                    wrongSourceId = card.sourceId
                    undoVisible = false
                    loading = true
                    reload += 1
                    onChanged()
                } else note = S.t("set.057")
                saving = false
            }
        }
    }
    val presentation = SessionUiState(loading = loading || invalidated || inspecting, loadFailed = loadFailed, sessionChanged = sessionChanged, queue = cards, index = index,
        revealed = revealed, remaining = (cards.size - index).coerceAtLeast(0),
        answeredToday = (plan?.answeredToday ?: 0) + answeredHere,
        goalAnswersToday = (plan?.goalAnswersToday ?: 0) + if (optionalBatch == null) answeredHere else 0,
        dailyMinimum = container.learningRepository.dailyMinimum(),
        sessionDone = if (optionalBatch != null) index else (plan?.sessionDone ?: 0) + index,
        sessionTotal = if (optionalBatch != null) optionalCards.size else plan?.sessionTotal ?: 0,
        deckTitle = plan?.deckTitle, perCardMs = settings.answerMs.takeIf { it > 0 }?.toLong(),
        reason = plan?.reason ?: dev.ikna.domain.governor.GovernorReason.OK,
        nextDueAt = plan?.nextDueAt, noMoreExtra = noMoreExtra,
        optionalHardReview = optionalBatch != null, optionalHardAvailable = optionalAvailable,
        optionalHardEmpty = optionalEmpty)
    val sessionDecks by produceState(emptyMap<String, DeckSummary>(), container) {
        value = runCatching { container.deckRepository.decks().associateBy { it.id } }.getOrDefault(emptyMap())
    }
    val hotkeys = remember(settings.hotkeys) { HotkeyBindings.decode(settings.hotkeys) }
    // Keyboard discoverability: the hint names the bindings the user actually
    // has, not the defaults (docs/ai-audits.md 7, Track G).


    LaunchedEffect(current?.card?.key, reportCard, loading) {
        if (!loading && reportCard == null) runCatching { focus.requestFocus() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    heldReviewKeys.remove(event.key)
                } else if (loading || loadFailed || saving || !acceptsInput() || reportCard != null || event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    val action = hotkeyAction(event, hotkeys) ?: return@onPreviewKeyEvent false
                    if (!heldReviewKeys.add(event.key)) return@onPreviewKeyEvent true
                    when (action) {
                        HotkeyAction.MISS -> if (current != null) {
                            if (revealed) requestKeyboardSwipe(Rating.AGAIN)
                            true
                        } else false
                        HotkeyAction.KNOW -> if (current != null) {
                            if (revealed) requestKeyboardSwipe(Rating.GOOD)
                            true
                        } else false
                        HotkeyAction.REVEAL -> if (current != null && !revealed) {
                            reveal(INPUT_KEYBOARD)
                            true
                        } else false
                        HotkeyAction.UNDO -> {
                            undo()
                            true
                        }
                    }
                }
            }
            .focusable()
            // Only the header is inset. The card below is the whole pane, edge
            // to edge, exactly as it is the whole screen on the phone.
            .padding(vertical = 0.dp)
    ) {
        // Chrome, hint and card share one reading measure, so the session
        // composes around its phrase; the keyboard hint sits in the counter's
        // own row instead of drifting at a far edge.
        if (!loading && !loadFailed && !invalidated && !sessionChanged && !inspecting) {
            Column(Modifier.fillMaxWidth().widthIn(max = 680.dp).align(Alignment.CenterHorizontally)) {
                IknaSessionTopBar(presentation, onFinishOptional = finishOptional)
                IknaTodayProgress(state = presentation)
            }

        }
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (sessionChanged && !invalidated && !inspecting) {
                IknaSessionChanged(onContinue = {
                    if (!saving) { loading = true; reload += 1 }
                })
            } else if (loading || invalidated || inspecting) {
                Text(
                    text = S.t("sess.001"),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.muted
                )
            } else if (loadFailed) {
                IknaCardLoadFailure(onRetry = {
                    if (!loading && !saving && acceptsInput()) { loading = true; reload += 1 }
                })
            } else if (current == null) {
                IknaSessionEmptyState(presentation, settings.animations, onExtra = addMore, onOptionalHard = startOptional)
            } else {
                val mode = settings.phoneticsFor(current.chunk.packId)
                val subject = current.chunk.lang == NO_LANG
                val cardWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
                // The gesture is a share of the card. The phone's 140 pixels were
                // measured against a screen that IS the card. A mouse needs a
                // shorter deliberate move than a thumb, especially in a wide
                // window. Android keeps its own 140 px threshold; only Desktop
                // uses this bounded eight-percent line. The shared gesture still
                // measures real drag latency and release velocity unchanged.
                val swipeLine = (cardWidthPx * 0.08f).coerceIn(40f, 112f)
                Box(Modifier.fillMaxSize().clipToBounds()) {
                    SwipeableCard(
                        key = current.card.key + ":" + index,
                        revealed = revealed,
                        animations = settings.animations,
                        haptics = settings.haptics,
                        hasNextCard = index + 1 < cards.size,
                        saving = saving,
                        shortcuts = CardShortcuts(
                            reveal = hotkeys[HotkeyAction.REVEAL]?.encoded,
                            miss = hotkeys[HotkeyAction.MISS]?.encoded,
                            know = hotkeys[HotkeyAction.KNOW]?.encoded
                        ),
                        onReveal = reveal,
                        onRate = gradeWithSignals,
                        signals = reviewSignals,
                        programmaticSwipe = programmaticSwipe,
                        onProgrammaticSwipeHandled = { token ->
                            if (programmaticSwipe?.token == token) programmaticSwipe = null
                        },
                        threshold = swipeLine
                    ) {
                        ChunkCard(
                            label = askLabel(current.ask, subject),
                            language = current.chunk.lang.takeUnless { it == NO_LANG },
                            deck = sessionDecks[current.chunk.packId],
                            look = settings.lookFor(current.chunk.packId),
                            prompt = current.prompt,
                            answer = current.answer,
                            promptTarget = current.promptTarget,
                            answerTarget = current.answerTarget,
                            promptTranscription = Phonetics.line(
                                ipa = current.promptIpa,
                                lang = current.chunk.lang,
                                mode = mode
                            ),
                            answerTranscription = Phonetics.line(
                                ipa = current.answerIpa,
                                lang = current.chunk.lang,
                                mode = mode
                            ),
                            hint = null,
                            sourceLabel = current.sourceId?.let { S.t("src.001") + "Tatoeba #" + it },
                            onSource = current.sourceId?.let { id -> { tatoebaSentenceUrl(id)?.let(::openInBrowser); Unit } },
                            revealed = revealed,
                            onTap = { reveal(INPUT_SWIPE) },
                            tapEnabled = !revealed,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        val message = note
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted
            )
        }

        IknaSessionUndoBar(undoVisible && !loading && !loadFailed && acceptsInput(), undoFailed && !loading && !loadFailed && acceptsInput(), wrongMarked && acceptsInput(), wrongSourceId, reportCopied,
            onUndo = undo, onDismiss = { undoVisible = false },
            onOpenSource = { id -> tatoebaSentenceUrl(id)?.let(::openInBrowser) })
        IknaBottomBar {
            // The session pane is the app's default state in a wide window, so
            // back has nowhere to take the learner; the button only appears
            // where it can actually do something (narrow layout, list hidden).
            if (showBack) {
                IknaIconButton(IknaGlyph.BACK, onClick = onBack, label = S.t("a11y.001"))
            }
            if (onToggleDecks != null) {
                IknaIconButton(
                    glyph = IknaGlyph.DECK_PANEL,
                    onClick = onToggleDecks,
                    label = S.t(if (decksCollapsed) "deck.panel_show" else "deck.panel_hide")
                )
            }
            Spacer(Modifier.weight(1f))
            if (current != null && revealed && !saving) {
                IknaTextButton(S.t("sess.044"), onClick = {
                    reportCopied = false
                    if (catalogCardReport(current.chunk) != null) reportCard = current else wrong()
                }, color = palette.muted)
            }
        }
    }
    reportCard?.takeIf { acceptsInput() }?.let { pending ->
        IknaDialog(S.t("src.002"), S.t("src.003"), S.t("src.004"),
            onConfirm = {
                reportCopied = runCatching {
                    val text = catalogCardReport(pending.chunk) ?: return@runCatching false
                    clipboard.setText(AnnotatedString(text)); true
                }.getOrDefault(false)
                reportCard = null
                wrong()
            }, dismissLabel = S.t("src.005"), onDismiss = { reportCard = null })
    }

}

/** What the card is asking, in the phone's own words. */
private fun askLabel(ask: Ask, subject: Boolean): String = when (ask) {
    Ask.RECOGNISE -> if (subject) S.t("sess.046") else S.t("sess.015")
}
