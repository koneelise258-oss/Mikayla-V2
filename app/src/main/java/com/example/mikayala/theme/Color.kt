package com.example.mikayala.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ==============================================================================
// SYSTÈME DE DESIGN « SOFT » — couleurs sémantiques, mode clair ET mode sombre
// ------------------------------------------------------------------------------
// Une seule identité : crème / blanc doux / gris chaud / anthracite, accents pastel.
// Les écrans utilisent des noms sémantiques (fond, surface, texte, accent…) : les valeurs
// changent selon le thème actif (Paramètres > Apparence), sans toucher aux écrans.
// Les anciens noms (DeepNight, CardDark, VibrantCyan…) sont conservés comme alias pour
// que tout l'existant bascule automatiquement sur la nouvelle palette.
// ==============================================================================

@Immutable
data class MikayalaPalette(
    val isDark: Boolean,
    // Fonds et surfaces
    val background: Color,        // fond principal
    val surface: Color,           // surfaces (listes, cartes discrètes)
    val surfaceElevated: Color,   // surfaces mises en avant, éléments sélectionnés
    val surfaceSunken: Color,     // zones en creux (champs de saisie, pistes)
    val modal: Color,             // feuilles et boîtes de dialogue
    // Textes
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    // Lignes
    val border: Color,
    val borderStrong: Color,
    // Accents pastel
    val accent: Color,            // rose poudré : action principale
    val accentSoft: Color,        // fond léger derrière un accent
    val lavender: Color,
    val sky: Color,               // bleu doux : liens, informations
    val sand: Color,              // sable doré : mises en valeur
    val warning: Color,
    val success: Color,
    val danger: Color,
    // Messagerie
    val bubbleOutgoing: Color,
    val bubbleIncoming: Color,
    val onBubbleOutgoing: Color,
    // États
    val pressed: Color,
)

val SoftLight = MikayalaPalette(
    isDark = false,
    background = Color(0xFFFAF7F2),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF3EEE8),
    surfaceSunken = Color(0xFFF1ECE6),
    modal = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF2B2A2E),
    textSecondary = Color(0xFF6B6661),
    textMuted = Color(0xFF9A948D),
    border = Color(0xFFE8E1D9),
    borderStrong = Color(0xFFD6CCC1),
    accent = Color(0xFFC97B86),
    accentSoft = Color(0xFFF6E3E5),
    lavender = Color(0xFF9283C4),
    sky = Color(0xFF6E97BF),
    sand = Color(0xFFB8925A),
    warning = Color(0xFFC9963E),
    success = Color(0xFF6FA287),
    danger = Color(0xFFC4605C),
    bubbleOutgoing = Color(0xFFF4DEE0),
    bubbleIncoming = Color(0xFFFFFFFF),
    onBubbleOutgoing = Color(0xFF2B2A2E),
    pressed = Color(0x0F2B2A2E),
)

val SoftDark = MikayalaPalette(
    isDark = true,
    background = Color(0xFF1B1A1E),
    surface = Color(0xFF242328),
    surfaceElevated = Color(0xFF2D2B31),
    surfaceSunken = Color(0xFF17161A),
    modal = Color(0xFF26252A),
    textPrimary = Color(0xFFEEEAE5),
    textSecondary = Color(0xFFADA7A1),
    textMuted = Color(0xFF7F7A75),
    border = Color(0xFF34323A),
    borderStrong = Color(0xFF45424B),
    accent = Color(0xFFE6A3AD),
    accentSoft = Color(0xFF3A2C30),
    lavender = Color(0xFFBDB0EA),
    sky = Color(0xFF9DC2E4),
    sand = Color(0xFFE2C796),
    warning = Color(0xFFE3B866),
    success = Color(0xFF8EC2A5),
    danger = Color(0xFFE58A85),
    bubbleOutgoing = Color(0xFF4A3539),
    bubbleIncoming = Color(0xFF2A292E),
    onBubbleOutgoing = Color(0xFFF5EEEC),
    pressed = Color(0x14FFFFFF),
)

val LocalMikayalaPalette = staticCompositionLocalOf { SoftDark }

/** Accès direct : MikayalaColors.accent, MikayalaColors.surface… */
val MikayalaColors: MikayalaPalette
    @Composable @ReadOnlyComposable get() = LocalMikayalaPalette.current

private val p: MikayalaPalette @Composable @ReadOnlyComposable get() = LocalMikayalaPalette.current

// ---------- Alias historiques (utilisés par les écrans existants) ----------
// Fonds
val MatteSlateBackground: Color @Composable @ReadOnlyComposable get() = p.background
val DeepNight: Color @Composable @ReadOnlyComposable get() = p.background
val DeepSurfaceBlack: Color @Composable @ReadOnlyComposable get() = p.surfaceSunken
// Surfaces
val MatteCardDark: Color @Composable @ReadOnlyComposable get() = p.surface
val CardDark: Color @Composable @ReadOnlyComposable get() = p.surface
val MatteCardElevated: Color @Composable @ReadOnlyComposable get() = p.surfaceElevated
val CardElevated: Color @Composable @ReadOnlyComposable get() = p.surfaceElevated
val CardDarkElevated: Color @Composable @ReadOnlyComposable get() = p.surfaceElevated
val CardModalSurface: Color @Composable @ReadOnlyComposable get() = p.modal
val CardDarkBlur95: Color @Composable @ReadOnlyComposable get() = p.surface.copy(alpha = 0.95f)
val MatteSquircle: Color @Composable @ReadOnlyComposable get() = p.surfaceElevated
val MatteSquircleBorder: Color @Composable @ReadOnlyComposable get() = p.border
val MatteSquircleHighlight: Color @Composable @ReadOnlyComposable get() = p.pressed
val MatteInset: Color @Composable @ReadOnlyComposable get() = p.surfaceSunken
// Accents : plus de dégradés fluo, des pastels sobres
val CyanBlueGradientStart: Color @Composable @ReadOnlyComposable get() = p.accent
val CyanBlueGradientEnd: Color @Composable @ReadOnlyComposable get() = p.accent
val VibrantBlue: Color @Composable @ReadOnlyComposable get() = p.sky
val VibrantCyan: Color @Composable @ReadOnlyComposable get() = p.accent
val AccentRose: Color @Composable @ReadOnlyComposable get() = p.accent
val AccentViolet: Color @Composable @ReadOnlyComposable get() = p.lavender
val AccentGold: Color @Composable @ReadOnlyComposable get() = p.sand
val WarningGold: Color @Composable @ReadOnlyComposable get() = p.warning
val OutgoingGradientStart: Color @Composable @ReadOnlyComposable get() = p.bubbleOutgoing
val OutgoingGradientEnd: Color @Composable @ReadOnlyComposable get() = p.bubbleOutgoing
// Textes
val TextPrimary: Color @Composable @ReadOnlyComposable get() = p.textPrimary
val TextSecondary: Color @Composable @ReadOnlyComposable get() = p.textSecondary
val TextSecondary70: Color @Composable @ReadOnlyComposable get() = p.textSecondary
val TextMuted: Color @Composable @ReadOnlyComposable get() = p.textMuted
val TimeStampMuted: Color @Composable @ReadOnlyComposable get() = p.textMuted
// Lignes
val BorderSubtleWhite: Color @Composable @ReadOnlyComposable get() = p.border
val BorderHighlight: Color @Composable @ReadOnlyComposable get() = p.borderStrong
// Statuts
val StatusBlue: Color @Composable @ReadOnlyComposable get() = p.sky
val StatusGrey: Color @Composable @ReadOnlyComposable get() = p.textMuted
val StatusSent: Color @Composable @ReadOnlyComposable get() = p.textMuted
val OnlinePresenceGreen: Color @Composable @ReadOnlyComposable get() = p.success
// Messagerie
val ChatBubbleReceiver: Color @Composable @ReadOnlyComposable get() = p.bubbleIncoming
val ChatBubbleSender: Color @Composable @ReadOnlyComposable get() = p.bubbleOutgoing
val ChatBubbleSenderFallback: Color @Composable @ReadOnlyComposable get() = p.bubbleOutgoing
val ProgressRingFill: Color @Composable @ReadOnlyComposable get() = p.accent
val ProgressRingTrack: Color @Composable @ReadOnlyComposable get() = p.border
// États
val HoverStateWhite: Color @Composable @ReadOnlyComposable get() = p.pressed
val HoverStateCyan: Color @Composable @ReadOnlyComposable get() = p.accentSoft
val HoverStateCoral: Color @Composable @ReadOnlyComposable get() = p.accentSoft
