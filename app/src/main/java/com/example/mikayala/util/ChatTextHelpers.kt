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
}
