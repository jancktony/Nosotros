package com.tony.nosotros.util

import java.util.Calendar

/** Cálculos del tiempo juntos a partir de la fecha de aniversario. */
object Together {
    data class Span(val years: Int, val months: Int, val days: Int, val hours: Int, val minutes: Int, val totalDays: Long)

    fun span(since: Long, now: Long = System.currentTimeMillis()): Span? {
        if (since <= 0 || since > now) return null
        val a = Calendar.getInstance().apply { timeInMillis = since }
        val b = Calendar.getInstance().apply { timeInMillis = now }
        var years = b.get(Calendar.YEAR) - a.get(Calendar.YEAR)
        var months = b.get(Calendar.MONTH) - a.get(Calendar.MONTH)
        var days = b.get(Calendar.DAY_OF_MONTH) - a.get(Calendar.DAY_OF_MONTH)
        var hours = b.get(Calendar.HOUR_OF_DAY) - a.get(Calendar.HOUR_OF_DAY)
        var minutes = b.get(Calendar.MINUTE) - a.get(Calendar.MINUTE)
        if (minutes < 0) { minutes += 60; hours-- }
        if (hours < 0) { hours += 24; days-- }
        if (days < 0) {
            val prev = (b.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
            days += prev.getActualMaximum(Calendar.DAY_OF_MONTH); months--
        }
        if (months < 0) { months += 12; years-- }
        return Span(years, months, days, hours, minutes, (now - since) / 86_400_000L)
    }

    fun longText(s: Span): String {
        val parts = mutableListOf<String>()
        if (s.years > 0) parts += "${s.years} ${if (s.years == 1) "año" else "años"}"
        if (s.months > 0) parts += "${s.months} ${if (s.months == 1) "mes" else "meses"}"
        parts += "${s.days} ${if (s.days == 1) "día" else "días"}"
        return parts.joinToString(", ")
    }

    /** Días que faltan para el próximo aniversario (0 = hoy). */
    fun daysToAnniversary(since: Long, now: Long = System.currentTimeMillis()): Int? {
        if (since <= 0) return null
        val a = Calendar.getInstance().apply { timeInMillis = since }
        val today = Calendar.getInstance().apply {
            timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val next = (today.clone() as Calendar).apply {
            set(Calendar.MONTH, a.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, minOf(a.get(Calendar.DAY_OF_MONTH), getActualMaximum(Calendar.DAY_OF_MONTH)))
        }
        if (next.before(today)) next.add(Calendar.YEAR, 1)
        return ((next.timeInMillis - today.timeInMillis) / 86_400_000L).toInt()
    }

    /** ¿Hoy es "mesiversario" (mismo día del mes)? */
    fun isMonthiversary(since: Long, now: Long = System.currentTimeMillis()): Boolean {
        if (since <= 0) return false
        val a = Calendar.getInstance().apply { timeInMillis = since }
        val b = Calendar.getInstance().apply { timeInMillis = now }
        return a.get(Calendar.DAY_OF_MONTH) == b.get(Calendar.DAY_OF_MONTH) && now - since > 20L * 86_400_000L
    }
}
