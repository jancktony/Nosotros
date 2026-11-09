package com.tony.nosotros.util

import com.tony.nosotros.data.Member
import com.tony.nosotros.data.Mode
import com.tony.nosotros.data.Place
import com.tony.nosotros.data.Routing

/** Adivina a qué lugar va alguien y calcula cuánto le falta. */
object Eta {
    data class Result(val place: Place, val seconds: Double, val arriveAt: Long)

    /** El lugar guardado al que más se ha acercado desde que empezó el trayecto. */
    fun destination(m: Member, places: List<Place>, leftName: String?): Place? {
        val cands = places.filter { !it.suggested && it.name.isNotBlank() && it.name != leftName &&
            Geo.dist(m.lat, m.lng, it.lat, it.lng) > it.radius }
        if (cands.isEmpty()) return null
        val start = Geo.decode(m.route).firstOrNull()
        if (start != null && Geo.dist(start.lat, start.lng, m.lat, m.lng) > 250) {
            val best = cands.map { p ->
                val d0 = Geo.dist(start.lat, start.lng, p.lat, p.lng)
                p to (d0 - Geo.dist(m.lat, m.lng, p.lat, p.lng)) / d0.coerceAtLeast(1.0)
            }.maxBy { it.second }
            if (best.second > 0.12) return best.first
        }
        return fallback(cands, m.uid)
    }

    /** Sin rumbo claro todavía: su lugar más visitado. */
    fun fallback(places: List<Place>, uid: String): Place? =
        places.filter { it.owner == uid }.maxByOrNull { it.visits } ?: places.maxByOrNull { it.visits }

    suspend fun compute(from: Pt, to: Place, mode: Mode): Result? {
        val r = Routing.route(from, Pt(to.lat, to.lng), foot = mode == Mode.WALK)
        var secs = r?.seconds ?: (Geo.dist(from.lat, from.lng, to.lat, to.lng) * 1.3 / (if (mode == Mode.WALK) 1.3 else 8.0))
        if (mode == Mode.BUS) secs *= 1.4
        if (mode == Mode.BIKE && r != null) secs *= 2.0
        return Result(to, secs, System.currentTimeMillis() + (secs * 1000).toLong())
    }

    fun mins(seconds: Double): String {
        val m = (seconds / 60).toInt().coerceAtLeast(1)
        return if (m < 60) "$m min" else "${m / 60} h ${m % 60} min"
    }
}
