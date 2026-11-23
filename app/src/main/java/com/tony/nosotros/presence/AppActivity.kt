package com.tony.nosotros.presence

import java.util.Locale

data class AppActivity(val title: String, val subtitle: String, val badge: String, val available: Boolean, val icon: String = "") {
    companion object {
        fun label(raw: String): String = when (raw.trim().lowercase(Locale.ROOT)) {
            "inicio de one ui", "one ui home" -> "Pantalla de inicio"
            else -> raw.trim()
        }
        fun from(p: Presence, now: Long): AppActivity {
            if (p.updatedAt <= 0) return AppActivity("Sin señal reciente","Última app sin datos recientes","—",false)
            if (!p.sharing) return AppActivity("Actividad no compartida","No hay una app para mostrar","—",false)
            if (now-p.updatedAt !in -5_000L..180_000L) return AppActivity("Sin señal reciente","Última app sin datos recientes","—",false)
            val name=label(p.label)
            val seconds=((now-p.updatedAt).coerceAtLeast(0)/1000)
            val ago=if(seconds<5) "un instante" else if(seconds<60) "$seconds s" else "${seconds/60} min"
            if (name.isBlank()) return AppActivity("Sin app detectada","Última detección · hace $ago","—",false)
            val badge=when(name.lowercase(Locale.ROOT)) {
                "instagram" -> "IG"
                "whatsapp", "whatsapp business" -> "WA"
                "pantalla de inicio" -> "⌂"
                else -> name.take(2).uppercase(Locale.ROOT)
            }
            return AppActivity(name,"Última detección · hace $ago",badge,true,p.icon)
        }
    }
}
