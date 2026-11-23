package com.example.mikayala.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ==============================================================================
// SYSTÈME DE DESIGN « VIOLET & ÉMERAUDE » — 5 harmonies personnalisables,
// modes clair ET sombre, inspiré de l'ergonomie WhatsApp Material 3.
// Les alias historiques (DeepNight, CardDark, VibrantCyan, AccentRose...) restent
// synchronisés automatiquement pour toute l'application.
// ==============================================================================

@Immutable
data class MikayalaPalette(
    val isDark: Boolean,
    // Fonds et surfaces
    val background: Color,        // fond principal
    val surface: Color,           // surfaces (listes, cartes, topbar)
    val surfaceElevated: Color,   // surfaces mises en avant, éléments sélectionnés
    val surfaceSunken: Color,     // zones en creux (champs de saisie, barres)
    val modal: Color,             // feuilles et boîtes de dialogue
    // Textes
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    // Lignes
    val border: Color,
    val borderStrong: Color,
    // Accents principaux : Violet + Vert Émeraude (ou harmonie choisie)
    val accent: Color,            // couleur primaire (ex: Violet royal)
    val accentSoft: Color,        // fond léger derrière un accent
    val secondary: Color,         // couleur secondaire (ex: Vert émeraude)
    val secondarySoft: Color,     // fond léger secondaire
    val lavender: Color,
    val sky: Color,
    val sand: Color,
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

val VioletEmeraldLight = MikayalaPalette(
    isDark = false,
    background = Color(0xFFF6F4FB),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFEDE8F8),
    surfaceSunken = Color(0xFFE7E2F3),
    modal = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF1A1625),
    textSecondary = Color(0xFF5D576B),
    textMuted = Color(0xFF8C859C),
    border = Color(0xFFE1DAE9),
    borderStrong = Color(0xFFCBBFE0),
    accent = Color(0xFF7C3AED),
    accentSoft = Color(0xFFEDE9FE),
    secondary = Color(0xFF10B981),
    secondarySoft = Color(0xFFD1FAE5),
    lavender = Color(0xFF8B5CF6),
    sky = Color(0xFF0EA5E9),
    sand = Color(0xFFD97706),
    warning = Color(0xFFD97706),
    success = Color(0xFF059669),
    danger = Color(0xFFDC2626),
    bubbleOutgoing = Color(0xFFEDE9FE),
    bubbleIncoming = Color(0xFFFFFFFF),
    onBubbleOutgoing = Color(0xFF1E1438),
    pressed = Color(0x127C3AED),
)

val VioletEmeraldDark = MikayalaPalette(
    isDark = true,
    background = Color(0xFF0F0B1A),
    surface = Color(0xFF171226),
    surfaceElevated = Color(0xFF211A36),
    surfaceSunken = Color(0xFF120E1E),
    modal = Color(0xFF1C162E),
    textPrimary = Color(0xFFF5F3FA),
    textSecondary = Color(0xFFB8B0CE),
    textMuted = Color(0xFF81789B),
    border = Color(0xFF2E2646),
    borderStrong = Color(0xFF423763),
    accent = Color(0xFF9D7BFF),
    accentSoft = Color(0xFF2D2152),
    secondary = Color(0xFF34D399),
    secondarySoft = Color(0xFF12382B),
    lavender = Color(0xFFA78BFA),
    sky = Color(0xFF38BDF8),
    sand = Color(0xFFFBBF24),
    warning = Color(0xFFF59E0B),
    success = Color(0xFF10B981),
    danger = Color(0xFFEF4444),
    bubbleOutgoing = Color(0xFF43297D),
    bubbleIncoming = Color(0xFF1C2331),
    onBubbleOutgoing = Color(0xFFF7F5FD),
    pressed = Color(0x18FFFFFF),
)

// Variante inversée : Émeraude primaire + Violet secondaire
val EmeraldVioletLight = VioletEmeraldLight.copy(
    background = Color(0xFFF3FAF7),
    surfaceElevated = Color(0xFFE4F4EE),
    surfaceSunken = Color(0xFFDCF0E8),
    border = Color(0xFFD0E7DE),
    borderStrong = Color(0xFFA9D6C5),
    accent = Color(0xFF059669),
    accentSoft = Color(0xFFD1FAE5),
    secondary = Color(0xFF7C3AED),
    secondarySoft = Color(0xFFEDE9FE),
    bubbleOutgoing = Color(0xFFD1FAE5),
    onBubbleOutgoing = Color(0xFF062E22)
)

val EmeraldVioletDark = VioletEmeraldDark.copy(
    background = Color(0xFF091512),
    surface = Color(0xFF10201B),
    surfaceElevated = Color(0xFF172D26),
    surfaceSunken = Color(0xFF0B1814),
    modal = Color(0xFF122620),
    border = Color(0xFF214036),
    borderStrong = Color(0xFF315D4F),
    accent = Color(0xFF34D399),
    accentSoft = Color(0xFF133D2E),
    secondary = Color(0xFFA78BFA),
    secondarySoft = Color(0xFF2C204D),
    bubbleOutgoing = Color(0xFF115E46),
    bubbleIncoming = Color(0xFF18222B),
    onBubbleOutgoing = Color(0xFFECFDF5)
)

val AmethystMintLight = VioletEmeraldLight.copy(
    accent = Color(0xFF9333EA),
    accentSoft = Color(0xFFF3E8FF),
    secondary = Color(0xFF14B8A6),
    secondarySoft = Color(0xFFCCFBF1),
    success = Color(0xFF0D9488),
    bubbleOutgoing = Color(0xFFF3E8FF),
    onBubbleOutgoing = Color(0xFF2E1065)
)

val AmethystMintDark = VioletEmeraldDark.copy(
    accent = Color(0xFFC084FC),
    accentSoft = Color(0xFF3B1D5C),
    secondary = Color(0xFF2DD4BF),
    secondarySoft = Color(0xFF133B37),
    success = Color(0xFF2DD4BF),
    bubbleOutgoing = Color(0xFF581C87),
    onBubbleOutgoing = Color(0xFFFAF5FF)
)

val ForestPlumLight = VioletEmeraldLight.copy(
    background = Color(0xFFF4F8F5),
    accent = Color(0xFF15803D),
    accentSoft = Color(0xFFDCFCE7),
    secondary = Color(0xFF7E22CE),
    secondarySoft = Color(0xFFF3E8FF),
    bubbleOutgoing = Color(0xFFDCFCE7),
    onBubbleOutgoing = Color(0xFF052E16)
)

val ForestPlumDark = VioletEmeraldDark.copy(
    background = Color(0xFF0B130F),
    surface = Color(0xFF131F19),
    surfaceElevated = Color(0xFF1B2C24),
    border = Color(0xFF284234),
    accent = Color(0xFF4ADE80),
    accentSoft = Color(0xFF163822),
    secondary = Color(0xFFC084FC),
    secondarySoft = Color(0xFF341E4E),
    bubbleOutgoing = Color(0xFF166534),
    onBubbleOutgoing = Color(0xFFF0FDF4)
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
    secondary = Color(0xFF6FA287),
    secondarySoft = Color(0xFFE3EFE8),
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
    accent = Color(0xFFC77683),
    accentSoft = Color(0xFF3A2C30),
    secondary = Color(0xFF8EC2A5),
    secondarySoft = Color(0xFF22362B),
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

fun paletteFor(darkTheme: Boolean, accentTheme: String = "violet_emerald"): MikayalaPalette =
    when (accentTheme) {
        "emerald_violet" -> if (darkTheme) EmeraldVioletDark else EmeraldVioletLight
        "amethyst_mint" -> if (darkTheme) AmethystMintDark else AmethystMintLight
        "forest_plum" -> if (darkTheme) ForestPlumDark else ForestPlumLight
        "rose_soft" -> if (darkTheme) SoftDark else SoftLight
        else -> if (darkTheme) VioletEmeraldDark else VioletEmeraldLight
    }

val LocalMikayalaPalette = staticCompositionLocalOf { VioletEmeraldDark }

/** Accès direct : MikayalaColors.accent, MikayalaColors.secondary, MikayalaColors.surface… */
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
// Accents : Violet & Vert Émeraude
val CyanBlueGradientStart: Color @Composable @ReadOnlyComposable get() = p.accent
val CyanBlueGradientEnd: Color @Composable @ReadOnlyComposable get() = p.secondary
val VibrantBlue: Color @Composable @ReadOnlyComposable get() = p.sky
val VibrantCyan: Color @Composable @ReadOnlyComposable get() = p.secondary
val AccentRose: Color @Composable @ReadOnlyComposable get() = p.accent
val AccentViolet: Color @Composable @ReadOnlyComposable get() = p.lavender
val AccentEmerald: Color @Composable @ReadOnlyComposable get() = p.secondary
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
val StatusBlue: Color @Composable @ReadOnlyComposable get() = p.secondary
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
val HoverStateCyan: Color @Composable @ReadOnlyComposable get() = p.secondarySoft
val HoverStateCoral: Color @Composable @ReadOnlyComposable get() = p.accentSoft
