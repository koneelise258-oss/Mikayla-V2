package com.example.mikayala.ui.media

import android.graphics.Bitmap
import android.net.Uri

sealed class MediaEditorTarget {
    data class PhotoUri(val uri: Uri) : MediaEditorTarget()
    data class PhotoBitmap(val bitmap: Bitmap) : MediaEditorTarget()
    data class Video(val uri: Uri) : MediaEditorTarget()
}
