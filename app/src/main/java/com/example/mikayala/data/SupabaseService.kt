package com.example.mikayala.data

import android.util.Log
import com.example.mikayala.BuildConfig
import com.example.mikayala.data.model.MessageEntity
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.presenceDataFlow
import kotlinx.serialization.Serializable
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.json.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class SupabaseService(
    private var customUrl: String? = null,
    private var customKey: String? = null
) {
    val supabase by lazy {
        try {
            createSupabaseClient(
                supabaseUrl = customUrl?.ifEmpty { null } ?: BuildConfig.SUPABASE_URL,
                supabaseKey = customKey?.ifEmpty { null } ?: BuildConfig.SUPABASE_ANON_KEY
            ) {
                install(Auth)
                install(Postgrest)
                install(Realtime)
                install(Storage)
            }
        } catch (e: Exception) {
            Log.e("SupabaseService", "Failed to create Supabase client: ${e.message}")
            throw e
        }
    }

    val supabaseUrl: String
        get() = customUrl?.ifEmpty { null } ?: BuildConfig.SUPABASE_URL

    val supabaseKey: String
        get() = customKey?.ifEmpty { null } ?: BuildConfig.SUPABASE_ANON_KEY

    suspend fun ensureAnonymousSession(): Boolean {
        try {
            val session = supabase.auth.currentSessionOrNull()
            if (session != null) {
                Log.d("MikayalaAuth", "[AUTH] Session active trouvée pour UID: ${session.user?.id}")
                return true
            }
            
            Log.d("MikayalaAuth", "[AUTH] Aucune session active. Création d'une session anonyme Supabase...")
            supabase.auth.signInAnonymously()
            val newSession = supabase.auth.currentSessionOrNull()
            Log.d("MikayalaAuth", "[AUTH] Session anonyme créée avec succès. UID: ${newSession?.user?.id}")
            return true
        } catch (e: Exception) {
            Log.e("MikayalaAuth", "[AUTH] ensureAnonymousSession a échoué: ${e.message}")
            return false
        }
    }

    suspend fun signInWithEmail(email: String, password: String): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            Log.d("MikayalaAuth", "[AUTH] Tentative de connexion par email...")
            supabase.auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
            }
            val session = supabase.auth.currentSessionOrNull()
            val uid = session?.user?.id
            if (uid != null) {
                Log.d("MikayalaAuth", "[AUTH] Connexion email réussie. UID: $uid")
                Pair(true, null)
            } else {
                Log.w("MikayalaAuth", "[AUTH] Connexion email sans session retournée.")
                Pair(false, "Session non disponible après connexion.")
            }
        } catch (e: Exception) {
            Log.e("MikayalaAuth", "[AUTH] Échec de la connexion email: ${e.message}")
            val msg = when {
                e.message?.contains("Invalid login credentials", ignoreCase = true) == true ->
                    "Email ou mot de passe incorrect."
                e.message?.contains("Email not confirmed", ignoreCase = true) == true ->
                    "Veuillez confirmer votre email avant de vous connecter."
                else -> e.localizedMessage ?: "Erreur de connexion."
            }
            Pair(false, msg)
        }
    }

    suspend fun signUpWithEmail(email: String, password: String, displayName: String): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            Log.d("MikayalaAuth", "[AUTH] Tentative d'inscription par email...")
            supabase.auth.signUpWith(Email) {
                this.email = email.trim()
                this.password = password
            }
            val session = supabase.auth.currentSessionOrNull()
            val uid = session?.user?.id
            Log.d("MikayalaAuth", "[AUTH] Inscription email effectuée. UID: $uid")
            if (uid != null && displayName.isNotBlank()) {
                updateProfile(uid, displayName, "En ligne ❤️", "🌹", 1)
            }
            Pair(true, null)
        } catch (e: Exception) {
            Log.e("MikayalaAuth", "[AUTH] Échec de l'inscription email: ${e.message}")
            val msg = when {
                e.message?.contains("User already registered", ignoreCase = true) == true ->
                    "Un compte existe déjà avec cet email. Connectez-vous."
                e.message?.contains("Password should be at least", ignoreCase = true) == true ->
                    "Le mot de passe doit comporter au moins 6 caractères."
                else -> e.localizedMessage ?: "Erreur d'inscription."
            }
            Pair(false, msg)
        }
    }

    fun getCurrentSessionUid(): String? {
        return try {
            supabase.auth.currentSessionOrNull()?.user?.id
        } catch (e: Exception) {
            null
        }
    }

    fun getCurrentUserEmail(): String? {
        return try {
            supabase.auth.currentSessionOrNull()?.user?.email
        } catch (e: Exception) {
            null
        }
    }

    fun hasActiveSession(): Boolean {
        return try {
            supabase.auth.currentSessionOrNull() != null
        } catch (e: Exception) {
            false
        }
    }
    
    fun setCredentials(url: String, key: String) {
        customUrl = url
        customKey = key
    }

    private fun isConfigured(): Boolean {
        return supabaseUrl.isNotEmpty() &&
                supabaseKey.isNotEmpty() &&
                !supabaseUrl.contains("xyzcompany.supabase.co") &&
                !supabaseKey.contains("dummykey")
    }

    suspend fun testConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (supabaseUrl.isEmpty() || supabaseUrl.contains("xyzcompany.supabase.co")) {
            return@withContext Pair(false, "URL Supabase non configurée ou valeur par défaut.")
        }
        try {
            val url = URL("$supabaseUrl/rest/v1/")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("apikey", supabaseKey)
            conn.setRequestProperty("Authorization", "Bearer $supabaseKey")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            val code = conn.responseCode
            if (code in 200..299 || code == 404 || code == 401) {
                return@withContext Pair(true, "Connexion Supabase active (Code HTTP $code) ! ⚡")
            } else {
                return@withContext Pair(false, "Erreur Supabase HTTP $code")
            }
        } catch (e: Exception) {
            Log.e("SupabaseService", "Test connection failed: ${e.message}")
            return@withContext Pair(false, "Erreur réseau / Supabase: ${e.localizedMessage}")
        }
    }

    suspend fun createCoupleSpace(pairingCode: String): JSONObject? = withContext(Dispatchers.IO) {
        Log.d("SupabaseDiag", "[CREATE] createCoupleSpace starting...")
        
        // 1. Garantir une session active avant l'appel
        if (!ensureAnonymousSession()) {
            Log.e("SupabaseDiag", "[CREATE] Échec ensureAnonymousSession")
            return@withContext null
        }
        
        val session = supabase.auth.currentSessionOrNull()
        Log.d("SupabaseDiag", "[CREATE] Session UID active: ${session?.user?.id}")

        try {
            // Le RPC SQL n'attend que "requested_code"
            val parameters = buildJsonObject {
                put("requested_code", pairingCode)
            }
            Log.d("SupabaseDiag", "[CREATE] Appel RPC 'create_couple' avec: $parameters")
            
            val result = supabase.postgrest.rpc("create_couple", parameters)
            Log.d("SupabaseDiag", "[CREATE] Réponse brute du RPC: ${result.data}")
            
            val dataString = result.data.toString()
            if (dataString == "null" || dataString.isEmpty()) {
                Log.e("SupabaseDiag", "[CREATE] Le RPC a retourné une donnée vide")
                return@withContext null
            }
            
            // Si le retour est une liste [ {...} ], on prend le premier élément
            if (dataString.startsWith("[")) {
                val array = JSONArray(dataString)
                if (array.length() > 0) array.getJSONObject(0) else null
            } else {
                JSONObject(dataString)
            }
        } catch (e: Exception) {
            Log.e("SupabaseDiag", "[CREATE] Erreur RPC 'create_couple': ${e.message}")
            null
        }
    }

    suspend fun joinCoupleSpace(pairingCode: String): JSONObject? = withContext(Dispatchers.IO) {
        Log.d("SupabaseDiag", "[JOIN] joinCoupleSpace starting...")
        
        if (!ensureAnonymousSession()) {
            Log.e("SupabaseDiag", "[JOIN] Échec ensureAnonymousSession")
            return@withContext null
        }
        
        try {
            val parameters = buildJsonObject {
                put("requested_code", pairingCode)
            }
            Log.d("SupabaseDiag", "[JOIN] Appel RPC 'join_couple' avec: $parameters")
            
            val result = supabase.postgrest.rpc("join_couple", parameters)
            Log.d("SupabaseDiag", "[JOIN] Réponse brute du RPC: ${result.data}")
            
            val dataString = result.data.toString()
            if (dataString == "null" || dataString.isEmpty()) return@withContext null
            
            if (dataString.startsWith("[")) {
                val array = JSONArray(dataString)
                if (array.length() > 0) array.getJSONObject(0) else null
            } else {
                JSONObject(dataString)
            }
        } catch (e: Exception) {
            Log.e("SupabaseDiag", "[JOIN] Erreur RPC 'join_couple': ${e.message}")
            null
        }
    }

    suspend fun getCoupleSpace(pairingCode: String): JSONObject? = withContext(Dispatchers.IO) {
        if (!isConfigured() || pairingCode.isEmpty()) return@withContext null
        try {
            val existing = executeGet("/rest/v1/couples?pairing_code=eq.$pairingCode")
            if (existing != null && existing != "[]") {
                val array = JSONArray(existing)
                if (array.length() > 0) return@withContext array.getJSONObject(0)
            }
        } catch (e: Exception) {
            Log.e("SupabaseService", "getCoupleSpace error: ${e.message}")
        }
        return@withContext null
    }
    
    suspend fun getCoupleSpaceForUser(userId: String): JSONObject? = withContext(Dispatchers.IO) {
        val couples = getAllCouplesForUser(userId)
        // Prioritize paired couple spaces with both users present
        val paired = couples.firstOrNull {
            it.optString("status") == "paired" &&
            it.optString("user1_id").isNotBlank() && it.optString("user1_id") != "null" &&
            it.optString("user2_id").isNotBlank() && it.optString("user2_id") != "null"
        }
        paired ?: couples.firstOrNull()
    }

    suspend fun getAllCouplesForUser(userId: String): List<JSONObject> = withContext(Dispatchers.IO) {
        if (!isConfigured() || userId.isEmpty()) return@withContext emptyList()
        val results = mutableListOf<JSONObject>()
        val seenIds = mutableSetOf<String>()

        try {
            Log.d("MikayalaCouple", "[COUPLE] Recherche des couples pour UID dans Supabase...")
            // Filter by user1_id or user2_id
            val query1 = executeGet("/rest/v1/couples?user1_id=eq.$userId&order=created_at.desc")
            if (query1 != null && query1 != "[]") {
                val array = JSONArray(query1)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("id", "")
                    if (id.isNotEmpty() && seenIds.add(id)) {
                        results.add(obj)
                    }
                }
            }

            val query2 = executeGet("/rest/v1/couples?user2_id=eq.$userId&order=created_at.desc")
            if (query2 != null && query2 != "[]") {
                val array = JSONArray(query2)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("id", "")
                    if (id.isNotEmpty() && seenIds.add(id)) {
                        results.add(obj)
                    }
                }
            }
            Log.d("MikayalaCouple", "[COUPLE] Nombre de couples trouvés dans Supabase: ${results.size}")
        } catch (e: Exception) {
            Log.e("MikayalaCouple", "[COUPLE] Erreur lors de la recherche des couples: ${e.message}")
        }
        results
    }

    suspend fun fetchMessages(coupleId: String, pairingCode: String = ""): JSONArray? = withContext(Dispatchers.IO) {
        if (!isConfigured() || coupleId.isEmpty()) return@withContext null
        try {
            val res = executeGet("/rest/v1/messages?couple_id=eq.$coupleId&order=created_at.asc")
            if (res != null) return@withContext JSONArray(res)
        } catch (e: Exception) {
            Log.e("SupabaseService", "fetchMessages error: ${e.message}")
        }
        return@withContext null
    }

    private fun isValidUuid(str: String?): Boolean {
        if (str.isNullOrEmpty()) return false
        return try {
            java.util.UUID.fromString(str)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun postMessage(
        coupleId: String,
        senderId: String,
        receiverId: String,
        content: String,
        type: String = "text",
        pairingCode: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        val dummyEntity = MessageEntity(
            id = java.util.UUID.randomUUID().toString(),
            coupleId = coupleId,
            senderId = senderId,
            content = content,
            type = type
        )
        return@withContext postMessageEntity(dummyEntity)
    }

    suspend fun postMessageEntity(
        msg: MessageEntity,
        storagePath: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured() || msg.coupleId.isEmpty()) {
            Log.e("SupabaseMessage", "[MESSAGE INSERT FAILED] Supabase not configured or coupleId empty: ${msg.coupleId}")
            return@withContext false
        }

        val session = supabase.auth.currentSessionOrNull()
        val authUid = session?.user?.id ?: getCurrentSessionUid() ?: msg.senderId
        if (authUid.isEmpty()) {
            Log.e("SupabaseMessage", "[MESSAGE INSERT FAILED] No active auth session (auth.uid() is null/empty)")
            return@withContext false
        }

        try {
            val validUuid = if (isValidUuid(msg.id)) msg.id else java.util.UUID.randomUUID().toString()

            val messageType = when (msg.type.lowercase()) {
                "image", "photo", "camera" -> "image"
                "audio", "voice", "voice_note" -> "audio"
                "video" -> "video"
                else -> "text"
            }

            val body = JSONObject().apply {
                put("id", validUuid)
                put("couple_id", msg.coupleId)
                put("sender_id", authUid)
                put("content", msg.content)
                put("message_type", messageType)
                put("is_ephemeral", msg.isViewOnce)
                put("status", msg.status.ifEmpty { "sent" })

                val path = storagePath ?: msg.storagePath ?: if (!msg.mediaUrl.isNullOrEmpty() && !msg.mediaUrl.startsWith("http")) msg.mediaUrl else null
                if (!path.isNullOrEmpty()) {
                    put("storage_path", path)
                }
                val finalMediaUrl = if (!msg.mediaUrl.isNullOrEmpty() && msg.mediaUrl.startsWith("http")) {
                    msg.mediaUrl
                } else if (!path.isNullOrEmpty()) {
                    getSignedMessageMediaUrl(path)
                } else null

                if (!finalMediaUrl.isNullOrEmpty()) {
                    put("media_url", finalMediaUrl)
                }

                if (msg.duration > 0) put("audio_duration", msg.duration)
                if (!msg.replyToId.isNullOrEmpty() && isValidUuid(msg.replyToId)) put("reply_to_id", msg.replyToId)
                if (msg.reactions.isNotEmpty() && msg.reactions != "{}") {
                    try {
                        put("reactions", JSONObject(msg.reactions))
                    } catch (e: Exception) {
                        put("reactions", JSONObject())
                    }
                }
            }

            Log.d("SupabaseMessage", "Posting message to /rest/v1/messages: $body")
            val res = executePost("/rest/v1/messages", body.toString())
            if (res != null) {
                Log.d("SupabaseMessage", "MESSAGE INSERT SUCCESS: $res")
                return@withContext true
            } else {
                Log.e("SupabaseMessage", "MESSAGE INSERT FAILED: executePost returned null for payload $body")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e("SupabaseMessage", "MESSAGE INSERT FAILED exception: ${e.message}", e)
            return@withContext false
        }
    }

    suspend fun uploadMessageMedia(
        coupleId: String,
        storagePath: String,
        bytes: ByteArray,
        mimeType: String
    ): String? = withContext(Dispatchers.IO) {
        if (!isConfigured() || coupleId.isEmpty()) return@withContext null
        try {
            val bucket = "messages-media"
            val url = URL("$supabaseUrl/storage/v1/object/$bucket/$storagePath")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", supabaseKey)
            val token = getAuthToken()
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", mimeType)
            conn.setRequestProperty("x-upsert", "true")
            conn.doOutput = true
            conn.connectTimeout = 25000
            conn.readTimeout = 25000

            val os = conn.outputStream
            os.write(bytes)
            os.flush()
            os.close()

            if (conn.responseCode in 200..299) {
                Log.d("SupabaseStorage", "[STORAGE] Successfully uploaded to $bucket/$storagePath")
                return@withContext storagePath
            } else {
                val errorStream = conn.errorStream
                val errorMsg = errorStream?.bufferedReader()?.use { it.readText() } ?: "No error stream"
                Log.e("SupabaseStorage", "[STORAGE] Upload failed code ${conn.responseCode}: $errorMsg")
                null
            }
        } catch (e: Exception) {
            Log.e("SupabaseStorage", "[STORAGE] Upload exception: ${e.message}", e)
            null
        }
    }

    suspend fun deleteMessageMedia(storagePath: String): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured() || storagePath.isEmpty()) return@withContext false
        try {
            val bucket = "messages-media"
            val url = URL("$supabaseUrl/storage/v1/object/$bucket/$storagePath")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "DELETE"
            conn.setRequestProperty("apikey", supabaseKey)
            val token = getAuthToken()
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val success = conn.responseCode in 200..299
            if (success) {
                Log.d("SupabaseStorage", "[STORAGE] Deleted file $storagePath from $bucket")
            }
            success
        } catch (e: Exception) {
            Log.e("SupabaseStorage", "Failed to delete storage file $storagePath: ${e.message}")
            false
        }
    }

    suspend fun getSignedMessageMediaUrl(storagePath: String, expiresInSeconds: Int = 604800): String? = withContext(Dispatchers.IO) {
        if (!isConfigured() || storagePath.isEmpty()) return@withContext null
        try {
            val bucket = "messages-media"
            val url = URL("$supabaseUrl/storage/v1/object/sign/$bucket/$storagePath")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", supabaseKey)
            val token = getAuthToken()
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val body = JSONObject().apply {
                put("expiresIn", expiresInSeconds)
            }

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(body.toString())
            writer.flush()
            writer.close()

            if (conn.responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val sb = java.lang.StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                reader.close()
                val obj = JSONObject(sb.toString())
                val partialUrl = obj.optString("signedURL", obj.optString("signedUrl", ""))
                if (partialUrl.isNotEmpty()) {
                    val fullUrl = if (partialUrl.startsWith("http")) partialUrl else "$supabaseUrl$partialUrl"
                    Log.d("SupabaseStorage", "[STORAGE] Generated signed URL for $storagePath: $fullUrl")
                    return@withContext fullUrl
                }
            } else {
                val errorStream = conn.errorStream
                val errorMsg = errorStream?.bufferedReader()?.use { it.readText() } ?: "No error stream"
                Log.e("SupabaseStorage", "[STORAGE] getSignedMessageMediaUrl failed code ${conn.responseCode}: $errorMsg")
            }
        } catch (e: Exception) {
            Log.e("SupabaseStorage", "[STORAGE] getSignedMessageMediaUrl exception: ${e.message}", e)
        }
        return@withContext null
    }

    suspend fun updateMessageFields(messageId: String, fields: JSONObject): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured() || messageId.isEmpty()) return@withContext true
        try {
            val res = executePatch("/rest/v1/messages?id=eq.$messageId", fields.toString())
            return@withContext res != null
        } catch (e: Exception) {
            Log.e("SupabaseService", "updateMessageFields error: ${e.message}")
            return@withContext false
        }
    }

    suspend fun deleteMessage(messageId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext true
        try {
            return@withContext executeDelete("/rest/v1/messages?id=eq.$messageId")
        } catch (e: Exception) {
            Log.e("SupabaseService", "deleteMessage error: ${e.message}")
            return@withContext false
        }
    }

    suspend fun deleteMessageForEveryone(messageId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured() || messageId.isEmpty()) return@withContext true
        try {
            val body = JSONObject().apply {
                put("is_deleted_for_everyone", true)
                put("content", "Ce message a été supprimé")
            }
            val res = executePatch("/rest/v1/messages?id=eq.$messageId", body.toString())
            return@withContext res != null
        } catch (e: Exception) {
            Log.e("SupabaseService", "deleteMessageForEveryone error: ${e.message}")
            return@withContext false
        }
    }

    // --- REALTIME SUBSCRIPTION & BROADCAST ---
    private var activeRealtimeChannel: RealtimeChannel? = null
    private var activeSubscribedCoupleId: String? = null
    private var subscriptionJob: Job? = null
    private val realtimeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Serializable
    data class PresencePayload(val userId: String)

    private var activePresenceChannel: RealtimeChannel? = null
    private var presenceJob: Job? = null

    fun subscribeToPresenceRealtime(
        coupleId: String,
        myUserId: String,
        onPresenceUpdate: (List<String>) -> Unit
    ) {
        if (!isConfigured() || coupleId.isEmpty()) return

        presenceJob?.cancel()
        presenceJob = realtimeScope.launch {
            try {
                val oldChannel = activePresenceChannel
                activePresenceChannel = null
                if (oldChannel != null) {
                    try {
                        supabase.realtime.removeChannel(oldChannel)
                    } catch (ignored: Exception) {}
                }

                val channelId = "presence:couple:$coupleId"
                Log.d("SupabasePresence", "[REALTIME] Connecting to presence channel: $channelId")
                val channel = supabase.channel(channelId)
                activePresenceChannel = channel

                // Listen to presence updates
                launch {
                    channel.presenceDataFlow<PresencePayload>().collect { list ->
                        val onlineUserIds = list.map { it.userId }.distinct()
                        Log.d("SupabasePresence", "[REALTIME] Active online user IDs in presence channel: $onlineUserIds")
                        onPresenceUpdate(onlineUserIds)
                    }
                }

                channel.subscribe(blockUntilSubscribed = false)
                
                // Track our presence on join (with retry)
                launch {
                    var retries = 5
                    while (retries > 0) {
                        try {
                            channel.track(buildJsonObject { put("userId", myUserId) })
                            Log.d("SupabasePresence", "[REALTIME] Successfully tracking presence for user $myUserId")
                            break
                        } catch (e: Exception) {
                            Log.e("SupabasePresence", "[REALTIME] Failed to track presence (retries left: ${retries - 1}): ${e.message}")
                            delay(1000)
                            retries--
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SupabasePresence", "[REALTIME] Presence subscription failed: ${e.message}")
            }
        }
    }

    fun subscribeToMessagesRealtime(
        coupleId: String,
        myUserId: String,
        onInsert: (JSONObject) -> Unit,
        onUpdate: (JSONObject) -> Unit,
        onDelete: (String) -> Unit,
        onTyping: (String, Boolean) -> Unit,
        onRecording: (String, Boolean) -> Unit
    ) {
        if (!isConfigured() || coupleId.isEmpty()) return

        // If already connected to this couple and channel is active, do not re-register flows
        if (activeSubscribedCoupleId == coupleId && activeRealtimeChannel?.status?.value?.name == "SUBSCRIBED") {
            Log.d("SupabaseRealtime", "[REALTIME] Already subscribed to couple $coupleId, skipping duplicate subscribe")
            return
        }

        subscriptionJob?.cancel()
        subscriptionJob = realtimeScope.launch {
            try {
                activeSubscribedCoupleId = null
                val oldChannel = activeRealtimeChannel
                activeRealtimeChannel = null
                if (oldChannel != null) {
                    try {
                        supabase.realtime.removeChannel(oldChannel)
                    } catch (e: Exception) {
                        Log.w("SupabaseRealtime", "Error removing old channel: ${e.message}")
                    }
                }

                Log.d("SupabaseRealtime", "[REALTIME] Connecting to messages channel for couple: $coupleId")
                val channelId = "messages:couple:$coupleId"
                val channel = supabase.channel(channelId)
                activeRealtimeChannel = channel
                activeSubscribedCoupleId = coupleId

                // Listen to channel connection status
                launch {
                    channel.status.collectLatest { status ->
                        val statusName = status.name
                        Log.d("SupabaseRealtime", "[REALTIME STATUS] $statusName on $channelId")
                        when (statusName) {
                            "SUBSCRIBED" -> Log.i("SupabaseRealtime", "[REALTIME] SUBSCRIBED to $channelId successfully")
                            "CHANNEL_ERROR" -> Log.e("SupabaseRealtime", "[REALTIME] CHANNEL_ERROR on $channelId")
                            "TIMED_OUT" -> Log.w("SupabaseRealtime", "[REALTIME] TIMED_OUT on $channelId")
                            "CLOSED" -> Log.w("SupabaseRealtime", "[REALTIME] CLOSED on $channelId")
                        }
                    }
                }

                // Listen to Postgres changes for 'messages' table BEFORE subscribing
                val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "messages"
                }

                launch {
                    changeFlow.collect { action ->
                        try {
                            when (action) {
                                is PostgresAction.Insert -> {
                                    val jsonStr = action.record.toString()
                                    val obj = JSONObject(jsonStr)
                                    val msgCoupleId = obj.optString("couple_id", "")
                                    if (msgCoupleId == coupleId || msgCoupleId.isEmpty()) {
                                        Log.d("SupabaseRealtime", "[REALTIME INSERT] id=${obj.optString("id")}")
                                        onInsert(obj)
                                    }
                                }
                                is PostgresAction.Update -> {
                                    val jsonStr = action.record.toString()
                                    val obj = JSONObject(jsonStr)
                                    val msgCoupleId = obj.optString("couple_id", "")
                                    if (msgCoupleId == coupleId || msgCoupleId.isEmpty()) {
                                        Log.d("SupabaseRealtime", "[REALTIME UPDATE] id=${obj.optString("id")}")
                                        onUpdate(obj)
                                    }
                                }
                                is PostgresAction.Delete -> {
                                    val jsonStr = action.oldRecord.toString()
                                    val obj = JSONObject(jsonStr)
                                    val id = obj.optString("id", "")
                                    if (id.isNotEmpty()) {
                                        Log.d("SupabaseRealtime", "[REALTIME DELETE] id=$id")
                                        onDelete(id)
                                    }
                                }
                                else -> {}
                            }
                        } catch (e: Exception) {
                            Log.e("SupabaseRealtime", "[REALTIME] Error processing postgres change: ${e.message}")
                        }
                    }
                }

                // Broadcast typing flow
                launch {
                    channel.broadcastFlow<JsonObject>(event = "typing").collect { payload ->
                        try {
                            val senderId = payload["userId"]?.jsonPrimitive?.contentOrNull ?: ""
                            val isTyping = payload["isTyping"]?.jsonPrimitive?.booleanOrNull ?: false
                            if (senderId.isNotEmpty() && senderId != myUserId) {
                                onTyping(senderId, isTyping)
                            }
                        } catch (e: Exception) {
                            Log.e("SupabaseRealtime", "Typing payload error: ${e.message}")
                        }
                    }
                }

                // Broadcast recording flow
                launch {
                    channel.broadcastFlow<JsonObject>(event = "recording").collect { payload ->
                        try {
                            val senderId = payload["userId"]?.jsonPrimitive?.contentOrNull ?: ""
                            val isRecording = payload["isRecording"]?.jsonPrimitive?.booleanOrNull ?: false
                            if (senderId.isNotEmpty() && senderId != myUserId) {
                                onRecording(senderId, isRecording)
                            }
                        } catch (e: Exception) {
                            Log.e("SupabaseRealtime", "Recording payload error: ${e.message}")
                        }
                    }
                }

                channel.subscribe(blockUntilSubscribed = false)
            } catch (e: Exception) {
                Log.e("SupabaseRealtime", "[REALTIME] Subscription failed: ${e.message}")
            }
        }
    }

    fun broadcastTyping(isTyping: Boolean, myUserId: String) {
        val channel = activeRealtimeChannel ?: return
        realtimeScope.launch {
            try {
                channel.broadcast(
                    event = "typing",
                    message = buildJsonObject {
                        put("userId", myUserId)
                        put("isTyping", isTyping)
                    }
                )
            } catch (e: Exception) {
                Log.e("SupabaseRealtime", "Failed to broadcast typing: ${e.message}")
            }
        }
    }

    fun broadcastRecording(isRecording: Boolean, myUserId: String) {
        val channel = activeRealtimeChannel ?: return
        realtimeScope.launch {
            try {
                channel.broadcast(
                    event = "recording",
                    message = buildJsonObject {
                        put("userId", myUserId)
                        put("isRecording", isRecording)
                    }
                )
            } catch (e: Exception) {
                Log.e("SupabaseRealtime", "Failed to broadcast recording: ${e.message}")
            }
        }
    }

    fun unsubscribeRealtime() {
        try {
            subscriptionJob?.cancel()
            subscriptionJob = null
            activeSubscribedCoupleId = null
            val channel = activeRealtimeChannel
            activeRealtimeChannel = null
            if (channel != null) {
                realtimeScope.launch {
                    try {
                        supabase.realtime.removeChannel(channel)
                    } catch (e: Exception) {
                        Log.e("SupabaseRealtime", "Error in removeChannel: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseRealtime", "Error in unsubscribeRealtime: ${e.message}")
        }
    }

    // --- SUPABASE STORAGE MEDIA UPLOADS ---
    suspend fun uploadChatMedia(
        coupleId: String,
        fileName: String,
        bytes: ByteArray,
        mimeType: String
    ): String? = withContext(Dispatchers.IO) {
        if (!isConfigured() || coupleId.isEmpty()) return@withContext null
        val buckets = listOf("chat_media", "attachments", "media", "avatars")
        for (bucket in buckets) {
            try {
                val path = "$coupleId/$fileName"
                val url = URL("$supabaseUrl/storage/v1/object/$bucket/$path")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("apikey", supabaseKey)
                conn.setRequestProperty("Authorization", "Bearer $supabaseKey")
                conn.setRequestProperty("Content-Type", mimeType)
                conn.setRequestProperty("x-upsert", "true")
                conn.doOutput = true
                conn.connectTimeout = 25000
                conn.readTimeout = 25000

                val os = conn.outputStream
                os.write(bytes)
                os.flush()
                os.close()

                if (conn.responseCode in 200..299) {
                    Log.d("SupabaseStorage", "[STORAGE] Successfully uploaded $fileName to bucket $bucket")
                    return@withContext "$supabaseUrl/storage/v1/object/public/$bucket/$path"
                }
            } catch (e: Exception) {
                Log.w("SupabaseStorage", "Upload attempt to $bucket failed: ${e.message}")
            }
        }

        try {
            val path = "$coupleId/$fileName"
            supabase.storage.from("chat_media").upload(path, bytes) {
                upsert = true
            }
            return@withContext supabase.storage.from("chat_media").publicUrl(path)
        } catch (e: Exception) {
            Log.e("SupabaseStorage", "Supabase client upload fallback failed: ${e.message}")
        }

        null
    }

    // --- SHARED VAULT SUPABASE METHODS ---
    suspend fun fetchVaultItems(coupleId: String, pairingCode: String = ""): JSONArray? = withContext(Dispatchers.IO) {
        if (!isConfigured() || coupleId.isEmpty()) return@withContext null
        try {
            val res = executeGet("/rest/v1/vault_items?couple_id=eq.$coupleId&order=created_at.desc")
            if (res != null) return@withContext JSONArray(res)
        } catch (e: Exception) {
            Log.e("SupabaseService", "fetchVaultItems error: ${e.message}")
        }
        return@withContext null
    }

    suspend fun insertVaultItem(
        coupleId: String,
        senderId: String,
        title: String,
        type: String,
        category: String,
        caption: String,
        mediaUrl: String = "",
        pairingCode: String = ""
    ): JSONObject? = withContext(Dispatchers.IO) {
        if (!isConfigured() || coupleId.isEmpty()) return@withContext null
        try {
            val vId = "v_" + java.util.UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val body = JSONObject().apply {
                put("id", vId)
                put("couple_id", coupleId)
                put("title", title)
                put("type", type)
                put("media_type", type)
                put("category", category)
                put("caption", caption)
                put("media_url", mediaUrl)
                put("added_by", senderId)
                put("date_added", now)
                put("created_at", now)
            }
            val res = executePost("/rest/v1/vault_items", body.toString())
            if (res != null && res.startsWith("[")) {
                val array = JSONArray(res)
                if (array.length() > 0) return@withContext array.getJSONObject(0)
            }
        } catch (e: Exception) {
            Log.e("SupabaseService", "insertVaultItem error: ${e.message}")
        }
        return@withContext null
    }

    suspend fun deleteVaultItem(id: String): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext true
        try {
            return@withContext executeDelete("/rest/v1/vault_items?id=eq.$id")
        } catch (e: Exception) {
            Log.e("SupabaseService", "deleteVaultItem error: ${e.message}")
            return@withContext false
        }
    }

    // --- CALL SIGNALING SUPABASE METHODS ---
    suspend fun initiateCall(coupleId: String, callerName: String, callType: String, pairingCode: String = ""): JSONObject? = withContext(Dispatchers.IO) {
        if (!isConfigured() || coupleId.isEmpty()) return@withContext null
        try {
            val callId = "call_" + java.util.UUID.randomUUID().toString()
            val body = JSONObject().apply {
                put("id", callId)
                put("couple_id", coupleId)
                put("caller_name", callerName)
                put("call_type", callType) // "voice" or "video"
                put("status", "ringing") // "ringing", "active", "ended", "declined"
                put("created_at", System.currentTimeMillis())
            }
            val res = executePost("/rest/v1/call_signals", body.toString())
            if (res != null && res.startsWith("[")) {
                val array = JSONArray(res)
                if (array.length() > 0) return@withContext array.getJSONObject(0)
            }
        } catch (e: Exception) {
            Log.e("SupabaseService", "initiateCall error: ${e.message}")
        }
        return@withContext null
    }

    suspend fun fetchActiveCall(coupleId: String, pairingCode: String = ""): JSONObject? = withContext(Dispatchers.IO) {
        if (!isConfigured() || coupleId.isEmpty()) return@withContext null
        try {
            val res = executeGet("/rest/v1/call_signals?couple_id=eq.$coupleId&status=in.(ringing,active)&order=created_at.desc&limit=1")
            if (res != null) {
                val array = JSONArray(res)
                if (array.length() > 0) return@withContext array.getJSONObject(0)
            }
        } catch (e: Exception) {
            Log.e("SupabaseService", "fetchActiveCall error: ${e.message}")
        }
        return@withContext null
    }

    suspend fun updateCallStatus(callId: String, status: String): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext true
        try {
            val body = JSONObject().apply {
                put("status", status)
            }
            val res = executePatch("/rest/v1/call_signals?id=eq.$callId", body.toString())
            return@withContext res != null
        } catch (e: Exception) {
            Log.e("SupabaseService", "updateCallStatus error: ${e.message}")
            return@withContext false
        }
    }

    private fun getAuthToken(): String {
        return try {
            supabase.auth.currentSessionOrNull()?.accessToken ?: supabaseKey
        } catch (e: Exception) {
            supabaseKey
        }
    }

    private fun executeGet(path: String): String? {
        return try {
            val url = URL("$supabaseUrl$path")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("apikey", supabaseKey)
            val token = getAuthToken()
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 6000
            conn.readTimeout = 6000

            if (conn.responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val sb = java.lang.StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                reader.close()
                sb.toString()
            } else null
        } catch (e: Exception) {
            Log.e("SupabaseService", "executeGet failed: ${e.message}")
            null
        }
    }

    private fun executePost(path: String, jsonBody: String, accessToken: String? = null): String? {
        return try {
            val url = URL("$supabaseUrl$path")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", supabaseKey)
            val token = accessToken ?: getAuthToken()
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Prefer", "return=representation")
            conn.doOutput = true
            conn.connectTimeout = 6000
            conn.readTimeout = 6000

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(jsonBody)
            writer.flush()
            writer.close()

            if (conn.responseCode in 200..299) {
                if (conn.responseCode == 204) {
                    return ""
                }
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val sb = java.lang.StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                reader.close()
                sb.toString()
            } else {
                val errorStream = conn.errorStream
                val errorMessage = if (errorStream != null) {
                    val reader = BufferedReader(InputStreamReader(errorStream))
                    val sb = java.lang.StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line)
                    }
                    reader.close()
                    sb.toString()
                } else "No error stream"
                Log.w("SupabaseService", "executePost status ${conn.responseCode} on $path: $errorMessage")
                null
            }
        } catch (e: Exception) {
            Log.e("SupabaseService", "executePost failed: ${e.message}")
            null
        }
    }

    suspend fun executeRpc(rpcName: String, params: JSONObject): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext false
        try {
            val url = URL("$supabaseUrl/rest/v1/rpc/$rpcName")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", supabaseKey)
            val token = getAuthToken()
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(params.toString())
            writer.flush()
            writer.close()

            val code = conn.responseCode
            if (code in 200..299) {
                Log.d("SupabaseService", "[RPC SUCCESS] $rpcName with $params (status $code)")
                return@withContext true
            } else {
                val errorMsg = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error stream"
                Log.e("SupabaseService", "[RPC FAILED] $rpcName code $code: $errorMsg (params: $params)")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e("SupabaseService", "[RPC EXCEPTION] $rpcName: ${e.message}", e)
            return@withContext false
        }
    }

    private fun executePatch(path: String, jsonBody: String): String? {
        return try {
            val url = URL("$supabaseUrl$path")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST" // HTTP connection workaround for PATCH or use X-HTTP-Method-Override
            conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            conn.setRequestProperty("apikey", supabaseKey)
            val token = getAuthToken()
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 6000
            conn.readTimeout = 6000

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(jsonBody)
            writer.flush()
            writer.close()

            if (conn.responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val sb = java.lang.StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                reader.close()
                sb.toString()
            } else null
        } catch (e: Exception) {
            Log.e("SupabaseService", "executePatch failed: ${e.message}")
            null
        }
    }

    private fun executeDelete(path: String): Boolean {
        return try {
            val url = URL("$supabaseUrl$path")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "DELETE"
            conn.setRequestProperty("apikey", supabaseKey)
            val token = getAuthToken()
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 6000
            conn.readTimeout = 6000

            conn.responseCode in 200..299
        } catch (e: Exception) {
            Log.e("SupabaseService", "executeDelete failed: ${e.message}")
            false
        }
    }

    suspend fun signOut(token: String): Boolean = withContext(Dispatchers.IO) {
        try {
            supabase.auth.signOut()
            true
        } catch (e: Exception) {
            Log.e("SupabaseService", "signOut failed: ${e.message}")
            false
        }
    }

    suspend fun markMessagesDelivered(coupleId: String, myUserId: String = ""): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured() || coupleId.isEmpty() || coupleId == "couple_main" || !isValidUuid(coupleId)) return@withContext false
        try {
            val rpcBody = JSONObject().apply {
                put("target_couple_id", coupleId)
            }
            val success = executeRpc("mark_messages_delivered", rpcBody)
            if (success) {
                Log.d("SupabaseService", "[MIKAYALA_MESSAGING] markMessagesDelivered RPC SUCCESS for coupleId=$coupleId")
            } else {
                Log.w("SupabaseService", "[MIKAYALA_MESSAGING] markMessagesDelivered RPC FAILED for coupleId=$coupleId")
            }
            success
        } catch (e: Exception) {
            Log.e("SupabaseService", "[MIKAYALA_MESSAGING] markMessagesDelivered error: ${e.message}")
            false
        }
    }

    suspend fun markMessagesRead(coupleId: String, myUserId: String = ""): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured() || coupleId.isEmpty() || coupleId == "couple_main" || !isValidUuid(coupleId)) return@withContext false
        try {
            val rpcBody = JSONObject().apply {
                put("target_couple_id", coupleId)
            }
            val success = executeRpc("mark_messages_read", rpcBody)
            if (success) {
                Log.d("SupabaseService", "[MIKAYALA_MESSAGING] markMessagesRead RPC SUCCESS for coupleId=$coupleId")
            } else {
                Log.w("SupabaseService", "[MIKAYALA_MESSAGING] markMessagesRead RPC FAILED for coupleId=$coupleId")
            }
            success
        } catch (e: Exception) {
            Log.e("SupabaseService", "[MIKAYALA_MESSAGING] markMessagesRead error: ${e.message}")
            false
        }
    }

    suspend fun touchUserActivity(): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext false
        try {
            val success = executeRpc("touch_user_activity", JSONObject())
            if (success) {
                Log.d("SupabaseService", "[MIKAYALA_ACTIVITY] touchUserActivity RPC SUCCESS")
            }
            success
        } catch (e: Exception) {
            Log.e("SupabaseService", "[MIKAYALA_ACTIVITY] touchUserActivity error: ${e.message}")
            false
        }
    }

    suspend fun getPartnerLastSeen(partnerUserId: String): String? = withContext(Dispatchers.IO) {
        if (!isConfigured() || partnerUserId.isEmpty()) return@withContext null
        try {
            val res = executeGet("/rest/v1/user_activity?user_id=eq.$partnerUserId&select=last_seen_at")
            if (res != null && res != "[]") {
                val array = JSONArray(res)
                if (array.length() > 0) {
                    val lastSeen = array.getJSONObject(0).optString("last_seen_at", null)
                    Log.d("SupabaseService", "[MIKAYALA_PRESENCE] Partner $partnerUserId last_seen_at: $lastSeen")
                    return@withContext lastSeen
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseService", "[MIKAYALA_PRESENCE] getPartnerLastSeen error: ${e.message}")
        }
        return@withContext null
    }

    suspend fun getProfile(userId: String): JSONObject? = withContext(Dispatchers.IO) {
        if (!isConfigured() || userId.isEmpty()) return@withContext null
        try {
            val res = executeGet("/rest/v1/profiles?id=eq.$userId")
            if (res != null && res != "[]") {
                val array = JSONArray(res)
                if (array.length() > 0) return@withContext array.getJSONObject(0)
            }
        } catch (e: Exception) {
            Log.e("SupabaseService", "getProfile error: ${e.message}")
        }
        return@withContext null
    }

    suspend fun updateProfile(userId: String, displayName: String, bio: String, avatarPath: String, avatarVersion: Int = 1): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured() || userId.isEmpty()) return@withContext false
        try {
            val body = JSONObject().apply {
                put("display_name", displayName)
                put("bio", bio)
                put("avatar_path", avatarPath)
                put("avatar_version", avatarVersion)
                put("updated_at", System.currentTimeMillis())
            }
            val res = executePatch("/rest/v1/profiles?id=eq.$userId", body.toString())
            res != null
        } catch (e: Exception) {
            Log.e("SupabaseService", "updateProfile error: ${e.message}")
            false
        }
    }

    suspend fun uploadAvatar(userId: String, avatarUuid: String, bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        if (!isConfigured() || userId.isEmpty()) return@withContext null
        try {
            val url = URL("$supabaseUrl/storage/v1/object/avatars/$userId/$avatarUuid.webp")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", supabaseKey)
            conn.setRequestProperty("Authorization", "Bearer $supabaseKey")
            conn.setRequestProperty("Content-Type", "image/webp")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val os = conn.outputStream
            os.write(bytes)
            os.flush()
            os.close()

            if (conn.responseCode in 200..299) {
                return@withContext "$userId/$avatarUuid.webp"
            } else {
                Log.e("SupabaseService", "uploadAvatar failed with code: ${conn.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.e("SupabaseService", "uploadAvatar exception: ${e.message}")
            null
        }
    }

    suspend fun getSignedAvatarUrl(path: String): String? = withContext(Dispatchers.IO) {
        if (!isConfigured() || path.isEmpty()) return@withContext null
        try {
            val url = URL("$supabaseUrl/storage/v1/object/sign/avatars/$path")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", supabaseKey)
            conn.setRequestProperty("Authorization", "Bearer $supabaseKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 6000
            conn.readTimeout = 6000

            val body = JSONObject().apply {
                put("expiresIn", 3600)
            }

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(body.toString())
            writer.flush()
            writer.close()

            if (conn.responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val sb = java.lang.StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                reader.close()
                val obj = JSONObject(sb.toString())
                val partialUrl = obj.optString("signedURL", obj.optString("signedUrl", ""))
                if (partialUrl.isNotEmpty()) {
                    return@withContext if (partialUrl.startsWith("http")) {
                        partialUrl
                    } else {
                        "$supabaseUrl$partialUrl"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseService", "getSignedAvatarUrl error: ${e.message}")
        }
        return@withContext null
    }

    suspend fun deleteAvatarFile(path: String): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured() || path.isEmpty()) return@withContext true
        try {
            val url = URL("$supabaseUrl/storage/v1/object/avatars")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "DELETE"
            conn.setRequestProperty("apikey", supabaseKey)
            conn.setRequestProperty("Authorization", "Bearer $supabaseKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val body = JSONObject().apply {
                val array = JSONArray().apply { put(path) }
                put("prefixes", array)
            }

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(body.toString())
            writer.flush()
            writer.close()

            conn.responseCode in 200..299
        } catch (e: Exception) {
            Log.e("SupabaseService", "deleteAvatarFile error: ${e.message}")
            false
        }
    }

    suspend fun upsertPartnerNickname(coupleId: String, ownerId: String, partnerId: String, nickname: String): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured() || ownerId.isEmpty() || partnerId.isEmpty()) return@withContext false
        try {
            val existing = executeGet("/rest/v1/partner_nicknames?owner_user_id=eq.$ownerId&partner_user_id=eq.$partnerId")
            val body = JSONObject().apply {
                put("couple_id", coupleId)
                put("owner_user_id", ownerId)
                put("partner_user_id", partnerId)
                put("nickname", nickname)
            }
            if (existing != null && existing != "[]") {
                val res = executePatch("/rest/v1/partner_nicknames?owner_user_id=eq.$ownerId&partner_user_id=eq.$partnerId", body.toString())
                res != null
            } else {
                val res = executePost("/rest/v1/partner_nicknames", body.toString())
                res != null
            }
        } catch (e: Exception) {
            Log.e("SupabaseService", "upsertPartnerNickname error: ${e.message}")
            false
        }
    }

    suspend fun getPartnerNickname(ownerId: String, partnerId: String): String? = withContext(Dispatchers.IO) {
        if (!isConfigured() || ownerId.isEmpty() || partnerId.isEmpty()) return@withContext null
        try {
            val res = executeGet("/rest/v1/partner_nicknames?owner_user_id=eq.$ownerId&partner_user_id=eq.$partnerId")
            if (res != null && res != "[]") {
                val array = JSONArray(res)
                if (array.length() > 0) {
                    return@withContext array.getJSONObject(0).optString("nickname", "")
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseService", "getPartnerNickname error: ${e.message}")
        }
        return@withContext null
    }
}
