package com.example.mikayala.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.mikayala.R

/**
 * Garde l'appel actif quand l'écran est verrouillé ou que l'on change d'application
 * (notification permanente « Appel en cours », comme WhatsApp).
 * Sans ce service, Android 11+ coupe le micro et la caméra dès que l'appli passe en arrière-plan.
 */
class CallForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HANG_UP -> {
                activeCall?.hangUp()
                stopSelfSafely()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                stopSelfSafely()
                return START_NOT_STICKY
            }
        }
        val isVideo = intent?.getBooleanExtra(EXTRA_VIDEO, false) == true
        val name = intent?.getStringExtra(EXTRA_NAME).orEmpty().ifBlank { "ton amour" }
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(isVideo, name), serviceType(isVideo))
        } catch (e: Exception) {
            // Android refuse parfois le démarrage (appli pas au premier plan, permission retirée) :
            // l'appel continue normalement tant que l'appli reste ouverte.
            Log.w(TAG, "Service d'appel non démarré : ${e.message}")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun serviceType(isVideo: Boolean): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return 0
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        val cameraGranted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (isVideo && cameraGranted) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        return type
    }

    private fun buildNotification(isVideo: Boolean, name: String): android.app.Notification {
        ensureChannel(this)
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        val openPending = openIntent?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val hangUpPending = PendingIntent.getService(
            this, 1,
            Intent(this, CallForegroundService::class.java).setAction(ACTION_HANG_UP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mikayala)
            .setContentTitle(if (isVideo) "Appel vidéo en cours" else "Appel en cours")
            .setContentText("Avec $name — touche pour revenir à l'appel")
            .setOngoing(true)
            .setUsesChronometer(true)
            .setWhen(System.currentTimeMillis())
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openPending)
            .addAction(0, "Raccrocher", hangUpPending)
            .build()
    }

    private fun stopSelfSafely() {
        try { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MikayalaCallService"
        private const val CHANNEL_ID = "mikayala_call_ongoing"
        private const val NOTIF_ID = 4711
        private const val EXTRA_VIDEO = "video"
        private const val EXTRA_NAME = "name"
        private const val ACTION_HANG_UP = "com.mikayla.app.CALL_HANG_UP"
        private const val ACTION_STOP = "com.mikayla.app.CALL_STOP"

        /** Appel en cours, pour le bouton « Raccrocher » de la notification. */
        @Volatile var activeCall: WebRtcCall? = null

        fun start(context: Context, call: WebRtcCall, partnerName: String) {
            activeCall = call
            val intent = Intent(context, CallForegroundService::class.java)
                .putExtra(EXTRA_VIDEO, call.isVideo)
                .putExtra(EXTRA_NAME, partnerName)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Démarrage du service d'appel impossible : ${e.message}")
            }
        }

        fun stop(context: Context, call: WebRtcCall) {
            if (activeCall !== call) return
            activeCall = null
            try {
                context.startService(Intent(context, CallForegroundService::class.java).setAction(ACTION_STOP))
            } catch (_: Exception) {
                try { context.stopService(Intent(context, CallForegroundService::class.java)) } catch (_: Exception) {}
            }
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Appel en cours", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Garde l'appel actif écran verrouillé"
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
    }
}
