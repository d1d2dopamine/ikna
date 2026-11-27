package dev.ikna.domain.session

import dev.ikna.data.db.DailyPlanEntity
import dev.ikna.data.db.HistoryBoundaryEntity

/** Local presentation observation only, never an answer authorization or tamper proof. */
data class SessionObservation(
    val day: String,
    val dayStart: Long,
    val reviewHighWater: Long,
    val reviewCount: Long,
    val storedPlan: DailyPlanEntity?,
    val boundary: HistoryBoundaryEntity?,
    val configuredCutoff: Long?,
    val configuredToken: String?
)

/** One committed read. A missing plan requires an explicit learner action to build it. */
data class CommittedSessionSnapshot(
    val observation: SessionObservation,
    val plan: SessionPlan?,
    val answeredKeys: Set<String>,
    val requiredComplete: Boolean,
    val answeredToday: Int,
    val goalAnswersToday: Int
)
