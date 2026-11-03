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
    private var pendingUnregisterToken: String? = null
    private var pendingUnregisterUid: String? = null
    private var lastSyncedToken: String? = null
    private var lastSyncedUid: String? = null

    override fun getPendingToken(): String? = pending
    override fun savePendingToken(token: String?) { pending = token }

    override fun getPendingUnregistrationInfo(): Pair<String, String>? {
        if (pendingUnregisterToken.isNullOrEmpty() || pendingUnregisterUid.isNullOrEmpty()) return null
        return Pair(pendingUnregisterToken!!, pendingUnregisterUid!!)
    }

    override fun savePendingUnregistrationInfo(token: String?, ownerUid: String?) {
        pendingUnregisterToken = token
        pendingUnregisterUid = ownerUid
    }

    override fun getLastSyncedTokenInfo(): Pair<String, String>? {
        if (lastSyncedToken.isNullOrEmpty() || lastSyncedUid.isNullOrEmpty()) return null
        return Pair(lastSyncedToken!!, lastSyncedUid!!)
    }

    override fun saveLastSyncedTokenInfo(token: String?, uid: String?) {
        lastSyncedToken = token
        lastSyncedUid = uid
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

    // --- TESTS DU CODE DE PRODUCTION FcmTokenSyncEngine ---

    @Test
    fun testSharedSingletonInstanceAcrossEntryPoints() {
        val storage = FakeTokenStorage()
        val fetcher = FakeFirebaseTokenFetcher("token_singleton")
        val api = FakeSupabasePushApi(currentUid = "user_singleton")

        val engine1 = FcmTokenSyncEngine.getInstance(storage, fetcher, api)
        val engine2 = FcmTokenSyncEngine.getInstance(storage, fetcher, api)

        // Les deux points d'entrée partagent EXACTEMENT la même instance Singleton
        assertTrue(engine1 === engine2)
    }

    @Test
    fun testTwoSimultaneousCallsFromDifferentEntryPoints() = runBlocking {
        val storage = FakeTokenStorage()
        val fetcher = FakeFirebaseTokenFetcher("token_simultaneous")
        val api = FakeSupabasePushApi(currentUid = "user_simultaneous")

        val engine1 = FcmTokenSyncEngine.getInstance(storage, fetcher, api)
        val engine2 = FcmTokenSyncEngine.getInstance(storage, fetcher, api)

        // Simulation de 2 appels simultanés (ex: Application + MainActivity)
        val job1 = async { engine1.syncPendingAndCurrentToken() }
        val job2 = async { engine2.syncPendingAndCurrentToken() }

        val res1 = job1.await()
        val res2 = job2.await()

        assertTrue(res1 || res2)
        assertEquals(1, api.upsertedTokens.size)
        assertEquals(Pair("user_simultaneous", "token_simultaneous"), api.upsertedTokens.first())
    }

    @Test
    fun testAccountUnregistrationOwnerUidIsolation() = runBlocking {
        val storage = FakeTokenStorage()
        // Désinscription en attente enregistrée pour l'Utilisateur A
        storage.savePendingUnregistrationInfo("token_device_A", "user_account_A")

        val fetcher = FakeFirebaseTokenFetcher("token_device_B")
        // L'Utilisateur B se connecte
        val api = FakeSupabasePushApi(currentUid = "user_account_B", deleteSuccess = true, upsertSuccess = true)

        val engine = FcmTokenSyncEngine.getInstance(storage, fetcher, api)
        val result = engine.syncPendingAndCurrentToken()

        assertTrue(result)
        // Vérifie que la désinscription du jeton de l'Utilisateur A n'a PAS été exécutée sous la session de l'Utilisateur B
        assertTrue(api.deletedTokens.none { it.first == "user_account_B" && it.second == "token_device_A" })
        // Vérifie que les infos de désinscription de l'Utilisateur A sont restées intactes pour sa prochaine reconnexion
        assertEquals(Pair("token_device_A", "user_account_A"), storage.getPendingUnregistrationInfo())
        // Vérifie que le jeton de l'Utilisateur B a été correctement inscrit
        assertEquals(Pair("user_account_B", "token_device_B"), api.upsertedTokens.last())
    }

    @Test
    fun testUnregistrationPassFailsDoesNotReRegisterSameToken() = runBlocking {
        val storage = FakeTokenStorage()
        // Désinscription en attente pour l'Utilisateur A avec le jeton "token_failed_unreg"
        storage.savePendingUnregistrationInfo("token_failed_unreg", "user_account_A")

        val fetcher = FakeFirebaseTokenFetcher("token_failed_unreg")
        val api = FakeSupabasePushApi(currentUid = "user_account_A", deleteSuccess = false, upsertSuccess = true)

        val engine = FcmTokenSyncEngine.getInstance(storage, fetcher, api)
        val result = engine.syncPendingAndCurrentToken()

        assertFalse(result)
        // La désinscription ayant échoué, le jeton ne doit pas être ré-inscrit dans la même passe !
        assertTrue(api.upsertedTokens.none { it.second == "token_failed_unreg" })
    }

    @Test
    fun testTokenRenewalDeletesOldTokenAndRegistersNew() = runBlocking {
        val storage = FakeTokenStorage()
        // Ancien jeton synchronisé pour Alice
        storage.saveLastSyncedTokenInfo("token_v1_old", "user_alice")

        // Firebase fournit un jeton renouvelé
        val fetcher = FakeFirebaseTokenFetcher("token_v2_new")
        val api = FakeSupabasePushApi(currentUid = "user_alice", deleteSuccess = true, upsertSuccess = true)

        val engine = FcmTokenSyncEngine.getInstance(storage, fetcher, api)
        val result = engine.syncPendingAndCurrentToken()

        assertTrue(result)
        // Ancien jeton supprimé pour Alice
        assertTrue(api.deletedTokens.contains(Pair("user_alice", "token_v1_old")))
        // Nouveau jeton inscrit pour Alice
        assertTrue(api.upsertedTokens.contains(Pair("user_alice", "token_v2_new")))
        // Info de dernier jeton synchronisé mise à jour
        assertEquals(Pair("token_v2_new", "user_alice"), storage.getLastSyncedTokenInfo())
    }

    @Test
    fun testNetworkFailureAndPersistenceAcrossRestarts() = runBlocking {
        val storage = FakeTokenStorage()
        val fetcher = FakeFirebaseTokenFetcher("token_offline_999")
        val api = FakeSupabasePushApi(currentUid = "user_bob", upsertSuccess = false)

        val engine1 = FcmTokenSyncEngine.getInstance(storage, fetcher, api)
        val result1 = engine1.syncPendingAndCurrentToken()

        assertFalse(result1)
        // Jeton conservé localement
        assertEquals("token_offline_999", storage.getPendingToken())

        // Simulation de redémarrage du processus : ré-initialisation du singleton avec la même mémoire locale
        FcmTokenSyncEngine.resetInstance()
        api.upsertSuccess = true

        val engine2 = FcmTokenSyncEngine.getInstance(storage, fetcher, api)
        val result2 = engine2.syncPendingAndCurrentToken()

        assertTrue(result2)
        assertNull(storage.getPendingToken())
        assertEquals(Pair("user_bob", "token_offline_999"), api.upsertedTokens.last())
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
