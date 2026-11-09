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
        inputStream.bufferedReader().use { it.readText() }
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
        runCatching {
            val profile = if (foot) "foot" else "driving"
            val url = "https://router.project-osrm.org/route/v1/$profile/" +
                "${from.lng},${from.lat};${to.lng},${to.lat}?overview=full&geometries=polyline&steps=true&annotations=false"
            val o = JSONObject(get(url))
            if (o.getString("code") != "Ok") return@runCatching null
            val r = o.getJSONArray("routes").getJSONObject(0)
            val pts = Geo.decode(r.getString("geometry"))
            val steps = ArrayList<Step>()
            val legs = r.getJSONArray("legs")
            for (i in 0 until legs.length()) {
                val st = legs.getJSONObject(i).getJSONArray("steps")
                for (j in 0 until st.length()) {
                    val s = st.getJSONObject(j)
                    val man = s.getJSONObject("maneuver")
                    steps.add(Step(describe(man, s.optString("name")), s.getDouble("distance")))
                }
            }
            Route(pts, r.getDouble("distance"), r.getDouble("duration"), steps)
        }.getOrNull()
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
