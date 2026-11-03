package com.example.mikayala

import com.example.mikayala.services.CallActionReceiver
import com.example.mikayala.services.FcmTokenSyncEngine
import com.example.mikayala.services.FirebaseTokenFetcher
import com.example.mikayala.services.MyFirebaseMessagingService
import com.example.mikayala.services.SupabasePushApi
import com.example.mikayala.services.TokenStorage
import com.example.mikayala.ui.components.VoicePlaybackCoordinator
import com.example.mikayala.utils.VoiceNoteState
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class FakeTokenStorage : TokenStorage {
    private var pending: String? = null
    private var pendingUnregister: String? = null

    override fun getPendingToken(): String? = pending
    override fun savePendingToken(token: String?) { pending = token }
    override fun getPendingUnregistrationToken(): String? = pendingUnregister
    override fun savePendingUnregistrationToken(token: String?) { pendingUnregister = token }
}

class FakeFirebaseTokenFetcher(var tokenToReturn: String? = null) : FirebaseTokenFetcher {
    override suspend fun fetchToken(): String? = tokenToReturn
}

class FakeSupabasePushApi(
    var currentUid: String? = null,
    var upsertSuccess: Boolean = true,
    var deleteSuccess: Boolean = true
) : SupabasePushApi {
    val upsertedTokens = mutableListOf<Pair<String, String>>()
    val deletedTokens = mutableListOf<Pair<String, String>>()

    override fun getCurrentSessionUid(): String? = currentUid

    override suspend fun upsertDevicePushToken(fcmToken: String): Boolean {
        if (!upsertSuccess) return false
        val uid = currentUid ?: return false
        upsertedTokens.add(Pair(uid, fcmToken))
        return true
    }

    override suspend fun deleteDevicePushToken(fcmToken: String): Boolean {
        if (!deleteSuccess) return false
        val uid = currentUid ?: return false
        deletedTokens.add(Pair(uid, fcmToken))
        return true
    }
}

class VoiceAndPushUnitTest {

    @Test
    fun testVoicePlaybackCoordinatorSinglePlayback() {
        // État initial propre
        VoicePlaybackCoordinator.stopAll()
        assertNull(VoicePlaybackCoordinator.currentlyPlayingUrl.value)

        // Début de lecture note 1
        VoicePlaybackCoordinator.requestPlay("https://supabase.co/audio1.m4a")
        assertEquals("https://supabase.co/audio1.m4a", VoicePlaybackCoordinator.currentlyPlayingUrl.value)

        // Début de lecture note 2 -> le coordinateur bascule immédiatement sur note 2
        VoicePlaybackCoordinator.requestPlay("https://supabase.co/audio2.m4a")
        assertEquals("https://supabase.co/audio2.m4a", VoicePlaybackCoordinator.currentlyPlayingUrl.value)

        // Notification d'arrêt obsolète de note 1 -> ne doit pas stopper note 2
        VoicePlaybackCoordinator.notifyStopped("https://supabase.co/audio1.m4a")
        assertEquals("https://supabase.co/audio2.m4a", VoicePlaybackCoordinator.currentlyPlayingUrl.value)

        // Notification d'arrêt effective de note 2 -> libère la lecture
        VoicePlaybackCoordinator.notifyStopped("https://supabase.co/audio2.m4a")
        assertNull(VoicePlaybackCoordinator.currentlyPlayingUrl.value)
    }

    @Test
    fun testTokenMaskingFunction() {
        val longToken = "fKm98xLa7200q1Z58921abcdef0123456789"
        assertEquals("fKm98x...6789", MyFirebaseMessagingService.maskToken(longToken))

        val shortToken = "abc"
        assertEquals("***", MyFirebaseMessagingService.maskToken(shortToken))

        val emptyToken = ""
        assertEquals("***", MyFirebaseMessagingService.maskToken(emptyToken))
    }

    @Test
    fun testSelfNotificationFilterRealFunction() {
        val userA = "11111111-2222-3333-4444-555555555555"
        val userB = "66666666-7777-8888-9999-000000000000"

        assertTrue(MyFirebaseMessagingService.shouldIgnoreNotification(userA, userA))
        assertFalse(MyFirebaseMessagingService.shouldIgnoreNotification(userA, userB))
        assertFalse(MyFirebaseMessagingService.shouldIgnoreNotification(null, userB))
        assertFalse(MyFirebaseMessagingService.shouldIgnoreNotification(userA, null))
        assertFalse(MyFirebaseMessagingService.shouldIgnoreNotification("", userB))
    }

    @Test
    fun testResolveCallerNameWithCamouflageModes() {
        val rawCaller = "Élise"

        assertEquals("Service Météo", MyFirebaseMessagingService.resolveCallerName("weather_camouflage", rawCaller))
        assertEquals("Système", MyFirebaseMessagingService.resolveCallerName("system_camouflage", rawCaller))
        assertEquals("Mikayala", MyFirebaseMessagingService.resolveCallerName("discreet", rawCaller))
        assertEquals("Élise", MyFirebaseMessagingService.resolveCallerName("romantic", rawCaller))
        assertEquals("Mon Amour", MyFirebaseMessagingService.resolveCallerName("romantic", ""))
    }

    @Test
    fun testResolveMessageDisplayCamouflageAndTypes() {
        val rawSender = "Alex"

        val weather = MyFirebaseMessagingService.resolveMessageDisplay("chat_message", "weather_camouflage", rawSender, "Bonjour")
        assertEquals("Météo ☀️", weather.first)
        assertEquals("Ciel dégagé • 21°C", weather.second)

        val system = MyFirebaseMessagingService.resolveMessageDisplay("chat_message", "system_camouflage", rawSender, "Bonjour")
        assertEquals("Système ⚙️", system.first)
        assertEquals("Mise à jour système prête", system.second)

        val discreet = MyFirebaseMessagingService.resolveMessageDisplay("chat_message", "discreet", rawSender, "Texte secret")
        assertEquals("Mikayala 🔒", discreet.first)
        assertEquals("Nouveau message chiffré", discreet.second)

        val audioMsg = MyFirebaseMessagingService.resolveMessageDisplay("audio", "romantic", rawSender, "")
        assertEquals("Alex", audioMsg.first)
        assertEquals("🎤 Note vocale reçue", audioMsg.second)

        val imageMsg = MyFirebaseMessagingService.resolveMessageDisplay("image", "romantic", rawSender, "")
        assertEquals("Alex", imageMsg.first)
        assertEquals("📷 Photo reçue", imageMsg.second)

        val videoMsg = MyFirebaseMessagingService.resolveMessageDisplay("video", "romantic", rawSender, "")
        assertEquals("Alex", videoMsg.first)
        assertEquals("📹 Vidéo reçue", videoMsg.second)

        val replyMsg = MyFirebaseMessagingService.resolveMessageDisplay("chat_message", "romantic", rawSender, "D'accord !", isReply = true)
        assertEquals("Alex", replyMsg.first)
        assertEquals("↳ D'accord !", replyMsg.second)
    }

    @Test
    fun testCallActionReceiverConstants() {
        assertEquals("com.example.mikayala.ACTION_ANSWER_CALL", CallActionReceiver.ACTION_ANSWER_CALL)
        assertEquals("com.example.mikayala.ACTION_DECLINE_CALL", CallActionReceiver.ACTION_DECLINE_CALL)
        assertEquals("CALLER_NAME", CallActionReceiver.EXTRA_CALLER_NAME)
        assertEquals("IS_VIDEO", CallActionReceiver.EXTRA_IS_VIDEO)
    }

    // --- TESTS DU CODE DE PRODUCTION FcmTokenSyncEngine ---

    @Test
    fun testSyncNetworkFailureRetainsPendingToken() = runBlocking {
        val storage = FakeTokenStorage()
        val fetcher = FakeFirebaseTokenFetcher("token_network_fail_123")
        val api = FakeSupabasePushApi(currentUid = "user_alice", upsertSuccess = false)
        val engine = FcmTokenSyncEngine(storage, fetcher, api)

        val result = engine.syncPendingAndCurrentToken()

        assertFalse(result)
        assertEquals("token_network_fail_123", storage.getPendingToken())
    }

    @Test
    fun testSyncNoSessionRetainsPendingToken() = runBlocking {
        val storage = FakeTokenStorage()
        val fetcher = FakeFirebaseTokenFetcher("token_no_session_456")
        val api = FakeSupabasePushApi(currentUid = null)
        val engine = FcmTokenSyncEngine(storage, fetcher, api)

        val result = engine.syncPendingAndCurrentToken()

        assertFalse(result)
        assertEquals("token_no_session_456", storage.getPendingToken())
    }

    @Test
    fun testConcurrentSyncRequestsAreQueuedAndProcessed() = runBlocking {
        val storage = FakeTokenStorage()
        val fetcher = FakeFirebaseTokenFetcher("token_concurrent_789")
        val api = FakeSupabasePushApi(currentUid = "user_bob", upsertSuccess = true)
        val engine = FcmTokenSyncEngine(storage, fetcher, api)

        val job1 = async { engine.syncPendingAndCurrentToken() }
        val job2 = async { engine.syncPendingAndCurrentToken() }

        val res1 = job1.await()
        val res2 = job2.await()

        assertTrue(res1 || res2)
        assertNull(storage.getPendingToken())
        assertTrue(api.upsertedTokens.any { it.first == "user_bob" && it.second == "token_concurrent_789" })
    }

    @Test
    fun testRenewedTokenReplacesOldPendingToken() = runBlocking {
        val storage = FakeTokenStorage()
        storage.savePendingToken("old_token_111")
        val fetcher = FakeFirebaseTokenFetcher("fresh_token_999")
        val api = FakeSupabasePushApi(currentUid = "user_charlie", upsertSuccess = true)
        val engine = FcmTokenSyncEngine(storage, fetcher, api)

        val result = engine.syncPendingAndCurrentToken()

        assertTrue(result)
        assertNull(storage.getPendingToken())
        assertEquals("fresh_token_999", api.upsertedTokens.last().second)
    }

    @Test
    fun testFailedLogoutUnregistrationRetainsInfo() = runBlocking {
        val storage = FakeTokenStorage()
        storage.savePendingToken("active_token_555")
        val fetcher = FakeFirebaseTokenFetcher("active_token_555")
        val api = FakeSupabasePushApi(currentUid = "user_david", deleteSuccess = false)
        val engine = FcmTokenSyncEngine(storage, fetcher, api)

        val result = engine.unregisterDeviceTokenOnLogout()

        assertFalse(result)
        assertEquals("active_token_555", storage.getPendingUnregistrationToken())
    }

    @Test
    fun testRetryLogoutUnregistrationSucceedsOnNextSync() = runBlocking {
        val storage = FakeTokenStorage()
        storage.savePendingUnregistrationToken("failed_token_777")
        val fetcher = FakeFirebaseTokenFetcher("fresh_token_888")
        val api = FakeSupabasePushApi(currentUid = "user_david", deleteSuccess = true, upsertSuccess = true)
        val engine = FcmTokenSyncEngine(storage, fetcher, api)

        val result = engine.syncPendingAndCurrentToken()

        assertTrue(result)
        assertNull(storage.getPendingUnregistrationToken())
        assertTrue(api.deletedTokens.any { it.second == "failed_token_777" })
    }

    @Test
    fun testAccountSwitchingIsolation() = runBlocking {
        val storageA = FakeTokenStorage()
        val fetcherA = FakeFirebaseTokenFetcher("shared_device_token")
        val apiA = FakeSupabasePushApi(currentUid = "user_account_A", deleteSuccess = true)
        val engineA = FcmTokenSyncEngine(storageA, fetcherA, apiA)

        val logoutResult = engineA.unregisterDeviceTokenOnLogout()
        assertTrue(logoutResult)
        assertEquals(Pair("user_account_A", "shared_device_token"), apiA.deletedTokens.last())

        val storageB = FakeTokenStorage()
        val fetcherB = FakeFirebaseTokenFetcher("shared_device_token")
        val apiB = FakeSupabasePushApi(currentUid = "user_account_B", upsertSuccess = true)
        val engineB = FcmTokenSyncEngine(storageB, fetcherB, apiB)

        val syncResult = engineB.syncPendingAndCurrentToken()
        assertTrue(syncResult)
        assertEquals(Pair("user_account_B", "shared_device_token"), apiB.upsertedTokens.last())
    }

    @Test
    fun testVoiceNoteStateTransitions() {
        val states = VoiceNoteState.values().map { it.name }
        assertTrue(states.contains("IDLE"))
        assertTrue(states.contains("RECORDING"))
        assertTrue(states.contains("LOCKED"))
        assertTrue(states.contains("PAUSED"))
    }

    @Test
    fun testIso8601UtcTimestampFormat() {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val formatted = sdf.format(Date(1730640000000L))
        assertTrue(formatted.endsWith("Z"))
        assertEquals(20, formatted.length)
        assertFalse(formatted.contains("now()"))
    }
}
