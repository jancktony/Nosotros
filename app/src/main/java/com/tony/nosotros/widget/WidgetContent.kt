package com.tony.nosotros.widget

import com.tony.nosotros.util.Geo
import com.tony.nosotros.util.TimeFmt

/** Nunca convierte datos inexistentes ni una pausa en una distancia aparentemente actual. */
fun distanceWidgetValue(signedIn: Boolean, paused: Boolean, updated: Long, myLat: Double, myLng: Double, lat: Double, lng: Double): String = when {
    !signedIn -> "Inicia sesión"
    paused -> "En pausa"
    updated <= 0 || (myLat == 0.0 && myLng == 0.0) || (lat == 0.0 && lng == 0.0) -> "Sin ubicación"
    else -> TimeFmt.km(Geo.dist(myLat, myLng, lat, lng))
}
