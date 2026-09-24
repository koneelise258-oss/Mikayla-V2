package com.example.mikayala.ui.media.photo

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mikayala.theme.VibrantCyan

/**
 * Screen showing the exact exported WebP photo to be sent,
 * allowing caption editing, view-once toggle, going back to editor, or final sending.
 */
@Composable
fun PhotoPreviewScreen(
    webpBytes: ByteArray,
    initialCaption: String = "",
    initialViewOnce: Boolean = false,
    onBackToEditor: () -> Unit,
    onSend: (caption: String, isViewOnce: Boolean) -> Unit
) {
    var caption by remember { mutableStateOf(initialCaption) }
    var isViewOnce by remember { mutableStateOf(initialViewOnce) }

    val previewBitmap = remember(webpBytes) {
        try {
            BitmapFactory.decodeByteArray(webpBytes, 0, webpBytes.size)
        } catch (_: Exception) {
            null
        }
    }

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
                    contentDescription = "Revenir à l'éditeur",
                    tint = Color.White
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Text(
                    text = "Aperçu final",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            // Edit icon to jump back
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

        // Center: Full rendered image preview
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 70.dp, bottom = 120.dp),
            contentAlignment = Alignment.Center
        ) {
            if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = "Photo finale",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("Chargement de l'aperçu...", color = Color.White)
            }
        }

        // Bottom Bar: Caption + View Once + Send
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

                // View Once Toggle
                IconButton(
                    onClick = { isViewOnce = !isViewOnce },
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (isViewOnce) VibrantCyan.copy(alpha = 0.25f) else Color(0x44000000),
                            CircleShape
                        )
                        .border(
                            1.dp,
                            if (isViewOnce) VibrantCyan else Color.White.copy(alpha = 0.2f),
                            CircleShape
                        )
                ) {
                    Text(
                        text = "1",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = if (isViewOnce) VibrantCyan else Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Final Send Button
                FilledIconButton(
                    onClick = { onSend(caption, isViewOnce) },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = VibrantCyan),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Send,
                        contentDescription = "Envoyer la photo",
                        tint = Color.Black
                    )
                }
            }
        }
    }
}
