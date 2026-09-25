package com.example.mikayala.util

import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import androidx.core.content.ContextCompat

object BiometricHelper {

    /**
     * Vérifie si la biométrie (empreinte digitale ou reconnaissance faciale) est disponible.
     */
    fun isBiometricAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val pm = context.packageManager
        val hasFingerprint = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        val hasFace = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pm.hasSystemFeature(PackageManager.FEATURE_FACE) || pm.hasSystemFeature("android.hardware.biometrics.face")
        } else false

        if (hasFingerprint || hasFace) return true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bm = context.getSystemService(android.hardware.biometrics.BiometricManager::class.java)
            if (bm != null) {
                val canAuth = bm.canAuthenticate(
                    android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK
                )
                return canAuth == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS ||
                        canAuth == android.hardware.biometrics.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
            }
        }
        return true // Fallback sur appareils Android compatibles
    }

    private fun findActivity(context: Context): android.app.Activity? {
        var c = context
        while (c is android.content.ContextWrapper) {
            if (c is android.app.Activity) return c
            c = c.baseContext
        }
        return null
    }

    /**
     * Déclenche l'invite d'authentification biométrique (Empreinte & Reconnaissance Faciale).
     */
    fun authenticate(
        context: Context,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val activityContext = findActivity(context) ?: context
                val executor = ContextCompat.getMainExecutor(activityContext)
                val cancellationSignal = CancellationSignal()

                val builder = BiometricPrompt.Builder(activityContext)
                    .setTitle("Espace Sécurisé Mikayala 🔒")
                    .setSubtitle("Déverrouillage Biométrique")
                    .setDescription("Scannez votre empreinte digitale ou utilisez la reconnaissance faciale pour accéder à votre espace de couple.")
                    .setNegativeButton("Utiliser le code PIN 🔢", executor, DialogInterface.OnClickListener { _, _ ->
                        onError("Utilisation du code PIN")
                    })

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    builder.setAllowedAuthenticators(
                        android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK
                    )
                }

                val prompt = builder.build()

                prompt.authenticate(
                    cancellationSignal,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                            super.onAuthenticationSucceeded(result)
                            Log.d("BiometricHelper", "Authentification biométrique réussie !")
                            onSuccess()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            super.onAuthenticationError(errorCode, errString)
                            Log.d("BiometricHelper", "Code erreur biométrique: $errorCode ($errString)")
                            if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED &&
                                errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED &&
                                errorCode != 13) {
                                onError(errString?.toString() ?: "Erreur d'authentification")
                            }
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            onError("Biométrie non reconnue (empreinte / visage)")
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("BiometricHelper", "Erreur lors du lancement de la biométrie: ${e.message}", e)
                onError("Impossible de lancer la biométrie")
            }
        } else {
            onError("Biométrie non supportée sur cette version d'Android")
        }
    }
}
