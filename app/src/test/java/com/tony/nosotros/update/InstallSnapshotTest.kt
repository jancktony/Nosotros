package com.tony.nosotros.update

import org.junit.Assert.*
import org.junit.Test

class InstallSnapshotTest {
    @Test fun downloadedIsNotInstalled() {
        val snapshot = InstallSnapshot("1.15.5", InstallStage.DOWNLOADING, 100, 100, 20)
        assertEquals(100, snapshot.percent)
        assertFalse(snapshot.terminal)
        assertTrue(snapshot.cancellable)
        assertNull(snapshot.secondsLeft)
    }
    @Test fun installHasNoInventedPercentAndCannotBeCancelledAfterCommit() {
        listOf(InstallStage.VERIFYING, InstallStage.PREPARING, InstallStage.CONFIRMATION, InstallStage.INSTALLING, InstallStage.INSTALLED)
            .forEach { assertNull(InstallSnapshot("1.15.5", it, 100, 100).percent) }
        assertFalse(InstallSnapshot("1.15.5", InstallStage.INSTALLING).cancellable)
        assertFalse(InstallSnapshot("1.15.5", InstallStage.CONFIRMATION).terminal)
        assertTrue(InstallSnapshot("1.15.5", InstallStage.INSTALLED).terminal)
    }
    @Test fun etaRequiresMeasuredSpeedAndKnownSize() {
        assertNull(InstallSnapshot("1.15.5", InstallStage.DOWNLOADING, 25, 100).secondsLeft)
        assertNull(InstallSnapshot("1.15.5", InstallStage.DOWNLOADING).percent)
        assertEquals(3L, InstallSnapshot("1.15.5", InstallStage.DOWNLOADING, 25, 100, 25).secondsLeft)
        assertEquals(100, InstallSnapshot("1.15.5", InstallStage.DOWNLOADING, 150, 100).percent)
    }
}
