package com.example.mikayala.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

interface TokenStorage {
    fun getPendingToken(): String?
    fun savePendingToken(token: String?)

    fun getPendingUnregistrationInfo(): Pair<String, String>? // Pair(token, ownerUid)
    fun savePendingUnregistrationInfo(token: String?, ownerUid: String?)

    fun getLastSyncedTokenInfo(): Pair<String, String>? // Pair(token, uid)
    fun saveLastSyncedTokenInfo(token: String?, uid: String?)
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
        val currentUid = api.getCurrentSessionUid()

        // 1. Traitement des désinscriptions en attente
        val unregInfo = storage.getPendingUnregistrationInfo()
        if (unregInfo != null) {
            val (unregToken, ownerUid) = unregInfo
            if (currentUid == ownerUid) {
                // L'utilisateur actuellement authentifié est le propriétaire légitime de la désinscription
                val deleted = api.deleteDevicePushToken(unregToken)
                if (deleted) {
                    storage.savePendingUnregistrationInfo(null, null)
                } else {
                    // Échec de désinscription. Ne pas tenter d'inscrire à nouveau ce jeton dans la même passe.
                    return false
                }
            } else {
                // Le compte actif est différent (ex: Utilisateur B connecté alors que la désinscription de l'Utilisateur A est en attente).
                // Ne JAMAIS supprimer ni attribuer le jeton de l'Utilisateur A sous la session de l'Utilisateur B !
                // Conserver les données de désinscription de l'Utilisateur A intactes pour lorsqu'il se reconnectera.
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

        // 2. Gestion du renouvellement ou vérification si déjà synchronisé pour currentUid
        val lastSynced = storage.getLastSyncedTokenInfo()
        if (lastSynced != null) {
            val (oldToken, oldUid) = lastSynced
            if (oldUid == currentUid) {
                if (oldToken != currentToken) {
                    // Renouvellement de jeton détecté pour cet utilisateur : supprimer l'ancien jeton
                    api.deleteDevicePushToken(oldToken)
                } else if (storage.getPendingToken().isNullOrBlank()) {
                    // Jeton déjà synchronisé avec succès pour l'utilisateur courant : pas besoin de ré-émettre une requête inutile
                    return true
                }
            }
        }

        // 3. Enregistrement / Upsert du jeton actuel pour currentUid
        val ok = api.upsertDevicePushToken(currentToken)
        if (ok) {
            storage.savePendingToken(null)
            storage.saveLastSyncedTokenInfo(currentToken, currentUid)
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
            storage.savePendingToken(null)
            storage.savePendingUnregistrationInfo(null, null)
            return@withContext true
        }

        val token = getOrFetchFcmToken()
        if (token.isNullOrBlank()) {
            storage.savePendingToken(null)
            storage.savePendingUnregistrationInfo(null, null)
            return@withContext true
        }

        // Tentative de suppression du jeton auprès de Supabase avec filtrage par user_id
        val success = api.deleteDevicePushToken(token)
        if (success) {
            storage.savePendingToken(null)
            storage.savePendingUnregistrationInfo(null, null)
            storage.saveLastSyncedTokenInfo(null, null)
        } else {
            // Échec : conserver de manière persistante (token, currentUid) pour ré-essayer lors de la reconnection de currentUid
            storage.savePendingUnregistrationInfo(token, currentUid)
        }
        return@withContext success
    }
}
