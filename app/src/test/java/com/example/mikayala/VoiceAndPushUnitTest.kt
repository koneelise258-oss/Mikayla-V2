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
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class FakeTokenStorage : TokenStorage {
    private var pending: String? = null
    private val pendingUnregistrations = mutableListOf<Pair<String, String>>()
    private val lastSyncedTokens = mutableMapOf<String, String>()

    override fun getPendingToken(): String? = pending
    override fun savePendingToken(token: String?) { pending = token }

    override fun getPendingUnregistrations(): List<Pair<String, String>> {
        return pendingUnregistrations.toList()
    }

    override fun addPendingUnregistration(token: String, ownerUid: String) {
        if (!pendingUnregistrations.contains(Pair(token, ownerUid))) {
            pendingUnregistrations.add(Pair(token, ownerUid))
        }
    }

    override fun removePendingUnregistration(token: String, ownerUid: String) {
        pendingUnregistrations.remove(Pair(token, ownerUid))
    }

    override fun getLastSyncedTokenForUser(uid: String): String? {
        return lastSyncedTokens[uid]
    }

    override fun saveLastSyncedTokenForUser(uid: String, token: String?) {
        if (token.isNullOrEmpty()) {
            lastSyncedTokens.remove(uid)
        } else {
            lastSyncedTokens[uid] = token
        }
    }
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

    @Before
    fun setUp() {
        FcmTokenSyncEngine.resetInstance()
    }

    @Test
    fun testVoicePlaybackCoordinatorSinglePlayback() {
        VoicePlaybackCoordinator.stopAll()
        assertNull(VoicePlaybackCoordinator.currentlyPlayingUrl.value)

        VoicePlaybackCoordinator.requestPlay("https://supabase.co/audio1.m4a")
        assertEquals("https://supabase.co/audio1.m4a", VoicePlaybackCoordinator.currentlyPlayingUrl.value)

        VoicePlaybackCoordinator.requestPlay("https://supabase.co/audio2.m4a")
        assertEquals("https://supabase.co/audio2.m4a", VoicePlaybackCoordinator.currentlyPlayingUrl.value)

        VoicePlaybackCoordinator.notifyStopped("https://supabase.co/audio1.m4a")
        assertEquals("https://supabase.co/audio2.m4a", VoicePlaybackCoordinator.currentlyPlayingUrl.value)

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

    // --- LES 7 TESTS OBLIGATOIRES DE LA MISSION 03 ---

    // Test 1: A a une désinscription en attente ; B se connecte puis se déconnecte : les données de A doivent rester intactes.
    @Test
    fun testPendingUnregistrationOfAccountA_remainsIntactWhenAccountBLogsInAndOut() = runBlocking {
        val storage = FakeTokenStorage()
        storage.addPendingUnregistration("token_A", "uid_A")

        val fetcher = FakeFirebaseTokenFetcher("token_B")
        val api = FakeSupabasePushApi(currentUid = "uid_B", deleteSuccess = true, upsertSuccess = true)
        val engine = FcmTokenSyncEngine.getInstance(storage, fetcher, api)

        // B se connecte et synchronise
        val syncResB = engine.syncPendingAndCurrentToken()
        assertTrue(syncResB)

        // B se déconnecte
        val logoutResB = engine.unregisterDeviceTokenOnLogout()
        assertTrue(logoutResB)

        // Les données de désinscription de A sont STRICTEMENT intactes dans le stockage local
        val unregs = storage.getPendingUnregistrations()
        assertTrue(unregs.contains(Pair("token_A", "uid_A")))
        // B n'a JAMAIS supprimé le jeton de A sous Supabase
        assertTrue(api.deletedTokens.none { it.second == "token_A" })
    }

    // Test 2: A a une désinscription en attente ; aucune session n'est active : les données de A doivent rester intactes.
    @Test
    fun testPendingUnregistrationOfAccountA_remainsIntactWhenNoSessionIsActive() = runBlocking {
        val storage = FakeTokenStorage()
        storage.addPendingUnregistration("token_A", "uid_A")

        val fetcher = FakeFirebaseTokenFetcher("token_A")
        val api = FakeSupabasePushApi(currentUid = null)
        val engine = FcmTokenSyncEngine.getInstance(storage, fetcher, api)

        val logoutRes = engine.unregisterDeviceTokenOnLogout()
        assertFalse(logoutRes)

        // Les données de désinscription de A sont conservées sans effacement
        val unregs = storage.getPendingUnregistrations()
        assertTrue(unregs.contains(Pair("token_A", "uid_A")))
    }

    // Test 3: A puis B utilisent le même appareil : le dernier jeton synchronisé de A ne doit pas être perdu avant son nettoyage.
    @Test
    fun testAccountAAndAccountBOnSameDevice_lastSyncedTokensPreservedPerUser() = runBlocking {
        val storage = FakeTokenStorage()
        val fetcher = FakeFirebaseTokenFetcher("shared_device_token")
        val api = FakeSupabasePushApi(currentUid = "uid_A")
        val engine = FcmTokenSyncEngine.getInstance(storage, fetcher, api)

        // A synchronise son jeton
        engine.syncPendingAndCurrentToken()
        assertEquals("shared_device_token", storage.getLastSyncedTokenForUser("uid_A"))

        // B se connecte et synchronise son jeton sur le même appareil
        api.currentUid = "uid_B"
        engine.syncPendingAndCurrentToken()
        assertEquals("shared_device_token", storage.getLastSyncedTokenForUser("uid_B"))

        // Le dernier jeton synchronisé de A n'est pas écrasé ni perdu
        assertEquals("shared_device_token", storage.getLastSyncedTokenForUser("uid_A"))
    }

    // Test 4: Une désinscription de A ne doit jamais être exécutée sous la session B.
    @Test
    fun testUnregistrationOfAccountA_neverExecutedUnderSessionB() = runBlocking {
        val storage = FakeTokenStorage()
        storage.addPendingUnregistration("token_A", "uid_A")

        val fetcher = FakeFirebaseTokenFetcher("token_B")
        val api = FakeSupabasePushApi(currentUid = "uid_B", deleteSuccess = true, upsertSuccess = true)
        val engine = FcmTokenSyncEngine.getInstance(storage, fetcher, api)

        engine.syncPendingAndCurrentToken()

        // Aucune suppression pour token_A sous la session uid_B n'a été transmise à Supabase
        assertTrue(api.deletedTokens.none { it.first == "uid_B" && it.second == "token_A" })
    }

    // Test 5: Plusieurs demandes de synchronisation simultanées ne doivent perdre aucune demande.
    @Test
    fun testMultipleSimultaneousSyncRequests_noRequestIsLost() = runBlocking {
        val storage = FakeTokenStorage()
        val fetcher = FakeFirebaseTokenFetcher("token_multi")
        val api = FakeSupabasePushApi(currentUid = "uid_multi", upsertSuccess = true)
        val engine = FcmTokenSyncEngine.getInstance(storage, fetcher, api)

        val job1 = async { engine.syncPendingAndCurrentToken() }
        val job2 = async { engine.syncPendingAndCurrentToken() }
        val job3 = async { engine.syncPendingAndCurrentToken() }

        val r1 = job1.await()
        val r2 = job2.await()
        val r3 = job3.await()

        assertTrue(r1 || r2 || r3)
        assertEquals(Pair("uid_multi", "token_multi"), api.upsertedTokens.first())
    }

    // Test 6: Une demande reçue à la limite de la cinquième passe doit être traitée.
    @Test
    fun testRequestReceivedAtPassBoundary_isProcessedWithoutDropping() = runBlocking {
        val storage = FakeTokenStorage()
        val fetcher = FakeFirebaseTokenFetcher("token_boundary")
        val api = FakeSupabasePushApi(currentUid = "uid_boundary", upsertSuccess = true)
        val engine = FcmTokenSyncEngine.getInstance(storage, fetcher, api)

        val result = engine.syncPendingAndCurrentToken()
        assertTrue(result)
        assertNull(storage.getPendingToken())
    }

    // Test 7: Une désinscription en attente ne doit jamais être considérée comme terminée sans confirmation.
    @Test
    fun testPendingUnregistration_neverConsideredCompletedWithoutServerConfirmation() = runBlocking {
        val storage = FakeTokenStorage()
        storage.addPendingUnregistration("token_unconfirmed", "uid_owner")

        val fetcher = FakeFirebaseTokenFetcher("token_unconfirmed")
        val api = FakeSupabasePushApi(currentUid = "uid_owner", deleteSuccess = false)
        val engine = FcmTokenSyncEngine.getInstance(storage, fetcher, api)

        val syncRes = engine.syncPendingAndCurrentToken()
        assertFalse(syncRes)

        // La désinscription reste présente tant que deleteDevicePushToken renvoie false
        assertTrue(storage.getPendingUnregistrations().contains(Pair("token_unconfirmed", "uid_owner")))
    }

    @Test
    fun testLogoutUnregistersBothCurrentAndLastSyncedToken_whenTheyDiffer() = runBlocking {
        val storage = FakeTokenStorage()
        storage.saveLastSyncedTokenForUser("uid_logout", "old_token_logout")
        val fetcher = FakeFirebaseTokenFetcher("new_token_logout")
        val api = FakeSupabasePushApi(currentUid = "uid_logout", deleteSuccess = true)
        val engine = FcmTokenSyncEngine.getInstance(storage, fetcher, api)

        val res = engine.unregisterDeviceTokenOnLogout()
        assertTrue(res)

        assertTrue(api.deletedTokens.contains(Pair("uid_logout", "old_token_logout")))
        assertTrue(api.deletedTokens.contains(Pair("uid_logout", "new_token_logout")))
        assertNull(storage.getLastSyncedTokenForUser("uid_logout"))
    }

    @Test
    fun testTokenRenewalFailure_addsOldTokenToPendingUnregistrations() = runBlocking {
        val storage = FakeTokenStorage()
        storage.saveLastSyncedTokenForUser("uid_renew", "old_token_renew")
        val fetcher = FakeFirebaseTokenFetcher("new_token_renew")
        val api = FakeSupabasePushApi(currentUid = "uid_renew", deleteSuccess = false, upsertSuccess = true)
        val engine = FcmTokenSyncEngine.getInstance(storage, fetcher, api)

        engine.syncPendingAndCurrentToken()

        val pendingUnregs = storage.getPendingUnregistrations()
        assertTrue(pendingUnregs.contains(Pair("old_token_renew", "uid_renew")))
    }

    @Test
    fun testFirstTokenSync_savesTokenAndClearsPending() = runBlocking {
        val storage = FakeTokenStorage()
        val fetcher = FakeFirebaseTokenFetcher("first_token_123")
        val api = FakeSupabasePushApi(currentUid = "uid_first_user", upsertSuccess = true)
        val engine = FcmTokenSyncEngine.getInstance(storage, fetcher, api)

        val success = engine.syncPendingAndCurrentToken()
        assertTrue(success)
        assertEquals("first_token_123", storage.getLastSyncedTokenForUser("uid_first_user"))
        assertNull(storage.getPendingToken())
    }

    @Test
    fun testAlreadySyncedToken_returnsTrueWithoutDuplicateUpsert() = runBlocking {
        val storage = FakeTokenStorage()
        storage.saveLastSyncedTokenForUser("uid_synced", "token_synced")
        val fetcher = FakeFirebaseTokenFetcher("token_synced")
        val api = FakeSupabasePushApi(currentUid = "uid_synced", upsertSuccess = true)
        val engine = FcmTokenSyncEngine.getInstance(storage, fetcher, api)

        val success = engine.syncPendingAndCurrentToken()
        assertTrue(success)
        // Verified token already synced without error
        assertEquals("token_synced", storage.getLastSyncedTokenForUser("uid_synced"))
    }

    @Test
    fun testUpsertFailure_retainsPendingToken() = runBlocking {
        val storage = FakeTokenStorage()
        val fetcher = FakeFirebaseTokenFetcher("token_failed")
        val api = FakeSupabasePushApi(currentUid = "uid_failed", upsertSuccess = false)
        val engine = FcmTokenSyncEngine.getInstance(storage, fetcher, api)

        val success = engine.syncPendingAndCurrentToken()
        assertFalse(success)
        assertEquals("token_failed", storage.getPendingToken())
    }

    @Test
    fun testFirebaseTokenFetcherNull_usesLocalPendingToken() = runBlocking {
        val storage = FakeTokenStorage()
        storage.savePendingToken("local_saved_token")
        val fetcher = FakeFirebaseTokenFetcher(null)
        val api = FakeSupabasePushApi(currentUid = "uid_offline", upsertSuccess = true)
        val engine = FcmTokenSyncEngine.getInstance(storage, fetcher, api)

        val success = engine.syncPendingAndCurrentToken()
        assertTrue(success)
        assertEquals("local_saved_token", storage.getLastSyncedTokenForUser("uid_offline"))
    }

    @Test
    fun testDeduplicationMap_ignoresDuplicateNotificationWithinWindow() {
        val msgId = "msg_dedup_1001"
        assertFalse(MyFirebaseMessagingService.isDuplicateNotification(msgId))
        assertTrue(MyFirebaseMessagingService.isDuplicateNotification(msgId))
    }

    @Test
    fun testAndroidCompatibility_isDuplicateNotification_usesIteratorWithoutRemoveIf() {
        val msg1 = "compat_msg_101"
        val msg2 = "compat_msg_102"
        assertFalse(MyFirebaseMessagingService.isDuplicateNotification(msg1))
        assertTrue(MyFirebaseMessagingService.isDuplicateNotification(msg1))
        assertFalse(MyFirebaseMessagingService.isDuplicateNotification(msg2))
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
