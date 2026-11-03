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

        /**
         * Récupère le jeton FCM actif via FirebaseMessaging.getInstance().token
         * même si onNewToken() n'a pas été appelé, et le synchronise sur Supabase si une session est active.
         */
        fun fetchAndSyncToken(context: Context, onSynced: ((Boolean) -> Unit)? = null) {
            try {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val token = task.result
                            if (!token.isNullOrEmpty()) {
                                val masked = if (token.length > 10) "${token.take(6)}...${token.takeLast(4)}" else "***"
                                Log.d("FCM_TOKEN", "Jeton FCM actuel récupéré : $masked")
                                savePendingToken(context, token)

                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        val supabase = SupabaseService.getInstance()
                                        val uid = supabase.getCurrentSessionUid()
                                        if (!uid.isNullOrEmpty()) {
                                            val ok = supabase.upsertDevicePushToken(token)
                                            if (ok) {
                                                clearPendingToken(context)
                                                Log.d("FCM_TOKEN", "Jeton FCM synchronisé pour UID=$uid")
                                                onSynced?.invoke(true)
                                            } else {
                                                onSynced?.invoke(false)
                                            }
                                        } else {
                                            Log.d("FCM_TOKEN", "Session absente : jeton conservé en attente")
                                            onSynced?.invoke(false)
                                        }
                                    } catch (e: Exception) {
                                        Log.w("FCM_TOKEN", "Erreur synchronisation jeton : ${e.message}")
                                        onSynced?.invoke(false)
                                    }
                                }
                            } else {
                                onSynced?.invoke(false)
                            }
                        } else {
                            Log.w("FCM_TOKEN", "Échec récupération jeton FCM: ${task.exception?.message}")
                            onSynced?.invoke(false)
                        }
                    }
            } catch (e: Throwable) {
                Log.w("FCM_TOKEN", "FirebaseMessaging non disponible : ${e.message}")
                onSynced?.invoke(false)
            }
        }

        /**
         * Supprime l'association du jeton sur Supabase lors d'une déconnexion ou d'un changement de compte
         * pour éviter d'associer le jeton de l'ancien utilisateur au nouveau compte.
         */
        fun unregisterDeviceTokenOnLogout(context: Context) {
            val pendingToken = getPendingToken(context)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val supabase = SupabaseService.getInstance()
                    if (!pendingToken.isNullOrEmpty()) {
                        supabase.deleteDevicePushToken(pendingToken)
                    }
                    try {
                        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    val currentToken = task.result
                                    if (!currentToken.isNullOrEmpty()) {
                                        CoroutineScope(Dispatchers.IO).launch {
                                            supabase.deleteDevicePushToken(currentToken)
                                        }
                                    }
                                }
                            }
                    } catch (_: Exception) {}
                    clearPendingToken(context)
                    Log.d("FCM_TOKEN", "Jeton désinscrit de Supabase suite à déconnexion")
                } catch (e: Exception) {
                    Log.w("FCM_TOKEN", "Erreur lors de la désinscription du jeton: ${e.message}")
                }
            }
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
            val senderId = data["senderId"] ?: data["sender_id"]

            // Ne jamais notifier l'utilisateur de ses propres messages
            val currentUid = try { SupabaseService.getInstance().getCurrentSessionUid() } catch (_: Exception) { null }
            if (!currentUid.isNullOrEmpty() && !senderId.isNullOrEmpty() && currentUid == senderId) {
                Log.d("FCM_MSG", "Notification ignorée : l'expéditeur est l'utilisateur courant")
                return
            }

            // Paramètres locaux de confidentialité et camouflage
            val prefs = applicationContext.getSharedPreferences("mikayala_prefs", Context.MODE_PRIVATE)
            val privacyMode = prefs.getString("notification_privacy_mode", "discreet") ?: "discreet"

            when (type) {
                "call" -> {
                    val rawCaller = data["callerName"] ?: "Mon Amour"
                    val isVideo = data["callType"] == "video"
                    val callId = data["callId"] ?: data["call_id"] ?: ""
                    val callerName = when (privacyMode) {
                        "weather_camouflage" -> "Service Météo"
                        "system_camouflage" -> "Système"
                        "discreet" -> "Mikayala"
                        else -> rawCaller
                    }
                    NotificationHelper.showIncomingCallNotification(
                        context = applicationContext,
                        callerName = callerName,
                        isVideo = isVideo,
                        callId = callId
                    )
                }
                "missed_call" -> {
                    val rawCaller = data["callerName"] ?: "Mon Amour"
                    val isVideo = data["callType"] == "video"
                    val callId = data["callId"] ?: data["call_id"] ?: ""
                    val callerName = when (privacyMode) {
                        "weather_camouflage" -> "Service Météo"
                        "system_camouflage" -> "Système"
                        "discreet" -> "Mikayala"
                        else -> rawCaller
                    }
                    NotificationHelper.showMissedCallNotification(
                        context = applicationContext,
                        callerName = callerName,
                        isVideo = isVideo,
                        callId = callId
                    )
                }
                "chat_message", "audio", "image", "video" -> {
                    val rawSender = data["senderName"] ?: "Mon Amour"
                    val defaultMsg = when (type) {
                        "audio" -> "🎤 Note vocale reçue"
                        "image" -> "📷 Photo reçue"
                        "video" -> "📹 Vidéo reçue"
                        else -> "Nouveau message"
                    }
                    val isReply = !data["replyToId"].isNullOrEmpty()
                    val rawText = data["messageText"] ?: data["content"] ?: defaultMsg
                    val formattedText = if (isReply && !rawText.startsWith("↳")) "↳ $rawText" else rawText
                    val messageId = data["messageId"] ?: "msg_${System.currentTimeMillis()}"

                    val (displayedTitle, displayedText) = when (privacyMode) {
                        "weather_camouflage" -> Pair("Météo ☀️", "Ciel dégagé • 21°C")
                        "system_camouflage" -> Pair("Système ⚙️", "Mise à jour système prête")
                        "discreet" -> Pair("Mikayala 🔒", "Nouveau message chiffré")
                        else -> Pair(rawSender, formattedText)
                    }

                    NotificationHelper.showNewMessageNotification(
                        context = applicationContext,
                        senderName = displayedTitle,
                        messageText = displayedText,
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
