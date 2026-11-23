package com.example.mikayala.data.firebase

import android.content.Context
import android.util.Log
import com.example.mikayala.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Service Firebase unifié (backend principal de Mikayala).
 * Remplace toutes les fonctions exécutées par SupabaseService :
 * - Session Google / Firebase Auth
 * - Profils utilisateur & jeton FCM (/users/{uid})
 * - Espace couple, codes de jumelage, présence, écriture & enregistrement (/couple_spaces/{coupleId})
 * - Messages temps réel, pagination, accusés de réception/lecture (/messages)
 * - Médias chiffrés/privés (Firebase Storage : messages-media/, avatars/)
 * - Coffre partagé, Calendrier, Jeux, Appels temps réel & file d'envoi push FCM (/push_requests)
 */
class FirebaseService private constructor(appContext: Context) {

    private val context: Context = appContext.applicationContext
    val auth: FirebaseAuth = Firebase.auth
    val db: FirebaseFirestore = FirebaseFirestore.getInstance(
        context.getString(R.string.firestore_database_id)
    )
    val storage: FirebaseStorage = Firebase.storage
    val coupleRepository: FirebaseCoupleRepository = FirebaseCoupleRepository(db, auth)

    private val downloadUrlCache = ConcurrentHashMap<String, String>()
    @Volatile var lastStorageError: String? = null
    @Volatile var storageUnavailable: Boolean = false

    companion object {
        private const val TAG = "FirebaseService"
        private const val PREFIX_MESSAGES_MEDIA = "messages-media/"
        private const val PREFIX_AVATARS = "avatars/"

        @Volatile
        private var instance: FirebaseService? = null

        fun getInstance(context: Context): FirebaseService =
            instance ?: synchronized(this) {
                instance ?: FirebaseService(context.applicationContext).also { instance = it }
            }

        fun normalizeMessageStoragePath(storagePath: String): String {
            val clean = storagePath.trim().removePrefix("msgmedia:").removePrefix("/")
            return if (clean.startsWith(PREFIX_MESSAGES_MEDIA)) clean else "$PREFIX_MESSAGES_MEDIA$clean"
        }

        fun storageErrorMessage(error: Throwable): String {
            val code = (error as? StorageException)?.errorCode
            return when (code) {
                StorageException.ERROR_BUCKET_NOT_FOUND,
                StorageException.ERROR_PROJECT_NOT_FOUND ->
                    "Firebase Storage non activé dans la console Firebase."
                StorageException.ERROR_NOT_AUTHORIZED,
                StorageException.ERROR_NOT_AUTHENTICATED ->
                    "Accès Firebase Storage refusé : vérifiez les règles Storage."
                StorageException.ERROR_QUOTA_EXCEEDED ->
                    "Quota Firebase Storage dépassé."
                else -> error.localizedMessage ?: "Erreur Firebase Storage."
            }
        }
    }

    // ---------------------------------------------------------
    // 1. Auth & Session
    // ---------------------------------------------------------
    fun getCurrentSessionUid(): String? = auth.currentUser?.uid?.takeIf { it.isNotBlank() }

    fun hasActiveSession(): Boolean = getCurrentSessionUid() != null

    suspend fun getCurrentIdToken(forceRefresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: return@withContext null
        try {
            user.getIdToken(forceRefresh).await()?.token?.takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "getCurrentIdToken failed: ${e.message}")
            null
        }
    }

    suspend fun awaitSessionReady(timeoutMs: Long = 2_500L): Boolean {
        if (hasActiveSession()) return true
        return withTimeoutOrNull(timeoutMs) {
            callbackFlow {
                val listener = FirebaseAuth.AuthStateListener { fbAuth ->
                    trySend(fbAuth.currentUser != null)
                }
                auth.addAuthStateListener(listener)
                awaitClose { auth.removeAuthStateListener(listener) }
            }.first { it }
        } ?: hasActiveSession()
    }

    // ---------------------------------------------------------
    // 2. FCM Device Token Sync (/users/{uid}.fcmToken)
    // ---------------------------------------------------------
    suspend fun upsertDevicePushToken(fcmToken: String): Boolean = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: return@withContext false
        val uid = user.uid
        if (uid.isBlank() || fcmToken.isBlank()) return@withContext false
        try {
            val ref = db.collection("users").document(uid)
            val snap = ref.get().await()
            if (!snap.exists()) {
                val profile = FirebaseUserProfile(
                    uid = uid,
                    displayName = user.displayName.orEmpty().ifBlank { "Moi" },
                    email = user.email.orEmpty(),
                    avatarUrl = user.photoUrl?.toString().orEmpty(),
                    fcmToken = fcmToken
                )
                ref.set(profile.toCreateMap()).await()
            } else {
                ref.update(
                    mapOf(
                        "fcmToken" to fcmToken.take(512),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            false
        }
    }

    suspend fun deleteDevicePushToken(@Suppress("UNUSED_PARAMETER") fcmToken: String): Boolean =
        withContext(Dispatchers.IO) {
            val uid = getCurrentSessionUid() ?: return@withContext false
            try {
                val ref = db.collection("users").document(uid)
                val snap = ref.get().await()
                if (!snap.exists()) return@withContext true
                ref.update(
                    mapOf(
                        "fcmToken" to "",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
                false
            }
        }

    // ---------------------------------------------------------
    // 3. Firebase Storage (Chat Media, Vault Media & Avatars)
    // ---------------------------------------------------------
    suspend fun uploadMessageMedia(
        storagePath: String,
        bytes: ByteArray,
        contentType: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!hasActiveSession()) {
            return@withContext Result.failure(IllegalStateException("Session Firebase requise pour envoyer un média."))
        }
        val objectPath = normalizeMessageStoragePath(storagePath)
        try {
            lastStorageError = null
            val ref = storage.reference.child(objectPath)
            val metadata = StorageMetadata.Builder()
                .setContentType(contentType.ifBlank { "application/octet-stream" })
                .build()
            ref.putBytes(bytes, metadata).await()
            val url = ref.downloadUrl.await().toString()
            downloadUrlCache[storagePath] = url
            downloadUrlCache[objectPath] = url
            Result.success(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val code = (e as? StorageException)?.errorCode
            if (code == StorageException.ERROR_BUCKET_NOT_FOUND || code == StorageException.ERROR_PROJECT_NOT_FOUND) {
                storageUnavailable = true
            }
            val msg = storageErrorMessage(e)
            lastStorageError = msg
            Log.e(TAG, "uploadMessageMedia failed ($objectPath): $msg", e)
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun getDownloadUrl(storagePath: String): String? = withContext(Dispatchers.IO) {
        if (storagePath.isBlank()) return@withContext null
        downloadUrlCache[storagePath]?.let { return@withContext it }
        val objectPath = normalizeMessageStoragePath(storagePath)
        downloadUrlCache[objectPath]?.let { return@withContext it }
        try {
            val url = storage.reference.child(objectPath).downloadUrl.await().toString()
            downloadUrlCache[storagePath] = url
            downloadUrlCache[objectPath] = url
            url
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "getDownloadUrl failed ($objectPath): ${e.message}")
            null
        }
    }

    suspend fun deleteMessageMedia(storagePath: String): Boolean = withContext(Dispatchers.IO) {
        if (storagePath.isBlank()) return@withContext false
        val objectPath = normalizeMessageStoragePath(storagePath)
        try {
            storage.reference.child(objectPath).delete().await()
            downloadUrlCache.remove(storagePath)
            downloadUrlCache.remove(objectPath)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "deleteMessageMedia failed ($objectPath): ${e.message}")
            false
        }
    }

    suspend fun uploadAvatar(
        userId: String,
        avatarUuid: String,
        bytes: ByteArray
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext Result.failure(IllegalStateException("UID manquant."))
        val objectPath = "$PREFIX_AVATARS$userId/$avatarUuid.webp"
        try {
            lastStorageError = null
            val ref = storage.reference.child(objectPath)
            val metadata = StorageMetadata.Builder().setContentType("image/webp").build()
            ref.putBytes(bytes, metadata).await()
            val url = ref.downloadUrl.await().toString()
            Result.success(objectPath to url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val code = (e as? StorageException)?.errorCode
            if (code == StorageException.ERROR_BUCKET_NOT_FOUND || code == StorageException.ERROR_PROJECT_NOT_FOUND) {
                storageUnavailable = true
            }
            val msg = storageErrorMessage(e)
            lastStorageError = msg
            Log.e(TAG, "uploadAvatar failed ($objectPath): $msg", e)
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun deleteAvatarByUrl(avatarUrl: String): Boolean = withContext(Dispatchers.IO) {
        if (!avatarUrl.contains("firebasestorage.googleapis.com")) return@withContext false
        try {
            storage.getReferenceFromUrl(avatarUrl).delete().await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "deleteAvatarByUrl skipped: ${e.message}")
            false
        }
    }

    // ---------------------------------------------------------
    // 4. Realtime Presence, Typing & Recording
    // ---------------------------------------------------------
    suspend fun touchPresence(
        coupleId: String,
        isPartner1: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (coupleId.isBlank() || !hasActiveSession()) return false
        val key = if (isPartner1) "partner1LastSeenMs" else "partner2LastSeenMs"
        return coupleRepository.updateCoupleProfileAndMeta(coupleId, mapOf(key to nowMs)).isSuccess
    }

    suspend fun setTypingState(coupleId: String, isPartner1: Boolean, isTyping: Boolean): Boolean {
        if (coupleId.isBlank() || !hasActiveSession()) return false
        val key = if (isPartner1) "partner1TypingAtMs" else "partner2TypingAtMs"
        val value = if (isTyping) System.currentTimeMillis() else 0L
        return coupleRepository.updateCoupleProfileAndMeta(coupleId, mapOf(key to value)).isSuccess
    }

    suspend fun setRecordingState(coupleId: String, isPartner1: Boolean, isRecording: Boolean): Boolean {
        if (coupleId.isBlank() || !hasActiveSession()) return false
        val key = if (isPartner1) "partner1RecordingAtMs" else "partner2RecordingAtMs"
        val value = if (isRecording) System.currentTimeMillis() else 0L
        return coupleRepository.updateCoupleProfileAndMeta(coupleId, mapOf(key to value)).isSuccess
    }

    // ---------------------------------------------------------
    // 5. Messages Pagination & Batch Read/Delivery Receipts
    // ---------------------------------------------------------
    suspend fun loadOlderMessages(
        coupleId: String,
        uid: String,
        beforeCreatedAtMs: Long,
        limit: Int = 100
    ): Result<List<FirebaseMessage>> = withContext(Dispatchers.IO) {
        try {
            val snap = db.collection("couple_spaces").document(coupleId)
                .collection("messages")
                .whereArrayContains("memberUids", uid)
                .whereLessThan("createdAt", Timestamp(Date(beforeCreatedAtMs)))
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()
            val list = snap.toObjects(
                FirebaseMessage::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).reversed()
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "couple_spaces/$coupleId/messages")
            Result.failure(e)
        }
    }

    suspend fun markMessagesDeliveredIds(coupleId: String, messageIds: List<String>): Boolean =
        batchUpdateMessageReceipt(coupleId, messageIds, isRead = false)

    suspend fun markMessagesReadIds(coupleId: String, messageIds: List<String>): Boolean =
        batchUpdateMessageReceipt(coupleId, messageIds, isRead = true)

    private suspend fun batchUpdateMessageReceipt(
        coupleId: String,
        messageIds: List<String>,
        isRead: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        if (coupleId.isBlank() || messageIds.isEmpty() || !hasActiveSession()) return@withContext false
        val now = System.currentTimeMillis()
        try {
            val messagesCol = db.collection("couple_spaces").document(coupleId).collection("messages")
            for (chunk in messageIds.distinct().chunked(400)) {
                val batch = db.batch()
                for (id in chunk) {
                    val fields = if (isRead) {
                        mapOf(
                            "status" to "read",
                            "readAtMs" to now,
                            "deliveredAtMs" to now,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    } else {
                        mapOf(
                            "status" to "delivered",
                            "deliveredAtMs" to now,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    }
                    batch.update(messagesCol.document(id), fields)
                }
                batch.commit().await()
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "couple_spaces/$coupleId/messages")
            false
        }
    }

    // ---------------------------------------------------------
    // 6. Push Request Queue (/couple_spaces/{coupleId}/push_requests)
    // ---------------------------------------------------------
    suspend fun sendFcmPush(
        coupleId: String,
        memberUids: List<String>,
        recipientId: String,
        type: String,
        title: String = "Mikayala",
        body: String = "",
        extraData: Map<String, String> = emptyMap(),
        hidePreview: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val senderId = getCurrentSessionUid() ?: return@withContext false
        if (coupleId.isBlank() || recipientId.isBlank() || senderId == recipientId) return@withContext false
        if (senderId !in memberUids || recipientId !in memberUids) return@withContext false

        val requestId = "push_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val dataJson = try {
            JSONObject().apply {
                for ((k, v) in extraData) put(k, v)
            }.toString()
        } catch (_: Exception) {
            "{}"
        }

        val req = FirebasePushRequest(
            id = requestId,
            coupleId = coupleId,
            memberUids = memberUids,
            senderId = senderId,
            recipientId = recipientId,
            type = type,
            title = title,
            body = body,
            dataJson = dataJson,
            hidePreview = hidePreview
        )
        try {
            db.collection("couple_spaces").document(coupleId)
                .collection("push_requests").document(requestId)
                .set(req.toCreateMap())
                .await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "couple_spaces/$coupleId/push_requests/$requestId")
            false
        }
    }
}
