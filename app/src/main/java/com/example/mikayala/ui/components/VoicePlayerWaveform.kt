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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mikayala.theme.*
import kotlinx.coroutines.delay
import java.io.File

/**
 * Lecteur audio de note vocale avec waveform interactive :
 * - Empêche les créations multiples de MediaPlayer lors d'appuis rapides
 * - Coordination globale : une seule lecture active à la fois dans l'application
 * - Maintien du MediaPlayer lors de la pause pour reprise exacte
 * - Libération rigoureuse des ressources (onDispose, erreurs, changement d'URL)
 * - Waveform utilisant les amplitudes réelles si disponibles, ou silhouette neutre élégante
 * - Vitesse de lecture dynamique (1x, 1.5x, 2x)
 */
@Composable
fun VoicePlayerWaveform(
    durationSeconds: Int,
    isSender: Boolean,
    audioUrl: String? = null,
    amplitudes: List<Float>? = null,
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

    // Suivi direct de l'instance active du MediaPlayer pour libération sans fuite
    var activeMediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    val speeds = listOf(1.0f, 1.5f, 2.0f)

    // Coordination globale : si un autre composant démarre la lecture, mettre en pause celui-ci
    val currentlyPlayingGlobal by VoicePlaybackCoordinator.currentlyPlayingUrl.collectAsState()
    LaunchedEffect(currentlyPlayingGlobal) {
        val activeGlobal = currentlyPlayingGlobal
        if (activeGlobal != null && activeGlobal != audioUrl && isPlaying) {
            try {
                activeMediaPlayer?.pause()
                currentPositionMs = activeMediaPlayer?.currentPosition ?: currentPositionMs
            } catch (ignored: Exception) {}
            isPlaying = false
        }
    }

    // Libération stricte et déterministe lors du changement d'URL ou démontage du composant
    DisposableEffect(audioUrl) {
        onDispose {
            val playerToRelease = activeMediaPlayer
            activeMediaPlayer = null
            isPrepared = false
            isPlaying = false
            isLoading = false
            currentPositionMs = 0

            audioUrl?.let { VoicePlaybackCoordinator.notifyStopped(it) }

            if (playerToRelease != null) {
                try {
                    if (playerToRelease.isPlaying) {
                        playerToRelease.stop()
                    }
                } catch (ignored: Exception) {}
                try {
                    playerToRelease.reset()
                    playerToRelease.release()
                } catch (ignored: Exception) {}
            }
        }
    }

    fun pausePlayback() {
        val player = activeMediaPlayer
        try {
            player?.pause()
            currentPositionMs = player?.currentPosition ?: currentPositionMs
        } catch (ignored: Exception) {}
        isPlaying = false
        audioUrl?.let { VoicePlaybackCoordinator.notifyStopped(it) }
    }

    fun seekToPosition(targetMs: Int) {
        val clamped = targetMs.coerceIn(0, actualDurationMs)
        currentPositionMs = clamped
        val player = activeMediaPlayer
        if (player != null && isPrepared) {
            try {
                player.seekTo(clamped)
            } catch (ignored: Exception) {}
        }
    }

    fun startOrResumePlayback() {
        // Empêcher les doubles créations lors des clics rapides
        if (isLoading) return

        val urlStr = audioUrl?.trim()
        if (urlStr.isNullOrEmpty()) {
            hasError = true
            onRetryFetch?.invoke()
            return
        }

        // Informer le coordinateur global pour stopper tout autre lecteur
        VoicePlaybackCoordinator.requestPlay(urlStr)

        // Cas 1 : Lecteur déjà préparé -> Reprendre
        val existing = activeMediaPlayer
        if (existing != null && isPrepared) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        existing.playbackParams = existing.playbackParams.setSpeed(playbackSpeed)
                    } catch (e: Exception) {
                        Log.w("VoicePlayer", "Failed to update speed: ${e.message}")
                    }
                }
                if (currentPositionMs > 0 && currentPositionMs < actualDurationMs) {
                    existing.seekTo(currentPositionMs)
                }
                existing.start()
                isPlaying = true
                hasError = false
            } catch (e: Exception) {
                Log.e("VoicePlayer", "Resume error: ${e.message}")
                hasError = true
                VoicePlaybackCoordinator.notifyStopped(urlStr)
            }
            return
        }

        // Cas 2 : Initialisation d'une nouvelle instance
        try {
            isLoading = true
            hasError = false

            // Nettoyer toute instance résiduelle avant de créer la nouvelle
            existing?.let { old ->
                try { old.reset(); old.release() } catch (ignored: Exception) {}
            }
            activeMediaPlayer = null

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
                if (activeMediaPlayer !== mp) {
                    // Une autre instance a été initialisée entre-temps
                    try { mp.release() } catch (ignored: Exception) {}
                    return@setOnPreparedListener
                }
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
                VoicePlaybackCoordinator.notifyStopped(urlStr)
            }

            player.setOnErrorListener { mp, what, extra ->
                Log.e("VoicePlayer", "MediaPlayer playback error: what=$what, extra=$extra")
                isPlaying = false
                isLoading = false
                isPrepared = false
                hasError = true
                try {
                    mp.reset()
                    mp.release()
                } catch (ignored: Exception) {}
                if (activeMediaPlayer === mp) {
                    activeMediaPlayer = null
                }
                VoicePlaybackCoordinator.notifyStopped(urlStr)
                onRetryFetch?.invoke()
                true
            }

            activeMediaPlayer = player
            player.prepareAsync()
        } catch (e: Exception) {
            Log.e("VoicePlayer", "Init MediaPlayer failed for $urlStr: ${e.message}", e)
            isLoading = false
            isPlaying = false
            hasError = true
            activeMediaPlayer?.let { try { it.release() } catch (ignored: Exception) {} }
            activeMediaPlayer = null
            VoicePlaybackCoordinator.notifyStopped(urlStr)
            onRetryFetch?.invoke()
        }
    }

    // Suivi continu de la position de lecture réelle
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying) {
                val player = activeMediaPlayer
                if (player != null && isPrepared) {
                    try {
                        if (player.isPlaying) {
                            currentPositionMs = player.currentPosition
                        }
                    } catch (ignored: Exception) {}
                }
                delay(60)
            }
        }
    }

    // Construction de la waveform : réelles amplitudes si disponibles, sinon forme équilibrée propre
    val barCount = 28
    val waveHeights: List<Dp> = remember(amplitudes, durationSeconds) {
        if (!amplitudes.isNullOrEmpty()) {
            val src = amplitudes
            List(barCount) { i ->
                val srcIdx = ((i.toFloat() / barCount.toFloat()) * src.size).toInt().coerceIn(0, src.size - 1)
                val amp = src[srcIdx].coerceIn(0f, 1f)
                (5 + (amp * 17)).toInt().dp
            }
        } else {
            // Silhouette neutre et sobre style mémo vocal standard
            val basePattern = listOf(
                0.3f, 0.45f, 0.7f, 0.55f, 0.85f, 0.6f, 0.95f, 0.75f,
                0.5f, 0.65f, 0.8f, 0.9f, 0.6f, 0.75f, 0.85f, 0.5f,
                0.7f, 0.9f, 0.65f, 0.8f, 0.55f, 0.75f, 0.95f, 0.6f,
                0.8f, 0.5f, 0.4f, 0.3f
            )
            List(barCount) { i ->
                val factor = basePattern.getOrElse(i) { 0.5f }
                (6 + (factor * 16)).toInt().dp
            }
        }
    }

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
                    if (hasError) Brush.linearGradient(listOf(Color(0xFFC4605C), Color(0xFFC4605C)))
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
                        color = TextPrimary,
                        strokeWidth = 2.dp
                    )
                }
                hasError -> {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Réessayer la lecture",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                isPlaying -> {
                    Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = "Mettre en pause",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Lire la note vocale",
                        tint = TextPrimary,
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
                    val renderedHeight = waveHeights[i]

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

            // Horodatage (écoulé / total) et Sélecteur de vitesse WhatsApp
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
                        val player = activeMediaPlayer
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
