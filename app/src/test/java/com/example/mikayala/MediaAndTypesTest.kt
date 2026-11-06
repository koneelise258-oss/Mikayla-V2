package com.example.mikayala

import com.example.mikayala.util.MessageTypeTag
import com.example.mikayala.util.SignedUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 2 — types de messages conservés et liens signés des médias. */
class MediaAndTypesTest {

    // --- Type réel des messages (jeux, documents…) ---

    @Test
    fun coreTypesAreNeverTagged() {
        for (t in listOf("text", "image", "audio", "video", "photo", "voice")) {
            assertFalse(t, MessageTypeTag.isExtended(t))
            assertEquals("Bonjour", MessageTypeTag.encode(t, "Bonjour"))
        }
    }

    @Test
    fun gameTypeSurvivesRoundTrip() {
        val stored = MessageTypeTag.encode("tictactoe", "Partie de Morpion")
        assertEquals("tictactoe" to "Partie de Morpion", MessageTypeTag.decode(stored))
        assertEquals("text", MessageTypeTag.coreType("tictactoe"))
    }

    @Test
    fun allChatGameAndAttachmentTypesAreExtended() {
        for (t in listOf("tictactoe", "wheel", "scratch_card", "document", "location", "poll", "quiz", "dilemma", "coupon")) {
            assertTrue(t, MessageTypeTag.isExtended(t))
            assertEquals(t, MessageTypeTag.decode(MessageTypeTag.encode(t, "x")).first)
        }
    }

    @Test
    fun plainTextIsNotMistakenForATag() {
        assertEquals(null to "mk:tictactoe salut", MessageTypeTag.decode("mk:tictactoe salut"))
        assertEquals(null to "", MessageTypeTag.decode(null))
    }

    @Test
    fun tagIsInvisibleSoOldAppsShowNormalText() {
        val stored = MessageTypeTag.encode("wheel", "Roue de l'amour")
        val visible = stored.filter { it != '⁣' }
        assertEquals("mk:wheelRoue de l'amour", visible) // seul « mk:wheel » serait visible, sans séparateurs
        assertTrue(stored.endsWith("Roue de l'amour"))
    }

    // --- Liens signés : expiration ---

    private fun signedUrl(expSeconds: Long): String {
        val payload = """{"url":"messages-media/c1/photos/a.webp","iat":1,"exp":$expSeconds}"""
        val b64 = base64Url(payload.toByteArray())
        return "https://x.supabase.co/storage/v1/object/sign/messages-media/c1/photos/a.webp?token=eyJhbGciOiJIUzI1NiJ9.$b64.sig"
    }

    private fun base64Url(bytes: ByteArray): String {
        val a = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val sb = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0
            sb.append(a[b0 shr 2]); sb.append(a[((b0 and 3) shl 4) or (b1 shr 4)])
            if (i + 1 < bytes.size) sb.append(a[((b1 and 15) shl 2) or (b2 shr 6)])
            if (i + 2 < bytes.size) sb.append(a[b2 and 63])
            i += 3
        }
        return sb.toString()
    }

    @Test
    fun expiryIsReadFromSignedUrl() {
        assertEquals(2_000_000_000_000L, SignedUrls.expiryMillis(signedUrl(2_000_000_000L)))
    }

    @Test
    fun expiredSignedUrlIsNotUsable() {
        val now = 1_800_000_000_000L
        assertFalse(SignedUrls.isUsable(signedUrl(now / 1000 - 10), now))
        // moins d'une heure restante : régénéré par précaution
        assertFalse(SignedUrls.isUsable(signedUrl(now / 1000 + 600), now))
        assertTrue(SignedUrls.isUsable(signedUrl(now / 1000 + 86_400), now))
    }

    @Test
    fun localFilesAndUnsignedUrlsStayUsable() {
        assertTrue(SignedUrls.isUsable("/data/user/0/app/files/outgoing_media/a.webp"))
        assertTrue(SignedUrls.isUsable("https://example.com/image.png"))
        assertFalse(SignedUrls.isUsable(null))
        assertNull(SignedUrls.expiryMillis("https://x/storage/v1/object/sign/b/p?token=bad"))
    }
}
