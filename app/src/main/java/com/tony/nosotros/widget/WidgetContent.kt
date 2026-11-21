package com.tony.nosotros.widget

import com.tony.nosotros.util.Geo
import com.tony.nosotros.util.TimeFmt

/** Nunca convierte datos inexistentes ni una pausa en una distancia aparentemente actual. */
fun distanceWidgetValue(signedIn: Boolean, paused: Boolean, updated: Long, myLat: Double, myLng: Double, lat: Double, lng: Double, now: Long = System.currentTimeMillis(), myLocationAt: Long = updated): String = when {
    !signedIn -> "Inicia sesión"
    paused -> "En pausa"
    updated <= 0 || (myLat == 0.0 && myLng == 0.0) || (lat == 0.0 && lng == 0.0) -> "Sin ubicación"
    updated>now || myLocationAt<=0 || now-myLocationAt !in 0..360_000 || now-updated>360_000 -> "Por confirmar"
    else -> TimeFmt.km(Geo.dist(myLat, myLng, lat, lng))
}
