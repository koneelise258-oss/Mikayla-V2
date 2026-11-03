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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

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
        private const val KEY_PENDING_UNREGISTER_TOKEN = "pending_unregister_fcm_token"
        private const val KEY_PENDING_UNREGISTER_UID = "pending_unregister_user_id"
        private const val KEY_LAST_SYNCED_TOKEN = "last_synced_fcm_token"
        private const val KEY_LAST_SYNCED_UID = "last_synced_user_id"

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

        fun savePendingToken(context: Context, token: String?) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            if (token.isNullOrEmpty()) {
                prefs.remove(KEY_PENDING_TOKEN)
            } else {
                prefs.putString(KEY_PENDING_TOKEN, token)
            }
            prefs.apply()
        }

        fun getPendingUnregistrationInfo(context: Context): Pair<String, String>? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val token = prefs.getString(KEY_PENDING_UNREGISTER_TOKEN, null)
            val uid = prefs.getString(KEY_PENDING_UNREGISTER_UID, null)
            if (token.isNullOrEmpty() || uid.isNullOrEmpty()) return null
            return Pair(token, uid)
        }

        fun savePendingUnregistrationInfo(context: Context, token: String?, ownerUid: String?) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            if (token.isNullOrEmpty() || ownerUid.isNullOrEmpty()) {
                prefs.remove(KEY_PENDING_UNREGISTER_TOKEN)
                prefs.remove(KEY_PENDING_UNREGISTER_UID)
            } else {
                prefs.putString(KEY_PENDING_UNREGISTER_TOKEN, token)
                prefs.putString(KEY_PENDING_UNREGISTER_UID, ownerUid)
            }
            prefs.apply()
        }

        fun getLastSyncedTokenInfo(context: Context): Pair<String, String>? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val token = prefs.getString(KEY_LAST_SYNCED_TOKEN, null)
            val uid = prefs.getString(KEY_LAST_SYNCED_UID, null)
            if (token.isNullOrEmpty() || uid.isNullOrEmpty()) return null
            return Pair(token, uid)
        }

        fun saveLastSyncedTokenInfo(context: Context, token: String?, uid: String?) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            if (token.isNullOrEmpty() || uid.isNullOrEmpty()) {
                prefs.remove(KEY_LAST_SYNCED_TOKEN)
                prefs.remove(KEY_LAST_SYNCED_UID)
            } else {
                prefs.putString(KEY_LAST_SYNCED_TOKEN, token)
                prefs.putString(KEY_LAST_SYNCED_UID, uid)
            }
            prefs.apply()
        }

        fun getSyncEngine(context: Context): FcmTokenSyncEngine {
            val appContext = context.applicationContext
            val storage = object : TokenStorage {
                override fun getPendingToken(): String? = getPendingToken(appContext)
                override fun savePendingToken(token: String?) = savePendingToken(appContext, token)
                override fun getPendingUnregistrationInfo(): Pair<String, String>? = getPendingUnregistrationInfo(appContext)
                override fun savePendingUnregistrationInfo(token: String?, ownerUid: String?) = savePendingUnregistrationInfo(appContext, token, ownerUid)
                override fun getLastSyncedTokenInfo(): Pair<String, String>? = getLastSyncedTokenInfo(appContext)
                override fun saveLastSyncedTokenInfo(token: String?, uid: String?) = saveLastSyncedTokenInfo(appContext, token, uid)
            }
            val fetcher = object : FirebaseTokenFetcher {
                override suspend fun fetchToken(): String? = suspendCancellableCoroutine<String?> { cont ->
                    try {
                        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                            .addOnCompleteListener { task ->
                                if (cont.isActive) {
                                    if (task.isSuccessful && !task.result.isNullOrBlank()) {
                                        cont.resume(task.result, null)
                                    } else {
                                        cont.resume(null, null)
                                    }
                                }
                            }
                    } catch (t: Throwable) {
                        if (cont.isActive) cont.resume(null, null)
                    }
                }
            }
            val supabase = SupabaseService.getInstance()
            val api = object : SupabasePushApi {
                override fun getCurrentSessionUid(): String? = supabase.getCurrentSessionUid()
                override suspend fun upsertDevicePushToken(fcmToken: String): Boolean = supabase.upsertDevicePushToken(fcmToken)
                override suspend fun deleteDevicePushToken(fcmToken: String): Boolean = supabase.deleteDevicePushToken(fcmToken)
            }
            return FcmTokenSyncEngine.getInstance(storage, fetcher, api)
        }

        suspend fun getOrFetchFcmToken(context: Context): String? {
            return getSyncEngine(context).getOrFetchFcmToken()
        }

        suspend fun syncPendingAndCurrentToken(context: Context): Boolean {
            return getSyncEngine(context).syncPendingAndCurrentToken()
        }

        fun fetchAndSyncToken(context: Context, onSynced: ((Boolean) -> Unit)? = null) {
            CoroutineScope(Dispatchers.IO).launch {
                val res = syncPendingAndCurrentToken(context)
                onSynced?.invoke(res)
            }
        }

        suspend fun unregisterDeviceTokenOnLogout(context: Context): Boolean {
            return getSyncEngine(context).unregisterDeviceTokenOnLogout()
        }

        fun maskToken(token: String): String {
            return if (token.length > 10) "${token.take(6)}...${token.takeLast(4)}" else "***"
        }

        fun shouldIgnoreNotification(currentUid: String?, senderId: String?): Boolean {
            if (currentUid.isNullOrEmpty() || senderId.isNullOrEmpty()) return false
            return currentUid == senderId
        }

        fun resolveCallerName(privacyMode: String, rawCaller: String): String {
            return when (privacyMode) {
                "weather_camouflage" -> "Service Météo"
                "system_camouflage" -> "Système"
                "discreet" -> "Mikayala"
                else -> rawCaller.ifBlank { "Mon Amour" }
            }
        }

        fun resolveMessageDisplay(
            type: String,
            privacyMode: String,
            rawSender: String,
            rawText: String,
            isReply: Boolean = false
        ): Pair<String, String> {
            val defaultMsg = when (type) {
                "audio" -> "🎤 Note vocale reçue"
                "image" -> "📷 Photo reçue"
                "video" -> "📹 Vidéo reçue"
                else -> "Nouveau message"
            }
            val text = rawText.ifBlank { defaultMsg }
            val formattedText = if (isReply && !text.startsWith("↳")) "↳ $text" else text

            return when (privacyMode) {
                "weather_camouflage" -> Pair("Météo ☀️", "Ciel dégagé • 21°C")
                "system_camouflage" -> Pair("Système ⚙️", "Mise à jour système prête")
                "discreet" -> Pair("Mikayala 🔒", "Nouveau message chiffré")
                else -> Pair(rawSender.ifBlank { "Mon Amour" }, formattedText)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_TOKEN", "Nouveau jeton Firebase généré : ${maskToken(token)}")

        // Sauvegarder localement pour garantie de synchronisation après authentification
        savePendingToken(applicationContext, token)

        // Tenter d'enregistrer sur Supabase via le service partagé si la session est déjà ouverte
        CoroutineScope(Dispatchers.IO).launch {
            try {
                syncPendingAndCurrentToken(applicationContext)
            } catch (e: Exception) {
                Log.w("FCM_TOKEN", "Erreur lors de la synchronisation du token renouvelé: ${e.message}")
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
            if (shouldIgnoreNotification(currentUid, senderId)) {
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
                    val callerName = resolveCallerName(privacyMode, rawCaller)
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
                    val callerName = resolveCallerName(privacyMode, rawCaller)
                    NotificationHelper.showMissedCallNotification(
                        context = applicationContext,
                        callerName = callerName,
                        isVideo = isVideo,
                        callId = callId
                    )
                }
                "chat_message", "audio", "image", "video" -> {
                    val rawSender = data["senderName"] ?: "Mon Amour"
                    val rawText = data["messageText"] ?: data["content"] ?: ""
                    val isReply = !data["replyToId"].isNullOrEmpty()
                    val messageId = data["messageId"] ?: "msg_${System.currentTimeMillis()}"

                    val (displayedTitle, displayedText) = resolveMessageDisplay(
                        type = type,
                        privacyMode = privacyMode,
                        rawSender = rawSender,
                        rawText = rawText,
                        isReply = isReply
                    )

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
