package com.example.mikayala.ui.media.video

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID

object VideoExporter {

    /**
     * Trims video from startMs to endMs, optionally stripping audio if isMuted is true,
     * writing the result to an MP4 file in context cache.
     */
    suspend fun exportVideo(
        context: Context,
        inputUri: Uri,
        startMs: Long,
        endMs: Long,
        isMuted: Boolean
    ): File? = withContext(Dispatchers.IO) {
        val outputFile = File(context.cacheDir, "edited_video_${UUID.randomUUID()}.mp4")
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        try {
            extractor = MediaExtractor()
            val afd = context.contentResolver.openAssetFileDescriptor(inputUri, "r")
            if (afd != null) {
                extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
            } else {
                extractor.setDataSource(context, inputUri, null)
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val trackCount = extractor.trackCount
            val indexMap = HashMap<Int, Int>()
            var bufferSize = -1

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

                if (mime.startsWith("audio") && isMuted) {
                    // Skip audio track if video is muted
                    continue
                }

                if (mime.startsWith("video") || mime.startsWith("audio")) {
                    extractor.selectTrack(i)
                    val dstIndex = muxer.addTrack(format)
                    indexMap[i] = dstIndex

                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        val newSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                        bufferSize = if (newSize > bufferSize) newSize else bufferSize
                    }
                }
            }

            if (bufferSize < 0) {
                bufferSize = 1024 * 1024 // default 1MB buffer
            }

            // Write orientation tag
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, inputUri)
                val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                val rotation = rotationStr?.toIntOrNull() ?: 0
                muxer.setOrientationHint(rotation)
                retriever.release()
            } catch (e: Exception) {
                Log.w("VideoExporter", "Could not set orientation hint: ${e.message}")
            }

            muxer.start()

            val startUs = startMs * 1000L
            val endUs = if (endMs > startMs) endMs * 1000L else Long.MAX_VALUE

            // Seek to start position
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val buffer = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                val trackIndex = extractor.sampleTrackIndex
                if (trackIndex < 0) break

                val sampleTime = extractor.sampleTime
                if (sampleTime > endUs) break

                val dstTrack = indexMap[trackIndex]
                if (dstTrack != null) {
                    bufferInfo.offset = 0
                    bufferInfo.size = extractor.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0) {
                        bufferInfo.size = 0
                        break
                    }
                    bufferInfo.presentationTimeUs = sampleTime - startUs
                    bufferInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(dstTrack, buffer, bufferInfo)
                }
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            extractor.release()

            if (outputFile.exists() && outputFile.length() > 0) {
                Log.d("VideoExporter", "Successfully exported video to ${outputFile.absolutePath} size=${outputFile.length()}")
                return@withContext outputFile
            } else {
                Log.e("VideoExporter", "Export produced empty file")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e("VideoExporter", "Error exporting video: ${e.message}", e)
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
            if (outputFile.exists()) outputFile.delete()
            return@withContext null
        }
    }
}
