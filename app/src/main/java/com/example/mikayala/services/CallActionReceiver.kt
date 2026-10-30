package com.example.mikayala.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.mikayala.MainActivity
import com.example.mikayala.data.repository.MikayalaRepository
import com.example.mikayala.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Récepteur d'actions pour les notifications d'appels entrants (Style WhatsApp).
 * Gère "Décrocher" (ouverture directe avec décrochage automatique) et
 * "Refuser" (rejet immédiat de l'appel, fermeture de la notification et notification du partenaire).
 */
class CallActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ANSWER_CALL = "com.example.mikayala.ACTION_ANSWER_CALL"
        const val ACTION_DECLINE_CALL = "com.example.mikayala.ACTION_DECLINE_CALL"
        const val EXTRA_CALLER_NAME = "CALLER_NAME"
        const val EXTRA_IS_VIDEO = "IS_VIDEO"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("CallActionReceiver", "Action reçue : $action")

        when (action) {
            ACTION_DECLINE_CALL -> {
                NotificationHelper.dismissIncomingCallNotification(context)
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val repository = MikayalaRepository(context.applicationContext)
                        repository.declineIncomingCall()
                        Log.d("CallActionReceiver", "Appel entrant refusé et rejet notifié")
                    } catch (e: Exception) {
                        Log.w("CallActionReceiver", "Erreur lors du rejet de l'appel : ${e.message}")
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_ANSWER_CALL -> {
                NotificationHelper.dismissIncomingCallNotification(context)
                val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Mon Amour"
                val isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)

                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("OPEN_CALL", true)
                    putExtra("AUTO_ANSWER", true)
                    putExtra("CALLER_NAME", callerName)
                    putExtra("IS_VIDEO", isVideo)
                }
                context.startActivity(launchIntent)
            }
        }
    }
}
