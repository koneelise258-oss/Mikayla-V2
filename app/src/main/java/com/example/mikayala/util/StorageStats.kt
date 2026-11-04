package com.example.mikayala.util

import android.content.Context
import java.io.File

/** Mesures RÉELLES de l'espace utilisé par l'application sur cet appareil. */
object StorageStats {
    data class Usage(val cacheBytes: Long, val filesBytes: Long, val databasesBytes: Long) {
        val totalBytes get() = cacheBytes + filesBytes + databasesBytes
    }

    fun sizeOf(f: File?): Long {
        if (f == null || !f.exists()) return 0L
        if (f.isFile) return f.length()
        return f.listFiles()?.sumOf { sizeOf(it) } ?: 0L
    }

    fun measure(context: Context): Usage = Usage(
        cacheBytes = sizeOf(context.cacheDir) + sizeOf(context.externalCacheDir),
        filesBytes = sizeOf(context.filesDir),
        databasesBytes = sizeOf(context.getDatabasePath("x").parentFile)
    )

    /** Vide le cache (images téléchargées, fichiers temporaires). Les messages et réglages ne sont pas touchés. */
    fun clearCache(context: Context): Long {
        val before = sizeOf(context.cacheDir) + sizeOf(context.externalCacheDir)
        listOfNotNull(context.cacheDir, context.externalCacheDir).forEach { dir ->
            dir.listFiles()?.forEach { it.deleteRecursively() }
        }
        return before - (sizeOf(context.cacheDir) + sizeOf(context.externalCacheDir))
    }

    fun format(bytes: Long): String = when {
        bytes < 1024 -> "$bytes o"
        bytes < 1024 * 1024 -> String.format(java.util.Locale.FRANCE, "%.0f Ko", bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> String.format(java.util.Locale.FRANCE, "%.1f Mo", bytes / (1024.0 * 1024))
        else -> String.format(java.util.Locale.FRANCE, "%.2f Go", bytes / (1024.0 * 1024 * 1024))
    }
}
