package com.tony.nosotros

import com.tony.nosotros.util.TimeFmt
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class NotificationTimeTest {
    @Test fun timesUseTwelveHoursInSpanish() {
        val zone=TimeZone.getTimeZone("UTC")
        assertEquals("12:00 a. m.",TimeFmt.hour(0,zone))
        assertEquals("12:00 p. m.",TimeFmt.hour(12*3600000L,zone))
        assertEquals("3:05 p. m.",TimeFmt.hour((15*60+5)*60000L,zone))
    }
}
