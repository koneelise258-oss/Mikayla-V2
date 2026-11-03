package com.example.mikayala.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.mikayala.R

/** Poppins (licence SIL OFL, docs/logo/POPPINS-LICENCE-OFL.txt), embarquée : aucun téléchargement. */
val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    // Pas de fichier Bold/Black embarqué : SemiBold sert pour ces graisses (APK plus léger)
    Font(R.font.poppins_semibold, FontWeight.Bold),
    Font(R.font.poppins_semibold, FontWeight.ExtraBold),
    Font(R.font.poppins_semibold, FontWeight.Black)
)

private fun TextStyle.poppins() = copy(fontFamily = Poppins)

/** Typographie de toute l'application : styles Material par défaut, en Poppins. */
val Typography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.poppins(),
        displayMedium = t.displayMedium.poppins(),
        displaySmall = t.displaySmall.poppins(),
        headlineLarge = t.headlineLarge.poppins(),
        headlineMedium = t.headlineMedium.poppins(),
        headlineSmall = t.headlineSmall.poppins(),
        titleLarge = t.titleLarge.poppins(),
        titleMedium = t.titleMedium.poppins(),
        titleSmall = t.titleSmall.poppins(),
        bodyLarge = t.bodyLarge.poppins(),
        bodyMedium = t.bodyMedium.poppins(),
        bodySmall = t.bodySmall.poppins(),
        labelLarge = t.labelLarge.poppins(),
        labelMedium = t.labelMedium.poppins(),
        labelSmall = t.labelSmall.poppins()
    )
}
