package com.example.mikayala.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.example.mikayala.MainActivity
import com.example.mikayala.R
import com.example.mikayala.services.DirectReplyReceiver

object NotificationHelper {

    private const val CHANNEL_CALLS_ID = "mikayala_incoming_calls"
    private const val CHANNEL_LOVE_TIME_ID = "mikayala_love_time_pinned"
    private const val CHANNEL_NOTIFS_ID = "mikayala_general_notifs"

    private const val NOTIF_ID_CALL = 1001
    private const val NOTIF_ID_LOVE_TIME = 1002
    private const val NOTIF_ID_CYCLE = 1003
    private const val NOTIF_ID_ANNIVERSARY = 1004
    private const val NOTIF_ID_GAME = 1005

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Incoming Calls Channel (High Priority with Sound & Vibration)
            val callChannel = NotificationChannel(
                CHANNEL_CALLS_ID,
                "Appels Entrants (Style WhatsApp)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications d'appels vocaux et vidéo en direct"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            }

            // 2. Love Time Pinned Channel (Ongoing / Status Bar)
            val loveTimeChannel = NotificationChannel(
                CHANNEL_LOVE_TIME_ID,
                "Love Time Épinglé 💖",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Compteur de couple épinglé dans la barre de statut"
            }

            // 3. General Notifications (Cycle, Jeux, Anniversaires)
            val generalChannel = NotificationChannel(
                CHANNEL_NOTIFS_ID,
                "Rappels & Activités de Couple",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Rappels de cycle menstruel, anniversaires et jeux du partenaire"
            }

            manager.createNotificationChannel(callChannel)
            manager.createNotificationChannel(loveTimeChannel)
            manager.createNotificationChannel(generalChannel)
        }
    }

    // --- 1. APPLIQUER LA NOTIFICATION D'APPEL ENTRANT (Style WhatsApp) ---
    fun showIncomingCallNotification(context: Context, callerName: String, isVideo: Boolean) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_CALL", true)
            putExtra("CALLER_NAME", callerName)
            putExtra("IS_VIDEO", isVideo)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_CALLS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (isVideo) "📹 Appel Vidéo Entrant" else "📞 Appel Vocal Entrant")
            .setContentText("$callerName vous appelle...")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(true)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .addAction(R.mipmap.ic_launcher, "Décrocher 💚", pendingIntent)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID_CALL, builder.build())
    }

    fun dismissIncomingCallNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIF_ID_CALL)
    }

    // --- 2. ÉPINGLAGE DU LOVE TIME DANS LA BARRE DE NOTIFICATION ---
    fun showLoveTimeOngoingNotification(context: Context, daysTogether: Int, coupleNames: String) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_LOVE_TIME_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("💖 Love Time : $coupleNames")
            .setContentText("Ensemble depuis $daysTogether jours de pur bonheur ✨")
            .setOngoing(true) // Pinned in status bar
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID_LOVE_TIME, builder.build())
    }

    // --- 3. RAPPEL DU CYCLE MENSTRUEL / PÉRIODE ---
    fun showCycleReminderNotification(context: Context, phaseName: String, partnerNickname: String) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_NOTIFS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🌸 Suivi du Cycle • $partnerNickname")
            .setContentText("Nouvelle phase détectée : $phaseName. Soyez aux petits soins ! 💕")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID_CYCLE, builder.build())
    }

    // --- 4. RAPPEL D'ANNIVERSAIRE ---
    fun showAnniversaryNotification(context: Context, anniversaryText: String) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_NOTIFS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🎉 Joyeux Anniversaire de Couple ! 🎉")
            .setContentText(anniversaryText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID_ANNIVERSARY, builder.build())
    }

    // --- 5. NOTIFICATION DE JEU DU PARTENAIRE ---
    fun showGameActivityNotification(context: Context, partnerName: String, gameTitle: String) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_NOTIFS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🎮 Jeu de Couple • $partnerName")
            .setContentText("$partnerName vient de jouer à « $gameTitle » ! Découvrez son score 🔥")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID_GAME, builder.build())
    }

    // --- 6. NOTIFICATION DE NOUVEAU MESSAGE AVEC RÉPONSE DIRECTE (Style WhatsApp) ---
    fun showNewMessageNotification(context: Context, senderName: String, messageText: String, messageId: String = "msg_${System.currentTimeMillis()}") {
        val notifId = messageId.hashCode()

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_CHAT", true)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remoteInput = RemoteInput.Builder(DirectReplyReceiver.KEY_TEXT_REPLY)
            .setLabel("Répondre à $senderName...")
            .build()

        val replyIntent = Intent(context, DirectReplyReceiver::class.java).apply {
            putExtra(DirectReplyReceiver.NOTIFICATION_ID_KEY, notifId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notifId,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            R.mipmap.ic_launcher,
            "Répondre 💬",
            replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        val builder = NotificationCompat.Builder(context, CHANNEL_NOTIFS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(replyAction)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notifId, builder.build())
    }

    // --- 7. NOTIFICATION DE RAPPEL DE CALENDRIER ---
    fun showCalendarEventNotification(context: Context, eventTitle: String, eventDate: String, location: String = "") {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (location.isNotBlank()) "À $eventDate • Lieu: $location" else "À $eventDate"

        val builder = NotificationCompat.Builder(context, CHANNEL_NOTIFS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("📅 Rappel d'Événement : $eventTitle")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(eventTitle.hashCode(), builder.build())
    }

    // --- 8. NOTIFICATION DE TOUR DE JEU ("C'est à ton tour de jouer !") ---
    fun showGameTurnNotification(context: Context, partnerName: String, gameTitle: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_GAMES", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_NOTIFS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🎮 C'est à ton tour de jouer !")
            .setContentText("$partnerName a validé son coup dans « $gameTitle ». À toi de jouer ! 🔥")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(3001, builder.build())
    }

    // --- 9. NOTIFICATION PROXIMITÉ HORS-LIGNE (SOCKET / P2P) AVEC QUICK REPLY ---
    fun showProximityMessageNotification(
        context: Context,
        senderName: String,
        content: String,
        isFile: Boolean = false
    ) {
        val notifId = ("p2p_" + System.currentTimeMillis()).hashCode()

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_CHAT", true)
            putExtra("P2P_MODE", true)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remoteInput = RemoteInput.Builder(DirectReplyReceiver.KEY_TEXT_REPLY)
            .setLabel("Répondre en mode Proximité...")
            .build()

        val replyIntent = Intent(context, DirectReplyReceiver::class.java).apply {
            putExtra(DirectReplyReceiver.NOTIFICATION_ID_KEY, notifId)
            putExtra("IS_P2P", true)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notifId,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            R.mipmap.ic_launcher,
            "Répondre 💬",
            replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        val displayText = if (isFile) "📁 Fichier reçu en mode Proximité : $content" else "⚡ [Mode Proximité] $content"

        val builder = NotificationCompat.Builder(context, CHANNEL_NOTIFS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("📡 Proximité • $senderName")
            .setContentText(displayText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(replyAction)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notifId, builder.build())
    }

    // --- 10. NOTIFICATION COFFRE-FORT SECRET ---
    fun showVaultItemAddedNotification(context: Context, partnerName: String, itemTitle: String) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_NOTIFS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🔒 Coffre-Fort Intime • $partnerName")
            .setContentText("$partnerName a ajouté un nouveau souvenir secret (« $itemTitle ») ! ✨")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(3002, builder.build())
    }

    // --- 11. NOTIFICATION HUMEUR DU JOUR ---
    fun showMoodUpdateNotification(context: Context, partnerName: String, moodEmoji: String, moodLabel: String) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_NOTIFS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🎭 Humeur du Jour • $partnerName")
            .setContentText("$partnerName se sent $moodEmoji $moodLabel aujourd'hui. Envoie-lui un mot doux ! 💕")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(3003, builder.build())
    }

    // --- 12. NOTIFICATION SONDAGE DE COUPLE ---
    fun showPollCreatedNotification(context: Context, partnerName: String, pollQuestion: String) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_NOTIFS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("📊 Sondage de Couple • $partnerName")
            .setContentText("$partnerName demande : « $pollQuestion » ! Donne ton avis 🗳️")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(3004, builder.build())
    }

    // --- 13. NOTIFICATION BISOU VOLANT / LOVE BOMB ---
    fun showLoveBombNotification(context: Context, partnerName: String, kissCount: Int = 100) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_NOTIFS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("💋 Bisou Volant Envoyé ! 💖")
            .setContentText("$partnerName vient de t'envoyer $kissCount bisous virtuels ! Pluie d'amour 🌧️💕")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(3005, builder.build())
    }
}
