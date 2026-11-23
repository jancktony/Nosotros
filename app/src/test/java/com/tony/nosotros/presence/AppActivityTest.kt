package com.tony.nosotros.presence

import org.junit.Assert.*
import org.junit.Test

class AppActivityTest {
    private val now=1_000_000L
    @Test fun recentDetectionNeverClaimsCurrentUse() {
        val a=AppActivity.from(Presence(true,"Instagram",updatedAt=now-30_000),now)
        assertEquals("Instagram",a.title)
        assertEquals("Última detección · hace 30 s",a.subtitle)
        assertFalse(a.subtitle.contains("Usando"))
        assertTrue(a.available)
    }
    @Test fun withdrawnOrExpiredDataNeverExposesPreviousApp() {
        for(p in listOf(Presence(false,"Instagram",updatedAt=now),Presence(true,"Instagram",updatedAt=now-180_001),
            Presence(true,"Instagram",updatedAt=now+5_001),Presence(true,"Instagram",updatedAt=0))) {
            val a=AppActivity.from(p,now)
            assertFalse(a.available);assertFalse(a.title.contains("Instagram"))
        }
        assertEquals("Actividad no compartida",AppActivity.from(Presence(false,updatedAt=now),now).title)
        assertEquals("Sin señal reciente",AppActivity.from(Presence(),now).title)
    }
    @Test fun normalizesOldAndNewLauncherLabels() {
        for(label in listOf("Inicio de One UI","One UI Home","Pantalla de inicio")) {
            val a=AppActivity.from(Presence(true,label,updatedAt=now),now)
            assertEquals("Pantalla de inicio",a.title);assertEquals("⌂",a.badge)
        }
    }
    @Test fun smallServerClockDifferenceAndEmptyReadingsAreHandled() {
        assertEquals("Última detección · hace un instante",
            AppActivity.from(Presence(true,"WhatsApp",updatedAt=now+2000),now).subtitle)
        assertEquals("Sin app detectada",AppActivity.from(Presence(true,"",updatedAt=now),now).title)
    }

    @Test fun iconIsOnlyExposedWithFreshVoluntarilySharedActivity() {
        val icon = "iVBORw0KGgoAAA=="
        assertEquals(icon, AppActivity.from(Presence(true,"Instagram",updatedAt=now,icon=icon),now).icon)
        assertEquals("", AppActivity.from(Presence(false,"Instagram",updatedAt=now,icon=icon),now).icon)
        assertEquals("", AppActivity.from(Presence(true,"Instagram",updatedAt=now-180001,icon=icon),now).icon)
    }
}
