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

        // Catégorie désactivée dans Paramètres > Notifications : aucun affichage, aucune vibration
        if (!com.example.mikayala.util.NotificationPrefs.isTypeEnabled(context, if (type == "calendar_event") "calendar_event" else if (type == "cycle_2_days") "cycle" else "anniversary")) {
            return
        }

        when (type) {
            "cycle_2_days" -> {
                // Texte neutre : aucune donnée de cycle sur l'écran verrouillé
                NotificationHelper.showCycleReminderNotification(context)
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
