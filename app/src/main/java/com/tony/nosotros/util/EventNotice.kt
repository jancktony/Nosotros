package com.tony.nosotros.util

/** El aviso conserva la acción y el lugar incluso en su presentación de una línea. */
data class EventNotice(val title: String, val detail: String)

fun eventNotice(type: String, name: String, place: String?, at: Long, until: Long = 0L): EventNotice? {
    val person = name.trim().ifBlank { "Tu pareja" }
    val location = place?.trim()?.takeIf { it.isNotEmpty() } ?: "un lugar registrado"
    val action = when (type) {
        "arrive" -> "Llegó a $location"
        "leave" -> "Salió de $location"
        "pause" -> if (until > 4_000_000_000_000L || until == 0L) "Pausó su ubicación" else "Pausó su ubicación hasta las ${TimeFmt.hour(until)}"
        "resume" -> "Volvió a compartir su ubicación"
        "photo" -> "Te mandó una foto" + (place?.trim()?.takeIf { it.isNotEmpty() }?.let { ": $it" } ?: "")
        else -> return null
    }
    return EventNotice("$person · $action", "$action · ${TimeFmt.hour(at)}")
}
