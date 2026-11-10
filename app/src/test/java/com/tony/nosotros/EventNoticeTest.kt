package com.tony.nosotros

import com.tony.nosotros.util.eventNotice
import org.junit.Assert.*
import org.junit.Test

class EventNoticeTest {
    @Test fun arrivalAndDepartureKeepActionAndPlaceInBothPresentations() {
        for ((kind, action) in listOf("arrive" to "Llegó a", "leave" to "Salió de")) {
            val notice = eventNotice(kind, "Kelin", "Casa", 1_700_000_000_000L)!!
            assertTrue(notice.title.contains("Kelin"))
            assertTrue(notice.title.contains("$action Casa"))
            assertTrue(notice.detail.startsWith("$action Casa · "))
        }
    }
    @Test fun oldEventsWithoutPlaceNeverShowNullOrOnlyTime() {
        for (place in listOf(null, "", "   ")) {
            val notice = eventNotice("leave", "", place, 1_700_000_000_000L)!!
            assertTrue(notice.detail.contains("Salió de un lugar registrado"))
            assertFalse(notice.title.contains("null"))
        }
    }
    @Test fun photosPreserveCaptionAndUnknownTypesDoNotNotify() {
        assertTrue(eventNotice("photo", "Kelin", "Mira el paisaje", 1L)!!.detail.contains("Mira el paisaje"))
        assertNull(eventNotice("unknown", "Kelin", "Casa", 1L))
    }
}
