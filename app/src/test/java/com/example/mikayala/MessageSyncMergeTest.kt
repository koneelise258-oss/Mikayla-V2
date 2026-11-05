package com.example.mikayala

import com.example.mikayala.data.model.MessageEntity
import com.example.mikayala.data.repository.MikayalaRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1 — fusion des messages locaux et serveur pendant la synchronisation.
 * Ces règles empêchent la perte de messages, les doublons et le recul des accusés.
 */
class MessageSyncMergeTest {

    private fun msg(id: String, at: Long, status: String = "sent", sender: String = "A", content: String = id) =
        MessageEntity(id = id, coupleId = "c1", senderId = sender, content = content, createdAt = at, status = status)

    @Test
    fun localPendingMessageIsNeverLostWhenServerDoesNotHaveItYet() {
        val local = listOf(msg("1", 1000), msg("2", 2000, status = "pending"))
        val remote = listOf(msg("1", 1000))
        val merged = MikayalaRepository.mergeMessageLists(local, remote)
        assertEquals(listOf("1", "2"), merged.map { it.id })
        assertEquals("pending", merged.first { it.id == "2" }.status)
    }

    @Test
    fun newRemoteMessagesAreAddedOnceAndSortedChronologically() {
        val local = listOf(msg("1", 1000), msg("3", 3000))
        val remote = listOf(msg("2", 2000, sender = "B"), msg("3", 3000), msg("2", 2000, sender = "B"))
        val merged = MikayalaRepository.mergeMessageLists(local, remote)
        assertEquals(listOf("1", "2", "3"), merged.map { it.id })
    }

    @Test
    fun duplicateLocalEntriesAreCollapsed() {
        val local = listOf(msg("1", 1000), msg("1", 1000))
        val merged = MikayalaRepository.mergeMessageLists(local, listOf(msg("2", 2000)))
        assertEquals(listOf("1", "2"), merged.map { it.id })
    }

    @Test
    fun readReceiptNeverGoesBackwards() {
        // Accusé « lu » reçu en temps réel, puis une synchronisation plus ancienne répond « reçu »
        val local = listOf(msg("1", 1000, status = "read"))
        val remote = listOf(msg("1", 1000, status = "delivered"))
        assertEquals("read", MikayalaRepository.mergeMessageLists(local, remote).single().status)
    }

    @Test
    fun serverConfirmationReplacesLocalPendingState() {
        val local = listOf(msg("1", 1000, status = "pending"))
        val remote = listOf(msg("1", 1000, status = "sent"))
        assertEquals("sent", MikayalaRepository.mergeMessageLists(local, remote).single().status)
    }

    @Test
    fun localMediaUrlAndReplyPreviewAreKeptWhenServerOmitsThem() {
        val local = listOf(
            msg("1", 1000).copy(mediaUrl = "https://signed", replyToId = "0", replyToSender = "Moi", replyToContent = "Salut")
        )
        val remote = listOf(msg("1", 1000).copy(mediaUrl = null, replyToId = "0"))
        val merged = MikayalaRepository.mergeMessageLists(local, remote).single()
        assertEquals("https://signed", merged.mediaUrl)
        assertEquals("Moi", merged.replyToSender)
        assertEquals("Salut", merged.replyToContent)
    }

    @Test
    fun remoteEditsAndReactionsWin() {
        val local = listOf(msg("1", 1000, content = "avant"))
        val remote = listOf(msg("1", 1000, content = "après").copy(reactions = """{"❤️":["B"]}"""))
        val merged = MikayalaRepository.mergeMessageLists(local, remote).single()
        assertEquals("après", merged.content)
        assertTrue(merged.reactions.contains("❤️"))
    }

    @Test
    fun emptyFetchLeavesLocalListUntouched() {
        val local = listOf(msg("1", 1000), msg("2", 2000, status = "failed"))
        assertEquals(local, MikayalaRepository.mergeMessageLists(local, emptyList()))
    }

    @Test
    fun failedLocalStatusIsReplacedWhenServerHasTheMessage() {
        // Envoi réussi côté serveur mais réponse perdue : la synchro corrige « échec » en « envoyé »
        val local = listOf(msg("1", 1000, status = "failed"))
        val merged = MikayalaRepository.mergeRemoteMessage(local.single(), msg("1", 1000, status = "sent"))
        assertEquals("sent", merged.status)
        assertNull(merged.readAt)
    }
}
