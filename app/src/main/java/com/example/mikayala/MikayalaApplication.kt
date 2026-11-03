package com.example.mikayala

import android.app.Application
import android.util.Log
import com.example.mikayala.data.SupabaseService
import com.example.mikayala.services.MyFirebaseMessagingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MikayalaApplication : Application() {
    override fun onCreate() {
        Log.d("MikayalaStartup", "Application.onCreate started")
        super.onCreate()
        
        // Setup global crash handler to capture errors during startup
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("MikayalaCrash", "CRITICAL: Uncaught exception in thread ${thread.name}")
            Log.e("MikayalaCrash", "Type: ${throwable.javaClass.name}")
            Log.e("MikayalaCrash", "Reason: ${throwable.message}")
            throwable.printStackTrace()
        }

        // Initialize Firebase Messaging safely with token masking and sync queue
        try {
            val fcm = com.google.firebase.messaging.FirebaseMessaging.getInstance()
            fcm.isAutoInitEnabled = true
            fcm.token
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        if (!token.isNullOrEmpty()) {
                            val masked = if (token.length > 10) "${token.take(6)}...${token.takeLast(4)}" else "***"
                            Log.d("FCM_TOKEN", "Jeton FCM initial récupéré : $masked")
                            MyFirebaseMessagingService.savePendingToken(applicationContext, token)

                            // Synchronisation si session déjà active
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val supabase = SupabaseService.getInstance()
                                    val uid = supabase.getCurrentSessionUid()
                                    if (!uid.isNullOrEmpty()) {
                                        val ok = supabase.upsertDevicePushToken(token)
                                        if (ok) {
                                            MyFirebaseMessagingService.clearPendingToken(applicationContext)
                                        }
                                    }
                                } catch (ignored: Exception) {}
                            }
                        }
                    } else {
                        Log.w("FCM_TOKEN", "Échec de la récupération du jeton FCM (attendu si google-services.json non lié au projet ou hors-ligne) : ${task.exception?.message}")
                    }
                }
        } catch (e: Throwable) {
            Log.w("FCM_INIT", "FCM non disponible ou désactivé temporairement : ${e.message}")
        }

        Log.d("MikayalaStartup", "Application.onCreate finished")
    }
}
