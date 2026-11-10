package com.tony.nosotros.util

import org.junit.Assert.*
import org.junit.Test

class RealisticCameraTest {
    @Test fun rejectsInvalidCoordinates() {
        assertNull(RealisticCamera.frame(listOf(Pt(Double.NaN, 0.0), Pt(91.0, 0.0))))
        assertNull(RealisticCamera.frame(emptyList()))
    }
    @Test fun fitsAcrossDateLine() {
        val frame = RealisticCamera.frame(listOf(Pt(0.0, 179.9), Pt(0.0, -179.9)))!!
        assertEquals(180.0, kotlin.math.abs(frame.center.lng), 0.001)
        assertTrue(frame.range in 40_000.0..50_000.0)
    }
    @Test fun singlePersonHasStreetLevelRange() {
        val point = Pt(4.711, -74.072)
        val frame = RealisticCamera.frame(listOf(point))!!
        assertEquals(point.lat, frame.center.lat, 0.000001)
        assertEquals(point.lng, frame.center.lng, 0.000001)
        assertEquals(800.0, frame.range, 0.0)
    }
    @Test fun antipodalPeopleRemainFinite() {
        val frame = RealisticCamera.frame(listOf(Pt(0.0, 0.0), Pt(0.0, 180.0)))!!
        assertTrue(frame.range.isFinite())
        assertEquals(40_000_000.0, frame.range, 0.0)
    }
}
