package com.example.mikayala.services

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.mikayala.data.SupabaseService
import com.example.mikayala.data.firebase.FirebaseService
import java.util.concurrent.TimeUnit

/**
 * Accusé « reçu » (2e coche) qui survit à la fermeture de l'application.
 *
 * Quand l'application est fermée, Android la réveille quelques secondes pour la notification :
 * si la session n'a pas encore été renouvelée ou si le réseau est lent, l'accusé échouait et
 * n'était jamais renvoyé. Ici, WorkManager réessaie automatiquement dès que le réseau est là.
 */
class DeliveryAckWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    companion object {
        private const val KEY_COUPLE = "coupleId"
        private const val KEY_MESSAGE = "messageId"
        private const val KEY_FIREBASE = "useFirebase"
        private const val MAX_ATTEMPTS = 8

        /** Envoie l'accusé ; true si le serveur l'a accepté. */
        suspend fun markDelivered(context: Context, coupleId: String, messageId: String?, useFirebase: Boolean): Boolean {
            return if (useFirebase) {
                if (messageId.isNullOrBlank()) return true // rien de précis à confirmer
                FirebaseService.getInstance(context.applicationContext)
                    .markMessagesDeliveredIds(coupleId, listOf(messageId))
            } else {
                val svc = SupabaseService.getInstance()
                if (messageId.isNullOrBlank()) svc.markMessagesDelivered(coupleId)
                else svc.markMessagesDeliveredIds(coupleId, listOf(messageId))
            }
        }

        fun enqueue(context: Context, coupleId: String, messageId: String?, useFirebase: Boolean) {
            try {
                val request = OneTimeWorkRequestBuilder<DeliveryAckWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                    .setInputData(
                        workDataOf(
                            KEY_COUPLE to coupleId,
                            KEY_MESSAGE to (messageId ?: ""),
                            KEY_FIREBASE to useFirebase
                        )
                    )
                    .build()
                // Un seul accusé en attente par message (ou par couple si l'id est absent)
                val uniqueName = "delivery_ack_" + (messageId?.takeIf { it.isNotBlank() } ?: coupleId)
                WorkManager.getInstance(context.applicationContext)
                    .enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request)
            } catch (e: Exception) {
                Log.w("FCM_MSG", "Impossible de programmer l'accusé de réception: ${e.message}")
            }
        }
    }

    override suspend fun doWork(): Result {
        val coupleId = inputData.getString(KEY_COUPLE).orEmpty()
        if (coupleId.isBlank()) return Result.success()
        val messageId = inputData.getString(KEY_MESSAGE)?.takeIf { it.isNotBlank() }
        val useFirebase = inputData.getBoolean(KEY_FIREBASE, false)

        val sessionReady = try {
            if (useFirebase) FirebaseService.getInstance(applicationContext).awaitSessionReady(10_000L)
            else SupabaseService.getInstance().awaitSessionReady(15_000L)
        } catch (_: Exception) { false }

        val ok = sessionReady && try {
            markDelivered(applicationContext, coupleId, messageId, useFirebase)
        } catch (e: Exception) {
            Log.w("FCM_MSG", "Accusé de réception (nouvel essai) échoué: ${e.message}")
            false
        }

        return when {
            ok -> Result.success()
            runAttemptCount + 1 >= MAX_ATTEMPTS -> Result.failure()
            else -> Result.retry()
        }
    }
}
