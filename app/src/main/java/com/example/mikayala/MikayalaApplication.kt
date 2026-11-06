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
        
        // Journalisation des plantages, PUIS gestionnaire d'Android. Avant, le gestionnaire d'Android
        // était remplacé : après une erreur sur le fil principal, l'application restait figée
        // (écran bloqué, plus aucune réaction) au lieu de se fermer et de pouvoir redémarrer.
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("MikayalaCrash", "Exception non gérée dans ${thread.name}: ${throwable.javaClass.name}: ${throwable.message}", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }

        // Émojis : police Noto Color Emoji embarquée, même rendu sur tous les téléphones
        try {
            androidx.emoji2.text.EmojiCompat.init(
                androidx.emoji2.bundled.BundledEmojiCompatConfig(this)
                    .setReplaceAll(true)
            )
        } catch (e: Throwable) {
            Log.w("MikayalaStartup", "EmojiCompat : ${e.message}")
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
