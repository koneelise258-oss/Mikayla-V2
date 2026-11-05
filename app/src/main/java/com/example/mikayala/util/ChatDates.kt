package com.example.mikayala.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Libellés des séparateurs de date du chat (fonctions pures, testables). */
object ChatDates {
    fun sameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    fun label(time: Long, now: Long = System.currentTimeMillis()): String {
        if (sameDay(time, now)) return "Aujourd'hui"
        if (sameDay(time, now - 24L * 60 * 60 * 1000)) return "Hier"
        val sameYear = Calendar.getInstance().apply { timeInMillis = time }.get(Calendar.YEAR) ==
            Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)
        val pattern = if (sameYear) "EEEE d MMMM" else "d MMMM yyyy"
        return SimpleDateFormat(pattern, Locale.FRANCE).format(Date(time)).replaceFirstChar { it.uppercase() }
    }
}
