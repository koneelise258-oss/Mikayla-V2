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

    var isMuted by remember { mutableStateOf(false) }
    var selectedMediaTab by remember { mutableIntStateOf(0) } // 0: Photos/Médias, 1: Liens, 2: Favoris ⭐

    val isOnline = userSettings.connectionMode == com.example.mikayala.data.model.ConnectionMode.ONLINE

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_profile_dot")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    val mediaMessages = remember(messages) {
        messages.filter { it.type == "image" || it.type == "video" || it.type == "audio" }
    }
    val linkMessages = remember(messages) {
        messages.filter { it.content.contains("http://") || it.content.contains("https://") || it.content.contains("www.") }
    }
    val starredMessages = remember(messages) {
        messages.filter { it.isStarred }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MatteSlateBackground,
        scrimColor = Color.Black.copy(alpha = 0.7f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Profile Card (Style WhatsApp Contact Info)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MatteCardDark,
                border = BorderStroke(1.dp, BorderSubtleWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Avatar with Presence Glow
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(Brush.radialGradient(listOf(VibrantCyan.copy(alpha = 0.3f), Color.Transparent)))
                                .border(2.dp, VibrantCyan, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = "Avatar Partenaire",
                                tint = VibrantCyan,
                                modifier = Modifier.size(50.dp)
                            )
                        }

                        // Online Presence Dot / Badge
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (isOnline) OnlinePresenceGreen.copy(alpha = pulseAlpha) else Color.Gray)
                                .border(2.dp, MatteCardDark, CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Partner Name
                    Text(
                        text = userSettings.partnerNickname,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    // Partner Status & Last Seen
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        if (isOnline) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(OnlinePresenceGreen.copy(alpha = pulseAlpha))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "En ligne",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OnlinePresenceGreen
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (userSettings.partnerStatus.isNotBlank() && userSettings.partnerStatus != "Hors ligne") userSettings.partnerStatus else "Hors ligne",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    // Custom Bio / Quote
                    if (userSettings.partnerBio.isNotBlank() && userSettings.partnerBio != userSettings.partnerStatus) {
                        Text(
                            text = "« " + userSettings.partnerBio + " »",
                            fontSize = 13.sp,
                            color = AccentRose,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Quick Actions Row (Appels, Recherche, Silencieux)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ContactActionButton(
                            icon = Icons.Rounded.Call,
                            label = "Audio",
                            onClick = {
                                onDismiss()
                                onStartCall(false)
                            }
                        )
                        ContactActionButton(
                            icon = Icons.Rounded.Videocam,
                            label = "Vidéo",
                            onClick = {
                                onDismiss()
                                onStartCall(true)
                            }
                        )
                        ContactActionButton(
                            icon = Icons.Rounded.Search,
                            label = "Chercher",
                            onClick = {
                                onDismiss()
                                onOpenSearch()
                            }
                        )
                        ContactActionButton(
                            icon = if (isMuted) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications,
                            label = if (isMuted) "Muet" else "Sons",
                            onClick = {
                                isMuted = !isMuted
                                val msg = if (isMuted) "Notifications en sourdine 🔕" else "Notifications activées 🔔"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Encryption & Security Banner
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardDarkElevated,
                border = BorderStroke(1.dp, BorderSubtleWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Chiffrement",
                        tint = VibrantCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Chiffrement de bout en bout",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Les messages et appels sont chiffrés. Personne d'autre ne peut les lire.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Shared Media & Links Section Tabs
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MatteCardDark,
                border = BorderStroke(1.dp, BorderSubtleWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Médias & Liens Partagés",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${messages.size} msgs",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tab selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedMediaTab == 0,
                            onClick = { selectedMediaTab = 0 },
                            label = { Text("Photos/Médias (${mediaMessages.size})", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0x3800E5FF),
                                selectedLabelColor = Color.White,
                                containerColor = CardDarkElevated,
                                labelColor = TextSecondary
                            )
                        )
                        FilterChip(
                            selected = selectedMediaTab == 1,
                            onClick = { selectedMediaTab = 1 },
                            label = { Text("Liens (${linkMessages.size})", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0x3800E5FF),
                                selectedLabelColor = Color.White,
                                containerColor = CardDarkElevated,
                                labelColor = TextSecondary
                            )
                        )
                        FilterChip(
                            selected = selectedMediaTab == 2,
                            onClick = { selectedMediaTab = 2 },
                            label = { Text("Favoris ⭐ (${starredMessages.size})", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0x3800E5FF),
                                selectedLabelColor = Color.White,
                                containerColor = CardDarkElevated,
                                labelColor = TextSecondary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    when (selectedMediaTab) {
                        0 -> {
                            if (mediaMessages.isEmpty()) {
                                Text("Aucun média partagé pour le moment 📸", fontSize = 12.sp, color = TextMuted, modifier = Modifier.padding(vertical = 12.dp))
                            } else {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(mediaMessages) { msg ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = CardDarkElevated,
                                            border = BorderStroke(1.dp, BorderSubtleWhite),
                                            modifier = Modifier.size(70.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = if (msg.type == "video") Icons.Rounded.PlayCircle else Icons.Rounded.Image,
                                                    contentDescription = "Média",
                                                    tint = VibrantCyan,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            if (linkMessages.isEmpty()) {
                                Text("Aucun lien partagé pour le moment 🔗", fontSize = 12.sp, color = TextMuted, modifier = Modifier.padding(vertical = 12.dp))
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    linkMessages.take(3).forEach { msg ->
                                        Text(text = "• " + msg.content, fontSize = 12.sp, color = VibrantCyan, maxLines = 1)
                                    }
                                }
                            }
                        }
                        2 -> {
                            if (starredMessages.isEmpty()) {
                                Text("Aucun message étoilé ⭐", fontSize = 12.sp, color = TextMuted, modifier = Modifier.padding(vertical = 12.dp))
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    starredMessages.take(3).forEach { msg ->
                                        Text(text = "⭐ " + msg.content, fontSize = 12.sp, color = TextPrimary, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Code de Jumelage Section
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = CardDarkElevated,
                border = BorderStroke(1.dp, BorderSubtleWhite),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Code de couple", coupleSpace.pairingCode)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Code copié dans le presse-papier ! 📋", Toast.LENGTH_SHORT).show()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Code de Jumelage Partagé", fontSize = 12.sp, color = TextSecondary)
                        Text(
                            text = if (coupleSpace.pairingCode.isNotEmpty()) coupleSpace.pairingCode else "NON JUMELÉ",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = VibrantCyan
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copier",
                        tint = VibrantCyan,
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Surface(
            shape = CircleShape,
            color = CardDarkElevated,
            border = BorderStroke(1.dp, BorderSubtleWhite),
            modifier = Modifier.size(46.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = label, tint = VibrantCyan, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = label, fontSize = 11.sp, color = TextSecondary)
    }
}
