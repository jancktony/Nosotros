package com.tony.nosotros.util

data class PairFix(val uid: String, val lat: Double, val lng: Double, val accuracy: Float,
                   val updatedAt: Long, val paused: Boolean)

/** Uses already-shared fixes only; never requests GPS or writes a proximity history. */
class PairProximity {
    private var members = emptyList<String>()
    private var enteredAt: Long? = null
    private var joined = false

    fun update(fixes: List<PairFix>, now: Long, elapsed: Long): Boolean {
        val ids = fixes.map { it.uid }.sorted()
        if (ids != members) { reset(); members = ids }
        if (!usable(fixes, now)) { reset(); return false }
        val a = fixes[0]; val b = fixes[1]
        val distance = Geo.dist(a.lat, a.lng, b.lat, b.lng)
        if (distance > if (joined) 70.0 else 40.0) { reset(); return false }
        if (!joined) {
            val start = enteredAt ?: elapsed.also { enteredAt = it }
            joined = elapsed - start >= 10_000L
        }
        return joined
    }

    fun reset() { joined = false; enteredAt = null }

    companion object {
        fun usable(fixes: List<PairFix>, now: Long): Boolean =
            fixes.size == 2 && fixes.map { it.uid }.distinct().size == 2 && fixes.none {
                it.uid.isBlank() || it.paused || it.updatedAt <= 0 || now - it.updatedAt !in -30_000L..360_000L ||
                !it.lat.isFinite() || !it.lng.isFinite() || it.lat !in -90.0..90.0 || it.lng !in -180.0..180.0 ||
                !it.accuracy.isFinite() || it.accuracy <= 0f || it.accuracy > 35f
            }

        fun distanceText(together: Boolean, meters: Double?): String = when {
            together -> "Juntos"
            meters == null -> "Por confirmar"
            meters <= 40.0 -> "Confirmando"
            else -> "≈ ${TimeFmt.km(meters)}"
        }

        fun center(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Pt {
            val a = Math.toRadians(aLng); val b = Math.toRadians(bLng)
            return Pt((aLat + bLat) / 2,
                Math.toDegrees(kotlin.math.atan2(kotlin.math.sin(a) + kotlin.math.sin(b),
                    kotlin.math.cos(a) + kotlin.math.cos(b))))
        }
    }
}
