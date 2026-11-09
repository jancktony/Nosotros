package com.tony.nosotros.tracking

import com.tony.nosotros.data.Place
import com.tony.nosotros.data.Prefs
import com.tony.nosotros.util.Geo
import com.tony.nosotros.util.TimeFmt

/**
 * Decide cuándo se LLEGA o se SALE de un lugar y detecta lugares frecuentes.
 *
 * Para evitar falsos avisos (pasar en carro frente a la casa), una llegada
 * necesita 2 lecturas dentro del radio a baja velocidad, o que la persona
 * se quede quieta, o la confirmación de la geocerca de Android.
 * Una salida necesita 2 lecturas claramente fuera.
 */
class PlaceEngine(
    private val prefs: Prefs,
    private val onArrive: (Place) -> Unit,
    private val onLeave: (Place) -> Unit,
    private val onSuggest: (lat: Double, lng: Double) -> Unit,
) {
    /** Lugares con nombre de ESTA persona. */
    var places: List<Place> = emptyList()
    /** Todos los lugares (incluidas sugerencias) para no sugerir repetidos. */
    var allPlaces: List<Place> = emptyList()

    private var pendingId: String? = null
    private var pendingHits = 0
    private var outsideHits = 0

    val current: Place? get() = places.firstOrNull { it.id == prefs.currentPlaceId }

    /** [settled] = la persona está quieta: con una sola lectura dentro basta para llegar. */
    fun onLocation(lat: Double, lng: Double, acc: Float, speedMs: Float, settled: Boolean = false) {
        if (acc > 150f) return
        if (prefs.currentPlaceId != null && current == null && places.isNotEmpty()) prefs.currentPlaceId = null

        val cur = current
        if (cur != null) {
            val d = Geo.dist(cur.lat, cur.lng, lat, lng)
            if (d > cur.radius + 40 + minOf(acc, 80f) / 2) {
                outsideHits++
                if (outsideHits >= 2) leave(cur)
            } else outsideHits = 0
            return
        }

        val hit = places
            .map { it to Geo.dist(it.lat, it.lng, lat, lng) }
            .filter { (p, d) -> d <= p.radius + minOf(acc, 75f) }
            .minByOrNull { it.second }?.first
        if (hit != null && speedMs < 7f) {
            if (pendingId == hit.id) pendingHits++ else { pendingId = hit.id; pendingHits = 1 }
            if (pendingHits >= 2 || settled || speedMs < 0.8f && acc < 40f) arrive(hit)
        } else {
            pendingId = null; pendingHits = 0
        }
    }

    /** La persona se quedó quieta: si estaba entrando a un lugar, confirmar llegada. */
    fun onStill() {
        val p = places.firstOrNull { it.id == pendingId } ?: return
        if (current == null) arrive(p)
    }

    fun onGeofence(placeId: String, enter: Boolean) {
        val p = places.firstOrNull { it.id == placeId } ?: return
        if (enter && current == null) arrive(p)
        if (!enter && current?.id == placeId) leave(p)
    }

    private fun arrive(p: Place) {
        prefs.currentPlaceId = p.id
        pendingId = null; pendingHits = 0; outsideHits = 0
        onArrive(p)
    }

    private fun leave(p: Place) {
        prefs.currentPlaceId = null
        outsideHits = 0
        onLeave(p)
    }

    /** Se terminó una estancia (la persona empezó a moverse de nuevo). */
    fun onStayEnded(start: Long, end: Long, lat: Double, lng: Double) {
        if (end - start < 12 * 60_000L) return
        val longStay = end - start >= 3 * 3_600_000L   // 3 h o más: sugerir de una vez (casa, trabajo)
        if (allPlaces.any { Geo.dist(it.lat, it.lng, lat, lng) < it.radius + 50 }) return
        val today = TimeFmt.dayKey(end)
        val list = prefs.candidates.toMutableList()
        val i = list.indexOfFirst { Geo.dist(it.lat, it.lng, lat, lng) < 120 }
        if (i >= 0) {
            val c = list[i]
            if (c.lastDay != today) {
                val updated = c.copy(
                    lat = (c.lat * c.count + lat) / (c.count + 1),
                    lng = (c.lng * c.count + lng) / (c.count + 1),
                    count = c.count + 1, lastDay = today,
                )
                list[i] = updated
                if ((updated.count >= 2 || longStay) && !updated.suggested) {
                    list[i] = updated.copy(suggested = true)
                    onSuggest(updated.lat, updated.lng)
                }
            }
        } else {
            list.add(Prefs.Candidate(lat, lng, 1, today, longStay))
            if (longStay) onSuggest(lat, lng)
        }
        prefs.candidates = list
    }
}
