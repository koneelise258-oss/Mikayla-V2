package com.example.mikayala.services

import android.content.Context
import android.util.Log
import com.example.mikayala.data.SupabaseService
import com.example.mikayala.util.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Service de réception des notifications push Firebase Cloud Messaging (FCM).
 * - Réutilise l'instance authentifiée unique de SupabaseService.
 * - Ne consigne jamais les jetons FCM complets dans les logs (masquage systématique).
 * - Met à jour la table sécurisée public.device_push_tokens dès qu'une session authentifiée est active.
 * - Gère les états d'application : premier plan, arrière-plan et état tué.
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val PREFS_NAME = "mikayala_push_prefs"
        private const val KEY_PENDING_TOKEN = "pending_fcm_token"

        fun getPendingToken(context: Context): String? {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PENDING_TOKEN, null)
        }

        fun clearPendingToken(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_PENDING_TOKEN)
                .apply()
        }

        fun savePendingToken(context: Context, token: String) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PENDING_TOKEN, token)
                .apply()
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val masked = if (token.length > 10) "${token.take(6)}...${token.takeLast(4)}" else "***"
        Log.d("FCM_TOKEN", "Nouveau jeton Firebase généré : $masked")

        // Sauvegarder localement pour garantie de synchronisation après authentification
        savePendingToken(applicationContext, token)

        // Tenter d'enregistrer sur Supabase via le service partagé si la session est déjà ouverte
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val supabase = SupabaseService.getInstance()
                val uid = supabase.getCurrentSessionUid()
                if (!uid.isNullOrEmpty()) {
                    val success = supabase.upsertDevicePushToken(token)
                    if (success) {
                        clearPendingToken(applicationContext)
                        Log.d("FCM_TOKEN", "Jeton FCM synchronisé avec succès sur device_push_tokens pour UID=$uid")
                    }
                } else {
                    Log.d("FCM_TOKEN", "Session non encore authentifiée : le jeton sera synchronisé après connexion")
                }
            } catch (e: Exception) {
                Log.w("FCM_TOKEN", "Erreur lors de la synchronisation du token FCM: ${e.message}")
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM_MSG", "Notification reçue de : ${remoteMessage.from}")

        // 1. Intercepter le dictionnaire de données (Data Payload)
        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            val type = data["type"] ?: "chat_message"
            val coupleId = data["coupleId"] ?: data["couple_id"] ?: ""

            when (type) {
                "call" -> {
                    val callerName = data["callerName"] ?: "Mon Amour"
                    val callType = data["callType"] ?: "voice"
                    val isVideo = callType == "video"
                    NotificationHelper.showIncomingCallNotification(
                        context = applicationContext,
                        callerName = callerName,
                        isVideo = isVideo
                    )
                }
                "missed_call" -> {
                    val callerName = data["callerName"] ?: "Mon Amour"
                    val isVideo = data["callType"] == "video"
                    NotificationHelper.showMissedCallNotification(
                        context = applicationContext,
                        callerName = callerName,
                        isVideo = isVideo
                    )
                }
                "chat_message", "audio", "image", "video" -> {
                    val senderName = data["senderName"] ?: "Mon Amour"
                    val defaultMsg = when (type) {
                        "audio" -> "🎤 Note vocale reçue"
                        "image" -> "📷 Photo reçue"
                        "video" -> "📹 Vidéo reçue"
                        else -> "Nouveau message"
                    }
                    val messageText = data["messageText"] ?: data["content"] ?: defaultMsg
                    val messageId = data["messageId"] ?: "msg_${System.currentTimeMillis()}"

                    NotificationHelper.showNewMessageNotification(
                        context = applicationContext,
                        senderName = senderName,
                        messageText = messageText,
                        messageId = messageId
                    )

                    // Signaler la délivrance sur le serveur sans bloquer
                    if (coupleId.isNotEmpty()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                SupabaseService.getInstance().markMessagesDelivered(coupleId)
                            } catch (e: Exception) {
                                Log.w("FCM_MSG", "Échec confirmation message délivré: ${e.message}")
                            }
                        }
                    }
                }
                "vault_add" -> {
                    val partnerName = data["partnerName"] ?: "Mon Amour"
                    val itemTitle = data["itemTitle"] ?: "Souvenir secret"
                    NotificationHelper.showVaultItemAddedNotification(
                        context = applicationContext,
                        partnerName = partnerName,
                        itemTitle = itemTitle
                    )
                }
                "game" -> {
                    val partnerName = data["partnerName"] ?: "Mon Amour"
                    val gameTitle = data["gameTitle"] ?: "Quiz de complicité"
                    NotificationHelper.showGameActivityNotification(
                        context = applicationContext,
                        partnerName = partnerName,
                        gameTitle = gameTitle
                    )
                }
                "game_turn" -> {
                    val partnerName = data["partnerName"] ?: "Mon Amour"
                    val gameTitle = data["gameTitle"] ?: "Jeu de couple"
                    NotificationHelper.showGameTurnNotification(
                        context = applicationContext,
                        partnerName = partnerName,
                        gameTitle = gameTitle
                    )
                }
                "anniversary" -> {
                    val text = data["message"] ?: "C'est notre anniversaire aujourd'hui ! 💕"
                    NotificationHelper.showAnniversaryNotification(
                        context = applicationContext,
                        anniversaryText = text
                    )
                }
                "cycle" -> {
                    val phaseName = data["phaseName"] ?: "Nouvelle Phase"
                    val partnerNickname = data["partnerNickname"] ?: "Mon Partenaire"
                    NotificationHelper.showCycleReminderNotification(
                        context = applicationContext,
                        phaseName = phaseName,
                        partnerNickname = partnerNickname
                    )
                }
                "mood_update" -> {
                    val partnerName = data["partnerName"] ?: "Mon Amour"
                    val moodEmoji = data["moodEmoji"] ?: "💖"
                    val moodLabel = data["moodLabel"] ?: "Amoureux"
                    NotificationHelper.showMoodUpdateNotification(
                        context = applicationContext,
                        partnerName = partnerName,
                        moodEmoji = moodEmoji,
                        moodLabel = moodLabel
                    )
                }
                "poll_created" -> {
                    val partnerName = data["partnerName"] ?: "Mon Amour"
                    val pollQuestion = data["pollQuestion"] ?: "Sondage de couple"
                    NotificationHelper.showPollCreatedNotification(
                        context = applicationContext,
                        partnerName = partnerName,
                        pollQuestion = pollQuestion
                    )
                }
                "love_bomb" -> {
                    val partnerName = data["partnerName"] ?: "Mon Amour"
                    val kissCount = data["kissCount"]?.toIntOrNull() ?: 100
                    NotificationHelper.showLoveBombNotification(
                        context = applicationContext,
                        partnerName = partnerName,
                        kissCount = kissCount
                    )
                }
            }
        }

        // 2. Traitement d'un éventuel bloc notification simple si présent
        remoteMessage.notification?.let {
            val title = it.title ?: "Mikayala"
            val body = it.body ?: ""
            Log.d("FCM_NOTIFICATION", "Titre: $title | Corps: $body")
        }
    }
}
