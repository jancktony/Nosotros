package com.tony.nosotros.tracking

import com.tony.nosotros.data.Mode
import org.junit.Assert.assertEquals
import org.junit.Test

class MotionDetectorTest {
    private fun MotionDetector.sample(t: Long, speed: Float? = 1.4f, distance: Double = 35.0,
                                      accuracy: Float = 5f, interval: Long = 30_000) =
        fix(t, accuracy, speed, distance, interval, 10.0)

    @Test fun repeatedWalkingWakesUnknownState() {
        val d = MotionDetector()
        d.sample(30_000); d.sample(60_000)
        assertEquals(Mode.UNKNOWN, d.mode)
        assertEquals(Mode.WALK, d.sample(90_000))
    }
    @Test fun singleFastSpikeDoesNotBecomeVehicle() {
        val d = MotionDetector()
        d.activity(Mode.WALK, 0)
        d.sample(10_000, 14f, 80.0)
        assertEquals(Mode.WALK, d.sample(30_000))
    }
    @Test fun sustainedFastMovementBecomesVehicle() {
        val d = MotionDetector()
        d.sample(10_000, 12f, 100.0)
        assertEquals(Mode.VEHICLE, d.sample(20_000, 12f, 100.0))
    }
    @Test fun poorGpsCannotChangeActivity() {
        val d = MotionDetector()
        d.activity(Mode.STILL, 0)
        repeat(5) { d.sample((it + 1) * 30_000L, 18f, 200.0, 100f) }
        assertEquals(Mode.STILL, d.mode)
    }
    @Test fun indoorDriftDoesNotBecomeWalking() {
        val d = MotionDetector()
        d.activity(Mode.STILL, 0)
        repeat(5) { d.sample((it + 1) * 30_000L, distance = 4.0) }
        assertEquals(Mode.STILL, d.mode)
    }
    @Test fun trafficDoesNotBecomeWalking() {
        val d = MotionDetector()
        d.activity(Mode.VEHICLE, 0)
        repeat(4) { d.sample((it + 1) * 30_000L) }
        assertEquals(Mode.VEHICLE, d.mode)
        repeat(4) { d.sample(150_000L + it * 30_000L, 0f, 2.0) }
        assertEquals(Mode.VEHICLE, d.mode)
    }
    @Test fun sustainedFootMovementAfterVehicleCanRecover() {
        val d = MotionDetector()
        d.activity(Mode.VEHICLE, 0)
        repeat(3) { d.sample(150_000L + it * 30_000L) }
        assertEquals(Mode.WALK, d.mode)
    }
    @Test fun footSensorImmediatelyUpdatesAfterParking() {
        val d = MotionDetector()
        d.activity(Mode.VEHICLE, 0)
        assertEquals(Mode.WALK, d.activity(Mode.WALK, 90_000))
    }
    @Test fun lateFootSensorCannotOverrideFastGps() {
        val d = MotionDetector()
        d.sample(10_000, 12f, 100.0); d.sample(20_000, 12f, 100.0)
        assertEquals(Mode.VEHICLE, d.activity(Mode.WALK, 25_000))
    }
    @Test fun cyclingIsNotReclassifiedAsWalkingAtLowSpeed() {
        val d = MotionDetector()
        d.activity(Mode.BIKE, 0)
        repeat(5) { d.sample((it + 1) * 30_000L) }
        assertEquals(Mode.BIKE, d.mode)
    }
    @Test fun MissingSpeedCanUseAccurateDisplacement() {
        val d = MotionDetector()
        repeat(3) { d.sample((it + 1) * 30_000L, null, 50.0) }
        assertEquals(Mode.WALK, d.mode)
    }
    @Test fun oldOrWidelySpacedSamplesDoNotConfirmMovement() {
        val d = MotionDetector()
        d.sample(10_000, 12f, 100.0)
        assertEquals(Mode.UNKNOWN, d.sample(9_000, 12f, 100.0))
        assertEquals(Mode.UNKNOWN, d.sample(200_000, 12f, 100.0))
    }
}
