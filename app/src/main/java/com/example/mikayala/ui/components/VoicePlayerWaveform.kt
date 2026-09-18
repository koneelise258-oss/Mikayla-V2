package com.example.mikayala.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mikayala.theme.*
import kotlinx.coroutines.delay

@Composable
fun VoicePlayerWaveform(
    durationSeconds: Int,
    isSender: Boolean,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentProgressSeconds by remember { mutableIntStateOf(0) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }

    val speeds = listOf(1.0f, 1.5f, 2.0f)

    LaunchedEffect(isPlaying, playbackSpeed) {
        if (isPlaying) {
            while (currentProgressSeconds < durationSeconds) {
                delay((1000L / playbackSpeed).toLong())
                currentProgressSeconds++
            }
            isPlaying = false
            currentProgressSeconds = 0
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "waveform_anim")
    val waveAnimPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveform_phase"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play / Pause Circle (Vibrant Blue as in image)
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(VibrantCyan, VibrantBlue)
                    )
                )
                .clickable { isPlaying = !isPlaying },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Lire",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Waveform vertical bars
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val barCount = 24
                for (i in 0 until barCount) {
                    val staticHeight = when (i % 7) {
                        0 -> 6.dp
                        1 -> 12.dp
                        2 -> 20.dp
                        3 -> 16.dp
                        4 -> 8.dp
                        5 -> 18.dp
                        else -> 14.dp
                    }
                    val progressFraction = if (durationSeconds > 0) currentProgressSeconds.toFloat() / durationSeconds else 0f
                    val barFraction = i.toFloat() / barCount
                    val isPast = barFraction <= progressFraction

                    val currentBarHeight = if (isPlaying && isPast) {
                        (staticHeight.value * (0.8f + 0.4f * waveAnimPhase)).dp
                    } else {
                        staticHeight
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(currentBarHeight)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (isPast) VibrantCyan else Color.White.copy(alpha = 0.35f)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Time & Speed Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayTime = if (isPlaying) currentProgressSeconds else durationSeconds
                val mins = displayTime / 60
                val secs = displayTime % 60
                Text(
                    text = String.format("%02d:%02d", mins, secs),
                    fontSize = 11.sp,
                    color = TimeStampMuted
                )

                // Speed Switcher Pill [ 1x, 1.5x, 2x ]
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = HoverStateCyan,
                    modifier = Modifier.clickable {
                        val nextIdx = (speeds.indexOf(playbackSpeed) + 1) % speeds.size
                        playbackSpeed = speeds[nextIdx]
                    }
                ) {
                    Text(
                        text = "${if (playbackSpeed == 1.0f) "1" else if (playbackSpeed == 1.5f) "1.5" else "2"}x",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = VibrantCyan,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
