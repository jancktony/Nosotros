package com.tony.nosotros.ui

import org.junit.Assert.*
import org.junit.Test

class MapImageCacheTest {
    @Test fun thousandsOfSpeedChangesKeepBoundedHistoryAndVisiblePeople() {
        val images = MapImageCache()
        repeat(2000) { speed ->
            val visible = setOf("me-$speed", "partner-$speed", "home")
            visible.forEach { images.touch(it) }
            assertTrue(images.retireUnused(visible).none { it in visible })
            assertTrue("Sprite history grew with travel duration", images.size <= 32)
        }
        assertFalse(images.touch("me-1999"))
        assertFalse(images.touch("partner-1999"))
    }
    @Test fun visiblePlacesAreNotEvictedWhenThereAreMoreThanTheHistoryLimit() {
        val images = MapImageCache(2)
        val visible = setOf("a", "b", "c")
        visible.forEach { images.touch(it) }
        assertTrue(images.retireUnused(visible).isEmpty())
        assertEquals(listOf("a"), images.retireUnused(setOf("b", "c")))
        assertTrue(images.touch("a")) // A retired image must be generated again if it returns.
    }
}
