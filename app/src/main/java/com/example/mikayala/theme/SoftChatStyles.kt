package com.example.mikayala.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

/**
 * Styles de conversation et thèmes personnalisables (Paramètres > Apparence & Conversations).
 * Tous les identifiants historiques sont conservés pour assurer la compatibilité des préférences.
 */
object SoftChatStyles {
    data class Bubble(val background: Color, val text: Color, val outlined: Boolean)

    val ACCENT_THEMES = listOf(
        "violet_emerald" to "Violet & Émeraude (défaut)",
        "emerald_violet" to "Émeraude & Violet",
        "amethyst_mint" to "Améthyste & Menthe",
        "forest_plum" to "Forêt & Prune",
        "rose_soft" to "Rose poudré classique"
    )

    val BUBBLES = listOf(
        "gradient_pink" to "Violet royal",
        "emerald_luxury" to "Vert émeraude",
        "neon_cyan" to "Améthyste douce",
        "dark_gold" to "Sable & or",
        "frosted_glass" to "Verre neutre"
    )

    val WALLPAPERS = listOf(
        "twilight" to "Violet nuit",
        "cyber_cyan" to "Émeraude brume",
        "sunset" to "Aurore violet-vert",
        "rose_gold" to "Prune velours",
        "matte_black" to "Noir profond"
    )

    val PATTERNS = listOf(
        "whatsapp_doodle" to "Motifs WhatsApp",
        "hearts" to "Cœurs discrets",
        "botanical" to "Feuilles émeraude",
        "none" to "Uni sans motif"
    )

    val NAV_STYLES = listOf(
        "whatsapp_pill" to "Pilule WhatsApp",
        "floating_glass" to "Barre flottante",
        "compact" to "Compacte"
    )

    fun bubble(style: String, p: MikayalaPalette, isSender: Boolean): Bubble {
        if (!isSender) return Bubble(p.bubbleIncoming, p.textPrimary, outlined = !p.isDark && p.bubbleIncoming == Color.White)
        val bg = when (style) {
            "emerald_luxury" -> if (p.isDark) Color(0xFF125A43) else Color(0xFFD1FAE5)
            "neon_cyan" -> if (p.isDark) Color(0xFF4C2E7A) else Color(0xFFF3E8FF)
            "dark_gold" -> if (p.isDark) Color(0xFF4E4027) else Color(0xFFFEF3C7)
            "frosted_glass" -> if (p.isDark) Color(0xFF2B2738) else Color(0xFFECE9F4)
            else -> p.bubbleOutgoing
        }
        val txt = when (style) {
            "emerald_luxury" -> if (p.isDark) Color(0xFFECFDF5) else Color(0xFF064E3B)
            "neon_cyan" -> if (p.isDark) Color(0xFFF5F3FF) else Color(0xFF3B0764)
            "dark_gold" -> if (p.isDark) Color(0xFFFFFBEB) else Color(0xFF451A03)
            "frosted_glass" -> p.textPrimary
            else -> p.onBubbleOutgoing
        }
        return Bubble(bg, txt, outlined = style == "frosted_glass")
    }

    fun wallpaper(theme: String, p: MikayalaPalette): Brush {
        return when (theme) {
            "cyber_cyan" -> Brush.verticalGradient(
                if (p.isDark) listOf(Color(0xFF091B16), Color(0xFF0D231C), Color(0xFF081410))
                else listOf(Color(0xFFE9F8F2), Color(0xFFF2FBF7), Color(0xFFE6F4EE))
            )
            "sunset" -> Brush.verticalGradient(
                if (p.isDark) listOf(Color(0xFF1C1030), Color(0xFF111C20), Color(0xFF0B1512))
                else listOf(Color(0xFFF3ECFD), Color(0xFFEEF9F4), Color(0xFFEAF7F1))
            )
            "rose_gold" -> Brush.verticalGradient(
                if (p.isDark) listOf(Color(0xFF21132C), Color(0xFF161024), Color(0xFF0F0B1A))
                else listOf(Color(0xFFF8EFFC), Color(0xFFF4ECFB), Color(0xFFEEE8F9))
            )
            "matte_black" -> SolidColor(if (p.isDark) Color(0xFF09080E) else Color(0xFFF1EFF5))
            else -> SolidColor(p.background)
        }
    }
}
