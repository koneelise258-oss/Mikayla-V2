package com.example.mikayala

import android.app.Application
import android.util.Log
import kotlin.system.exitProcess

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

        // Initialize Firebase Messaging safely without causing hard unhandled exceptions
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        Log.d("FCM_TOKEN", "Jeton FCM récupéré avec succès : $token")
                    } else {
                        Log.w("FCM_TOKEN", "Échec de la récupération du jeton FCM (ignoré en mode hors-ligne/test) : ${task.exception?.message}")
                    }
                }
        } catch (e: Throwable) {
            Log.w("FCM_INIT", "FCM non disponible ou désactivé temporairement : ${e.message}")
        }

        Log.d("MikayalaStartup", "Application.onCreate finished")
    }
}
