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
}
