package com.example.mikayala.ui.media.photo

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mikayala.theme.VibrantCyan
import kotlinx.coroutines.launch

@Composable
fun PhotoEditorScreen(
    imageUri: Uri? = null,
    bitmap: Bitmap? = null,
    onDismiss: () -> Unit,
    onComplete: (webpBytes: ByteArray, caption: String, isViewOnce: Boolean) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val viewModel: PhotoEditorViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    var isRendering by remember { mutableStateOf(false) }
    var exportedPreviewBytes by remember { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(imageUri, bitmap) {
        viewModel.loadMedia(context, imageUri, bitmap)
    }

    if (exportedPreviewBytes != null) {
        PhotoPreviewScreen(
            webpBytes = exportedPreviewBytes!!,
            initialCaption = state.caption,
            initialViewOnce = state.isViewOnce,
            onBackToEditor = { exportedPreviewBytes = null },
            onSend = { finalCaption, finalViewOnce ->
                val bytes = exportedPreviewBytes!!
                exportedPreviewBytes = null
                onComplete(bytes, finalCaption, finalViewOnce)
            }
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (state.isLoading || isRendering) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = VibrantCyan)
                }
            } else if (state.originalBitmap != null) {
                val original = state.originalBitmap!!

                // Real-time ColorMatrix for Adjustments (Brightness, Contrast, Saturation) + Filters
                val composeColorMatrix = remember(state.adjustments, state.activeFilter) {
                    val androidMatrix = PhotoRenderer.createAdjustedColorMatrix(state.adjustments, state.activeFilter)
                    ColorMatrix(androidMatrix.array)
                }

                val rotation = state.rotationDegrees.toFloat()
                val scaleX = if (state.isFlippedHorizontal) -1f else 1f
                val aspectRatio = state.cropRatio.ratio ?: (original.width.toFloat() / original.height.toFloat())

                // Center Canvas & Fullscreen Image Preview
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 90.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .aspectRatio(aspectRatio, matchHeightConstraintsFirst = false)
                            .onGloballyPositioned { coordinates ->
                                viewModel.updateViewDimensions(
                                    coordinates.size.width.toFloat(),
                                    coordinates.size.height.toFloat()
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = original.asImageBitmap(),
                            contentDescription = "Aperçu photo en direct",
                            colorFilter = ColorFilter.colorMatrix(composeColorMatrix),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    rotationZ = rotation,
                                    scaleX = scaleX
                                )
                        )

                        // Interactive real-time overlay (Draw, Text, Blur, Stickers)
                        PhotoEditorCanvas(
                            state = state,
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Top Floating Bar: Back, Undo, Redo, Reset
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    ) {
                        Icon(
                            Icons.Rounded.ArrowBack,
                            contentDescription = "Retour",
                            tint = Color.White
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Undo
                        IconButton(
                            onClick = { viewModel.undo() },
                            enabled = state.undoStack.isNotEmpty(),
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        ) {
                            Icon(
                                Icons.Rounded.Undo,
                                contentDescription = "Annuler",
                                tint = if (state.undoStack.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.3f)
                            )
                        }

                        // Redo
                        IconButton(
                            onClick = { viewModel.redo() },
                            enabled = state.redoStack.isNotEmpty(),
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        ) {
                            Icon(
                                Icons.Rounded.Redo,
                                contentDescription = "Rétablir",
                                tint = if (state.redoStack.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.3f)
                            )
                        }

                        // Reset All
                        IconButton(
                            onClick = { viewModel.resetAll() },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        ) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = "Réinitialiser",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Bottom Floating Bar: Tools + Caption + View Once + Send
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                ) {
                    PhotoEditorBottomBar(
                        state = state,
                        viewModel = viewModel,
                        onSend = {
                            coroutineScope.launch {
                                isRendering = true
                                val webpBytes = PhotoRenderer.renderToWebpBytes(
                                    context = context,
                                    originalBitmap = original,
                                    state = state
                                )
                                isRendering = false
                                if (webpBytes != null && webpBytes.isNotEmpty()) {
                                    exportedPreviewBytes = webpBytes
                                } else {
                                    Toast.makeText(context, "Erreur lors du traitement de l'image", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
