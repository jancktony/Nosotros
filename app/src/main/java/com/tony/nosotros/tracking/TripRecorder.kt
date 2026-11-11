package com.tony.nosotros.tracking

import com.tony.nosotros.data.Mode
import com.tony.nosotros.data.Prefs
import com.tony.nosotros.util.Geo
import com.tony.nosotros.util.Pt
import com.tony.nosotros.util.TimeFmt

/** Records the trip's dominant activity. GPS cannot distinguish a bus from a car
 * in traffic, so vehicle trips use the person's configured vehicle. */
class TripRecorder(private val prefs: Prefs) {
    var active = false
        private set
    private var start = 0L
    private var from: String? = null
    private val points = ArrayList<Pt>()
    var distance = 0.0
        private set
    private val modeTime = HashMap<Mode, Long>()
    private var lastRaw = Mode.UNKNOWN
    private var lastT = 0L

    fun begin(fromPlace: String?, now: Long = System.currentTimeMillis()) {
        active = true; start = now; from = fromPlace
        points.clear(); distance = 0.0; modeTime.clear()
        lastT = now
    }

    fun onRawMode(raw: Mode, now: Long = System.currentTimeMillis()) {
        if (active && lastRaw.moving) modeTime[lastRaw] = (modeTime[lastRaw] ?: 0L) + (now - lastT)
        lastRaw = raw; lastT = now
    }

    fun add(lat: Double, lng: Double, acc: Float, t: Long) {
        if (!active || acc > 60f) return
        val last = points.lastOrNull()
        if (last != null) {
            val d = Geo.dist(last.lat, last.lng, lat, lng)
            if (d < maxOf(acc, 10f) / 2) return   // ruido del GPS estando casi quieto
            distance += d
        }
        points.add(Pt(lat, lng, t))


    }

    fun classify(): Mode {
        val times = HashMap(modeTime)
        if (active && lastRaw.moving) times[lastRaw] = (times[lastRaw] ?: 0L) + (System.currentTimeMillis() - lastT)
        val dominant = times.maxByOrNull { it.value }?.key ?: lastRaw
        return when {
            dominant.isVehicle -> {
                prefs.vehicle
            }
            dominant == Mode.BIKE -> Mode.BIKE
            dominant == Mode.WALK -> Mode.WALK
            else -> Mode.UNKNOWN
        }
    }

    /** Ruta reciente para dibujar en vivo en el mapa de la pareja. */
    fun tail(): String = Geo.encode(points.takeLast(300))

    /** Cierra el trayecto. Devuelve los datos a guardar o null si fue demasiado corto. */
    fun finish(to: String?, uid: String, now: Long = System.currentTimeMillis()): Map<String, Any?>? {
        if (!active) return null
        val mode = classify()
        active = false
        if (distance < 300 || points.size < 3 || mode == Mode.UNKNOWN) return null
        val simplified = if (points.size > 800) points.filterIndexed { i, _ -> i % (points.size / 800 + 1) == 0 } else points
        return mapOf(
            "uid" to uid, "mode" to mode.name, "start" to start, "end" to now,
            "distanceM" to distance, "from" to from, "to" to to,
            "path" to Geo.encode(simplified), "day" to TimeFmt.dayKey(start),
        )
    }
}
