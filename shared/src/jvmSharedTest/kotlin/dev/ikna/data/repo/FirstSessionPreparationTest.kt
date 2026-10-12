package dev.ikna.data.repo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirstSessionPreparationTest {
    @Test
    fun completionFollowsBothPrerequisites() = runBlocking {
        val calls = mutableListOf<String>()
        prepareFirstSession(
            installPacks = { calls += "install" },
            preparePlan = { calls += "plan" },
            markComplete = { calls += "complete" }
        )
        assertEquals(listOf("install", "plan", "complete"), calls)
    }

    @Test
    fun failedInstallDoesNotBuildPlanOrComplete() = runBlocking {
        var planned = false
        var completed = false
        assertFailsWith<IllegalStateException> {
            prepareFirstSession(
                installPacks = { error("unavailable pack") },
                preparePlan = { planned = true },
                markComplete = { completed = true }
            )
        }
        assertFalse(planned)
        assertFalse(completed)
    }

    @Test
    fun failedPlanCanRetryWithoutPrematureCompletion() = runBlocking {
        val installed = linkedSetOf<String>()
        var planAttempts = 0
        var completions = 0
        suspend fun attempt() = prepareFirstSession(
            installPacks = { installed += "starter" },
            preparePlan = { if (++planAttempts == 1) error("temporary failure") },
            markComplete = { completions++ }
        )
        assertFailsWith<IllegalStateException> { attempt() }
        assertEquals(0, completions)
        attempt()
        assertEquals(setOf("starter"), installed)
        assertEquals(2, planAttempts)
        assertEquals(1, completions)
    }

    @Test
    fun cancellationDoesNotCompleteFirstRun() = runBlocking {
        var completed = false
        assertFailsWith<CancellationException> {
            prepareFirstSession(
                installPacks = {},
                preparePlan = { throw CancellationException("screen closed") },
                markComplete = { completed = true }
            )
        }
        assertFalse(completed)
    }

    @Test
    fun failedCommitRemainsVisibleToCaller() = runBlocking {
        var planPrepared = false
        assertFailsWith<IllegalStateException> {
            prepareFirstSession(
                installPacks = {},
                preparePlan = { planPrepared = true },
                markComplete = { error("settings unavailable") }
            )
        }
        assertTrue(planPrepared)
    }
}
