package dev.ikna.data.repo

import dev.ikna.data.db.CardDao
import dev.ikna.data.db.CardEntity
import dev.ikna.data.db.ChunkDao
import dev.ikna.data.db.ChunkEntity
import dev.ikna.data.db.DailyPlanEntity
import dev.ikna.data.db.DailyStatEntity
import dev.ikna.data.db.GovernorDao
import dev.ikna.data.db.GovernorLogEntity
import dev.ikna.data.db.PlanDao
import dev.ikna.data.db.ReviewDao
import dev.ikna.data.db.ReviewEntity
import dev.ikna.data.db.StatsDao
import dev.ikna.domain.fsrs.ComponentPrior
import dev.ikna.domain.fsrs.DAY_MS
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.ChunkSelector
import dev.ikna.domain.governor.CleanStreak
import dev.ikna.domain.governor.GovernorConfig
import dev.ikna.domain.governor.GovernorDecision
import dev.ikna.domain.governor.GovernorReason
import dev.ikna.domain.governor.GovernorSignals
import dev.ikna.domain.governor.LoadGovernor
import dev.ikna.domain.session.Level
import dev.ikna.domain.session.SessionBuilder
import dev.ikna.domain.session.SessionCard
import dev.ikna.domain.session.SessionPlan
import dev.ikna.domain.session.Shapes
import dev.ikna.domain.time.DayBoundary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LearningRepository(
    private val cardDao: CardDao,
    private val chunkDao: ChunkDao,
    private val reviewDao: ReviewDao,
    private val statsDao: StatsDao,
    private val governorDao: GovernorDao,
    private val planDao: PlanDao,
    private val components: ComponentRepository,
    private val scheduler: Scheduler,
    private val selector: ChunkSelector,
    private val baseConfig: GovernorConfig
) {

    /** The load switch as stored, read on demand. See [loadSettings]. */
    data class LoadSetting(val auto: Boolean, val manual: Int)

    /**
     * Where the size of a day really comes from.
     *
     * [dailyTargetOverride] lives in the process, and it was only ever filled
     * in by a settings flow that a screen had to be alive to collect. So a
     * plan built by the nightly worker, or by the first screen of a freshly
     * started process, could use the 40 from `governor.json` while the user's
     * own norm said 15 -- and the day arrived a third too big, with nothing on
     * any screen to explain it. Reading the stored value at the moment the
     * plan is built removes the race instead of narrowing it.
     */
    @Volatile var loadSettings: (suspend () -> LoadSetting)? = null

    /**
     * The chunks the learner has marked as wrong, read on demand.
     *
     * A deck written by a model can contain a card that is false, and this
     * scheduler is very good at teaching whatever it is handed. So there is a
     * third answer beside "knew it" and "did not", and everything it names is
     * filtered out of introductions, plans and sessions alike.
     *
     * A function rather than a stored set, for the same reason [loadSettings] is
     * one: the plan can be built by a background worker in a process where no
     * screen has ever collected a flow.
     */
    @Volatile var suppressedChunks: (suspend () -> Set<String>)? = null

    /** Writes one chunk into that set. Wired to the settings store. */
    @Volatile var onSuppress: (suspend (String) -> Unit)? = null

    /** Set from the load switch in settings: calm / normal / dense. */
    @Volatile var dailyTargetOverride: Int? = null

    /**
     * When true, the size of a normal day is measured instead of chosen: see
     * [autoTarget]. On by default, because asking the user to predict their own
     * capacity is asking them to make a decision, and the decision is the
     * expensive part — they will either guess high and drown, or never answer.
     */
    @Volatile var autoLoad: Boolean = true

    /**
     * One writer at a time.
     *
     * Answering is a read-modify-write: read today's counter, add one,
     * write it back. Two fast swipes overlapped, both read the same value,
     * and the second write erased the first — a card answered but not
     * counted. That number is not cosmetic: it feeds the measured norm and
     * the load governor, so a lost answer quietly shrinks tomorrow. Swiping
     * fast is not misuse here, it is the normal speed of a good session.
     * These writes are milliseconds long, so serialising them costs nothing
     * the user can feel.
     */
    private val writeLock = Mutex()

    /**
     * The mornings whose verdict was about room rather than about the user.
     * Only these can be reopened by work done later in the day.
     */
    private val EARNABLE_REASONS = setOf(
        GovernorReason.OK,
        GovernorReason.NO_HEADROOM,
        GovernorReason.FIRST_RUN
    )

    private val config: GovernorConfig
        get() = baseConfig.copy(
            targetDailyReviews = dailyTargetOverride ?: baseConfig.targetDailyReviews
        )

    // Cheap objects, rebuilt per call so a settings change takes effect at once.
    private fun governor() = LoadGovernor(config)
    private fun builder() = SessionBuilder(cardDao, chunkDao, config)

    /**
     * What must never be asked again. Empty when nothing is wired, which is the
     * safe direction: a missing correction shows a card, a missing card cannot be
     * corrected.
     */
    private suspend fun suppressedNow(): Set<String> =
        suppressedChunks?.invoke() ?: emptySet()


    // Rebuilt per call so a config change takes effect immediately.
    private val boundary: DayBoundary
        get() = DayBoundary(config.dayStartHour)

    /**
     * Which day a moment belongs to. The night belongs to the evening that
     * produced it: see [DayBoundary].
     */
    fun dayKey(ts: Long): String = boundary.key(ts)

    fun dailyMinimum(): Int = config.dailyMinimumCards

    // ---- daily plan -------------------------------------------------------

    /**
     * The plan for today, computed at most once per calendar day.
     *
     * This idempotence is the fix for the counter bug. Previously the plan was
     * recomputed on every entry to the session screen: answering cards lowered
     * the due count, the governor saw free headroom, spent it on new chunks,
     * and the number at the top of the screen went up while the user worked.
     * Now the day's questions are decided once and stored; the only way the set
     * grows is [addExtra], which the user triggers on purpose.
     */
    suspend fun ensureDailyPlan(now: Long = System.currentTimeMillis()): DailyPlanEntity =
        writeLock.withLock { ensureDailyPlanLocked(now) }

    private suspend fun ensureDailyPlanLocked(now: Long): DailyPlanEntity {
        val day = dayKey(now)
        val existing = planDao.plan(day)

        // A stored plan is authoritative: today's questions are decided once.
        if (existing != null) return existing

        // What the user actually set, straight from storage. Without this the
        // switch only reached the plan if some screen had already collected it.
        loadSettings?.invoke()?.let { stored ->
            autoLoad = stored.auto
            if (!stored.auto) dailyTargetOverride = stored.manual
        }

        // The measured norm is refreshed once a day, right here, so today's
        // capacity reflects the last two weeks of real behaviour.
        if (autoLoad) dailyTargetOverride = autoTarget()

        // Overdue cards leave the visible queue. They are not forgiven, only
        // hidden: FSRS still recomputes their stability from real elapsed time.
        cardDao.moveOverdueToAmnesty(now - config.amnestyAfterDays * DAY_MS)

        val signals = collectSignals(now)
        val decision = withNightRule(governor().decide(signals), now)

        governorDao.insert(
            GovernorLogEntity(
                ts = now,
                day = day,
                dueToday = signals.dueToday,
                forecastAvg3d = signals.forecastAvg3d,
                backlog = signals.backlog,
                accuracyRecent = signals.accuracyRecent,
                daysSinceLastSession = signals.daysSinceLastSession,
                reviewsDoneToday = signals.reviewsDoneToday,
                capacity = decision.capacity,
                headroom = decision.headroom,
                allowedNew = decision.allowedNew,
                // The valve's own name plus what it overrode. A log saying
                // only SAFETY_VALVE cannot answer the one question worth
                // asking about the valve: why it had to open again.
                reason = decision.gate
                    ?.let { decision.reason.name + "/" + it.name }
                    ?: decision.reason.name
            )
        )

        // New chunks are introduced exactly once per day, here and nowhere else.
        val introduced =
            if (decision.allowedNew > 0) introduce(decision.allowedNew, now) else emptyList()

        // The introductions are handed to the builder rather than left for it to
        // find. A fresh card is written with dueAt = now, so a "soonest first"
        // query sorts it behind every overdue review and the capacity limit cut
        // it first: the governor spent the day's new-material budget, the
        // counter recorded that new material had arrived — which is what closes
        // the safety valve for a week — and the user was shown none of it.
        // A card marked wrong never reaches a plan again, on any day.
        val picked = builder().pickForDay(decision, now, introduced)
            .filterNot { it.chunkId in suppressedNow() }
        val plannedKeys = picked.map { it.key }.toSet()

        // Whatever still did not fit is deleted rather than left behind as a
        // card nobody has ever seen but which counts as introduced, and only
        // what actually reached the plan is counted.
        val placed = introduced.count { it.key in plannedKeys }
        for (card in introduced) {
            if (card.key !in plannedKeys) cardDao.delete(card.chunkId, card.level)
        }
        countIntroduced(now, placed)

        val alreadyAnswered = reviewDao.answeredKeysSince(startOfDay(now)).toSet()
        val ids = picked.map { it.key }.filterNot { it in alreadyAnswered }

        return storePlan(
            day = day,
            ids = ids,
            capacity = decision.capacity,
            allowedNew = decision.allowedNew,
            amnestyQuota = decision.amnestyQuota,
            reason = decision.reason,
            now = now
        )
    }

    /** Kept for the nightly worker, which only needs the side effects. */
    suspend fun runDailyPlan(now: Long = System.currentTimeMillis()): DailyPlanEntity =
        ensureDailyPlan(now)

    private suspend fun storePlan(
        day: String,
        ids: List<String>,
        capacity: Int,
        allowedNew: Int,
        amnestyQuota: Int,
        reason: GovernorReason,
        now: Long
    ): DailyPlanEntity {
        val plan = DailyPlanEntity(
            day = day,
            plannedIds = ids.joinToString(","),
            plannedTotal = ids.size,
            capacity = capacity,
            allowedNew = allowedNew,
            amnestyQuota = amnestyQuota,
            reason = reason.name,
            extraRequested = 0,
            createdAt = now
        )
        planDao.upsert(plan)
        planDao.clearOtherThan(day)
        return plan
    }

    // An absence used to be repaid here, by adding the unused part of every
    // missed day to every due date in the table. That is gone, and deliberately:
    //
    //  - the same query also moved lastReviewAt, and lastReviewAt is how FSRS
    //    knows how much time really passed. Shifting it tells the scheduler that
    //    a card seen three weeks ago was seen yesterday, so stability is
    //    recomputed from a number that never happened;
    //  - nothing about the shift was written to the review log, so restoring
    //    that log onto another phone produced different due dates than the phone
    //    it came from — the log stopped being a full description of the state;
    //  - the app already has a mechanism for exactly this, and it is the one the
    //    documentation describes: overdue cards move into the amnesty pool and
    //    come back a fifth of a session at a time, with return mode capping the
    //    day after a long absence. Two mechanisms for one problem meant the
    //    queue was being softened twice.
    //
    // expectedForCredit() stays: activityRatio still needs to know what a fully
    // used day looks like.

    /**
     * How much work counts as a day fully used. Deliberately generous: a third
     * of the norm buys the whole day, because the goal is a queue that stays
     * small, not a quota that has to be met.
     */
    private suspend fun expectedForCredit(): Double =
        max(
            config.dailyMinimumCards.toDouble(),
            currentDailyTarget() * config.idleCreditRatio
        )

    /**
     * The share of the last week that was actually used, 0..1.
     *
     * This is what replaces "did the user skip yesterday": one missed day out of
     * seven barely moves it, three quiet days halve it, and a week away drives it
     * to zero. The governor reads it and stops handing out new chunks long before
     * the queue turns into a pile. A brand new account reads as fully active, so
     * a first day is never punished for having no history.
     */
    suspend fun activityRatio(now: Long = System.currentTimeMillis()): Double {
        val window = config.activityWindowDays
        val all = statsDao.lastDays(window * 3)
        if (all.isEmpty()) return 1.0

        val cutoff = dayKey(now - (window - 1) * DAY_MS)
        val inWindow = all.filter { it.day >= cutoff }

        val today = LocalDate.parse(dayKey(now))
        val firstEver = runCatching { LocalDate.parse(all.last().day) }.getOrNull()
        val span = if (firstEver == null) window
        else (ChronoUnit.DAYS.between(firstEver, today).toInt() + 1).coerceIn(1, window)

        val expected = expectedForCredit()
        val used = inWindow.sumOf { (it.reviewsDone / expected).coerceIn(0.0, 1.0) }
        // The norm is weekly, not daily. Dividing by the whole window made an
        // entirely ordinary week — four full days, three off — read as 0.57,
        // one quiet evening away from the gate that stops new chunks. Four or
        // five used days now count as a week that went fine.
        val need = minOf(span.toDouble(), config.activeDaysPerWeek).coerceAtLeast(1.0)
        return (used / need).coerceIn(0.0, 1.0)
    }

    /**
     * The measured size of a normal day.
     *
     * Median of cards actually answered on active days over the last two weeks,
     * plus a little headroom so a good stretch can still grow the load. Median
     * rather than mean because one heroic evening should not become the new
     * standard. Fewer than three active days means there is nothing to measure
     * yet, so it starts deliberately small.
     */
    suspend fun autoTarget(): Int {
        val done = statsDao.lastDays(AUTO_WINDOW_DAYS)
            .map { it.reviewsDone }
            .filter { it > 0 }
            .sorted()
        if (done.size < 3) return AUTO_COLD_START
        val median = done[done.size / 2]
        return (median * AUTO_HEADROOM).toInt().coerceIn(AUTO_MIN, AUTO_MAX)
    }

    /** What today's plan is aiming at. Shown on the progress screen. */
    suspend fun currentDailyTarget(): Int =
        if (autoLoad) autoTarget() else (dailyTargetOverride ?: baseConfig.targetDailyReviews)

    /**
     * Whether the norm above is measured or still a placeholder.
     *
     * [AUTO_COLD_START] is a guess for the first days, and showing a guess as a
     * measurement is how an app teaches the user to distrust all of its numbers.
     * The screens ask this before printing a figure.
     */
    suspend fun normIsMeasured(): Boolean =
        !autoLoad || statsDao.lastDays(AUTO_WINDOW_DAYS).count { it.reviewsDone > 0 } >= 3

    /**
     * Which of the last [days] days had a session, most recent first.
     *
     * A map, not a chain: the progress screen draws these as separate marks so a
     * gap is a gap and not a broken streak.
     */
    suspend fun activityMap(days: Int = 30, now: Long = System.currentTimeMillis()): List<Boolean> {
        val active = statsDao.lastDays(days)
            .filter { it.reviewsDone > 0 }
            .map { it.day }
            .toSet()
        return (0 until days).map { d -> dayKey(now - d * DAY_MS) in active }
    }

    suspend fun collectSignals(now: Long): GovernorSignals {
        val dueToday = cardDao.dueCount(now)
        val backlog = cardDao.amnestyCount()

        var forecastTotal = 0
        for (d in 1..config.forecastHorizonDays) {
            forecastTotal += cardDao.dueBetween(now + (d - 1) * DAY_MS, now + d * DAY_MS)
        }
        val forecastAvg = forecastTotal.toDouble() / max(1, config.forecastHorizonDays)

        val recent = reviewDao.recent(config.recentWindowSize)
        val accuracy = if (recent.isEmpty()) 1.0
        else recent.count { it.rating >= 3 }.toDouble() / recent.size

        val lastTs = reviewDao.lastReviewTs()
        val daysSince = if (lastTs == null) 0
        else ((startOfDay(now) - startOfDay(lastTs)) / DAY_MS).toInt()

        val today = statsDao.day(dayKey(now))
        val cleanDays = countCleanDays(now)
        // The safety valve window, taken from the config instead of a 7
        // hard-coded next to a setting called safetyValveDays.
        val valveWindowStart = dayKey(now - config.safetyValveDays * DAY_MS)

        return GovernorSignals(
            dueToday = dueToday,
            forecastAvg3d = forecastAvg,
            backlog = backlog,
            accuracyRecent = accuracy,
            activityRatio = activityRatio(now),
            daysSinceLastSession = daysSince,
            daysSinceStart = daysSinceStart(now),
            reviewsDoneToday = today?.reviewsDone ?: 0,
            cleanDays = cleanDays,
            newIntroducedLastWeek = statsDao.newIntroducedSince(valveWindowStart) ?: 0,
            totalReviews = reviewDao.total(),
            daysSinceReturn = daysSinceReturn(now),
            overheated = overheating(now)
        )
    }

    /**
     * Whether the last day of work looks like one that cost more than it gave.
     *
     * Three things have to agree, because any one of them alone is an ordinary
     * day: the day was well above the median size, and either its accuracy fell
     * away from the usual or its plan was abandoned. A big accurate day that
     * was finished is a good day and is left alone.
     *
     * The median is what makes this readable at all: one outlier is exactly
     * what a median does not move, so "far above the median" means the day
     * stood out rather than the habit having grown.
     *
     * This is deliberately blunt. The sharper signals -- where in the session
     * accuracy fell, how long the gaps between answers grew -- need a
     * per-answer record that does not exist yet, and inventing one badly is
     * worse than reading the three columns that are already trustworthy.
     */
    private suspend fun overheating(now: Long): Boolean {
        val days = statsDao.lastDays(config.activityWindowDays * 2)
            .filter { it.reviewsDone > 0 }
        if (days.size < 4) return false

        val counts = days.map { it.reviewsDone }.sorted()
        val median = counts[counts.size / 2]
        if (median <= 0) return false

        // Today is still being lived in, so it is not the day being judged.
        val today = dayKey(now)
        val last = days.firstOrNull { it.day != today } ?: return false
        if (last.reviewsDone <= median * config.overheatRatio) return false

        val usual = days.map { it.accuracy }.average()
        val sloppy = last.accuracy < usual - config.overheatAccuracyDrop
        return sloppy || !last.planCompleted
    }

    /**
     * Days since the first session after the last real absence, or null if the
     * history has no gap that long in it.
     *
     * Return mode is supposed to last a few days, and the only way to know it is
     * still running is to find where it started: the most recent gap in the
     * activity history that was long enough to count as being away. Reading it
     * from the history rather than storing a flag means it survives a restore
     * and cannot be left switched on by a crash.
     */
    private suspend fun daysSinceReturn(now: Long): Int? {
        // Newest first, and a yyyy-MM-dd key sorts the same way as the date.
        val active = statsDao.lastDays(RETURN_SCAN_DAYS)
            .filter { it.reviewsDone > 0 }
            .mapNotNull { runCatching { LocalDate.parse(it.day) }.getOrNull() }
        if (active.size < 2) return null

        val today = runCatching { LocalDate.parse(dayKey(now)) }.getOrNull() ?: return null
        for (i in 0 until active.size - 1) {
            val gap = ChronoUnit.DAYS.between(active[i + 1], active[i]).toInt()
            if (gap >= config.returnModeGapDays) {
                return ChronoUnit.DAYS.between(active[i], today).toInt().coerceAtLeast(0)
            }
        }
        return null
    }

    /**
     * Days since the first day with any activity. Zero on a fresh install, so a
     * new account is inside the settling window by definition.
     */
    private suspend fun daysSinceStart(now: Long): Int {
        val first = statsDao.firstDay() ?: return 0
        return runCatching {
            ChronoUnit.DAYS.between(LocalDate.parse(first), LocalDate.parse(dayKey(now))).toInt()
        }.getOrDefault(0)
    }

    /**
     * Nothing new between [GovernorConfig.nightCutoffHour] and the hour the
     * day rolls over.
     *
     * A chunk met once at midnight gets the worst possible first contact:
     * overnight consolidation is weaker here than it is for most people, so that
     * single late pass is largely wasted and the chunk comes back tomorrow as
     * something unfamiliar that already carries a schedule. Reviews are
     * untouched — only the introduction waits for the morning. A first ever
     * session is exempt: an empty first screen is worse than anything this rule
     * protects against.
     */
    private fun withNightRule(decision: GovernorDecision, now: Long): GovernorDecision {
        if (decision.allowedNew <= 0 || decision.reason == GovernorReason.FIRST_RUN) {
            return decision
        }
        if (!boundary.isNight(now, config.nightCutoffHour)) return decision
        return decision.copy(allowedNew = 0, reason = GovernorReason.LATE_NIGHT)
    }

    /**
     * Consecutive days whose plan was finished. Days, not rows: see
     * [CleanStreak], which is where the counting lives and is tested.
     */
    private suspend fun countCleanDays(now: Long): Int {
        val finished = statsDao.lastDays(CLEAN_DAY_SCAN)
            .associate { it.day to it.planCompleted }
        val days = (0 until CLEAN_DAY_SCAN).map { d -> dayKey(now - d * DAY_MS) }
        return CleanStreak.count(days, finished)
    }

    /**
     * Creates the cards the governor authorised and returns them.
     *
     * Deliberately does not touch the daily counter any more: a chunk counts as
     * introduced when it reaches the plan, not when a row is written. See
     * [ensureDailyPlanLocked].
     */
    private suspend fun introduce(
        count: Int,
        now: Long,
        packId: String? = null
    ): List<CardEntity> {
        val hidden = suppressedNow()
        val candidates = (
            if (packId == null) chunkDao.unintroducedByFrequency(count * 12)
            else chunkDao.unintroducedByFrequencyFor(packId, count * 12)
            ).filterNot { it.id in hidden }
        if (candidates.isEmpty()) return emptyList()

        val tokens = chunkDao.tokensFor(candidates.map { it.id }).groupBy { it.chunkId }
        val lemmas = tokens.values.flatten().map { it.lemma }
        val comps = components.componentsFor(lemmas)

        // A subject deck is taken strictly in the order it was written.
        //
        // Everything the selector does is a statement about language: how new the
        // words are, which weak components a phrase would repair, how common the
        // phrase is. None of that holds for a deck about neuroscience or a
        // programming language, where line forty may be meaningless before line
        // thirty-nine. There the author's order IS the curriculum, and it is
        // already recorded -- the importer writes each line's position into
        // freqRank.
        //
        // When both kinds of deck are active the day's new material is split, so
        // switching a subject deck on never silences a language deck or the
        // other way round.
        val subject = candidates.filter { it.lang == NO_LANG }.sortedBy { it.freqRank }
        val language = candidates.filterNot { it.lang == NO_LANG }
        val share = if (subject.isEmpty() || language.isEmpty()) count else (count + 1) / 2
        val fromSubject = subject.take(share.coerceAtMost(count))

        // A subject card carries no lexical prior: its sentence is a definition,
        // and "how many of these words are already known" says nothing useful
        // about how hard the concept will be. One unknown component, no head start.
        val subjectPrior = ComponentPrior(
            knownRatio = 0.0,
            unknownContentTokens = 1,
            weakLemmas = emptyList()
        )
        val chosen = selector.select(language, tokens, comps, now, count - fromSubject.size)
        val cards = fromSubject.map { chunk ->
            scheduler.introduce(chunk.id, level = 0, componentPrior = subjectPrior, now = now)
        } + chosen.map { sc ->
            scheduler.introduce(sc.chunk.id, level = 0, componentPrior = sc.prior, now = now)
        }
        cardDao.upsertAll(cards)
        return cards
    }

    /**
     * What is left of today's new-material budget.
     *
     * The governor decides `allowedNew` once a day and the plan stores it, so
     * this is that number minus everything already introduced today -- whether
     * it arrived as a fresh chunk this morning or as a promotion five minutes
     * ago. No plan row means no authorisation, which is zero rather than
     * "unlimited": that direction of doubt is the one that cannot hurt anyone.
     */
    private suspend fun newRoomToday(now: Long): Int {
        val day = dayKey(now)
        val allowed = planDao.plan(day)?.allowedNew ?: return 0
        val used = statsDao.day(day)?.newIntroduced ?: 0
        return (allowed - used).coerceAtLeast(0)
    }

    /** Returns the new-material budget a retracted promotion had spent. */
    private suspend fun uncountIntroduced(ts: Long) {
        val day = dayKey(ts)
        val stat = statsDao.day(day) ?: return
        statsDao.upsert(stat.copy(newIntroduced = (stat.newIntroduced - 1).coerceAtLeast(0)))
    }

    /** Records chunks that actually reached today's plan. */
    private suspend fun countIntroduced(now: Long, added: Int) {
        if (added <= 0) return
        val day = dayKey(now)
        val stat = statsDao.day(day)
        statsDao.upsert(
            (stat ?: DailyStatEntity(day, 0, 0, 0L, 1.0, false))
                .copy(newIntroduced = (stat?.newIntroduced ?: 0) + added)
        )
    }

    // ---- session ----------------------------------------------------------

    /**
     * Today's plan minus everything already answered today. Rebuilding this
     * after a rotation, a tab switch or a process death yields exactly the same
     * queue and the same count, which is what makes the counter trustworthy.
     */
    suspend fun buildSession(
        deckId: String? = null,
        now: Long = System.currentTimeMillis()
    ): SessionPlan {
        val plan = ensureDailyPlan(now)
        val answered = reviewDao.answeredKeysSince(startOfDay(now)).toSet()

        // The whole plan is materialised here, not just its pending part, because
        // a deck session has to know how much of its own share is already done —
        // and the deck of a card is only reachable through its chunk. The plan is
        // a few dozen keys at most, so this costs one query and no joins.
        // A plan decided this morning still names a card marked wrong this
        // evening. It is dropped while the session is being read rather than
        // by rewriting the day's row, so a correction can never collide with
        // an answer being written.
        val hiddenNow = suppressedNow()
        val all = builder().materialize(plan.ids)
            .filterNot { it.chunk.id in hiddenNow }
        val scope = if (deckId == null) all else all.filter { it.chunk.packId == deckId }
        val pending = scope.filterNot { it.card.key in answered }

        val storedReason = runCatching { GovernorReason.valueOf(plan.reason) }
            .getOrDefault(GovernorReason.OK)

        // A finished deck and a quiet day produce exactly the same empty plan,
        // and the governor cannot tell them apart: it rules on how much new
        // material is allowed, never on how much exists. So the one layer that
        // can count does it here. Without this the end of a deck was reported as
        // "everything is repeated, the next ones are not due yet" — a promise of
        // cards that are never coming.
        val reason = if (pending.isEmpty() && untouchedIn(deckId) == 0) {
            GovernorReason.PACK_EXHAUSTED
        } else {
            storedReason
        }

        return SessionPlan(
            cards = pending,
            plannedTotal = plan.plannedTotal,
            answeredToday = statsDao.day(plan.day)?.reviewsDone ?: 0,
            reason = reason,
            nextDueAt = cardDao.nextDueAt(now),
            deckId = deckId,
            deckTitle = deckId?.let { id -> chunkDao.pack(id)?.title ?: id },
            sessionTotal = scope.size,
            sessionDone = scope.size - pending.size
        )
    }

    /**
     * Chunks never met, in one deck or across every active deck.
     *
     * Zero does not mean the deck is over for good — its cards keep coming back
     * for review — it means there is nothing left in it to meet for a first time.
     */
    private suspend fun untouchedIn(deckId: String?): Int =
        if (deckId == null) chunkDao.untouchedCount() else chunkDao.untouchedCountFor(deckId)

    /**
     * What each deck still owes today.
     *
     * Read by the deck list, which is the first screen. A deck showing zero is
     * not a deck that failed — the day's capacity is one number for the whole
     * app and the plan spends it where the schedule points, so a quiet deck today
     * is simply a deck whose cards are not due today.
     */
    suspend fun remainingByDeck(now: Long = System.currentTimeMillis()): Map<String, Int> {
        val plan = ensureDailyPlan(now)
        val answered = reviewDao.answeredKeysSince(startOfDay(now)).toSet()
        return builder().materialize(plan.ids)
            .filterNot { it.card.key in answered }
            .groupingBy { it.chunk.packId }
            .eachCount()
    }

    /**
     * "Ещё немного". Adds cards that are already due to today's plan and
     * nothing else: no new chunks, so a good day today never inflates tomorrow.
     * Returns how many were actually added.
     */
    suspend fun addExtra(
        count: Int = 5,
        deckId: String? = null,
        now: Long = System.currentTimeMillis()
    ): Int = writeLock.withLock { addExtraLocked(count, deckId, now) }

    /**
     * Held under [writeLock] from the first read of the plan to the write that
     * replaces it.
     *
     * It used to run outside the lock, and it is a read-modify-write on the one
     * row that defines what the day owes: it reads `plannedIds`, appends to that
     * copy and writes the whole row back. An answer landing in between — the
     * button is on the session screen, so there is always one in flight — was
     * written to the same row by [answerLocked] and then overwritten by this
     * copy, which had been read before it. The day's plan silently reverted, the
     * counter went back up, and the card the user had just answered came back.
     *
     * [ensureDailyPlanLocked] rather than [ensureDailyPlan] because the lock is
     * not reentrant: asking for it twice on the same coroutine is a deadlock,
     * and a session screen that stops responding is worse than the bug above.
     */
    private suspend fun addExtraLocked(count: Int, deckId: String?, now: Long): Int {
        val plan = ensureDailyPlanLocked(now)
        val answered = reviewDao.answeredKeysSince(startOfDay(now))
        val exclude = (plan.ids + answered).distinct()

        // Inside a deck session the extra cards come from that deck, asked for
        // by the query itself. This used to over-ask by ten and filter the result
        // in Kotlin, which returned nothing whenever those candidates happened to
        // belong to other decks.
        val hidden = suppressedNow()
        val repeats = builder().pickExtra(exclude, count, now, deckId)
            .filterNot { it.chunkId in hidden }

        // A deck nothing has been learned from yet has no cards at all, so there
        // is nothing to repeat and this used to answer "nothing is due" - true,
        // and useless, because what such a deck has is new material and only the
        // governor could hand that out, once a day. A deck switched on this
        // evening was therefore switched on and empty, and the one button on the
        // screen could not help.
        //
        // So here, and nowhere else, new chunks are introduced outside the daily
        // budget: this is a deliberate press, not the app deciding. They are
        // still counted against the day, so tomorrow's measured capacity knows
        // what happened and the safety valve still sees it.
        val fresh =
            if (repeats.isNotEmpty()) emptyList()
            else introduce(count, now, deckId)
        if (fresh.isNotEmpty()) countIntroduced(now, fresh.size)

        val extra = (repeats + fresh).take(count)
        if (extra.isEmpty()) return 0

        planDao.upsert(
            plan.copy(
                plannedIds = (plan.ids + extra.map { it.key }).joinToString(","),
                plannedTotal = plan.plannedTotal + extra.size,
                extraRequested = plan.extraRequested + extra.size
            )
        )
        return extra.size
    }

    /**
     * The third answer: this card is wrong.
     *
     * Until now the only way to react to a false card was to rate it "did not
     * know", which is the worst possible outcome twice over. FSRS reads that as
     * a lapse and starts showing the card MORE often, so a hallucination earns
     * more of the learner's time than a true card does; and the day's accuracy
     * falls, which is a number the governor reads before deciding whether any new
     * material is allowed. One bad line in an imported deck could shut the door
     * on new cards for a week.
     *
     * So this writes no review, no rating and no statistic. Nothing about the day
     * changes except that the card leaves it: the log stays honest, the accuracy
     * stays untouched, and the schedule never sees the card again at any level,
     * because being false is a property of the fact and not of how it was asked.
     *
     * The rows in `cards` are deliberately left alone. Deleting them would throw
     * away the history of a card that may simply have been mistyped, and the
     * filter costs nothing; a chunk taken back out of the list becomes askable
     * again with its schedule intact.
     */
    /**
     * How many ways a chunk of this deck can be asked.
     *
     * Two on a subject deck: recognise the term, then recall it inside its own
     * definition. The third level asks for the phrase from its meaning, which is
     * a language exercise -- see [LevelPromotion].
     */
    private fun maxLevelFor(chunk: ChunkEntity): Int {
        val byShape = Shapes.maxLevel(Shapes.of(chunk))
        val byLanguage =
            if (chunk.lang == NO_LANG) Level.CLOZE.value else Level.PRODUCTION.value
        // The lower of the two wins. The deck sets one limit; the chunk sets
        // the other, because a bare word has no sentence to take a gap out of.
        // Promoting past either produces a question that cannot be answered,
        // and the miss is then recorded against the item.
        return minOf(byShape, byLanguage)
    }

    suspend fun markWrong(sessionCard: SessionCard, now: Long = System.currentTimeMillis()) =
        writeLock.withLock { markWrongLocked(sessionCard, now) }

    private suspend fun markWrongLocked(sessionCard: SessionCard, now: Long) {
        val chunkId = sessionCard.chunk.id
        onSuppress?.invoke(chunkId)

        val plan = planDao.plan(dayKey(now)) ?: return
        // Every level of the chunk leaves the plan, not just the one on screen.
        val kept = plan.ids.filterNot { it.substringBeforeLast(':') == chunkId }
        val removed = plan.ids.size - kept.size
        if (removed > 0) {
            planDao.upsert(
                plan.copy(
                    plannedIds = kept.joinToString(","),
                    plannedTotal = (plan.plannedTotal - removed).coerceAtLeast(0)
                )
            )
        }

        // A chunk met for the first time today and thrown away was never learned,
        // so the day's new-material budget is handed back and a real card can take
        // its place. A chunk from an earlier day keeps its history: those answers
        // happened, whatever the card turned out to be.
        val card = cardDao.card(chunkId, sessionCard.level.value)
        if (card != null && card.isNew && dayKey(card.introducedAt) == dayKey(now)) {
            uncountIntroduced(now)
        }
    }

    suspend fun answer(sessionCard: SessionCard, rating: Rating, durationMs: Long, now: Long) =
        writeLock.withLock { answerLocked(sessionCard, rating, durationMs, now) }

    private suspend fun answerLocked(
        sessionCard: SessionCard,
        rating: Rating,
        durationMs: Long,
        now: Long
    ) {
        // Read the row instead of trusting the copy the UI holds: a card that
        // was rated "again" earlier in the same session is shown again from an
        // in-memory copy, and that copy is stale.
        val before = cardDao.card(sessionCard.chunk.id, sessionCard.level.value) ?: sessionCard.card
        val result = scheduler.apply(before, rating, now)
        cardDao.upsert(result.card)

        reviewDao.insert(
            ReviewEntity(
                chunkId = sessionCard.chunk.id,
                level = sessionCard.level.value,
                ts = now,
                rating = rating.value,
                elapsedDays = result.elapsedDays,
                stabilityBefore = result.before.stability,
                stabilityAfter = result.after.stability,
                difficultyBefore = result.before.difficulty,
                difficultyAfter = result.after.difficulty,
                durationMs = durationMs,
                wasAmnesty = sessionCard.fromAmnesty,
                // Snapshot for undo. Restoring a saved state is the only honest
                // way back: FSRS is not invertible.
                prevStability = before.stability,
                prevDifficulty = before.difficulty,
                prevDueAt = before.dueAt,
                prevLastReviewAt = before.lastReviewAt,
                prevReps = before.reps,
                prevLapses = before.lapses,
                prevIsNew = before.isNew,
                prevInAmnesty = before.inAmnesty
            )
        )

        components.recordAnswer(sessionCard.chunk.id, rating.value, now)

        // Promote to the next presentation level once the item is solid, which
        // gives novelty without growing the queue -- but only inside the day's
        // new-material budget. A promoted level is a question the user has never
        // been asked, and the governor is the only thing allowed to decide how
        // many of those arrive in a day. See LevelPromotion.
        builder()
            .nextLevelFor(result.card, newRoomToday(now), maxLevelFor(sessionCard.chunk))
            ?.let { nextLevel ->
            if (cardDao.card(result.card.chunkId, nextLevel) == null) {
                cardDao.upsert(
                    result.card.copy(
                        level = nextLevel,
                        stability = result.card.stability * 0.4,
                        dueAt = now + DAY_MS,
                        reps = 0,
                        lapses = 0,
                        isNew = true,
                        lastReviewAt = null,
                        introducedAt = now
                    )
                )
                // Counted like the introduction it is. Otherwise the measured
                // norm and the safety valve both read the day as lighter than it
                // was, and tomorrow's capacity is computed from a day that did
                // not happen.
                countIntroduced(now, 1)
            }
        }

        bumpDailyStat(now, rating, durationMs)

        // Work past the day's obligation buys new material now rather than
        // tomorrow. See [earnNewIfDue].
        earnNewIfDue(now)
    }

    /**
     * Opens one more new chunk once the day's obligation has been paid for.
     *
     * The governor rules once, before the day's first answer, and on a day
     * whose queue already fills the capacity it correctly rules that there is
     * no room -- and then the user answers everything, faster than predicted,
     * and the app has nothing unfamiliar left to show them. That is the exact
     * shape of the treadmill this app was written against: the reward for
     * finishing was that finishing changed nothing.
     *
     * So the budget is re-read as the session goes: every
     * [GovernorConfig.earnedNewPerReviews] reviews past the obligation open one
     * chunk, up to the day's hard ceiling. The price is what the chunk will
     * cost over the coming week, so nothing is borrowed from tomorrow, and the
     * ceiling is what keeps a good evening from emptying the deck.
     *
     * Gates are not overridden. If the morning's reason was a state of the user
     * rather than a shortage of room -- a pile, a quiet week, poor accuracy, a
     * return, an overheated day -- nothing is earned, however much work is
     * done. Counted as extra rather than as plan, so finishing the day still
     * means what it meant when the day started.
     */
    private suspend fun earnNewIfDue(now: Long) {
        if (boundary.isNight(now, config.nightCutoffHour)) return

        val day = dayKey(now)
        val plan = planDao.plan(day) ?: return
        val reason = runCatching { GovernorReason.valueOf(plan.reason) }.getOrNull() ?: return
        if (reason !in EARNABLE_REASONS) return

        val stat = statsDao.day(day) ?: return
        val obligation = (plan.plannedTotal - plan.extraRequested)
            .coerceAtLeast(config.dailyMinimumCards)
        val beyond = stat.reviewsDone - obligation
        if (beyond <= 0) return

        val gov = governor()
        val ceiling = gov.dailyNewCeiling(plan.capacity)
        val target = (plan.allowedNew + gov.earnedNew(beyond)).coerceAtMost(ceiling)
        if (stat.newIntroduced >= target) return

        val fresh = introduce(1, now)
        if (fresh.isEmpty()) return
        countIntroduced(now, fresh.size)
        planDao.upsert(
            plan.copy(
                plannedIds = (plan.ids + fresh.map { it.key }).joinToString(","),
                plannedTotal = plan.plannedTotal + fresh.size,
                extraRequested = plan.extraRequested + fresh.size
            )
        )
    }

    /**
     * Takes back the most recent answer.
     *
     * The review log stays append-only: the retraction is an inserted row that
     * points at the answer it cancels, and every query that reads "real
     * answers" filters those out. The card is restored from the snapshot taken
     * when the answer was recorded.
     *
     * The word layer keeps its exposure on purpose. It is a smoothed estimate
     * where one extra observation is noise, and Settings can rebuild it exactly
     * from the log at any time.
     *
     * Returns the key of the restored card, or null if there was nothing that
     * could be undone (answers recorded before this version carry no snapshot).
     */
    suspend fun undoLast(now: Long = System.currentTimeMillis()): String? =
        writeLock.withLock { undoLastLocked(now) }

    private suspend fun undoLastLocked(now: Long): String? {
        val last = reviewDao.lastAnswer() ?: return null
        val stability = last.prevStability ?: return null
        val difficulty = last.prevDifficulty ?: return null
        val dueAt = last.prevDueAt ?: return null
        val reps = last.prevReps ?: return null
        val lapses = last.prevLapses ?: return null
        val wasNew = last.prevIsNew ?: return null

        val card = cardDao.card(last.chunkId, last.level) ?: return null

        // A snapshot written before a scheduler migration describes the old
        // algorithm's state. The immutable review row is still true history,
        // but restoring that snapshot would put one card back on FSRS-4.5.
        // A current state that does not equal the row's recorded after-state is
        // therefore an undo boundary. After one new FSRS-6 answer the values
        // match again, and ordinary multi-step undo continues to work.
        if (abs(card.stability - last.stabilityAfter) > STATE_EPSILON ||
            abs(card.difficulty - last.difficultyAfter) > STATE_EPSILON
        ) return null

        cardDao.upsert(
            card.copy(
                stability = stability,
                difficulty = difficulty,
                dueAt = dueAt,
                lastReviewAt = last.prevLastReviewAt,
                reps = reps,
                lapses = lapses,
                isNew = wasNew,
                inAmnesty = last.prevInAmnesty ?: card.inAmnesty
            )
        )

        // If that answer promoted the chunk to a new level, take the promotion
        // back too, but only while the new level is still untouched.
        val promotedLevel = last.level + 1
        cardDao.card(last.chunkId, promotedLevel)?.let { promoted ->
            if (promoted.isNew && promoted.reps == 0 && promoted.introducedAt == last.ts) {
                cardDao.delete(last.chunkId, promotedLevel)
                // Give the budget back with it, or a session of undone answers
                // would spend the day's new material without introducing any.
                uncountIntroduced(last.ts)
            }
        }

        reviewDao.insert(
            ReviewEntity(
                chunkId = last.chunkId,
                level = last.level,
                ts = now,
                rating = 0,
                elapsedDays = 0.0,
                stabilityBefore = last.stabilityAfter,
                stabilityAfter = last.stabilityBefore,
                difficultyBefore = last.difficultyAfter,
                difficultyAfter = last.difficultyBefore,
                durationMs = 0L,
                wasAmnesty = last.wasAmnesty,
                undoOf = last.id
            )
        )

        unbumpDailyStat(last)
        return last.chunkId + ":" + last.level
    }

    private suspend fun bumpDailyStat(now: Long, rating: Rating, durationMs: Long) {
        val day = dayKey(now)
        val prev = statsDao.day(day) ?: DailyStatEntity(day, 0, 0, 0L, 1.0, false)
        val done = prev.reviewsDone + 1
        val correct = prev.correctCount + if (rating.value >= 3) 1 else 0
        statsDao.upsert(
            prev.copy(
                reviewsDone = done,
                correctCount = correct,
                activeMs = prev.activeMs + durationMs,
                accuracy = correct.toDouble() / done,
                planCompleted = planCompleted(day, done)
            )
        )
    }

    private suspend fun unbumpDailyStat(review: ReviewEntity) {
        val day = dayKey(review.ts)
        val prev = statsDao.day(day) ?: return
        val done = (prev.reviewsDone - 1).coerceAtLeast(0)
        val correct = (prev.correctCount - if (review.rating >= 3) 1 else 0).coerceAtLeast(0)
        statsDao.upsert(
            prev.copy(
                reviewsDone = done,
                correctCount = correct,
                activeMs = (prev.activeMs - review.durationMs).coerceAtLeast(0L),
                accuracy = if (done == 0) 1.0 else (correct.toDouble() / done).coerceIn(0.0, 1.0),
                planCompleted = planCompleted(day, done)
            )
        )
    }

    /**
     * Did this day's plan actually get finished?
     *
     * This flag is not cosmetic: [countCleanDays] counts consecutive days where
     * it is true, and five of them make the governor raise the daily target. It
     * used to be set by `done >= dailyMinimumCards`, and dailyMinimumCards is 1
     * — so answering a single card, on a day the governor had planned forty,
     * counted as a clean day, and a week of one card an evening was rewarded
     * with a larger target. The plan the governor built is the obligation.
     *
     * Extras are subtracted out: "ещё немного" is voluntary, and pressing it
     * must not move the finish line further away. If there is no plan row for
     * the day (an old day whose row has been cleaned up, or a replay from the
     * log) the old minimum is the fallback.
     */
    private suspend fun planCompleted(day: String, done: Int): Boolean {
        val plan = planDao.plan(day)
        val obligation = plan?.let { it.plannedTotal - it.extraRequested } ?: 0
        return if (obligation > 0) done >= obligation else done >= config.dailyMinimumCards
    }

    private fun startOfDay(ts: Long): Long = boundary.startOfDay(ts)

    // ---- maintenance ------------------------------------------------------

    /**
     * Danger zone. Clears card schedules and every derived table, then lets the
     * next plan start from scratch. The review log is NOT touched: it is the
     * one thing in this database that cannot be regenerated, so "start over"
     * means forgetting the schedule, not the history.
     */
    suspend fun resetProgress() {
        cardDao.clear()
        components.clearAll()
        statsDao.clear()
        planDao.clear()
    }

    /** Drops today's plan so the next session rebuilds it. Used after imports. */
    suspend fun invalidatePlan() = planDao.clear()

    // ---- stats ------------------------------------------------------------

    fun last30Flow() = statsDao.last30Flow()

    /**
     * The only streak-like metric in the app: days with a session out of the
     * last 30. A single missed day cannot break it, so there is nothing to feel
     * guilty about and nothing to reset.
     */
    suspend fun activeDaysLast30(): Int = statsDao.lastDays(30).count { it.reviewsDone > 0 }

    suspend fun governorLog(limit: Int = 30) = governorDao.recent(limit)

    suspend fun forecast(days: Int, now: Long = System.currentTimeMillis()): List<Int> =
        (1..days).map { d -> cardDao.dueBetween(now + (d - 1) * DAY_MS, now + d * DAY_MS) }

    suspend fun answeredToday(now: Long = System.currentTimeMillis()): Int =
        statsDao.day(dayKey(now))?.reviewsDone ?: 0

    /**
     * How long one answer actually takes, so the queue can be shown in
     * minutes instead of card counts.
     *
     * Median rather than mean, and mis-swipes and put-the-phone-down pauses
     * are dropped, so one interrupted evening cannot inflate the estimate.
     * Null until there is enough history: an invented number is worse than
     * no number, because the whole point is that the estimate can be
     * trusted.
     *
     * The bounds started at 0.8s and one minute and threw away almost
     * everything: a phrase that is recognised on sight is answered faster than
     * that, and a phrase that is being thought about takes longer, so a real
     * session often left fewer than the eight samples the estimate needed and no
     * minutes were shown at all. What the bounds are for is discarding a
     * mis-swipe and a phone put face-down, and 0.4s to two minutes does that
     * without discarding the session.
     */
    suspend fun medianAnswerMs(): Long? {
        val samples = reviewDao.recentDurations(DURATION_SAMPLE)
            .filter { it in MIN_SANE_ANSWER_MS..MAX_SANE_ANSWER_MS }
        if (samples.size < DURATION_MIN_SAMPLES) return null
        val sorted = samples.sorted()
        return sorted[sorted.size / 2]
    }

    /**
     * The four measurements added to the statistics screen, in one read.
     *
     * Deliberately computed here rather than in SQL: the day starts at 04:00,
     * the hour of an answer depends on the phone's timezone, and a review has to
     * be told apart from a first contact by its snapshot. All three are things
     * SQLite would have to be taught and Kotlin already knows.
     */
    suspend fun statsDigest(now: Long = System.currentTimeMillis()): StatsDigest {
        val answers = reviewDao.since(now - STATS_WINDOW_DAYS * DAY_MS)

        // Only real reviews count towards retention. A first contact cannot be
        // "forgotten", so counting it would quietly inflate every figure here.
        // Rows written before the undo schema carry no snapshot and are skipped.
        val reviews = answers.filter { it.prevIsNew == false }
        val recalled = reviews.count { it.rating >= Rating.HARD.value }
        val retention =
            if (reviews.size >= RETENTION_MIN_SAMPLE) recalled.toDouble() / reviews.size else null

        val zone = ZoneId.systemDefault()
        val buckets = HashMap<Int, IntArray>()
        reviews.forEach { review ->
            val hour = Instant.ofEpochMilli(review.ts).atZone(zone).hour
            val slot = buckets.getOrPut(hour) { IntArray(2) }
            slot[0]++
            if (review.rating >= Rating.HARD.value) slot[1]++
        }
        val hours = buckets.entries
            .sortedBy { it.key }
            .map { (hour, slot) ->
                HourSlice(hour = hour, answers = slot[0], accuracy = slot[1].toDouble() / slot[0])
            }
        val bestHour = hours
            .filter { it.answers >= HOUR_MIN_SAMPLE }
            .maxWithOrNull(compareBy<HourSlice> { it.accuracy }.thenBy { it.answers })
            ?.hour

        // Minutes come from the daily counters rather than from the log: they
        // already exclude the gaps where the phone was put down mid-session.
        val todayMs = statsDao.day(dayKey(now))?.activeMs ?: 0L
        val weekMs = statsDao.lastDays(7).sumOf { it.activeMs }

        val leechCards = cardDao.leeches(LEECH_MIN_LAPSES, LEECH_LIMIT)
        val chunks = chunkDao.chunks(leechCards.map { it.chunkId }.distinct()).associateBy { it.id }
        val leeches = leechCards
            .mapNotNull { card ->
                val chunk = chunks[card.chunkId] ?: return@mapNotNull null
                LeechItem(
                    text = chunk.text,
                    translation = chunk.translation,
                    lapses = card.lapses
                )
            }
            // The same phrase can be a leech at two levels. It is one phrase to
            // the person reading the list.
            .distinctBy { it.text }

        return StatsDigest(
            retention = retention,
            retentionSample = reviews.size,
            minutesToday = (todayMs / 60_000L).toInt(),
            minutesLast7 = (weekMs / 60_000L).toInt(),
            medianSeconds = medianAnswerMs()?.let { ((it + 500L) / 1000L).toInt() },
            hours = hours,
            bestHour = bestHour,
            leeches = leeches
        )
    }

    private companion object {
        const val STATE_EPSILON = 1e-9
        /** How far back countCleanDays and daysSinceReturn look, in days. */
        const val CLEAN_DAY_SCAN = 30
        const val RETURN_SCAN_DAYS = 90

        const val AUTO_WINDOW_DAYS = 14
        const val AUTO_COLD_START = 25
        const val AUTO_MIN = 12
        const val AUTO_MAX = 80
        const val AUTO_HEADROOM = 1.15

        /** Sample size and sanity bounds for the time estimate. */
        const val DURATION_SAMPLE = 100
        const val DURATION_MIN_SAMPLES = 4
        const val MIN_SANE_ANSWER_MS = 400L
        const val MAX_SANE_ANSWER_MS = 120_000L

        /**
         * Sizes for the statistics screen.
         *
         * The two sample floors are the difference between a measurement and a
         * rumour: twenty reviews before a retention figure is shown at all, and
         * twelve answers inside one hour before that hour may compete for
         * "best". Four forgettings is where a phrase stops being hard and starts
         * being broken.
         */
        const val STATS_WINDOW_DAYS = 30L
        const val RETENTION_MIN_SAMPLE = 20
        const val HOUR_MIN_SAMPLE = 12
        const val LEECH_MIN_LAPSES = 4
        const val LEECH_LIMIT = 8
    }
}
