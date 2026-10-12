package dev.ikna.ui.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A failed read is not an empty plan. Cancellation belongs to the caller. */
suspend fun <T> cardLoadResult(read: suspend () -> T): Result<T> = try {
    val value = read()
    currentCoroutineContext().ensureActive()
    Result.success(value)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    currentCoroutineContext().ensureActive()
    Result.failure(failure)
}

/** Claimed synchronously on the UI thread, before launching a load coroutine. */
class CardLoadGate {
    private var active = false
    fun tryStart(): Boolean {
        if (active) return false
        active = true
        return true
    }
    fun finish() { active = false }
}
