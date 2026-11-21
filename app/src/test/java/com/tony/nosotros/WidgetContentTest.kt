package com.tony.nosotros

import com.tony.nosotros.widget.distanceWidgetValue
import org.junit.Assert.*
import org.junit.Test

class WidgetContentTest {
    @Test fun signedOutAndPausedNeverShowCachedDistance() {
        assertEquals("Inicia sesión", distanceWidgetValue(false, false, 1, 4.0, -74.0, 4.1, -74.0))
        assertEquals("En pausa", distanceWidgetValue(true, true, 1, 4.0, -74.0, 4.1, -74.0))
    }
    @Test fun missingCoordinatesDoNotBecomeDistanceToZeroZero() {
        assertEquals("Sin ubicación", distanceWidgetValue(true, false, 0, 4.0, -74.0, 0.0, 0.0))
        assertEquals("Sin ubicación", distanceWidgetValue(true, false, 1, 0.0, 0.0, 4.0, -74.0))
    }
    @Test fun staleGpsNeverLooksLikeCurrentDistance(){
        assertEquals("Por confirmar",distanceWidgetValue(true,false,1,4.0,-74.0,4.1,-74.0,now=400000))
        assertEquals("Por confirmar",distanceWidgetValue(true,false,400000,4.0,-74.0,4.1,-74.0,now=400000,myLocationAt=1))
    }
    @Test fun realLocationsProduceDistance() {
        val text = distanceWidgetValue(true, false, 1, 4.0, -74.0, 4.1, -74.0, now=1)
        assertTrue(text.endsWith(" km"))
        assertFalse(text.startsWith("0"))
    }
}
