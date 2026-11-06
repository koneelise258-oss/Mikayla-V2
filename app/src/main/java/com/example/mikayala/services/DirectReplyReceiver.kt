package com.example.mikayala.services

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.example.mikayala.R
import com.example.mikayala.data.repository.MikayalaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class DirectReplyReceiver : BroadcastReceiver() {

    companion object {
        const val KEY_TEXT_REPLY = "key_text_reply"
        const val NOTIFICATION_ID_KEY = "notification_id"
        const val CHANNEL_NOTIFS_ID = "mikayala_general_notifs"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        if (remoteInput != null) {
            val replyText = remoteInput.getCharSequence(KEY_TEXT_REPLY)?.toString()
            val notifId = intent.getIntExtra(NOTIFICATION_ID_KEY, 2001)

            if (!replyText.isNullOrBlank()) {
                Log.d("DirectReplyReceiver", "Réponse rapide reçue depuis la notification : $replyText")
                val pendingResult = goAsync()

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val repository = MikayalaRepository.getInstance(context.applicationContext)
                        // App fermée : attendre le rechargement de la session avant d'envoyer
                        com.example.mikayala.data.SupabaseService.getInstance().awaitSessionReady()
                        repository.sendMessage(replyText)
                        // Attendre la confirmation réelle du serveur (le message reste sinon en attente
                        // et sera renvoyé automatiquement à la prochaine ouverture)
                        val confirmed = kotlinx.coroutines.withTimeoutOrNull(15_000L) {
                            repository.allMessages.first { list ->
                                val mine = list.lastOrNull { it.content == replyText && repository.isMessageFromMe(it) }
                                mine != null && mine.status != "pending" && mine.status != "failed"
                            }
                        } != null

                        // Mettre à jour la notification pour indiquer que le message a été envoyé
                        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        val updatedNotif = NotificationCompat.Builder(context, CHANNEL_NOTIFS_ID)
                            .setSmallIcon(R.drawable.ic_stat_mikayala)
                            .setContentTitle(if (confirmed) "Message envoyé" else "Réponse en attente d'envoi")
                            .setContentText(replyText)
                            .setTimeoutAfter(3000)
                            .build()

                        manager.notify(notifId, updatedNotif)
                    } catch (e: Exception) {
                        Log.e("DirectReplyReceiver", "Erreur lors de l'envoi de la réponse directe : ${e.message}", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
