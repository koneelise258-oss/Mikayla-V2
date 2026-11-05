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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentType

class SupabaseService(
    private var customUrl: String? = null,
    private var customKey: String? = null
) {
    companion object {
        @Volatile
        private var instance: SupabaseService? = null

        fun getInstance(customUrl: String? = null, customKey: String? = null): SupabaseService {
            return instance ?: synchronized(this) {
                instance ?: SupabaseService(customUrl, customKey).also { instance = it }
            }
        }
    }
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

    /** Dernière cause d'échec d'authentification, lisible par l'utilisateur (aucune donnée secrète). */
    @Volatile var lastAuthError: String? = null
        private set

    /**
     * Attend que la session enregistrée sur le téléphone soit rechargée.
     *
     * Au démarrage, le SDK recharge la session de manière asynchrone : tant que ce n'est pas fini,
     * currentSessionOrNull() vaut null. Sans cette attente, l'application interrogeait Supabase
     * sans identité (clé anon) : la RLS renvoyait une liste de couples VIDE, et l'application
     * effaçait le couple local ou créait un nouveau compte anonyme. Retourne true si une session existe.
     */
    suspend fun awaitSessionReady(timeoutMs: Long = 10_000L): Boolean {
        return try {
            withTimeoutOrNull(timeoutMs) { supabase.auth.awaitInitialization() }
            supabase.auth.currentSessionOrNull() != null
        } catch (e: Exception) {
            Log.w("MikayalaAuth", "[AUTH] awaitSessionReady: ${e.message}")
            false
        }
    }

    /** Jeton utilisateur réel (jamais la clé anon), ou null si aucune session n'est chargée. */
    fun userAccessTokenOrNull(): String? = try {
        supabase.auth.currentSessionOrNull()?.accessToken?.takeIf { it.isNotBlank() }
    } catch (_: Exception) { null }

    suspend fun ensureAnonymousSession(): Boolean {
        try {
            // Ne jamais conclure « aucune session » avant que la session enregistrée soit rechargée :
            // sinon un nouveau compte anonyme est créé et le téléphone perd son couple.
            awaitSessionReady()
            val session = supabase.auth.currentSessionOrNull()
            if (session != null) {
                Log.d("MikayalaAuth", "[AUTH] Session active trouvée pour UID: ${session.user?.id}")
                return true
            }
            
            Log.d("MikayalaAuth", "[AUTH] Aucune session active. Création d'une session anonyme Supabase...")
            supabase.auth.signInAnonymously()
            val newSession = supabase.auth.currentSessionOrNull()
            Log.d("MikayalaAuth", "[AUTH] Session anonyme créée avec succès. UID: ${newSession?.user?.id}")
            lastAuthError = null
            return true
        } catch (e: Exception) {
            Log.e("MikayalaAuth", "[AUTH] ensureAnonymousSession a échoué: ${e.message}")
            lastAuthError = AuthErrorExplainer.explain(e.message, isConfigured())
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
        Log.d("SupabaseService", "DEBUG: setCredentials rawUrl: $url")
        // Clean URL: remove potential trailing 'https', trailing slash, or even 'https://' if provided incorrectly
        var cleanedUrl = url.trim()
        
        // Remove 'https://' from the start to prevent double protocols
        if (cleanedUrl.startsWith("https://")) {
            cleanedUrl = cleanedUrl.substring(8)
        } else if (cleanedUrl.startsWith("http://")) {
            cleanedUrl = cleanedUrl.substring(7)
        }
        
        // Remove trailing slash
        if (cleanedUrl.endsWith("/")) {
            cleanedUrl = cleanedUrl.substring(0, cleanedUrl.length - 1)
        }

        // Re-add safe 'https://'
        cleanedUrl = "https://$cleanedUrl"

        Log.d("SupabaseService", "DEBUG: setCredentials cleanedUrl: $cleanedUrl")
        customUrl = cleanedUrl
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

    /** Résultat de la recherche des couples : une liste vide n'est fiable que dans [Found]. */
    sealed class CoupleFetch {
        data class Found(val couples: List<JSONObject>) : CoupleFetch()
        /** Réseau, serveur, RLS, session absente : on ne sait PAS s'il existe un couple. */
        data class Failed(val reason: String) : CoupleFetch()
    }

    /**
     * Recherche fiable des couples de l'utilisateur. Contrairement à [getAllCouplesForUser],
     * une erreur n'est jamais transformée en « aucun couple ».
     */
    suspend fun fetchCouplesForUser(userId: String): CoupleFetch = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext CoupleFetch.Failed("serveur non configuré")
        if (userId.isEmpty()) return@withContext CoupleFetch.Failed("utilisateur inconnu")
        awaitSessionReady()
        val token = userAccessTokenOrNull()
            ?: return@withContext CoupleFetch.Failed("session non chargée")
        val sessionUid = getCurrentSessionUid()
        if (sessionUid != null && sessionUid != userId) {
            return@withContext CoupleFetch.Failed("session d'un autre compte")
        }
        val results = mutableListOf<JSONObject>()
        val seen = mutableSetOf<String>()
        for (column in listOf("user1_id", "user2_id")) {
            val r = executeGetResult("/rest/v1/couples?$column=eq.$userId&order=created_at.desc", token)
            if (!r.ok || r.body == null) {
                Log.w("MikayalaCouple", "[COUPLE] Recherche impossible ($column) : code=${r.code} ${r.error ?: ""}")
                return@withContext CoupleFetch.Failed("HTTP ${r.code} ${r.error ?: ""}".trim())
            }
            try {
                val array = JSONArray(r.body)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("id", "")
                    if (id.isNotEmpty() && seen.add(id)) results.add(obj)
                }
            } catch (e: Exception) {
                return@withContext CoupleFetch.Failed("réponse illisible")
            }
        }
        Log.d("MikayalaCouple", "[COUPLE] Couples trouvés (réponse fiable) : ${results.size}")
        CoupleFetch.Found(results)
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

    /**
     * Messages du couple, du plus ancien au plus récent.
     * - [createdAfterIso] : seulement les messages plus récents (synchronisation légère) ;
     * - [createdBeforeIso] : page de messages plus anciens (pagination) ;
     * - sinon : les [limit] messages les plus récents (fenêtre de réconciliation).
     * Retourne null en cas d'erreur (jamais une liste vide trompeuse).
     */
    suspend fun fetchMessages(
        coupleId: String,
        pairingCode: String = "",
        createdAfterIso: String? = null,
        createdBeforeIso: String? = null,
        limit: Int = 500
    ): JSONArray? = withContext(Dispatchers.IO) {
        if (!isConfigured() || coupleId.isEmpty()) return@withContext null
        val token = userAccessTokenOrNull() ?: return@withContext null
        try {
            fun enc(v: String) = java.net.URLEncoder.encode(v, "UTF-8")
            val path = when {
                createdAfterIso != null ->
                    "/rest/v1/messages?couple_id=eq.$coupleId&created_at=gt.${enc(createdAfterIso)}&order=created_at.asc&limit=$limit"
                createdBeforeIso != null ->
                    "/rest/v1/messages?couple_id=eq.$coupleId&created_at=lt.${enc(createdBeforeIso)}&order=created_at.desc&limit=$limit"
                else ->
                    "/rest/v1/messages?couple_id=eq.$coupleId&order=created_at.desc&limit=$limit"
            }
            val r = executeGetResult(path, token)
            if (!r.ok || r.body == null) {
                Log.w("SupabaseService", "fetchMessages HTTP ${r.code}: ${r.error}")
                return@withContext null
            }
            val array = JSONArray(r.body)
            if (createdAfterIso != null) return@withContext array
            // Requêtes triées du plus récent au plus ancien : on remet dans l'ordre chronologique
            val asc = JSONArray()
            for (i in array.length() - 1 downTo 0) asc.put(array.get(i))
            return@withContext asc
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
                // Toujours « sent » côté serveur : l'état local « pending »/« failed » ne doit jamais
                // être publié (sinon mark_messages_delivered, qui cherche status = 'sent', ne le voit pas).
                put("status", "sent")

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

            Log.d("SupabaseMessage", "Posting message id=$validUuid type=$messageType")
            val res = executePostResult("/rest/v1/messages", body.toString())
            if (res.ok) {
                Log.d("SupabaseMessage", "MESSAGE INSERT SUCCESS id=$validUuid")
                return@withContext true
            }
            // 409 = l'identifiant existe déjà : un envoi précédent a réussi mais sa réponse s'est perdue
            // (coupure réseau). Avant, le message restait « en attente » et était renvoyé à l'infini.
            if (res.code == 409 || (res.error ?: "").contains("23505")) {
                val existing = executeGet("/rest/v1/messages?id=eq.$validUuid&select=id")
                if (existing != null && existing.trim() != "[]") {
                    Log.d("SupabaseMessage", "MESSAGE ALREADY STORED id=$validUuid (renvoi idempotent)")
                    return@withContext true
                }
            }
            Log.e("SupabaseMessage", "MESSAGE INSERT FAILED id=$validUuid code=${res.code}: ${res.error}")
            return@withContext false
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
                    val fullUrl = when {
                        partialUrl.startsWith("http://") || partialUrl.startsWith("https://") -> partialUrl
                        partialUrl.startsWith("/storage/v1") -> "$supabaseUrl$partialUrl"
                        partialUrl.startsWith("/") -> "$supabaseUrl/storage/v1$partialUrl"
                        else -> "$supabaseUrl/storage/v1/$partialUrl"
                    }
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
        if (!isConfigured() || messageId.isEmpty()) return@withContext false
        try {
            val res = executePatch("/rest/v1/messages?id=eq.$messageId", fields.toString())
            // « [] » = aucune ligne modifiée (RLS refuse ou message absent) : ce n'est PAS un succès
            return@withContext res != null && res.trim().let { it.isNotEmpty() && it != "[]" }
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
            return@withContext res != null && res.trim().let { it.isNotEmpty() && it != "[]" }
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
    @Volatile private var activePresenceCoupleId: String? = null
    @Volatile private var presenceSubscribeAttemptAt = 0L
    @Volatile private var messagesSubscribeAttemptAt = 0L

    fun subscribeToPresenceRealtime(
        coupleId: String,
        myUserId: String,
        onPresenceUpdate: (List<String>) -> Unit
    ) {
        if (!isConfigured() || coupleId.isEmpty()) return
        if (userAccessTokenOrNull() == null) return

        // Même protection que pour les messages : recréer le canal toutes les 4 s faisait
        // clignoter le partenaire « en ligne / hors ligne ».
        val st = activePresenceChannel?.status?.value
        val sinceAttempt = System.currentTimeMillis() - presenceSubscribeAttemptAt
        if (activePresenceCoupleId == coupleId && (st == RealtimeChannel.Status.SUBSCRIBED ||
                (st == RealtimeChannel.Status.SUBSCRIBING && sinceAttempt < 45_000L))) {
            return
        }
        presenceSubscribeAttemptAt = System.currentTimeMillis()
        activePresenceCoupleId = coupleId

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

                channel.subscribe(blockUntilSubscribed = false)

                // Listen to presence updates
                launch {
                    channel.presenceDataFlow<PresencePayload>().collect { list ->
                        val onlineUserIds = list.map { it.userId }.distinct()
                        Log.d("SupabasePresence", "[REALTIME] Active online user IDs in presence channel: $onlineUserIds")
                        onPresenceUpdate(onlineUserIds)
                    }
                }

                // Subscribe and wait until SUBSCRIBED before calling track()
                launch {
                    channel.status.collectLatest { status ->
                        Log.d("SupabasePresence", "[REALTIME STATUS] Presence channel status: ${status.name}")
                        if (status.name == "SUBSCRIBED") {
                            try {
                                channel.track(buildJsonObject { put("userId", myUserId) })
                                Log.d("SupabasePresence", "[REALTIME] Successfully tracking presence for user $myUserId after SUBSCRIBED")
                            } catch (e: Exception) {
                                Log.e("SupabasePresence", "[REALTIME] Failed to track presence after SUBSCRIBED: ${e.message}")
                            }
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
        onRecording: (String, Boolean) -> Unit,
        onSubscribed: () -> Unit = {}
    ) {
        if (!isConfigured() || coupleId.isEmpty()) return
        if (userAccessTokenOrNull() == null) {
            // Sans jeton utilisateur, la RLS filtrerait tous les événements : canal muet.
            Log.w("SupabaseRealtime", "[REALTIME] Session non chargée : abonnement reporté")
            return
        }

        // Canal déjà actif OU en cours de (re)connexion pour ce couple : ne rien casser.
        // Avant, la synchronisation (toutes les 2,5–4 s) détruisait et recréait le canal pendant
        // sa reconnexion (délai SDK 7 s) : il n'était plus jamais abonné et les messages n'arrivaient plus.
        val st = activeRealtimeChannel?.status?.value
        val sinceAttempt = System.currentTimeMillis() - messagesSubscribeAttemptAt
        if (activeSubscribedCoupleId == coupleId && (st == RealtimeChannel.Status.SUBSCRIBED ||
                (st == RealtimeChannel.Status.SUBSCRIBING && sinceAttempt < 45_000L))) {
            return
        }
        messagesSubscribeAttemptAt = System.currentTimeMillis()

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
                            "SUBSCRIBED" -> {
                                Log.i("SupabaseRealtime", "[REALTIME] SUBSCRIBED to $channelId successfully")
                                // (Ré)abonné : rattraper ce qui a pu arriver pendant la coupure
                                try { onSubscribed() } catch (_: Exception) {}
                            }
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

    fun untrackPresence() {
        val channel = activePresenceChannel ?: return
        realtimeScope.launch {
            try {
                channel.untrack()
                Log.d("SupabasePresence", "[REALTIME] Presence untrack() called successfully")
            } catch (e: Exception) {
                Log.w("SupabasePresence", "[REALTIME] untrack() error: ${e.message}")
            }
        }
    }

    fun unsubscribePresence() {
        try {
            presenceJob?.cancel()
            presenceJob = null
            activePresenceCoupleId = null
            val channel = activePresenceChannel
            activePresenceChannel = null
            if (channel != null) {
                realtimeScope.launch {
                    try {
                        channel.untrack()
                    } catch (ignored: Exception) {}
                    try {
                        supabase.realtime.removeChannel(channel)
                        Log.d("SupabasePresence", "[REALTIME] Presence channel removed cleanly")
                    } catch (e: Exception) {
                        Log.w("SupabasePresence", "Error in removeChannel for presence: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SupabasePresence", "Error in unsubscribePresence: ${e.message}")
        }
    }

    fun unsubscribeRealtime() {
        unsubscribePresence()
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
        // Uniquement chat_media : l'ancien essai en cascade (attachments, media, avatars) pouvait déposer
        // un document de conversation dans le stockage des photos de profil.
        val buckets = listOf("chat_media")
        for (bucket in buckets) for (token in storageAuthTokens()) {
            try {
                val path = "$coupleId/$fileName"
                val url = URL("$supabaseUrl/storage/v1/object/$bucket/$path")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("apikey", supabaseKey)
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
                    Log.d("SupabaseStorage", "[STORAGE] Upload OK ($bucket, ${if (token == supabaseKey) "repli anon" else "session"})")
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

    /**
     * Jetons à essayer pour Storage : d'abord la session utilisateur (JWT, rôle authenticated),
     * puis la clé anon en repli TRANSITOIRE tant que les politiques Storage actuelles l'exigent.
     * Une fois l'upload anonyme supprimé côté Supabase, le repli échouera simplement (401/403).
     */
    internal fun storageAuthTokens(): List<String> {
        val session = try { supabase.auth.currentSessionOrNull()?.accessToken } catch (_: Exception) { null }
        return listOfNotNull(session?.takeIf { it.isNotBlank() }, supabaseKey).distinct()
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
            val fullUrl = if (path.startsWith("http://") || path.startsWith("https://")) {
                path
            } else {
                "$supabaseUrl$path"
            }
            val url = URL(fullUrl)
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

    /** Réponse HTTP brute : permet de distinguer « vide » (succès) d'une erreur. */
    data class HttpResult(val code: Int, val body: String?, val error: String?) {
        val ok: Boolean get() = code in 200..299
    }

    private fun readAll(stream: java.io.InputStream?): String? =
        stream?.bufferedReader()?.use { it.readText() }

    private fun executeGetResult(path: String, tokenOverride: String? = null): HttpResult {
        return try {
            val fullUrl = if (path.startsWith("http")) path else "$supabaseUrl$path"
            val conn = URL(fullUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("apikey", supabaseKey)
            conn.setRequestProperty("Authorization", "Bearer ${tokenOverride ?: getAuthToken()}")
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            val code = conn.responseCode
            if (code in 200..299) HttpResult(code, readAll(conn.inputStream), null)
            else HttpResult(code, null, readAll(conn.errorStream)?.take(300))
        } catch (e: Exception) {
            HttpResult(-1, null, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun executePostResult(path: String, jsonBody: String, prefer: String = "return=representation"): HttpResult {
        return try {
            val fullUrl = if (path.startsWith("http")) path else "$supabaseUrl$path"
            val conn = URL(fullUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", supabaseKey)
            conn.setRequestProperty("Authorization", "Bearer ${getAuthToken()}")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Prefer", prefer)
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            OutputStreamWriter(conn.outputStream).use { it.write(jsonBody) }
            val code = conn.responseCode
            if (code in 200..299) HttpResult(code, if (code == 204) "" else readAll(conn.inputStream), null)
            else HttpResult(code, null, readAll(conn.errorStream)?.take(300))
        } catch (e: Exception) {
            HttpResult(-1, null, e.message ?: e.javaClass.simpleName)
        }
    }

    /** Client HTTP capable d'envoyer un vrai PATCH (HttpURLConnection ne le sait pas). */
    private val restHttp by lazy {
        io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp) { expectSuccess = false }
    }

    private fun executePost(
        path: String,
        jsonBody: String,
        accessToken: String? = null,
        prefer: String = "return=representation"
    ): String? {
        return try {
            val fullUrl = if (path.startsWith("http://") || path.startsWith("https://")) {
                path
            } else {
                "$supabaseUrl$path"
            }
            val url = URL(fullUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", supabaseKey)
            val token = accessToken ?: getAuthToken()
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Prefer", prefer)
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

    /**
     * Vraie requête HTTP PATCH (mise à jour).
     *
     * Avant : POST + en-tête « X-HTTP-Method-Override: PATCH ». PostgREST (Supabase) ignore cet
     * en-tête et traite la requête comme un POST, c'est-à-dire une INSERTION d'une ligne incomplète,
     * refusée. Conséquence : réactions, modifications, suppression pour les deux, coups de jeu,
     * profil, surnoms, statut des appels et replis des accusés n'étaient jamais enregistrés.
     * Retourne le corps (« [] » si aucune ligne modifiée) ou null en cas d'erreur.
     */
    private suspend fun executePatch(path: String, jsonBody: String): String? {
        val fullUrl = if (path.startsWith("http://") || path.startsWith("https://")) path else "$supabaseUrl$path"
        return try {
            val response = restHttp.request(fullUrl) {
                method = io.ktor.http.HttpMethod.Patch
                headers {
                    append("apikey", supabaseKey)
                    append(io.ktor.http.HttpHeaders.Authorization, "Bearer ${getAuthToken()}")
                    append("Prefer", "return=representation")
                }
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody(jsonBody)
            }
            val text = response.bodyAsText()
            if (response.status.value in 200..299) text else {
                Log.w("SupabaseService", "executePatch HTTP ${response.status.value} on ${path.substringBefore('?')}: ${text.take(300)}")
                null
            }
        } catch (e: Exception) {
            Log.e("SupabaseService", "executePatch failed: ${e.message}")
            null
        }
    }

    private fun executeDelete(path: String): Boolean {
        return try {
            val fullUrl = if (path.startsWith("http://") || path.startsWith("https://")) {
                path
            } else {
                "$supabaseUrl$path"
            }
            val url = URL(fullUrl)
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

    private fun executeDeleteWithRepresentation(path: String): Boolean {
        return try {
            val fullUrl = if (path.startsWith("http://") || path.startsWith("https://")) {
                path
            } else {
                "$supabaseUrl$path"
            }
            val url = URL(fullUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "DELETE"
            conn.setRequestProperty("apikey", supabaseKey)
            val token = getAuthToken()
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Prefer", "return=representation")
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
                val responseText = sb.toString().trim()
                if (responseText.startsWith("[")) {
                    true
                } else {
                    responseText.isEmpty() || conn.responseCode == 204
                }
            } else false
        } catch (e: Exception) {
            Log.e("SupabaseService", "executeDeleteWithRepresentation failed: ${e.message}")
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

    private fun nowIso(): String {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(Date())
    }

    suspend fun markMessagesDelivered(coupleId: String, myUserId: String = ""): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured() || coupleId.isEmpty() || coupleId == "couple_main" || !isValidUuid(coupleId)) return@withContext false
        try {
            val rpcBody = JSONObject().apply {
                put("target_couple_id", coupleId)
            }
            var success = executeRpc("mark_messages_delivered", rpcBody)
            if (!success) {
                // Repli : uniquement les messages REÇUS (jamais les miens), avec l'heure de réception
                val me = myUserId.ifEmpty { getCurrentSessionUid() ?: "" }
                if (me.isNotEmpty()) {
                    val url = "$supabaseUrl/rest/v1/messages?couple_id=eq.$coupleId&sender_id=neq.$me&status=eq.sent"
                    val body = JSONObject().apply { put("status", "delivered"); put("delivered_at", nowIso()) }.toString()
                    success = executePatch(url, body) != null
                }
            }
            if (success) {
                Log.d("SupabaseService", "[MIKAYALA_MESSAGING] markMessagesDelivered SUCCESS for coupleId=$coupleId")
            } else {
                Log.w("SupabaseService", "[MIKAYALA_MESSAGING] markMessagesDelivered FAILED for coupleId=$coupleId")
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
            var success = executeRpc("mark_messages_read", rpcBody)
            if (!success) {
                // Repli : uniquement les messages REÇUS ; « sent » aussi (lu sans accusé de réception préalable)
                val me = myUserId.ifEmpty { getCurrentSessionUid() ?: "" }
                if (me.isNotEmpty()) {
                    val now = nowIso()
                    val url = "$supabaseUrl/rest/v1/messages?couple_id=eq.$coupleId&sender_id=neq.$me&status=in.(sent,delivered)"
                    val body = JSONObject().apply { put("status", "read"); put("read_at", now) }.toString()
                    success = executePatch(url, body) != null
                }
            }
            if (success) {
                Log.d("SupabaseService", "[MIKAYALA_MESSAGING] markMessagesRead SUCCESS for coupleId=$coupleId")
            } else {
                Log.w("SupabaseService", "[MIKAYALA_MESSAGING] markMessagesRead FAILED for coupleId=$coupleId")
            }
            success
        } catch (e: Exception) {
            Log.e("SupabaseService", "[MIKAYALA_MESSAGING] markMessagesRead error: ${e.message}")
            false
        }
    }

    suspend fun touchUserActivity(): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext false
        val currentSession = supabase.auth.currentSessionOrNull()
        if (currentSession == null || currentSession.user?.id.isNullOrEmpty()) {
            // User is not authenticated in Supabase Auth yet; skip RPC to avoid 400 'Non authentifié'
            return@withContext false
        }
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
                    val lastSeen = array.getJSONObject(0).optString("last_seen_at", "")
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
        for (token in storageAuthTokens()) try {
            val url = URL("$supabaseUrl/storage/v1/object/avatars/$userId/$avatarUuid.webp")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", supabaseKey)
            conn.setRequestProperty("Authorization", "Bearer $token")
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
        null
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

    // --- NATIVE ANDROID FCM PUSH TOKEN & DISPATCH ---

    suspend fun upsertDevicePushToken(fcmToken: String, deviceLabel: String = "Android", installationSecret: String? = null): Boolean = withContext(Dispatchers.IO) {
        val uid = getCurrentSessionUid()
        if (uid.isNullOrEmpty() || fcmToken.isBlank()) {
            Log.w("FCM_TOKEN", "upsertDevicePushToken ignored: session non authentifiée ou jeton vide")
            return@withContext false
        }
        val maskedToken = if (fcmToken.length > 10) "${fcmToken.take(6)}...${fcmToken.takeLast(4)}" else "***"
        Log.d("FCM_TOKEN", "Enregistrement sécurisé du jeton FCM pour UID=$uid ($maskedToken)")
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val nowIso = sdf.format(Date())
            val body = JSONObject().apply {
                put("user_id", uid)
                put("fcm_token", fcmToken.trim())
                put("platform", "android")
                put("device_label", deviceLabel)
                put("updated_at", nowIso)
                put("last_seen_at", nowIso)
            }
            // 1) Fonction register_device_push_token : la possession du jeton est prouvée par le secret
            //    d'installation (voir DeviceInstallation). Tant qu'elle n'est pas déployée, l'appel
            //    échoue et on se replie sur l'ancien upsert, sans changement de comportement.
            if (!installationSecret.isNullOrBlank()) {
                val rpcBody = JSONObject().apply {
                    put("p_installation_secret", installationSecret)
                    put("p_token", fcmToken.trim())
                    put("p_label", deviceLabel)
                }
                val rpcRes = executePost("/rest/v1/rpc/register_device_push_token", rpcBody.toString(), prefer = "return=minimal")
                if (rpcRes != null) {
                    Log.d("FCM_TOKEN", "Jeton enregistré via register_device_push_token")
                    return@withContext true
                }
            }
            // 2) Repli : ancien upsert REST (comportement actuel)
            val path = "/rest/v1/device_push_tokens?on_conflict=fcm_token"
            val res = executePost(path, body.toString(), prefer = "return=representation,resolution=merge-duplicates")
            res != null
        } catch (e: Exception) {
            Log.e("FCM_TOKEN", "upsertDevicePushToken error: ${e.message}")
            false
        }
    }

    suspend fun deleteDevicePushToken(fcmToken: String): Boolean = withContext(Dispatchers.IO) {
        if (fcmToken.isBlank()) return@withContext false
        val uid = getCurrentSessionUid()
        if (uid.isNullOrEmpty()) {
            Log.w("FCM_TOKEN", "deleteDevicePushToken ignoré : aucune session active")
            return@withContext false
        }
        val masked = if (fcmToken.length > 10) "${fcmToken.take(6)}...${fcmToken.takeLast(4)}" else "***"
        Log.d("FCM_TOKEN", "deleteDevicePushToken : suppression sécurisée pour UID=$uid ($masked)")
        try {
            executeDeleteWithRepresentation("/rest/v1/device_push_tokens?fcm_token=eq.${fcmToken.trim()}&user_id=eq.$uid")
        } catch (e: Exception) {
            Log.e("FCM_TOKEN", "deleteDevicePushToken error: ${e.message}")
            false
        }
    }

    suspend fun sendFcmPush(
        coupleId: String,
        recipientId: String,
        type: String,
        title: String,
        body: String,
        extraData: Map<String, String> = emptyMap(),
        hidePreview: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured() || recipientId.isBlank() || coupleId.isBlank()) return@withContext false
        try {
            val payload = JSONObject().apply {
                put("coupleId", coupleId)
                put("recipientId", recipientId)
                put("type", type)
                put("title", title)
                put("body", body)
                put("hidePreview", hidePreview)
                val dataObj = JSONObject()
                for ((k, v) in extraData) {
                    dataObj.put(k, v)
                }
                put("data", dataObj)
            }
            val res = executePost("/functions/v1/send-fcm", payload.toString())
            res != null
        } catch (e: Exception) {
            Log.w("FCM_SEND", "sendFcmPush failed: ${e.message}")
            false
        }
    }
}

