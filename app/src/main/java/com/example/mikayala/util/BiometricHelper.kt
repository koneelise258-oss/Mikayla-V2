package com.example.mikayala.util

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Biométrie via la bibliothèque officielle AndroidX : fonctionne dès Android 6 (empreinte),
 * y compris sur les téléphones Android 8 / Go (ex. Infinix Hot 8 Lite). L'ancienne version
 * utilisait l'API système réservée à Android 9+ et désactivait la biométrie en dessous.
 */
object BiometricHelper {

    // BIOMETRIC_WEAK inclut aussi les capteurs « forts » (empreinte) : le plus large possible
    private const val AUTHENTICATORS = BIOMETRIC_WEAK

    /** Vrai si le téléphone a un capteur utilisable (même si aucune empreinte n'est encore enregistrée). */
    fun isBiometricAvailable(context: Context): Boolean {
        return when (BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS,
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> true
            else -> false
        }
    }

    private fun findActivity(context: Context): FragmentActivity? {
        var c = context
        while (c is android.content.ContextWrapper) {
            if (c is FragmentActivity) return c
            c = c.baseContext
        }
        return null
    }

    /**
     * Déclenche l'invite d'authentification biométrique (empreinte, ou visage si le téléphone
     * le propose aux applications).
     */
    fun authenticate(
        context: Context,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val activity = findActivity(context)
        if (activity == null) {
            onError("Impossible de lancer la biométrie")
            return
        }

        when (BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Unit
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                onError("Aucune empreinte enregistrée : ajoutez-en une dans les paramètres du téléphone")
                return
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                onError("Biométrie non disponible sur ce téléphone")
                return
            }
            else -> {
                onError("Biométrie non disponible pour le moment")
                return
            }
        }

        try {
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    Log.d("BiometricHelper", "Authentification biométrique réussie !")
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    Log.d("BiometricHelper", "Code erreur biométrique: $errorCode ($errString)")
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> onError("Utilisation du code PIN")
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_CANCELED -> Unit
                        else -> onError(errString.toString().ifBlank { "Erreur d'authentification" })
                    }
                }

                override fun onAuthenticationFailed() {
                    onError("Biométrie non reconnue (empreinte / visage)")
                }
            })

            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Espace Sécurisé Mikayala 🔒")
                .setSubtitle("Déverrouillage Biométrique")
                .setDescription("Scannez votre empreinte digitale pour accéder à votre espace de couple.")
                .setNegativeButtonText("Utiliser le code PIN 🔢")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()

            prompt.authenticate(info)
        } catch (e: Exception) {
            Log.e("BiometricHelper", "Erreur lors du lancement de la biométrie: ${e.message}", e)
            onError("Impossible de lancer la biométrie")
        }
    }
}
