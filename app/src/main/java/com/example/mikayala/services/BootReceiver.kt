package com.example.mikayala.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.mikayala.data.repository.MikayalaRepository

/** Android efface les alarmes au redémarrage : on reprogramme les rappels du calendrier et du cycle. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != "android.intent.action.MY_PACKAGE_REPLACED") return
        try {
            MikayalaRepository(context.applicationContext).rescheduleAllReminders()
        } catch (e: Exception) {
            Log.w("BootReceiver", "Reprogrammation des rappels impossible : ${e.message}")
        }
    }
}
