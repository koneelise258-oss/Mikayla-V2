package com.example.mikayala.ui.media.photo

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

object PhotoRenderer {

    /**
     * Renders the edited image in high quality and encodes it into WebP bytes ready for upload.
     */
    suspend fun renderToWebpBytes(
        context: Context,
        originalBitmap: Bitmap,
        state: PhotoEditorState,
        quality: Int = 88
    ): ByteArray? = withContext(Dispatchers.Default) {
        try {
            // 1. Apply transformations (Rotation + Flip)
            val matrix = Matrix().apply {
                if (state.rotationDegrees != 0) {
                    postRotate(state.rotationDegrees.toFloat())
                }
                if (state.isFlippedHorizontal) {
                    postScale(-1f, 1f)
                }
            }

            var transformedBitmap = Bitmap.createBitmap(
                originalBitmap,
                0,
                0,
                originalBitmap.width,
                originalBitmap.height,
                matrix,
                true
            )

            // 2. Apply Crop if specified
            state.cropRatio.ratio?.let { targetRatio ->
                val currentWidth = transformedBitmap.width.toFloat()
                val currentHeight = transformedBitmap.height.toFloat()
                val currentRatio = currentWidth / currentHeight

                val cropWidth: Int
                val cropHeight: Int
                if (currentRatio > targetRatio) {
                    // Image is wider than target ratio
                    cropHeight = transformedBitmap.height
                    cropWidth = (cropHeight * targetRatio).toInt().coerceAtMost(transformedBitmap.width)
                } else {
                    // Image is taller than target ratio
                    cropWidth = transformedBitmap.width
                    cropHeight = (cropWidth / targetRatio).toInt().coerceAtMost(transformedBitmap.height)
                }

                val cropX = max(0, (transformedBitmap.width - cropWidth) / 2)
                val cropY = max(0, (transformedBitmap.height - cropHeight) / 2)

                val cropped = Bitmap.createBitmap(transformedBitmap, cropX, cropY, cropWidth, cropHeight)
                if (cropped != transformedBitmap) {
                    transformedBitmap.recycle()
                    transformedBitmap = cropped
                }
            }

            // 3. Create mutable canvas bitmap with color filter / adjustments
            val renderedBitmap = Bitmap.createBitmap(
                transformedBitmap.width,
                transformedBitmap.height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(renderedBitmap)

            // Paint for bitmap drawing with color adjustment + filter
            val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val colorMatrix = createAdjustedColorMatrix(state.adjustments, state.activeFilter)
            imagePaint.colorFilter = ColorMatrixColorFilter(colorMatrix)

            canvas.drawBitmap(transformedBitmap, 0f, 0f, imagePaint)
            transformedBitmap.recycle()

            val scaleX = if (state.viewWidth > 0f) renderedBitmap.width.toFloat() / state.viewWidth else 1f
            val scaleY = if (state.viewHeight > 0f) renderedBitmap.height.toFloat() / state.viewHeight else 1f
            val avgScale = (scaleX + scaleY) / 2f

            // 4. Apply Localized Blur Areas
            if (state.blurAreas.isNotEmpty()) {
                val scaledBlurAreas = state.blurAreas.map { blur ->
                    blur.copy(
                        center = androidx.compose.ui.geometry.Offset(blur.center.x * scaleX, blur.center.y * scaleY),
                        radius = blur.radius * avgScale
                    )
                }
                applyBlurAreas(renderedBitmap, scaledBlurAreas)
            }

            // 5. Draw Vector / Finger Drawing Paths
            val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            for (pathItem in state.drawPaths) {
                if (pathItem.points.size < 2) continue

                if (pathItem.isEraser) {
                    pathPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                } else {
                    pathPaint.xfermode = null
                    pathPaint.color = pathItem.color.toArgb()
                }
                pathPaint.strokeWidth = pathItem.strokeWidth * avgScale

                val path = Path()
                path.moveTo(pathItem.points[0].x * scaleX, pathItem.points[0].y * scaleY)
                for (i in 1 until pathItem.points.size) {
                    val p = pathItem.points[i]
                    path.lineTo(p.x * scaleX, p.y * scaleY)
                }
                canvas.drawPath(path, pathPaint)
            }

            // 6. Draw Text Overlays
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.DEFAULT_BOLD
                setShadowLayer(6f * avgScale, 2f * scaleX, 2f * scaleY, android.graphics.Color.BLACK)
            }

            for (item in state.textOverlays) {
                textPaint.color = item.color.toArgb()
                textPaint.textSize = item.fontSizeSp * 2.5f * item.scale * avgScale
                canvas.drawText(item.text, item.position.x * scaleX, item.position.y * scaleY, textPaint)
            }

            // 7. Draw Stickers (Emojis & Decorative symbols)
            val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 64f
                typeface = Typeface.DEFAULT
            }

            for (sticker in state.stickerOverlays) {
                emojiPaint.textSize = 64f * sticker.scale * avgScale
                canvas.drawText(sticker.emoji, sticker.position.x * scaleX, sticker.position.y * scaleY, emojiPaint)
            }

            // 8. Compress directly to WebP bytes
            val outputStream = ByteArrayOutputStream()
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }

            renderedBitmap.compress(format, quality, outputStream)
            val finalBytes = outputStream.toByteArray()
            renderedBitmap.recycle()

            Log.d("PhotoRenderer", "Rendered edited photo to WebP: ${finalBytes.size} bytes")
            return@withContext finalBytes
        } catch (e: Exception) {
            Log.e("PhotoRenderer", "Error rendering edited photo: ${e.message}", e)
            return@withContext null
        }
    }

    internal fun createAdjustedColorMatrix(
        adj: PhotoAdjustment,
        filter: PhotoFilterType
    ): ColorMatrix {
        val matrix = ColorMatrix()

        // Saturation
        matrix.setSaturation(adj.saturation)

        // Contrast & Brightness
        val contrast = adj.contrast
        val brightness = adj.brightness
        val scale = contrast
        val translate = (-0.5f * scale + 0.5f) * 255f + brightness

        val contrastMatrix = ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(contrastMatrix)

        // Color Filters
        when (filter) {
            PhotoFilterType.NORMAL -> { /* No-op */ }
            PhotoFilterType.BLACK_AND_WHITE -> {
                val bw = ColorMatrix()
                bw.setSaturation(0f)
                matrix.postConcat(bw)
            }
            PhotoFilterType.WARM -> {
                // Boost red/yellow, lower blue
                val warmMatrix = ColorMatrix(floatArrayOf(
                    1.2f, 0f, 0f, 0f, 20f,
                    0f, 1.1f, 0f, 0f, 10f,
                    0f, 0f, 0.85f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                ))
                matrix.postConcat(warmMatrix)
            }
            PhotoFilterType.COLD -> {
                // Boost cyan/blue, lower red
                val coldMatrix = ColorMatrix(floatArrayOf(
                    0.85f, 0f, 0f, 0f, -15f,
                    0f, 1.05f, 0f, 0f, 5f,
                    0f, 0f, 1.25f, 0f, 25f,
                    0f, 0f, 0f, 1f, 0f
                ))
                matrix.postConcat(coldMatrix)
            }
        }

        return matrix
    }

    /**
     * Pixelates / blurs circular localized areas directly on the target bitmap.
     */
    private fun applyBlurAreas(bitmap: Bitmap, areas: List<BlurArea>) {
        val width = bitmap.width
        val height = bitmap.height

        for (area in areas) {
            val cx = area.center.x.toInt()
            val cy = area.center.y.toInt()
            val r = area.radius.toInt()

            val left = max(0, cx - r)
            val top = max(0, cy - r)
            val right = min(width, cx + r)
            val bottom = min(height, cy + r)

            if (right <= left || bottom <= top) continue

            val boxW = right - left
            val boxH = bottom - top

            // Pixelation block size based on intensity
            val blockSize = max(6, (area.intensity * 1.5f).toInt())

            for (y in top until bottom step blockSize) {
                for (x in left until right step blockSize) {
                    val blockW = min(blockSize, right - x)
                    val blockH = min(blockSize, bottom - y)

                    // Check if block center is within circle radius
                    val midX = x + blockW / 2
                    val midY = y + blockH / 2
                    val dx = (midX - cx).toFloat()
                    val dy = (midY - cy).toFloat()

                    if (dx * dx + dy * dy <= r * r) {
                        // Average pixel color in block
                        val sampleColor = bitmap.getPixel(midX.coerceIn(0, width - 1), midY.coerceIn(0, height - 1))
                        for (by in y until (y + blockH)) {
                            for (bx in x until (x + blockW)) {
                                bitmap.setPixel(bx, by, sampleColor)
                            }
                        }
                    }
                }
            }
        }
    }
}
