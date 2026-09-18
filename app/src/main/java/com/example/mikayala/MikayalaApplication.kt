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
        Log.d("MikayalaStartup", "Application.onCreate finished")
    }
}
