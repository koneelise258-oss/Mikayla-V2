package com.example.mikayala.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

data class GroupedReaction(
    val emoji: String,
    val count: Int,
    val includesMe: Boolean
)

data class TextMatchRange(
    val start: Int,
    val end: Int,
    val isUrl: Boolean
)

object ChatTextHelpers {

    private val URL_REGEX = Regex(
        """(?i)\b((?:https?://|www\.)[^\s<>()\[\]{}"']+)"""
    )

    /**
     * Parses a message's JSON reaction map `{"uid": "❤️"}` into grouped counts,
     * preserving first-seen emoji order so pills don't jump around while tapping.
     */
    fun groupReactions(reactionsJson: String?, currentUserId: String): List<GroupedReaction> {
        if (reactionsJson.isNullOrBlank()) return emptyList()
        return try {
            val json = Json.parseToJsonElement(reactionsJson).jsonObject
            val counts = LinkedHashMap<String, Pair<Int, Boolean>>()
            for ((uid, element) in json) {
                val emoji = (element as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim().orEmpty()
                if (emoji.isNotEmpty()) {
                    val prev = counts[emoji]
                    val newCount = (prev?.first ?: 0) + 1
                    val byMe = (prev?.second ?: false) || (currentUserId.isNotEmpty() && uid == currentUserId)
                    counts[emoji] = newCount to byMe
                }
            }
            counts.entries.map { (emoji, pair) ->
                GroupedReaction(emoji = emoji, count = pair.first, includesMe = pair.second)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Finds clickable URL ranges in plain text (excluding trailing sentence punctuation).
     */
    fun findUrlRanges(text: String): List<IntRange> {
        if (text.isBlank()) return emptyList()
        return URL_REGEX.findAll(text).mapNotNull { match ->
            var raw = match.value
            var end = match.range.last + 1
            while (raw.isNotEmpty() && raw.last() in charArrayOf('.', ',', ';', ':', '!', '?', ')')) {
                raw = raw.dropLast(1)
                end--
            }
            if (end > match.range.first) match.range.first until end else null
        }.toList()
    }

    fun normalizeUrl(raw: String): String =
        if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
            raw
        } else {
            "https://$raw"
        }

    /**
     * Finds case-insensitive search query occurrences in text.
     */
    fun findSearchRanges(text: String, query: String): List<IntRange> {
        val q = query.trim()
        if (q.isEmpty() || text.isEmpty()) return emptyList()
        val result = mutableListOf<IntRange>()
        var index = text.indexOf(q, ignoreCase = true)
        while (index >= 0) {
            result.add(index until (index + q.length))
            index = text.indexOf(q, startIndex = index + q.length, ignoreCase = true)
        }
        return result
    }

    fun containsLink(text: String): Boolean = findUrlRanges(text).isNotEmpty()

    /**
     * Groups consecutive messages sent by the same person within 3 minutes on the same day.
     */
    fun isGroupedWithPrevious(
        previousSenderId: String?,
        previousCreatedAt: Long?,
        previousDeleted: Boolean,
        currentSenderId: String,
        currentCreatedAt: Long,
        currentDeleted: Boolean
    ): Boolean {
        if (previousSenderId == null || previousCreatedAt == null) return false
        if (previousDeleted || currentDeleted) return false
        if (previousSenderId != currentSenderId) return false
        val diff = currentCreatedAt - previousCreatedAt
        return diff in 0L..180_000L
    }

    /**
     * Nombre d'émojis si le texte n'est QUE des émojis (espaces permis), sinon 0.
     * Compte les graphèmes (un émoji composé, drapeau ou famille = 1).
     */
    fun emojiOnlyCount(text: String): Int {
        val t = text.trim()
        if (t.isEmpty() || t.length > 40) return 0
        val it = java.text.BreakIterator.getCharacterInstance()
        it.setText(t)
        var count = 0
        var start = it.first()
        var end = it.next()
        while (end != java.text.BreakIterator.DONE) {
            val g = t.substring(start, end)
            if (g.isBlank()) { start = end; end = it.next(); continue }
            if (!isEmojiGrapheme(g)) return 0
            count++
            start = end
            end = it.next()
        }
        return count
    }

    private fun isEmojiGrapheme(g: String): Boolean {
        var hasEmoji = false
        var i = 0
        while (i < g.length) {
            val cp = g.codePointAt(i)
            val emojiLike = cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2300..0x23FF ||
                cp in 0x2B00..0x2BFF || cp == 0x2764 || cp in 0x1F1E6..0x1F1FF
            val joiner = cp == 0x200D || cp in 0xFE00..0xFE0F || cp in 0x1F3FB..0x1F3FF || cp == 0x20E3 || cp in 0xE0020..0xE007F
            if (emojiLike) hasEmoji = true
            else if (!joiner && !(cp in 0x30..0x39 || cp == 0x23 || cp == 0x2A)) return false
            i += Character.charCount(cp)
        }
        return hasEmoji
    }
}
