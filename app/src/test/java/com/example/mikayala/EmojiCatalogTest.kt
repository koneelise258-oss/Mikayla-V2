package com.example.mikayala

import com.example.mikayala.ui.components.EmojiCatalog
import com.example.mikayala.ui.components.EmojiItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Phase 3 — catalogue d'émojis complet et recherche en français. */
class EmojiCatalogTest {

    private fun catalogLines(): List<List<String>> {
        val f = listOf(File("src/main/assets/emoji/emoji_fr.tsv"), File("app/src/main/assets/emoji/emoji_fr.tsv")).first { it.exists() }
        return f.readLines(Charsets.UTF_8).filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
    }

    @Test
    fun catalogIsCompleteAndContainsOnlyRealEmojis() {
        val rows = catalogLines()
        assertTrue("catalogue trop petit : ${rows.size}", rows.size >= 1800)
        // Aucune entrée « mot » (l'ancien catalogue insérait « Map », « Camping », « Hotel »…)
        val words = rows.filter { r -> r[1].any { it.isLetter() && it.code < 128 } && !r[1].startsWith("\uD83C") }
        assertTrue("entrées non émoji : ${words.take(5)}", words.isEmpty())
        val cats = rows.map { it[0] }.toSet()
        assertEquals(setOf("smileys", "people", "animals", "food", "travel", "activities", "objects", "symbols", "flags"), cats)
    }

    @Test
    fun ivoryCoastAndCommonEmojisArePresent() {
        val chars = catalogLines().map { it[1] }.toSet()
        for (e in listOf("🇨🇮", "❤️", "😂", "🥰", "🙏", "🔥", "🎉")) assertTrue(e, e in chars)
    }

    @Test
    fun searchIgnoresAccentsAndCase() {
        val list = listOf(
            EmojiItem("❤️", "cœur rouge", "symbols", EmojiCatalog.normalize("cœur rouge amour")),
            EmojiItem("🎉", "cotillons", "activities", EmojiCatalog.normalize("cotillons fête célébration"))
        )
        assertEquals(listOf("❤️"), EmojiCatalog.search(list, "COEUR").map { it.char })
        assertEquals(listOf("🎉"), EmojiCatalog.search(list, "fete").map { it.char })
        assertEquals(listOf("🎉"), EmojiCatalog.search(list, "célébration").map { it.char })
        assertTrue(EmojiCatalog.search(list, "   ").isEmpty())
    }
}
