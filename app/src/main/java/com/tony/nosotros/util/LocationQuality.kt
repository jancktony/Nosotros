package com.tony.nosotros.util

data class MeasuredFix(val lat: Double, val lng: Double, val accuracy: Float, val elapsedMs: Long)

/** Retains an actual measurement, never a place centre or a fabricated averaged fix. */
object LocationQuality {
    fun shouldUse(next: MeasuredFix, previous: MeasuredFix?, nowElapsed: Long): Boolean {
        if (!next.lat.isFinite() || !next.lng.isFinite() || next.lat !in -90.0..90.0 || next.lng !in -180.0..180.0 ||
            !next.accuracy.isFinite() || next.accuracy <= 0f || next.accuracy > 150f || next.elapsedMs <= 0 ||
            nowElapsed - next.elapsedMs !in 0..30_000L) return false
        if (previous == null) return true
        if (next.elapsedMs <= previous.elapsedMs) return false
        val previousAge = nowElapsed - previous.elapsedMs
        val degraded = next.accuracy > maxOf(previous.accuracy * 1.5f, previous.accuracy + 5f)
        if (previousAge in 0..60_000L && previous.accuracy in 0.01f..35f && degraded) {
            val displacement = Geo.dist(previous.lat, previous.lng, next.lat, next.lng)
            if (displacement <= previous.accuracy + next.accuracy) return false
        }
        return true
    }
}
