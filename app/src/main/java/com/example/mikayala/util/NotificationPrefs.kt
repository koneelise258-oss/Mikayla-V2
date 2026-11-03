package com.example.mikayala.util

import android.content.Context

/**
 * Préférences de notification persistées (SharedPreferences "mikayala_prefs").
 *
 * Portée : LOCALE À L'APPAREIL. Elles survivent à la fermeture de l'application, au redémarrage
 * du téléphone et à la déconnexion/reconnexion (elles ne sont pas effacées par signOut et ne sont
 * pas synchronisées avec Supabase). Elles sont lues par MyFirebaseMessagingService même quand
 * l'application est fermée, donc indépendantes de tout état en mémoire.
 */
object NotificationPrefs {
    private const val PREFS = "mikayala_prefs"
    const val KEY_PRIVACY_MODE = "notification_privacy_mode"
    const val KEY_SOUND = "notification_sound"
    const val KEY_VIBRATION = "heart_haptic_enabled" // clé historique conservée pour ne pas perdre le choix existant
    const val KEY_CYCLE_LEGACY = "cycle_partner_alerts_enabled"

    const val SOUND_DEFAULT = "default"
    const val SOUND_SILENT = "silent"

    /** Sons réellement disponibles (le projet ne contient aucun fichier audio personnalisé). */
    val AVAILABLE_SOUNDS = listOf(
        SOUND_DEFAULT to "Son de notification du téléphone",
        SOUND_SILENT to "Silencieux (ni son ni vibration)"
    )

    enum class Category(val key: String, val label: String) {
        MESSAGES("notif_cat_messages", "Messages"),
        CALLS("notif_cat_calls", "Appels"),
        GAMES("notif_cat_games", "Jeux"),
        CALENDAR("notif_cat_calendar", "Calendrier"),
        CYCLE("notif_cat_cycle", "Rappels du cycle"),
        OTHER("notif_cat_other", "Autres activités (coffre, humeur, sondages)")
    }

    /** Catégorie associée à un type de push FCM ou de rappel local. */
    fun categoryFor(type: String): Category = when (type) {
        "chat_message", "audio", "image", "video", "proximity_message" -> Category.MESSAGES
        "call", "missed_call", "call_declined", "call_cancelled", "call_ended" -> Category.CALLS
        "game", "game_turn" -> Category.GAMES
        "anniversary", "calendar_event" -> Category.CALENDAR
        "cycle", "cycle_2_days" -> Category.CYCLE
        else -> Category.OTHER
    }

    /** Événements qui retirent une notification : jamais bloqués par une catégorie désactivée. */
    fun isDismissalType(type: String): Boolean =
        type == "call_cancelled" || type == "call_ended" || type == "call_declined"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context, category: Category): Boolean = prefs(context).getBoolean(category.key, true)

    fun setEnabled(context: Context, category: Category, enabled: Boolean) {
        val editor = prefs(context).edit().putBoolean(category.key, enabled)
        // Ancien réglage "Alertes de cycle" : même sens, maintenu cohérent
        if (category == Category.CYCLE) editor.putBoolean(KEY_CYCLE_LEGACY, enabled)
        editor.apply()
    }

    fun isTypeEnabled(context: Context, type: String): Boolean = isEnabled(context, categoryFor(type))

    /** "romantic" (nom + aperçu), "discreet", "weather_camouflage", "system_camouflage". */
    fun privacyMode(context: Context): String = prefs(context).getString(KEY_PRIVACY_MODE, "romantic") ?: "romantic"

    fun setPrivacyMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_PRIVACY_MODE, mode).apply()
    }

    /** Normalise les anciennes valeurs (heartbeat, soft_chime, whisper n'ont jamais eu de fichier audio). */
    fun normalizeSound(raw: String?): String = if (raw == SOUND_SILENT) SOUND_SILENT else SOUND_DEFAULT

    fun sound(context: Context): String = normalizeSound(prefs(context).getString(KEY_SOUND, SOUND_DEFAULT))

    fun setSound(context: Context, sound: String) {
        prefs(context).edit().putString(KEY_SOUND, normalizeSound(sound)).apply()
    }

    fun vibration(context: Context): Boolean = prefs(context).getBoolean(KEY_VIBRATION, true)

    fun setVibration(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_VIBRATION, enabled).apply()
    }

    /** Profil d'alerte effectif. Le mode silencieux coupe aussi la vibration. */
    data class AlertProfile(val sound: Boolean, val vibrate: Boolean) {
        /** Suffixe de canal : un canal Android ne peut plus changer de son après création. */
        val channelSuffix: String get() = when {
            sound && vibrate -> "_sv"
            sound -> "_s"
            vibrate -> "_v"
            else -> "_q"
        }
    }

    fun alertProfileFor(soundSetting: String?, vibration: Boolean): AlertProfile {
        val sound = normalizeSound(soundSetting) != SOUND_SILENT
        return AlertProfile(sound = sound, vibrate = sound && vibration)
    }

    fun alertProfile(context: Context): AlertProfile = alertProfileFor(sound(context), vibration(context))
}
