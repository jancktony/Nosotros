package com.tony.nosotros

import com.tony.nosotros.util.eventNotice
import org.junit.Assert.*
import org.junit.Test

class EventNoticeTest {
    @Test fun noticesContainOnlyActionAndPlace() {
        assertEquals("Salió de casa", eventNotice("leave", "Kelin", "Casa", 1700000000000)!!.title)
        assertEquals("Llegó a casa", eventNotice("arrive", "Kelin", "Casa", 1700000000000)!!.title)
        assertEquals("Llegó al trabajo", eventNotice("arrive", "Kelin", "Trabajo", 1700000000000)!!.title)
        assertEquals("Salió del trabajo", eventNotice("leave", "Kelin", "Trabajo", 1700000000000)!!.title)
        assertEquals("", eventNotice("arrive", "Kelin", "Casa", 1700000000000)!!.detail)
    }
    @Test fun customPlaceLabelsArePreserved() {
        assertEquals("Llegó a la oficina", eventNotice("arrive", "", "la oficina", 0)!!.title)
        assertEquals("Salió de Casa de mamá", eventNotice("leave", "", "Casa de mamá", 0)!!.title)
    }
    @Test fun otherEventsAndUnnamedPlacesDoNotNotify() {
        for (kind in listOf("photo", "pause", "resume", "anniversary", "unknown")) assertNull(eventNotice(kind, "Kelin", "Casa", 0))
        assertNull(eventNotice("arrive", "", "  ", 0))
    }
}
