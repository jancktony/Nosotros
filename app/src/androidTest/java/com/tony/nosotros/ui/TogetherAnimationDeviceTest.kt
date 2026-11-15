package com.tony.nosotros.ui

import androidx.test.platform.app.InstrumentationRegistry
import org.maplibre.android.MapLibre
import org.junit.Assert.*
import org.junit.Test

class TogetherAnimationDeviceTest {
    @Test fun particlesAndPortraitsUseViewportAlignmentAtAnyCameraBearing() {
        MapLibre.getInstance(InstrumentationRegistry.getInstrumentation().targetContext)
        val portraits=togetherPortraitLayer()
        assertEquals("viewport",portraits.iconRotationAlignment.value)
        assertEquals("viewport",portraits.iconPitchAlignment.value)
        repeat(TogetherMotion.HEARTS) {
            val heart=togetherHeartLayer(it)
            assertEquals("viewport",heart.iconTranslateAnchor.value)
            assertEquals("viewport",heart.iconRotationAlignment.value)
            assertEquals("viewport",heart.iconPitchAlignment.value)
            assertTrue(heart.iconTranslate.value!![1] < -28f)
        }
        val halo=togetherHaloLayer()
        assertEquals("viewport",halo.circlePitchAlignment.value)
        assertEquals("viewport",halo.circlePitchScale.value)
    }
}
