package com.tony.nosotros.util

import kotlin.math.*

/** Spherical midpoint also fits people on opposite sides of the date line. */
object RealisticCamera {
    data class Frame(val center: Pt, val range: Double)
    fun frame(points: List<Pt>): Frame? {
        val valid = points.filter { it.lat.isFinite() && it.lng.isFinite() && it.lat in -90.0..90.0 && it.lng in -180.0..180.0 }
        if (valid.isEmpty()) return null
        val x = valid.sumOf { cos(Math.toRadians(it.lat)) * cos(Math.toRadians(it.lng)) }
        val y = valid.sumOf { cos(Math.toRadians(it.lat)) * sin(Math.toRadians(it.lng)) }
        val z = valid.sumOf { sin(Math.toRadians(it.lat)) }
        val center = if (sqrt(x*x+y*y+z*z) < 1e-8) valid.first() else
            Pt(Math.toDegrees(atan2(z, sqrt(x*x+y*y))), Math.toDegrees(atan2(y, x)))
        val radius = valid.maxOf { Geo.dist(center.lat, center.lng, it.lat, it.lng) }
        return Frame(center, (radius * 4.0).coerceIn(800.0, 40_000_000.0))
    }
}
