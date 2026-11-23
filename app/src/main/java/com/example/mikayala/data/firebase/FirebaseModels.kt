package com.example.mikayala.data.firebase

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class FirebaseUserProfile(
    @DocumentId val id: String = "",
    val uid: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarUrl: String = "",
    val moodEmoji: String = "🥰",
    val email: String = "",
    val fcmToken: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf<String, Any?>(
        "uid" to uid,
        "displayName" to displayName.trim().ifBlank { "Moi" }.take(60),
        "bio" to bio.take(160),
        "avatarUrl" to avatarUrl.take(1024),
        "moodEmoji" to moodEmoji.take(16),
        "email" to email.take(120),
        "fcmToken" to fcmToken.take(512),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class FirebasePairingCode(
    @DocumentId val id: String = "",
    val code: String = "",
    val coupleId: String = "",
    val creatorUid: String = "",
    val status: String = "waiting",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "code" to code,
        "coupleId" to coupleId,
        "creatorUid" to creatorUid,
        "status" to "waiting",
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FirebaseCoupleSpace(
    @DocumentId val id: String = "",
    val pairingCode: String = "",
    val status: String = "waiting",
    val partner1Id: String = "",
    val partner2Id: String = "",
    val memberUids: List<String> = emptyList(),
    val partner1Name: String = "",
    val partner2Name: String = "",
    val partner1Avatar: String = "",
    val partner2Avatar: String = "",
    val partner1Bio: String = "",
    val partner2Bio: String = "",
    val partner1Mood: String = "🥰",
    val partner2Mood: String = "💖",
    val partner1LastSeenMs: Long = 0L,
    val partner2LastSeenMs: Long = 0L,
    val partner1TypingAtMs: Long = 0L,
    val partner2TypingAtMs: Long = 0L,
    val partner1RecordingAtMs: Long = 0L,
    val partner2RecordingAtMs: Long = 0L,
    val anniversaryDate: Long = 0L,
    val loveQuote: String = "Chaque seconde à tes côtés est magique.",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf<String, Any?>(
        "pairingCode" to pairingCode,
        "status" to "waiting",
        "partner1Id" to partner1Id,
        "partner2Id" to "",
        "memberUids" to listOf(partner1Id),
        "partner1Name" to partner1Name.take(60),
        "partner2Name" to "",
        "partner1Avatar" to partner1Avatar.take(1024),
        "partner2Avatar" to "",
        "partner1Bio" to partner1Bio.take(160),
        "partner2Bio" to "",
        "partner1Mood" to partner1Mood.take(16),
        "partner2Mood" to "💖",
        "partner1LastSeenMs" to partner1LastSeenMs,
        "partner2LastSeenMs" to 0L,
        "partner1TypingAtMs" to 0L,
        "partner2TypingAtMs" to 0L,
        "partner1RecordingAtMs" to 0L,
        "partner2RecordingAtMs" to 0L,
        "anniversaryDate" to anniversaryDate,
        "loveQuote" to loveQuote.take(300),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class FirebaseMessage(
    @DocumentId val id: String = "",
    val coupleId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val memberUids: List<String> = emptyList(),
    val content: String = "",
    val type: String = "text",
    val mediaUrl: String? = null,
    val storagePath: String? = null,
    val duration: Long = 0L,
    val status: String = "sent",
    val deliveredAtMs: Long? = null,
    val readAtMs: Long? = null,
    val editedAtMs: Long? = null,
    @get:PropertyName("isViewOnce") @set:PropertyName("isViewOnce")
    var isViewOnce: Boolean = false,
    @get:PropertyName("isViewed") @set:PropertyName("isViewed")
    var isViewed: Boolean = false,
    @get:PropertyName("isPinned") @set:PropertyName("isPinned")
    var isPinned: Boolean = false,
    @get:PropertyName("isDeletedForEveryone") @set:PropertyName("isDeletedForEveryone")
    var isDeletedForEveryone: Boolean = false,
    val reactions: String = "{}",
    val replyToId: String? = null,
    val replyToSender: String? = null,
    val replyToContent: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf<String, Any?>(
        "coupleId" to coupleId,
        "senderId" to senderId,
        "receiverId" to receiverId.takeIf { it.isNotBlank() },
        "memberUids" to memberUids,
        "content" to content.take(4000),
        "type" to type.ifBlank { "text" }.take(32),
        "mediaUrl" to mediaUrl?.take(4096),
        "storagePath" to storagePath?.take(512),
        "duration" to duration.coerceAtLeast(0L),
        "status" to if (status in setOf("pending", "sent", "delivered", "read")) status else "sent",
        "deliveredAtMs" to deliveredAtMs,
        "readAtMs" to readAtMs,
        "editedAtMs" to editedAtMs,
        "isViewOnce" to isViewOnce,
        "isViewed" to isViewed,
        "isPinned" to isPinned,
        "isDeletedForEveryone" to isDeletedForEveryone,
        "reactions" to reactions.ifBlank { "{}" },
        "replyToId" to replyToId,
        "replyToSender" to replyToSender,
        "replyToContent" to replyToContent?.take(500),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class FirebaseVaultItem(
    @DocumentId val id: String = "",
    val coupleId: String = "",
    val memberUids: List<String> = emptyList(),
    val addedBy: String = "",
    val addedByName: String = "",
    val title: String = "",
    val type: String = "note",
    val category: String = "intime",
    val caption: String = "",
    val mediaUrl: String = "",
    val dateAddedMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf<String, Any?>(
        "coupleId" to coupleId,
        "memberUids" to memberUids,
        "addedBy" to addedBy,
        "addedByName" to addedByName.take(60),
        "title" to title.ifBlank { "Souvenir" }.take(120),
        "type" to if (type in setOf("photo", "video", "audio", "note", "letter")) type else "note",
        "category" to if (category in setOf("intime", "souvenirs", "projets", "sauvegardes")) category else "intime",
        "caption" to caption.take(1000),
        "mediaUrl" to mediaUrl.take(4096),
        "dateAddedMs" to dateAddedMs,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class FirebaseCoupleEvent(
    @DocumentId val id: String = "",
    val coupleId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val title: String = "",
    val dateMs: Long = 0L,
    val category: String = "Anniversaire",
    val note: String = "",
    @get:PropertyName("isRecurring") @set:PropertyName("isRecurring")
    var isRecurring: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf<String, Any?>(
        "coupleId" to coupleId,
        "memberUids" to memberUids,
        "createdBy" to createdBy,
        "title" to title.trim().ifBlank { "Rendez-vous" }.take(120),
        "dateMs" to dateMs,
        "category" to category.ifBlank { "Anniversaire" },
        "note" to note.take(1000),
        "isRecurring" to isRecurring,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class FirebaseSharedGameItem(
    @DocumentId val id: String = "",
    val coupleId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val kind: String = "coupon",
    val payloadJson: String = "{}",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "coupleId" to coupleId,
        "memberUids" to memberUids,
        "createdBy" to createdBy,
        "kind" to kind,
        "payloadJson" to payloadJson.take(8000),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FirebaseCallSignal(
    @DocumentId val id: String = "",
    val coupleId: String = "",
    val memberUids: List<String> = emptyList(),
    val callerUid: String = "",
    val callerName: String = "",
    @get:PropertyName("isVideo") @set:PropertyName("isVideo")
    var isVideo: Boolean = false,
    val status: String = "ringing",
    val durationSeconds: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "coupleId" to coupleId,
        "memberUids" to memberUids,
        "callerUid" to callerUid,
        "callerName" to callerName.take(60),
        "isVideo" to isVideo,
        "status" to if (status in setOf("ringing", "accepted", "declined", "cancelled", "ended")) status else "ringing",
        "durationSeconds" to durationSeconds.coerceAtLeast(0L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FirebasePushRequest(
    @DocumentId val id: String = "",
    val coupleId: String = "",
    val memberUids: List<String> = emptyList(),
    val senderId: String = "",
    val recipientId: String = "",
    val type: String = "chat_message",
    val title: String = "Mikayala",
    val body: String = "",
    val dataJson: String = "{}",
    val hidePreview: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        val ALLOWED_TYPES = setOf(
            "chat_message", "call", "missed_call", "audio", "image", "video",
            "vault_add", "game", "game_turn", "anniversary", "mood_update",
            "poll_created", "love_bomb", "calendar_event", "cycle",
            "call_declined", "call_cancelled", "call_ended"
        )
    }

    fun toCreateMap(): Map<String, Any> = mapOf(
        "coupleId" to coupleId,
        "memberUids" to memberUids,
        "senderId" to senderId,
        "recipientId" to recipientId,
        "type" to if (type in ALLOWED_TYPES) type else "chat_message",
        "title" to title.take(120),
        "body" to if (type == "cycle") "Vous avez une nouvelle notification" else body.take(500),
        "dataJson" to if (type == "cycle") "{}" else dataJson.take(4000),
        "hidePreview" to hidePreview,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
