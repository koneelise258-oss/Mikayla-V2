package com.example.mikayala.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

/**
 * Styles de conversation Soft (Paramètres > Conversations).
 * Les identifiants historiques sont conservés pour ne perdre aucun choix enregistré.
 */
object SoftChatStyles {
    data class Bubble(val background: Color, val text: Color, val outlined: Boolean)

    /** Choix de bulles proposés : identifiant historique → libellé. */
    val BUBBLES = listOf(
        "gradient_pink" to "Rose poudré",
        "neon_cyan" to "Bleu brume",
        "dark_gold" to "Sable",
        "emerald_luxury" to "Sauge",
        "frosted_glass" to "Neutre"
    )

    /** Choix de fonds proposés : identifiant historique → libellé. */
    val WALLPAPERS = listOf(
        "twilight" to "Uni",
        "sunset" to "Pêche",
        "rose_gold" to "Rose",
        "cyber_cyan" to "Brume",
        "matte_black" to "Lin"
    )

    fun bubble(style: String, p: MikayalaPalette, isSender: Boolean): Bubble {
        if (!isSender) return Bubble(p.bubbleIncoming, p.textPrimary, outlined = !p.isDark)
        val bg = when (style) {
            "neon_cyan" -> if (p.isDark) Color(0xFF2F3E4D) else Color(0xFFDCE7F1)
            "dark_gold" -> if (p.isDark) Color(0xFF4A4030) else Color(0xFFF1E6D3)
            "emerald_luxury" -> if (p.isDark) Color(0xFF2F4239) else Color(0xFFDDEBE2)
            "frosted_glass" -> if (p.isDark) Color(0xFF34333A) else Color(0xFFEDE8E2)
            else -> p.bubbleOutgoing
        }
        return Bubble(bg, p.onBubbleOutgoing, outlined = false)
    }

    fun wallpaper(theme: String, p: MikayalaPalette): Brush {
        val tint = when (theme) {
            "sunset" -> if (p.isDark) Color(0xFF221D1C) else Color(0xFFFBF1EA)
            "rose_gold" -> if (p.isDark) Color(0xFF221C1F) else Color(0xFFFAF0F1)
            "cyber_cyan" -> if (p.isDark) Color(0xFF1B1E22) else Color(0xFFF0F4F7)
            "matte_black" -> if (p.isDark) Color(0xFF1E1D1B) else Color(0xFFF4F0E8)
            else -> p.background
        }
        return SolidColor(tint)
    }
}
