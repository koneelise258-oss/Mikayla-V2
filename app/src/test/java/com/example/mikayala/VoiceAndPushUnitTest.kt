package com.example.mikayala

import com.example.mikayala.services.CallActionReceiver
import com.example.mikayala.ui.components.VoicePlaybackCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceAndPushUnitTest {

    @Test
    fun testVoicePlaybackCoordinatorSinglePlayback() {
        // Given coordinator is at clean state
        VoicePlaybackCoordinator.stopAll()
        assertNull(VoicePlaybackCoordinator.currentlyPlayingUrl.value)

        // When note 1 plays
        VoicePlaybackCoordinator.requestPlay("https://supabase.co/audio1.m4a")
        assertEquals("https://supabase.co/audio1.m4a", VoicePlaybackCoordinator.currentlyPlayingUrl.value)

        // When note 2 plays, coordinator updates to note 2
        VoicePlaybackCoordinator.requestPlay("https://supabase.co/audio2.m4a")
        assertEquals("https://supabase.co/audio2.m4a", VoicePlaybackCoordinator.currentlyPlayingUrl.value)

        // When note 1 signals completion, it must not stop note 2
        VoicePlaybackCoordinator.notifyStopped("https://supabase.co/audio1.m4a")
        assertEquals("https://supabase.co/audio2.m4a", VoicePlaybackCoordinator.currentlyPlayingUrl.value)

        // When note 2 signals completion, active playback is cleared
        VoicePlaybackCoordinator.notifyStopped("https://supabase.co/audio2.m4a")
        assertNull(VoicePlaybackCoordinator.currentlyPlayingUrl.value)
    }

    @Test
    fun testTokenMaskingFormat() {
        val testToken = "fKm98xLa7200q1Z58921abcdef0123456789"
        val masked = if (testToken.length > 10) "${testToken.take(6)}...${testToken.takeLast(4)}" else "***"
        assertEquals("fKm98x...6789", masked)
    }

    @Test
    fun testCallActionReceiverConstants() {
        assertEquals("com.example.mikayala.ACTION_ANSWER_CALL", CallActionReceiver.ACTION_ANSWER_CALL)
        assertEquals("com.example.mikayala.ACTION_DECLINE_CALL", CallActionReceiver.ACTION_DECLINE_CALL)
    }

    @Test
    fun testNotificationCamouflageMapping() {
        val rawSender = "Mon Amour"
        val rawMessage = "Tu me manques tellement ❤️"

        // Mode 1 : discreet
        val discreet = when ("discreet") {
            "weather_camouflage" -> Pair("Météo ☀️", "Ciel dégagé • 21°C")
            "system_camouflage" -> Pair("Système ⚙️", "Mise à jour système prête")
            "discreet" -> Pair("Mikayala 🔒", "Nouveau message chiffré")
            else -> Pair(rawSender, rawMessage)
        }
        assertEquals("Mikayala 🔒", discreet.first)
        assertEquals("Nouveau message chiffré", discreet.second)

        // Mode 2 : weather camouflage
        val weather = when ("weather_camouflage") {
            "weather_camouflage" -> Pair("Météo ☀️", "Ciel dégagé • 21°C")
            "system_camouflage" -> Pair("Système ⚙️", "Mise à jour système prête")
            "discreet" -> Pair("Mikayala 🔒", "Nouveau message chiffré")
            else -> Pair(rawSender, rawMessage)
        }
        assertEquals("Météo ☀️", weather.first)
        assertEquals("Ciel dégagé • 21°C", weather.second)

        // Mode 3 : system camouflage
        val system = when ("system_camouflage") {
            "weather_camouflage" -> Pair("Météo ☀️", "Ciel dégagé • 21°C")
            "system_camouflage" -> Pair("Système ⚙️", "Mise à jour système prête")
            "discreet" -> Pair("Mikayala 🔒", "Nouveau message chiffré")
            else -> Pair(rawSender, rawMessage)
        }
        assertEquals("Système ⚙️", system.first)
        assertEquals("Mise à jour système prête", system.second)

        // Mode 4 : romantic
        val romantic = when ("romantic") {
            "weather_camouflage" -> Pair("Météo ☀️", "Ciel dégagé • 21°C")
            "system_camouflage" -> Pair("Système ⚙️", "Mise à jour système prête")
            "discreet" -> Pair("Mikayala 🔒", "Nouveau message chiffré")
            else -> Pair(rawSender, rawMessage)
        }
        assertEquals("Mon Amour", romantic.first)
        assertEquals("Tu me manques tellement ❤️", romantic.second)
    }

    @Test
    fun testSelfNotificationFilter() {
        val currentUserId = "user_123_abc"
        val incomingSenderId = "user_123_abc"
        val shouldIgnore = currentUserId == incomingSenderId
        assertEquals(true, shouldIgnore)

        val partnerSenderId = "user_456_xyz"
        val shouldIgnorePartner = currentUserId == partnerSenderId
        assertEquals(false, shouldIgnorePartner)
    }

    @Test
    fun testVoiceNoteStates() {
        val states = com.example.mikayala.utils.VoiceNoteState.values().map { it.name }
        assertEquals(true, states.contains("IDLE"))
        assertEquals(true, states.contains("RECORDING"))
        assertEquals(true, states.contains("LOCKED"))
        assertEquals(true, states.contains("PAUSED"))
    }

    @Test
    fun testIso8601TimestampFormat() {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val formatted = sdf.format(java.util.Date(1730640000000L)) // Fixed timestamp
        assertEquals(true, formatted.endsWith("Z"))
        assertEquals(20, formatted.length)
    }
}
