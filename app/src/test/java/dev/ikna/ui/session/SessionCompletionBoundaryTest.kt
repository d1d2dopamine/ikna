package dev.ikna.ui.session

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Structural Android wiring evidence; repository and queue arithmetic have separate runtime tests. */
class SessionCompletionBoundaryTest {
    @Test fun `ordinary answer checks durable global required completion before showing repeats`() {
        val source = listOf(File("app/src/main/java/dev/ikna/ui/session/SessionViewModel.kt"),
            File("src/main/java/dev/ikna/ui/session/SessionViewModel.kt"))
            .first { it.isFile }.readText()
        val ordinaryCommit = source.substringAfter("repo.answer(card, rating, duration, now, signals)")
            .substringBefore("fun undo()")
        assertTrue("Ordinary Android answers currently append AGAIN or first-contact repeats without a durable global required-plan completion check",
            ordinaryCommit.contains("repo.requiredPlanComplete("))
    }
    @Test fun `all AGAIN required keys finish exactly once global required plan is complete`() {
        var queue = listOf("a", "b", "c")
        var index = 0
        val answered = mutableSetOf<String>()
        repeat(3) {
            answered += queue[index]
            val step = MainLessonQueue.afterAnswer(queue, index, true, answered,
                requiredComplete = answered.size == 3, key = { it })
            queue = step.queue; index = step.index
        }
        assertEquals(3, index)
        assertEquals(3, queue.size)
    }

    @Test fun `mixed first contact and AGAIN repeats are deferred at global boundary`() {
        val first = MainLessonQueue.afterAnswer(listOf("new", "again", "known"), 0, true,
            setOf("new"), false) { it }
        val second = MainLessonQueue.afterAnswer(first.queue, first.index, true,
            setOf("new", "again"), false) { it }
        val last = MainLessonQueue.afterAnswer(second.queue, second.index, false,
            setOf("new", "again", "known"), true) { it }
        assertTrue(last.finished)
        assertEquals(listOf("new", "again", "known"), last.queue)
    }

    @Test fun `one card norm does not automatically repeat AGAIN or first contact`() {
        for (repeat in listOf(false, true)) {
            val step = MainLessonQueue.afterAnswer(listOf("a"), 0, repeat, setOf("a"), true) { it }
            assertTrue(step.finished)
            assertEquals(listOf("a"), step.queue)
        }
    }

    @Test fun `filtered deck completion does not imply global completion`() {
        val step = MainLessonQueue.afterAnswer(listOf("deck-a"), 0, true,
            setOf("deck-a"), requiredComplete = false) { it }
        assertFalse(step.finished)
        assertEquals(listOf("deck-a", "deck-a"), step.queue)
    }

    @Test fun `global completion preserves unanswered opted in extras while dropping answered copies`() {
        val step = MainLessonQueue.afterAnswer(listOf("required", "extra-one", "required", "extra-two"),
            0, true, setOf("required"), true) { it }
        assertEquals(listOf("required", "extra-one", "extra-two"), step.queue)
        assertFalse(step.finished)
    }

    @Test fun `Undo and restart rebuilt pending queues remain answerable`() {
        // Repository reload supplies only pending persisted keys. It owns the
        // Undo/global-completion result, which this pure decision consumes.
        val reopened = MainLessonQueue.afterAnswer(listOf("undone", "still-required"), 0,
            true, setOf("undone"), false) { it }
        assertFalse(reopened.finished)
        val completed = MainLessonQueue.afterAnswer(reopened.queue, reopened.index, false,
            setOf("undone", "still-required"), true) { it }
        assertTrue(completed.finished)
        val restartedExtra = MainLessonQueue.afterAnswer(listOf("persisted-extra"), 0,
            true, setOf("persisted-extra"), true) { it }
        assertTrue(restartedExtra.finished)
    }

    @Test fun `durable guarded transition uses production helper and superseded failures cannot replace reload`() {
        val source = listOf(File("app/src/main/java/dev/ikna/ui/session/SessionViewModel.kt"),
            File("src/main/java/dev/ikna/ui/session/SessionViewModel.kt"))
            .first { it.isFile }.readText()
        val rate = source.substringAfter("fun rate(").substringBefore("fun undo()")
        val durable = rate.indexOf("repo.answer(card, rating, duration, now, signals)")
        val decision = rate.indexOf("MainLessonQueue.afterAnswer")
        assertTrue(durable >= 0 && decision > durable)
        assertTrue(rate.substring(durable, decision).contains("repo.requiredPlanComplete(now)"))
        assertTrue(rate.substring(decision).contains("if (version != presentationVersion) return@serially"))
        assertTrue(source.contains("if (version != presentationVersion) return@onFailure"))
        assertTrue(source.contains("presentation.invalidate()"))
    }

}
