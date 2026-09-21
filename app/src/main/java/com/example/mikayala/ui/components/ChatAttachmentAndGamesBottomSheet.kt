package com.example.mikayala.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mikayala.theme.*

data class AttachmentGridItem(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val gradientColors: List<Color>,
    val badge: String? = null,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatAttachmentAndGamesBottomSheet(
    onDismiss: () -> Unit,
    onSendGallery: () -> Unit,
    onSendCamera: () -> Unit,
    onSendVideo: (() -> Unit)? = null,
    onSendLocation: () -> Unit,
    onSendContact: () -> Unit,
    onSendDocument: () -> Unit,
    onSendAudio: () -> Unit,
    onSendPoll: () -> Unit,
    onSendEvent: () -> Unit,
    onSendAiImage: () -> Unit,
    // Interactive Chat Games
    onSendScratchCard: () -> Unit,
    onSendTruthOrDare: () -> Unit,
    onSendQuiz: () -> Unit,
    onSendTicTacToe: () -> Unit,
    onSendDilemma: () -> Unit,
    onSendLoveCoupon: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Médias & Pièces jointes, 1: Jeux de Chat interactifs

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF2121A28),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(42.dp)
                    .height(4.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.25f)
            ) {}
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Segmented Header Bar for Switching Between Attachments and Chat Games
            Surface(
                shape = CircleShape,
                color = Color(0x331E2A3A),
                border = BorderStroke(0.8.dp, BorderSubtleWhite),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Tab 0: Pièces jointes
                    Surface(
                        shape = CircleShape,
                        color = if (selectedTab == 0) CardDarkElevated else Color.Transparent,
                        border = if (selectedTab == 0) BorderStroke(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.05f)))) else null,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = 0 }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AttachFile,
                                contentDescription = null,
                                tint = if (selectedTab == 0) VibrantCyan else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Partages & Médias",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 0) Color.White else TextSecondary
                            )
                        }
                    }

                    // Tab 1: Jeux de Chat
                    Surface(
                        shape = CircleShape,
                        color = if (selectedTab == 1) CardDarkElevated else Color.Transparent,
                        border = if (selectedTab == 1) BorderStroke(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.05f)))) else null,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = 1 }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SportsEsports,
                                contentDescription = null,
                                tint = if (selectedTab == 1) AccentRose else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Jeux de Chat 🎮",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 1) Color.White else TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Crossfade(targetState = selectedTab, label = "AttachmentTabCrossfade") { tab ->
                if (tab == 0) {
                    // Standard WhatsApp-style Media & Shares (with App's Dark Luxury aesthetic)
                    val mediaItems = listOf(
                        AttachmentGridItem(
                            id = "gallery",
                            title = "Galerie",
                            icon = Icons.Rounded.PhotoLibrary,
                            gradientColors = listOf(Color(0xFF007AFF), Color(0xFF00C6FF)),
                            onClick = onSendGallery
                        ),
                        AttachmentGridItem(
                            id = "camera",
                            title = "Caméra",
                            icon = Icons.Rounded.PhotoCamera,
                            gradientColors = listOf(Color(0xFFFF2D55), Color(0xFFFF5252)),
                            onClick = onSendCamera
                        ),
                        AttachmentGridItem(
                            id = "video",
                            title = "Vidéo",
                            icon = Icons.Rounded.Videocam,
                            gradientColors = listOf(Color(0xFF9C27B0), Color(0xFFE040FB)),
                            onClick = { onSendVideo?.invoke() ?: onSendGallery() }
                        ),
                        AttachmentGridItem(
                            id = "location",
                            title = "Localisation",
                            icon = Icons.Rounded.LocationOn,
                            gradientColors = listOf(Color(0xFF00C853), Color(0xFF64DD17)),
                            onClick = onSendLocation
                        ),
                        AttachmentGridItem(
                            id = "contact",
                            title = "Contact",
                            icon = Icons.Rounded.Person,
                            gradientColors = listOf(Color(0xFF00BCD4), Color(0xFF0288D1)),
                            onClick = onSendContact
                        ),
                        AttachmentGridItem(
                            id = "document",
                            title = "Document",
                            icon = Icons.Rounded.InsertDriveFile,
                            gradientColors = listOf(Color(0xFF7E57C2), Color(0xFF5E35B1)),
                            onClick = onSendDocument
                        ),
                        AttachmentGridItem(
                            id = "audio",
                            title = "Audio",
                            icon = Icons.Rounded.Headphones,
                            gradientColors = listOf(Color(0xFFFF9800), Color(0xFFF57C00)),
                            onClick = onSendAudio
                        ),
                        AttachmentGridItem(
                            id = "poll",
                            title = "Sondage",
                            icon = Icons.Rounded.Poll,
                            gradientColors = listOf(Color(0xFFFFD600), Color(0xFFFFAB00)),
                            onClick = onSendPoll
                        ),
                        AttachmentGridItem(
                            id = "event",
                            title = "Événement",
                            icon = Icons.Rounded.Event,
                            gradientColors = listOf(Color(0xFFE91E63), Color(0xFFC2185B)),
                            onClick = onSendEvent
                        ),
                        AttachmentGridItem(
                            id = "ai_image",
                            title = "Images d'IA",
                            icon = Icons.Rounded.AutoAwesome,
                            gradientColors = listOf(Color(0xFF00E5FF), Color(0xFF7C4DFF)),
                            badge = "IA",
                            onClick = onSendAiImage
                        )
                    )

                    AttachmentGrid(items = mediaItems)
                } else {
                    // Chat Games (Games that require the chat to play!)
                    val gameItems = listOf(
                        AttachmentGridItem(
                            id = "scratch_card",
                            title = "Carte à Gratter",
                            icon = Icons.Rounded.CardGiftcard,
                            gradientColors = listOf(Color(0xFFFFD700), Color(0xFFFF9100)),
                            badge = "Surprise",
                            onClick = onSendScratchCard
                        ),
                        AttachmentGridItem(
                            id = "truth_dare",
                            title = "Action / Vérité",
                            icon = Icons.Rounded.LocalFireDepartment,
                            gradientColors = listOf(Color(0xFFFF1744), Color(0xFFFF5252)),
                            badge = "Piquant 🔥",
                            onClick = onSendTruthOrDare
                        ),
                        AttachmentGridItem(
                            id = "quiz",
                            title = "Quiz Couple",
                            icon = Icons.Rounded.Psychology,
                            gradientColors = listOf(Color(0xFF9C27B0), Color(0xFF673AB7)),
                            badge = "Complicité",
                            onClick = onSendQuiz
                        ),
                        AttachmentGridItem(
                            id = "tictactoe",
                            title = "Morpion Direct",
                            icon = Icons.Rounded.Grid3x3,
                            gradientColors = listOf(Color(0xFF00E5FF), Color(0xFF00B0FF)),
                            badge = "Tour par Tour",
                            onClick = onSendTicTacToe
                        ),
                        AttachmentGridItem(
                            id = "dilemma",
                            title = "Tu Préfères ?",
                            icon = Icons.Rounded.Balance,
                            gradientColors = listOf(Color(0xFF4CAF50), Color(0xFF009688)),
                            onClick = onSendDilemma
                        ),
                        AttachmentGridItem(
                            id = "coupon",
                            title = "Bon d'Amour",
                            icon = Icons.Rounded.ConfirmationNumber,
                            gradientColors = listOf(Color(0xFFFF4081), Color(0xFFF50057)),
                            badge = "Cadeau 💖",
                            onClick = onSendLoveCoupon
                        )
                    )

                    Column {
                        Text(
                            text = "Jeux interactifs jouables directement dans le fil de discussion",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                        )
                        AttachmentGrid(items = gameItems)
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentGrid(items: List<AttachmentGridItem>) {
    // 4 Columns Grid matching reference layout
    val rows = items.chunked(4)

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                rowItems.forEach { item ->
                    AttachmentSquircleButton(item = item)
                }
                // Fill empty slots in the row for proper alignment
                if (rowItems.size < 4) {
                    repeat(4 - rowItems.size) {
                        Spacer(modifier = Modifier.width(68.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentSquircleButton(item: AttachmentGridItem) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable { item.onClick() }
            .testTag("attachment_${item.id}")
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Squircle Button Container with Subtle Gradient and Inner Light Glow
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CardDarkElevated,
                border = BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            item.gradientColors.first().copy(alpha = 0.4f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    )
                ),
                shadowElevation = 8.dp,
                modifier = Modifier.size(56.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    item.gradientColors.first().copy(alpha = 0.22f),
                                    Color(0x22141C2B)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = item.gradientColors.first(),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Optional Small Badge
            if (item.badge != null) {
                Surface(
                    shape = CircleShape,
                    color = AccentRose,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                ) {
                    Text(
                        text = item.badge,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 13.sp
        )
    }
}
