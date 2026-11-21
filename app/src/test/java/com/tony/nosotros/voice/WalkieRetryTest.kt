package com.tony.nosotros.voice
import org.junit.Assert.*
import org.junit.Test
class WalkieRetryTest {
    @Test fun continuesBeyondThreeFailuresWithBoundedTraffic() {
        assertEquals(listOf(2000L,4000L,8000L,16000L,30000L,30000L), (1..6).map(WalkieRetry::delayMillis))
        assertEquals(30000L, WalkieRetry.delayMillis(Int.MAX_VALUE))
    }
}
