package com.tony.nosotros.util
import org.junit.Assert.*
import org.junit.Test
class NavigationFixTest {
    private val now = 1_800_000_000_000L
    @Test fun recentHeartbeatDoesNotMakeOldGpsUsable() {
        assertFalse(NavigationFix.fresh(now-300_000,15f,false,now,60_000))
        assertTrue(NavigationFix.fresh(now-1000,15f,false,now,60_000))
    }
    @Test fun localNavigationExpiresSoonerThanSharedFallback() {
        assertFalse(NavigationFix.fresh(now-20_000,15f,false,now,15_000))
        assertTrue(NavigationFix.fresh(now-20_000,15f,false,now,60_000))
    }
    @Test fun rejectsPausedMissingFutureAndUnreliableFixes() {
        assertFalse(NavigationFix.fresh(now,10f,true,now,60_000))
        assertFalse(NavigationFix.fresh(0,10f,false,now,60_000))
        assertFalse(NavigationFix.fresh(now+1000,10f,false,now,60_000))
        for (accuracy in listOf(0f,-1f,81f,Float.NaN,Float.POSITIVE_INFINITY))
            assertFalse(NavigationFix.fresh(now,accuracy,false,now,60_000))
    }
}
