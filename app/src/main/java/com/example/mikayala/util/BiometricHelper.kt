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

    fun isBiometricAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val pm = context.packageManager
        val hasFingerprint = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        if (hasFingerprint) return true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bm = context.getSystemService(android.hardware.biometrics.BiometricManager::class.java)
            if (bm != null) {
                val canAuth = bm.canAuthenticate()
                return canAuth == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS ||
                        canAuth == android.hardware.biometrics.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
            }
        }
        return false
    }

    fun authenticate(
        context: Context,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!isBiometricAvailable(context)) {
                onError("Biométrie non disponible sur cet appareil")
                return
            }

            try {
                val executor = ContextCompat.getMainExecutor(context)
                val cancellationSignal = CancellationSignal()
                val prompt = BiometricPrompt.Builder(context)
                    .setTitle("Authentification Mikayala")
                    .setSubtitle("Espace Sécurisé")
                    .setDescription("Scannez votre empreinte digitale pour déverrouiller")
                    .setNegativeButton("Utiliser le code PIN", executor, DialogInterface.OnClickListener { _, _ ->
                        // Annulation par l'utilisateur : rester verrouillé
                    })
                    .build()

                prompt.authenticate(
                    cancellationSignal,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                            super.onAuthenticationSucceeded(result)
                            onSuccess()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            super.onAuthenticationError(errorCode, errString)
                            if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED &&
                                errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED) {
                                onError(errString?.toString() ?: "Authentification annulée")
                            }
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            onError("Empreinte non reconnue")
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("BiometricHelper", "Erreur lancement biométrique: ${e.message}", e)
                onError("Impossible de lancer l'authentification biométrique")
            }
        } else {
            onError("Biométrie non supportée sur cette version d'Android")
        }
    }
}
