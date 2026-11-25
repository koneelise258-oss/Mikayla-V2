package com.example.mikayala.data

import android.content.SharedPreferences
import com.example.mikayala.data.model.UserSettingsEntity

/**
 * Préférences d'interface, LOCALES À L'APPAREIL (SharedPreferences "mikayala_prefs").
 * Avant cette correction, ces réglages n'étaient gardés qu'en mémoire : ils revenaient à leur
 * valeur par défaut à chaque redémarrage. Ils survivent maintenant à la fermeture de l'application,
 * au redémarrage du téléphone et à la déconnexion (ce sont des préférences de l'appareil).
 * Codes PIN, compte et couple restent gérés à part par MikayalaRepository.
 */
object UiPreferencesStore {
    private const val P = "ui_"
    private const val KEY_FLAG_SECURE_MIGRATED_V2 = "ui_flag_secure_migrated_v2"

    fun load(prefs: SharedPreferences, base: UserSettingsEntity): UserSettingsEntity {
        val migratedFlagSecure = prefs.getBoolean(KEY_FLAG_SECURE_MIGRATED_V2, false)
        val screenBlur = if (migratedFlagSecure) {
            prefs.getBoolean(P + "screen_blur", base.screenBlurOnSwitch)
        } else {
            base.screenBlurOnSwitch
        }
        val antiScreenshot = if (migratedFlagSecure) {
            prefs.getBoolean(P + "anti_screenshot", base.antiScreenshotEnabled)
        } else {
            base.antiScreenshotEnabled
        }
        if (!migratedFlagSecure && (prefs.contains(P + "screen_blur") || prefs.contains(P + "anti_screenshot"))) {
            prefs.edit()
                .putBoolean(P + "screen_blur", screenBlur)
                .putBoolean(P + "anti_screenshot", antiScreenshot)
                .putBoolean(KEY_FLAG_SECURE_MIGRATED_V2, true)
                .apply()
        }
        return base.copy(
            isDarkMode = prefs.getBoolean("is_dark_mode", base.isDarkMode),
            followSystemTheme = prefs.getBoolean(P + "follow_system_theme", base.followSystemTheme),
            biometricEnabled = prefs.getBoolean(P + "biometric", base.biometricEnabled),
            lockTimeoutSeconds = prefs.getInt(P + "lock_timeout", base.lockTimeoutSeconds),
            screenBlurOnSwitch = screenBlur,
            antiScreenshotEnabled = antiScreenshot,
            lastSeenPrivacy = prefs.getString(P + "last_seen", base.lastSeenPrivacy) ?: base.lastSeenPrivacy,
            readReceiptsEnabled = prefs.getBoolean(P + "read_receipts", base.readReceiptsEnabled),
            disappearingMessagesDays = prefs.getInt(P + "disappearing_days", base.disappearingMessagesDays),
            enterIsSend = prefs.getBoolean(P + "enter_is_send", base.enterIsSend),
            mediaVisibilityInGallery = prefs.getBoolean(P + "media_in_gallery", base.mediaVisibilityInGallery),
            autoDownloadMedia = prefs.getString(P + "auto_download", base.autoDownloadMedia) ?: base.autoDownloadMedia,
            photoUploadQuality = prefs.getString(P + "photo_quality", base.photoUploadQuality) ?: base.photoUploadQuality,
            chatTheme = prefs.getString(P + "chat_theme", base.chatTheme) ?: base.chatTheme,
            customWallpaperUri = prefs.getString(P + "wallpaper_uri", base.customWallpaperUri) ?: base.customWallpaperUri,
            wallpaperOpacity = prefs.getFloat(P + "wallpaper_opacity", base.wallpaperOpacity),
            wallpaperBlur = prefs.getFloat(P + "wallpaper_blur", base.wallpaperBlur),
            fontFamilyPreference = prefs.getString(P + "font_family", base.fontFamilyPreference) ?: base.fontFamilyPreference,
            bubbleStyle = prefs.getString(P + "bubble_style", base.bubbleStyle) ?: base.bubbleStyle,
            fontSizeScale = prefs.getString(P + "font_size", base.fontSizeScale) ?: base.fontSizeScale,
            proximityP2PEnabled = prefs.getBoolean(P + "p2p_enabled", base.proximityP2PEnabled),
            autoSendOfflineQueue = prefs.getBoolean(P + "auto_send_queue", base.autoSendOfflineQueue),
            backendProvider = "supabase", // forcé : l'ancien mode Firebase est abandonné
            accentTheme = prefs.getString(P + "accent_theme", base.accentTheme) ?: base.accentTheme,
            bubbleCornerRadiusDp = prefs.getInt(P + "bubble_radius_dp", base.bubbleCornerRadiusDp).coerceIn(10, 26),
            showBubbleTails = prefs.getBoolean(P + "show_bubble_tails", base.showBubbleTails),
            chatWallpaperPattern = prefs.getString(P + "wallpaper_pattern", base.chatWallpaperPattern) ?: base.chatWallpaperPattern,
            navBarStyle = prefs.getString(P + "nav_bar_style", base.navBarStyle) ?: base.navBarStyle,
            uiDensity = prefs.getString(P + "ui_density", base.uiDensity) ?: base.uiDensity,
            showLoveDaysInHeader = prefs.getBoolean(P + "show_love_days", base.showLoveDaysInHeader),
        )
    }

    fun save(prefs: SharedPreferences, s: UserSettingsEntity) {
        prefs.edit()
            .putBoolean(KEY_FLAG_SECURE_MIGRATED_V2, true)
            .putBoolean("is_dark_mode", s.isDarkMode)
            .putBoolean(P + "follow_system_theme", s.followSystemTheme)
            .putBoolean(P + "biometric", s.biometricEnabled)
            .putInt(P + "lock_timeout", s.lockTimeoutSeconds)
            .putBoolean(P + "screen_blur", s.screenBlurOnSwitch)
            .putBoolean(P + "anti_screenshot", s.antiScreenshotEnabled)
            .putString(P + "last_seen", s.lastSeenPrivacy)
            .putBoolean(P + "read_receipts", s.readReceiptsEnabled)
            .putInt(P + "disappearing_days", s.disappearingMessagesDays)
            .putBoolean(P + "enter_is_send", s.enterIsSend)
            .putBoolean(P + "media_in_gallery", s.mediaVisibilityInGallery)
            .putString(P + "auto_download", s.autoDownloadMedia)
            .putString(P + "photo_quality", s.photoUploadQuality)
            .putString(P + "chat_theme", s.chatTheme)
            .putString(P + "wallpaper_uri", s.customWallpaperUri)
            .putFloat(P + "wallpaper_opacity", s.wallpaperOpacity)
            .putFloat(P + "wallpaper_blur", s.wallpaperBlur)
            .putString(P + "font_family", s.fontFamilyPreference)
            .putString(P + "bubble_style", s.bubbleStyle)
            .putString(P + "font_size", s.fontSizeScale)
            .putBoolean(P + "p2p_enabled", s.proximityP2PEnabled)
            .putBoolean(P + "auto_send_queue", s.autoSendOfflineQueue)
            .putString(P + "backend_provider", s.backendProvider)
            .putString(P + "accent_theme", s.accentTheme)
            .putInt(P + "bubble_radius_dp", s.bubbleCornerRadiusDp)
            .putBoolean(P + "show_bubble_tails", s.showBubbleTails)
            .putString(P + "wallpaper_pattern", s.chatWallpaperPattern)
            .putString(P + "nav_bar_style", s.navBarStyle)
            .putString(P + "ui_density", s.uiDensity)
            .putBoolean(P + "show_love_days", s.showLoveDaysInHeader)
            .apply()
    }

    /** Facteur de taille du texte appliqué à toute l'application. */
    fun fontScaleFactor(scale: String): Float = when (scale) {
        "small" -> 0.9f
        "large" -> 1.12f
        "xlarge" -> 1.25f
        else -> 1.0f
    }
}
