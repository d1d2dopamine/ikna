package dev.ikna.ui.session
import dev.ikna.domain.session.SessionCard
import dev.ikna.domain.session.Ask
import dev.ikna.domain.governor.GovernorReason

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
    /**
     * A card was just thrown away as wrong. Worth one quiet line, because a
     * card disappearing without a word looks like the app losing work.
     */
    val wrongMarked: Boolean = false,
    /** Tatoeba sentence left behind by the card that was just hidden. */
    val wrongSourceId: String? = null,
    /** Whether a paste-ready catalogue report reached the clipboard. */
    val wrongReportCopied: Boolean = false,
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
            card.ask == Ask.RECOGNISE || revealed
        } == true
}
