package com.example.mikayala.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.mikayala.data.SupabaseService
import com.example.mikayala.data.model.*
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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
    private val mediaUrlCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val _partnerIsTyping = MutableStateFlow(false)
    val partnerIsTyping: StateFlow<Boolean> = _partnerIsTyping.asStateFlow()
    val isPartnerTyping: StateFlow<Boolean> get() = partnerIsTyping

    private val _partnerIsRecordingAudio = MutableStateFlow(false)
    val partnerIsRecordingAudio: StateFlow<Boolean> = _partnerIsRecordingAudio.asStateFlow()
    val isPartnerRecordingAudio: StateFlow<Boolean> get() = partnerIsRecordingAudio

    private val _isPartnerOnline = MutableStateFlow(false)
    val isPartnerOnline: StateFlow<Boolean> = _isPartnerOnline.asStateFlow()

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

        // Load local message cache (P2P + offline messages)
        loadLocalMessagesCache()

        // Bind P2P socket receiver to store incoming P2P messages locally
        com.example.mikayala.util.P2PSocketManager.onP2PMessageReceived = { senderName, content, isFile, type ->
            val (myUserId, partnerId) = resolveCoupleMembers()
            val coupleId = _coupleSpace.value.id
            val p2pMsgId = "p2p_rcvd_${System.currentTimeMillis()}"
            val rcvdMsg = MessageEntity(
                id = p2pMsgId,
                coupleId = coupleId,
                senderId = partnerId,
                receiverId = myUserId,
                content = content,
                type = type ?: "text",
                createdAt = System.currentTimeMillis(),
                status = "delivered",
                deliveredAt = System.currentTimeMillis()
            )
            _messages.value = (_messages.value + rcvdMsg).sortedBy { it.createdAt }
            saveLocalMessagesCache()
        }

        com.example.mikayala.util.P2PSocketManager.onP2PStatusUpdated = { msgId, status ->
            _messages.value = _messages.value.map {
                if (it.id == msgId) it.copy(status = status) else it
            }
            saveLocalMessagesCache()
        }

        com.example.mikayala.util.P2PSocketManager.onP2PAllReadReceived = {
            _messages.value = _messages.value.map { msg ->
                if (isMessageFromMe(msg) && msg.status != "read") msg.copy(status = "read") else msg
            }
            saveLocalMessagesCache()
        }

        // Register network restoration listener for instant auto-sync when internet returns
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            if (cm != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: android.net.Network) {
                        Log.d("MikayalaRepository", "[NETWORK] Internet network restored -> trigger immediate syncWithSupabase()")
                        repositoryScope.launch {
                            try {
                                syncWithSupabase()
                            } catch (e: Exception) {
                                Log.w("MikayalaRepository", "Network restored sync failed: ${e.message}")
                            }
                        }
                    }
                })
            }
        } catch (e: Exception) {
            Log.w("MikayalaRepository", "Could not register NetworkCallback: ${e.message}")
        }

        // Launch periodic background sync loop to upload pending offline/P2P messages as soon as internet is available
        repositoryScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(4000)
                try {
                    syncWithSupabase()
                } catch (e: Exception) {
                    Log.w("MikayalaRepository", "Background sync loop iteration skipped: ${e.message}")
                }
            }
        }
    }

    // --- Offline & P2P Local Storage Helper Methods ---
    fun saveLocalMessagesCache() {
        try {
            val array = JSONArray()
            val list = _messages.value.takeLast(250)
            for (msg in list) {
                val obj = JSONObject().apply {
                    put("id", msg.id)
                    put("couple_id", msg.coupleId)
                    put("sender_id", msg.senderId)
                    put("receiver_id", msg.receiverId)
                    put("content", msg.content)
                    put("type", msg.type)
                    put("media_url", msg.mediaUrl ?: "")
                    put("storage_path", msg.storagePath ?: "")
                    put("duration", msg.duration)
                    put("created_at", msg.createdAt)
                    put("status", msg.status)
                    put("delivered_at", msg.deliveredAt ?: 0)
                    put("read_at", msg.readAt ?: 0)
                    put("is_ephemeral", msg.isViewOnce)
                    put("is_viewed", msg.isViewed)
                    put("is_starred", msg.isStarred)
                    put("is_pinned", msg.isPinned)
                    put("is_deleted_for_everyone", msg.isDeletedForEveryone)
                    put("deleted_for", JSONArray(msg.deletedFor))
                    put("reactions", msg.reactions)
                    put("reply_to_id", msg.replyToId ?: "")
                    put("reply_to_sender", msg.replyToSender ?: "")
                    put("reply_to_content", msg.replyToContent ?: "")
                }
                array.put(obj)
            }
            prefs.edit().putString("cached_local_messages", array.toString()).apply()
        } catch (e: Exception) {
            Log.e("MikayalaRepository", "Error saving cached local messages: ${e.message}")
        }
    }

    fun loadLocalMessagesCache() {
        try {
            val jsonStr = prefs.getString("cached_local_messages", null) ?: return
            val array = JSONArray(jsonStr)
            val loaded = mutableListOf<MessageEntity>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val deletedForArr = obj.optJSONArray("deleted_for")
                val deletedForList = if (deletedForArr != null) {
                    (0 until deletedForArr.length()).map { deletedForArr.getString(it) }
                } else emptyList<String>()

                val msg = MessageEntity(
                    id = obj.optString("id"),
                    coupleId = obj.optString("couple_id"),
                    senderId = obj.optString("sender_id"),
                    receiverId = obj.optString("receiver_id"),
                    content = obj.optString("content"),
                    type = obj.optString("type", "text"),
                    mediaUrl = obj.optString("media_url").ifEmpty { null },
                    storagePath = obj.optString("storage_path").ifEmpty { null },
                    duration = obj.optInt("duration", 0),
                    createdAt = obj.optLong("created_at", System.currentTimeMillis()),
                    status = obj.optString("status", "sent"),
                    deliveredAt = if (obj.optLong("delivered_at", 0) > 0) obj.optLong("delivered_at") else null,
                    readAt = if (obj.optLong("read_at", 0) > 0) obj.optLong("read_at") else null,
                    isViewOnce = obj.optBoolean("is_ephemeral", false),
                    isViewed = obj.optBoolean("is_viewed", false),
                    isStarred = obj.optBoolean("is_starred", false),
                    isPinned = obj.optBoolean("is_pinned", false),
                    isDeletedForEveryone = obj.optBoolean("is_deleted_for_everyone", false),
                    deletedFor = deletedForList,
                    reactions = obj.optString("reactions", "{}"),
                    replyToId = obj.optString("reply_to_id").ifEmpty { null },
                    replyToSender = obj.optString("reply_to_sender").ifEmpty { null },
                    replyToContent = obj.optString("reply_to_content").ifEmpty { null }
                )
                loaded.add(msg)
            }
            if (loaded.isNotEmpty()) {
                _messages.value = loaded.sortedBy { it.createdAt }
                Log.d("MikayalaRepository", "Loaded ${loaded.size} local cached messages from disk with full properties.")
            }
        } catch (e: Exception) {
            Log.e("MikayalaRepository", "Error loading cached local messages: ${e.message}")
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

    fun isNetworkAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            if (cm != null) {
                val activeNetwork = cm.activeNetwork ?: return false
                val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
                capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun isP2PConnected(): Boolean {
        return com.example.mikayala.util.P2PSocketManager.p2pState.value is com.example.mikayala.util.P2PState.Connected
    }

    fun getCurrentUserId(): String {
        val sessionUid = supabaseService.getCurrentSessionUid()
        val cachedUid = prefs.getString("user_id", "") ?: ""
        if (!sessionUid.isNullOrEmpty()) {
            if (sessionUid != cachedUid) {
                Log.i("MikayalaRepository", "[MIKAYALA_IDENTITY] Syncing user_id cache with auth session: old=$cachedUid -> auth=$sessionUid")
                prefs.edit().putString("user_id", sessionUid).apply()
            }
            return sessionUid
        }
        return cachedUid
    }

    fun isMessageFromMe(message: MessageEntity): Boolean {
        if (message.senderId == "me") return true
        if (message.senderId == "partner") return false

        val myUserId = getCurrentUserId()
        if (myUserId.isNotEmpty() && message.senderId == myUserId) return true

        val authUid = supabaseService.getCurrentSessionUid()
        if (!authUid.isNullOrEmpty() && message.senderId == authUid) return true

        val settingsUid = _userSettings.value.userId
        if (settingsUid.isNotEmpty() && message.senderId == settingsUid) return true

        val cachedUid = prefs.getString("user_id", "") ?: ""
        if (cachedUid.isNotEmpty() && message.senderId == cachedUid) return true

        val couple = _coupleSpace.value
        if (couple.isPaired) {
            val p1 = couple.partner1Id
            val p2 = couple.partner2Id
            val isP1 = (myUserId == p1 || authUid == p1 || settingsUid == p1 || cachedUid == p1)
            val isP2 = (myUserId == p2 || authUid == p2 || settingsUid == p2 || cachedUid == p2)
            if (isP1 && message.senderId == p1) return true
            if (isP2 && message.senderId == p2) return true
        }

        return false
    }

    fun resolveCoupleMembers(): Pair<String, String> {
        val authUid = supabaseService.getCurrentSessionUid() ?: getCurrentUserId()
        val cachedUid = prefs.getString("user_id", "") ?: ""
        val couple = _coupleSpace.value
        val user1 = couple.partner1Id
        val user2 = couple.partner2Id

        val myUserId = authUid
        val partnerUserId = when {
            myUserId == user1 && user2.isNotEmpty() -> user2
            myUserId == user2 && user1.isNotEmpty() -> user1
            user1.isNotEmpty() && myUserId != user1 -> user1
            user2.isNotEmpty() && myUserId != user2 -> user2
            else -> ""
        }

        Log.d("MikayalaRepository", "[MIKAYALA_IDENTITY] auth.uid=$authUid cached.user_id=$cachedUid couple.user1_id=$user1 couple.user2_id=$user2 resolved.myUserId=$myUserId resolved.partnerUserId=$partnerUserId")

        return Pair(myUserId, partnerUserId)
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
        val (myUserId, partnerUserId) = resolveCoupleMembers()
        if (!couple.isPaired || couple.id.isBlank() || myUserId.isBlank()) return

        supabaseService.subscribeToMessagesRealtime(
            coupleId = couple.id,
            myUserId = myUserId,
            onInsert = { obj -> handleRealtimeMessageInsert(obj) },
            onUpdate = { obj -> handleRealtimeMessageUpdate(obj) },
            onDelete = { id -> handleRealtimeMessageDelete(id) },
            onTyping = { senderId, isTyping ->
                val currentAuthUid = getCurrentUserId()
                val isPartner = senderId.isNotEmpty() && senderId != currentAuthUid
                Log.d("MikayalaRepository", "[MIKAYALA_TYPING] senderId=$senderId currentAuthUid=$currentAuthUid isPartner=$isPartner isTyping=$isTyping")
                if (isPartner) {
                    _partnerIsTyping.value = isTyping
                    typingResetJob?.cancel()
                    if (isTyping) {
                        typingResetJob = repositoryScope.launch {
                            delay(4000)
                            _partnerIsTyping.value = false
                        }
                    }
                }
            },
            onRecording = { senderId, isRecording ->
                val currentAuthUid = getCurrentUserId()
                val isPartner = senderId.isNotEmpty() && senderId != currentAuthUid
                Log.d("MikayalaRepository", "[MIKAYALA_TYPING] senderId=$senderId currentAuthUid=$currentAuthUid isPartner=$isPartner isRecording=$isRecording")
                if (isPartner) {
                    _partnerIsRecordingAudio.value = isRecording
                    recordingResetJob?.cancel()
                    if (isRecording) {
                        recordingResetJob = repositoryScope.launch {
                            delay(6000)
                            _partnerIsRecordingAudio.value = false
                        }
                    }
                }
            }
        )

        supabaseService.subscribeToPresenceRealtime(
            coupleId = couple.id,
            myUserId = myUserId,
            onPresenceUpdate = { list ->
                val (resolvedMy, resolvedPartner) = resolveCoupleMembers()
                val isPartnerOnline = if (resolvedPartner.isNotBlank()) {
                    list.contains(resolvedPartner)
                } else {
                    list.any { it != resolvedMy }
                }
                Log.d("MikayalaRepository", "[MIKAYALA_PRESENCE] myUserId=$resolvedMy partnerUserId=$resolvedPartner presenceUsers=$list isPartnerOnline=$isPartnerOnline")
                _isPartnerOnline.value = isPartnerOnline
            }
        )
    }

    fun disconnectRealtime() {
        supabaseService.unsubscribeRealtime()
        _isPartnerOnline.value = false
        _partnerIsTyping.value = false
        _partnerIsRecordingAudio.value = false
    }

    fun untrackPresence() {
        supabaseService.untrackPresence()
    }

    fun sendTypingBroadcast(isTyping: Boolean) {
        val myUserId = getCurrentUserId()
        supabaseService.broadcastTyping(isTyping, myUserId)
    }

    fun sendRecordingBroadcast(isRecording: Boolean) {
        val myUserId = getCurrentUserId()
        supabaseService.broadcastRecording(isRecording, myUserId)
    }

    fun retryFetchMediaUrl(messageId: String, storagePath: String) {
        fetchAndApplySignedUrl(messageId, storagePath, forceRefresh = true)
    }

    fun fetchAndApplySignedUrl(messageId: String, storagePath: String, forceRefresh: Boolean = false) {
        if (storagePath.isBlank()) return
        repositoryScope.launch {
            try {
                if (!forceRefresh) {
                    val cached = mediaUrlCache[storagePath]
                    if (cached != null) {
                        _messages.value = _messages.value.map {
                            if (it.id == messageId) it.copy(mediaUrl = cached) else it
                        }
                        return@launch
                    }
                }
                Log.d("MikayalaRepository", "[MIKAYALA_MEDIA] Generating signed URL for messageId=$messageId, storagePath=$storagePath, forceRefresh=$forceRefresh")
                val signed = supabaseService.getSignedMessageMediaUrl(storagePath)
                if (signed != null) {
                    mediaUrlCache[storagePath] = signed
                    _messages.value = _messages.value.map {
                        if (it.id == messageId) it.copy(mediaUrl = signed) else it
                    }
                    Log.d("MikayalaRepository", "[MIKAYALA_MEDIA] Successfully updated mediaUrl for messageId=$messageId")
                } else {
                    Log.e("MikayalaRepository", "[MIKAYALA_MEDIA_ERROR] Failed to generate signed URL for messageId=$messageId storagePath=$storagePath")
                }
            } catch (e: Exception) {
                Log.e("MikayalaRepository", "[MIKAYALA_MEDIA_ERROR] Exception in fetchAndApplySignedUrl for $messageId: ${e.message}", e)
            }
        }
    }

    private fun handleRealtimeMessageInsert(obj: JSONObject) {
        val newEntity = parseMessageJson(obj) ?: return
        val myUserId = getCurrentUserId()

        Log.d("MikayalaRepository", "[MIKAYALA_REALTIME] INSERT RECEIVED id=${newEntity.id}, sender=${newEntity.senderId}, myUserId=$myUserId, type=${newEntity.type}")

        if (newEntity.senderId != myUserId && (newEntity.status == "sent" || newEntity.deliveredAt == null)) {
            repositoryScope.launch {
                Log.d("MikayalaRepository", "[MIKAYALA_MESSAGING] Realtime INSERT from partner -> trigger markMessagesDelivered")
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
        saveLocalMessagesCache()

        if (!newEntity.storagePath.isNullOrEmpty() && newEntity.mediaUrl.isNullOrEmpty()) {
            fetchAndApplySignedUrl(newEntity.id, newEntity.storagePath)
        }
    }

    private fun handleRealtimeMessageUpdate(obj: JSONObject) {
        val updatedEntity = parseMessageJson(obj) ?: return
        Log.d("MikayalaRepository", "[MIKAYALA_REALTIME] UPDATE RECEIVED id=${updatedEntity.id}, status=${updatedEntity.status}")
        _messages.value = _messages.value.map { local ->
            if (local.id == updatedEntity.id) {
                // Keep local valid mediaUrl if remote mediaUrl was not resolved yet
                if (updatedEntity.mediaUrl.isNullOrEmpty() && !local.mediaUrl.isNullOrEmpty()) {
                    updatedEntity.copy(mediaUrl = local.mediaUrl)
                } else {
                    updatedEntity
                }
            } else {
                local
            }
        }
        saveLocalMessagesCache()
        if (!updatedEntity.storagePath.isNullOrEmpty() && updatedEntity.mediaUrl.isNullOrEmpty()) {
            fetchAndApplySignedUrl(updatedEntity.id, updatedEntity.storagePath)
        }
    }

    private fun handleRealtimeMessageDelete(id: String) {
        _messages.value = _messages.value.filterNot { it.id == id }
        saveLocalMessagesCache()
    }

    private fun parseMessageJson(obj: JSONObject): MessageEntity? {
        try {
            val id = obj.optString("id", "")
            if (id.isEmpty()) return null
            val coupleId = obj.optString("couple_id", _coupleSpace.value.id)
            val rawSenderId = obj.optString("sender_id", "")
            val myUserId = getCurrentUserId()
            val authUid = supabaseService.getCurrentSessionUid()
            val settingsUid = _userSettings.value.userId
            val cachedUid = prefs.getString("user_id", "") ?: ""

            val isSenderMe = (rawSenderId == "me") ||
                    (rawSenderId.isNotEmpty() && (rawSenderId == myUserId || (authUid != null && rawSenderId == authUid) || rawSenderId == settingsUid || rawSenderId == cachedUid)) ||
                    (_coupleSpace.value.partner1Id.isNotEmpty() && (_coupleSpace.value.partner1Id == myUserId || _coupleSpace.value.partner1Id == authUid) && rawSenderId == _coupleSpace.value.partner1Id) ||
                    (_coupleSpace.value.partner2Id.isNotEmpty() && (_coupleSpace.value.partner2Id == myUserId || _coupleSpace.value.partner2Id == authUid) && rawSenderId == _coupleSpace.value.partner2Id)

            val senderId = if (isSenderMe) (if (myUserId.isNotEmpty()) myUserId else "me") else rawSenderId
            val partnerId = if (_coupleSpace.value.partner1Id == myUserId) _coupleSpace.value.partner2Id else _coupleSpace.value.partner1Id
            val receiverId = if (isSenderMe) partnerId else myUserId

            val content = obj.optString("content", "")
            val type = obj.optString("message_type", obj.optString("type", "text"))

            val storagePath = obj.optString("storage_path", "").ifEmpty { null }
            var mediaUrl = obj.optString("media_url", "").ifEmpty { null }

            if (!storagePath.isNullOrEmpty()) {
                val cached = mediaUrlCache[storagePath]
                if (cached != null) {
                    mediaUrl = cached
                } else if (mediaUrl.isNullOrEmpty() || mediaUrl.contains("/storage/v1/object/public/messages-media/")) {
                    mediaUrl = null
                    fetchAndApplySignedUrl(id, storagePath)
                } else if (mediaUrl.startsWith("http")) {
                    mediaUrlCache[storagePath] = mediaUrl
                }
            }

            val duration = if (obj.has("audio_duration")) obj.optInt("audio_duration", 0) else obj.optInt("duration", 0)

            val createdAt = when {
                obj.has("created_at") && obj.optLong("created_at", 0L) > 0L -> obj.optLong("created_at")
                obj.has("created_at") -> parseTimestamp(obj.optString("created_at")) ?: System.currentTimeMillis()
                else -> System.currentTimeMillis()
            }
            val editedAt = if (obj.has("edited_at") && !obj.isNull("edited_at")) {
                obj.optLong("edited_at", 0L).takeIf { it > 0L } ?: parseTimestamp(obj.optString("edited_at"))
            } else null

            val deliveredAtRaw = obj.optString("delivered_at", "null")
            val readAtRaw = obj.optString("read_at", "null")
            val parsedDeliveredAt = parseTimestamp(if (deliveredAtRaw != "null" && deliveredAtRaw.isNotEmpty()) deliveredAtRaw else null)
            val parsedReadAt = parseTimestamp(if (readAtRaw != "null" && readAtRaw.isNotEmpty()) readAtRaw else null)

            val status = when {
                parsedReadAt != null || (readAtRaw != "null" && readAtRaw.isNotEmpty()) -> "read"
                parsedDeliveredAt != null || (deliveredAtRaw != "null" && deliveredAtRaw.isNotEmpty()) -> "delivered"
                else -> obj.optString("status", "sent")
            }

            val isViewOnce = obj.optBoolean("is_ephemeral", obj.optBoolean("is_view_once", false))
            val isViewed = obj.optBoolean("is_viewed", false)
            val isStarred = obj.optBoolean("is_starred", false)
            val isPinned = obj.optBoolean("is_pinned", false)
            val isDeletedForEveryone = obj.optBoolean("is_deleted_for_everyone", obj.optBoolean("deleted_for_everyone", false))
            val deletedForArray = obj.optJSONArray("deleted_for")
            val deletedForList = if (deletedForArray != null) {
                (0 until deletedForArray.length()).map { deletedForArray.getString(it) }
            } else emptyList<String>()
            val reactions = when {
                obj.has("reactions") && !obj.isNull("reactions") -> {
                    val r = obj.opt("reactions")
                    when (r) {
                        is JSONObject -> r.toString()
                        is String -> if (r.isBlank()) "{}" else r
                        else -> r?.toString() ?: "{}"
                    }
                }
                else -> "{}"
            }
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
                storagePath = storagePath,
                thumbnailUrl = null,
                duration = duration,
                createdAt = createdAt,
                editedAt = editedAt,
                status = status,
                deliveredAt = parsedDeliveredAt,
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
        val (myUserId, partnerId) = resolveCoupleMembers()
        if (!couple.isPaired || couple.id.isBlank() || myUserId.isBlank() || partnerId.isBlank()) {
            Log.w("MikayalaRepository", "[MIKAYALA_IDENTITY] Cannot send message: unverified identity or couple not paired. myUserId=$myUserId partnerId=$partnerId")
            return
        }

        val coupleId = couple.id
        val messageUuid = UUID.randomUUID().toString()
        val hasNetwork = isNetworkAvailable()
        val hasP2P = isP2PConnected()

        // Status is 'pending' ONLY when device has NO internet AND NO P2P connection
        val initialStatus = if (!hasNetwork && !hasP2P) "pending" else "sent"

        val pendingMsg = MessageEntity(
            id = messageUuid,
            coupleId = coupleId,
            senderId = myUserId,
            receiverId = partnerId,
            content = content,
            type = type,
            mediaUrl = mediaUrl,
            duration = duration,
            createdAt = System.currentTimeMillis(),
            status = initialStatus,
            isViewOnce = isViewOnce,
            replyToId = replyToId,
            replyToSender = replyToSender,
            replyToContent = replyToContent
        )
        _messages.value = _messages.value + pendingMsg
        saveLocalMessagesCache()
        Log.d("MikayalaRepository", "[MIKAYALA_MESSAGE] Message added locally: id=$messageUuid status=$initialStatus hasNetwork=$hasNetwork hasP2P=$hasP2P")

        // If connected via P2P Direct, send over P2P socket
        if (hasP2P) {
            com.example.mikayala.util.P2PSocketManager.sendMessageWithId(context, messageUuid, content)
        }

        // If connected to internet, post to Supabase
        if (hasNetwork) {
            repositoryScope.launch {
                val success = supabaseService.postMessageEntity(pendingMsg)
                Log.d("MikayalaRepository", "[MIKAYALA_MESSAGE] postMessageEntity result=$success for ID $messageUuid")
                if (success) {
                    _messages.value = _messages.value.map {
                        if (it.id == messageUuid && it.status == "pending") it.copy(status = "sent") else it
                    }
                } else {
                    Log.e("MikayalaRepository", "[MIKAYALA_MESSAGE] INSERT FAILED for ID $messageUuid - keeping status as pending for auto-sync on reconnect")
                }
                saveLocalMessagesCache()
            }
        }
    }

    fun sendVoiceNote(filePath: String, durationSeconds: Int) {
        val couple = _coupleSpace.value
        val (myUserId, partnerId) = resolveCoupleMembers()
        if (!couple.isPaired || couple.id.isBlank() || myUserId.isBlank() || partnerId.isBlank()) {
            Log.e("MikayalaRepository", "[MIKAYALA_IDENTITY] Cannot send voice note: identity not resolved. myUserId=$myUserId")
            return
        }
        val coupleId = couple.id

        Log.d("MikayalaRepository", "[MIKAYALA_AUDIO] RECORDING FINISHED path=$filePath duration=$durationSeconds myUserId=$myUserId")

        repositoryScope.launch {
            try {
                val file = File(filePath)
                if (!file.exists() || file.length() == 0L) {
                    Log.e("MikayalaRepository", "[MIKAYALA_AUDIO] Voice note file missing or empty: $filePath")
                    return@launch
                }

                // Save copy to dedicated public Mikayala folder (Style WhatsApp com.example.mikayala)
                val dedicatedFile = com.example.mikayala.util.MikayalaMediaStorage.saveVoiceNoteToDedicatedFolder(context, file)
                val playableLocalPath = dedicatedFile?.absolutePath ?: file.absolutePath

                val fileSize = file.length()
                val bytes = file.readBytes()
                val messageId = UUID.randomUUID().toString()
                val storagePath = "$coupleId/audio/$messageId.mp4"
                val hasNetwork = isNetworkAvailable()
                val hasP2P = isP2PConnected()

                val initialStatus = if (!hasNetwork && !hasP2P) "pending" else "sent"

                val pendingMsg = MessageEntity(
                    id = messageId,
                    coupleId = coupleId,
                    senderId = myUserId,
                    receiverId = partnerId,
                    content = "Message vocal 🎙️",
                    type = "audio",
                    mediaUrl = playableLocalPath,
                    storagePath = storagePath,
                    duration = durationSeconds,
                    createdAt = System.currentTimeMillis(),
                    status = initialStatus
                )
                _messages.value = _messages.value + pendingMsg
                saveLocalMessagesCache()

                // Send over P2P socket if P2P is connected
                if (hasP2P) {
                    val sizeFormatted = String.format("%.1f Mo", fileSize / (1024f * 1024f)).replace(',', '.')
                    com.example.mikayala.util.P2PSocketManager.sendFile(context, "Vocal_${messageId.take(6)}.mp4", sizeFormatted, isAudio = true)
                }

                if (hasNetwork) {
                    Log.d("MikayalaRepository", "[MIKAYALA_AUDIO] AUTH UID=$myUserId COUPLE ID=$coupleId MESSAGE ID=$messageId STORAGE PATH=$storagePath FILE SIZE=$fileSize UPLOAD START")

                    val uploadedPath = supabaseService.uploadMessageMedia(coupleId, storagePath, bytes, "audio/mp4")
                    val uploadSuccess = uploadedPath != null
                    Log.d("MikayalaRepository", "[MIKAYALA_AUDIO] UPLOAD RESULT=$uploadSuccess storagePath=$storagePath")

                    if (uploadSuccess) {
                        val signedUrl = supabaseService.getSignedMessageMediaUrl(storagePath)
                        if (signedUrl != null) {
                            mediaUrlCache[storagePath] = signedUrl
                        }
                        val finalMediaUrl = signedUrl ?: playableLocalPath
                        val updatedPending = pendingMsg.copy(mediaUrl = finalMediaUrl, storagePath = storagePath)

                        val insertSuccess = supabaseService.postMessageEntity(updatedPending, storagePath = storagePath)
                        if (insertSuccess) {
                            _messages.value = _messages.value.map {
                                if (it.id == messageId) it.copy(status = "sent", mediaUrl = finalMediaUrl, storagePath = storagePath) else it
                            }
                        }
                    }
                    saveLocalMessagesCache()
                }
            } catch (e: Exception) {
                Log.e("MikayalaRepository", "[MIKAYALA_AUDIO] Exception in sendVoiceNote: ${e.message}", e)
            }
        }
    }

    fun sendEditedImageMedia(webpBytes: ByteArray, caption: String = "", isViewOnce: Boolean = false) {
        val couple = _coupleSpace.value
        val (myUserId, partnerId) = resolveCoupleMembers()
        if (!couple.isPaired || couple.id.isBlank() || myUserId.isBlank() || partnerId.isBlank()) return
        val coupleId = couple.id

        Log.d("MikayalaRepository", "[MIKAYALA_MEDIA] Sending edited WebP photo: ${webpBytes.size} bytes, caption='$caption', viewOnce=$isViewOnce")

        repositoryScope.launch {
            try {
                val messageId = UUID.randomUUID().toString()
                val storagePath = "$coupleId/photos/$messageId.webp"

                val contentText = when {
                    caption.isNotBlank() -> caption
                    isViewOnce -> "Photo éphémère 📸"
                    else -> "Photo partagée 🖼️"
                }

                val pendingMsg = MessageEntity(
                    id = messageId,
                    coupleId = coupleId,
                    senderId = myUserId,
                    receiverId = partnerId,
                    content = contentText,
                    type = "image",
                    mediaUrl = null,
                    storagePath = storagePath,
                    isViewOnce = isViewOnce,
                    createdAt = System.currentTimeMillis(),
                    status = "pending"
                )
                _messages.value = _messages.value + pendingMsg
                Log.d("MikayalaRepository", "[MIKAYALA_MEDIA] messageId=$messageId type=image storagePath=$storagePath UPLOAD START")

                val uploadedPath = supabaseService.uploadMessageMedia(coupleId, storagePath, webpBytes, "image/webp")
                val uploadSuccess = uploadedPath != null
                if (!uploadSuccess) {
                    Log.e("MikayalaRepository", "[MIKAYALA_MEDIA] messageId=$messageId type=image storagePath=$storagePath uploadResult=false - marking as failed")
                    _messages.value = _messages.value.map {
                        if (it.id == messageId) it.copy(status = "failed") else it
                    }
                    return@launch
                }

                val signedUrl = supabaseService.getSignedMessageMediaUrl(storagePath)
                if (signedUrl != null) {
                    mediaUrlCache[storagePath] = signedUrl
                }
                val updatedPending = pendingMsg.copy(mediaUrl = signedUrl, storagePath = storagePath)

                val insertSuccess = supabaseService.postMessageEntity(updatedPending, storagePath = storagePath)
                Log.d("MikayalaRepository", "[MIKAYALA_MEDIA] messageId=$messageId type=image storagePath=$storagePath uploadResult=true signedUrl=${signedUrl != null} insertResult=$insertSuccess")

                if (insertSuccess) {
                    _messages.value = _messages.value.map {
                        if (it.id == messageId) it.copy(status = "sent", mediaUrl = signedUrl, storagePath = storagePath) else it
                    }
                } else {
                    _messages.value = _messages.value.map {
                        if (it.id == messageId) it.copy(status = "failed", mediaUrl = signedUrl, storagePath = storagePath) else it
                    }
                }
            } catch (e: Exception) {
                Log.e("MikayalaRepository", "[MIKAYALA_MEDIA] Failed to send edited image media: ${e.message}", e)
            }
        }
    }

    fun sendEditedVideoMedia(videoFile: File, caption: String = "") {
        val couple = _coupleSpace.value
        val (myUserId, partnerId) = resolveCoupleMembers()
        if (!couple.isPaired || couple.id.isBlank() || myUserId.isBlank() || partnerId.isBlank()) return
        val coupleId = couple.id

        Log.d("MikayalaRepository", "[MIKAYALA_MEDIA] Sending edited video file: ${videoFile.absolutePath}, size=${videoFile.length()} bytes")

        repositoryScope.launch {
            try {
                if (!videoFile.exists() || videoFile.length() <= 0L) {
                    Log.e("MikayalaRepository", "[MIKAYALA_MEDIA] Video file is empty or does not exist")
                    return@launch
                }

                val bytes = withContext(Dispatchers.IO) {
                    videoFile.readBytes()
                }

                val messageId = UUID.randomUUID().toString()
                val storagePath = "$coupleId/video/$messageId.mp4"
                val contentText = if (caption.isNotBlank()) caption else "Vidéo partagée 🎬"

                val pendingMsg = MessageEntity(
                    id = messageId,
                    coupleId = coupleId,
                    senderId = myUserId,
                    receiverId = partnerId,
                    content = contentText,
                    type = "video",
                    mediaUrl = null,
                    storagePath = storagePath,
                    createdAt = System.currentTimeMillis(),
                    status = "pending"
                )
                _messages.value = _messages.value + pendingMsg
                Log.d("MikayalaRepository", "[MIKAYALA_MEDIA] messageId=$messageId type=video storagePath=$storagePath UPLOAD START")

                val uploadedPath = supabaseService.uploadMessageMedia(coupleId, storagePath, bytes, "video/mp4")
                val uploadSuccess = uploadedPath != null
                if (!uploadSuccess) {
                    Log.e("MikayalaRepository", "[MIKAYALA_MEDIA] messageId=$messageId type=video storagePath=$storagePath uploadResult=false - marking as failed")
                    _messages.value = _messages.value.map {
                        if (it.id == messageId) it.copy(status = "failed") else it
                    }
                    return@launch
                }

                val signedUrl = supabaseService.getSignedMessageMediaUrl(storagePath)
                if (signedUrl != null) {
                    mediaUrlCache[storagePath] = signedUrl
                }
                val updatedPending = pendingMsg.copy(mediaUrl = signedUrl, storagePath = storagePath)

                val insertSuccess = supabaseService.postMessageEntity(updatedPending, storagePath = storagePath)
                Log.d("MikayalaRepository", "[MIKAYALA_MEDIA] messageId=$messageId type=video storagePath=$storagePath uploadResult=true signedUrl=${signedUrl != null} insertResult=$insertSuccess")

                if (insertSuccess) {
                    _messages.value = _messages.value.map {
                        if (it.id == messageId) it.copy(status = "sent", mediaUrl = signedUrl, storagePath = storagePath) else it
                    }
                    try { videoFile.delete() } catch (_: Exception) {}
                } else {
                    _messages.value = _messages.value.map {
                        if (it.id == messageId) it.copy(status = "failed", mediaUrl = signedUrl, storagePath = storagePath) else it
                    }
                }
            } catch (e: Exception) {
                Log.e("MikayalaRepository", "[MIKAYALA_MEDIA] Failed to send edited video media: ${e.message}", e)
            }
        }
    }

    fun sendImageMedia(uri: Uri, isViewOnce: Boolean = false) {
        val couple = _coupleSpace.value
        val (myUserId, partnerId) = resolveCoupleMembers()
        if (!couple.isPaired || couple.id.isBlank() || myUserId.isBlank() || partnerId.isBlank()) return
        val coupleId = couple.id

        Log.d("MikayalaRepository", "[MIKAYALA_MEDIA] PICKED uri=$uri, viewOnce=$isViewOnce")

        repositoryScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (originalBitmap == null) {
                    Log.e("MikayalaRepository", "[MIKAYALA_MEDIA] Failed to decode image from uri: $uri")
                    return@launch
                }

                val stream = ByteArrayOutputStream()
                val compressFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                originalBitmap.compress(compressFormat, 85, stream)
                val bytes = stream.toByteArray()
                originalBitmap.recycle()

                if (bytes.isEmpty()) {
                    Log.e("MikayalaRepository", "[MIKAYALA_MEDIA] WebP compression produced 0 bytes")
                    return@launch
                }

                val messageId = UUID.randomUUID().toString()
                val storagePath = "$coupleId/photos/$messageId.webp"

                val contentText = if (isViewOnce) "Photo éphémère 📸" else "Photo partagée 🖼️"
                val pendingMsg = MessageEntity(
                    id = messageId,
                    coupleId = coupleId,
                    senderId = myUserId,
                    receiverId = partnerId,
                    content = contentText,
                    type = "image",
                    mediaUrl = null,
                    storagePath = storagePath,
                    isViewOnce = isViewOnce,
                    createdAt = System.currentTimeMillis(),
                    status = "pending"
                )
                _messages.value = _messages.value + pendingMsg
                Log.d("MikayalaRepository", "[MIKAYALA_MEDIA] messageId=$messageId type=image storagePath=$storagePath UPLOAD START")

                val uploadedPath = supabaseService.uploadMessageMedia(coupleId, storagePath, bytes, "image/webp")
                val uploadSuccess = uploadedPath != null
                if (!uploadSuccess) {
                    Log.e("MikayalaRepository", "[MIKAYALA_MEDIA] messageId=$messageId type=image storagePath=$storagePath uploadResult=false - marking as failed")
                    _messages.value = _messages.value.map {
                        if (it.id == messageId) it.copy(status = "failed") else it
                    }
                    return@launch
                }

                val signedUrl = supabaseService.getSignedMessageMediaUrl(storagePath)
                if (signedUrl != null) {
                    mediaUrlCache[storagePath] = signedUrl
                }
                val updatedPending = pendingMsg.copy(mediaUrl = signedUrl, storagePath = storagePath)

                val insertSuccess = supabaseService.postMessageEntity(updatedPending, storagePath = storagePath)
                Log.d("MikayalaRepository", "[MIKAYALA_MEDIA] messageId=$messageId type=image storagePath=$storagePath uploadResult=true signedUrl=${signedUrl != null} insertResult=$insertSuccess")

                if (insertSuccess) {
                    _messages.value = _messages.value.map {
                        if (it.id == messageId) it.copy(status = "sent", mediaUrl = signedUrl, storagePath = storagePath) else it
                    }
                } else {
                    _messages.value = _messages.value.map {
                        if (it.id == messageId) it.copy(status = "failed", mediaUrl = signedUrl, storagePath = storagePath) else it
                    }
                }
            } catch (e: Exception) {
                Log.e("MikayalaRepository", "[MIKAYALA_MEDIA] Failed to send image media: ${e.message}", e)
            }
        }
    }

    fun sendCameraPhoto(bitmap: Bitmap, isViewOnce: Boolean = false) {
        val couple = _coupleSpace.value
        val (myUserId, partnerId) = resolveCoupleMembers()
        if (!couple.isPaired || couple.id.isBlank() || myUserId.isBlank() || partnerId.isBlank()) return
        val coupleId = couple.id

        Log.d("MikayalaRepository", "[MIKAYALA_MEDIA] CAMERA CAPTURED bitmap ${bitmap.width}x${bitmap.height}")

        repositoryScope.launch {
            try {
                val stream = ByteArrayOutputStream()
                val compressFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                bitmap.compress(compressFormat, 85, stream)
                val bytes = stream.toByteArray()

                val messageId = UUID.randomUUID().toString()
                val storagePath = "$coupleId/photos/$messageId.webp"

                val contentText = if (isViewOnce) "Photo instantanée éphémère 📸" else "Photo instantanée 📸"
                val pendingMsg = MessageEntity(
                    id = messageId,
                    coupleId = coupleId,
                    senderId = myUserId,
                    receiverId = partnerId,
                    content = contentText,
                    type = "image",
                    mediaUrl = null,
                    storagePath = storagePath,
                    isViewOnce = isViewOnce,
                    createdAt = System.currentTimeMillis(),
                    status = "pending"
                )
                _messages.value = _messages.value + pendingMsg
                Log.d("MikayalaRepository", "[MIKAYALA_MEDIA] messageId=$messageId type=camera_photo storagePath=$storagePath UPLOAD START")

                val uploadedPath = supabaseService.uploadMessageMedia(coupleId, storagePath, bytes, "image/webp")
                val uploadSuccess = uploadedPath != null
                if (!uploadSuccess) {
                    Log.e("MikayalaRepository", "[MIKAYALA_MEDIA] messageId=$messageId type=camera_photo storagePath=$storagePath uploadResult=false - marking as failed")
                    _messages.value = _messages.value.map {
                        if (it.id == messageId) it.copy(status = "failed") else it
                    }
                    return@launch
                }

                val signedUrl = supabaseService.getSignedMessageMediaUrl(storagePath)
                if (signedUrl != null) {
                    mediaUrlCache[storagePath] = signedUrl
                }
                val updatedPending = pendingMsg.copy(mediaUrl = signedUrl, storagePath = storagePath)

                val insertSuccess = supabaseService.postMessageEntity(updatedPending, storagePath = storagePath)
                Log.d("MikayalaRepository", "[MIKAYALA_MEDIA] messageId=$messageId type=camera_photo storagePath=$storagePath uploadResult=true signedUrl=${signedUrl != null} insertResult=$insertSuccess")

                if (insertSuccess) {
                    _messages.value = _messages.value.map {
                        if (it.id == messageId) it.copy(status = "sent", mediaUrl = signedUrl, storagePath = storagePath) else it
                    }
                } else {
                    _messages.value = _messages.value.map {
                        if (it.id == messageId) it.copy(status = "failed", mediaUrl = signedUrl, storagePath = storagePath) else it
                    }
                }
            } catch (e: Exception) {
                Log.e("MikayalaRepository", "[MIKAYALA_MEDIA] Failed to send camera photo: ${e.message}", e)
            }
        }
    }

    fun sendVideoMedia(uri: Uri) {
        val couple = _coupleSpace.value
        val (myUserId, partnerId) = resolveCoupleMembers()
        if (!couple.isPaired || couple.id.isBlank() || myUserId.isBlank() || partnerId.isBlank()) return
        val coupleId = couple.id

        Log.d("MikayalaRepository", "[MIKAYALA_MEDIA] PICKED uri=$uri")

        repositoryScope.launch {
            try {
                val mimeType = context.contentResolver.getType(uri) ?: "video/mp4"
                if (mimeType.contains("mkv") || mimeType.contains("3gp")) {
                    Log.e("MikayalaRepository", "[MIKAYALA_MEDIA] Unsupported format: $mimeType. Only MP4 is accepted.")
                    return@launch
                }

                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()

                if (bytes == null || bytes.isEmpty()) return@launch

                val messageId = UUID.randomUUID().toString()
                val storagePath = "$coupleId/video/$messageId.mp4"

                val pendingMsg = MessageEntity(
                    id = messageId,
                    coupleId = coupleId,
                    senderId = myUserId,
                    receiverId = partnerId,
                    content = "Vidéo partagée 🎬",
                    type = "video",
                    mediaUrl = null,
                    storagePath = storagePath,
                    createdAt = System.currentTimeMillis(),
                    status = "pending"
                )
                _messages.value = _messages.value + pendingMsg
                Log.d("MikayalaRepository", "[MIKAYALA_MEDIA] messageId=$messageId type=video storagePath=$storagePath UPLOAD START")

                val uploadedPath = supabaseService.uploadMessageMedia(coupleId, storagePath, bytes, "video/mp4")
                val uploadSuccess = uploadedPath != null
                if (!uploadSuccess) {
                    Log.e("MikayalaRepository", "[MIKAYALA_MEDIA] messageId=$messageId type=video storagePath=$storagePath uploadResult=false - marking as failed")
                    _messages.value = _messages.value.map {
                        if (it.id == messageId) it.copy(status = "failed") else it
                    }
                    return@launch
                }

                val signedUrl = supabaseService.getSignedMessageMediaUrl(storagePath)
                if (signedUrl != null) {
                    mediaUrlCache[storagePath] = signedUrl
                }
                val updatedPending = pendingMsg.copy(mediaUrl = signedUrl, storagePath = storagePath)

                val insertSuccess = supabaseService.postMessageEntity(updatedPending, storagePath = storagePath)
                Log.d("MikayalaRepository", "[MIKAYALA_MEDIA] messageId=$messageId type=video storagePath=$storagePath uploadResult=true signedUrl=${signedUrl != null} insertResult=$insertSuccess")

                if (insertSuccess) {
                    _messages.value = _messages.value.map {
                        if (it.id == messageId) it.copy(status = "sent", mediaUrl = signedUrl, storagePath = storagePath) else it
                    }
                } else {
                    _messages.value = _messages.value.map {
                        if (it.id == messageId) it.copy(status = "failed", mediaUrl = signedUrl, storagePath = storagePath) else it
                    }
                }
            } catch (e: Exception) {
                Log.e("MikayalaRepository", "[MIKAYALA_MEDIA] Failed to send video media: ${e.message}", e)
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
        val myUserId = getCurrentUserId().ifEmpty { "me" }
        _messages.value = _messages.value.map { msg ->
            if (msg.id == messageId) {
                try {
                    val json = if (msg.reactions.isNotBlank() && msg.reactions != "{}") JSONObject(msg.reactions) else JSONObject()
                    if (json.optString(myUserId) == emoji || json.optString("me") == emoji) {
                        json.remove(myUserId)
                        json.remove("me")
                    } else {
                        json.put(myUserId, emoji)
                    }
                    updatedReactionsJson = json.toString()
                    msg.copy(reactions = updatedReactionsJson)
                } catch (e: Exception) {
                    updatedReactionsJson = "{\"$myUserId\":\"$emoji\"}"
                    msg.copy(reactions = updatedReactionsJson)
                }
            } else msg
        }
        saveLocalMessagesCache()
        repositoryScope.launch {
            try {
                val jsonObjectToSend = try { JSONObject(updatedReactionsJson) } catch (e: Exception) { JSONObject() }
                supabaseService.updateMessageFields(
                    messageId,
                    JSONObject().apply { put("reactions", jsonObjectToSend) }
                )
            } catch (e: Exception) {
                Log.e("MikayalaRepository", "Error updating reaction in Supabase: ${e.message}")
            }
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
        saveLocalMessagesCache()
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
        saveLocalMessagesCache()
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
        val myUserId = getCurrentUserId()
        if (forEveryone) {
            _messages.value = _messages.value.map {
                if (it.id == messageId) it.copy(isDeletedForEveryone = true, content = "Ce message a été supprimé") else it
            }
            saveLocalMessagesCache()
            repositoryScope.launch {
                supabaseService.deleteMessageForEveryone(messageId)
            }
        } else {
            _messages.value = _messages.value.map {
                if (it.id == messageId) {
                    val updatedList = (it.deletedFor + myUserId).distinct()
                    it.copy(deletedFor = updatedList)
                } else it
            }
            saveLocalMessagesCache()
            repositoryScope.launch {
                val currentMsg = _messages.value.firstOrNull { it.id == messageId }
                if (currentMsg != null) {
                    val jsonArray = JSONArray().apply {
                        currentMsg.deletedFor.forEach { put(it) }
                    }
                    val fields = JSONObject().apply {
                        put("deleted_for", jsonArray)
                    }
                    supabaseService.updateMessageFields(messageId, fields)
                }
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
        prefs.edit().putString("partner_name", nickname).putString("partner_custom_nickname", nickname).apply()

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

                    if (partnerUserId.isNotEmpty()) {
                        prefs.edit().putString("partner_custom_nickname_$partnerUserId", nickname).apply()
                    }

                    if (partnerUserId.isNotEmpty() && coupleId.isNotEmpty()) {
                        try {
                            supabaseService.upsertPartnerNickname(coupleId, myUserId, partnerUserId, nickname)
                        } catch (e: Exception) {
                            Log.w("MikayalaRepository", "upsertPartnerNickname background sync skipped: ${e.message}")
                        }
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

    private fun formatLastSeen(isoStr: String): String {
        return try {
            val ts = parseTimestamp(isoStr)
            if (ts != null) {
                val now = System.currentTimeMillis()
                val diffMs = now - ts
                val diffMins = diffMs / 60000

                val nowCal = java.util.Calendar.getInstance()
                val seenCal = java.util.Calendar.getInstance().apply { timeInMillis = ts }
                val isToday = nowCal.get(java.util.Calendar.ERA) == seenCal.get(java.util.Calendar.ERA) &&
                              nowCal.get(java.util.Calendar.YEAR) == seenCal.get(java.util.Calendar.YEAR) &&
                              nowCal.get(java.util.Calendar.DAY_OF_YEAR) == seenCal.get(java.util.Calendar.DAY_OF_YEAR)

                val yesterdayCal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
                val isYesterday = yesterdayCal.get(java.util.Calendar.ERA) == seenCal.get(java.util.Calendar.ERA) &&
                                  yesterdayCal.get(java.util.Calendar.YEAR) == seenCal.get(java.util.Calendar.YEAR) &&
                                  yesterdayCal.get(java.util.Calendar.DAY_OF_YEAR) == seenCal.get(java.util.Calendar.DAY_OF_YEAR)

                val timeSdf = java.text.SimpleDateFormat("HH'h'mm", java.util.Locale.FRANCE)
                val timeFormatted = timeSdf.format(java.util.Date(ts))

                when {
                    diffMins < 2 -> "En ligne"
                    isToday -> "Vu aujourd'hui à $timeFormatted"
                    isYesterday -> "Vu hier à $timeFormatted"
                    else -> {
                        val outSdf = java.text.SimpleDateFormat("dd/MM à HH'h'mm", java.util.Locale.FRANCE)
                        "Vu le ${outSdf.format(java.util.Date(ts))}"
                    }
                }
            } else "Hors ligne"
        } catch (e: Exception) {
            "Hors ligne"
        }
    }

    suspend fun syncProfiles() {
        val myUserId = getCurrentUserId()
        if (myUserId.isEmpty()) return

        // 0. Ensure session is active before updating activity presence
        if (!supabaseService.hasActiveSession()) {
            supabaseService.ensureAnonymousSession()
        }
        if (supabaseService.hasActiveSession()) {
            supabaseService.touchUserActivity()
        }

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
                        val localNickname = prefs.getString("partner_custom_nickname_$partnerUserId", "")?.ifEmpty {
                            prefs.getString("partner_custom_nickname", "")
                        } ?: ""
                        val remoteNickname = try { supabaseService.getPartnerNickname(myUserId, partnerUserId) ?: "" } catch (e: Exception) { "" }
                        val customNickname = localNickname.ifEmpty { remoteNickname }
                        val finalDisplayName = customNickname.ifEmpty { partnerDispName }.ifEmpty { "Mon Partenaire" }

                        val partnerLastSeen = supabaseService.getPartnerLastSeen(partnerUserId)
                        val presenceStatus = if (partnerLastSeen != null) {
                            formatLastSeen(partnerLastSeen)
                        } else {
                            "En ligne"
                        }
                        Log.d("MikayalaRepository", "[MIKAYALA_PRESENCE] Partner presence status: $presenceStatus")

                        _userSettings.value = _userSettings.value.copy(
                            partnerNickname = finalDisplayName,
                            partnerBio = partnerBioText,
                            partnerAvatarUrl = partnerFinalAvatarUrl,
                            partnerStatus = presenceStatus
                        )

                        _coupleSpace.value = _coupleSpace.value.copy(
                            id = coupleId,
                            partner2Name = finalDisplayName,
                            partner2Avatar = partnerFinalAvatarUrl,
                            partner2Status = presenceStatus
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
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    try {
                        java.time.OffsetDateTime.parse(ts).toInstant().toEpochMilli()
                    } catch (e: Exception) {
                        try {
                            java.time.Instant.parse(ts).toEpochMilli()
                        } catch (e2: Exception) {
                            null
                        }
                    }
                } else null
            } ?: run {
                val clean = ts.replace("Z", "+0000").split(".")[0]
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                sdf.parse(clean)?.time
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

        // 0.1 Upload pending P2P / Offline messages to Supabase when network is back
        val (myUserId, _) = resolveCoupleMembers()
        val pendingMsgs = _messages.value.filter { (it.status == "pending" || it.status == "failed") && (it.senderId == myUserId || it.senderId == "me") }
        if (pendingMsgs.isNotEmpty()) {
            Log.d("MikayalaRepository", "Syncing ${pendingMsgs.size} pending offline/P2P messages to Supabase...")
            for (pending in pendingMsgs) {
                try {
                    val success = supabaseService.postMessageEntity(pending)
                    if (success) {
                        _messages.value = _messages.value.map {
                            if (it.id == pending.id) it.copy(status = "sent") else it
                        }
                        Log.d("MikayalaRepository", "Offline/P2P message ${pending.id} successfully synced to Supabase!")
                    }
                } catch (e: Exception) {
                    Log.w("MikayalaRepository", "Failed to sync pending message ${pending.id}: ${e.message}")
                }
            }
            saveLocalMessagesCache()
        }

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

                val finalSorted = updatedList.sortedBy { it.createdAt }
                _messages.value = finalSorted

                // Resolve any pending signed URLs for media messages
                for (msg in finalSorted) {
                    if (!msg.storagePath.isNullOrEmpty() && msg.mediaUrl.isNullOrEmpty()) {
                        fetchAndApplySignedUrl(msg.id, msg.storagePath)
                    }
                }
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
