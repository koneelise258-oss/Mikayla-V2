package com.example.mikayala.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.mikayala.MainActivity
import com.example.mikayala.R

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
}
