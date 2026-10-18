package dev.ikna.ui.session

/** In-memory repeats may continue only while the global required plan is incomplete. */
internal object MainLessonQueue {
    data class Step<T>(val queue: List<T>, val index: Int) {
        val finished: Boolean get() = index >= queue.size
    }

    fun <T> afterAnswer(
        queue: List<T>, index: Int, repeatCurrent: Boolean,
        answeredKeys: Set<String>, requiredComplete: Boolean, key: (T) -> String
    ): Step<T> {
        require(index in queue.indices)
        val next = index + 1
        val expanded = if (repeatCurrent && !requiredComplete) queue + queue[index] else queue
        // Keep the answered prefix for the existing cursor. Only synthetic copies
        // of answered keys are removed; unanswered explicit extras stay queued.
        val remaining = expanded.drop(next).let { tail ->
            if (requiredComplete) tail.filterNot { key(it) in answeredKeys } else tail
        }
        return Step(expanded.take(next) + remaining, next)
    }
}
