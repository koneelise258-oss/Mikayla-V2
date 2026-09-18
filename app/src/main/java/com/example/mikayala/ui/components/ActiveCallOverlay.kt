package com.example.mikayala.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mikayala.theme.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun ActiveCallOverlay(
    partnerName: String = "Mikayala",
    isVideo: Boolean = true,
    onEndCall: (durationSeconds: Int) -> Unit
) {
    var isMuted by remember { mutableStateOf(false) }
    var isCameraOff by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(true) }
    var isFrontCamera by remember { mutableStateOf(true) }

    var callDurationSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            callDurationSeconds++
        }
    }

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val formattedDuration = remember(callDurationSeconds) {
        val mins = callDurationSeconds / 60
        val secs = callDurationSeconds % 60
        String.format("%02d:%02d", mins, secs)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNight)
    ) {
        // Video / Audio Canvas
        if (isVideo && !isCameraOff) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF1E1430),
                                Color(0xFF0F0B18),
                                Color(0xFF2B143E)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Partner simulated HD video feed
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .border(3.dp, AccentRose, CircleShape)
                            .background(CardElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "👩‍🦰", fontSize = 56.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = partnerName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Appel Vidéo HD P2P Chiffré • $formattedDuration",
                        fontSize = 13.sp,
                        color = AccentViolet
                    )
                }

                // Draggable Picture-in-Picture (PiP) Window of Self
                Box(
                    modifier = Modifier
                        .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                        .align(Alignment.TopEnd)
                        .padding(top = 40.dp, end = 20.dp)
                        .size(width = 110.dp, height = 155.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardElevated)
                        .border(1.5.dp, AccentViolet, RoundedCornerShape(16.dp))
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "🧑", fontSize = 34.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Vous", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            // Audio Only Interface
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(AccentRose.copy(alpha = 0.3f), Color.Transparent)))
                        .border(2.dp, AccentRose, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🎙️", fontSize = 48.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = partnerName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Appel Vocal Privé • $formattedDuration",
                    fontSize = 14.sp,
                    color = AccentViolet
                )
            }
        }

        // Top Status Bar Overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CardDarkBlur95,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtleWhite)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(OnlinePresenceGreen)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formattedDuration,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            if (isVideo) {
                IconButton(
                    onClick = { isFrontCamera = !isFrontCamera },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(CardDarkBlur95)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FlipCameraIos,
                        contentDescription = "Inverser caméra",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Bottom Controls Bar
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = CardDarkBlur95,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtleWhite),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute Mic
                IconButton(
                    onClick = { isMuted = !isMuted },
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(if (isMuted) Color.White.copy(alpha = 0.2f) else HoverStateWhite)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                        contentDescription = "Micro",
                        tint = if (isMuted) Color(0xFFFF5252) else TextPrimary
                    )
                }

                if (isVideo) {
                    // Mute Camera
                    IconButton(
                        onClick = { isCameraOff = !isCameraOff },
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(if (isCameraOff) Color.White.copy(alpha = 0.2f) else HoverStateWhite)
                    ) {
                        Icon(
                            imageVector = if (isCameraOff) Icons.Rounded.VideocamOff else Icons.Rounded.Videocam,
                            contentDescription = "Caméra",
                            tint = if (isCameraOff) Color(0xFFFF5252) else TextPrimary
                        )
                    }
                }

                // Speaker
                IconButton(
                    onClick = { isSpeakerOn = !isSpeakerOn },
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(if (isSpeakerOn) AccentViolet.copy(alpha = 0.2f) else HoverStateWhite)
                ) {
                    Icon(
                        imageVector = if (isSpeakerOn) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeDown,
                        contentDescription = "Haut-parleur",
                        tint = if (isSpeakerOn) AccentViolet else TextPrimary
                    )
                }

                // Hang Up (Red button)
                IconButton(
                    onClick = { onEndCall(callDurationSeconds) },
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF5252))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CallEnd,
                        contentDescription = "Raccrocher",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}
