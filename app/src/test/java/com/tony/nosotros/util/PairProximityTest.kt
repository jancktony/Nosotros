package com.tony.nosotros.util

import org.junit.Assert.*
import org.junit.Test

class PairProximityTest {
    private val now = 1_000_000L
    private fun pair(meters: Double = 10.0) = listOf(
        PairFix("a", 11.0, -74.0, 8f, now, false),
        PairFix("b", 11.0 + meters / 111_195.0, -74.0, 8f, now, false))
    private fun join(p: PairProximity, fixes: List<PairFix> = pair()) {
        assertFalse(p.update(fixes, now, 0))
        assertFalse(p.update(fixes, now, 9_999))
        assertTrue(p.update(fixes, now, 10_000))
    }
    @Test fun requiresStableNearnessAndUsesHysteresis() {
        val p = PairProximity(); join(p)
        assertTrue(p.update(pair(55.0), now, 12_000))
        assertFalse(p.update(pair(80.0), now, 14_000))
        assertFalse(p.update(pair(55.0), now, 30_000))
    }
    @Test fun pausedStaleInaccurateAndMissingFixesImmediatelyUngroup() {
        val invalid = listOf(pair().drop(1), pair().map { it.copy(paused=true) },
            pair().map { it.copy(updatedAt=now-360_001) }, pair().map { it.copy(updatedAt=now+30_001) },
            pair().map { it.copy(accuracy=0f) }, pair().map { it.copy(accuracy=36f) },
            pair().map { it.copy(accuracy=Float.NaN) }, pair().map { it.copy(lat=Double.NaN) },
            pair().map { it.copy(lat=91.0) }, pair().map { it.copy(updatedAt=0) },
            pair().map { it.copy(uid="a") })
        for (fixes in invalid) { val p=PairProximity(); join(p); assertFalse(p.update(fixes,now,20_000)) }
    }
    @Test fun changingPairOrBriefSeparationRestartsConfirmation() {
        val p=PairProximity(); join(p)
        val changed=pair().map { it.copy(uid=it.uid+"new") }
        assertFalse(p.update(changed,now,12_000))
        assertTrue(p.update(changed,now,22_000))
        assertFalse(p.update(pair(80.0),now,23_000))
        assertFalse(p.update(pair(),now,24_000))
    }
    @Test fun centerDoesNotJumpAcrossDateLine() {
        val c=PairProximity.center(1.0,179.9999,1.0,-179.9999)
        assertTrue(kotlin.math.abs(c.lng)>179.9)
    }
}
