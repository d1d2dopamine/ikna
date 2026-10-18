package dev.ikna.domain.session

/** Bounded answer duration; independent of event timestamps and grading latency. */
object ReviewDuration {
    const val MAX_MS = 120_000L

    fun normalize(durationMs: Long): Long = durationMs.coerceIn(0L, MAX_MS)

    /**
     * System.nanoTime marks share one process clock. Signed subtraction handles
     * clock wrap for intervals below 2^63 nanoseconds (about 292 years).
     * A negative injected interval is defensive zero, never negative active time.
     */
    fun elapsedMillis(startNanos: Long, endNanos: Long): Long =
        normalize((endNanos - startNanos) / 1_000_000L)
}
