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
    data class Hit(val name: String, val lat: Double, val lng: Double, val detail: String = "")
    data class Step(val text: String, val distanceM: Double)
    data class Route(val points: List<Pt>, val distanceM: Double, val seconds: Double, val steps: List<Step>)

    enum class SearchFailure { NONE, NOT_FOUND, UNAVAILABLE }
    data class SearchResult(val hits: List<Hit>, val failure: SearchFailure = SearchFailure.NONE)
    private val searchLock = kotlinx.coroutines.sync.Mutex()
    private var lastNominatim = 0L

    private fun get(url: String): String = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).run {
        setRequestProperty("User-Agent", "NosotrosApp/1.13 (github.com/jancktony/Nosotros)")
        connectTimeout = 7000; readTimeout = 8000
        try { inputStream.bufferedReader().use { it.readText() } } finally { disconnect() }
    }

    /** Bounded fallback search; no apartment data, no autocomplete or silent network errors. */
    suspend fun search(query: String, near: Pt?): SearchResult = withContext(Dispatchers.IO) {
        var unavailable=false
        for(candidate in AddressSearch.candidates(query,near)) {
            val encoded=URLEncoder.encode(candidate.text,"UTF-8")
            val view=near?.let { "&viewbox=${it.lng-.5},${it.lat+.5},${it.lng+.5},${it.lat-.5}&bounded=0" }.orEmpty()
            for(provider in 0..1) {
                try {
                    val hits=if(provider==0) {
                        searchLock.lock()
                        val json=try {
                            val wait=1100-(System.nanoTime()/1_000_000-lastNominatim)
                            if(wait>0)kotlinx.coroutines.delay(wait)
                            lastNominatim=System.nanoTime()/1_000_000
                            JSONArray(get("https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=6&addressdetails=1&accept-language=es&countrycodes=co$view"))
                        } finally { searchLock.unlock() }
                        (0 until json.length()).map { i ->
                            val o=json.getJSONObject(i)
                            val house=o.optJSONObject("address")?.optString("house_number").orEmpty()
                            Hit(o.getString("display_name"),o.getString("lat").toDouble(),o.getString("lon").toDouble(),
                                if(candidate.approximate||house.isBlank()&&candidate.text.contains("#"))"Ubicación aproximada; número sin confirmar" else "Coincidencia del mapa")
                        }
                    } else {
                        // Photon supports only a limited language set; lang=es returns HTTP 400.
                        val proximity=near?.let { "&lat=${it.lat}&lon=${it.lng}" }.orEmpty()
                        val json=JSONObject(get("https://photon.komoot.io/api/?q=$encoded&limit=6$proximity")).getJSONArray("features")
                        (0 until json.length()).mapNotNull { i ->
                            val feature=json.getJSONObject(i);val p=feature.getJSONObject("properties")
                            if(!p.optString("countrycode").equals("CO",true))null else {
                                val coordinates=feature.getJSONObject("geometry").getJSONArray("coordinates")
                                val label=listOf(p.optString("name"),p.optString("street"),p.optString("housenumber"),p.optString("city"),p.optString("state"))
                                    .filter { it.isNotBlank() }.distinct().joinToString(", ")
                                Hit(label,coordinates.getDouble(1),coordinates.getDouble(0),if(candidate.approximate||p.optString("housenumber").isBlank())"Ubicación aproximada; confirma el punto" else "Coincidencia del mapa")
                            }
                        }
                    }
                    if(hits.isNotEmpty())return@withContext SearchResult(if(near==null)hits else hits.sortedBy { Geo.dist(near.lat,near.lng,it.lat,it.lng) })
                } catch(e:kotlinx.coroutines.CancellationException){throw e}
                  catch(_:Exception){unavailable=true}
            }
        }
        SearchResult(emptyList(),if(unavailable)SearchFailure.UNAVAILABLE else SearchFailure.NOT_FOUND)
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
