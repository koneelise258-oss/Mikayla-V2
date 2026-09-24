package com.example.mikayala.ui.media.video

import android.net.Uri
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mikayala.theme.VibrantCyan
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun VideoEditorScreen(
    videoUri: Uri,
    onDismiss: () -> Unit,
    onComplete: (exportedFile: File, caption: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val viewModel: VideoEditorViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    var isExporting by remember { mutableStateOf(false) }
    var exportedVideoFile by remember { mutableStateOf<File?>(null) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

    LaunchedEffect(videoUri) {
        viewModel.loadVideo(context, videoUri)
    }

    if (exportedVideoFile != null) {
        VideoPreviewScreen(
            videoFile = exportedVideoFile!!,
            initialCaption = state.caption,
            onBackToEditor = { exportedVideoFile = null },
            onSend = { finalCaption ->
                val f = exportedVideoFile!!
                exportedVideoFile = null
                onComplete(f, finalCaption)
            }
        )
    } else {
        // Playback loop to loop inside [trimStartMs, trimEndMs]
        LaunchedEffect(state.isPlaying) {
            while (state.isPlaying) {
                videoViewRef?.let { vv ->
                    if (vv.isPlaying) {
                        val pos = vv.currentPosition.toLong()
                        viewModel.updateCurrentPosition(pos)
                        if (pos >= state.trimEndMs && state.trimEndMs > state.trimStartMs) {
                            vv.seekTo(state.trimStartMs.toInt())
                        }
                    }
                }
                delay(200)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
        if (isExporting) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = VibrantCyan)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Exportation de la vidéo en cours...",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        contentDescription = "Retour",
                        tint = Color.White
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Audio Mute Toggle
                    IconButton(
                        onClick = { viewModel.toggleMute() },
                        modifier = Modifier.background(
                            if (state.isMuted) Color.Red.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.5f),
                            CircleShape
                        )
                    ) {
                        Icon(
                            if (state.isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                            contentDescription = "Audio",
                            tint = if (state.isMuted) Color.Red else Color.White
                        )
                    }

                    // Rotate 90°
                    IconButton(
                        onClick = { viewModel.rotate90() },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            Icons.Rounded.RotateRight,
                            contentDescription = "Pivoter",
                            tint = Color.White
                        )
                    }
                }
            }

            // Video Preview Center
            val targetRatio = state.cropRatio.ratio ?: (16f / 9f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 70.dp, bottom = 160.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .aspectRatio(targetRatio)
                        .rotate(state.rotationDegrees.toFloat())
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(videoUri)
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    start()
                                    viewModel.setPlaying(true)
                                }
                                setOnErrorListener { _, _, _ ->
                                    Toast.makeText(ctx, "Erreur lecture vidéo", Toast.LENGTH_SHORT).show()
                                    true
                                }
                                videoViewRef = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Text Overlay Preview
                    if (state.textOverlay.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = state.textOverlay,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Play/Pause Overlay Click
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                videoViewRef?.let { vv ->
                                    if (vv.isPlaying) {
                                        vv.pause()
                                        viewModel.setPlaying(false)
                                    } else {
                                        vv.start()
                                        viewModel.setPlaying(true)
                                    }
                                }
                            }
                    )
                }
            }

            // Bottom Controls Bar: Trim timeline, Caption, Send
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f), Color.Black)
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Active Sub-panel
                AnimatedVisibility(visible = state.activeTab != VideoEditorTab.NONE) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xDD1E293B)
                    ) {
                        when (state.activeTab) {
                            VideoEditorTab.TRIM -> {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    val startSec = (state.trimStartMs / 1000f)
                                    val endSec = (state.trimEndMs / 1000f)
                                    val durationSec = (state.durationMs / 1000f)
                                    Text(
                                        "Découpe : ${String.format("%.1f", startSec)}s - ${String.format("%.1f", endSec)}s (Total ${String.format("%.1f", durationSec)}s)",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    RangeSlider(
                                        value = (state.trimStartMs.toFloat())..(state.trimEndMs.toFloat()),
                                        onValueChange = { range ->
                                            viewModel.updateTrim(range.start.toLong(), range.endInclusive.toLong())
                                            videoViewRef?.seekTo(range.start.toInt())
                                        },
                                        valueRange = 0f..state.durationMs.coerceAtLeast(1000L).toFloat()
                                    )
                                }
                            }
                            VideoEditorTab.CROP -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    VideoCropRatio.values().forEach { ratio ->
                                        FilterChip(
                                            selected = state.cropRatio == ratio,
                                            onClick = { viewModel.setCropRatio(ratio) },
                                            label = { Text(ratio.label, fontSize = 12.sp) }
                                        )
                                    }
                                }
                            }
                            VideoEditorTab.TEXT -> {
                                var txtInput by remember { mutableStateOf(state.textOverlay) }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = txtInput,
                                        onValueChange = { txtInput = it },
                                        placeholder = { Text("Texte sur la vidéo...", color = Color.White.copy(alpha = 0.6f)) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(onClick = { viewModel.updateTextOverlay(txtInput) }) {
                                        Text("Appliquer")
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }

                // Caption Input + Send
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.caption,
                        onValueChange = { viewModel.updateCaption(it) },
                        placeholder = { Text("Ajouter une légende...", color = Color.White.copy(alpha = 0.6f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = VibrantCyan,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = Color(0x66000000),
                            unfocusedContainerColor = Color(0x44000000)
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f),
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledIconButton(
                        onClick = {
                            coroutineScope.launch {
                                isExporting = true
                                val exported = VideoExporter.exportVideo(
                                    context = context,
                                    inputUri = videoUri,
                                    startMs = state.trimStartMs,
                                    endMs = state.trimEndMs,
                                    isMuted = state.isMuted
                                )
                                isExporting = false
                                if (exported != null && exported.exists() && exported.length() > 0) {
                                    exportedVideoFile = exported
                                } else {
                                    Toast.makeText(context, "Erreur lors de l'export de la vidéo", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = VibrantCyan),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Send,
                            contentDescription = "Envoyer",
                            tint = Color.Black
                        )
                    }
                }

                // Tool Tabs Row
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (state.activeTab == VideoEditorTab.TRIM) VibrantCyan.copy(alpha = 0.2f) else Color(0x331E293B),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (state.activeTab == VideoEditorTab.TRIM) VibrantCyan else Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier.clickable { viewModel.setActiveTab(VideoEditorTab.TRIM) }
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.ContentCut, contentDescription = null, tint = VibrantCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Découper", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (state.activeTab == VideoEditorTab.CROP) VibrantCyan.copy(alpha = 0.2f) else Color(0x331E293B),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (state.activeTab == VideoEditorTab.CROP) VibrantCyan else Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier.clickable { viewModel.setActiveTab(VideoEditorTab.CROP) }
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Crop, contentDescription = null, tint = VibrantCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Format", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (state.activeTab == VideoEditorTab.TEXT) VibrantCyan.copy(alpha = 0.2f) else Color(0x331E293B),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (state.activeTab == VideoEditorTab.TEXT) VibrantCyan else Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier.clickable { viewModel.setActiveTab(VideoEditorTab.TEXT) }
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.TextFields, contentDescription = null, tint = VibrantCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Texte", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
}
