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
            // Enregistrement et synchronisation centralisés avec garde d'anti-concurrence
            MyFirebaseMessagingService.fetchAndSyncToken(applicationContext)
        } catch (e: Throwable) {
            Log.w("MikayalaStartup", "FirebaseMessaging initialisation : ${e.message}")
        }

        Log.d("MikayalaStartup", "Application.onCreate finished")
    }
}
