package dev.ikna.data.repo

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * The first-run flag is a commit marker, not an acknowledgement of a tap.
 * Both front doors prepare the same prerequisites before saving it. Exceptions,
 * including coroutine cancellation, propagate so the screen can retry safely.
 * The install and plan callbacks must retain their existing idempotent contract.
 */
suspend fun prepareFirstSession(
    installPacks: suspend () -> Unit,
    preparePlan: suspend () -> Unit,
    markComplete: suspend () -> Unit
) {
    installPacks()
    preparePlan()
    currentCoroutineContext().ensureActive()
    markComplete()
}
