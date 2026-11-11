package com.tony.nosotros.data

enum class Feature(val id: String, val title: String, val defaultMode: String = "all") {
    APP_PRESENCE("appPresence", "Compartir aplicación en uso (voluntario)", "off"),
    SLEEP_STATUS("sleepStatus", "Descanso estimado (voluntario)", "off"),
    MAP_PLACES("mapPlaces", "Nombres de universidades y lugares", "off"),
    MAP_GLOBE("mapGlobe", "Globo terrestre experimental", "off"),
    WALKIE("walkie", "Walkie-talkie en directo", "off"),
    VOICE_NOTES("voiceNotes", "Notas de voz", "off"),
    MAP_3D("map3d", "Mapa inclinado y edificios"),
    PROFILE_PHOTO("profilePhoto", "Fotos de perfil"),
    COMPACT_WIDGETS("compactWidgets", "Widgets compactos"),
    PLACE_ALERTS("placeAlerts", "Avisos de llegadas y salidas"),
    AUTO_UPDATES("autoUpdates", "Actualizaciones automáticas"),
    LOCK_DETAILS("lockDetails", "Detalles de avisos en pantalla de bloqueo"),
    MANUAL_UPDATES("manualUpdates", "Buscar actualizaciones manualmente");
}

data class FeatureAccess(val modes: Map<String, String> = emptyMap(), val admin: Boolean = false) {
    fun enabled(feature: Feature): Boolean = when (modes[feature.id] ?: feature.defaultMode) {
        "all" -> true
        "admin" -> admin
        else -> false
    }
}
