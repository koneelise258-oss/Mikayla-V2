package com.example.mikayala.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

interface TokenStorage {
    fun getPendingToken(): String?
    fun savePendingToken(token: String?)
    fun getPendingUnregistrationToken(): String?
    fun savePendingUnregistrationToken(token: String?)
}

interface FirebaseTokenFetcher {
    suspend fun fetchToken(): String?
}

interface SupabasePushApi {
    fun getCurrentSessionUid(): String?
    suspend fun upsertDevicePushToken(fcmToken: String): Boolean
    suspend fun deleteDevicePushToken(fcmToken: String): Boolean
}

class FcmTokenSyncEngine(
    private val storage: TokenStorage,
    private val fetcher: FirebaseTokenFetcher,
    private val api: SupabasePushApi
) {
    private val isSyncing = AtomicBoolean(false)
    private val pendingSyncRequested = AtomicBoolean(false)

    suspend fun getOrFetchFcmToken(): String? = withContext(Dispatchers.IO) {
        val localPending = storage.getPendingToken()
        val freshToken = try { fetcher.fetchToken() } catch (_: Exception) { null }

        val selectedToken = when {
            !freshToken.isNullOrBlank() -> {
                storage.savePendingToken(freshToken)
                freshToken
            }
            !localPending.isNullOrBlank() -> {
                localPending
            }
            else -> null
        }
        return@withContext selectedToken
    }

    suspend fun syncPendingAndCurrentToken(): Boolean = withContext(Dispatchers.IO) {
        pendingSyncRequested.set(true)

        if (!isSyncing.compareAndSet(false, true)) {
            // Synchronisation déjà en cours. Attente de la fin de la boucle active pour ne pas perdre la demande.
            while (isSyncing.get()) {
                delay(50)
            }
            return@withContext storage.getPendingToken().isNullOrBlank()
        }

        var lastSuccess = false
        try {
            var passCount = 0
            while (pendingSyncRequested.getAndSet(false) && passCount < 5) {
                passCount++
                lastSuccess = executeSyncPass()
            }
        } finally {
            isSyncing.set(false)
        }
        return@withContext lastSuccess
    }

    private suspend fun executeSyncPass(): Boolean {
        // En premier lieu : ré-essayer la désinscription si un jeton de désinscription était en attente
        val pendingUnregister = storage.getPendingUnregistrationToken()
        if (!pendingUnregister.isNullOrBlank()) {
            val uid = api.getCurrentSessionUid()
            if (!uid.isNullOrBlank()) {
                val deleted = api.deleteDevicePushToken(pendingUnregister)
                if (deleted) {
                    storage.savePendingUnregistrationToken(null)
                }
            }
        }

        val token = getOrFetchFcmToken()
        if (token.isNullOrBlank()) {
            return false
        }

        val uid = api.getCurrentSessionUid()
        if (uid.isNullOrBlank()) {
            // Session absente : conserve le jeton localement en attente
            return false
        }

        val ok = api.upsertDevicePushToken(token)
        if (ok) {
            storage.savePendingToken(null)
            return true
        } else {
            // Échec réseau/serveur : conserve le jeton localement
            return false
        }
    }

    suspend fun unregisterDeviceTokenOnLogout(): Boolean = withContext(Dispatchers.IO) {
        val uid = api.getCurrentSessionUid()
        if (uid.isNullOrBlank()) {
            storage.savePendingToken(null)
            storage.savePendingUnregistrationToken(null)
            return@withContext true
        }

        val token = getOrFetchFcmToken()
        if (token.isNullOrBlank()) {
            storage.savePendingToken(null)
            storage.savePendingUnregistrationToken(null)
            return@withContext true
        }

        val success = api.deleteDevicePushToken(token)
        if (success) {
            storage.savePendingToken(null)
            storage.savePendingUnregistrationToken(null)
        } else {
            storage.savePendingUnregistrationToken(token)
        }
        return@withContext success
    }
}
