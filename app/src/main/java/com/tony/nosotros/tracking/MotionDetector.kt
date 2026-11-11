package com.tony.nosotros.tracking

import com.tony.nosotros.data.Mode

/** Combines Android activity with repeated, accurate GPS evidence. Times are monotonic. */
internal class MotionDetector {
    var mode = Mode.UNKNOWN
        private set
    private var vehicleActivityAt = Long.MIN_VALUE
    private var fastAt = Long.MIN_VALUE
    private var lastFixAt = Long.MIN_VALUE
    private var candidate = Mode.UNKNOWN
    private var candidateSince = 0L
    private var hits = 0

    fun activity(value: Mode, now: Long): Mode {
        if (value == Mode.VEHICLE) vehicleActivityAt = now
        // A spurious foot/still event must not override a recent fast, accurate fix.
        if (value in listOf(Mode.WALK, Mode.STILL) && recent(fastAt, now, 30_000)) return mode
        mode = value
        reset()
        return mode
    }

    fun fix(now: Long, accuracy: Float, speed: Float?, displacement: Double?, interval: Long,
            uncertainty: Double): Mode {
        if (now <= lastFixAt) return mode
        val gap = if (lastFixAt == Long.MIN_VALUE) Long.MAX_VALUE else now - lastFixAt
        lastFixAt = now
        if (gap > 120_000) reset()
        if (!accuracy.isFinite() || accuracy !in 0f..35f) { reset(); return mode }
        val moved = displacement != null && displacement.isFinite() &&
            displacement > maxOf(12.0, uncertainty)
        val inferred = if (moved && interval in 5_000..120_000)
            ((displacement!! - uncertainty).coerceAtLeast(0.0) / (interval / 1000.0)).toFloat() else null
        val reliableSpeed = speed?.takeIf { it.isFinite() && it in 0f..90f } ?: inferred
        val next = when {
            reliableSpeed != null && reliableSpeed >= 8f -> Mode.VEHICLE
            // Speed alone is not enough: GPS drift indoors must not count as walking.
            reliableSpeed != null && reliableSpeed in 0.7f..2.8f && moved &&
                mode != Mode.BIKE && !recent(vehicleActivityAt, now, 120_000) -> Mode.WALK
            else -> { reset(); return mode }
        }
        if (next == Mode.VEHICLE) fastAt = now
        if (candidate != next) { candidate = next; candidateSince = now; hits = 1 } else hits++
        val requiredHits = if (next == Mode.VEHICLE) 2 else 3
        val requiredTime = if (next == Mode.VEHICLE) 5_000 else if (mode.isVehicle) 45_000 else 20_000
        if (hits >= requiredHits && now - candidateSince >= requiredTime) {
            mode = next
            reset()
        }
        return mode
    }

    private fun recent(at: Long, now: Long, duration: Long) = at != Long.MIN_VALUE && now - at in 0..duration
    private fun reset() { candidate = Mode.UNKNOWN; hits = 0 }
}
