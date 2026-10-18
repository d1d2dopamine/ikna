package dev.ikna.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout

/** A profile switch cannot close a database while its ordinary UI still issues queries.
 * The root composition acknowledges removal of that UI before the container closes it. */
class ProfileRestartGate {
    private val mutable = MutableStateFlow(false)
    val required = mutable.asStateFlow()
    private val complete = MutableStateFlow(false)
    val readyToClose = complete.asStateFlow()
    fun finish() { complete.value = true }
    private val removed = CompletableDeferred<Unit>()
    suspend fun awaitUiQuiescence() {
        mutable.value = true
        withTimeout(10_000) { removed.await() }
    }
    fun acknowledgeUiRemoved() { removed.complete(Unit) }
}
