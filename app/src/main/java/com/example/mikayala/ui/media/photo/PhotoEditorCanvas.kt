package com.example.mikayala.ui.media.photo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun PhotoEditorCanvas(
    state: PhotoEditorState,
    viewModel: PhotoEditorViewModel,
    modifier: Modifier = Modifier
) {
    var currentDrawPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(state.activeTab) {
                when (state.activeTab) {
                    PhotoEditorActiveTab.DRAW -> {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentDrawPoints = listOf(offset)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                currentDrawPoints = currentDrawPoints + change.position
                            },
                            onDragEnd = {
                                if (currentDrawPoints.size > 1) {
                                    viewModel.addDrawPath(currentDrawPoints)
                                }
                                currentDrawPoints = emptyList()
                            },
                            onDragCancel = {
                                currentDrawPoints = emptyList()
                            }
                        )
                    }
                    PhotoEditorActiveTab.BLUR -> {
                        detectDragGestures(
                            onDragStart = { offset ->
                                viewModel.addBlurArea(offset)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                viewModel.addBlurArea(change.position)
                            }
                        )
                    }
                    else -> {}
                }
            }
    ) {
        // Draw real-time strokes and visual indicators on top of the image
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 1. Saved Drawing Paths
            for (pathItem in state.drawPaths) {
                if (pathItem.points.size > 1) {
                    val p = Path()
                    p.moveTo(pathItem.points[0].x, pathItem.points[0].y)
                    for (i in 1 until pathItem.points.size) {
                        p.lineTo(pathItem.points[i].x, pathItem.points[i].y)
                    }
                    drawPath(
                        path = p,
                        color = if (pathItem.isEraser) Color.Black.copy(alpha = 0.5f) else pathItem.color,
                        style = Stroke(
                            width = pathItem.strokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }

            // 2. Active stroke being drawn
            if (currentDrawPoints.size > 1) {
                val p = Path()
                p.moveTo(currentDrawPoints[0].x, currentDrawPoints[0].y)
                for (i in 1 until currentDrawPoints.size) {
                    p.lineTo(currentDrawPoints[i].x, currentDrawPoints[i].y)
                }
                drawPath(
                    path = p,
                    color = if (state.isEraserActive) Color.White.copy(alpha = 0.5f) else state.currentBrushColor,
                    style = Stroke(
                        width = state.currentBrushWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // 3. Blur indicator circles
            for (area in state.blurAreas) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.25f),
                    radius = area.radius,
                    center = area.center
                )
                drawCircle(
                    color = Color.Cyan.copy(alpha = 0.6f),
                    radius = area.radius,
                    center = area.center,
                    style = Stroke(width = 2f)
                )
            }
        }

        // 4. Draggable Text Overlays
        state.textOverlays.forEach { item ->
            var currentPos by remember(item.id) { mutableStateOf(item.position) }
            val density = LocalDensity.current

            Box(
                modifier = Modifier
                    .offset { IntOffset(currentPos.x.roundToInt(), currentPos.y.roundToInt()) }
                    .pointerInput(item.id) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                currentPos += dragAmount
                            },
                            onDragEnd = {
                                viewModel.updateTextPosition(item.id, currentPos)
                            }
                        )
                    }
                    .background(Color.Black.copy(alpha = 0.45f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.text,
                        color = item.color,
                        fontSize = item.fontSizeSp.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { viewModel.removeTextOverlay(item.id) },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Supprimer",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // 5. Draggable Sticker Overlays
        state.stickerOverlays.forEach { sticker ->
            var currentPos by remember(sticker.id) { mutableStateOf(sticker.position) }

            Box(
                modifier = Modifier
                    .offset { IntOffset(currentPos.x.roundToInt(), currentPos.y.roundToInt()) }
                    .pointerInput(sticker.id) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                currentPos += dragAmount
                            },
                            onDragEnd = {
                                viewModel.updateStickerPosition(sticker.id, currentPos)
                            }
                        )
                    }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = sticker.emoji,
                        fontSize = 44.sp
                    )
                    IconButton(
                        onClick = { viewModel.removeSticker(sticker.id) },
                        modifier = Modifier
                            .size(20.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Supprimer",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}
