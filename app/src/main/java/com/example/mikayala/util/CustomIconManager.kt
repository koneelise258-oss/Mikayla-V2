package com.example.mikayala.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import java.io.File

/**
 * Icône d'écran d'accueil à partir d'une photo choisie par l'utilisateur.
 *
 * Android ne permet à AUCUNE application de remplacer son icône principale par une image
 * quelconque (seules les icônes déclarées à l'avance sont possibles, voir AppIconManager).
 * La seule méthode autorisée est le « raccourci épinglé » : une icône d'écran d'accueil avec
 * l'image choisie, qui ouvre Mikayala. Une fois posé, son image peut être changée à volonté
 * sans en recréer un. Les images déjà utilisées sont gardées pour pouvoir y revenir.
 */
object CustomIconManager {
    private const val SHORTCUT_ID = "mikayala_custom_icon"
    private const val DIR = "custom_icons"
    private const val MAX_HISTORY = 12
    private const val SIZE = 256
    private const val PREFS = "mikayala_prefs"
    private const val KEY_CURRENT = "custom_icon_current"

    enum class Result { PINNED_REQUESTED, UPDATED, UNSUPPORTED, FAILED }

    private fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    /** Images déjà utilisées, de la plus récente à la plus ancienne. */
    fun history(context: Context): List<File> =
        dir(context).listFiles { f -> f.name.endsWith(".png") }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun currentPath(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CURRENT, null)

    fun isSupported(context: Context): Boolean = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    /** Recadre la photo en carré, la réduit et l'ajoute à l'historique. Retourne le fichier ou null. */
    fun importImage(context: Context, uri: Uri): File? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val minSide = minOf(bounds.outWidth, bounds.outHeight)
        if (minSide <= 0) null else {
            var sample = 1
            while (minSide / (sample * 2) >= SIZE) sample *= 2
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null
            val oriented = rotateFromExif(context, uri, decoded)
            val side = minOf(oriented.width, oriented.height)
            val square = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            val left = (oriented.width - side) / 2
            val top = (oriented.height - side) / 2
            Canvas(square).drawBitmap(
                oriented,
                Rect(left, top, left + side, top + side),
                Rect(0, 0, SIZE, SIZE),
                Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            )
            if (oriented !== decoded) oriented.recycle()
            decoded.recycle()
            val file = File(dir(context), "icon_${System.currentTimeMillis()}.png")
            file.outputStream().use { square.compress(Bitmap.CompressFormat.PNG, 100, it) }
            square.recycle()
            trimHistory(context)
            file
        }
    } catch (e: Exception) {
        Log.w("CustomIcon", "Image illisible: ${e.message}")
        null
    }

    private fun rotateFromExif(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        if (Build.VERSION.SDK_INT < 24) return bitmap
        val degrees = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                when (android.media.ExifInterface(input).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (_: Exception) { 0f }
        if (degrees == 0f) return bitmap
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }

    private fun trimHistory(context: Context) {
        val current = currentPath(context)
        history(context).drop(MAX_HISTORY).filter { it.absolutePath != current }.forEach { it.delete() }
    }

    fun delete(context: Context, file: File) {
        if (file.absolutePath == currentPath(context)) return // l'icône affichée reste
        file.delete()
    }

    private fun isPinned(context: Context): Boolean = try {
        ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
            .any { it.id == SHORTCUT_ID }
    } catch (_: Exception) { false }

    /**
     * Applique l'image : met à jour l'icône déjà posée, ou demande au téléphone d'en poser une
     * (une fenêtre de confirmation du téléphone s'affiche la première fois).
     */
    fun apply(context: Context, file: File): Result {
        if (!isSupported(context)) return Result.UNSUPPORTED
        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return Result.FAILED
            val launch = Intent(context, com.example.mikayala.MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            val info = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
                .setShortLabel("Mikayala")
                .setIcon(IconCompat.createWithBitmap(bitmap))
                .setIntent(launch)
                .build()
            val result = if (Build.VERSION.SDK_INT >= 26 && isPinned(context)) {
                if (ShortcutManagerCompat.updateShortcuts(context, listOf(info))) Result.UPDATED else Result.FAILED
            } else {
                if (ShortcutManagerCompat.requestPinShortcut(context, info, null)) Result.PINNED_REQUESTED else Result.FAILED
            }
            if (result != Result.FAILED) {
                file.setLastModified(System.currentTimeMillis()) // remonte en tête de l'historique
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_CURRENT, file.absolutePath).apply()
            }
            result
        } catch (e: Exception) {
            Log.w("CustomIcon", "Icône personnalisée refusée: ${e.message}")
            Result.FAILED
        }
    }
}
