package com.example.mikayala.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.example.mikayala.R

/**
 * Changement RÉEL de l'icône de l'écran d'accueil.
 * Android ne permet pas de dessiner une icône à la volée : chaque icône est un <activity-alias>
 * déclaré dans le manifeste. On active l'alias choisi et on désactive tous les autres.
 *
 * Limites Android (à afficher à l'utilisateur) :
 * - le lanceur peut mettre quelques secondes à afficher la nouvelle icône ;
 * - certains lanceurs retirent le raccourci de l'écran d'accueil (l'app reste dans le tiroir) ;
 * - une image de la galerie ne peut pas devenir icône d'application.
 */
object AppIconManager {
    data class AppIcon(val id: String, val alias: String, val label: String, val preview: Int)

    val ICONS = listOf(
        AppIcon("main", ".LauncherMain", "Deux bulles (principal)", R.mipmap.ic_launcher_round),
        // Violet & vert
        AppIcon("v1", ".LauncherV1", "Cœur violet", R.mipmap.ic_launcher_v1),
        AppIcon("v2", ".LauncherV2", "Papillon vert", R.mipmap.ic_launcher_v2),
        AppIcon("v3", ".LauncherV3", "Violet & vert", R.mipmap.ic_launcher_v3),
        AppIcon("v4", ".LauncherV4", "Nuit étoilée", R.mipmap.ic_launcher_v4),
        AppIcon("a", ".LauncherA", "Papillon dans une bulle", R.mipmap.ic_launcher_a_round),
        AppIcon("b", ".LauncherB", "Papillon seul", R.mipmap.ic_launcher_b_round),
        AppIcon("c", ".LauncherC", "Fond blanc, papillon dégradé", R.mipmap.ic_launcher_c_round),
        AppIcon("d", ".LauncherD", "Nuit, papillon dégradé", R.mipmap.ic_launcher_d_round),
        AppIcon("e", ".LauncherE", "Bulle pleine", R.mipmap.ic_launcher_e_round),
        AppIcon("f", ".LauncherF", "Corail chaleureux", R.mipmap.ic_launcher_f_round),
        AppIcon("n1", ".LauncherN1", "Cœur-bulle et papillon", R.mipmap.ic_launcher_n1_round),
        AppIcon("n2", ".LauncherN2", "Cœur corail, nuit bleue", R.mipmap.ic_launcher_n2_round),
        AppIcon("n3", ".LauncherN3", "Monogramme M", R.mipmap.ic_launcher_n3_round)
    )

    /**
     * Les noms ".LauncherX" du manifeste se résolvent depuis le namespace du code (com.example.mikayala),
     * PAS depuis l'applicationId (com.mikayla.app). ComponentName prend le package de l'app + ce nom complet.
     */
    private val CLASS_PREFIX: String = com.example.mikayala.MainActivity::class.java.name.substringBeforeLast('.')

    private const val PREFS = "mikayala_prefs"
    private const val KEY = "app_icon_id"

    /** Fonction pure (testable) : quels alias activer / désactiver pour un choix donné. */
    fun planFor(selectedId: String): Pair<String, List<String>> {
        val chosen = ICONS.firstOrNull { it.id == selectedId } ?: ICONS.first()
        return chosen.alias to ICONS.filter { it.alias != chosen.alias }.map { it.alias }
    }

    fun current(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "main") ?: "main"

    /** Applique l'icône. Retourne false si le système refuse (rien n'est alors enregistré). */
    fun apply(context: Context, id: String): Boolean {
        val pm = context.packageManager
        val (enable, disable) = planFor(id)
        return try {
            // Activer d'abord le nouvel alias : l'app garde toujours au moins une entrée de lancement
            pm.setComponentEnabledSetting(
                ComponentName(context, CLASS_PREFIX + enable),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            for (alias in disable) {
                pm.setComponentEnabledSetting(
                    ComponentName(context, CLASS_PREFIX + alias),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, ICONS.firstOrNull { it.id == id }?.id ?: "main").apply()
            true
        } catch (e: Exception) {
            android.util.Log.e("AppIconManager", "Changement d'icône refusé : ${e.message}")
            false
        }
    }

    // ------------------------------------------------------------------ icône à partir d'une photo

    enum class PhotoIconResult { REQUESTED, UPDATED, UNSUPPORTED, UNREADABLE }

    private const val PHOTO_SHORTCUT_ID = "mikayala_photo_icon"
    private const val PHOTO_FILE = "custom_app_icon.png"

    /** Photo choisie précédemment (aperçu dans les réglages), ou null. */
    fun customPhotoFile(context: Context): java.io.File? =
        java.io.File(context.filesDir, PHOTO_FILE).takeIf { it.exists() && it.length() > 0 }

    /**
     * Android ne laisse pas une appli remplacer son icône par une image quelconque.
     * Solution officielle : un RACCOURCI épinglé sur l'écran d'accueil, avec la photo comme icône,
     * qui ouvre Mikayala. Le téléphone demande confirmation (« Ajouter à l'écran d'accueil »).
     */
    fun pinPhotoShortcut(context: Context, uri: android.net.Uri): PhotoIconResult {
        val bitmap = decodeSquare(context, uri, 256) ?: return PhotoIconResult.UNREADABLE
        try {
            java.io.File(context.filesDir, PHOTO_FILE).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        } catch (_: Exception) {}

        val launch = android.content.Intent(context, com.example.mikayala.MainActivity::class.java).apply {
            action = android.content.Intent.ACTION_MAIN
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        val info = androidx.core.content.pm.ShortcutInfoCompat.Builder(context, PHOTO_SHORTCUT_ID)
            .setShortLabel(context.applicationInfo.loadLabel(context.packageManager).toString().ifBlank { "Mikayala" })
            .setIcon(androidx.core.graphics.drawable.IconCompat.createWithBitmap(bitmap))
            .setIntent(launch)
            .build()

        return try {
            // Raccourci déjà sur l'écran d'accueil : on change juste son image
            val alreadyPinned = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N_MR1) {
                androidx.core.content.pm.ShortcutManagerCompat
                    .getShortcuts(context, androidx.core.content.pm.ShortcutManagerCompat.FLAG_MATCH_PINNED)
                    .any { it.id == PHOTO_SHORTCUT_ID }
            } else false
            if (alreadyPinned) {
                androidx.core.content.pm.ShortcutManagerCompat.updateShortcuts(context, listOf(info))
                PhotoIconResult.UPDATED
            } else if (androidx.core.content.pm.ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(context, info, null)
                PhotoIconResult.REQUESTED
            } else {
                PhotoIconResult.UNSUPPORTED
            }
        } catch (e: Exception) {
            android.util.Log.e("AppIconManager", "Raccourci photo refusé : ${e.message}")
            PhotoIconResult.UNSUPPORTED
        }
    }

    /** Lit la photo (orientation corrigée), la recadre au centre en carré et la réduit. */
    private fun decodeSquare(context: Context, uri: android.net.Uri, size: Int): android.graphics.Bitmap? = try {
        val src: android.graphics.Bitmap? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
            android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                val minSide = minOf(info.size.width, info.size.height)
                if (minSide > size * 2) {
                    val ratio = (size * 2).toFloat() / minSide
                    decoder.setTargetSize((info.size.width * ratio).toInt().coerceAtLeast(1), (info.size.height * ratio).toInt().coerceAtLeast(1))
                }
            }
        } else {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size * 2) sample *= 2
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
        }
        src?.let {
            val side = minOf(it.width, it.height)
            val cropped = android.graphics.Bitmap.createBitmap(it, (it.width - side) / 2, (it.height - side) / 2, side, side)
            android.graphics.Bitmap.createScaledBitmap(cropped, size, size, true)
        }
    } catch (e: Exception) {
        android.util.Log.e("AppIconManager", "Photo illisible : ${e.message}")
        null
    }
}
