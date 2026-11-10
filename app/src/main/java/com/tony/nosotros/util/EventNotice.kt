package com.tony.nosotros.util

data class EventNotice(val title: String, val detail: String = "")

@Suppress("UNUSED_PARAMETER")
fun eventNotice(type: String, name: String, place: String?, at: Long, until: Long = 0L): EventNotice? {
    val location = place?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val label = when {
        location.equals("casa", true) -> "casa"
        location.equals("trabajo", true) -> "el trabajo"
        else -> location
    }
    val destination = if (label.startsWith("el ", true)) "al ${label.substring(3)}" else "a $label"
    val origin = if (label.startsWith("el ", true)) "del ${label.substring(3)}" else "de $label"
    return when (type) {
        "arrive" -> EventNotice("Llegó $destination")
        "leave" -> EventNotice("Salió $origin")
        else -> null
    }
}
