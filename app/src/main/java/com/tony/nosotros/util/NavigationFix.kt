package com.tony.nosotros.util

/** A heartbeat or app activity must never refresh the age of a GPS fix. */
object NavigationFix {
    fun fresh(locationAt: Long, accuracy: Float, paused: Boolean, now: Long, maxAge: Long): Boolean =
        !paused && locationAt > 0 && now - locationAt in 0..maxAge &&
            accuracy.isFinite() && accuracy > 0f && accuracy <= 80f
}
