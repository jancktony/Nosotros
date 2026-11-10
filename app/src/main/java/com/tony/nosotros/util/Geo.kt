package com.tony.nosotros.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

data class Pt(val lat: Double, val lng: Double, val t: Long = 0L)

object Geo {
    /** Distancia en metros entre dos coordenadas (fórmula de haversine). */
    fun dist(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Codifica una ruta en el formato "polyline" (compacto, para guardar en Firestore). */
    fun encode(points: List<Pt>): String {
        val sb = StringBuilder()
        var pLat = 0L
        var pLng = 0L
        for (p in points) {
            val lat = (p.lat * 1e5).roundToLong()
            val lng = (p.lng * 1e5).roundToLong()
            enc(lat - pLat, sb); enc(lng - pLng, sb)
            pLat = lat; pLng = lng
        }
        return sb.toString()
    }

    private fun enc(v: Long, sb: StringBuilder) {
        var x = if (v < 0) (v shl 1).inv() else v shl 1
        while (x >= 0x20) {
            sb.append(((0x20L or (x and 0x1f)) + 63).toInt().toChar())
            x = x shr 5
        }
        sb.append((x + 63).toInt().toChar())
    }

    fun decode(s: String?): List<Pt> {
        if (s.isNullOrEmpty()) return emptyList()
        val out = ArrayList<Pt>()
        var i = 0
        var lat = 0L
        var lng = 0L
        while (i < s.length) {
            var shift = 0; var result = 0L; var b: Int
            do { b = s[i++].code - 63; result = result or ((b and 0x1f).toLong() shl shift); shift += 5 } while (b >= 0x20 && i < s.length)
            lat += if (result and 1L != 0L) (result shr 1).inv() else result shr 1
            shift = 0; result = 0L
            if (i >= s.length) break
            do { b = s[i++].code - 63; result = result or ((b and 0x1f).toLong() shl shift); shift += 5 } while (b >= 0x20 && i < s.length)
            lng += if (result and 1L != 0L) (result shr 1).inv() else result shr 1
            out.add(Pt(lat / 1e5, lng / 1e5))
        }
        return out
    }
}

object TimeFmt {
    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val hourFmt = SimpleDateFormat("H:mm", Locale.getDefault())
    private val niceDay = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es"))

    fun dayKey(t: Long = System.currentTimeMillis()): String = dayFmt.format(Date(t))
    fun dayKeyOffset(offset: Int): String {
        val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, offset)
        return dayFmt.format(c.time)
    }
    fun niceDayOffset(offset: Int): String = when (offset) {
        0 -> "Hoy"
        -1 -> "Ayer"
        else -> {
            val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, offset)
            niceDay.format(c.time).replaceFirstChar { it.uppercase() }
        }
    }
    fun hour(t: Long, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): String {
        val c = Calendar.getInstance(zone).apply { timeInMillis = t }
        val h = c.get(Calendar.HOUR).takeIf { it != 0 } ?: 12
        return "$h:${c.get(Calendar.MINUTE).toString().padStart(2, '0')} ${if (c.get(Calendar.AM_PM) == Calendar.AM) "a. m." else "p. m."}"
    }
    fun ago(t: Long): String {
        if (t <= 0) return "sin datos"
        val s = (System.currentTimeMillis() - t) / 1000
        return when {
            s < 45 -> "hace ${maxOf(s, 1)} s"
            s < 3600 -> "hace ${s / 60} min"
            s < 86400 -> "hace ${s / 3600} h"
            else -> "hace ${s / 86400} d"
        }
    }
    fun duration(ms: Long): String {
        val m = ms / 60000
        return if (m < 60) "$m min" else "${m / 60} h ${"%02d".format(m % 60)} min"
    }
    fun km(m: Double): String = if (m < 1000) "${m.toInt()} m" else "%.1f km".format(m / 1000)
    fun tomorrowAt(hour: Int): Long {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, 1); c.set(Calendar.HOUR_OF_DAY, hour)
        c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0)
        return c.timeInMillis
    }
}
