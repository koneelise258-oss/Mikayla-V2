package com.example.mikayala.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.mikayala.util.NotificationHelper

class CycleAndCalendarReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra("REMINDER_TYPE") ?: "generic"
        Log.d("CycleAndCalendarReceiver", "Rappel déclenché de type : $type")

        when (type) {
            "cycle_2_days" -> {
                val partnerNickname = intent.getStringExtra("PARTNER_NICKNAME") ?: "Mon Partenaire"
                val phaseName = intent.getStringExtra("PHASE_NAME") ?: "Début de cycle prévu dans 2 jours"
                NotificationHelper.showCycleReminderNotification(
                    context = context,
                    phaseName = phaseName,
                    partnerNickname = partnerNickname
                )
            }
            "calendar_event" -> {
                val eventTitle = intent.getStringExtra("EVENT_TITLE") ?: "Événement de couple"
                val eventDate = intent.getStringExtra("EVENT_DATE") ?: "Aujourd'hui"
                val location = intent.getStringExtra("EVENT_LOCATION") ?: ""
                NotificationHelper.showCalendarEventNotification(
                    context = context,
                    eventTitle = eventTitle,
                    eventDate = eventDate,
                    location = location
                )
            }
            else -> {
                val title = intent.getStringExtra("TITLE") ?: "Rappel Mikayala"
                val message = intent.getStringExtra("MESSAGE") ?: "C'est l'heure de votre activité !"
                NotificationHelper.showAnniversaryNotification(context, "$title : $message")
            }
        }
    }
}
