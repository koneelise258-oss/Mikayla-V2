package com.example.mikayala.services

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.example.mikayala.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_TOKEN", "Nouveau jeton Firebase généré : $token")
        // Ici, en production, on peut synchroniser ce token avec la table user_settings sur Supabase
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM_MSG", "Notification reçue de : ${remoteMessage.from}")

        // 1. Intercepter les données du message push (Data Message)
        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            val type = data["type"] // "call", "cycle", "anniversary", "game"
            
            when (type) {
                "call" -> {
                    val callerName = data["callerName"] ?: "Mon Amour"
                    val callType = data["callType"] ?: "voice" // "voice" ou "video"
                    val isVideo = callType == "video"
                    
                    // Déclencher la notification d'appel entrant haute priorité style WhatsApp
                    NotificationHelper.showIncomingCallNotification(
                        context = applicationContext,
                        callerName = callerName,
                        isVideo = isVideo
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
                "anniversary" -> {
                    val text = data["message"] ?: "C'est notre anniversaire aujourd'hui ! 💕"
                    NotificationHelper.showAnniversaryNotification(
                        context = applicationContext,
                        anniversaryText = text
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
                "chat_message" -> {
                    val senderName = data["senderName"] ?: "Mon Amour"
                    val messageText = data["messageText"] ?: "Nouveau message"
                    val messageId = data["messageId"] ?: "msg_${System.currentTimeMillis()}"
                    val coupleId = data["coupleId"] ?: data["couple_id"] ?: ""
                    NotificationHelper.showNewMessageNotification(
                        context = applicationContext,
                        senderName = senderName,
                        messageText = messageText,
                        messageId = messageId
                    )
                    if (coupleId.isNotEmpty()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                com.example.mikayala.data.SupabaseService().markMessagesDelivered(coupleId)
                            } catch (e: Exception) {
                                Log.w("FCM_MSG", "Failed to mark messages delivered from FCM: ${e.message}")
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

        // 2. Si le message contient également une notification classique simple (Notification Message)
        remoteMessage.notification?.let {
            val title = it.title ?: "Mikayala"
            val body = it.body ?: ""
            Log.d("FCM_NOTIFICATION", "Titre: $title | Corps: $body")
        }
    }
}
