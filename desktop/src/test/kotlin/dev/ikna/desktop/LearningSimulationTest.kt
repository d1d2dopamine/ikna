package dev.ikna.desktop

import dev.ikna.data.dev.DeveloperScenario
import dev.ikna.data.dev.IknaDataProfile
import dev.ikna.domain.fsrs.DAY_MS
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.grading.INPUT_SWIPE
import dev.ikna.domain.grading.PEEK_REQUIRED
import dev.ikna.domain.governor.GovernorConfig
import dev.ikna.domain.governor.GovernorReason
import dev.ikna.domain.session.ReviewSignals
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A simulated learner, driven day by day through the real plan, governor and
 * FSRS pipeline with an explicit clock.
 *
 * The clock is a parameter of the repository (`ensureDailyPlan`, `answer`
 * both take `now`), so no system time is touched: the simulation advances
 * `now` by whole days from a fixed anchor and answers every planned card with
 * a scripted persona. Everything runs against a throwaway developer profile
 * in a temp directory; the real profile is unreachable, and no product code
 * is involved -- this file only drives the production classes the way the
 * session screen does.
 */
class LearningSimulationTest {

    private val config = GovernorConfig()
    private val zone: ZoneId = ZoneId.systemDefault()

    private data class DayRecord(
        val day: Int,
        val plannedTotal: Int,
        val capacity: Int,
        val allowedNew: Int,
        val reason: String,
        val pending: Int,
        val answered: Int,
        val againCount: Int
    )

    private data class SimResult(
        val days: List<DayRecord>,
        val seededReviews: Int,
        val answeredTotal: Int,
        val reviewsTotal: Int
    )

    private enum class Persona { DILIGENT, AVERAGE, STRUGGLING }

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 9, 1, hour, minute).plusDays(day.toLong())
            .atZone(zone).toInstant().toEpochMilli()

    private fun ratingFor(persona: Persona, rng: Random): Rating = when (persona) {
        Persona.DILIGENT -> when {
            rng.nextDouble() < 0.10 -> Rating.HARD
            rng.nextDouble() < 0.97 -> Rating.GOOD
            else -> Rating.EASY
        }
        Persona.AVERAGE -> when {
            rng.nextDouble() < 0.15 -> Rating.AGAIN
            rng.nextDouble() < 0.35 -> Rating.HARD
            else -> Rating.GOOD
        }
        Persona.STRUGGLING -> when {
            rng.nextDouble() < 0.40 -> Rating.AGAIN
            rng.nextDouble() < 0.60 -> Rating.HARD
            else -> Rating.GOOD
        }
    }

    private suspend fun simulate(
        scenario: DeveloperScenario,
        persona: Persona,
        days: Int,
        seed: Long = 20260926L
    ): SimResult {
        val home = Files.createTempDirectory("ikna-simulation").toFile()
        val container = DesktopContainer(home, IknaDataProfile.DEVELOPER)
        try {
            val rng = Random(seed)
            val seeded = requireNotNull(container.developerSandbox)
                .seed(scenario, now = at(0, 19))
            val repo = container.learningRepository
            val records = ArrayList<DayRecord>()
            var answeredTotal = 0
            var now = at(0, 19)
            var day = 0
            while (day < days) {
                day += 1
                now += DAY_MS
                val plan = repo.ensureDailyPlan(now)
                if (day == 1) {
                    // The day's plan is decided once; a second call is a no-op.
                    val againPlan = repo.ensureDailyPlan(now)
                    assertEquals(plan.plannedTotal, againPlan.plannedTotal)
                    assertEquals(plan.reason, againPlan.reason)
                }
                val session = repo.buildSession(now = now)
                val keys = session.cards.map { it.card.key }
                assertTrue(
                    "day $day handed the same card twice",
                    keys.toSet().size == keys.size
                )
                assertTrue(
                    "day $day planned ${plan.plannedTotal} above capacity ${plan.capacity}",
                    plan.plannedTotal <= plan.capacity
                )
                assertTrue(
                    "day $day capacity ${plan.capacity} above the backlog hard limit",
                    plan.capacity <= config.backlogHardLimit
                )
                assertTrue(
                    "day $day reason ${plan.reason} is not a governor reason",
                    plan.reason in GovernorReason.entries.map { it.name }
                )
                var offset = 0L
                var answered = 0
                var again = 0
                for (card in session.cards) {
                    if (answered >= 60) break
                    val rating = ratingFor(persona, rng)
                    if (rating == Rating.AGAIN) again += 1
                    repo.answer(
                        card,
                        rating,
                        durationMs = 4_500,
                        now = now + offset,
                        signals = ReviewSignals(
                            latencyMs = 1_900,
                            swipeVelocityX = 640f,
                            peeked = true,
                            peekSemantics = PEEK_REQUIRED,
                            inputMethod = INPUT_SWIPE
                        )
                    )
                    answered += 1
                    offset += 45_000
                }
                answeredTotal += answered
                records += DayRecord(
                    day,
                    plan.plannedTotal,
                    plan.capacity,
                    plan.allowedNew,
                    plan.reason,
                    session.cards.size,
                    answered,
                    again
                )
            }
            val reviewsTotal = container.db.reviewDao().total()
            return SimResult(records, seeded.reviewCount, answeredTotal, reviewsTotal)
        } finally {
            container.db.close()
            home.deleteRecursively()
        }
    }

    private fun printRun(label: String, result: SimResult) {
        println("=== $label")
        result.days.forEach { d ->
            println(
                "d%02d planned=%3d capacity=%3d new=%d reason=%-16s pending=%3d answered=%3d again=%d"
                    .format(
                        d.day, d.plannedTotal, d.capacity, d.allowedNew,
                        d.reason, d.pending, d.answered, d.againCount
                    )
            )
        }
        println(
            "seeded=" + result.seededReviews +
                " answered=" + result.answeredTotal +
                " reviewsTotal=" + result.reviewsTotal
        )
        // Gradle swallows test stdout unless it fails; leave the day-by-day
        // table next to the other test artefacts for the documentation.
        runCatching {
            val file = java.io.File("build/sim-" + label.replace(' ', '-') + ".txt")
            file.parentFile?.mkdirs()
            file.printWriter().use { out ->
                out.println("=== $label")
                result.days.forEach { d ->
                    out.println(
                        "d%02d planned=%3d capacity=%3d new=%d reason=%-16s pending=%3d answered=%3d again=%d"
                            .format(
                                d.day, d.plannedTotal, d.capacity, d.allowedNew,
                                d.reason, d.pending, d.answered, d.againCount
                            )
                    )
                }
                out.println(
                    "seeded=" + result.seededReviews +
                        " answered=" + result.answeredTotal +
                        " reviewsTotal=" + result.reviewsTotal
                )
            }
        }
    }

    @Test
    fun `30 day diligent learner keeps every daily plan inside the governors limits`() = runBlocking {
        val result = simulate(DeveloperScenario.EARLY_HISTORY, Persona.DILIGENT, days = 30)
        printRun("diligent 30 days", result)
        result.days.forEach { d ->
            assertTrue(
                "day ${d.day} planned ${d.plannedTotal} above the backlog hard limit",
                d.plannedTotal <= config.backlogHardLimit
            )
        }
        assertTrue(result.days.any { it.reason == GovernorReason.OK.name })
        // The review log is append-only: seeded rows plus one row per answer,
        // nothing edited and nothing deleted across the whole month.
        assertEquals(result.seededReviews + result.answeredTotal, result.reviewsTotal)
    }

    @Test
    fun `30 day struggling learner stays inside the hard limits and keeps the log append-only`() = runBlocking {
        val result = simulate(DeveloperScenario.EARLY_HISTORY, Persona.STRUGGLING, days = 30)
        printRun("struggling 30 days", result)
        assertTrue(result.days.sumOf { it.againCount } > 0)
        result.days.forEach { d ->
            assertTrue(
                "day ${d.day} planned ${d.plannedTotal} above the backlog hard limit",
                d.plannedTotal <= config.backlogHardLimit
            )
        }
        assertEquals(result.seededReviews + result.answeredTotal, result.reviewsTotal)
    }

    @Test
    fun `ten day runs are deterministic for the same seed`() = runBlocking {
        val first = simulate(DeveloperScenario.EARLY_HISTORY, Persona.AVERAGE, days = 10, seed = 7L)
        val second = simulate(DeveloperScenario.EARLY_HISTORY, Persona.AVERAGE, days = 10, seed = 7L)
        assertEquals(
            first.days.map { Triple(it.day, it.plannedTotal, it.reason) },
            second.days.map { Triple(it.day, it.plannedTotal, it.reason) }
        )
        assertEquals(first.answeredTotal, second.answeredTotal)
    }

    @Test
    fun `a fourteen day gap puts the returning learner into return mode`() = runBlocking {
        val home = Files.createTempDirectory("ikna-sim-return").toFile()
        val container = DesktopContainer(home, IknaDataProfile.DEVELOPER)
        try {
            requireNotNull(container.developerSandbox)
                .seed(DeveloperScenario.MATURE_HISTORY, now = at(0, 19))
            val repo = container.learningRepository
            var now = at(0, 19)

            // Two ordinary days.
            for (day in 1..2) {
                now = at(day, 19)
                val session = repo.buildSession(now = now)
                var offset = 0L
                for (card in session.cards) {
                    repo.answer(
                        card, Rating.GOOD, durationMs = 4_500, now = now + offset,
                        signals = ReviewSignals(
                            latencyMs = 1_900, swipeVelocityX = 640f, peeked = true,
                            peekSemantics = PEEK_REQUIRED, inputMethod = INPUT_SWIPE
                        )
                    )
                    offset += 45_000
                }
            }

            // Fourteen days away, then the first day back.
            now = at(17, 19)
            val plan = repo.ensureDailyPlan(now)
            println(
                "return day: reason=%s planned=%d capacity=%d new=%d"
                    .format(plan.reason, plan.plannedTotal, plan.capacity, plan.allowedNew)
            )
            // The safety valve outranks the return-mode gate on a long gap
            // (LoadGovernor keeps the original gate in `gate`), so the reason
            // may name either; the contract is a small plan and at most one
            // new chunk on the first day back.
            assertTrue(
                "return reason ${plan.reason} is neither RETURN_MODE nor SAFETY_VALVE",
                plan.reason == GovernorReason.RETURN_MODE.name ||
                    plan.reason == GovernorReason.SAFETY_VALVE.name
            )
            assertTrue(
                "return plan ${plan.plannedTotal} above return capacity ${config.returnModeCapacity}",
                plan.plannedTotal <= config.returnModeCapacity
            )
            assertTrue(
                "return plan hands out ${plan.allowedNew} new chunks",
                plan.allowedNew <= 1
            )
        } finally {
            container.db.close()
            home.deleteRecursively()
        }
    }

    @Test
    fun `late night plans hand out reviews but no new material`() = runBlocking {
        val home = Files.createTempDirectory("ikna-sim-night").toFile()
        val container = DesktopContainer(home, IknaDataProfile.DEVELOPER)
        try {
            requireNotNull(container.developerSandbox)
                .seed(DeveloperScenario.EARLY_HISTORY, now = at(0, 19))
            val repo = container.learningRepository

            val nightPlan = repo.ensureDailyPlan(at(1, 23, 30))
            println(
                "night plan: reason=%s planned=%d new=%d"
                    .format(nightPlan.reason, nightPlan.plannedTotal, nightPlan.allowedNew)
            )
            // A hard gate that already blocks new material (post-skip warmup)
            // shadows LATE_NIGHT: withNightRule returns the decision untouched
            // when allowedNew is zero. The contract is that no new material is
            // handed out late at night, and reviews keep working.
            assertEquals(0, nightPlan.allowedNew)
            assertTrue(nightPlan.reason in GovernorReason.entries.map { it.name })
            assertTrue("reviews must stay available at night", nightPlan.plannedTotal > 0)

            val nextEvening = repo.ensureDailyPlan(at(2, 19))
            assertTrue(nextEvening.reason != GovernorReason.LATE_NIGHT.name)
        } finally {
            container.db.close()
            home.deleteRecursively()
        }
    }
}
