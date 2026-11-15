package com.tony.nosotros.ui

import org.junit.Assert.*
import org.junit.Test

class TogetherMotionTest {
    @Test fun particlesStayAbovePortraitsAndHaveIndependentPhases() {
        for (time in 0L..5600L step 33L) {
            val frames=(0 until TogetherMotion.HEARTS).map { TogetherMotion.heart(it,time) }
            assertTrue(frames.map { it.y }.distinct().size > 1)
            frames.forEach {
                assertTrue("Heart overlaps portraits",it.y + 14f*it.scale < -28f)
                assertTrue(it.opacity in 0f..1f)
                assertTrue(it.scale in .58f.. .89f)
            }
        }
    }
    @Test fun eachHeartRisesAndFadesOverItsOwnCycle() {
        val early=TogetherMotion.heart(0,350)
        val middle=TogetherMotion.heart(0,1400)
        val late=TogetherMotion.heart(0,2450)
        assertTrue(early.y>middle.y && middle.y>late.y)
        assertTrue(early.opacity<middle.opacity && late.opacity<middle.opacity)
        assertEquals(TogetherMotion.heart(0,0),TogetherMotion.heart(0,2800))
    }
    @Test fun reducedMotionLeavesAllHeartsVisibleAndStationary() {
        repeat(TogetherMotion.HEARTS) {
            assertEquals(TogetherMotion.heart(it,0,false),TogetherMotion.heart(it,99999,false))
            assertEquals(1f,TogetherMotion.heart(it,0,false).opacity,0f)
        }
    }
}
