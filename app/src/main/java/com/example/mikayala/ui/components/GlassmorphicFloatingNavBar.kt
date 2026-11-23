package com.example.mikayala.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
 * Barre de navigation simplifiée iOS 18 / WhatsApp M3 :
 * - Animations GPU (graphicsLayer) sans relayout ni saccades
 * - Harmonie Violet & Vert émeraude avec icônes SF Symbols simplifiées
 * - Styles personnalisables : whatsapp_pill, floating_glass, compact
 */
@Composable
fun GlassmorphicFloatingNavBar(
    items: List<GlassNavBarItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    navBarStyle: String = "whatsapp_pill"
) {
    val c = MikayalaColors
    val isFloating = navBarStyle == "floating_glass"
    val isCompact = navBarStyle == "compact"

    val density = LocalDensity.current
    val labelSize = ((if (isCompact) 10f else 11f) * minOf(density.fontScale, 1.15f) / density.fontScale).sp
    val badgeSize = (9f * minOf(density.fontScale, 1.15f) / density.fontScale).sp

    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .heightIn(min = if (isCompact) 48.dp else 56.dp)
                .padding(horizontal = 6.dp, vertical = if (isCompact) 2.dp else 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = item.index == selectedIndex
                val useEmerald = item.index == 0 || item.index == 1
                val pillColor = if (useEmerald) c.secondarySoft else c.accentSoft
                val activeTint = if (useEmerald) c.secondary else c.accent
                val tint by animateColorAsState(
                    targetValue = if (selected) activeTint else c.textMuted,
                    animationSpec = tween(160),
                    label = "nav_tint"
                )
                val pillBg by animateColorAsState(
                    targetValue = if (selected) pillColor else Color.Transparent,
                    animationSpec = tween(160),
                    label = "nav_pill"
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1.07f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "nav_icon_scale"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, color = activeTint),
                            role = Role.Tab
                        ) { onItemSelected(item.index) }
                        .testTag("nav_tab_${item.index}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .width(if (isCompact) 44.dp else 54.dp)
                                .height(if (isCompact) 26.dp else 28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(pillBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (selected) item.selectedIcon else item.icon,
                                contentDescription = item.label,
                                tint = tint,
                                modifier = Modifier
                                    .size(if (isCompact) 20.dp else 21.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    }
                            )
                        }
                        if (item.badgeCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-3).dp, y = (-2).dp)
                                    .heightIn(min = 16.dp)
                                    .widthIn(min = 16.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(c.secondary)
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (item.badgeCount > 99) "99+" else item.badgeCount.toString(),
                                    color = if (c.isDark) Color(0xFF04261B) else Color.White,
                                    fontSize = badgeSize,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    if (!isCompact) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.label,
                            fontSize = labelSize,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) c.textPrimary else c.textMuted,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    if (isFloating) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = c.surface.copy(alpha = 0.90f),
                border = BorderStroke(0.5.dp, c.borderStrong.copy(alpha = 0.55f)),
                shadowElevation = 2.dp
            ) {
                rowContent()
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(c.surface.copy(alpha = 0.96f))
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalDivider(thickness = 0.5.dp, color = c.border.copy(alpha = 0.7f))
            rowContent()
        }
    }
}
