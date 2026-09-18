package com.example.mikayala.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mikayala.theme.*

data class GlassNavBarItem(
    val index: Int,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val badgeCount: Int = 0
)

@Composable
fun GlassmorphicFloatingNavBar(
    items: List<GlassNavBarItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Floating glass capsule bar mirroring the frosted glass aesthetic
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0x3D141C2B), // Deep translucent frosted glass background
            border = BorderStroke(
                width = 1.2.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.38f),
                        Color.White.copy(alpha = 0.08f),
                        Color.White.copy(alpha = 0.18f)
                    )
                )
            ),
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x3500E5FF),
                            Color(0x100A101D),
                            Color(0x30000000)
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val isSelected = selectedIndex == item.index
                    val interactionSource = remember { MutableInteractionSource() }

                    val scaleAnim by animateFloatAsState(
                        targetValue = if (isSelected) 1.06f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "tab_scale"
                    )

                    val iconTint by animateColorAsState(
                        targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.55f),
                        animationSpec = tween(220),
                        label = "tab_tint"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .scale(scaleAnim)
                            .clip(RoundedCornerShape(26.dp))
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                onItemSelected(item.index)
                            }
                            .testTag("nav_tab_${item.index}"),
                        contentAlignment = Alignment.Center
                    ) {
                        // Active Pill Indicator background matching reference design
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 2.dp, vertical = 2.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color(0x522B3A4F),
                                                Color(0x40182433)
                                            )
                                        )
                                    )
                                    .border(
                                        width = 1.dp,
                                        brush = Brush.verticalGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.28f),
                                                Color.White.copy(alpha = 0.05f)
                                            )
                                        ),
                                        shape = RoundedCornerShape(24.dp)
                                    )
                            )
                        }

                        // Icon with badge
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon else item.icon,
                                contentDescription = item.label,
                                tint = iconTint,
                                modifier = Modifier.size(24.dp)
                            )

                            // Optional notification badge
                            if (item.badgeCount > 0) {
                                Surface(
                                    shape = CircleShape,
                                    color = AccentRose,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 8.dp, y = (-6).dp)
                                        .size(16.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = if (item.badgeCount > 9) "9+" else item.badgeCount.toString(),
                                            fontSize = 9.sp,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
