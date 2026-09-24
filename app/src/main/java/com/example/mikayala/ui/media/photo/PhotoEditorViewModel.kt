package com.example.mikayala.ui.media.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhotoEditorViewModel : ViewModel() {

    private val _state = MutableStateFlow(PhotoEditorState())
    val state: StateFlow<PhotoEditorState> = _state.asStateFlow()

    fun loadMedia(context: Context, uri: Uri?, bitmap: Bitmap? = null) {
        _state.update { it.copy(isLoading = true, originalUri = uri) }
        viewModelScope.launch {
            try {
                val loadedBitmap: Bitmap? = if (bitmap != null) {
                    bitmap
                } else if (uri != null) {
                    withContext(Dispatchers.IO) {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        BitmapFactory.decodeStream(inputStream)
                    }
                } else null

                if (loadedBitmap != null) {
                    _state.update {
                        it.copy(
                            originalBitmap = loadedBitmap,
                            isLoading = false
                        )
                    }
                } else {
                    _state.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                Log.e("PhotoEditorViewModel", "Failed to load media: ${e.message}", e)
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun saveSnapshot() {
        val s = _state.value
        val snapshot = PhotoEditorSnapshot(
            rotationDegrees = s.rotationDegrees,
            isFlippedHorizontal = s.isFlippedHorizontal,
            adjustments = s.adjustments,
            activeFilter = s.activeFilter,
            drawPaths = s.drawPaths,
            blurAreas = s.blurAreas,
            textOverlays = s.textOverlays,
            stickerOverlays = s.stickerOverlays
        )
        _state.update {
            it.copy(
                undoStack = it.undoStack + snapshot,
                redoStack = emptyList() // clear redo on new action
            )
        }
    }

    fun undo() {
        val s = _state.value
        if (s.undoStack.isEmpty()) return

        val lastSnapshot = s.undoStack.last()
        val currentSnapshot = PhotoEditorSnapshot(
            rotationDegrees = s.rotationDegrees,
            isFlippedHorizontal = s.isFlippedHorizontal,
            adjustments = s.adjustments,
            activeFilter = s.activeFilter,
            drawPaths = s.drawPaths,
            blurAreas = s.blurAreas,
            textOverlays = s.textOverlays,
            stickerOverlays = s.stickerOverlays
        )

        _state.update {
            it.copy(
                rotationDegrees = lastSnapshot.rotationDegrees,
                isFlippedHorizontal = lastSnapshot.isFlippedHorizontal,
                adjustments = lastSnapshot.adjustments,
                activeFilter = lastSnapshot.activeFilter,
                drawPaths = lastSnapshot.drawPaths,
                blurAreas = lastSnapshot.blurAreas,
                textOverlays = lastSnapshot.textOverlays,
                stickerOverlays = lastSnapshot.stickerOverlays,
                undoStack = it.undoStack.dropLast(1),
                redoStack = it.redoStack + currentSnapshot
            )
        }
    }

    fun redo() {
        val s = _state.value
        if (s.redoStack.isEmpty()) return

        val nextSnapshot = s.redoStack.last()
        val currentSnapshot = PhotoEditorSnapshot(
            rotationDegrees = s.rotationDegrees,
            isFlippedHorizontal = s.isFlippedHorizontal,
            adjustments = s.adjustments,
            activeFilter = s.activeFilter,
            drawPaths = s.drawPaths,
            blurAreas = s.blurAreas,
            textOverlays = s.textOverlays,
            stickerOverlays = s.stickerOverlays
        )

        _state.update {
            it.copy(
                rotationDegrees = nextSnapshot.rotationDegrees,
                isFlippedHorizontal = nextSnapshot.isFlippedHorizontal,
                adjustments = nextSnapshot.adjustments,
                activeFilter = nextSnapshot.activeFilter,
                drawPaths = nextSnapshot.drawPaths,
                blurAreas = nextSnapshot.blurAreas,
                textOverlays = nextSnapshot.textOverlays,
                stickerOverlays = nextSnapshot.stickerOverlays,
                undoStack = it.undoStack + currentSnapshot,
                redoStack = it.redoStack.dropLast(1)
            )
        }
    }

    fun resetAll() {
        saveSnapshot()
        _state.update {
            it.copy(
                cropRatio = PhotoCropRatio.FREE,
                rotationDegrees = 0,
                isFlippedHorizontal = false,
                adjustments = PhotoAdjustment(),
                activeFilter = PhotoFilterType.NORMAL,
                drawPaths = emptyList(),
                blurAreas = emptyList(),
                textOverlays = emptyList(),
                stickerOverlays = emptyList(),
                activeTab = PhotoEditorActiveTab.NONE
            )
        }
    }

    fun setActiveTab(tab: PhotoEditorActiveTab) {
        _state.update {
            if (it.activeTab == tab) it.copy(activeTab = PhotoEditorActiveTab.NONE)
            else it.copy(activeTab = tab)
        }
    }

    // --- Transform / Crop ---
    fun rotate90() {
        saveSnapshot()
        _state.update {
            it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360)
        }
    }

    fun flipHorizontal() {
        saveSnapshot()
        _state.update {
            it.copy(isFlippedHorizontal = !it.isFlippedHorizontal)
        }
    }

    fun setCropRatio(ratio: PhotoCropRatio) {
        saveSnapshot()
        _state.update { it.copy(cropRatio = ratio) }
    }

    // --- Adjustments ---
    fun updateBrightness(value: Float) {
        _state.update { it.copy(adjustments = it.adjustments.copy(brightness = value)) }
    }

    fun updateContrast(value: Float) {
        _state.update { it.copy(adjustments = it.adjustments.copy(contrast = value)) }
    }

    fun updateSaturation(value: Float) {
        _state.update { it.copy(adjustments = it.adjustments.copy(saturation = value)) }
    }

    fun setFilter(filter: PhotoFilterType) {
        saveSnapshot()
        _state.update { it.copy(activeFilter = filter) }
    }

    // --- Drawing ---
    fun setBrushColor(color: Color) {
        _state.update { it.copy(currentBrushColor = color, isEraserActive = false) }
    }

    fun setBrushWidth(width: Float) {
        _state.update { it.copy(currentBrushWidth = width) }
    }

    fun toggleEraser() {
        _state.update { it.copy(isEraserActive = !it.isEraserActive) }
    }

    fun addDrawPath(points: List<Offset>) {
        if (points.size < 2) return
        saveSnapshot()
        val s = _state.value
        val newPath = DrawPathItem(
            points = points,
            color = s.currentBrushColor,
            strokeWidth = s.currentBrushWidth,
            isEraser = s.isEraserActive
        )
        _state.update { it.copy(drawPaths = it.drawPaths + newPath) }
    }

    // --- Localized Blur ---
    fun setBlurRadius(radius: Float) {
        _state.update { it.copy(currentBlurRadius = radius) }
    }

    fun setBlurIntensity(intensity: Float) {
        _state.update { it.copy(currentBlurIntensity = intensity) }
    }

    fun addBlurArea(center: Offset) {
        saveSnapshot()
        val s = _state.value
        val newArea = BlurArea(
            center = center,
            radius = s.currentBlurRadius,
            intensity = s.currentBlurIntensity
        )
        _state.update { it.copy(blurAreas = it.blurAreas + newArea) }
    }

    // --- Text Overlays ---
    fun addTextOverlay(text: String, color: Color = Color.White) {
        if (text.isBlank()) return
        saveSnapshot()
        val newText = TextOverlayItem(
            text = text,
            color = color,
            position = Offset(150f, 300f)
        )
        _state.update { it.copy(textOverlays = it.textOverlays + newText) }
    }

    fun updateTextPosition(id: String, newPosition: Offset) {
        _state.update {
            it.copy(
                textOverlays = it.textOverlays.map { item ->
                    if (item.id == id) item.copy(position = newPosition) else item
                }
            )
        }
    }

    fun removeTextOverlay(id: String) {
        saveSnapshot()
        _state.update {
            it.copy(textOverlays = it.textOverlays.filterNot { item -> item.id == id })
        }
    }

    // --- Stickers ---
    fun addSticker(emoji: String) {
        saveSnapshot()
        val newSticker = StickerOverlayItem(
            emoji = emoji,
            position = Offset(150f, 300f)
        )
        _state.update { it.copy(stickerOverlays = it.stickerOverlays + newSticker) }
    }

    fun updateStickerPosition(id: String, newPosition: Offset) {
        _state.update {
            it.copy(
                stickerOverlays = it.stickerOverlays.map { item ->
                    if (item.id == id) item.copy(position = newPosition) else item
                }
            )
        }
    }

    fun removeSticker(id: String) {
        saveSnapshot()
        _state.update {
            it.copy(stickerOverlays = it.stickerOverlays.filterNot { item -> item.id == id })
        }
    }

    // --- Caption & Options ---
    fun updateCaption(caption: String) {
        _state.update { it.copy(caption = caption) }
    }

    fun toggleViewOnce() {
        _state.update { it.copy(isViewOnce = !it.isViewOnce) }
    }
}
