package com.tony.nosotros.presence

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class SleepEstimateTest {
    private val zone = TimeZone.getTimeZone("America/Bogota")
    private fun t(iso: String) = Instant.parse(iso).toEpochMilli()
    private fun estimate(now: String, since: String, interactive: Boolean = false) = SleepEstimate.estimate(t(now), t(since), interactive, 22, 8, zone)
    @Test fun thirtyMinutesAtNightIsOnlyAnEstimate() {
        assertEquals("idle", estimate("2026-11-11T03:20:00Z", "2026-11-11T03:00:00Z").state)
        assertEquals("sleeping", estimate("2026-11-11T04:00:00Z", "2026-11-11T03:00:00Z").state)
    }
    @Test fun unlockedPhoneIsAwakeImmediately() {
        assertEquals("awake", estimate("2026-11-11T06:00:00Z", "2026-11-11T03:00:00Z", true).state)
    }
    @Test fun daytimeInactivityIsNotSleep() {
        assertEquals("idle", estimate("2026-11-11T19:00:00Z", "2026-11-11T16:00:00Z").state)
    }
    @Test fun passingWakeTimeDoesNotDeclareCertainOversleeping() {
        assertEquals("overdue", estimate("2026-11-11T14:15:00Z", "2026-11-11T03:00:00Z").state)
        assertEquals("idle", estimate("2026-11-11T17:00:00Z", "2026-11-11T03:00:00Z").state)
    }
    @Test fun nightIsClampedRatherThanCountingTheWholeDay() {
        assertEquals(t("2026-11-11T03:00:00Z"), estimate("2026-11-11T04:00:00Z", "2026-11-10T16:00:00Z").since)
    }
    @Test fun futureOrUnknownStartCannotBeSleep() {
        assertEquals("idle", SleepEstimate.estimate(100, 200, false, 22, 8, zone).state)
        assertEquals("idle", SleepEstimate.estimate(100, 0, false, 22, 8, zone).state)
    }
    @Test fun oldOrRevokedAppDataIsNeverCurrent() {
        assertNull(Presence(true,"WhatsApp",updatedAt=100).text(200_000, false))
        assertNull(Presence(false,"WhatsApp",updatedAt=100).text(200, false))
        assertNull(Presence(true,"WhatsApp",updatedAt=300).text(200, false))
    }
}
