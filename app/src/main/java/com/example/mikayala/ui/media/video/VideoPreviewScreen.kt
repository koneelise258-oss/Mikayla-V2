package com.example.mikayala.ui.media.video

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.mikayala.theme.VibrantCyan
import java.io.File

/**
 * Screen showing the final exported video before uploading to Supabase,
 * giving the user a complete WhatsApp-like experience:
 * Video Selection -> Editor -> Final Video Preview & Caption -> Send.
 */
@Composable
fun VideoPreviewScreen(
    videoFile: File,
    initialCaption: String = "",
    onBackToEditor: () -> Unit,
    onSend: (caption: String) -> Unit
) {
    var caption by remember { mutableStateOf(initialCaption) }
    var isPlaying by remember { mutableStateOf(true) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
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
                onClick = onBackToEditor,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    Icons.Rounded.ArrowBack,
                    contentDescription = "Revenir à l'éditeur vidéo",
                    tint = Color.White
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Text(
                    text = "Aperçu de la vidéo",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            IconButton(
                onClick = onBackToEditor,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = "Modifier",
                    tint = Color.White
                )
            }
        }

        // Center: Video Player
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 70.dp, bottom = 120.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoURI(Uri.fromFile(videoFile))
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            start()
                            isPlaying = true
                        }
                        videoViewRef = this
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        videoViewRef?.let { vv ->
                            if (vv.isPlaying) {
                                vv.pause()
                                isPlaying = false
                            } else {
                                vv.start()
                                isPlaying = true
                            }
                        }
                    }
            )

            if (!isPlaying) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "Lire",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        // Bottom Bar: Caption + Send Button
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
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
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
                    maxLines = 3
                )

                Spacer(modifier = Modifier.width(8.dp))

                FilledIconButton(
                    onClick = { onSend(caption) },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = VibrantCyan),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Send,
                        contentDescription = "Envoyer la vidéo",
                        tint = Color.Black
                    )
                }
            }
        }
    }
}
