package com.example.mikayala.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Espacements du système Soft (grille de 4 dp). */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    /** Marge horizontale des écrans (téléphone). */
    val screen = 16.dp
    /** Largeur de contenu maximale : lisible sur tablette. */
    val maxContentWidth = 640.dp
}

/** Rayons : doux mais pas « bulle » partout. */
object Radius {
    val sm = RoundedCornerShape(8.dp)
    val md = RoundedCornerShape(12.dp)
    val lg = RoundedCornerShape(18.dp)
    val xl = RoundedCornerShape(24.dp)
    val pill = RoundedCornerShape(50)
}

/** Durées d'animation : courtes et discrètes. */
object Motion {
    const val fast = 150
    const val normal = 220
}
