package com.tony.nosotros.ui

import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.location.Priority
import com.tony.nosotros.tracking.precisionLocationRequest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre

class LocationAnchoringDeviceTest {
    @get:Rule val ui = androidx.compose.ui.test.junit4.createComposeRule()
    @Test fun groundPointAndPortraitUseTheirActualCoordinateRegardlessOfCameraPitch() {
        ui.runOnUiThread {
            MapLibre.getInstance(InstrumentationRegistry.getInstrumentation().targetContext)
            val point = placePointLayer()
            assertEquals("viewport",point.circlePitchAlignment.value)
            assertEquals("viewport",point.circlePitchScale.value)
            val photo = peopleIconLayer()
            assertEquals("center",photo.iconAnchor.value)
            assertEquals("viewport",photo.iconPitchAlignment.value)
            assertEquals("viewport",photo.iconRotationAlignment.value)
            val label = placeLabelLayer()
            assertEquals("bottom",label.iconAnchor.value)
            assertTrue(label.iconOffset.value!![1] < 0)
        }
    }
    @Test fun precisionRefreshNeedsNewMeasurementsAndHasABatteryBound() {
        val request=precisionLocationRequest()
        assertEquals(Priority.PRIORITY_HIGH_ACCURACY,request.priority)
        assertEquals(0L,request.maxUpdateAgeMillis)
        assertTrue(request.isWaitForAccurateLocation)
        assertTrue(request.durationMillis in 1..20_000L)
    }
}
