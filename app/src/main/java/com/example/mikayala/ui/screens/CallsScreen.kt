package com.example.mikayala.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.mikayala.data.model.CallLogEntity
import com.example.mikayala.data.repository.MikayalaRepository
import com.example.mikayala.theme.*
import com.example.mikayala.ui.icons.AppleSvgIcons
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CallsScreen(
    repository: MikayalaRepository,
    onStartCall: (isVideo: Boolean) -> Unit
) {
    val callLogs by repository.allCallLogs.collectAsState()
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.FRANCE) }
    val callsSettings by repository.userSettings.collectAsState()
    val palette = MikayalaColors
    var activeFilter by remember { mutableStateOf("all") }

    val filteredLogs = remember(callLogs, activeFilter) {
        when (activeFilter) {
            "missed" -> callLogs.filter { it.isMissed }
            "video" -> callLogs.filter { it.isVideo }
            "audio" -> callLogs.filter { !it.isVideo }
            else -> callLogs
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = Spacing.maxContentWidth)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // iOS 18 Direct Quick Call Card
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = palette.surface,
            border = BorderStroke(0.5.dp, palette.borderStrong),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(palette.accentSoft)
                            .border(1.5.dp, palette.secondary.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (callsSettings.partnerAvatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = callsSettings.partnerAvatarUrl,
                                contentDescription = "Photo de votre partenaire",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                AppleSvgIcons.Person,
                                contentDescription = "Partenaire",
                                tint = palette.accent,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = callsSettings.partnerNickname.ifBlank { "Votre partenaire" },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Audio & FaceTime chiffrés",
                            fontSize = 12.sp,
                            color = palette.secondary
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { onStartCall(false) },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(palette.accentSoft)
                            .testTag("calls_hero_audio")
                    ) {
                        Icon(
                            imageVector = AppleSvgIcons.Phone,
                            contentDescription = "Appel Audio",
                            tint = palette.accent,
                            modifier = Modifier.size(21.dp)
                        )
                    }

                    IconButton(
                        onClick = { onStartCall(true) },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(palette.secondary)
                            .testTag("calls_hero_video")
                    ) {
                        Icon(
                            imageVector = AppleSvgIcons.Video,
                            contentDescription = "Appel Vidéo",
                            tint = Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // iOS 18 Segmented Control for call filters
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = palette.surfaceSunken,
            border = BorderStroke(0.5.dp, palette.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "all" to "Tous",
                    "missed" to "Manqués",
                    "video" to "Vidéo",
                    "audio" to "Audio"
                ).forEach { (key, label) ->
                    val selected = activeFilter == key
                    Surface(
                        onClick = { activeFilter = key },
                        shape = RoundedCornerShape(11.dp),
                        color = if (selected) palette.surfaceElevated else Color.Transparent,
                        border = if (selected) BorderStroke(0.5.dp, palette.borderStrong) else null,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 34.dp)
                            .testTag("calls_filter_$key")
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) palette.textPrimary else palette.textSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "RÉCENTS",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            color = palette.textMuted,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                com.example.mikayala.ui.components.SoftEmptyState(
                    icon = AppleSvgIcons.Phone,
                    title = if (activeFilter == "all") "Aucun appel pour le moment" else "Aucun appel dans ce filtre",
                    message = "Vos appels audio et vidéo avec votre partenaire s'afficheront ici."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(filteredLogs, key = { it.id }) { log ->
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
    val palette = MikayalaColors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = palette.surface,
        border = BorderStroke(0.8.dp, palette.border),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onCallBack(log.isVideo) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                log.isMissed -> palette.danger.copy(alpha = 0.16f)
                                log.isIncoming -> palette.secondarySoft
                                else -> palette.accentSoft
                            }
                        ),
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
                        tint = if (log.isMissed) palette.danger else if (log.isIncoming) palette.secondary else palette.accent,
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
                        color = if (log.isMissed) palette.danger else TextSecondary
                    )
                }
            }

            IconButton(
                onClick = { onCallBack(log.isVideo) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (log.isVideo) AppleSvgIcons.Video else AppleSvgIcons.Phone,
                    contentDescription = "Rappeler",
                    tint = if (log.isVideo) palette.secondary else palette.accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
