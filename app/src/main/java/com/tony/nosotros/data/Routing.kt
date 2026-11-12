package com.tony.nosotros.data

import com.tony.nosotros.util.Geo
import com.tony.nosotros.util.Pt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Búsqueda de direcciones (Nominatim) y rutas (OSRM), todo gratis con OpenStreetMap. */
object Routing {
    data class Hit(val name: String, val lat: Double, val lng: Double)
    data class Step(val text: String, val distanceM: Double)
    data class Route(val points: List<Pt>, val distanceM: Double, val seconds: Double, val steps: List<Step>)

    private fun get(url: String): String = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).run {
        setRequestProperty("User-Agent", "NosotrosApp/1.0")
        connectTimeout = 12000; readTimeout = 15000
        try { inputStream.bufferedReader().use { it.readText() } } finally { disconnect() }
    }

    /** Busca una dirección o lugar. [near] mejora el orden por cercanía. */
    suspend fun search(query: String, near: Pt?): List<Hit> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            val q = URLEncoder.encode(query, "UTF-8")
            val vb = near?.let {
                val d = 0.5
                "&viewbox=${it.lng - d},${it.lat + d},${it.lng + d},${it.lat - d}&bounded=0"
            } ?: ""
            val json = JSONArray(get("https://nominatim.openstreetmap.org/search?q=$q&format=json&limit=6&accept-language=es&countrycodes=co$vb"))
            (0 until json.length()).map {
                val o = json.getJSONObject(it)
                Hit(o.getString("display_name"), o.getString("lat").toDouble(), o.getString("lon").toDouble())
            }
        }.getOrDefault(emptyList())
    }

    /** Ruta entre dos puntos. [foot] = a pie (prioriza calles peatonales). */
    suspend fun route(from: Pt, to: Pt, foot: Boolean): Route? = withContext(Dispatchers.IO) {
        val path = "${from.lng},${from.lat};${to.lng},${to.lat}?overview=full&geometries=polyline&steps=true&alternatives=3"
        val urls = if (foot) listOf(
            "https://routing.openstreetmap.de/routed-foot/route/v1/foot/$path",
        ) else listOf(
            "https://router.project-osrm.org/route/v1/driving/$path",
            "https://routing.openstreetmap.de/routed-car/route/v1/driving/$path",
        )
        urls.firstNotNullOfOrNull { url -> runCatching { parse(JSONObject(get(url)), foot) }.getOrNull() }
    }

    private fun parse(o: JSONObject, foot: Boolean): Route? {
        if (o.getString("code") != "Ok") return null
        val arr = o.getJSONArray("routes")
        val all = (0 until arr.length()).map { arr.getJSONObject(it) }
        if (all.isEmpty()) return null
        // A pie: entre las alternativas, la que más va por calles con nombre (más transitadas),
        // siempre que no alargue el camino más de un 30 %.
        val shortest = all.minOf { it.getDouble("distance") }
        val r = if (!foot) all[0] else all.filter { it.getDouble("distance") <= shortest * 1.3 }.maxBy { namedShare(it) }
        val steps = ArrayList<Step>()
        forEachStep(r) { s -> steps.add(Step(describe(s.getJSONObject("maneuver"), s.optString("name")), s.getDouble("distance"))) }
        return Route(Geo.decode(r.getString("geometry")), r.getDouble("distance"), r.getDouble("duration"), steps)
    }

    private fun forEachStep(r: JSONObject, f: (JSONObject) -> Unit) {
        val legs = r.getJSONArray("legs")
        for (i in 0 until legs.length()) {
            val st = legs.getJSONObject(i).getJSONArray("steps")
            for (j in 0 until st.length()) f(st.getJSONObject(j))
        }
    }

    private fun namedShare(r: JSONObject): Double {
        var named = 0.0
        forEachStep(r) { if (it.optString("name").isNotBlank()) named += it.getDouble("distance") }
        return named / r.getDouble("distance").coerceAtLeast(1.0)
    }

    private fun describe(man: JSONObject, road: String): String {
        val type = man.optString("type")
        val mod = man.optString("modifier")
        val calle = if (road.isNotBlank()) " por $road" else ""
        val giro = when (mod) {
            "left", "slight left", "sharp left" -> "Gira a la izquierda"
            "right", "slight right", "sharp right" -> "Gira a la derecha"
            "straight" -> "Sigue derecho"
            "uturn" -> "Haz un giro en U"
            else -> "Continúa"
        }
        return when (type) {
            "depart" -> "Empieza$calle"
            "arrive" -> "Llegaste a tu destino"
            "roundabout", "rotary" -> "Toma la rotonda"
            "fork" -> "$giro en la bifurcación$calle"
            "end of road" -> "$giro al final de la vía$calle"
            else -> "$giro$calle"
        }
    }
}
