package com.example.mikayala.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mikayala.data.model.CallLogEntity
import com.example.mikayala.data.repository.MikayalaRepository
import com.example.mikayala.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CallsScreen(
    repository: MikayalaRepository,
    onStartCall: (isVideo: Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val callLogs by repository.allCallLogs.collectAsState()
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.FRANCE) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNight)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Direct Quick Call Hero Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = CardDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderHighlight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(AccentRose.copy(alpha = 0.3f), Color.Transparent)))
                            .border(2.dp, AccentRose, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "👩‍🦰", fontSize = 26.sp)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Mikayala ❤️",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Ligne privée P2P active",
                            fontSize = 12.sp,
                            color = AccentViolet
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Audio Call Button
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                repository.initiateSupabaseCall(false)
                                onStartCall(false)
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(HoverStateWhite)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = "Appel Audio",
                            tint = AccentRose,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Video Call Button
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                repository.initiateSupabaseCall(true)
                                onStartCall(true)
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(AccentRose, AccentViolet)))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Videocam,
                            contentDescription = "Appel Vidéo",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "JOURNAL DES APPELS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = AccentViolet
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (callLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aucun appel pour le moment",
                    color = TextMuted,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(callLogs, key = { it.id }) { log ->
                    CallLogItem(
                        log = log,
                        timeFormatted = dateFormat.format(Date(log.timestamp)),
                        onCallBack = { isVideo -> onStartCall(isVideo) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CallLogItem(
    log: CallLogEntity,
    timeFormatted: String,
    onCallBack: (isVideo: Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtleWhite),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Call Type Icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (log.isMissed) Color(0x33FF5252) else HoverStateWhite),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (log.isMissed) {
                            Icons.AutoMirrored.Rounded.CallMissed
                        } else if (log.isIncoming) {
                            Icons.AutoMirrored.Rounded.CallReceived
                        } else {
                            Icons.AutoMirrored.Rounded.CallMade
                        },
                        contentDescription = null,
                        tint = if (log.isMissed) Color(0xFFFF5252) else if (log.isIncoming) OnlinePresenceGreen else AccentViolet,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = if (log.isVideo) "Appel vidéo HD" else "Appel vocal",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (log.isMissed) "Appel manqué • $timeFormatted" else "${log.durationSeconds / 60} min ${log.durationSeconds % 60} s • $timeFormatted",
                        fontSize = 12.sp,
                        color = if (log.isMissed) Color(0xFFFF5252) else TextSecondary
                    )
                }
            }

            IconButton(
                onClick = { onCallBack(log.isVideo) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (log.isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
                    contentDescription = "Rappeler",
                    tint = AccentRose,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
