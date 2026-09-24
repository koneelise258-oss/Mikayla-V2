package com.example.mikayala.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class CoupleSpaceEntity(
    val id: String = "",
    val pairingCode: String = "",
    val status: String = "none", // "none", "waiting", "paired"
    val partner1Id: String = "",
    val partner2Id: String = "",
    val partner1Name: String = "",
    val partner2Name: String = "",
    val partner1Avatar: String = "",
    val partner2Avatar: String = "",
    val partner1Status: String = "En ligne",
    val partner2Status: String = "En ligne",
    val anniversaryDate: Long = System.currentTimeMillis(),
    val loveQuote: String = "Chaque seconde à tes côtés est magique.",
    val themePreference: String = "twilight",
    val createdAt: Long = System.currentTimeMillis()
) {
    val isPaired: Boolean
        get() = (status == "paired") &&
                id.isNotBlank() && id != "couple_main" &&
                partner1Id.isNotBlank() && partner1Id != "null" && partner1Id != "p1" &&
                partner2Id.isNotBlank() && partner2Id != "null" && partner2Id != "p2"

    val isActive: Boolean
        get() = isPaired
}

@Immutable
data class CoupleSummaryItem(
    val coupleId: String,
    val pairingCode: String,
    val status: String,
    val partnerId: String,
    val partnerName: String,
    val user1Id: String,
    val user2Id: String,
    val createdAt: String = "",
    val isPaired: Boolean = (status == "paired")
)

@Immutable
data class LoveMilestoneEntity(
    val id: String,
    val title: String,
    val daysTarget: Long,
    val icon: String = "🏆",
    val isUnlocked: Boolean = false
)

@Immutable
data class LoveCouponEntity(
    val id: String,
    val title: String,
    val description: String,
    val icon: String = "🎟️",
    val isRedeemed: Boolean = false,
    val category: String = "Romance"
)

@Immutable
data class CoupleBucketItemEntity(
    val id: String,
    val title: String,
    val isCompleted: Boolean = false,
    val category: String = "Romantique",
    val isSecretGift: Boolean = false
)

@Immutable
data class LoveCapsuleEntity(
    val id: String,
    val title: String,
    val message: String,
    val senderId: String = "",
    val senderName: String = "",
    val unlockDate: Long, // timestamp
    val isUnlocked: Boolean = false,
    val isOpened: Boolean = false,
    val sealTheme: String = "Cœur d'Or 💛",
    val audioNote: String? = null,
    val photoHint: String? = null
)

@Immutable
data class TruthOrDareEntity(
    val id: String,
    val type: String, // "truth", "dare"
    val prompt: String,
    val intensity: String = "Romantique" // "Doux", "Romantique", "Piquant 🔥", "Intime"
)

@Immutable
data class CoupleDilemmaEntity(
    val id: String,
    val optionA: String,
    val optionB: String,
    val myChoice: String? = null,
    val partnerChoice: String? = null
)

@Immutable
data class CycleDailyLogEntity(
    val dateKey: String, // "YYYY-MM-DD"
    val flow: String = "none", // "none", "spotting", "light", "medium", "heavy"
    val symptoms: List<String> = emptyList(), // "Crampes", "Maux de tête", "Fatigue", "Ballonnements", "Sensibilité mammaire"
    val moods: List<String> = emptyList(), // "Heureuse 🥰", "Sensible 🥺", "Fatiguée 😴", "Irritable ⚡", "Amoureuse ❤️"
    val libido: String = "medium", // "low", "medium", "high", "very_high"
    val waterGlasses: Int = 4,
    val hadIntimacy: Boolean = false,
    val cervicalMucus: String = "dry", // "dry", "sticky", "creamy", "egg_white"
    val note: String = ""
)

@Immutable
data class MenstrualCycleInfo(
    val lastPeriodStartDate: Long = System.currentTimeMillis() - 14L * 86400000L,
    val cycleLengthDays: Int = 28,
    val periodDurationDays: Int = 5,
    val lutealPhaseDays: Int = 14,
    val currentPhase: String = "Fertilité & Ovulation ✨",
    val daysUntilNextPeriod: Int = 14,
    val partnerCareAdvice: String = "Moment de grande complicité et d'énergie. Parfait pour une sortie romantique imprévue ou un massage relaxant !",
    val dailyLogs: Map<String, CycleDailyLogEntity> = emptyMap()
)

@Immutable
data class MessageEntity(
    val id: String,
    val coupleId: String = "",
    val senderId: String,
    val receiverId: String = "",
    val content: String,
    val type: String = "text", // "text", "image", "audio", "video", "scratch_card", "quiz"
    val mediaUrl: String? = null,
    val storagePath: String? = null,
    val thumbnailUrl: String? = null,
    val duration: Int = 0, // In seconds for voice notes
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "sent", // "pending", "sent", "delivered", "read"
    val deliveredAt: Long? = null,
    val readAt: Long? = null,
    val isBurned: Boolean = false,
    val isViewOnce: Boolean = false,
    val isViewed: Boolean = false,
    val isDeletedForEveryone: Boolean = false,
    val deletedFor: List<String> = emptyList(),
    val editedAt: Long? = null,
    val reactions: String = "{}", // JSON map string
    val isStarred: Boolean = false,
    val isPinned: Boolean = false,
    val replyToId: String? = null,
    val replyToSender: String? = null,
    val replyToContent: String? = null
)

@Immutable
data class VaultItemEntity(
    val id: String,
    val coupleId: String = "",
    val title: String,
    val type: String = "photo", // "photo", "video", "audio", "note", "letter"
    val mediaType: String = "photo",
    val mediaUrl: String = "",
    val thumbnailUrl: String = "",
    val duration: Int = 0,
    val category: String = "intime", // "intime", "souvenirs", "projets", "sauvegardes"
    val addedBy: String = "",
    val addedByName: String = "",
    val addedByAvatar: String = "",
    val dateAdded: Long = System.currentTimeMillis(),
    val isViewOnce: Boolean = false,
    val isViewed: Boolean = false,
    val isBurned: Boolean = false,
    val caption: String = "",
    val tags: List<String> = emptyList()
)

enum class ConnectionMode {
    ONLINE,      // Mode En Ligne (Connexion Internet / Cloud Sync)
    OFFLINE_P2P  // Mode Liaison Hors-Ligne (Direct P2P / Sans Internet)
}

@Immutable
data class UserSettingsEntity(
    val userId: String = "",
    val coupleId: String = "",
    val displayName: String = "",
    val customStatus: String = "Toujours amoureux ❤️",
    val avatarUrl: String = "",
    val moodEmoji: String = "🥰",
    val bio: String = "",
    val partnerNickname: String = "",
    val partnerStatus: String = "En ligne",
    val partnerAvatarUrl: String = "",
    val partnerBio: String = "",
    val biometricEnabled: Boolean = true,
    val hasLocalPassword: Boolean = false,
    val hasCompletedPairingSetup: Boolean = false,
    val pinCode: String = "",
    val fakePinCode: String = "",
    val lockTimeoutSeconds: Int = 0, // 0 = Immédiat, 60 = 1 min, 300 = 5 min, -1 = Désactivé
    val screenBlurOnSwitch: Boolean = true,
    val antiScreenshotEnabled: Boolean = true,
    val lastSeenPrivacy: String = "couple_only", // "everyone", "couple_only", "nobody"
    val readReceiptsEnabled: Boolean = true,
    val disappearingMessagesDays: Int = 0, // 0 = désactivé, 1 = 24h, 7 = 7j, 90 = 90j
    val enterIsSend: Boolean = false,
    val mediaVisibilityInGallery: Boolean = false,
    val autoDownloadMedia: String = "wifi_and_data", // "wifi_and_data", "wifi_only", "never"
    val photoUploadQuality: String = "hd", // "hd", "standard"
    val chatTheme: String = "twilight", // "twilight", "sunset", "rose_gold", "cyber_cyan", "matte_black", "custom_image"
    val customWallpaperUri: String = "",
    val wallpaperOpacity: Float = 0.8f,
    val wallpaperBlur: Float = 0.0f,
    val customAppIcon: String = "heart_luxury", // "heart_luxury", "calculator", "notes", "weather", "cyber_cyan", "cherry_blossom", "custom_image"
    val customAppIconUri: String = "",
    val fontFamilyPreference: String = "default", // "default", "serif", "handwritten", "monospace", "cursive"
    val bubbleStyle: String = "gradient_pink", // "gradient_pink", "neon_cyan", "dark_gold", "emerald_luxury", "frosted_glass"
    val fontSizeScale: String = "medium", // "small", "medium", "large", "xlarge"
    val proximityP2PEnabled: Boolean = true,
    val autoSendOfflineQueue: Boolean = true,
    val notificationPrivacyMode: String = "discreet", // "discreet", "weather_camouflage", "system_camouflage", "romantic"
    val notificationSound: String = "heartbeat", // "heartbeat", "soft_chime", "whisper", "silent"
    val heartHapticEnabled: Boolean = true,
    val cyclePartnerAlertsEnabled: Boolean = true,
    val loveWidgetEnabled: Boolean = true,
    val connectionMode: ConnectionMode = ConnectionMode.ONLINE,
    val isDarkMode: Boolean = true
)

@Immutable
data class CallLogEntity(
    val id: String,
    val partnerName: String = "",
    val isVideo: Boolean = false,
    val isIncoming: Boolean = false,
    val isMissed: Boolean = false,
    val durationSeconds: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

@Immutable
data class QuizItemEntity(
    val id: String,
    val question: String,
    val answerMe: String? = null,
    val answerPartner: String? = null,
    val isLocked: Boolean = true,
    val category: String = "Intime"
)

@Immutable
data class CoupleEventEntity(
    val id: String,
    val title: String,
    val date: Long,
    val category: String = "Anniversaire", // "Anniversaire", "Rendez-vous", "Voyage", "Surprise", "Date Intime", "Cinéma"
    val note: String = "",
    val isRecurring: Boolean = false
)
