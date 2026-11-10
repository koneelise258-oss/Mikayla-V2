package com.example.mikayala.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ==============================================================================
// SYSTÈME DE DESIGN « SOFT » — couleurs sémantiques, mode clair ET mode sombre
// ------------------------------------------------------------------------------
// Identité du couple : vert (elle) + violet (lui), sur des fonds neutres doux.
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
    // Accents
    val accent: Color,            // vert : action principale
    val accentSoft: Color,        // fond léger derrière un accent
    val lavender: Color,          // violet : sélections, mises en valeur
    val sky: Color,               // bleu-vert doux : liens, informations
    val sand: Color,              // doré : mises en valeur secondaires
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
    background = Color(0xFFFAFAF7),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF1F2ED),
    surfaceSunken = Color(0xFFEFF0EB),
    modal = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF292B2A),
    textSecondary = Color(0xFF646A67),
    textMuted = Color(0xFF969C99),
    border = Color(0xFFE4E6E1),
    borderStrong = Color(0xFFD1D4CF),
    accent = Color(0xFF2E9E6B),          // vert principal
    accentSoft = Color(0xFFE2F3E9),     // fond vert très léger
    lavender = Color(0xFF7C5CD9),       // violet principal
    sky = Color(0xFF4E9A8E),            // bleu-vert doux
    sand = Color(0xFFB8925A),
    warning = Color(0xFFC9963E),
    success = Color(0xFF2E9E6B),
    danger = Color(0xFFC4605C),
    bubbleOutgoing = Color(0xFFD8F0E0),  // bulle envoyée : vert menthe
    bubbleIncoming = Color(0xFFFFFFFF),
    onBubbleOutgoing = Color(0xFF1F3A2C),
    pressed = Color(0x0F292B2A),
)

val SoftDark = MikayalaPalette(
    isDark = true,
    background = Color(0xFF15171A),
    surface = Color(0xFF1E2124),
    surfaceElevated = Color(0xFF262A2E),
    surfaceSunken = Color(0xFF121416),
    modal = Color(0xFF202326),
    textPrimary = Color(0xFFEAEDEB),
    textSecondary = Color(0xFFA8B0AB),
    textMuted = Color(0xFF7A807D),
    border = Color(0xFF2F343A),
    borderStrong = Color(0xFF41474E),
    accent = Color(0xFF4CAF7D),          // vert doux (texte clair lisible dessus)
    accentSoft = Color(0xFF1F3A2C),
    lavender = Color(0xFFB3A0F0),       // violet clair
    sky = Color(0xFF6FC2B2),
    sand = Color(0xFFE2C796),
    warning = Color(0xFFE3B866),
    success = Color(0xFF8EC2A5),
    danger = Color(0xFFE58A85),
    bubbleOutgoing = Color(0xFF1F4A38),  // bulle envoyée : vert profond
    bubbleIncoming = Color(0xFF26292C),
    onBubbleOutgoing = Color(0xFFEAEDEB),
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
// Accents : plus de rose, vert + violet
val CyanBlueGradientStart: Color @Composable @ReadOnlyComposable get() = p.accent
val CyanBlueGradientEnd: Color @Composable @ReadOnlyComposable get() = p.lavender
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
val TextMuted: Color @Composable @ReadOnlyComorable get() = p.textMuted
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
