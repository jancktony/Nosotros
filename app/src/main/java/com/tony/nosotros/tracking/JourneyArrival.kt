package com.tony.nosotros.tracking

/** Requires accurate repeated fixes for 20 seconds, never an ETA or a single GPS jump. */
class JourneyArrival {
    private var since: Long? = null
    fun fix(elapsed: Long, distance: Double, accuracy: Float): Boolean {
        if(!distance.isFinite() || distance>40 || !accuracy.isFinite() || accuracy !in 1f..35f){since=null;return false}
        val start=since ?: elapsed.also { since=it }
        return elapsed-start>=20_000
    }
    fun reset(){since=null}
}
