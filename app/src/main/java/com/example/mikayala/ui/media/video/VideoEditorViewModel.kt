package com.example.mikayala.ui.media.video

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoEditorViewModel : ViewModel() {

    private val _state = MutableStateFlow(VideoEditorState())
    val state: StateFlow<VideoEditorState> = _state.asStateFlow()

    fun loadVideo(context: Context, uri: Uri) {
        _state.update { it.copy(videoUri = uri) }
        viewModelScope.launch {
            try {
                val duration = withContext(Dispatchers.IO) {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, uri)
                    val timeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    retriever.release()
                    timeStr?.toLongOrNull() ?: 10000L
                }
                _state.update {
                    it.copy(
                        durationMs = duration,
                        trimStartMs = 0L,
                        trimEndMs = duration
                    )
                }
            } catch (e: Exception) {
                Log.e("VideoEditorViewModel", "Failed to get video duration: ${e.message}", e)
            }
        }
    }

    fun setActiveTab(tab: VideoEditorTab) {
        _state.update {
            if (it.activeTab == tab) it.copy(activeTab = VideoEditorTab.NONE)
            else it.copy(activeTab = tab)
        }
    }

    fun updateTrim(startMs: Long, endMs: Long) {
        _state.update { it.copy(trimStartMs = startMs, trimEndMs = endMs) }
    }

    fun rotate90() {
        _state.update { it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360) }
    }

    fun toggleMute() {
        _state.update { it.copy(isMuted = !it.isMuted) }
    }

    fun setCropRatio(ratio: VideoCropRatio) {
        _state.update { it.copy(cropRatio = ratio) }
    }

    fun updateCaption(caption: String) {
        _state.update { it.copy(caption = caption) }
    }

    fun updateTextOverlay(text: String) {
        _state.update { it.copy(textOverlay = text) }
    }

    fun setPlaying(playing: Boolean) {
        _state.update { it.copy(isPlaying = playing) }
    }

    fun updateCurrentPosition(pos: Long) {
        _state.update { it.copy(currentPositionMs = pos) }
    }
}
