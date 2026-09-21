package com.example.mikayala.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.example.mikayala.data.SupabaseService
import com.example.mikayala.data.model.*
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.UUID

class MikayalaRepository(private val context: Context) {

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val supabaseService = SupabaseService()

    // --- StateFlows ---
    private val _coupleSpace = MutableStateFlow(CoupleSpaceEntity())
    val activeCoupleSpace: StateFlow<CoupleSpaceEntity> = _coupleSpace.asStateFlow()

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val allMessages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    private val _partnerIsTyping = MutableStateFlow(false)
    val partnerIsTyping: StateFlow<Boolean> = _partnerIsTyping.asStateFlow()
    val isPartnerTyping: StateFlow<Boolean> get() = partnerIsTyping

    private val _partnerIsRecordingAudio = MutableStateFlow(false)
    val partnerIsRecordingAudio: StateFlow<Boolean> = _partnerIsRecordingAudio.asStateFlow()
    val isPartnerRecordingAudio: StateFlow<Boolean> get() = partnerIsRecordingAudio

    private var typingResetJob: Job? = null
    private var recordingResetJob: Job? = null

    private val _vaultItems = MutableStateFlow<List<VaultItemEntity>>(emptyList())
    val allVaultItems: StateFlow<List<VaultItemEntity>> = _vaultItems.asStateFlow()

    private val _userSettings = MutableStateFlow(UserSettingsEntity())
    val userSettings: StateFlow<UserSettingsEntity> = _userSettings.asStateFlow()

    private val _callLogs = MutableStateFlow<List<CallLogEntity>>(emptyList())
    val allCallLogs: StateFlow<List<CallLogEntity>> = _callLogs.asStateFlow()

    private val _quizItems = MutableStateFlow<List<QuizItemEntity>>(emptyList())
    val allQuizItems: StateFlow<List<QuizItemEntity>> = _quizItems.asStateFlow()

    private val _events = MutableStateFlow<List<CoupleEventEntity>>(emptyList())
    val allEvents: StateFlow<List<CoupleEventEntity>> = _events.asStateFlow()

    private val _milestones = MutableStateFlow<List<LoveMilestoneEntity>>(emptyList())
    val allMilestones: StateFlow<List<LoveMilestoneEntity>> = _milestones.asStateFlow()

    private val _coupons = MutableStateFlow<List<LoveCouponEntity>>(emptyList())
    val allCoupons: StateFlow<List<LoveCouponEntity>> = _coupons.asStateFlow()

    private val _bucketList = MutableStateFlow<List<CoupleBucketItemEntity>>(emptyList())
    val allBucketList: StateFlow<List<CoupleBucketItemEntity>> = _bucketList.asStateFlow()

    private val _truthOrDare = MutableStateFlow<List<TruthOrDareEntity>>(emptyList())
    val allTruthOrDare: StateFlow<List<TruthOrDareEntity>> = _truthOrDare.asStateFlow()

    private val _dilemmas = MutableStateFlow<List<CoupleDilemmaEntity>>(emptyList())
    val allDilemmas: StateFlow<List<CoupleDilemmaEntity>> = _dilemmas.asStateFlow()

    private val _loveCapsules = MutableStateFlow<List<LoveCapsuleEntity>>(emptyList())
    val allLoveCapsules: StateFlow<List<LoveCapsuleEntity>> = _loveCapsules.asStateFlow()

    private val _cycleInfo = MutableStateFlow(MenstrualCycleInfo())
    val cycleInfo: StateFlow<MenstrualCycleInfo> = _cycleInfo.asStateFlow()

    private val prefs = context.getSharedPreferences("mikayala_prefs", Context.MODE_PRIVATE)

    init {
        val storedUserId = prefs.getString("user_id", "") ?: ""
        val storedToken = prefs.getString("access_token", "") ?: ""
        val storedEmail = prefs.getString("user_email", "") ?: ""
        val storedDisplayName = prefs.getString("display_name", "") ?: ""
        val storedPairingCode = prefs.getString("pairing_code", "") ?: ""
        val storedPartnerName = prefs.getString("partner_name", "Mon Partenaire") ?: "Mon Partenaire"
        val storedCoupleId = prefs.getString("couple_id", "") ?: ""
        val storedUser1Id = prefs.getString("user1_id", "") ?: ""
        val storedUser2Id = prefs.getString("user2_id", "") ?: ""
        val storedCoupleStatus = prefs.getString("couple_status", "none") ?: "none"

        val storedPinCode = prefs.getString("pin_code", "") ?: ""
        val storedFakePinCode = prefs.getString("fake_pin_code", "") ?: ""
        val storedHasPassword = prefs.getBoolean("has_local_password", false) && storedPinCode.isNotEmpty()
        val storedPairingSetupCompleted = prefs.getBoolean("pairing_setup_completed", false)
        val storedConnModeStr = prefs.getString("connection_mode", "ONLINE") ?: "ONLINE"
        val storedConnMode = try {
            com.example.mikayala.data.model.ConnectionMode.valueOf(storedConnModeStr)
        } catch (e: Exception) {
            com.example.mikayala.data.model.ConnectionMode.ONLINE
        }

        if (storedUserId.isNotEmpty()) {
            _userSettings.value = _userSettings.value.copy(
                userId = storedUserId,
                displayName = storedDisplayName.ifEmpty { "Moi" },
                pinCode = storedPinCode,
                fakePinCode = storedFakePinCode,
                hasLocalPassword = storedHasPassword,
                hasCompletedPairingSetup = storedPairingSetupCompleted,
                connectionMode = storedConnMode
            )
            // Stored state is loaded purely as cache, status starts unverified until Supabase sync
            if (storedPairingCode.isNotEmpty()) {
                _coupleSpace.value = _coupleSpace.value.copy(
                    id = storedCoupleId,
                    pairingCode = storedPairingCode,
                    partner1Id = storedUser1Id,
                    partner2Id = storedUser2Id,
                    partner1Name = storedDisplayName.ifEmpty { "Moi" },
                    partner2Name = storedPartnerName,
                    status = storedCoupleStatus
                )
            }
        }
    }

    // --- Auth Session & Preferences Helpers ---
    fun saveSession(userId: String, token: String, email: String, displayName: String) {
        prefs.edit().apply {
            putString("user_id", userId)
            putString("access_token", token)
            putString("user_email", email)
            putString("display_name", displayName)
            apply()
        }
        _userSettings.value = _userSettings.value.copy(
            userId = userId,
            displayName = displayName
        )
    }

    fun getPrefs(): android.content.SharedPreferences {
        return prefs
    }

    suspend fun ensureAnonymousSession(displayName: String = "Moi"): Boolean {
        Log.d("MikayalaAuth", "ensureAnonymousSession starting for $displayName...")
        val success = supabaseService.ensureAnonymousSession()
        if (success) {
            val session = supabaseService.supabase.auth.currentSessionOrNull()
            val userId = session?.user?.id ?: ""
            Log.d("MikayalaAuth", "ensureAnonymousSession SUCCESS. Session UID: $userId")
            if (userId.isNotEmpty()) {
                prefs.edit().putString("user_id", userId).apply()
                _userSettings.value = _userSettings.value.copy(userId = userId)
            }
        } else {
            Log.e("MikayalaAuth", "ensureAnonymousSession FAILED")
        }
        return success
    }

    suspend fun restoreSupabaseSession(): String? {
        return try {
            val session = supabaseService.supabase.auth.currentSessionOrNull()
            val uid = session?.user?.id
            if (uid != null && uid.isNotEmpty()) {
                Log.d("MikayalaAuth", "[SESSION] Session Supabase restaurée avec succès pour UID: $uid")
                prefs.edit().putString("user_id", uid).apply()
                val email = session.user?.email ?: ""
                if (email.isNotEmpty()) {
                    prefs.edit().putString("user_email", email).apply()
                }
                _userSettings.value = _userSettings.value.copy(userId = uid)
                uid
            } else {
                Log.d("MikayalaAuth", "[SESSION] Aucune session Supabase active trouvée en mémoire.")
                null
            }
        } catch (e: Exception) {
            Log.e("MikayalaAuth", "[SESSION] Erreur lors de la vérification de session: ${e.message}")
            null
        }
    }

    suspend fun signIn(email: String, pass: String): Pair<Boolean, String?> {
        val result = supabaseService.signInWithEmail(email, pass)
        if (result.first) {
            val uid = supabaseService.getCurrentSessionUid() ?: ""
            if (uid.isNotEmpty()) {
                prefs.edit().putString("user_id", uid).putString("user_email", email).apply()
                _userSettings.value = _userSettings.value.copy(userId = uid)
                // Sync profile
                val profile = supabaseService.getProfile(uid)
                val disp = profile?.optString("display_name", "") ?: ""
                if (disp.isNotEmpty()) {
                    prefs.edit().putString("display_name", disp).apply()
                    _userSettings.value = _userSettings.value.copy(displayName = disp)
                }
            }
        }
        return result
    }

    suspend fun signUp(email: String, pass: String, displayName: String): Pair<Boolean, String?> {
        val result = supabaseService.signUpWithEmail(email, pass, displayName)
        if (result.first) {
            val uid = supabaseService.getCurrentSessionUid() ?: ""
            if (uid.isNotEmpty()) {
                prefs.edit().putString("user_id", uid)
                    .putString("user_email", email)
                    .putString("display_name", displayName)
                    .apply()
                _userSettings.value = _userSettings.value.copy(userId = uid, displayName = displayName)
            }
        }
        return result
    }

    sealed class CoupleRecoveryResult {
        object NoSession : CoupleRecoveryResult()
        object NoCoupleFound : CoupleRecoveryResult()
        data class SingleCoupleFound(val couple: CoupleSpaceEntity) : CoupleRecoveryResult()
        data class MultipleCouplesFound(val couples: List<CoupleSummaryItem>) : CoupleRecoveryResult()
        data class WaitingCouple(val pairingCode: String) : CoupleRecoveryResult()
    }

    suspend fun recoverCoupleFromSupabase(): CoupleRecoveryResult {
        var myUserId = getCurrentUserId()
        if (myUserId.isEmpty()) {
            val restoredUid = restoreSupabaseSession()
            if (restoredUid != null) {
                myUserId = restoredUid
            } else {
                Log.w("MikayalaRecovery", "[RECOVERY] Aucun utilisateur connecté.")
                return CoupleRecoveryResult.NoSession
            }
        }

        Log.d("MikayalaRecovery", "[RECOVERY] Interrogation de Supabase (source de vérité) pour UID: $myUserId")
        val rawCouples = supabaseService.getAllCouplesForUser(myUserId)
        if (rawCouples.isEmpty()) {
            Log.d("MikayalaRecovery", "[RECOVERY] Aucun couple trouvé dans la table couples pour cet UID.")
            clearCoupleCache()
            return CoupleRecoveryResult.NoCoupleFound
        }

        val pairedSummaries = mutableListOf<CoupleSummaryItem>()
        var waitingCoupleObj: JSONObject? = null

        for (space in rawCouples) {
            val pairingCode = space.optString("pairing_code", "")
            val status = space.optString("status", "")
            val u1Id = space.optString("user1_id", "")
            val u2Id = space.optString("user2_id", "")
            val coupleId = space.optString("id", "")
            val createdAt = space.optString("created_at", "")

            val isPaired = (status == "paired") &&
                    u1Id.isNotBlank() && u1Id != "null" &&
                    u2Id.isNotBlank() && u2Id != "null" &&
                    coupleId.isNotBlank() &&
                    (myUserId == u1Id || myUserId == u2Id)

            if (isPaired) {
                val isP1 = (myUserId == u1Id)
                val partnerId = if (isP1) u2Id else u1Id
                var pName = if (isP1) {
                    space.optString("partner_2_name", space.optString("partner2_name", "Partenaire"))
                } else {
                    space.optString("partner_1_name", space.optString("partner1_name", "Partenaire"))
                }
                if (pName.isBlank() || pName == "En attente...") {
                    val partnerProfile = supabaseService.getProfile(partnerId)
                    if (partnerProfile != null) {
                        pName = partnerProfile.optString("display_name", pName)
                    }
                }
                pairedSummaries.add(
                    CoupleSummaryItem(
                        coupleId = coupleId,
                        pairingCode = pairingCode,
                        status = "paired",
                        partnerId = partnerId,
                        partnerName = pName.ifEmpty { "Mon Partenaire" },
                        user1Id = u1Id,
                        user2Id = u2Id,
                        createdAt = createdAt,
                        isPaired = true
                    )
                )
            } else if (status == "waiting" && waitingCoupleObj == null) {
                waitingCoupleObj = space
            }
        }

        if (pairedSummaries.size > 1) {
            Log.d("MikayalaRecovery", "[RECOVERY] Plusieurs couples connectés trouvés (${pairedSummaries.size}). Choix requis.")
            return CoupleRecoveryResult.MultipleCouplesFound(pairedSummaries)
        } else if (pairedSummaries.size == 1) {
            val selected = pairedSummaries.first()
            Log.d("MikayalaRecovery", "[RECOVERY] Un seul couple connecté trouvé. Activation: ${selected.coupleId}")
            activateCouple(selected)
            return CoupleRecoveryResult.SingleCoupleFound(_coupleSpace.value)
        } else if (waitingCoupleObj != null) {
            val wCode = waitingCoupleObj.optString("pairing_code", "")
            val cId = waitingCoupleObj.optString("id", "")
            savePairingCode(
                code = wCode,
                partnerName = "En attente...",
                coupleId = cId,
                status = "waiting",
                u1Id = waitingCoupleObj.optString("user1_id", myUserId),
                u2Id = ""
            )
            return CoupleRecoveryResult.WaitingCouple(wCode)
        } else {
            clearCoupleCache()
            return CoupleRecoveryResult.NoCoupleFound
        }
    }

    suspend fun activateCouple(summary: CoupleSummaryItem) {
        val myUserId = getCurrentUserId()
        val isP1 = (myUserId == summary.user1Id)
        val pName = summary.partnerName

        savePairingCode(
            code = summary.pairingCode,
            partnerName = pName,
            coupleId = summary.coupleId,
            status = "paired",
            u1Id = summary.user1Id,
            u2Id = summary.user2Id
        )
        syncWithSupabase()
    }

    suspend fun findExistingCoupleSpace(): Boolean {
        val result = recoverCoupleFromSupabase()
        return when (result) {
            is CoupleRecoveryResult.SingleCoupleFound -> true
            is CoupleRecoveryResult.MultipleCouplesFound -> {
                // By default activate the most recent paired couple if not explicitly selected
                val first = result.couples.first()
                activateCouple(first)
                true
            }
            else -> false
        }
    }

    fun clearCoupleCache() {
        prefs.edit().apply {
            remove("pairing_code")
            remove("couple_id")
            remove("couple_status")
            remove("partner_name")
            remove("user1_id")
            remove("user2_id")
            remove("partner_1_id")
            remove("partner_2_id")
            apply()
        }
        _coupleSpace.value = CoupleSpaceEntity()
    }

    fun savePairingCode(
        code: String,
        partnerName: String = "Mon Partenaire",
        coupleId: String = "",
        status: String = "waiting",
        u1Id: String = "",
        u2Id: String = ""
    ) {
        val myUserId = getCurrentUserId()
        val hasU1 = u1Id.isNotBlank() && u1Id != "null"
        val hasU2 = u2Id.isNotBlank() && u2Id != "null"
        val hasValidId = coupleId.isNotBlank()
        val isUserInCouple = (myUserId.isEmpty() || myUserId == u1Id || myUserId == u2Id)
        val isPaired = (status == "paired") && hasU1 && hasU2 && hasValidId && isUserInCouple
        val normalizedStatus = if (isPaired) "paired" else if (code.isNotEmpty()) "waiting" else "none"

        prefs.edit().apply {
            putString("pairing_code", code)
            putString("partner_name", partnerName)
            putString("couple_status", normalizedStatus)
            if (coupleId.isNotEmpty()) {
                putString("couple_id", coupleId)
            }
            if (u1Id.isNotEmpty()) {
                putString("user1_id", u1Id)
            }
            if (u2Id.isNotEmpty()) {
                putString("user2_id", u2Id)
            }
            apply()
        }
        _coupleSpace.value = _coupleSpace.value.copy(
            id = coupleId.ifEmpty { _coupleSpace.value.id },
            pairingCode = code,
            partner1Id = if (u1Id.isNotEmpty()) u1Id else _coupleSpace.value.partner1Id,
            partner2Id = if (u2Id.isNotEmpty()) u2Id else _coupleSpace.value.partner2Id,
            partner2Name = partnerName,
            status = normalizedStatus
        )
    }

    fun clearSession() {
        prefs.edit().clear().apply()
        _userSettings.value = UserSettingsEntity()
        _coupleSpace.value = CoupleSpaceEntity()
        _messages.value = emptyList()
        _vaultItems.value = emptyList()
        _callLogs.value = emptyList()
        _quizItems.value = emptyList()
        _events.value = emptyList()
        _milestones.value = emptyList()
        _coupons.value = emptyList()
        _bucketList.value = emptyList()
        _truthOrDare.value = emptyList()
        _dilemmas.value = emptyList()
        _loveCapsules.value = emptyList()
    }

    fun getCurrentUserId(): String {
        return prefs.getString("user_id", "") ?: ""
    }

    fun getAccessToken(): String {
        return prefs.getString("access_token", "") ?: ""
    }

    fun getPairingCode(): String {
        return prefs.getString("pairing_code", "") ?: ""
    }

    // --- Realtime Connection & Handlers ---
    fun connectRealtime() {
        val couple = _coupleSpace.value
        val myUserId = getCurrentUserId()
        if (!couple.isPaired || couple.id.isBlank()) return

        supabaseService.subscribeToMessagesRealtime(
            coupleId = couple.id,
            myUserId = myUserId,
            onInsert = { obj -> handleRealtimeMessageInsert(obj) },
            onUpdate = { obj -> handleRealtimeMessageUpdate(obj) },
            onDelete = { id -> handleRealtimeMessageDelete(id) },
            onTyping = { senderId, isTyping ->
                _partnerIsTyping.value = isTyping
                typingResetJob?.cancel()
                if (isTyping) {
                    typingResetJob = repositoryScope.launch {
                        delay(4000)
                        _partnerIsTyping.value = false
                    }
                }
            },
            onRecording = { senderId, isRecording ->
                _partnerIsRecordingAudio.value = isRecording
                recordingResetJob?.cancel()
                if (isRecording) {
                    recordingResetJob = repositoryScope.launch {
                        delay(6000)
                        _partnerIsRecordingAudio.value = false
                    }
                }
            }
        )
    }

    fun sendTypingBroadcast(isTyping: Boolean) {
        val myUserId = getCurrentUserId()
        supabaseService.broadcastTyping(isTyping, myUserId)
    }

    fun sendRecordingBroadcast(isRecording: Boolean) {
        val myUserId = getCurrentUserId()
        supabaseService.broadcastRecording(isRecording, myUserId)
    }

    private fun handleRealtimeMessageInsert(obj: JSONObject) {
        val newEntity = parseMessageJson(obj) ?: return
        val myUserId = getCurrentUserId()

        if (newEntity.senderId != myUserId && newEntity.status == "sent") {
            repositoryScope.launch {
                markMessagesDelivered(_coupleSpace.value.id)
            }
        }

        _messages.value = _messages.value.let { currentList ->
            val existingIndex = currentList.indexOfFirst { it.id == newEntity.id }
            if (existingIndex >= 0) {
                currentList.toMutableList().apply { set(existingIndex, newEntity) }
            } else {
                (currentList + newEntity).sortedBy { it.createdAt }
            }
        }
    }

    private fun handleRealtimeMessageUpdate(obj: JSONObject) {
        val updatedEntity = parseMessageJson(obj) ?: return
        _messages.value = _messages.value.map { local ->
            if (local.id == updatedEntity.id) {
                updatedEntity
            } else {
                local
            }
        }
    }

    private fun handleRealtimeMessageDelete(id: String) {
        _messages.value = _messages.value.filterNot { it.id == id }
    }

    private fun parseMessageJson(obj: JSONObject): MessageEntity? {
        try {
            val id = obj.optString("id", "")
            if (id.isEmpty()) return null
            val coupleId = obj.optString("couple_id", _coupleSpace.value.id)
            val senderId = obj.optString("sender_id", "")
            val receiverId = obj.optString("receiver_id", "")
            val content = obj.optString("content", "")
            val type = obj.optString("type", "text")
            val mediaUrl = obj.optString("media_url", "").ifEmpty { null }
            val thumbnailUrl = obj.optString("thumbnail_url", "").ifEmpty { null }
            val duration = obj.optInt("duration", 0)

            val createdAt = when {
                obj.has("created_at") && obj.optLong("created_at", 0L) > 0L -> obj.optLong("created_at")
                obj.has("created_at") -> parseTimestamp(obj.optString("created_at")) ?: System.currentTimeMillis()
                else -> System.currentTimeMillis()
            }
            val editedAt = if (obj.has("edited_at") && !obj.isNull("edited_at")) {
                obj.optLong("edited_at", 0L).takeIf { it > 0L } ?: parseTimestamp(obj.optString("edited_at"))
            } else null

            val deliveredAt = obj.optString("delivered_at", "null")
            val readAt = obj.optString("read_at", "null")
            val status = when {
                readAt != "null" && readAt.isNotEmpty() -> "read"
                deliveredAt != "null" && deliveredAt.isNotEmpty() -> "delivered"
                else -> obj.optString("status", "sent")
            }
            val parsedReadAt = parseTimestamp(if (readAt != "null") readAt else null)

            val isViewOnce = obj.optBoolean("is_view_once", false)
            val isViewed = obj.optBoolean("is_viewed", false)
            val isStarred = obj.optBoolean("is_starred", false)
            val isPinned = obj.optBoolean("is_pinned", false)
            val isDeletedForEveryone = obj.optBoolean("is_deleted_for_everyone", false)
            val deletedForArray = obj.optJSONArray("deleted_for")
            val deletedForList = if (deletedForArray != null) {
                (0 until deletedForArray.length()).map { deletedForArray.getString(it) }
            } else emptyList<String>()
            val reactions = obj.optString("reactions", "{}")
            val replyToId = obj.optString("reply_to_id", "").ifEmpty { null }
            val replyToSender = obj.optString("reply_to_sender", "").ifEmpty { null }
            val replyToContent = obj.optString("reply_to_content", "").ifEmpty { null }

            return MessageEntity(
                id = id,
                coupleId = coupleId,
                senderId = senderId,
                receiverId = receiverId,
                content = if (isDeletedForEveryone) "Ce message a été supprimé" else content,
                type = type,
                mediaUrl = mediaUrl,
                thumbnailUrl = thumbnailUrl,
                duration = duration,
                createdAt = createdAt,
                editedAt = editedAt,
                status = status,
                readAt = parsedReadAt,
                isViewOnce = isViewOnce,
                isViewed = isViewed,
                isStarred = isStarred,
                isPinned = isPinned,
                isDeletedForEveryone = isDeletedForEveryone,
                deletedFor = deletedForList,
                reactions = reactions,
                replyToId = replyToId,
                replyToSender = replyToSender,
                replyToContent = replyToContent
            )
        } catch (e: Exception) {
            Log.e("MikayalaRepository", "Error parsing message json: ${e.message}")
            return null
        }
    }

    // --- Message Actions (Real Couple Checked & Supabase Synced) ---
    fun sendMessage(
        content: String,
        type: String = "text",
        mediaUrl: String? = null,
        duration: Int = 0,
        isViewOnce: Boolean = false,
        replyToId: String? = null,
        replyToSender: String? = null,
        replyToContent: String? = null
    ) {
        val couple = _coupleSpace.value
        val myUserId = getCurrentUserId()
        if (!couple.isPaired || couple.id.isBlank() || myUserId.isBlank()) {
            Log.w("MikayalaRepository", "Cannot send message: no active paired couple space.")
            return
        }

        val partnerId = if (couple.partner1Id == myUserId) couple.partner2Id else couple.partner1Id
        val coupleId = couple.id

        val newMsg = MessageEntity(
            id = "msg_" + UUID.randomUUID().toString().take(12),
            coupleId = coupleId,
            senderId = myUserId,
            receiverId = partnerId,
            content = content,
            type = type,
            mediaUrl = mediaUrl,
            duration = duration,
            createdAt = System.currentTimeMillis(),
            status = "sent",
            isViewOnce = isViewOnce,
            replyToId = replyToId,
            replyToSender = replyToSender,
            replyToContent = replyToContent
        )
        _messages.value = _messages.value + newMsg

        // Sync with Supabase
        repositoryScope.launch {
            val success = supabaseService.postMessageEntity(newMsg)
            if (!success) {
                Log.e("MikayalaRepository", "Failed to post message to Supabase")
            }
        }
    }

    fun sendVoiceNote(filePath: String, durationSeconds: Int) {
        val couple = _coupleSpace.value
        if (!couple.isPaired || couple.id.isBlank()) return
        repositoryScope.launch {
            try {
                val file = File(filePath)
                if (file.exists()) {
                    val bytes = file.readBytes()
                    val fileName = "voice_${UUID.randomUUID().toString().take(8)}.m4a"
                    val uploadedUrl = supabaseService.uploadChatMedia(couple.id, fileName, bytes, "audio/mp4")
                    sendMessage(
                        content = "Message vocal",
                        type = "audio",
                        mediaUrl = uploadedUrl ?: filePath,
                        duration = durationSeconds
                    )
                }
            } catch (e: Exception) {
                Log.e("MikayalaRepository", "Failed to upload voice note: ${e.message}")
                sendMessage(
                    content = "Message vocal",
                    type = "audio",
                    mediaUrl = filePath,
                    duration = durationSeconds
                )
            }
        }
    }

    fun sendImageMedia(uri: Uri, isViewOnce: Boolean = false) {
        val couple = _coupleSpace.value
        if (!couple.isPaired || couple.id.isBlank()) return
        repositoryScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes != null) {
                    val fileName = "img_${UUID.randomUUID().toString().take(8)}.jpg"
                    val uploadedUrl = supabaseService.uploadChatMedia(couple.id, fileName, bytes, "image/jpeg")
                    sendMessage(
                        content = if (isViewOnce) "Photo éphémère" else "Photo partagée",
                        type = "image",
                        mediaUrl = uploadedUrl ?: uri.toString(),
                        isViewOnce = isViewOnce
                    )
                }
            } catch (e: Exception) {
                Log.e("MikayalaRepository", "Failed to send image media: ${e.message}")
                sendMessage(
                    content = if (isViewOnce) "Photo éphémère" else "Photo partagée",
                    type = "image",
                    mediaUrl = uri.toString(),
                    isViewOnce = isViewOnce
                )
            }
        }
    }

    fun sendCameraPhoto(bitmap: Bitmap, isViewOnce: Boolean = false) {
        val couple = _coupleSpace.value
        if (!couple.isPaired || couple.id.isBlank()) return
        repositoryScope.launch {
            try {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                val bytes = stream.toByteArray()
                val fileName = "photo_${UUID.randomUUID().toString().take(8)}.jpg"
                val uploadedUrl = supabaseService.uploadChatMedia(couple.id, fileName, bytes, "image/jpeg")
                sendMessage(
                    content = if (isViewOnce) "Photo instantanée éphémère" else "Photo instantanée",
                    type = "image",
                    mediaUrl = uploadedUrl,
                    isViewOnce = isViewOnce
                )
            } catch (e: Exception) {
                Log.e("MikayalaRepository", "Failed to send camera photo: ${e.message}")
            }
        }
    }

    fun sendDocumentMedia(uri: Uri, fileName: String) {
        val couple = _coupleSpace.value
        if (!couple.isPaired || couple.id.isBlank()) return
        repositoryScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes != null) {
                    val safeName = "doc_${UUID.randomUUID().toString().take(8)}_$fileName"
                    val uploadedUrl = supabaseService.uploadChatMedia(couple.id, safeName, bytes, "application/octet-stream")
                    sendMessage(
                        content = fileName,
                        type = "document",
                        mediaUrl = uploadedUrl ?: uri.toString()
                    )
                }
            } catch (e: Exception) {
                Log.e("MikayalaRepository", "Failed to send document: ${e.message}")
                sendMessage(
                    content = fileName,
                    type = "document",
                    mediaUrl = uri.toString()
                )
            }
        }
    }

    fun sendLocationMessage(latitude: Double, longitude: Double, address: String = "Ma position actuelle") {
        sendMessage(
            content = address,
            type = "location",
            mediaUrl = "$latitude,$longitude"
        )
    }

    fun toggleReaction(messageId: String, emoji: String) {
        var updatedReactionsJson = "{}"
        _messages.value = _messages.value.map { msg ->
            if (msg.id == messageId) {
                try {
                    val json = JSONObject(msg.reactions)
                    if (json.optString("me") == emoji) {
                        json.remove("me")
                    } else {
                        json.put("me", emoji)
                    }
                    updatedReactionsJson = json.toString()
                    msg.copy(reactions = updatedReactionsJson)
                } catch (e: Exception) {
                    updatedReactionsJson = "{\"me\":\"$emoji\"}"
                    msg.copy(reactions = updatedReactionsJson)
                }
            } else msg
        }
        repositoryScope.launch {
            supabaseService.updateMessageFields(
                messageId,
                JSONObject().apply { put("reactions", updatedReactionsJson) }
            )
        }
    }

    fun toggleStarred(messageId: String) {
        var newStarred = false
        _messages.value = _messages.value.map {
            if (it.id == messageId) {
                newStarred = !it.isStarred
                it.copy(isStarred = newStarred)
            } else it
        }
        repositoryScope.launch {
            supabaseService.updateMessageFields(
                messageId,
                JSONObject().apply { put("is_starred", newStarred) }
            )
        }
    }

    fun togglePinned(messageId: String) {
        var newPinned = false
        _messages.value = _messages.value.map {
            if (it.id == messageId) {
                newPinned = !it.isPinned
                it.copy(isPinned = newPinned)
            } else it
        }
        repositoryScope.launch {
            supabaseService.updateMessageFields(
                messageId,
                JSONObject().apply { put("is_pinned", newPinned) }
            )
        }
    }

    fun editMessage(messageId: String, newContent: String) {
        val now = System.currentTimeMillis()
        _messages.value = _messages.value.map {
            if (it.id == messageId) it.copy(content = newContent, editedAt = now) else it
        }
        repositoryScope.launch {
            supabaseService.updateMessageFields(
                messageId,
                JSONObject().apply {
                    put("content", newContent)
                    put("edited_at", now)
                }
            )
        }
    }

    fun deleteMessage(messageId: String, forEveryone: Boolean) {
        if (forEveryone) {
            _messages.value = _messages.value.map {
                if (it.id == messageId) it.copy(isDeletedForEveryone = true, content = "Ce message a été supprimé") else it
            }
            repositoryScope.launch {
                supabaseService.deleteMessageForEveryone(messageId)
            }
        } else {
            _messages.value = _messages.value.filterNot { it.id == messageId }
            repositoryScope.launch {
                supabaseService.deleteMessage(messageId)
            }
        }
    }

    fun markViewOnceAsViewed(messageId: String) {
        _messages.value = _messages.value.map {
            if (it.id == messageId) it.copy(isViewed = true) else it
        }
        repositoryScope.launch {
            supabaseService.updateMessageFields(
                messageId,
                JSONObject().apply { put("is_viewed", true) }
            )
        }
    }

    fun clearAllMessages() {
        _messages.value = emptyList()
    }

    fun saveMessageToVault(message: MessageEntity) {
        val vaultItem = VaultItemEntity(
            id = "v_" + UUID.randomUUID().toString().take(8),
            title = if (message.type == "audio") "Message vocal sauvegardé" else "Souvenir du chat",
            type = if (message.type == "audio") "audio" else "note",
            category = "sauvegardes",
            caption = message.content,
            duration = message.duration,
            dateAdded = System.currentTimeMillis()
        )
        addVaultItem(vaultItem.title, vaultItem.type, vaultItem.category, vaultItem.caption)
    }

    // --- Shared Vault Actions ---
    fun addVaultItem(title: String, type: String, category: String, caption: String, isViewOnce: Boolean = false) {
        val couple = _coupleSpace.value
        val myUserId = getCurrentUserId()
        val coupleId = couple.id
        val pairingCode = couple.pairingCode

        val newItem = VaultItemEntity(
            id = "v_" + UUID.randomUUID().toString().take(8),
            coupleId = coupleId,
            title = title,
            type = type,
            category = category,
            caption = caption,
            isViewOnce = isViewOnce,
            dateAdded = System.currentTimeMillis()
        )
        _vaultItems.value = listOf(newItem) + _vaultItems.value

        // Sync with Supabase Shared Vault
        if (couple.isPaired && coupleId.isNotEmpty()) {
            repositoryScope.launch {
                supabaseService.insertVaultItem(
                    coupleId = coupleId,
                    senderId = myUserId,
                    title = title,
                    type = type,
                    category = category,
                    caption = caption,
                    mediaUrl = "",
                    pairingCode = pairingCode
                )
            }
        }
    }

    fun deleteVaultItem(id: String) {
        _vaultItems.value = _vaultItems.value.filterNot { it.id == id }
        repositoryScope.launch {
            supabaseService.deleteVaultItem(id)
        }
    }

    // --- Calls Actions ---
    fun addCallLog(isVideo: Boolean, durationSeconds: Int) {
        val couple = _coupleSpace.value
        val partnerName = if (couple.partner2Name.isNotBlank()) couple.partner2Name else "Partenaire"
        val newCall = CallLogEntity(
            id = "call_" + UUID.randomUUID().toString().take(8),
            partnerName = partnerName,
            isVideo = isVideo,
            isIncoming = false,
            isMissed = false,
            durationSeconds = durationSeconds,
            timestamp = System.currentTimeMillis()
        )
        _callLogs.value = listOf(newCall) + _callLogs.value
    }

    // --- Pairing Actions ---
    fun updatePairing(code: String) {
        _coupleSpace.value = _coupleSpace.value.copy(pairingCode = code)
    }

    // --- Settings & Customization Actions ---
    fun updateConnectionMode(mode: com.example.mikayala.data.model.ConnectionMode) {
        prefs.edit().putString("connection_mode", mode.name).apply()
        _userSettings.value = _userSettings.value.copy(connectionMode = mode)
    }

    fun setPairingSetupCompleted(completed: Boolean) {
        prefs.edit().putBoolean("pairing_setup_completed", completed).apply()
        _userSettings.value = _userSettings.value.copy(hasCompletedPairingSetup = completed)
    }

    fun updatePinCode(newPin: String) {
        prefs.edit().apply {
            putString("pin_code", newPin)
            putBoolean("has_local_password", newPin.isNotEmpty())
            putBoolean("pairing_setup_completed", true)
            apply()
        }
        _userSettings.value = _userSettings.value.copy(
            pinCode = newPin,
            hasLocalPassword = newPin.isNotEmpty(),
            hasCompletedPairingSetup = true
        )
    }

    fun updateDecoyPin(newFakePin: String) {
        prefs.edit().putString("fake_pin_code", newFakePin).apply()
        _userSettings.value = _userSettings.value.copy(fakePinCode = newFakePin)
    }

    fun updateLockTimeout(seconds: Int) {
        _userSettings.value = _userSettings.value.copy(lockTimeoutSeconds = seconds)
    }

    fun updateProfile(name: String, status: String, avatarUrl: String = "", moodEmoji: String = "🥰") {
        _userSettings.value = _userSettings.value.copy(
            displayName = name,
            customStatus = status,
            avatarUrl = avatarUrl,
            moodEmoji = moodEmoji
        )
        _coupleSpace.value = _coupleSpace.value.copy(
            partner1Name = name,
            partner1Status = status,
            partner1Avatar = avatarUrl
        )

        val userId = getCurrentUserId()
        if (userId.isNotEmpty()) {
            repositoryScope.launch {
                val curProfile = supabaseService.getProfile(userId)
                val avatarPath = curProfile?.optString("avatar_path", "") ?: ""
                val avatarVer = curProfile?.optInt("avatar_version", 0) ?: 0
                supabaseService.updateProfile(
                    userId = userId,
                    displayName = name,
                    bio = status,
                    avatarPath = avatarPath,
                    avatarVersion = avatarVer
                )
                syncProfiles()
            }
        }
    }

    fun updatePartnerProfile(nickname: String, status: String, avatarUrl: String = "") {
        _userSettings.value = _userSettings.value.copy(
            partnerNickname = nickname,
            partnerStatus = status,
            partnerAvatarUrl = avatarUrl
        )
        _coupleSpace.value = _coupleSpace.value.copy(
            partner2Name = nickname,
            partner2Status = status,
            partner2Avatar = avatarUrl
        )

        val myUserId = getCurrentUserId()
        val pairingCode = _coupleSpace.value.pairingCode
        if (myUserId.isNotEmpty() && pairingCode.isNotEmpty()) {
            repositoryScope.launch {
                val spaceJson = supabaseService.getCoupleSpace(pairingCode)
                if (spaceJson != null) {
                    val p1Id = spaceJson.optString("user1_id", "")
                    val p2Id = spaceJson.optString("user2_id", "")
                    val partnerUserId = if (myUserId == p1Id) p2Id else p1Id
                    val coupleId = spaceJson.optString("id", "")

                    if (partnerUserId.isNotEmpty() && coupleId.isNotEmpty()) {
                        supabaseService.upsertPartnerNickname(coupleId, myUserId, partnerUserId, nickname)
                        syncProfiles()
                    }
                }
            }
        }
    }

    fun updateWallpaper(theme: String, customUri: String = "") {
        _userSettings.value = _userSettings.value.copy(
            chatTheme = theme,
            customWallpaperUri = customUri
        )
    }

    fun updateWallpaperControls(opacity: Float, blur: Float) {
        _userSettings.value = _userSettings.value.copy(
            wallpaperOpacity = opacity,
            wallpaperBlur = blur
        )
    }

    fun updateAppIcon(iconPreset: String, customUri: String = "") {
        _userSettings.value = _userSettings.value.copy(
            customAppIcon = iconPreset,
            customAppIconUri = customUri
        )
    }

    fun updateFontFamily(fontFamily: String) {
        _userSettings.value = _userSettings.value.copy(fontFamilyPreference = fontFamily)
    }

    fun updateFontSize(scale: String) {
        _userSettings.value = _userSettings.value.copy(fontSizeScale = scale)
    }

    fun updateBubbleStyle(style: String) {
        _userSettings.value = _userSettings.value.copy(bubbleStyle = style)
    }

    fun updateDarkMode(enabled: Boolean) {
        _userSettings.value = _userSettings.value.copy(isDarkMode = enabled)
    }

    fun updatePrivacySettings(
        biometricEnabled: Boolean,
        antiScreenshotEnabled: Boolean,
        screenBlurOnSwitch: Boolean,
        lastSeenPrivacy: String,
        readReceiptsEnabled: Boolean,
        disappearingDays: Int
    ) {
        _userSettings.value = _userSettings.value.copy(
            biometricEnabled = biometricEnabled,
            antiScreenshotEnabled = antiScreenshotEnabled,
            screenBlurOnSwitch = screenBlurOnSwitch,
            lastSeenPrivacy = lastSeenPrivacy,
            readReceiptsEnabled = readReceiptsEnabled,
            disappearingMessagesDays = disappearingDays
        )
    }

    fun updateChatPreferences(
        enterIsSend: Boolean,
        mediaVisibilityInGallery: Boolean,
        autoDownloadMedia: String,
        photoUploadQuality: String
    ) {
        _userSettings.value = _userSettings.value.copy(
            enterIsSend = enterIsSend,
            mediaVisibilityInGallery = mediaVisibilityInGallery,
            autoDownloadMedia = autoDownloadMedia,
            photoUploadQuality = photoUploadQuality
        )
    }

    fun updateNotificationSettings(
        notificationPrivacyMode: String,
        notificationSound: String,
        heartHapticEnabled: Boolean,
        cyclePartnerAlertsEnabled: Boolean,
        loveWidgetEnabled: Boolean
    ) {
        _userSettings.value = _userSettings.value.copy(
            notificationPrivacyMode = notificationPrivacyMode,
            notificationSound = notificationSound,
            heartHapticEnabled = heartHapticEnabled,
            cyclePartnerAlertsEnabled = cyclePartnerAlertsEnabled,
            loveWidgetEnabled = loveWidgetEnabled
        )
    }

    fun clearChatHistory() {
        _messages.value = emptyList()
    }

    // --- Love Time & Customization Actions ---
    fun updateLoveTimeSettings(anniversaryDate: Long, partner1Name: String, partner2Name: String, loveQuote: String) {
        _coupleSpace.value = _coupleSpace.value.copy(
            anniversaryDate = anniversaryDate,
            partner1Name = partner1Name,
            partner2Name = partner2Name,
            loveQuote = loveQuote
        )
    }

    fun addMilestone(title: String, daysTarget: Long, icon: String = "🏆") {
        val newMilestone = LoveMilestoneEntity(
            id = "m_" + UUID.randomUUID().toString().take(8),
            title = title,
            daysTarget = daysTarget,
            icon = icon,
            isUnlocked = false
        )
        _milestones.value = _milestones.value + newMilestone
    }

    fun deleteMilestone(id: String) {
        _milestones.value = _milestones.value.filterNot { it.id == id }
    }

    // --- Events & Calendar Actions ---
    fun addEvent(title: String, date: Long, category: String, note: String = "") {
        val newEvent = CoupleEventEntity(
            id = "ev_" + UUID.randomUUID().toString().take(8),
            title = title,
            date = date,
            category = category,
            note = note
        )
        _events.value = _events.value + newEvent
    }

    fun updateEvent(id: String, title: String, date: Long, category: String, note: String = "") {
        _events.value = _events.value.map {
            if (it.id == id) it.copy(title = title, date = date, category = category, note = note) else it
        }
    }

    fun deleteEvent(id: String) {
        _events.value = _events.value.filterNot { it.id == id }
    }

    // --- Quiz & Games Actions ---
    fun answerQuiz(id: String, answer: String) {
        _quizItems.value = _quizItems.value.map {
            if (it.id == id) it.copy(answerMe = answer, isLocked = false) else it
        }
    }

    fun addQuizItem(question: String, answerMe: String, category: String = "Complicité") {
        val newQuiz = QuizItemEntity(
            id = "q_" + UUID.randomUUID().toString().take(8),
            question = question,
            answerMe = answerMe,
            category = category
        )
        _quizItems.value = _quizItems.value + newQuiz
    }

    fun deleteQuizItem(id: String) {
        _quizItems.value = _quizItems.value.filterNot { it.id == id }
    }

    // --- Coupons Actions ---
    fun redeemCoupon(id: String) {
        _coupons.value = _coupons.value.map {
            if (it.id == id) it.copy(isRedeemed = true) else it
        }
    }

    fun addCoupon(title: String, description: String, icon: String = "🎟️", category: String = "Romance") {
        val newCoupon = LoveCouponEntity(
            id = "cp_" + UUID.randomUUID().toString().take(8),
            title = title,
            description = description,
            icon = icon,
            isRedeemed = false,
            category = category
        )
        _coupons.value = listOf(newCoupon) + _coupons.value
    }

    fun deleteCoupon(id: String) {
        _coupons.value = _coupons.value.filterNot { it.id == id }
    }

    // --- Bucket List Actions ---
    fun toggleBucketItem(id: String) {
        _bucketList.value = _bucketList.value.map {
            if (it.id == id) it.copy(isCompleted = !it.isCompleted) else it
        }
    }

    fun addBucketItem(title: String, category: String = "Romantique") {
        val newItem = CoupleBucketItemEntity(
            id = "bk_" + UUID.randomUUID().toString().take(8),
            title = title,
            isCompleted = false,
            category = category
        )
        _bucketList.value = _bucketList.value + newItem
    }

    fun deleteBucketItem(id: String) {
        _bucketList.value = _bucketList.value.filterNot { it.id == id }
    }

    // --- Truth or Dare Actions ---
    fun addTruthOrDare(type: String, prompt: String, intensity: String = "Romantique") {
        val newCard = TruthOrDareEntity(
            id = "td_" + UUID.randomUUID().toString().take(8),
            type = type,
            prompt = prompt,
            intensity = intensity
        )
        _truthOrDare.value = listOf(newCard) + _truthOrDare.value
    }

    fun deleteTruthOrDare(id: String) {
        _truthOrDare.value = _truthOrDare.value.filterNot { it.id == id }
    }

    // --- Dilemmas Actions ---
    fun voteDilemma(id: String, choice: String) {
        _dilemmas.value = _dilemmas.value.map {
            if (it.id == id) it.copy(myChoice = choice) else it
        }
    }

    fun addDilemma(optionA: String, optionB: String) {
        val newDilemma = CoupleDilemmaEntity(
            id = "dl_" + UUID.randomUUID().toString().take(8),
            optionA = optionA,
            optionB = optionB
        )
        _dilemmas.value = listOf(newDilemma) + _dilemmas.value
    }

    // --- Flo-style Cycle & Care Actions ---
    fun updateFloCycleSettings(startDate: Long, cycleLength: Int, periodDuration: Int) {
        val now = System.currentTimeMillis()
        val daysSinceStart = ((now - startDate) / 86400000L).toInt().coerceAtLeast(0)
        val cycleDay = (daysSinceStart % cycleLength) + 1
        val daysUntilNext = (cycleLength - cycleDay).coerceAtLeast(0)

        val (phase, advice) = calculateCyclePhaseAndAdvice(cycleDay, periodDuration, cycleLength)

        _cycleInfo.value = _cycleInfo.value.copy(
            lastPeriodStartDate = startDate,
            cycleLengthDays = cycleLength,
            periodDurationDays = periodDuration,
            currentPhase = phase,
            daysUntilNextPeriod = daysUntilNext,
            partnerCareAdvice = advice
        )
    }

    fun saveCycleDailyLog(log: CycleDailyLogEntity) {
        val currentLogs = _cycleInfo.value.dailyLogs.toMutableMap()
        currentLogs[log.dateKey] = log
        _cycleInfo.value = _cycleInfo.value.copy(dailyLogs = currentLogs)
    }

    fun updateCycleInfo(phase: String, advice: String) {
        _cycleInfo.value = _cycleInfo.value.copy(
            currentPhase = phase,
            partnerCareAdvice = advice
        )
    }

    private fun calculateCyclePhaseAndAdvice(cycleDay: Int, periodDuration: Int, cycleLength: Int): Pair<String, String> {
        val ovulationDay = cycleLength - 14
        val fertileStart = ovulationDay - 5
        val fertileEnd = ovulationDay + 1

        return when {
            cycleDay in 1..periodDuration -> {
                Pair(
                    "Règles (Jour $cycleDay/$periodDuration) 🩸",
                    "Besoins : douceur, bouillotte chaude, tisane réconfortante et câlins tendres sans pression. Évite le stress !"
                )
            }
            cycleDay in (periodDuration + 1) until fertileStart -> {
                Pair(
                    "Phase Folliculaire (Jour $cycleDay) 🌱",
                    "Regain d'énergie et de positivité ! Idéal pour organiser des sorties, de nouvelles activités ou des projets à deux."
                )
            }
            cycleDay in fertileStart..fertileEnd -> {
                if (cycleDay == ovulationDay) {
                    Pair(
                        "Pic d'Ovulation (Jour $cycleDay) ✨",
                        "Fertilité maximale ! Libido et rayonnement au sommet. Moment de grande séduction et d'intimité intense."
                    )
                } else {
                    Pair(
                        "Fenêtre Fertile (Jour $cycleDay) 🌸",
                        "Sensibilité et énergie romantiques accrues. Sorties aux chandelles et mots doux particulièrement appréciés !"
                    )
                }
            }
            cycleDay in (fertileEnd + 1)..(cycleLength - 4) -> {
                Pair(
                    "Phase Lutéale (Jour $cycleDay) 🌿",
                    "Énergie stable qui commence à ralentir. Favorisez des soirées cocooning à la maison, bon film et bons petits plats."
                )
            }
            else -> {
                Pair(
                    "Phase SPM / Pré-menstruelle (Jour $cycleDay) 🌙",
                    "Possible irritabilité ou fatigue. Écoute active, petits chocolats, massages des pieds et patience bienveillante recommandés."
                )
            }
        }
    }

    // --- Love Capsules Actions ---
    fun addLoveCapsule(
        title: String,
        message: String,
        unlockDate: Long,
        sealTheme: String = "Cœur d'Or 💛",
        audioNote: String? = null,
        photoHint: String? = null
    ) {
        val myUserId = getCurrentUserId()
        val newCapsule = LoveCapsuleEntity(
            id = "cap_" + UUID.randomUUID().toString().take(8),
            title = title,
            message = message,
            senderId = myUserId,
            senderName = _userSettings.value.displayName,
            unlockDate = unlockDate,
            isUnlocked = System.currentTimeMillis() >= unlockDate,
            isOpened = false,
            sealTheme = sealTheme,
            audioNote = audioNote,
            photoHint = photoHint
        )
        _loveCapsules.value = listOf(newCapsule) + _loveCapsules.value
    }

    fun unlockLoveCapsule(id: String) {
        _loveCapsules.value = _loveCapsules.value.map {
            if (it.id == id) it.copy(isUnlocked = true, isOpened = true) else it
        }
    }

    fun deleteLoveCapsule(id: String) {
        _loveCapsules.value = _loveCapsules.value.filterNot { it.id == id }
    }

    // --- Supabase Realtime & API Integrations ---
    fun updateSupabaseCredentials(url: String, key: String) {
        supabaseService.setCredentials(url, key)
    }

    suspend fun testSupabaseConnection(): Pair<Boolean, String> {
        return supabaseService.testConnection()
    }

    suspend fun createCoupleSpaceInSupabase(pairingCode: String, partnerName: String): Boolean {
        Log.d("RepoDiag", "createCoupleSpaceInSupabase: code=$pairingCode")
        
        // On s'assure d'avoir un UID Supabase avant d'appeler le RPC
        if (!ensureAnonymousSession(partnerName)) {
            Log.e("RepoDiag", "createCoupleSpaceInSupabase FAILED: No anonymous session")
            return false
        }

        val result = supabaseService.createCoupleSpace(pairingCode)
        return if (result != null) {
            Log.d("RepoDiag", "createCoupleSpaceInSupabase SUCCESS: $result")
            val coupleId = result.optString("id", "")
            
            // On sauvegarde l'UID réel retourné par Supabase si possible
            val session = supabaseService.supabase.auth.currentSessionOrNull()
            val actualUid = session?.user?.id ?: ""
            if (actualUid.isNotEmpty()) {
                prefs.edit().putString("user_id", actualUid).apply()
            }

            savePairingCode(
                code = pairingCode,
                partnerName = "En attente...",
                coupleId = coupleId,
                status = "waiting",
                u1Id = actualUid,
                u2Id = ""
            )
            true
        } else {
            Log.e("RepoDiag", "createCoupleSpaceInSupabase FAILED (result is null)")
            false
        }
    }

    suspend fun joinCoupleSpaceInSupabase(pairingCode: String, partnerName: String): Boolean {
        Log.d("RepoDiag", "joinCoupleSpaceInSupabase: code=$pairingCode")
        
        if (!ensureAnonymousSession(partnerName)) {
            Log.e("RepoDiag", "joinCoupleSpaceInSupabase FAILED: No anonymous session")
            return false
        }

        val result = supabaseService.joinCoupleSpace(pairingCode)
        return if (result != null) {
            Log.d("RepoDiag", "joinCoupleSpaceInSupabase SUCCESS: $result")
            val p1 = result.optString("partner1_name", result.optString("partner_1_name", "Partenaire"))
            val coupleId = result.optString("id", "")
            
            val session = supabaseService.supabase.auth.currentSessionOrNull()
            val actualUid = session?.user?.id ?: ""
            if (actualUid.isNotEmpty()) {
                prefs.edit().putString("user_id", actualUid).apply()
            }

            // Retrieve updated couple row to get accurate user1_id and user2_id
            val coupleRow = supabaseService.getCoupleSpace(pairingCode)
            val u1Id = coupleRow?.optString("user1_id", "") ?: ""
            val u2Id = coupleRow?.optString("user2_id", actualUid) ?: actualUid

            savePairingCode(
                code = pairingCode,
                partnerName = p1,
                coupleId = coupleId,
                status = "paired",
                u1Id = u1Id,
                u2Id = u2Id
            )
            updatePartnerProfile(p1, "En ligne ❤️", "💖")
            syncProfiles()
            true
        } else {
            Log.e("RepoDiag", "joinCoupleSpaceInSupabase FAILED (result is null)")
            false
        }
    }

    suspend fun checkPartnerConnectedInSupabase(pairingCode: String): String? {
        val result = supabaseService.getCoupleSpace(pairingCode)
        if (result != null) {
            val status = result.optString("status", "")
            val u1Id = result.optString("user1_id", getCurrentUserId())
            val u2Id = result.optString("user2_id", "")
            val p2Name = result.optString("partner_2_name", result.optString("partner2_name", ""))
            val coupleId = result.optString("id", "")

            val hasU1 = u1Id.isNotBlank() && u1Id != "null"
            val hasU2 = u2Id.isNotBlank() && u2Id != "null"
            val hasValidId = coupleId.isNotBlank()
            val isPaired = (status == "paired") && hasU1 && hasU2 && hasValidId
            if (isPaired) {
                var finalP2Name = p2Name.ifEmpty { "Partenaire" }
                if (finalP2Name == "Partenaire" && hasU2) {
                    val p2Profile = supabaseService.getProfile(u2Id)
                    if (p2Profile != null) {
                        finalP2Name = p2Profile.optString("display_name", finalP2Name)
                    }
                }
                savePairingCode(
                    code = pairingCode,
                    partnerName = finalP2Name,
                    coupleId = coupleId,
                    status = "paired",
                    u1Id = u1Id,
                    u2Id = u2Id
                )
                updatePartnerProfile(finalP2Name, "En ligne ❤️", "💖")
                syncProfiles()
                return finalP2Name
            }
        }
        return null
    }

    private var myProfileVersion = 0

    private var partnerProfileVersion = 0

    suspend fun syncProfiles() {
        val myUserId = getCurrentUserId()
        if (myUserId.isEmpty()) return

        // 1. Sync current user's profile
        val myProfileJson = supabaseService.getProfile(myUserId)
        if (myProfileJson != null) {
            val dispName = myProfileJson.optString("display_name", "")
            val bio = myProfileJson.optString("bio", "")
            val avatarPath = myProfileJson.optString("avatar_path", "")
            myProfileVersion = myProfileJson.optInt("avatar_version", 0)

            var finalAvatarUrl = ""
            if (avatarPath.isNotEmpty()) {
                val signedUrl = supabaseService.getSignedAvatarUrl(avatarPath)
                if (signedUrl != null) {
                    finalAvatarUrl = signedUrl
                }
            }

            _userSettings.value = _userSettings.value.copy(
                userId = myUserId,
                displayName = dispName.ifEmpty { "Moi" },
                bio = bio,
                avatarUrl = finalAvatarUrl
            )
            prefs.edit().putString("display_name", dispName).apply()
        }

        // 2. Sync partner's profile
        val pairingCode = _coupleSpace.value.pairingCode
        if (pairingCode.isNotEmpty()) {
            val spaceJson = supabaseService.getCoupleSpace(pairingCode)
            if (spaceJson != null) {
                val status = spaceJson.optString("status", "")
                val p1Id = spaceJson.optString("user1_id", "")
                val p2Id = spaceJson.optString("user2_id", "")
                val coupleId = spaceJson.optString("id", "")

                // Find partner user ID
                val partnerUserId = if (myUserId == p1Id) p2Id else p1Id

                if (partnerUserId.isNotEmpty() && (status == "connected" || status == "paired")) {
                    val partnerProfileJson = supabaseService.getProfile(partnerUserId)
                    if (partnerProfileJson != null) {
                        val partnerDispName = partnerProfileJson.optString("display_name", "")
                        val partnerBioText = partnerProfileJson.optString("bio", "")
                        val partnerAvatarPath = partnerProfileJson.optString("avatar_path", "")

                        var partnerFinalAvatarUrl = ""
                        if (partnerAvatarPath.isNotEmpty()) {
                            val signedUrl = supabaseService.getSignedAvatarUrl(partnerAvatarPath)
                            if (signedUrl != null) {
                                partnerFinalAvatarUrl = signedUrl
                            }
                        }

                        // Load private nickname if any
                        val customNickname = supabaseService.getPartnerNickname(myUserId, partnerUserId) ?: ""
                        val finalDisplayName = customNickname.ifEmpty { partnerDispName }.ifEmpty { "Mon Partenaire" }

                        _userSettings.value = _userSettings.value.copy(
                            partnerNickname = finalDisplayName,
                            partnerBio = partnerBioText,
                            partnerAvatarUrl = partnerFinalAvatarUrl,
                            partnerStatus = "En ligne"
                        )

                        _coupleSpace.value = _coupleSpace.value.copy(
                            id = coupleId,
                            partner2Name = finalDisplayName,
                            partner2Avatar = partnerFinalAvatarUrl,
                            partner2Status = "En ligne"
                        )
                        prefs.edit().putString("partner_name", finalDisplayName).apply()
                        if (coupleId.isNotEmpty()) {
                            prefs.edit().putString("couple_id", coupleId).apply()
                        }
                    }
                } else {
                    _userSettings.value = _userSettings.value.copy(
                        partnerNickname = "En attente du partenaire",
                        partnerBio = "",
                        partnerAvatarUrl = "",
                        partnerStatus = "Hors ligne"
                    )
                    _coupleSpace.value = _coupleSpace.value.copy(
                        partner2Name = "En attente du partenaire",
                        partner2Avatar = "",
                        partner2Status = "Hors ligne"
                    )
                }
            }
        }
    }

    suspend fun uploadProfileAvatar(uri: android.net.Uri): Boolean {
        val myUserId = getCurrentUserId()
        if (myUserId.isEmpty()) return false
        try {
            val bytes = compressToWebP(context, uri) ?: return false
            val avatarUuid = UUID.randomUUID().toString()
            val newPath = supabaseService.uploadAvatar(myUserId, avatarUuid, bytes)
            if (newPath != null) {
                val curProfile = supabaseService.getProfile(myUserId)
                val oldPath = curProfile?.optString("avatar_path", "") ?: ""
                val currentVer = curProfile?.optInt("avatar_version", 0) ?: 0

                val success = supabaseService.updateProfile(
                    userId = myUserId,
                    displayName = _userSettings.value.displayName,
                    bio = _userSettings.value.customStatus,
                    avatarPath = newPath,
                    avatarVersion = currentVer + 1
                )

                if (success) {
                    if (oldPath.isNotEmpty()) {
                        supabaseService.deleteAvatarFile(oldPath)
                    }
                    syncProfiles()
                    return true
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MikayalaRepository", "uploadProfileAvatar error: ${e.message}")
        }
        return false
    }

    private fun compressToWebP(context: Context, uri: android.net.Uri): ByteArray? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) return null

            val maxDim = 500
            val width = originalBitmap.width
            val height = originalBitmap.height
            val resizedBitmap = if (width > maxDim || height > maxDim) {
                val ratio = width.toFloat() / height.toFloat()
                val newWidth = if (ratio > 1) maxDim else (maxDim * ratio).toInt()
                val newHeight = if (ratio > 1) (maxDim / ratio).toInt() else maxDim
                android.graphics.Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
            } else {
                originalBitmap
            }

            val outputStream = java.io.ByteArrayOutputStream()
            val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.graphics.Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                android.graphics.Bitmap.CompressFormat.WEBP
            }
            resizedBitmap.compress(format, 80, outputStream)
            val bytes = outputStream.toByteArray()
            outputStream.close()
            bytes
        } catch (e: Exception) {
            android.util.Log.e("AvatarHelper", "compressToWebP failed: ${e.message}")
            null
        }
    }

    private fun parseTimestamp(ts: String?): Long? {
        if (ts == null || ts == "null" || ts.isEmpty()) return null
        return try {
            ts.toLongOrNull() ?: run {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                sdf.parse(ts)?.time
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun markMessagesDelivered(coupleId: String) {
        supabaseService.markMessagesDelivered(coupleId)
    }

    suspend fun markMessagesRead(coupleId: String) {
        supabaseService.markMessagesRead(coupleId)
    }

    suspend fun syncWithSupabase() {
        val couple = _coupleSpace.value
        if (!couple.isPaired || couple.id.isBlank()) return
        val coupleId = couple.id
        val pairingCode = couple.pairingCode

        // 0. Sync profiles
        syncProfiles()

        // 1. Sync messages from Supabase
        val remoteMsgs = supabaseService.fetchMessages(coupleId, pairingCode)
        if (remoteMsgs != null) {
            val fetchedList = mutableListOf<MessageEntity>()
            for (i in 0 until remoteMsgs.length()) {
                val obj = remoteMsgs.getJSONObject(i)
                val msg = parseMessageJson(obj)
                if (msg != null) {
                    fetchedList.add(msg)
                }
            }
            if (fetchedList.isNotEmpty()) {
                val remoteMap = fetchedList.associateBy { it.id }
                val updatedList = _messages.value.map { localMsg ->
                    val remoteMsg = remoteMap[localMsg.id]
                    remoteMsg ?: localMsg
                }.toMutableList()

                val localIds = _messages.value.map { it.id }.toSet()
                val brandNew = fetchedList.filterNot { localIds.contains(it.id) }
                updatedList.addAll(brandNew)

                _messages.value = updatedList.sortedBy { it.createdAt }
            }
        }

        // Ensure Realtime channel is connected
        connectRealtime()

        // 2. Sync Shared Vault from Supabase
        val remoteVault = supabaseService.fetchVaultItems(coupleId, pairingCode)
        if (remoteVault != null) {
            val fetchedVault = mutableListOf<VaultItemEntity>()
            for (i in 0 until remoteVault.length()) {
                val obj = remoteVault.getJSONObject(i)
                val id = obj.optString("id", "v_remote_$i")
                val title = obj.optString("title", "Souvenir Partagé")
                val type = obj.optString("type", "photo")
                val category = obj.optString("category", "intime")
                val caption = obj.optString("caption", "")
                val createdAt = obj.optLong("created_at", System.currentTimeMillis())

                fetchedVault.add(
                    VaultItemEntity(
                        id = id,
                        coupleId = coupleId,
                        title = title,
                        type = type,
                        category = category,
                        caption = caption,
                        dateAdded = createdAt
                    )
                )
            }
            if (fetchedVault.isNotEmpty()) {
                val existingVaultIds = _vaultItems.value.map { it.id }.toSet()
                val newVaultFromRemote = fetchedVault.filterNot { existingVaultIds.contains(it.id) }
                if (newVaultFromRemote.isNotEmpty()) {
                    _vaultItems.value = newVaultFromRemote + _vaultItems.value
                }
            }
        }
    }

    suspend fun initiateSupabaseCall(isVideo: Boolean): JSONObject? {
        val couple = _coupleSpace.value
        if (!couple.isPaired || couple.id.isBlank()) return null
        val coupleId = couple.id
        val pairingCode = couple.pairingCode
        addCallLog(isVideo, 0)
        val myName = if (couple.partner1Name.isNotEmpty()) couple.partner1Name else "Moi"
        return supabaseService.initiateCall(coupleId, myName, if (isVideo) "video" else "voice", pairingCode)
    }

    suspend fun checkForIncomingCall(): Pair<Boolean, Boolean>? {
        val couple = _coupleSpace.value
        if (!couple.isPaired || couple.id.isBlank()) return null
        val coupleId = couple.id
        val pairingCode = couple.pairingCode
        val activeCall = supabaseService.fetchActiveCall(coupleId, pairingCode)
        if (activeCall != null) {
            val status = activeCall.optString("status")
            val callerName = activeCall.optString("caller_name", if (couple.partner2Name.isNotBlank()) couple.partner2Name else "Partenaire")
            val callType = activeCall.optString("call_type", "voice")
            val isVideo = callType == "video"

            if (status == "ringing") {
                com.example.mikayala.util.NotificationHelper.showIncomingCallNotification(context, callerName, isVideo)
                return Pair(true, isVideo)
            }
        }
        return null
    }
}
