package com.example.mikayala.ui.media.photo

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import java.util.UUID

enum class PhotoCropRatio(val label: String, val ratio: Float?) {
    FREE("Libre", null),
    RATIO_1_1("1:1", 1f),
    RATIO_4_5("4:5", 4f / 5f),
    RATIO_9_16("9:16", 9f / 16f),
    RATIO_16_9("16:9", 16f / 9f)
}

enum class PhotoFilterType(val label: String) {
    NORMAL("Normal"),
    BLACK_AND_WHITE("Noir & Blanc"),
    WARM("Chaud"),
    COLD("Froid")
}

data class DrawPathItem(
    val id: String = UUID.randomUUID().toString(),
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float,
    val isEraser: Boolean = false
)

data class BlurArea(
    val id: String = UUID.randomUUID().toString(),
    val center: Offset,
    val radius: Float,
    val intensity: Float = 15f // Blur radius in px
)

data class TextOverlayItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val color: Color = Color.White,
    val fontSizeSp: Float = 26f,
    val position: Offset = Offset(200f, 300f),
    val scale: Float = 1f
)

data class StickerOverlayItem(
    val id: String = UUID.randomUUID().toString(),
    val emoji: String,
    val position: Offset = Offset(200f, 300f),
    val scale: Float = 1f
)

enum class PhotoEditorActiveTab {
    NONE,
    CROP,
    ADJUST,
    FILTERS,
    DRAW,
    BLUR,
    TEXT,
    STICKERS
}

data class PhotoAdjustment(
    val brightness: Float = 0f, // -100 to 100
    val contrast: Float = 1f,   // 0.5 to 2.0
    val saturation: Float = 1f  // 0.0 to 2.0
)

data class PhotoEditorState(
    val originalUri: Uri? = null,
    val originalBitmap: Bitmap? = null,
    val isLoading: Boolean = false,
    
    // Crop & Transform
    val cropRatio: PhotoCropRatio = PhotoCropRatio.FREE,
    val rotationDegrees: Int = 0, // 0, 90, 180, 270
    val isFlippedHorizontal: Boolean = false,
    
    // Adjustments & Filters
    val adjustments: PhotoAdjustment = PhotoAdjustment(),
    val activeFilter: PhotoFilterType = PhotoFilterType.NORMAL,
    
    // Annotations
    val drawPaths: List<DrawPathItem> = emptyList(),
    val blurAreas: List<BlurArea> = emptyList(),
    val textOverlays: List<TextOverlayItem> = emptyList(),
    val stickerOverlays: List<StickerOverlayItem> = emptyList(),
    
    // Caption & Options
    val caption: String = "",
    val isViewOnce: Boolean = false,
    
    // Tool UI state
    val activeTab: PhotoEditorActiveTab = PhotoEditorActiveTab.NONE,
    val currentBrushColor: Color = Color.Red,
    val currentBrushWidth: Float = 10f,
    val isEraserActive: Boolean = false,
    val currentBlurRadius: Float = 40f,
    val currentBlurIntensity: Float = 15f,
    val viewWidth: Float = 0f,
    val viewHeight: Float = 0f,
    
    // History (Undo / Redo stacks)
    val undoStack: List<PhotoEditorSnapshot> = emptyList(),
    val redoStack: List<PhotoEditorSnapshot> = emptyList()
)

data class PhotoEditorSnapshot(
    val rotationDegrees: Int,
    val isFlippedHorizontal: Boolean,
    val adjustments: PhotoAdjustment,
    val activeFilter: PhotoFilterType,
    val drawPaths: List<DrawPathItem>,
    val blurAreas: List<BlurArea>,
    val textOverlays: List<TextOverlayItem>,
    val stickerOverlays: List<StickerOverlayItem>
)
