package com.example.mikayala.ui.media.video

import android.net.Uri

enum class VideoCropRatio(val label: String, val ratio: Float?) {
    ORIGINAL("Original", null),
    RATIO_1_1("1:1", 1f),
    RATIO_9_16("9:16", 9f / 16f),
    RATIO_16_9("16:9", 16f / 9f)
}

data class VideoEditorState(
    val videoUri: Uri? = null,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val rotationDegrees: Int = 0, // 0, 90, 180, 270
    val isMuted: Boolean = false,
    val cropRatio: VideoCropRatio = VideoCropRatio.ORIGINAL,
    val caption: String = "",
    val textOverlay: String = "",
    val activeTab: VideoEditorTab = VideoEditorTab.NONE,
    val isExporting: Boolean = false
)

enum class VideoEditorTab {
    NONE,
    TRIM,
    CROP,
    ROTATION,
    AUDIO,
    TEXT
}
