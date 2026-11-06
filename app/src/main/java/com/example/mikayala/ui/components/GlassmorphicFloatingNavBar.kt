package com.example.mikayala.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mikayala.theme.MikayalaColors

data class GlassNavBarItem(
    val index: Int,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val badgeCount: Int = 0
)

/**
 * Barre de navigation du bas, style Soft : surface unie, fine ligne de séparation,
 * onglet actif signalé par une pastille rose poudré. Aucun flou ni ombre : léger à afficher.
 * (Nom conservé pour ne pas casser les écrans qui l'utilisent.)
 */
@Composable
fun GlassmorphicFloatingNavBar(
    items: List<GlassNavBarItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MikayalaColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.surface)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(thickness = 0.5.dp, color = c.border)
        // Taille de texte de l'app (jusqu'à « très grande ») : plafonnée ici pour que les 5 libellés
        // tiennent toujours sur une ligne sans être coupés.
        val density = LocalDensity.current
        val labelSize = (11f * minOf(density.fontScale, 1.15f) / density.fontScale).sp
        val badgeSize = (9f * minOf(density.fontScale, 1.15f) / density.fontScale).sp
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp) // tablette : barre centrée, onglets pas étirés sur toute la largeur
                .align(Alignment.CenterHorizontally)
                .heightIn(min = 58.dp)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = item.index == selectedIndex
                val tint by animateColorAsState(
                    targetValue = if (selected) c.accent else c.textMuted,
                    animationSpec = tween(180),
                    label = "nav_tint"
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp) // zone tactile ≥ 48 dp
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, radius = 32.dp),
                            role = Role.Tab
                        ) { onItemSelected(item.index) }
                        .testTag("nav_tab_${item.index}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .width(52.dp)
                                .height(28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) c.accentSoft else c.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (selected) item.selectedIcon else item.icon,
                                contentDescription = null, // le libellé visible suffit (lecteur d'écran)
                                tint = tint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        if (item.badgeCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-6).dp, y = (-2).dp)
                                    .heightIn(min = 16.dp)
                                    .widthIn(min = 16.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(c.accent)
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (item.badgeCount > 99) "99+" else item.badgeCount.toString(),
                                    color = c.background,
                                    fontSize = badgeSize,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = item.label,
                        fontSize = labelSize,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = tint,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
