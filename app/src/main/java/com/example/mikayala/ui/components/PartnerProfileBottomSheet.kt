package com.example.mikayala.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mikayala.data.model.MessageEntity
import com.example.mikayala.data.repository.MikayalaRepository
import com.example.mikayala.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerProfileBottomSheet(
    repository: MikayalaRepository,
    onDismiss: () -> Unit,
    onStartCall: (isVideo: Boolean) -> Unit,
    onOpenSearch: () -> Unit
) {
    val context = LocalContext.current
    val userSettings by repository.userSettings.collectAsState()
    val coupleSpace by repository.activeCoupleSpace.collectAsState()
    val rawMessages by repository.allMessages.collectAsState()
    val myUserId = remember { repository.getCurrentUserId() }
    val messages = remember(rawMessages, myUserId) {
        rawMessages.filter { !it.deletedFor.contains(myUserId) }
    }

    val c = MikayalaColors
    val isPartnerOnline by repository.isPartnerOnline.collectAsState()
    var isMuted by remember { mutableStateOf(false) }
    var selectedMediaTab by remember { mutableIntStateOf(0) } // 0: Médias, 1: Liens, 2: Favoris

    val isOnline = isPartnerOnline && userSettings.connectionMode == com.example.mikayala.data.model.ConnectionMode.ONLINE
    val partnerName = userSettings.partnerNickname.ifBlank { "Votre partenaire" }

    val mediaMessages = remember(messages) {
        messages.filter { it.type in setOf("image", "photo", "video", "audio", "voice") }
    }
    val linkMessages = remember(messages) {
        messages.filter { com.example.mikayala.util.ChatTextHelpers.containsLink(it.content) }
    }
    val starredMessages = remember(messages) {
        messages.filter { it.isStarred }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.modal,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(c.borderStrong)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Profile Card (iOS 18 Contact Info)
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = c.surfaceElevated,
                border = BorderStroke(0.5.dp, c.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .background(c.accentSoft)
                                .border(1.5.dp, c.borderStrong, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (userSettings.partnerAvatarUrl.isNotBlank()) {
                                coil.compose.AsyncImage(
                                    model = userSettings.partnerAvatarUrl,
                                    contentDescription = "Photo de votre partenaire",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = com.example.mikayala.ui.icons.AppleSvgIcons.Person,
                                    contentDescription = "Photo de votre partenaire",
                                    tint = c.accent,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(if (isOnline) c.secondary else c.textMuted)
                                .border(2.5.dp, c.surfaceElevated, CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = partnerName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.textPrimary
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isOnline) c.secondary else c.textMuted)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                isOnline -> "En ligne"
                                userSettings.partnerStatus.isNotBlank() && userSettings.partnerStatus != "Hors ligne" -> userSettings.partnerStatus
                                else -> "Hors ligne"
                            },
                            fontSize = 13.sp,
                            fontWeight = if (isOnline) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isOnline) c.secondary else c.textSecondary
                        )
                    }

                    if (userSettings.partnerBio.isNotBlank() && userSettings.partnerBio != userSettings.partnerStatus) {
                        Text(
                            text = "« ${userSettings.partnerBio} »",
                            fontSize = 13.sp,
                            color = c.accent,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ContactActionButton(
                            icon = com.example.mikayala.ui.icons.AppleSvgIcons.Phone,
                            label = "Audio",
                            onClick = { onDismiss(); onStartCall(false) }
                        )
                        ContactActionButton(
                            icon = com.example.mikayala.ui.icons.AppleSvgIcons.Video,
                            label = "Vidéo",
                            onClick = { onDismiss(); onStartCall(true) }
                        )
                        ContactActionButton(
                            icon = com.example.mikayala.ui.icons.AppleSvgIcons.Search,
                            label = "Chercher",
                            onClick = { onDismiss(); onOpenSearch() }
                        )
                        ContactActionButton(
                            icon = if (isMuted) Icons.Rounded.NotificationsOff else com.example.mikayala.ui.icons.AppleSvgIcons.Bell,
                            label = if (isMuted) "Muet" else "Sons",
                            onClick = {
                                isMuted = !isMuted
                                Toast.makeText(
                                    context,
                                    if (isMuted) "Notifications en sourdine" else "Notifications activées",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Security Banner
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = c.surfaceElevated,
                border = BorderStroke(0.5.dp, c.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = com.example.mikayala.ui.icons.AppleSvgIcons.Lock,
                        contentDescription = "Espace privé",
                        tint = c.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Espace privé du couple",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = c.textPrimary
                        )
                        Text(
                            text = "Vos échanges transitent en HTTPS et sont réservés à votre binôme via les règles du serveur.",
                            fontSize = 11.sp,
                            color = c.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Shared Media, Links & Starred Section
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = c.surfaceElevated,
                border = BorderStroke(0.5.dp, c.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Médias, liens & favoris",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = c.textPrimary
                        )
                        Text(
                            text = "${messages.size} msgs",
                            fontSize = 12.sp,
                            color = c.textSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            0 to "Médias (${mediaMessages.size})",
                            1 to "Liens (${linkMessages.size})",
                            2 to "Favoris (${starredMessages.size})"
                        ).forEach { (idx, label) ->
                            val selected = selectedMediaTab == idx
                            FilterChip(
                                selected = selected,
                                onClick = { selectedMediaTab = idx },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = c.secondarySoft,
                                    selectedLabelColor = c.secondary,
                                    containerColor = c.surface,
                                    labelColor = c.textSecondary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    when (selectedMediaTab) {
                        0 -> {
                            if (mediaMessages.isEmpty()) {
                                Text("Aucun média partagé pour le moment", fontSize = 12.sp, color = c.textMuted, modifier = Modifier.padding(vertical = 8.dp))
                            } else {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(mediaMessages) { msg ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = c.surface,
                                            border = BorderStroke(0.5.dp, c.border),
                                            modifier = Modifier.size(64.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = when (msg.type) {
                                                        "video" -> com.example.mikayala.ui.icons.AppleSvgIcons.Video
                                                        "audio", "voice" -> com.example.mikayala.ui.icons.AppleSvgIcons.Mic
                                                        else -> Icons.Rounded.Image
                                                    },
                                                    contentDescription = "Média",
                                                    tint = c.secondary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            if (linkMessages.isEmpty()) {
                                Text("Aucun lien partagé pour le moment", fontSize = 12.sp, color = c.textMuted, modifier = Modifier.padding(vertical = 8.dp))
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    linkMessages.take(3).forEach { msg ->
                                        Text(text = "• ${msg.content}", fontSize = 12.sp, color = c.secondary, maxLines = 1)
                                    }
                                }
                            }
                        }
                        2 -> {
                            if (starredMessages.isEmpty()) {
                                Text("Aucun message favori", fontSize = 12.sp, color = c.textMuted, modifier = Modifier.padding(vertical = 8.dp))
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    starredMessages.take(3).forEach { msg ->
                                        Text(text = "★ ${msg.content}", fontSize = 12.sp, color = c.textPrimary, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pairing Code Section
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = c.surfaceElevated,
                border = BorderStroke(0.5.dp, c.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Code de couple", coupleSpace.pairingCode)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Code copié dans le presse-papier", Toast.LENGTH_SHORT).show()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Code de jumelage partagé", fontSize = 12.sp, color = c.textSecondary)
                        Text(
                            text = coupleSpace.pairingCode.ifEmpty { "NON JUMELÉ" },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.accent
                        )
                    }
                    Icon(
                        imageVector = com.example.mikayala.ui.icons.AppleSvgIcons.Copy,
                        contentDescription = "Copier",
                        tint = c.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ContactActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val c = MikayalaColors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = c.accentSoft,
            border = BorderStroke(0.5.dp, c.border),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = label, tint = c.accent, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = label, fontSize = 11.sp, color = c.textSecondary, fontWeight = FontWeight.Medium)
    }
}
