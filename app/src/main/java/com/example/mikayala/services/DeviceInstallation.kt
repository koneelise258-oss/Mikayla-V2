package com.example.mikayala.services

import android.content.Context
import java.security.SecureRandom

/**
 * Secret propre à CETTE installation de l'application (32 octets aléatoires, jamais envoyé ailleurs
 * qu'à la fonction register_device_push_token, qui n'en garde qu'une empreinte SHA-256).
 * Il prouve que l'appel vient bien de l'appareil qui possède le jeton FCM : connaître le jeton
 * d'un autre téléphone ne suffit plus pour se l'attribuer.
 * Il disparaît à la désinstallation (nouvelle installation = nouvel appareil).
 */
object DeviceInstallation {
    private const val PREFS = "mikayala_push_prefs"
    private const val KEY = "installation_secret"

    @Synchronized
    fun secret(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.takeIf { it.length == 64 }?.let { return it }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val hex = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString(KEY, hex).commit()
        return hex
    }
}
