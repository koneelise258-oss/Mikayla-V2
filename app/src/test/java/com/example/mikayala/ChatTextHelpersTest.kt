package com.example.mikayala

import com.example.mikayala.util.ChatTextHelpers
import com.example.mikayala.util.GroupedReaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTextHelpersTest {

    @Test
    fun groupReactionsMergesCountsPreservesOrderAndDetectsCurrentUser() {
        val raw = """{"u2":"❤️","u1":"❤️","u3":"😂","u4":"  "}"""
        val grouped = ChatTextHelpers.groupReactions(raw, currentUserId = "u1")

        assertEquals(
            listOf(
                GroupedReaction(emoji = "❤️", count = 2, includesMe = true),
                GroupedReaction(emoji = "😂", count = 1, includesMe = false)
            ),
            grouped
        )
    }

    @Test
    fun groupReactionsHandlesInvalidOrBlankJsonSafely() {
        assertTrue(ChatTextHelpers.groupReactions(null, "u1").isEmpty())
        assertTrue(ChatTextHelpers.groupReactions("  ", "u1").isEmpty())
        assertTrue(ChatTextHelpers.groupReactions("not-json", "u1").isEmpty())
        assertTrue(ChatTextHelpers.groupReactions("""{"u1":123}""", "u1").isEmpty())
    }

    @Test
    fun findUrlRangesTrimsTrailingPunctuationAndNormalizesWww() {
        val text = "Regarde https://example.com/photo?a=1, et aussi www.openai.com/docs!"
        val ranges = ChatTextHelpers.findUrlRanges(text)

        assertEquals(2, ranges.size)
        val first = text.substring(ranges[0].first, ranges[0].last + 1)
        val second = text.substring(ranges[1].first, ranges[1].last + 1)

        assertEquals("https://example.com/photo?a=1", first)
        assertEquals("www.openai.com/docs", second)
        assertEquals("https://example.com/photo?a=1", ChatTextHelpers.normalizeUrl(first))
        assertEquals("https://www.openai.com/docs", ChatTextHelpers.normalizeUrl(second))
        assertTrue(ChatTextHelpers.containsLink(text))
        assertFalse(ChatTextHelpers.containsLink("Juste un message normal sans lien."))
    }

    @Test
    fun findSearchRangesFindsAllCaseInsensitiveOccurrences() {
        val text = "Coucou mon Amour, amour toujours !"
        val ranges = ChatTextHelpers.findSearchRanges(text, "  amour ")

        assertEquals(2, ranges.size)
        assertEquals("Amour", text.substring(ranges[0].first, ranges[0].last + 1))
        assertEquals("amour", text.substring(ranges[1].first, ranges[1].last + 1))
        assertTrue(ChatTextHelpers.findSearchRanges(text, "").isEmpty())
    }

    @Test
    fun isGroupedWithPreviousGroupsWithinThreeMinutesOnly() {
        val t0 = 1_700_000_000_000L
        assertTrue(
            ChatTextHelpers.isGroupedWithPrevious(
                previousSenderId = "u1",
                previousCreatedAt = t0,
                previousDeleted = false,
                currentSenderId = "u1",
                currentCreatedAt = t0 + 180_000L,
                currentDeleted = false
            )
        )
        assertFalse(
            ChatTextHelpers.isGroupedWithPrevious(
                previousSenderId = "u1",
                previousCreatedAt = t0,
                previousDeleted = false,
                currentSenderId = "u1",
                currentCreatedAt = t0 + 180_001L,
                currentDeleted = false
            )
        )
        assertFalse(
            ChatTextHelpers.isGroupedWithPrevious(
                previousSenderId = "u2",
                previousCreatedAt = t0,
                previousDeleted = false,
                currentSenderId = "u1",
                currentCreatedAt = t0 + 5_000L,
                currentDeleted = false
            )
        )
        assertFalse(
            ChatTextHelpers.isGroupedWithPrevious(
                previousSenderId = "u1",
                previousCreatedAt = t0,
                previousDeleted = true,
                currentSenderId = "u1",
                currentCreatedAt = t0 + 5_000L,
                currentDeleted = false
            )
        )
    }
}
