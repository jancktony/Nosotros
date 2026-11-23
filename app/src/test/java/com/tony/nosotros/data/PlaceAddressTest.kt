package com.tony.nosotros.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceAddressTest {
    @Test fun preservesLegacyNamesAndDisplaysTheFullAddressWhenKnown() {
        assertEquals("Casa", Place(name="Casa").displayName)
        assertEquals("Calle 56 #43-20, Barranquilla", Place(name="Casa",address=" Calle 56 #43-20 ",city=" Barranquilla ").displayName)
        assertEquals("Calle 56 #43-20", Place(address="Calle 56 #43-20").displayName)
    }
}
