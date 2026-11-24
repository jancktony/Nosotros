package com.tony.nosotros.util

import org.junit.Assert.*
import org.junit.Test

class LocationQualityTest {
    private val now = 200_000L
    private val good = MeasuredFix(11.0, -74.8, 8f, now - 5_000)
    @Test fun overlappingCoarseFixDoesNotReplaceRecentAccurateMeasurement() {
        assertFalse(LocationQuality.shouldUse(MeasuredFix(11.0001, -74.8, 90f, now), good, now))
        assertTrue(LocationQuality.shouldUse(MeasuredFix(11.00002, -74.8, 5f, now), good, now))
    }
    @Test fun acceptsRealDisplacementOrNewPositionAfterOldFixExpires() {
        assertTrue(LocationQuality.shouldUse(MeasuredFix(11.01, -74.8, 30f, now), good, now))
        assertTrue(LocationQuality.shouldUse(MeasuredFix(11.0001, -74.8, 90f, now), good.copy(elapsedMs=now-61_000), now))
    }
    @Test fun rejectsStaleFutureOutOfOrderAndInvalidMeasurements() {
        for (next in listOf(good.copy(elapsedMs=now-31_000),good.copy(elapsedMs=now+1),good.copy(elapsedMs=now-6_000),
            good.copy(accuracy=0f),good.copy(accuracy=Float.NaN),good.copy(accuracy=151f),good.copy(lat=91.0),good.copy(lng=Double.NaN))) {
            assertFalse("Invalid measurement accepted: $next",LocationQuality.shouldUse(next,good,now))
        }
    }
    @Test fun qualityDoesNotSnapCoordinatesOrRefreshTheTimeOfTheRetainedFix() {
        val next=MeasuredFix(11.0001,-74.8,90f,now)
        val retained=if(LocationQuality.shouldUse(next,good,now))next else good
        assertEquals(good,retained)
        assertEquals(now-5_000,retained.elapsedMs)
    }
}
