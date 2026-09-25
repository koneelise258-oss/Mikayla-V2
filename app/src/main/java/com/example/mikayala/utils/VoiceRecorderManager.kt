package com.example.mikayala.utils

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File

class VoiceRecorderManager(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentFilePath: String? = null
    var isRecording = false
        private set
    var isPaused = false
        private set

    fun startRecording(fileName: String): Boolean {
        // Step 1: Ensure RECORD_AUDIO permission is granted
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e("VoiceRecorderManager", "RECORD_AUDIO permission not granted")
            return false
        }

        // Clean up any previous instance safely
        cleanup()

        val file = File(context.cacheDir, fileName)
        if (file.exists()) {
            file.delete()
        }
        currentFilePath = file.absolutePath

        var recorder: MediaRecorder? = null
        return try {
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(currentFilePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            isRecording = true
            isPaused = false
            Log.d("VoiceRecorderManager", "Recording started successfully to $currentFilePath")
            true
        } catch (e: Exception) {
            Log.e("VoiceRecorderManager", "startRecording failed: ${e.message}", e)
            try {
                recorder?.reset()
                recorder?.release()
            } catch (ignored: Exception) {}
            mediaRecorder = null
            isRecording = false
            isPaused = false
            currentFilePath?.let { File(it).delete() }
            currentFilePath = null
            false
        }
    }

    fun pauseRecording(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isRecording && !isPaused) {
            return try {
                mediaRecorder?.pause()
                isPaused = true
                true
            } catch (e: Exception) {
                Log.e("VoiceRecorderManager", "pauseRecording failed: ${e.message}")
                false
            }
        }
        return false
    }

    fun resumeRecording(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isRecording && isPaused) {
            return try {
                mediaRecorder?.resume()
                isPaused = false
                true
            } catch (e: Exception) {
                Log.e("VoiceRecorderManager", "resumeRecording failed: ${e.message}")
                false
            }
        }
        return false
    }

    fun getCurrentFilePath(): String? = currentFilePath

    fun getMaxAmplitude(): Int {
        return try {
            if (isRecording && !isPaused) {
                mediaRecorder?.maxAmplitude ?: 0
            } else 0
        } catch (e: Exception) {
            0
        }
    }

    fun stopRecording(): String? {
        if (!isRecording) return currentFilePath
        val path = currentFilePath
        return try {
            mediaRecorder?.apply {
                if (isPaused) {
                    try {
                        resume()
                    } catch (ignored: Exception) {}
                    isPaused = false
                }
                stop()
                reset()
                release()
            }
            mediaRecorder = null
            isRecording = false
            isPaused = false

            if (path != null) {
                val f = File(path)
                if (f.exists() && f.length() > 0) {
                    Log.d("VoiceRecorderManager", "Recording stopped successfully, file size=${f.length()} bytes")
                    path
                } else {
                    Log.e("VoiceRecorderManager", "Recording file is empty or missing")
                    f.delete()
                    null
                }
            } else null
        } catch (e: Exception) {
            Log.e("VoiceRecorderManager", "stop() failed: ${e.message}", e)
            cleanup()
            null
        }
    }

    fun cancelRecording() {
        cleanup()
    }

    private fun cleanup() {
        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (ignored: Exception) {}
                try {
                    reset()
                } catch (ignored: Exception) {}
                try {
                    release()
                } catch (ignored: Exception) {}
            }
        } catch (ignored: Exception) {}
        mediaRecorder = null
        isRecording = false
        isPaused = false
        currentFilePath?.let {
            val f = File(it)
            if (f.exists()) f.delete()
        }
        currentFilePath = null
    }
}
