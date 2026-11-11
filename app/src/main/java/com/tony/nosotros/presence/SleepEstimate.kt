package com.tony.nosotros.presence

import java.util.Calendar
import java.util.TimeZone

/** Phone inactivity is only a hint, never evidence that somebody is asleep. */
object SleepEstimate {
    data class Result(val state: String, val since: Long = 0)
    fun estimate(now: Long, inactiveSince: Long, interactive: Boolean, startHour: Int, wakeHour: Int,
                 zone: TimeZone = TimeZone.getDefault()): Result {
        if (interactive) return Result("awake")
        if (inactiveSince <= 0 || inactiveSince > now) return Result("idle")
        val start = Calendar.getInstance(zone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, startHour.coerceIn(18, 23)); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis > now) add(Calendar.DAY_OF_YEAR, -1)
        }
        val wake = (start.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, wakeHour.coerceIn(4, 12))
        }
        // Never turn a whole day away from the phone into endless sleep.
        if (now >= wake.timeInMillis + 3 * 3_600_000L) return Result("idle")
        val since = maxOf(inactiveSince, start.timeInMillis)
        if (now - since < 30 * 60_000L) return Result("idle")
        return Result(if (now >= wake.timeInMillis + 3_600_000L) "overdue" else "sleeping", since)
    }
}
