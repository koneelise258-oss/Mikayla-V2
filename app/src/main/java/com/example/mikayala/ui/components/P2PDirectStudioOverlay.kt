package com.example.mikayala.ui.components

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mikayala.data.repository.MikayalaRepository
import com.example.mikayala.theme.*
import kotlinx.coroutines.delay

@Composable
fun P2PDirectStudioOverlay(
    repository: MikayalaRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isConnectedP2P by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(true) }
    var currentBand by remember { mutableStateOf("5 GHz (Ultra-Rapide)") }
    var directMessageText by remember { mutableStateOf("") }
    val offlineTransfers = remember {
        mutableStateListOf(
            "Photo_Intime_01.jpg • 100% • 42 Mo/s ⚡",
            "Vocal_Secret_Mikayala.aac • 100% • 38 Mo/s 🎙️"
        )
    }

    // Radar pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "radar_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radar_scale"
    )

    LaunchedEffect(Unit) {
        delay(2500)
        isSearching = false
        isConnectedP2P = true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepNight),
            color = DeepNight
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Fermer", tint = TextPrimary)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Liaison Directe Hors-Ligne 📡",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Wi-Fi Direct / Sans 4G ni Internet (0 Mo Data)",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    // Mode switch badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isConnectedP2P) OnlinePresenceGreen.copy(alpha = 0.15f) else HoverStateCoral,
                        border = BorderStroke(1.dp, if (isConnectedP2P) OnlinePresenceGreen else AccentRose)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isConnectedP2P) OnlinePresenceGreen else AccentRose)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isConnectedP2P) "P2P Connecté" else "Recherche...",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isConnectedP2P) OnlinePresenceGreen else AccentRose
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Radar & Status Section
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = CardDark,
                    border = BorderStroke(1.dp, BorderHighlight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .scale(if (isSearching) pulseScale else 1f)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            AccentRose.copy(alpha = 0.35f),
                                            AccentViolet.copy(alpha = 0.1f),
                                            Color.Transparent
                                        )
                                    )
                                )
                                .border(
                                    2.dp,
                                    if (isConnectedP2P) OnlinePresenceGreen else AccentRose,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isConnectedP2P) Icons.Rounded.WifiTethering else Icons.Rounded.Search,
                                contentDescription = null,
                                tint = if (isConnectedP2P) OnlinePresenceGreen else AccentRose,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (isConnectedP2P) {
                            Text(
                                text = "Lié en direct à Mikayala ❤️",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Canal P2P sécurisé • Débit 45 Mo/s • 0 Mo Data",
                                fontSize = 12.sp,
                                color = OnlinePresenceGreen
                            )
                        } else {
                            Text(
                                text = "Recherche du signal de Mikayala...",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Assurez-vous que les deux téléphones sont à proximité",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Frequency band switch & fast send tools
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = CardDark,
                        border = BorderStroke(1.dp, BorderSubtleWhite),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                currentBand = if (currentBand.contains("5 GHz")) "2.4 GHz (Longue Portée)" else "5 GHz (Ultra-Rapide)"
                                Toast.makeText(context, "Bande Wi-Fi Direct : $currentBand", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Tune, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Canal P2P", fontSize = 10.sp, color = TextSecondary)
                                Text(currentBand, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = AccentRose.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, AccentRose.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                offlineTransfers.add(0, "Fichier_Envoyé_${System.currentTimeMillis().toString().takeLast(4)}.dat • 100% • 48 Mo/s 🚀")
                                Toast.makeText(context, "Fichier envoyé instantanément en P2P !", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.FileUpload, contentDescription = null, tint = AccentRose, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Envoi Éclair", fontSize = 10.sp, color = AccentRose)
                                Text("Beam Photo / Fichier", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Direct chat input for offline messages
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CardDark,
                    border = BorderStroke(1.dp, BorderSubtleWhite),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = directMessageText,
                            onValueChange = { directMessageText = it },
                            placeholder = { Text("Message direct P2P sans Internet...", color = TextMuted, fontSize = 13.sp) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = {
                                if (directMessageText.isNotBlank()) {
                                    repository.sendMessage(
                                        content = "📡 [P2P Direct] " + directMessageText.trim()
                                    )
                                    offlineTransfers.add(0, "Message P2P transmis à Mikayala ✓✓")
                                    directMessageText = ""
                                    Toast.makeText(context, "Message transmis en direct par liaison locale !", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Envoyer", tint = AccentRose)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "HISTORIQUE DU CANAL DIRECT (HORS-LIGNE)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = AccentViolet
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(offlineTransfers.size) { index ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = CardDark,
                            border = BorderStroke(1.dp, BorderSubtleWhite),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = OnlinePresenceGreen, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = offlineTransfers[index],
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
