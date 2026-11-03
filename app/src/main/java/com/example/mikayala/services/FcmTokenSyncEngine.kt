package com.example.mikayala.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

interface TokenStorage {
    fun getPendingToken(): String?
    fun savePendingToken(token: String?)

    // Support multi-comptes pour les désinscriptions en attente
    fun getPendingUnregistrations(): List<Pair<String, String>> // Liste de Pair(token, ownerUid)
    fun addPendingUnregistration(token: String, ownerUid: String)
    fun removePendingUnregistration(token: String, ownerUid: String)

    // Stockage persistant par utilisateur pour le dernier jeton synchronisé
    fun getLastSyncedTokenForUser(uid: String): String?
    fun saveLastSyncedTokenForUser(uid: String, token: String?)
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
    private val syncMutex = Mutex()
    private val pendingSyncRequested = AtomicBoolean(false)

    companion object {
        @Volatile
        private var instance: FcmTokenSyncEngine? = null

        fun getInstance(
            storage: TokenStorage,
            fetcher: FirebaseTokenFetcher,
            api: SupabasePushApi
        ): FcmTokenSyncEngine {
            return instance ?: synchronized(this) {
                instance ?: FcmTokenSyncEngine(storage, fetcher, api).also { instance = it }
            }
        }

        fun resetInstance() {
            synchronized(this) {
                instance = null
            }
        }
    }

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

        return@withContext syncMutex.withLock {
            var lastSuccess = false
            while (pendingSyncRequested.getAndSet(false)) {
                lastSuccess = executeSyncPass()
            }
            lastSuccess
        }
    }

    private suspend fun executeSyncPass(): Boolean {
        val currentUid = api.getCurrentSessionUid()

        // 1. Traitement des désinscriptions en attente UNIQUEMENT pour le compte actuellement authentifié
        if (!currentUid.isNullOrBlank()) {
            val pendingUnregsForUser = storage.getPendingUnregistrations().filter { it.second == currentUid }
            for ((unregToken, ownerUid) in pendingUnregsForUser) {
                val deleted = api.deleteDevicePushToken(unregToken)
                if (deleted) {
                    storage.removePendingUnregistration(unregToken, ownerUid)
                } else {
                    // Échec de désinscription pour l'utilisateur courant : suspendre la passe, ne pas ré-inscrire ce jeton
                    return false
                }
            }
        }

        if (currentUid.isNullOrBlank()) {
            // Aucune session active : récupérer le jeton et le conserver localement en attente
            getOrFetchFcmToken()
            return false
        }

        val freshToken = try { fetcher.fetchToken() } catch (_: Exception) { null }
        val localPending = storage.getPendingToken()
        val currentToken = freshToken?.takeIf { it.isNotBlank() } ?: localPending?.takeIf { it.isNotBlank() }

        if (currentToken.isNullOrBlank()) {
            return false
        }

        // 2. Gestion du renouvellement de jeton par utilisateur
        val oldSyncedToken = storage.getLastSyncedTokenForUser(currentUid)
        if (!oldSyncedToken.isNullOrBlank()) {
            if (oldSyncedToken != currentToken) {
                // Renouvellement de jeton pour le compte actif : désinscrire l'ancien jeton
                val deleted = api.deleteDevicePushToken(oldSyncedToken)
                if (!deleted) {
                    storage.addPendingUnregistration(oldSyncedToken, currentUid)
                }
            } else if (storage.getPendingToken().isNullOrBlank()) {
                // Jeton déjà synchronisé et confirmé pour le compte actif
                return true
            }
        }

        // 3. Inscription / Upsert du jeton actuel pour le compte actif
        val ok = api.upsertDevicePushToken(currentToken)
        if (ok) {
            storage.savePendingToken(null)
            storage.saveLastSyncedTokenForUser(currentUid, currentToken)
            return true
        } else {
            // Échec réseau/serveur : conserver le jeton localement en attente
            storage.savePendingToken(currentToken)
            return false
        }
    }

    suspend fun unregisterDeviceTokenOnLogout(): Boolean = withContext(Dispatchers.IO) {
        val currentUid = api.getCurrentSessionUid()
        if (currentUid.isNullOrBlank()) {
            // Aucune session active : NE JAMAIS effacer les désinscriptions en attente !
            return@withContext false
        }

        val currentToken = getOrFetchFcmToken()
        val lastSyncedToken = storage.getLastSyncedTokenForUser(currentUid)

        val tokensToUnregister = mutableSetOf<String>()
        if (!currentToken.isNullOrBlank()) tokensToUnregister.add(currentToken)
        if (!lastSyncedToken.isNullOrBlank()) tokensToUnregister.add(lastSyncedToken)

        val pendingForUser = storage.getPendingUnregistrations().filter { it.second == currentUid }.map { it.first }
        tokensToUnregister.addAll(pendingForUser)

        if (tokensToUnregister.isEmpty()) {
            storage.saveLastSyncedTokenForUser(currentUid, null)
            return@withContext true
        }

        var allSucceeded = true
        for (token in tokensToUnregister) {
            val deleted = api.deleteDevicePushToken(token)
            if (deleted) {
                storage.removePendingUnregistration(token, currentUid)
            } else {
                storage.addPendingUnregistration(token, currentUid)
                allSucceeded = false
            }
        }

        if (allSucceeded) {
            if (!currentToken.isNullOrBlank() && storage.getPendingToken() == currentToken) {
                storage.savePendingToken(null)
            }
            storage.saveLastSyncedTokenForUser(currentUid, null)
            return@withContext true
        } else {
            return@withContext false
        }
    }
}
