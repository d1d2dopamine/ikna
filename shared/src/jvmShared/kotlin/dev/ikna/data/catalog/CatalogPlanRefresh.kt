package dev.ikna.data.catalog

import kotlinx.coroutines.CancellationException

/** Refreshing the plan is separate from the already committed installation. */
suspend fun refreshCatalogStudyPlan(refresh: suspend () -> Unit): Boolean = try {
    refresh()
    true
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    false
}
