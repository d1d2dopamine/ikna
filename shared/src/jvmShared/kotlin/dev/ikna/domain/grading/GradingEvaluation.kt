package dev.ikna.domain.grading

import dev.ikna.data.db.CardEntity
import dev.ikna.data.db.ReviewEntity
import dev.ikna.data.export.ReviewRecord
import dev.ikna.data.repo.observations
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.fsrs.Scheduler
import kotlin.math.ln

/** Paired prequential scores: both models predict the SAME next observed outcome. */
data class GradingEvaluation(
    val sourceKind: String,
    val activeAnswers: Int,
    val scoredAnswers: Int,
    val heldOutAnswers: Int,
    val binaryBrier: Double?,
    val derivedBrier: Double?,
    val binaryLogLoss: Double?,
    val derivedLogLoss: Double?,
    val heldOutBinaryBrier: Double?,
    val heldOutDerivedBrier: Double?,
    val derivedHard: Int,
    val derivedEasy: Int,
    val warmupFallbacks: Int,
    val discardedTimings: Int,
    val candidatesDiffer: Boolean
) {
    /** Never authorize a rollout from synthetic rows, a tiny log or mere ties. */
    val warrantsRealWorldReview: Boolean get() = sourceKind == "real" && heldOutAnswers >= 100 &&
        candidatesDiffer && heldOutDerivedBrier != null && heldOutBinaryBrier != null &&
        heldOutDerivedBrier < heldOutBinaryBrier

    fun asText(): String = buildString {
        appendLine("source=$sourceKind")
        appendLine("method=paired prequential; fixed observed timestamps; no counterfactual scheduling claim")
        appendLine("heldout=last 25% of active chronological answers; online calibration uses past only")
        appendLine("activeAnswers=$activeAnswers")
        appendLine("scoredAnswers=$scoredAnswers")
        appendLine("heldOutAnswers=$heldOutAnswers")
        appendLine("binaryBrier=$binaryBrier")
        appendLine("derivedBrier=$derivedBrier")
        appendLine("binaryLogLoss=$binaryLogLoss")
        appendLine("derivedLogLoss=$derivedLogLoss")
        appendLine("heldOutBinaryBrier=$heldOutBinaryBrier")
        appendLine("heldOutDerivedBrier=$heldOutDerivedBrier")
        appendLine("derivedHard=$derivedHard")
        appendLine("derivedEasy=$derivedEasy")
        appendLine("warmupFallbacks=$warmupFallbacks")
        appendLine("discardedTimings=$discardedTimings")
        appendLine("warrantsRealWorldReview=$warrantsRealWorldReview")
        appendLine("automaticEnable=false")
        appendLine("warning=Synthetic improvement validates plumbing, NOT benefit to a real learner.")
        appendLine("warning=Even real-log improvement is observational and needs per-person review, not automatic rollout.")
    }
}

/** Runs entirely locally. No file path, socket, telemetry or account in this API. */
object GradingEvaluator {
    fun evaluate(records: List<ReviewRecord>, scheduler: Scheduler = Scheduler()): GradingEvaluation {
        require(records.all { it.synthetic } || records.none { it.synthetic }) {
            "Do not mix synthetic and real histories"
        }
        val synthetic = records.isNotEmpty() && records.all { it.synthetic }
        val undone = records.mapNotNull { it.undoOf }.toSet()
        // Export identity is independent of database row ids. Keep the first
        // occurrence exactly as restore does. Retractions are never outcomes.
        val answers = records.distinctBy { it.signature }
            .filter { it.undoOf == null && it.id !in undone && Rating.ofOrNull(it.inputRating ?: it.rating) != null }
            .sortedWith(compareBy<ReviewRecord> { it.ts }.thenBy { it.id })
        val binary = HashMap<String, CardEntity>()
        val derived = HashMap<String, CardEntity>()
        val window = TimingWindow()
        val brierA = ArrayList<Double>()
        val brierB = ArrayList<Double>()
        val lossA = ArrayList<Double>()
        val lossB = ArrayList<Double>()
        val heldA = ArrayList<Double>()
        val heldB = ArrayList<Double>()
        var hard = 0
        var easy = 0
        var warm = 0
        var discarded = 0
        val heldStart = (answers.size * 0.75).toInt()
        for ((index, record) in answers.withIndex()) {
            val row = record.toEntity(id = record.id)
            val key = row.chunkId + ":" + row.level
            val input = Rating.of(row.inputRating ?: row.rating)
            // Outcome: any reported recall (including explicit manual HARD) is
            // recalled; no grading decision is allowed to define its own label.
            val observed = if (input == Rating.AGAIN) 0.0 else 1.0
            val binaryGrade = if (observed == 0.0) Rating.AGAIN else Rating.GOOD
            val previousA = binary[key]
            val previousB = derived[key]
            if (previousA != null && previousB != null) {
                val a = scheduler.predictRecall(previousA, row.ts).coerceIn(1e-12, 1.0 - 1e-12)
                val b = scheduler.predictRecall(previousB, row.ts).coerceIn(1e-12, 1.0 - 1e-12)
                brierA += (a - observed) * (a - observed)
                brierB += (b - observed) * (b - observed)
                lossA += -(observed * ln(a) + (1.0 - observed) * ln(1.0 - a))
                lossB += -(observed * ln(b) + (1.0 - observed) * ln(1.0 - b))
                if (index >= heldStart) {
                    heldA += brierA.last()
                    heldB += brierB.last()
                }
            }
            val initial = seed(row)
            val derivedBefore = previousB ?: initial
            val decision = DerivedGrading.decide(
                binaryGrade, row.observations(), row.level, row.presentationLength, window,
                enabled = true,
                easyEligible = !derivedBefore.isNew &&
                    derivedBefore.reps - derivedBefore.lapses >= EASY_MIN_PRIOR_SUCCESSES
            )
            binary[key] = scheduler.apply(previousA ?: initial, binaryGrade, row.ts).card
            derived[key] = if (decision.rating == Rating.HARD || decision.rating == Rating.EASY) {
                scheduler.applyDerivedV1(previousB ?: initial, decision.rating, row.ts).card
            } else scheduler.apply(previousB ?: initial, decision.rating, row.ts).card
            if (decision.rating == Rating.HARD) hard++
            if (decision.rating == Rating.EASY) easy++
            if (decision.reason == "warmup") warm++
            if (decision.timingDiscardReason != null) discarded++
            decision.acceptedSample?.let { window.add(it) }
        }
        fun mean(values: List<Double>): Double? = values.takeIf { it.isNotEmpty() }?.average()
        return GradingEvaluation(
            if (synthetic) "synthetic" else "real", answers.size, brierA.size, heldA.size,
            mean(brierA), mean(brierB), mean(lossA), mean(lossB), mean(heldA), mean(heldB),
            hard, easy, warm, discarded, hard + easy > 0
        )
    }

    private fun seed(row: ReviewEntity) = CardEntity(
        chunkId = row.chunkId,
        level = row.level,
        stability = row.prevStability ?: row.stabilityBefore,
        difficulty = row.prevDifficulty ?: row.difficultyBefore,
        dueAt = row.prevDueAt ?: row.ts,
        lastReviewAt = row.prevLastReviewAt,
        introducedAt = row.ts,
        reps = row.prevReps ?: 0,
        lapses = row.prevLapses ?: 0,
        inAmnesty = false,
        isNew = row.prevIsNew ?: true
    )
}
