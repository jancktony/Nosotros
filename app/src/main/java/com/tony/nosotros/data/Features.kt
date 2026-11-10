package com.tony.nosotros.data

enum class Feature(val id: String, val title: String, val defaultMode: String = "all") {
    WALKIE("walkie", "Walkie-talkie en directo", "off"),
    VOICE_NOTES("voiceNotes", "Notas de voz", "off"),
    MAP_3D("map3d", "Mapa inclinado y edificios"),
    PROFILE_PHOTO("profilePhoto", "Fotos de perfil"),
    COMPACT_WIDGETS("compactWidgets", "Widgets compactos"),
    PLACE_ALERTS("placeAlerts", "Avisos de llegadas y salidas"),
    MANUAL_UPDATES("manualUpdates", "Buscar actualizaciones manualmente");
}

data class FeatureAccess(val modes: Map<String, String> = emptyMap(), val admin: Boolean = false) {
    fun enabled(feature: Feature): Boolean = when (modes[feature.id] ?: feature.defaultMode) {
        "all" -> true
        "admin" -> admin
        else -> false
    }
}
