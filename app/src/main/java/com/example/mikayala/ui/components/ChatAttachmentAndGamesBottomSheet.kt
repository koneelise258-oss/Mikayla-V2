package com.example.mikayala.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mikayala.theme.MikayalaColors
import com.example.mikayala.ui.icons.AppleSvgIcons

data class AttachmentGridItem(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val tint: Color,
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
    onSendAiImage: (() -> Unit)? = null,
    onSendScratchCard: () -> Unit,
    onSendTruthOrDare: () -> Unit,
    onSendQuiz: () -> Unit,
    onSendTicTacToe: () -> Unit,
    onSendDilemma: () -> Unit,
    onSendLoveCoupon: () -> Unit,
    onSendWheel: () -> Unit
) {
    val c = MikayalaColors
    var selectedTab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.modal,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(c.borderStrong)
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp)
        ) {
            // Contrôle segmenté épuré style iOS 18
            Surface(
                shape = CircleShape,
                color = c.surfaceSunken,
                border = BorderStroke(0.5.dp, c.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        Triple(0, AppleSvgIcons.Paperclip, "Partages"),
                        Triple(1, AppleSvgIcons.Gamepad, "Jeux à deux")
                    ).forEach { (idx, icon, label) ->
                        val active = selectedTab == idx
                        val activeBg = if (idx == 0) c.secondarySoft else c.accentSoft
                        val activeFg = if (idx == 0) c.secondary else c.accent
                        Surface(
                            shape = CircleShape,
                            color = if (active) activeBg else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp)
                                .clip(CircleShape)
                                .clickable { selectedTab = idx }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (active) activeFg else c.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (active) c.textPrimary else c.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(160),
                label = "AttachmentTabCrossfade"
            ) { tab ->
                if (tab == 0) {
                    val mediaItems = listOf(
                        AttachmentGridItem("gallery", "Photos", Icons.Rounded.PhotoLibrary, c.accent, onSendGallery),
                        AttachmentGridItem("camera", "Caméra", AppleSvgIcons.Camera, c.secondary, onSendCamera),
                        AttachmentGridItem("video", "Vidéo", AppleSvgIcons.Video, c.lavender) { onSendVideo?.invoke() ?: onSendGallery() },
                        AttachmentGridItem("location", "Position", AppleSvgIcons.Location, c.secondary, onSendLocation),
                        AttachmentGridItem("contact", "Contact", AppleSvgIcons.Contact, c.sky, onSendContact),
                        AttachmentGridItem("document", "Document", AppleSvgIcons.Document, c.accent, onSendDocument),
                        AttachmentGridItem("audio", "Audio", Icons.Rounded.Headphones, c.sand, onSendAudio),
                        AttachmentGridItem("poll", "Sondage", Icons.Rounded.Poll, c.secondary, onSendPoll),
                        AttachmentGridItem("event", "Moment", AppleSvgIcons.Calendar, c.accent, onSendEvent)
                    ) + listOfNotNull(
                        onSendAiImage?.let { action ->
                            AttachmentGridItem("ai_image", "Studio", AppleSvgIcons.Sparkles, c.accent, action)
                        }
                    )
                    AttachmentGrid(items = mediaItems)
                } else {
                    val gameItems = listOf(
                        AttachmentGridItem("scratch_card", "Carte mystère", Icons.Rounded.CardGiftcard, c.sand, onSendScratchCard),
                        AttachmentGridItem("truth_dare", "Action / Vérité", Icons.Rounded.LocalFireDepartment, c.danger, onSendTruthOrDare),
                        AttachmentGridItem("quiz", "Quiz Couple", Icons.Rounded.Psychology, c.accent, onSendQuiz),
                        AttachmentGridItem("tictactoe", "Morpion", Icons.Rounded.Grid3x3, c.secondary, onSendTicTacToe),
                        AttachmentGridItem("dilemma", "Tu préfères ?", Icons.Rounded.Balance, c.secondary, onSendDilemma),
                        AttachmentGridItem("coupon", "Bon d'amour", Icons.Rounded.ConfirmationNumber, c.accent, onSendLoveCoupon),
                        AttachmentGridItem("wheel", "Roue", Icons.Rounded.Casino, c.lavender, onSendWheel)
                    )
                    Column {
                        Text(
                            text = "Jouez directement dans le fil de discussion",
                            fontSize = 12.sp,
                            color = c.textSecondary,
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
    val rows = items.chunked(4)
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
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
                if (rowItems.size < 4) {
                    repeat(4 - rowItems.size) {
                        Spacer(modifier = Modifier.width(72.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentSquircleButton(item: AttachmentGridItem) {
    val c = MikayalaColors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { item.onClick() }
            .padding(vertical = 4.dp)
            .testTag("attachment_${item.id}")
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = item.tint.copy(alpha = 0.14f),
            border = BorderStroke(0.5.dp, item.tint.copy(alpha = 0.28f)),
            modifier = Modifier.size(54.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = item.tint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = c.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 13.sp
        )
    }
}
