package com.tony.nosotros

import com.tony.nosotros.util.Together
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class TogetherTest {
    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) =
        Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis

    @Test fun spanYearsMonthsDays() {
        val s = Together.span(at(2023, 2, 14, 20, 0), at(2025, 5, 20, 22, 30))!!
        assertEquals(2, s.years); assertEquals(3, s.months); assertEquals(6, s.days)
        assertEquals(2, s.hours); assertEquals(30, s.minutes)
    }

    @Test fun borrowsAcrossMonth() {
        val s = Together.span(at(2024, 1, 31, 10), at(2024, 3, 1, 9))!!
        assertEquals(0, s.years); assertEquals(1, s.months)
    }

    @Test fun futureIsNull() { assertNull(Together.span(at(2030, 1, 1), at(2025, 1, 1))) }

    @Test fun daysToAnniversary() {
        assertEquals(0, Together.daysToAnniversary(at(2020, 6, 10), at(2025, 6, 10, 15)))
        assertEquals(1, Together.daysToAnniversary(at(2020, 6, 11), at(2025, 6, 10, 15)))
        assertEquals(364, Together.daysToAnniversary(at(2020, 6, 9), at(2025, 6, 10, 15)))
    }
}
