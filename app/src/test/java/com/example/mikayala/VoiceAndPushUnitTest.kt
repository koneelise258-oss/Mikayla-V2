package com.example.mikayala

import com.example.mikayala.services.CallActionReceiver
import com.example.mikayala.services.MyFirebaseMessagingService
import com.example.mikayala.ui.components.VoicePlaybackCoordinator
import com.example.mikayala.utils.VoiceNoteState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
        // Vérification de la fonction de production MyFirebaseMessagingService.maskToken
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

        // 1. Même utilisateur : doit être ignoré
        assertTrue(MyFirebaseMessagingService.shouldIgnoreNotification(userA, userA))

        // 2. Utilisateurs différents : ne doit PAS être ignoré
        assertFalse(MyFirebaseMessagingService.shouldIgnoreNotification(userA, userB))

        // 3. Cas limites (UID null ou vide) : ne pas filtrer
        assertFalse(MyFirebaseMessagingService.shouldIgnoreNotification(null, userB))
        assertFalse(MyFirebaseMessagingService.shouldIgnoreNotification(userA, null))
        assertFalse(MyFirebaseMessagingService.shouldIgnoreNotification("", userB))
    }

    @Test
    fun testResolveCallerNameWithCamouflageModes() {
        val rawCaller = "Élise"

        // Mode Météo
        assertEquals("Service Météo", MyFirebaseMessagingService.resolveCallerName("weather_camouflage", rawCaller))

        // Mode Système
        assertEquals("Système", MyFirebaseMessagingService.resolveCallerName("system_camouflage", rawCaller))

        // Mode Discret
        assertEquals("Mikayala", MyFirebaseMessagingService.resolveCallerName("discreet", rawCaller))

        // Mode Romantique / Standard
        assertEquals("Élise", MyFirebaseMessagingService.resolveCallerName("romantic", rawCaller))

        // Nom vide -> fallback sur 'Mon Amour'
        assertEquals("Mon Amour", MyFirebaseMessagingService.resolveCallerName("romantic", ""))
    }

    @Test
    fun testResolveMessageDisplayCamouflageAndTypes() {
        val rawSender = "Alex"

        // 1. Mode Météo (tous types)
        val weather = MyFirebaseMessagingService.resolveMessageDisplay("chat_message", "weather_camouflage", rawSender, "Bonjour")
        assertEquals("Météo ☀️", weather.first)
        assertEquals("Ciel dégagé • 21°C", weather.second)

        // 2. Mode Système (tous types)
        val system = MyFirebaseMessagingService.resolveMessageDisplay("chat_message", "system_camouflage", rawSender, "Bonjour")
        assertEquals("Système ⚙️", system.first)
        assertEquals("Mise à jour système prête", system.second)

        // 3. Mode Discret
        val discreet = MyFirebaseMessagingService.resolveMessageDisplay("chat_message", "discreet", rawSender, "Texte secret")
        assertEquals("Mikayala 🔒", discreet.first)
        assertEquals("Nouveau message chiffré", discreet.second)

        // 4. Mode Romantique avec différents types multimédia
        val audioMsg = MyFirebaseMessagingService.resolveMessageDisplay("audio", "romantic", rawSender, "")
        assertEquals("Alex", audioMsg.first)
        assertEquals("🎤 Note vocale reçue", audioMsg.second)

        val imageMsg = MyFirebaseMessagingService.resolveMessageDisplay("image", "romantic", rawSender, "")
        assertEquals("Alex", imageMsg.first)
        assertEquals("📷 Photo reçue", imageMsg.second)

        val videoMsg = MyFirebaseMessagingService.resolveMessageDisplay("video", "romantic", rawSender, "")
        assertEquals("Alex", videoMsg.first)
        assertEquals("📹 Vidéo reçue", videoMsg.second)

        // 5. Réponse à un message (prefix ↳)
        val replyMsg = MyFirebaseMessagingService.resolveMessageDisplay("chat_message", "romantic", rawSender, "D'accord !", isReply = true)
        assertEquals("Alex", replyMsg.first)
        assertEquals("↳ D'accord !", replyMsg.second)

        // Préfixe non dupliqué si déjà présent
        val alreadyPrefixed = MyFirebaseMessagingService.resolveMessageDisplay("chat_message", "romantic", rawSender, "↳ Déjà préfixé", isReply = true)
        assertEquals("↳ Déjà préfixé", alreadyPrefixed.second)
    }

    @Test
    fun testCallActionReceiverConstants() {
        assertEquals("com.example.mikayala.ACTION_ANSWER_CALL", CallActionReceiver.ACTION_ANSWER_CALL)
        assertEquals("com.example.mikayala.ACTION_DECLINE_CALL", CallActionReceiver.ACTION_DECLINE_CALL)
        assertEquals("CALLER_NAME", CallActionReceiver.EXTRA_CALLER_NAME)
        assertEquals("IS_VIDEO", CallActionReceiver.EXTRA_IS_VIDEO)
    }

    @Test
    fun testTokenSyncLifecycleStateMachine() {
        // Simulation de la machine à états de synchronisation de jeton FCM
        var pendingToken: String? = null
        var registeredTokenInDb: String? = null
        var authenticatedUid: String? = null

        fun simulateTokenReceived(token: String) {
            pendingToken = token
            if (authenticatedUid != null) {
                registeredTokenInDb = token
                pendingToken = null
            }
        }

        fun simulateLogin(uid: String) {
            authenticatedUid = uid
            if (pendingToken != null) {
                registeredTokenInDb = pendingToken
                pendingToken = null
            }
        }

        fun simulateLogout(): Boolean {
            if (authenticatedUid != null && registeredTokenInDb != null) {
                registeredTokenInDb = null
                pendingToken = null
                authenticatedUid = null
                return true
            }
            authenticatedUid = null
            return false
        }

        // Étape 1 : Token généré avant la connexion (démarrage à froid / installation)
        simulateTokenReceived("fcm_token_initial_1234567890")
        assertEquals("fcm_token_initial_1234567890", pendingToken)
        assertNull(registeredTokenInDb)

        // Étape 2 : L'utilisateur se connecte
        simulateLogin("user_alice_uuid")
        assertNull(pendingToken) // Nettoyé après synchronisation
        assertEquals("fcm_token_initial_1234567890", registeredTokenInDb)

        // Étape 3 : Déconnexion attendue
        val unregisterSuccess = simulateLogout()
        assertTrue(unregisterSuccess)
        assertNull(registeredTokenInDb)
        assertNull(authenticatedUid)
        assertNull(pendingToken)
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
        // Vérifie qu'il n'y a pas de littéral 'now()' incorrect pour PostgREST
        assertFalse(formatted.contains("now()"))
    }
}
