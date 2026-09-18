package com.example.mikayala.data

import android.util.Log
import com.example.mikayala.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.github.jan.supabase.postgrest.rpc
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
            }
        } catch (e: Exception) {
            Log.e("SupabaseService", "Failed to create Supabase client: ${e.message}")
            // Fallback or rethrow if critical, but lazy helps avoid crash on main thread during startup
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
                Log.d("SupabaseDiag", "[CREATE] Session existante pour UID: ${session.user?.id}")
                return true
            }
            
            Log.d("SupabaseDiag", "[CREATE] Aucune session trouvée, connexion anonyme en cours...")
            supabase.auth.signInAnonymously()
            val newSession = supabase.auth.currentSessionOrNull()
            Log.d("SupabaseDiag", "[CREATE] Connexion anonyme réussie. Nouvel UID: ${newSession?.user?.id}")
            return true
        } catch (e: Exception) {
            Log.e("SupabaseDiag", "[CREATE] ensureAnonymousSession a échoué: ${e.message}")
            return false
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

    suspend fun createCoupleSpace(pairingCode: String, partnerName: String, userId: String, token: String): JSONObject? = withContext(Dispatchers.IO) {
        Log.d("SupabaseDiag", "[CREATE] createCoupleSpace starting...")
        Log.d("SupabaseDiag", "[CREATE] Code généré (reçu par le service): $pairingCode")
        
        val initialSession = supabase.auth.currentSessionOrNull()
        Log.d("SupabaseDiag", "[CREATE] Session présente avant ensure: ${initialSession != null}")
        Log.d("SupabaseDiag", "[CREATE] UID actuel avant ensure: ${initialSession?.user?.id}")

        if (!ensureAnonymousSession()) {
            Log.e("SupabaseDiag", "[CREATE] ensureAnonymousSession FAILED in createCoupleSpace")
            return@withContext null
        }
        
        val actualSession = supabase.auth.currentSessionOrNull()
        val actualUid = actualSession?.user?.id ?: ""
        Log.d("SupabaseDiag", "[CREATE] UID Supabase Anonymous Auth utilisé: $actualUid")

        try {
            val parameters = buildJsonObject {
                put("requested_code", pairingCode)
                put("partner_name", partnerName)
                put("user_id", userId)
            }
            Log.d("SupabaseDiag", "[CREATE] Code envoyé au RPC: $pairingCode")
            Log.d("SupabaseDiag", "[CREATE] Payload RPC 'create_couple': $parameters")
            
            val result = supabase.postgrest.rpc("create_couple", parameters)
            Log.d("SupabaseDiag", "[CREATE] Réponse brute du RPC: ${result.data}")
            
            JSONObject(result.data.toString())
        } catch (e: Exception) {
            Log.e("SupabaseDiag", "[CREATE] Erreur éventuelle RPC: ${e.message}")
            Log.e("SupabaseDiag", "[CREATE] StackTrace complet: ${e.stackTraceToString()}")
            null
        }
    }

    suspend fun joinCoupleSpace(pairingCode: String, partnerName: String, userId: String, token: String): JSONObject? = withContext(Dispatchers.IO) {
        Log.d("SupabaseDiag", "joinCoupleSpace starting...")
        Log.d("SupabaseDiag", "Args -> code: $pairingCode, name: $partnerName, userId: $userId")
        
        if (!ensureAnonymousSession()) {
            Log.e("SupabaseDiag", "ensureAnonymousSession FAILED in joinCoupleSpace")
            return@withContext null
        }
        
        val actualSession = supabase.auth.currentSessionOrNull()
        val actualUid = actualSession?.user?.id ?: ""
        Log.d("SupabaseDiag", "Actual Supabase Session UID: $actualUid")
        
        try {
            val parameters = buildJsonObject {
                put("requested_code", pairingCode)
                put("partner_name", partnerName)
                put("user_id", userId)
            }
            Log.d("SupabaseDiag", "RPC 'join_couple' payload: $parameters")
            
            val result = supabase.postgrest.rpc("join_couple", parameters)
            Log.d("SupabaseDiag", "RPC 'join_couple' response data: ${result.data}")
            
            JSONObject(result.data.toString())
        } catch (e: Exception) {
            Log.e("SupabaseDiag", "RPC 'join_couple' EXCEPTION: ${e.message}")
            // Tenter de parser l'erreur si elle contient du JSON (souvent le cas avec Supabase)
            Log.e("SupabaseDiag", "Exception details: ${e.stackTraceToString()}")
            null
        }
    }

    suspend fun getCoupleSpace(pairingCode: String): JSONObject? = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext null
        try {
            var existing = executeGet("/rest/v1/couples?pairing_code=eq.$pairingCode")
            if (existing == null || existing == "[]") {
                existing = executeGet("/rest/v1/couple_spaces?pairing_code=eq.$pairingCode")
            }
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
        if (!isConfigured() || userId.isEmpty()) return@withContext null
        try {
            var res = executeGet("/rest/v1/couples?partner_1_id=eq.$userId")
            if (res == null || res == "[]") {
                res = executeGet("/rest/v1/couples?partner_2_id=eq.$userId")
            }
            if (res == null || res == "[]") {
                res = executeGet("/rest/v1/couple_spaces?partner_1_id=eq.$userId")
            }
            if (res == null || res == "[]") {
                res = executeGet("/rest/v1/couple_spaces?partner_2_id=eq.$userId")
            }
            if (res != null && res != "[]") {
                val array = JSONArray(res)
                if (array.length() > 0) return@withContext array.getJSONObject(0)
            }
        } catch (e: Exception) {
            Log.e("SupabaseService", "getCoupleSpaceForUser error: ${e.message}")
        }
        return@withContext null
    }

    suspend fun fetchMessages(pairingCode: String): JSONArray? = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext null
        try {
            var res = executeGet("/rest/v1/messages?couple_id=eq.$pairingCode&order=created_at.asc")
            if (res == null || res == "[]") {
                res = executeGet("/rest/v1/messages?pairing_code=eq.$pairingCode&order=created_at.asc")
            }
            if (res != null) return@withContext JSONArray(res)
        } catch (e: Exception) {
            Log.e("SupabaseService", "fetchMessages error: ${e.message}")
        }
        return@withContext null
    }

    suspend fun postMessage(pairingCode: String, senderId: String, content: String, type: String = "text"): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext true
        try {
            val msgId = "m_" + java.util.UUID.randomUUID().toString()
            val body = JSONObject().apply {
                put("id", msgId)
                put("couple_id", pairingCode)
                put("pairing_code", pairingCode)
                put("sender_id", senderId)
                put("receiver_id", "partner")
                put("content", content)
                put("type", type)
                put("status", "sent")
                put("created_at", System.currentTimeMillis())
            }
            val res = executePost("/rest/v1/messages", body.toString())
            return@withContext res != null
        } catch (e: Exception) {
            Log.e("SupabaseService", "postMessage error: ${e.message}")
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

    // --- SHARED VAULT SUPABASE METHODS ---
    suspend fun fetchVaultItems(pairingCode: String): JSONArray? = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext null
        try {
            var res = executeGet("/rest/v1/vault_items?couple_id=eq.$pairingCode&order=created_at.desc")
            if (res == null || res == "[]") {
                res = executeGet("/rest/v1/vault_items?pairing_code=eq.$pairingCode&order=created_at.desc")
            }
            if (res != null) return@withContext JSONArray(res)
        } catch (e: Exception) {
            Log.e("SupabaseService", "fetchVaultItems error: ${e.message}")
        }
        return@withContext null
    }

    suspend fun insertVaultItem(pairingCode: String, title: String, type: String, category: String, caption: String, mediaUrl: String = ""): JSONObject? = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext null
        try {
            val vId = "v_" + java.util.UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val body = JSONObject().apply {
                put("id", vId)
                put("couple_id", pairingCode)
                put("pairing_code", pairingCode)
                put("title", title)
                put("type", type)
                put("media_type", type)
                put("category", category)
                put("caption", caption)
                put("media_url", mediaUrl)
                put("added_by", "me")
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
    suspend fun initiateCall(pairingCode: String, callerName: String, callType: String): JSONObject? = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext null
        try {
            val callId = "call_" + java.util.UUID.randomUUID().toString()
            val body = JSONObject().apply {
                put("id", callId)
                put("couple_id", pairingCode)
                put("pairing_code", pairingCode)
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

    suspend fun fetchActiveCall(pairingCode: String): JSONObject? = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext null
        try {
            val res = executeGet("/rest/v1/call_signals?pairing_code=eq.$pairingCode&status=in.(ringing,active)&order=created_at.desc&limit=1")
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

    private fun executeGet(path: String): String? {
        return try {
            val url = URL("$supabaseUrl$path")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("apikey", supabaseKey)
            conn.setRequestProperty("Authorization", "Bearer $supabaseKey")
            conn.setRequestProperty("Accept", "application/json")
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

    private fun executeGet(path: String, accessToken: String? = null): String? {
        return try {
            val url = URL("$supabaseUrl$path")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("apikey", supabaseKey)
            if (accessToken != null) {
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
            } else {
                conn.setRequestProperty("Authorization", "Bearer $supabaseKey")
            }
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
            if (accessToken != null) {
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
            } else {
                conn.setRequestProperty("Authorization", "Bearer $supabaseKey")
            }
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
                Log.e("SupabaseService", "executePost failed (code ${conn.responseCode}): $errorMessage")
                null
            }
        } catch (e: Exception) {
            Log.e("SupabaseService", "executePost failed: ${e.message}")
            null
        }
    }

    private fun executePatch(path: String, jsonBody: String): String? {
        return try {
            val url = URL("$supabaseUrl$path")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST" // HTTP connection workaround for PATCH or use X-HTTP-Method-Override
            conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            conn.setRequestProperty("apikey", supabaseKey)
            conn.setRequestProperty("Authorization", "Bearer $supabaseKey")
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
            conn.setRequestProperty("Authorization", "Bearer $supabaseKey")
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

    suspend fun markMessagesDelivered(coupleId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured() || coupleId.isEmpty() || coupleId == "couple_main") return@withContext false
        try {
            val body = JSONObject().apply {
                put("target_couple_id", coupleId)
            }
            val res = executePost("/rest/v1/rpc/mark_messages_delivered", body.toString())
            res != null
        } catch (e: Exception) {
            Log.e("SupabaseService", "markMessagesDelivered error: ${e.message}")
            false
        }
    }

    suspend fun markMessagesRead(coupleId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured() || coupleId.isEmpty() || coupleId == "couple_main") return@withContext false
        try {
            val body = JSONObject().apply {
                put("target_couple_id", coupleId)
            }
            val res = executePost("/rest/v1/rpc/mark_messages_read", body.toString())
            res != null
        } catch (e: Exception) {
            Log.e("SupabaseService", "markMessagesRead error: ${e.message}")
            false
        }
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

    suspend fun updateProfile(userId: String, displayName: String, bio: String, avatarPath: String, avatarVersion: Int): Boolean = withContext(Dispatchers.IO) {
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
