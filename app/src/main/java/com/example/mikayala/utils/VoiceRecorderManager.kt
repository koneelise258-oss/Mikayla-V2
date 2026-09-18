package com.example.mikayala.utils

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

class VoiceRecorderManager(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentFilePath: String? = null
    var isRecording = false
    var isPaused = false

    fun startRecording(fileName: String): Boolean {
        val file = File(context.cacheDir, fileName)
        currentFilePath = file.absolutePath

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(currentFilePath)
            try {
                prepare()
                start()
                isRecording = true
                isPaused = false
                return true
            } catch (e: IOException) {
                Log.e("VoiceRecorderManager", "prepare() failed: ${e.message}")
                return false
            }
        }
    }

    fun pauseRecording(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isRecording && !isPaused) {
            mediaRecorder?.pause()
            isPaused = true
            return true
        }
        return false
    }

    fun resumeRecording(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isRecording && isPaused) {
            mediaRecorder?.resume()
            isPaused = false
            return true
        }
        return false
    }

    fun stopRecording(): String? {
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            isPaused = false
            currentFilePath
        } catch (e: Exception) {
            Log.e("VoiceRecorderManager", "stop() failed: ${e.message}")
            null
        }
    }

    fun cancelRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            isPaused = false
            currentFilePath?.let { File(it).delete() }
            currentFilePath = null
        } catch (e: Exception) {
            Log.e("VoiceRecorderManager", "cancel() failed: ${e.message}")
        }
    }
}
