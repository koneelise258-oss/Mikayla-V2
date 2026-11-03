package com.example.mikayala.util

import android.content.Context

/**
 * Préférences de notification persistées (SharedPreferences "mikayala_prefs").
 * Lues par MyFirebaseMessagingService même quand l'application est fermée,
 * donc volontairement indépendantes de tout état en mémoire.
 */
object NotificationPrefs {
    private const val PREFS = "mikayala_prefs"

    enum class Category(val key: String, val label: String) {
        MESSAGES("notif_cat_messages", "Messages"),
        CALLS("notif_cat_calls", "Appels"),
        GAMES("notif_cat_games", "Jeux"),
        CALENDAR("notif_cat_calendar", "Calendrier"),
        CYCLE("notif_cat_cycle", "Rappels du cycle"),
        OTHER("notif_cat_other", "Autres activités (coffre, humeur, sondages)")
    }

    /** Catégorie associée à un type de push FCM. */
    fun categoryFor(type: String): Category = when (type) {
        "chat_message", "audio", "image", "video" -> Category.MESSAGES
        "call", "missed_call" -> Category.CALLS
        "game", "game_turn" -> Category.GAMES
        "anniversary", "calendar_event" -> Category.CALENDAR
        "cycle" -> Category.CYCLE
        else -> Category.OTHER
    }

    fun isEnabled(context: Context, category: Category): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(category.key, true)

    fun setEnabled(context: Context, category: Category, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(category.key, enabled).apply()
    }

    fun isTypeEnabled(context: Context, type: String): Boolean = isEnabled(context, categoryFor(type))

    /** Mode d'affichage : "romantic" (nom + aperçu), "discreet", "weather_camouflage", "system_camouflage". */
    fun privacyMode(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("notification_privacy_mode", "romantic") ?: "romantic"
}
