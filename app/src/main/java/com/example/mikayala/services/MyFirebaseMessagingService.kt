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
        private val isSyncing = AtomicBoolean(false)

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
         * Récupère le jeton FCM de manière asynchrone suspendue sans bloquer le thread.
         */
        suspend fun getOrFetchFcmToken(context: Context): String? = withContext(Dispatchers.IO) {
            val pending = getPendingToken(context)
            if (!pending.isNullOrBlank()) {
                return@withContext pending
            }
            return@withContext suspendCancellableCoroutine<String?> { cont ->
                try {
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful && !task.result.isNullOrBlank()) {
                                if (cont.isActive) cont.resume(task.result) {}
                            } else {
                                if (cont.isActive) cont.resume(null) {}
                            }
                        }
                } catch (t: Throwable) {
                    if (cont.isActive) cont.resume(null) {}
                }
            }
        }

        /**
         * Synchronise le jeton FCM de manière suspendue, sécurisée et avec garde d'anti-concurrence.
         */
        suspend fun syncPendingAndCurrentToken(context: Context): Boolean = withContext(Dispatchers.IO) {
            if (!isSyncing.compareAndSet(false, true)) {
                Log.d("FCM_TOKEN", "Synchronisation du jeton déjà en cours, requête ignorée.")
                return@withContext false
            }
            try {
                val token = getOrFetchFcmToken(context)
                if (token.isNullOrBlank()) {
                    Log.w("FCM_TOKEN", "Aucun jeton FCM disponible pour la synchronisation.")
                    return@withContext false
                }
                savePendingToken(context, token)

                val supabase = SupabaseService.getInstance()
                val uid = supabase.getCurrentSessionUid()
                if (uid.isNullOrBlank()) {
                    Log.d("FCM_TOKEN", "Session absente : jeton conservé localement en attente.")
                    return@withContext false
                }

                val ok = supabase.upsertDevicePushToken(token)
                if (ok) {
                    clearPendingToken(context)
                    Log.d("FCM_TOKEN", "Jeton FCM synchronisé avec succès pour UID=$uid")
                    return@withContext true
                } else {
                    Log.w("FCM_TOKEN", "Échec enregistrement jeton FCM sur Supabase (conservé en attente).")
                    return@withContext false
                }
            } catch (e: Exception) {
                Log.w("FCM_TOKEN", "Erreur synchronisation jeton : ${e.message}")
                return@withContext false
            } finally {
                isSyncing.set(false)
            }
        }

        /**
         * Récupère le jeton FCM actif via FirebaseMessaging.getInstance().token
         * même si onNewToken() n'a pas été appelé, et le synchronise sur Supabase si une session est active.
         */
        fun fetchAndSyncToken(context: Context, onSynced: ((Boolean) -> Unit)? = null) {
            CoroutineScope(Dispatchers.IO).launch {
                val result = syncPendingAndCurrentToken(context)
                onSynced?.invoke(result)
            }
        }

        /**
         * Supprime l'association du jeton sur Supabase de manière suspendue et ATTENDUE
         * AVANT que la session Supabase ne soit fermée.
         * Garantit :
         * 1. Attente de la récupération du token FCM.
         * 2. Attente de la suppression effective dans device_push_tokens.
         * 3. Ne supprime pas le token local en attente avant confirmation du résultat.
         * 4. Ne supprime jamais le token d'un autre compte (filtré par user_id).
         * 5. Ne journalise jamais le token complet.
         */
        suspend fun unregisterDeviceTokenOnLogout(context: Context): Boolean = withContext(Dispatchers.IO) {
            val supabase = SupabaseService.getInstance()
            val uid = supabase.getCurrentSessionUid()
            if (uid.isNullOrBlank()) {
                Log.w("FCM_TOKEN", "Désinscription ignorée : aucune session active.")
                return@withContext false
            }

            val token = getOrFetchFcmToken(context)
            if (token.isNullOrBlank()) {
                Log.w("FCM_TOKEN", "Aucun jeton FCM trouvé pour désinscription.")
                clearPendingToken(context)
                return@withContext true
            }

            val masked = if (token.length > 10) "${token.take(6)}...${token.takeLast(4)}" else "***"
            Log.d("FCM_TOKEN", "Désinscription du jeton $masked pour UID=$uid avant déconnexion...")

            val success = supabase.deleteDevicePushToken(token)
            if (success) {
                clearPendingToken(context)
                Log.d("FCM_TOKEN", "Jeton FCM désinscrit avec succès de Supabase.")
            } else {
                Log.w("FCM_TOKEN", "Échec désinscription du jeton (conservé en local pour reprise).")
            }
            return@withContext success
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
