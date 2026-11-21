package com.tony.nosotros.voice

/** Back off while a person is waiting; do not give up after a transient outage. */
object WalkieRetry {
    fun delayMillis(attempt: Int): Long = when {
        attempt <= 1 -> 2_000L
        attempt == 2 -> 4_000L
        attempt == 3 -> 8_000L
        attempt == 4 -> 16_000L
        else -> 30_000L
    }
}
