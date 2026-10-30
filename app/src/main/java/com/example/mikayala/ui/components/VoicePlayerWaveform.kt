package com.example.mikayala.ui.components

import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mikayala.theme.*
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.abs
import kotlin.math.sin

/**
 * Lecteur audio de note vocale avec waveform interactive :
 * - Maintien du MediaPlayer pendant les pauses pour reprise exacte
 * - Synchronisation rigoureuse sur MediaPlayer.currentPosition (sans simulation)
 * - Navigation interactive par tap/seek sur les barres d'onde
 * - Contrôle de vitesse réel (1x, 1.5x, 2x)
 * - Silhouette acoustique déterministe basée sur l'identifiant audio
 */
@Composable
fun VoicePlayerWaveform(
    durationSeconds: Int,
    isSender: Boolean,
    audioUrl: String? = null,
    onRetryFetch: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }

    var currentPositionMs by remember { mutableIntStateOf(0) }
    var actualDurationMs by remember(durationSeconds) { mutableIntStateOf((durationSeconds.coerceAtLeast(1)) * 1000) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    val speeds = listOf(1.0f, 1.5f, 2.0f)

    // Libération du MediaPlayer uniquement quand l'URL change ou que le composant quitte l'écran
    DisposableEffect(audioUrl) {
        onDispose {
            try {
                mediaPlayer?.stop()
            } catch (ignored: Exception) {}
            try {
                mediaPlayer?.release()
            } catch (ignored: Exception) {}
            mediaPlayer = null
            isPrepared = false
            isPlaying = false
            isLoading = false
            currentPositionMs = 0
        }
    }

    // Fonction interne pour préparer et démarrer le lecteur
    fun startOrResumePlayback() {
        val urlStr = audioUrl?.trim()
        if (urlStr.isNullOrEmpty()) {
            hasError = true
            onRetryFetch?.invoke()
            return
        }

        // Cas 1 : Lecteur déjà préparé -> Reprendre là où on s'était arrêté
        val existing = mediaPlayer
        if (existing != null && isPrepared) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        existing.playbackParams = existing.playbackParams.setSpeed(playbackSpeed)
                    } catch (e: Exception) {
                        Log.w("VoicePlayer", "Failed to update speed: ${e.message}")
                    }
                }
                existing.start()
                isPlaying = true
                hasError = false
            } catch (e: Exception) {
                Log.e("VoicePlayer", "Resume error: ${e.message}")
                hasError = true
            }
            return
        }

        // Cas 2 : Initialisation d'un nouveau MediaPlayer
        try {
            isLoading = true
            hasError = false
            val player = MediaPlayer()

            if (urlStr.startsWith("http://") || urlStr.startsWith("https://") ||
                urlStr.startsWith("content://") || urlStr.startsWith("file://")
            ) {
                player.setDataSource(context, Uri.parse(urlStr))
            } else {
                val file = File(urlStr)
                if (file.exists()) {
                    player.setDataSource(file.absolutePath)
                } else {
                    player.setDataSource(context, Uri.parse(urlStr))
                }
            }

            player.setOnPreparedListener { mp ->
                isPrepared = true
                isLoading = false
                val dur = mp.duration
                if (dur > 0) {
                    actualDurationMs = dur
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        mp.playbackParams = mp.playbackParams.setSpeed(playbackSpeed)
                    } catch (e: Exception) {
                        Log.w("VoicePlayer", "Failed to set initial speed: ${e.message}")
                    }
                }
                if (currentPositionMs > 0 && currentPositionMs < actualDurationMs) {
                    mp.seekTo(currentPositionMs)
                }
                mp.start()
                isPlaying = true
            }

            player.setOnCompletionListener {
                isPlaying = false
                currentPositionMs = 0
                try {
                    it.seekTo(0)
                } catch (ignored: Exception) {}
            }

            player.setOnErrorListener { _, what, extra ->
                Log.e("VoicePlayer", "MediaPlayer playback error: what=$what, extra=$extra")
                isPlaying = false
                isLoading = false
                isPrepared = false
                hasError = true
                onRetryFetch?.invoke()
                true
            }

            player.prepareAsync()
            mediaPlayer = player
        } catch (e: Exception) {
            Log.e("VoicePlayer", "Init MediaPlayer failed for $urlStr: ${e.message}", e)
            isLoading = false
            isPlaying = false
            hasError = true
            onRetryFetch?.invoke()
        }
    }

    fun pausePlayback() {
        try {
            mediaPlayer?.pause()
            currentPositionMs = mediaPlayer?.currentPosition ?: currentPositionMs
        } catch (ignored: Exception) {}
        isPlaying = false
    }

    fun seekToPosition(targetMs: Int) {
        val clamped = targetMs.coerceIn(0, actualDurationMs)
        currentPositionMs = clamped
        val player = mediaPlayer
        if (player != null && isPrepared) {
            try {
                player.seekTo(clamped)
            } catch (ignored: Exception) {}
        }
    }

    // Suivi continu de la position réelle (uniquement quand la lecture est effective)
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying) {
                val player = mediaPlayer
                if (player != null && isPrepared) {
                    try {
                        if (player.isPlaying) {
                            currentPositionMs = player.currentPosition
                        }
                    } catch (ignored: Exception) {}
                }
                delay(80)
            }
        }
    }

    // Profil acoustique déterministe pour une waveform stable et esthétique
    val barCount = 28
    val waveSilhouette = remember(audioUrl, durationSeconds) {
        val seed = abs((audioUrl?.hashCode() ?: durationSeconds.hashCode()))
        List(barCount) { i ->
            val factor = sin(i * 0.42 + (seed % 10)) * 0.45 + sin(i * 0.85 + (seed % 7)) * 0.35 + 0.5
            (6 + (factor.coerceIn(0.1, 1.0) * 16)).toInt().dp
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "waveform_anim")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveform_pulse"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bouton Play / Pause / Loading / Retry
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (hasError) Brush.linearGradient(listOf(Color(0xFFE53935), Color(0xFFC62828)))
                    else Brush.horizontalGradient(listOf(VibrantCyan, VibrantBlue))
                )
                .clickable {
                    when {
                        hasError -> {
                            hasError = false
                            onRetryFetch?.invoke()
                            startOrResumePlayback()
                        }
                        isPlaying -> pausePlayback()
                        else -> startOrResumePlayback()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                }
                hasError -> {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Réessayer",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                isPlaying -> {
                    Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = "Pause",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Lire",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Waveform & Barre de progression
        Column(modifier = Modifier.weight(1f)) {
            // Ligne des barres avec détection de clic pour scrubbing / seek
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .pointerInput(actualDurationMs) {
                        detectTapGestures { offset ->
                            val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            val targetMs = (fraction * actualDurationMs).toInt()
                            seekToPosition(targetMs)
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val progressFraction = if (actualDurationMs > 0) {
                    (currentPositionMs.toFloat() / actualDurationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f

                for (i in 0 until barCount) {
                    val barFraction = i.toFloat() / barCount.toFloat()
                    val isPast = barFraction <= progressFraction
                    val baseHeight = waveSilhouette[i]

                    val renderedHeight = if (isPlaying && isPast) {
                        (baseHeight.value * pulsePhase).coerceIn(4f, 24f).dp
                    } else {
                        baseHeight
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(renderedHeight)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (isPast) VibrantCyan else Color.White.copy(alpha = 0.32f)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Horodatage (écoulé / total) et Sélecteur de vitesse
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currentSecs = currentPositionMs / 1000
                val totalSecs = (actualDurationMs / 1000).coerceAtLeast(durationSeconds).coerceAtLeast(1)

                val displayTime = if (isPlaying || currentPositionMs > 0) {
                    String.format("%02d:%02d / %02d:%02d", currentSecs / 60, currentSecs % 60, totalSecs / 60, totalSecs % 60)
                } else {
                    String.format("%02d:%02d", totalSecs / 60, totalSecs % 60)
                }

                Text(
                    text = displayTime,
                    fontSize = 11.sp,
                    color = TimeStampMuted,
                    fontWeight = FontWeight.Medium
                )

                // Sélecteur de vitesse WhatsApp (1x, 1.5x, 2x)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = HoverStateCyan,
                    modifier = Modifier.clickable {
                        val nextIdx = (speeds.indexOf(playbackSpeed) + 1) % speeds.size
                        val newSpeed = speeds[nextIdx]
                        playbackSpeed = newSpeed
                        val player = mediaPlayer
                        if (player != null && isPrepared && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            try {
                                player.playbackParams = player.playbackParams.setSpeed(newSpeed)
                            } catch (e: Exception) {
                                try {
                                    player.playbackParams = PlaybackParams().setSpeed(newSpeed)
                                } catch (ignored: Exception) {}
                            }
                        }
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
