package com.example.mikayala.data.firebase

import android.content.Context
import android.util.Log
import com.example.mikayala.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Cloud Firestore repository for Mikayala Firebase Avant-Projet mode.
 * Connects strictly to the named database R.string.firestore_database_id (never (default)).
 */
class FirebaseCoupleRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: error("Vous devez d'abord vous connecter avec Google.")

    private fun <T> Flow<T>.unwrapFirestoreCancellation(op: OperationType, path: String): Flow<T> =
        catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            handleFirestoreError(root, op, path)
            throw root
        }

    // ---------------------------------------------------------
    // 1. User Profiles (/users/{uid})
    // ---------------------------------------------------------
    suspend fun upsertMyProfile(
        displayName: String,
        bio: String,
        avatarUrl: String,
        moodEmoji: String,
        email: String = auth.currentUser?.email.orEmpty(),
        fcmToken: String = ""
    ): Result<Unit> = try {
        val uid = requireUserId()
        val ref = db.collection("users").document(uid)
        val snap = ref.get().await()
        if (!snap.exists()) {
            val profile = FirebaseUserProfile(
                uid = uid,
                displayName = displayName.ifBlank { auth.currentUser?.displayName ?: "Moi" },
                bio = bio,
                avatarUrl = avatarUrl,
                moodEmoji = moodEmoji.ifBlank { "🥰" },
                email = email,
                fcmToken = fcmToken
            )
            ref.set(profile.toCreateMap()).await()
        } else {
            val updates = mutableMapOf<String, Any>(
                "displayName" to displayName.trim().ifBlank { "Moi" }.take(60),
                "bio" to bio.take(160),
                "moodEmoji" to moodEmoji.ifBlank { "🥰" }.take(16),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (avatarUrl.isNotBlank()) updates["avatarUrl"] = avatarUrl.take(1024)
            if (email.isNotBlank()) updates["email"] = email.take(120)
            if (fcmToken.isNotBlank()) updates["fcmToken"] = fcmToken.take(512)
            ref.update(updates).await()
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}")
        Result.failure(e)
    }

    suspend fun fetchMyProfile(): Result<FirebaseUserProfile?> = try {
        val uid = requireUserId()
        val snap = db.collection("users").document(uid).get().await()
        val profile = snap.toObject(
            FirebaseUserProfile::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        )
        Result.success(profile)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.GET, "users/${auth.currentUser?.uid}")
        Result.failure(e)
    }

    // ---------------------------------------------------------
    // 2. Couple Space Creation, Lookup & Joining
    // ---------------------------------------------------------
    suspend fun createCoupleSpace(
        pairingCode: String,
        partnerName: String,
        moodEmoji: String = "🥰",
        bio: String = "En ligne",
        avatarUrl: String = ""
    ): Result<FirebaseCoupleSpace> = try {
        val uid = requireUserId()
        val cleanCode = pairingCode.trim().uppercase()
        require(cleanCode.length in 4..20) { "Code de jumelage invalide (4 à 20 caractères)." }

        // Ensure creator user doc exists
        upsertMyProfile(
            displayName = partnerName.ifBlank { auth.currentUser?.displayName ?: "Partenaire 1" },
            bio = bio,
            avatarUrl = avatarUrl,
            moodEmoji = moodEmoji
        )

        val coupleId = "couple_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val space = FirebaseCoupleSpace(
            id = coupleId,
            pairingCode = cleanCode,
            status = "waiting",
            partner1Id = uid,
            partner2Id = "",
            memberUids = listOf(uid),
            partner1Name = partnerName.ifBlank { auth.currentUser?.displayName ?: "Partenaire 1" },
            partner1Avatar = avatarUrl,
            partner1Bio = bio,
            partner1Mood = moodEmoji,
            partner1LastSeenMs = System.currentTimeMillis(),
            anniversaryDate = System.currentTimeMillis()
        )
        val pairingDoc = FirebasePairingCode(
            code = cleanCode,
            coupleId = coupleId,
            creatorUid = uid,
            status = "waiting"
        )

        val coupleRef = db.collection("couple_spaces").document(coupleId)
        val codeRef = db.collection("pairing_codes").document(cleanCode)
        val batch = db.batch()
        batch.set(coupleRef, space.toCreateMap())
        batch.set(codeRef, pairingDoc.toCreateMap())
        batch.commit().await()

        Result.success(space)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, "couple_spaces")
        Result.failure(e)
    }

    suspend fun joinCoupleSpace(
        pairingCode: String,
        partnerName: String,
        moodEmoji: String = "💖",
        bio: String = "En ligne",
        avatarUrl: String = ""
    ): Result<FirebaseCoupleSpace> {
        return try {
        val uid = requireUserId()
        val cleanCode = pairingCode.trim().uppercase()
        require(cleanCode.length in 4..20) { "Code de jumelage invalide." }

        val codeSnap = db.collection("pairing_codes").document(cleanCode).get().await()
        if (!codeSnap.exists()) {
            return Result.failure(IllegalArgumentException("Code introuvable dans Firebase Avant-Projet."))
        }
        val codeObj = codeSnap.toObject(
            FirebasePairingCode::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        ) ?: return Result.failure(IllegalStateException("Code illisible."))

        val coupleRef = db.collection("couple_spaces").document(codeObj.coupleId)
        val spaceSnap = coupleRef.get().await()
        if (!spaceSnap.exists()) {
            return Result.failure(IllegalStateException("Espace de couple introuvable."))
        }
        val existing = spaceSnap.toObject(
            FirebaseCoupleSpace::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        ) ?: return Result.failure(IllegalStateException("Données du couple illisibles."))

        // If already a member (e.g., linking a second device/tablet with same Google account)
        if (uid in existing.memberUids) {
            return Result.success(existing)
        }
        if (existing.status != "waiting" || existing.partner2Id.isNotBlank()) {
            return Result.failure(IllegalStateException("Cet espace est déjà complet."))
        }

        val p2Display = partnerName.ifBlank { auth.currentUser?.displayName ?: "Partenaire 2" }
        upsertMyProfile(displayName = p2Display, bio = bio, avatarUrl = avatarUrl, moodEmoji = moodEmoji)

        val newMembers = listOf(existing.partner1Id, uid)
        val batch = db.batch()
        batch.update(
            coupleRef,
            mapOf(
                "status" to "paired",
                "partner2Id" to uid,
                "partner2Name" to p2Display.take(60),
                "partner2Avatar" to avatarUrl.take(1024),
                "partner2Bio" to bio.take(160),
                "partner2Mood" to moodEmoji.take(16),
                "memberUids" to newMembers,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            db.collection("pairing_codes").document(cleanCode),
            mapOf(
                "status" to "paired",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()

        val updated = existing.copy(
            status = "paired",
            partner2Id = uid,
            partner2Name = p2Display,
            partner2Avatar = avatarUrl,
            partner2Bio = bio,
            partner2Mood = moodEmoji,
            memberUids = newMembers
        )
        Result.success(updated)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "couple_spaces")
        Result.failure(e)
        }
    }

    suspend fun findMyCoupleSpaces(): Result<List<FirebaseCoupleSpace>> = try {
        val uid = requireUserId()
        val snap = db.collection("couple_spaces")
            .whereArrayContains("memberUids", uid)
            .get()
            .await()
        val list = snap.toObjects(
            FirebaseCoupleSpace::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        )
        Result.success(list)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.LIST, "couple_spaces")
        Result.failure(e)
    }

    suspend fun getCoupleSpaceByCode(pairingCode: String): Result<FirebaseCoupleSpace?> {
        return try {
            val cleanCode = pairingCode.trim().uppercase()
            if (cleanCode.length < 4) return Result.success(null)
            val codeSnap = db.collection("pairing_codes").document(cleanCode).get().await()
            val coupleId = codeSnap.getString("coupleId") ?: return Result.success(null)
            val spaceSnap = db.collection("couple_spaces").document(coupleId).get().await()
            val space = spaceSnap.toObject(
                FirebaseCoupleSpace::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(space)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "pairing_codes/$pairingCode")
            Result.failure(e)
        }
    }

    suspend fun updateCoupleProfileAndMeta(
        coupleId: String,
        fields: Map<String, Any>
    ): Result<Unit> = try {
        requireUserId()
        require(coupleId.isNotBlank()) { "coupleId manquant" }
        val payload = fields.toMutableMap()
        payload["updatedAt"] = FieldValue.serverTimestamp()
        db.collection("couple_spaces").document(coupleId).update(payload).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "couple_spaces/$coupleId")
        Result.failure(e)
    }

    fun observeCoupleSpace(coupleId: String): Flow<FirebaseCoupleSpace?> =
        db.collection("couple_spaces").document(coupleId)
            .snapshots()
            .map { snap ->
                if (!snap.exists()) null
                else snap.toObject(
                    FirebaseCoupleSpace::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .unwrapFirestoreCancellation(OperationType.GET, "couple_spaces/$coupleId")

    // ---------------------------------------------------------
    // 3. Messages (/couple_spaces/{coupleId}/messages/{messageId})
    // ---------------------------------------------------------
    fun observeMessages(coupleId: String, uid: String): Flow<List<FirebaseMessage>> =
        db.collection("couple_spaces").document(coupleId)
            .collection("messages")
            .whereArrayContains("memberUids", uid)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(200)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    FirebaseMessage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .unwrapFirestoreCancellation(OperationType.LIST, "couple_spaces/$coupleId/messages")

    suspend fun sendMessage(
        messageId: String,
        coupleId: String,
        memberUids: List<String>,
        receiverId: String,
        content: String,
        type: String,
        mediaUrl: String? = null,
        storagePath: String? = null,
        duration: Int = 0,
        isViewOnce: Boolean = false,
        replyToId: String? = null,
        replyToSender: String? = null,
        replyToContent: String? = null
    ): Result<Unit> = try {
        val uid = requireUserId()
        require(coupleId.isNotBlank()) { "coupleId manquant" }
        require(uid in memberUids) { "Utilisateur non membre de cet espace." }
        val model = FirebaseMessage(
            id = messageId,
            coupleId = coupleId,
            senderId = uid,
            receiverId = receiverId,
            memberUids = memberUids,
            content = content,
            type = type,
            mediaUrl = mediaUrl,
            storagePath = storagePath,
            duration = duration.toLong().coerceAtLeast(0L),
            status = "sent",
            isViewOnce = isViewOnce,
            replyToId = replyToId,
            replyToSender = replyToSender,
            replyToContent = replyToContent
        )
        db.collection("couple_spaces").document(coupleId)
            .collection("messages").document(messageId)
            .set(model.toCreateMap())
            .await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, "couple_spaces/$coupleId/messages/$messageId")
        Result.failure(e)
    }

    suspend fun updateMessageFields(
        coupleId: String,
        messageId: String,
        updates: Map<String, Any>
    ): Result<Unit> = try {
        requireUserId()
        val payload = updates.toMutableMap()
        payload["updatedAt"] = FieldValue.serverTimestamp()
        db.collection("couple_spaces").document(coupleId)
            .collection("messages").document(messageId)
            .update(payload)
            .await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "couple_spaces/$coupleId/messages/$messageId")
        Result.failure(e)
    }

    // ---------------------------------------------------------
    // 4. Shared Vault (/couple_spaces/{coupleId}/vault_items/{itemId})
    // ---------------------------------------------------------
    fun observeVaultItems(coupleId: String, uid: String): Flow<List<FirebaseVaultItem>> =
        db.collection("couple_spaces").document(coupleId)
            .collection("vault_items")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    FirebaseVaultItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .unwrapFirestoreCancellation(OperationType.LIST, "couple_spaces/$coupleId/vault_items")

    suspend fun addVaultItem(
        itemId: String,
        coupleId: String,
        memberUids: List<String>,
        addedByName: String,
        title: String,
        type: String,
        category: String,
        caption: String,
        mediaUrl: String
    ): Result<Unit> = try {
        val uid = requireUserId()
        require(uid in memberUids) { "Non membre." }
        val item = FirebaseVaultItem(
            id = itemId,
            coupleId = coupleId,
            memberUids = memberUids,
            addedBy = uid,
            addedByName = addedByName,
            title = title,
            type = type,
            category = category,
            caption = caption,
            mediaUrl = mediaUrl,
            dateAddedMs = System.currentTimeMillis()
        )
        db.collection("couple_spaces").document(coupleId)
            .collection("vault_items").document(itemId)
            .set(item.toCreateMap())
            .await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, "couple_spaces/$coupleId/vault_items/$itemId")
        Result.failure(e)
    }

    suspend fun deleteVaultItem(coupleId: String, itemId: String): Result<Unit> = try {
        requireUserId()
        db.collection("couple_spaces").document(coupleId)
            .collection("vault_items").document(itemId)
            .delete()
            .await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "couple_spaces/$coupleId/vault_items/$itemId")
        Result.failure(e)
    }

    // ---------------------------------------------------------
    // 5. Shared Calendar Events (/couple_spaces/{coupleId}/events/{eventId})
    // ---------------------------------------------------------
    fun observeEvents(coupleId: String, uid: String): Flow<List<FirebaseCoupleEvent>> =
        db.collection("couple_spaces").document(coupleId)
            .collection("events")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    FirebaseCoupleEvent::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .unwrapFirestoreCancellation(OperationType.LIST, "couple_spaces/$coupleId/events")

    suspend fun upsertEvent(
        eventId: String,
        coupleId: String,
        memberUids: List<String>,
        title: String,
        dateMs: Long,
        category: String,
        note: String,
        isCreate: Boolean
    ): Result<Unit> = try {
        val uid = requireUserId()
        val ref = db.collection("couple_spaces").document(coupleId)
            .collection("events").document(eventId)
        if (isCreate) {
            val ev = FirebaseCoupleEvent(
                id = eventId,
                coupleId = coupleId,
                memberUids = memberUids,
                createdBy = uid,
                title = title,
                dateMs = dateMs,
                category = category,
                note = note
            )
            ref.set(ev.toCreateMap()).await()
        } else {
            ref.update(
                mapOf(
                    "title" to title.trim().ifBlank { "Rendez-vous" }.take(120),
                    "dateMs" to dateMs,
                    "category" to category,
                    "note" to note.take(1000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "couple_spaces/$coupleId/events/$eventId")
        Result.failure(e)
    }

    suspend fun deleteEvent(coupleId: String, eventId: String): Result<Unit> = try {
        requireUserId()
        db.collection("couple_spaces").document(coupleId)
            .collection("events").document(eventId)
            .delete()
            .await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "couple_spaces/$coupleId/events/$eventId")
        Result.failure(e)
    }

    // ---------------------------------------------------------
    // 6. Shared Couple Games (/couple_spaces/{coupleId}/games/{gameId})
    // ---------------------------------------------------------
    fun observeGames(coupleId: String, uid: String): Flow<List<FirebaseSharedGameItem>> =
        db.collection("couple_spaces").document(coupleId)
            .collection("games")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    FirebaseSharedGameItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .unwrapFirestoreCancellation(OperationType.LIST, "couple_spaces/$coupleId/games")

    suspend fun upsertGameItem(
        gameId: String,
        coupleId: String,
        memberUids: List<String>,
        kind: String,
        payloadJson: String,
        isUpdate: Boolean
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            val ref = db.collection("couple_spaces").document(coupleId)
                .collection("games").document(gameId)
            if (isUpdate) {
                val snap = ref.get().await()
                if (snap.exists()) {
                    ref.update(
                        mapOf(
                            "payloadJson" to payloadJson.take(8000),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    return Result.success(Unit)
                }
            }
            val item = FirebaseSharedGameItem(
                id = gameId,
                coupleId = coupleId,
                memberUids = memberUids,
                createdBy = uid,
                kind = kind,
                payloadJson = payloadJson
            )
            ref.set(item.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "couple_spaces/$coupleId/games/$gameId")
            Result.failure(e)
        }
    }

    suspend fun deleteGameItem(coupleId: String, gameId: String): Result<Unit> = try {
        requireUserId()
        db.collection("couple_spaces").document(coupleId)
            .collection("games").document(gameId)
            .delete()
            .await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "couple_spaces/$coupleId/games/$gameId")
        Result.failure(e)
    }

    // ---------------------------------------------------------
    // 7. Call Signals (/couple_spaces/{coupleId}/call_signals/{signalId})
    // ---------------------------------------------------------
    fun observeCallSignals(coupleId: String, uid: String): Flow<List<FirebaseCallSignal>> =
        db.collection("couple_spaces").document(coupleId)
            .collection("call_signals")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    FirebaseCallSignal::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .unwrapFirestoreCancellation(OperationType.LIST, "couple_spaces/$coupleId/call_signals")

    suspend fun createCallSignal(
        signalId: String,
        coupleId: String,
        memberUids: List<String>,
        callerName: String,
        isVideo: Boolean
    ): Result<Unit> = try {
        val uid = requireUserId()
        val signal = FirebaseCallSignal(
            id = signalId,
            coupleId = coupleId,
            memberUids = memberUids,
            callerUid = uid,
            callerName = callerName,
            isVideo = isVideo,
            status = "ringing"
        )
        db.collection("couple_spaces").document(coupleId)
            .collection("call_signals").document(signalId)
            .set(signal.toCreateMap())
            .await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, "couple_spaces/$coupleId/call_signals/$signalId")
        Result.failure(e)
    }

    suspend fun updateCallSignalStatus(
        coupleId: String,
        signalId: String,
        status: String,
        durationSeconds: Int = 0
    ): Result<Unit> = try {
        requireUserId()
        db.collection("couple_spaces").document(coupleId)
            .collection("call_signals").document(signalId)
            .update(
                mapOf(
                    "status" to status,
                    "durationSeconds" to durationSeconds.toLong().coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "couple_spaces/$coupleId/call_signals/$signalId")
        Result.failure(e)
    }
}
