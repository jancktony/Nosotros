package com.tony.nosotros.voice

/** Server time advances with elapsed time; changing a phone's date cannot mute incoming voice. */
class VoiceClock(private val elapsed: () -> Long) {
    @Volatile private var serverAt = 0L
    private var elapsedAt = 0L
    val ready get() = serverAt > 0
    @Synchronized fun synchronize(serverMillis: Long) {
        require(serverMillis > 0)
        serverAt = serverMillis; elapsedAt = elapsed()
    }
    @Synchronized fun now(): Long {
        check(ready)
        return serverAt + (elapsed() - elapsedAt).coerceAtLeast(0)
    }
    fun fresh(timestamp: Long, maxAgeMillis: Long): Boolean =
        ready && timestamp > 0 && now() - timestamp in -5_000L..maxAgeMillis
}
