package com.example.mikayala.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.mikayala.services.CycleAndCalendarReceiver

object CycleAndCalendarScheduler {

    /**
     * Planifie un rappel 2 jours avant la date prévue des règles / du cycle.
     * Déclenchement local garanti même sans connexion Internet.
     */
    fun scheduleCycleTwoDaysBefore(
        context: Context,
        expectedCycleStartTimeMillis: Long,
        partnerNickname: String = "Mon Amour"
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 2 jours avant la date prévue
        val twoDaysInMillis = 2 * 24 * 60 * 60 * 1000L
        val triggerTimeMillis = expectedCycleStartTimeMillis - twoDaysInMillis

        if (triggerTimeMillis <= System.currentTimeMillis()) {
            Log.d("CycleScheduler", "La date de rappel 2 jours avant est déjà passée. Ignorée.")
            return
        }

        val intent = Intent(context, CycleAndCalendarReceiver::class.java).apply {
            putExtra("REMINDER_TYPE", "cycle_2_days")
            putExtra("PARTNER_NICKNAME", partnerNickname)
            putExtra("PHASE_NAME", "Début de cycle prévu dans 2 jours (Soyez attentif/ve 💕)")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            9001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
            }
            Log.d("CycleScheduler", "Rappel du cycle planifié avec succès pour : $triggerTimeMillis")
        } catch (e: SecurityException) {
            Log.e("CycleScheduler", "Permission d'alarme exacte non accordée : ${e.message}")
        }
    }

    /**
     * Planifie une notification locale pour un événement du calendrier de couple.
     */
    fun scheduleCalendarEventReminder(
        context: Context,
        eventId: String,
        eventTitle: String,
        eventTimeMillis: Long,
        eventDateFormatted: String,
        location: String = ""
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (eventTimeMillis <= System.currentTimeMillis()) {
            return
        }

        val intent = Intent(context, CycleAndCalendarReceiver::class.java).apply {
            putExtra("REMINDER_TYPE", "calendar_event")
            putExtra("EVENT_TITLE", eventTitle)
            putExtra("EVENT_DATE", eventDateFormatted)
            putExtra("EVENT_LOCATION", location)
        }

        val requestCode = eventId.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, eventTimeMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, eventTimeMillis, pendingIntent)
            }
            Log.d("CalendarScheduler", "Rappel d'événement planifié pour $eventTitle à $eventTimeMillis")
        } catch (e: SecurityException) {
            Log.e("CalendarScheduler", "Permission d'alarme exacte non accordée : ${e.message}")
        }
    }
}
