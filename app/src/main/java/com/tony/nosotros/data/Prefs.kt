package com.tony.nosotros.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Ajustes y estado local guardado en el teléfono. */
class Prefs(ctx: Context) {
    private val p: SharedPreferences = ctx.getSharedPreferences("nosotros", Context.MODE_PRIVATE)

    var pairId: String?
        get() = p.getString("pairId", null)
        set(v) = p.edit().putString("pairId", v).apply()
    var myName: String
        get() = p.getString("myName", "") ?: ""
        set(v) = p.edit().putString("myName", v).apply()
    /** Vehículo habitual: CAR o MOTO. Se usa para clasificar "en vehículo". */
    var vehicle: Mode
        get() = Mode.of(p.getString("vehicle", Mode.CAR.name))
        set(v) = p.edit().putString("vehicle", v.name).apply()
    var autoUpdate: Boolean
        get() = p.getBoolean("autoUpdate", true)
        set(v) = p.edit().putBoolean("autoUpdate", v).apply()
    /** Última versión que se intentó instalar sola (para no repetir la descarga). */
    var autoUpdateTried: String
        get() = p.getString("autoUpdateTried", "") ?: ""
        set(v) = p.edit().putString("autoUpdateTried", v).apply()
    var notifyArrive: Boolean
        get() = p.getBoolean("nArrive", true)
        set(v) = p.edit().putBoolean("nArrive", v).apply()
    var notifyLeave: Boolean
        get() = p.getBoolean("nLeave", true)
        set(v) = p.edit().putBoolean("nLeave", v).apply()
    var notifyPause: Boolean
        get() = p.getBoolean("nPause", true)
        set(v) = p.edit().putBoolean("nPause", v).apply()
    var extremeSaving: Boolean
        get() = p.getBoolean("extreme", true)
        set(v) = p.edit().putBoolean("extreme", v).apply()
    var lastEventSeen: Long
        get() = p.getLong("lastEventSeen", 0L)
        set(v) = p.edit().putLong("lastEventSeen", v).apply()
    var currentPlaceId: String?
        get() = p.getString("curPlace", null)
        set(v) = p.edit().putString("curPlace", v).apply()
    var currentVisitId: String?
        get() = p.getString("curVisit", null)
        set(v) = p.edit().putString("curVisit", v).apply()
    var stillSince: Long
        get() = p.getLong("stillSince", 0L)
        set(v) = p.edit().putLong("stillSince", v).apply()
    var theme: String
        get() = p.getString("theme", "night") ?: "night"
        set(v) = p.edit().putString("theme", v).apply()
    var mapSatellite: Boolean
        get() = p.getBoolean("sat", false)
        set(v) = p.edit().putBoolean("sat", v).apply()

    var map3d: Boolean
        get() = p.getBoolean("map3d", false)
        set(v) = p.edit().putBoolean("map3d", v).apply()
    var myAvatar: String
        get() = p.getString("avatar", "") ?: ""
        set(v) = p.edit().putString("avatar", v).apply()
    var lockDetails: Boolean
        get() = p.getBoolean("lockDetails", false)
        set(v) = p.edit().putBoolean("lockDetails", v).apply()
    var partnerPaused: Boolean
        get() = p.getBoolean("partnerPaused", false)
        set(v) = p.edit().putBoolean("partnerPaused", v).apply()

    // ---- Datos para los widgets (los escribe el servicio) ----
    var since: Long
        get() = p.getLong("since", 0L)
        set(v) = p.edit().putLong("since", v).apply()
    var partnerName: String
        get() = p.getString("pName", "") ?: ""
        set(v) = p.edit().putString("pName", v).apply()
    var partnerLat: Double
        get() = java.lang.Double.longBitsToDouble(p.getLong("pLat", 0L))
        set(v) = p.edit().putLong("pLat", java.lang.Double.doubleToRawLongBits(v)).apply()
    var partnerLng: Double
        get() = java.lang.Double.longBitsToDouble(p.getLong("pLng", 0L))
        set(v) = p.edit().putLong("pLng", java.lang.Double.doubleToRawLongBits(v)).apply()
    var partnerUpdated: Long
        get() = p.getLong("pUpd", 0L)
        set(v) = p.edit().putLong("pUpd", v).apply()
    var partnerMode: String
        get() = p.getString("pMode", "") ?: ""
        set(v) = p.edit().putString("pMode", v).apply()
    var partnerPlace: String?
        get() = p.getString("pPlace", null)
        set(v) = p.edit().putString("pPlace", v).apply()
    var myLat: Double
        get() = java.lang.Double.longBitsToDouble(p.getLong("mLat", 0L))
        set(v) = p.edit().putLong("mLat", java.lang.Double.doubleToRawLongBits(v)).apply()
    var myLng: Double
        get() = java.lang.Double.longBitsToDouble(p.getLong("mLng", 0L))
        set(v) = p.edit().putLong("mLng", java.lang.Double.doubleToRawLongBits(v)).apply()

    var annivNotifiedDay: String
        get() = p.getString("annivDay", "") ?: ""
        set(v) = p.edit().putString("annivDay", v).apply()
    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) = p.edit().putBoolean("onboarded", v).apply()
    var wasPaused: Boolean
        get() = p.getBoolean("wasPaused", false)
        set(v) = p.edit().putBoolean("wasPaused", v).apply()

    /** Sitios donde la persona se ha quedado ≥15 min (para sugerir lugares). */
    data class Candidate(val lat: Double, val lng: Double, val count: Int, val lastDay: String, val suggested: Boolean)

    var candidates: List<Candidate>
        get() {
            val arr = JSONArray(p.getString("candidates", "[]"))
            return (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Candidate(o.getDouble("lat"), o.getDouble("lng"), o.getInt("count"),
                    o.optString("day"), o.optBoolean("sug"))
            }
        }
        set(v) {
            val arr = JSONArray()
            v.takeLast(60).forEach {
                arr.put(JSONObject().put("lat", it.lat).put("lng", it.lng).put("count", it.count)
                    .put("day", it.lastDay).put("sug", it.suggested))
            }
            p.edit().putString("candidates", arr.toString()).apply()
        }

    fun clearPairing() {
        p.edit().remove("pairId").remove("curPlace").remove("curVisit").remove("lastEventSeen")
            .remove("pName").remove("pLat").remove("pLng").remove("pUpd").remove("pMode").remove("pPlace")
            .remove("mLat").remove("mLng").remove("since").remove("partnerPaused").remove("wasPaused").apply()
    }

    /** Conserva la apariencia, pero borra todos los datos personales de este teléfono. */
    fun clearSession() {
        val savedTheme = theme
        val saved3d = map3d
        val savedSatellite = mapSatellite
        p.edit().clear().putString("theme", savedTheme).putBoolean("map3d", saved3d)
            .putBoolean("sat", savedSatellite).apply()
    }
}
