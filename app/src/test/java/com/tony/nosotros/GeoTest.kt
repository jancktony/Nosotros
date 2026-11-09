package com.tony.nosotros

import com.tony.nosotros.util.Geo
import com.tony.nosotros.util.Pt
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {
    @Test fun polylineRoundTrip() {
        val pts = listOf(Pt(4.60971, -74.08175), Pt(4.61234, -74.07001), Pt(-33.4489, -70.6693), Pt(4.6, -74.0))
        val back = Geo.decode(Geo.encode(pts))
        assertEquals(pts.size, back.size)
        pts.zip(back).forEach { (a, b) ->
            assertEquals(a.lat, b.lat, 1e-5); assertEquals(a.lng, b.lng, 1e-5)
        }
    }

    @Test fun knownPolyline() {
        // ejemplo oficial del formato
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@",
            Geo.encode(listOf(Pt(38.5, -120.2), Pt(40.7, -120.95), Pt(43.252, -126.453))))
    }

    @Test fun distance() {
        assertEquals(111_195.0, Geo.dist(0.0, 0.0, 1.0, 0.0), 50.0)
    }
}
