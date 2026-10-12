package dev.ikna.data.repo

import dev.ikna.data.db.ReviewEntity
import java.time.Instant
import java.time.ZoneId
import kotlin.test.*

class StudyStatsTest {
    private fun row(id: Long, target: String = "dev-target", rating: Int = 3,
        ts: Long = id, previousNew: Boolean? = false, level: Int = 0, undoOf: Long? = null) =
        ReviewEntity(id, target, level, ts, rating, 1.0, 1.0, 2.0, 5.0, 5.0, 1000, false,
            prevIsNew = previousNew, undoOf = undoOf)

    @Test fun countsTargetsByIdentityAndKeepsUnknownSeparate() {
        val facts = studyEvidence(listOf(
            row(1, "dev-en"), row(2, "dev-en", previousNew = true),
            row(3, "dev-de", previousNew = null), row(4, "dev-de", rating = 1)
        ), 0, 10)
        assertEquals(2, facts.targets)
        assertEquals(4, facts.answers)
        assertEquals(2, facts.repeats)
        assertEquals(1, facts.recalled)
        assertEquals(1, facts.unclassified)
    }

    @Test fun recoveryRequiresTwoLaterRecallAnswersAfterLatestMiss() {
        assertTrue(studyEvidence(listOf(row(1, rating = 1), row(2)), 0, 10).recovered.isEmpty())
        val facts = studyEvidence(listOf(row(1, rating = 1), row(2), row(3, rating = 2)), 0, 10)
        assertEquals(2, facts.recovered.single().laterRecalls)
        assertTrue(studyEvidence(listOf(row(1, rating = 1), row(2), row(3), row(4, rating = 1)), 0, 10)
            .recovered.isEmpty())
    }

    @Test fun attentionNeedsRepeatedMissesAndLatestReviewMiss() {
        assertTrue(studyEvidence(listOf(row(1, rating = 1)), 0, 10).attention.isEmpty())
        assertEquals(1, studyEvidence(listOf(row(1, rating = 1), row(2, rating = 1)), 0, 10).attention.size)
        assertTrue(studyEvidence(listOf(row(1, rating = 1), row(2, rating = 1), row(3)), 0, 10).attention.isEmpty())
    }

    @Test fun firstContactsUnknownAndOtherQuestionLevelsCannotCreateRecovery() {
        val rows = listOf(row(1, rating = 1), row(2, previousNew = true), row(3, previousNew = null),
            row(4, level = 1), row(5, level = 2))
        assertTrue(studyEvidence(rows, 0, 10).recovered.isEmpty())
        assertTrue(studyEvidence(rows, 0, 10).attention.isEmpty())
    }

    @Test fun anUnknownStatusMissInterruptsTheRecoverySequence() {
        val rows = listOf(row(1, rating = 1), row(2), row(3, rating = 1, previousNew = null), row(4))
        assertTrue(studyEvidence(rows, 0, 10).recovered.isEmpty())
        assertTrue(studyEvidence(rows + row(5), 0, 10).recovered.isEmpty())
    }

    @Test fun boundsAndRetractionsAndInvalidRatingsAreNotAnswers() {
        val facts = studyEvidence(listOf(row(1, ts = 9), row(2, ts = 10), row(3, ts = 20),
            row(4, ts = 21), row(5, ts = 15, undoOf = 1), row(6, ts = 15, rating = 0)), 10, 20)
        assertEquals(2, facts.answers)
        assertTrue(studyEvidence(emptyList(), 10, 20).recovered.isEmpty())
    }

    @Test fun orderingIsDeterministicWhenAnswerTimesMatch() {
        val rows = listOf(row(3, ts = 5), row(1, ts = 5, rating = 1), row(2, ts = 5))
        assertEquals(studyEvidence(rows, 0, 10), studyEvidence(rows.reversed(), 0, 10))
        assertEquals(1, studyEvidence(rows, 0, 10).recovered.size)
    }

    @Test fun studyDaysUseLocalFourAmIncludingDstAndBeforeBoundary() {
        val zone = ZoneId.of("Europe/Berlin")
        assertEquals(Instant.parse("2026-03-23T03:00:00Z").toEpochMilli(),
            studyWindowStart(Instant.parse("2026-03-29T12:00:00Z").toEpochMilli(), 7, zone, 4))
        assertEquals(Instant.parse("2026-10-19T02:00:00Z").toEpochMilli(),
            studyWindowStart(Instant.parse("2026-10-25T12:00:00Z").toEpochMilli(), 7, zone, 4))
        assertEquals(Instant.parse("2026-10-11T04:00:00Z").toEpochMilli(),
            studyWindowStart(Instant.parse("2026-10-12T03:59:00Z").toEpochMilli(), 1, ZoneId.of("UTC"), 4))
    }
}
