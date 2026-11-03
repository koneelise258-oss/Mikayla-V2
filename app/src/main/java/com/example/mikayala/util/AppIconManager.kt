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
}
