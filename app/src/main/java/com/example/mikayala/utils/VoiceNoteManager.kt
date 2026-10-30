package com.example.mikayala.utils

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.pow

enum class VoiceNoteState {
    IDLE,
    RECORDING,
    LOCKED,
    PAUSED
}

/**
 * Manager gérant le cycle de vie complet de l'enregistrement de notes vocales
 * avec échantillonnage d'amplitudes en temps réel (Waveform WhatsApp),
 * contrôle des états (Hold, Locked, Paused) et gestion des fichiers temporaires.
 */
class VoiceNoteManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceNoteManager"
    }

    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startTimeMillis: Long = 0L
    private var accumulatedDurationSeconds: Int = 0

    private val _recordingState = MutableStateFlow(VoiceNoteState.IDLE)
    val recordingState: StateFlow<VoiceNoteState> = _recordingState.asStateFlow()

    private val _durationSeconds = MutableStateFlow(0)
    val durationSeconds: StateFlow<Int> = _durationSeconds.asStateFlow()

    // Amplitudes normalisées (0.08f .. 1.0f) pour la waveform temps réel
    private val _liveAmplitudes = MutableStateFlow<List<Float>>(emptyList())
    val liveAmplitudes: StateFlow<List<Float>> = _liveAmplitudes.asStateFlow()

    private var pollingJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startRecording(): Boolean {
        if (!hasRecordPermission()) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            return false
        }

        cleanup()

        val tempFile = File(context.cacheDir, "voice_note_${System.currentTimeMillis()}.m4a")
        if (tempFile.exists()) {
            tempFile.delete()
        }
        currentFile = tempFile

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
                setOutputFile(tempFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            startTimeMillis = System.currentTimeMillis()
            accumulatedDurationSeconds = 0
            _durationSeconds.value = 0
            _liveAmplitudes.value = List(18) { 0.12f }
            _recordingState.value = VoiceNoteState.RECORDING

            startPolling()
            Log.d(TAG, "Voice recording started -> ${tempFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            try {
                recorder?.reset()
                recorder?.release()
            } catch (ignored: Exception) {}
            mediaRecorder = null
            _recordingState.value = VoiceNoteState.IDLE
            currentFile?.delete()
            currentFile = null
            false
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = coroutineScope.launch {
            while (isActive && (_recordingState.value == VoiceNoteState.RECORDING || _recordingState.value == VoiceNoteState.LOCKED)) {
                val elapsedSec = ((System.currentTimeMillis() - startTimeMillis) / 1000).toInt() + accumulatedDurationSeconds
                _durationSeconds.value = elapsedSec

                val rawAmp = try {
                    mediaRecorder?.maxAmplitude ?: 0
                } catch (e: Exception) {
                    0
                }

                // Normalisation 0..32767 -> 0.08..1.0f avec courbe de réponse dynamique
                val normalized = (rawAmp / 32767f).coerceIn(0f, 1f)
                val boosted = (normalized.toDouble().pow(0.55).toFloat()).coerceIn(0.08f, 1.0f)

                val current = _liveAmplitudes.value
                val updated = (if (current.size >= 24) current.drop(1) else current) + boosted
                _liveAmplitudes.value = updated

                delay(70)
            }
        }
    }

    fun lockRecording() {
        if (_recordingState.value == VoiceNoteState.RECORDING) {
            _recordingState.value = VoiceNoteState.LOCKED
            Log.d(TAG, "Voice recording locked into hands-free mode")
        }
    }

    fun pauseRecording(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            (_recordingState.value == VoiceNoteState.RECORDING || _recordingState.value == VoiceNoteState.LOCKED)
        ) {
            return try {
                mediaRecorder?.pause()
                accumulatedDurationSeconds += ((System.currentTimeMillis() - startTimeMillis) / 1000).toInt()
                pollingJob?.cancel()
                _recordingState.value = VoiceNoteState.PAUSED
                Log.d(TAG, "Voice recording paused at $accumulatedDurationSeconds s")
                true
            } catch (e: Exception) {
                Log.e(TAG, "pauseRecording failed: ${e.message}", e)
                false
            }
        }
        return false
    }

    fun resumeRecording(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && _recordingState.value == VoiceNoteState.PAUSED) {
            return try {
                mediaRecorder?.resume()
                startTimeMillis = System.currentTimeMillis()
                _recordingState.value = VoiceNoteState.LOCKED
                startPolling()
                Log.d(TAG, "Voice recording resumed")
                true
            } catch (e: Exception) {
                Log.e(TAG, "resumeRecording failed: ${e.message}", e)
                false
            }
        }
        return false
    }

    fun stopAndGetResult(): Pair<File?, Int> {
        val state = _recordingState.value
        if (state == VoiceNoteState.IDLE) return Pair(null, 0)

        pollingJob?.cancel()
        val file = currentFile
        val totalDuration = if (state == VoiceNoteState.PAUSED) {
            accumulatedDurationSeconds.coerceAtLeast(1)
        } else {
            val elapsed = ((System.currentTimeMillis() - startTimeMillis) / 1000).toInt() + accumulatedDurationSeconds
            elapsed.coerceAtLeast(1)
        }

        return try {
            mediaRecorder?.apply {
                if (state == VoiceNoteState.PAUSED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    try { resume() } catch (ignored: Exception) {}
                }
                stop()
                reset()
                release()
            }
            mediaRecorder = null
            _recordingState.value = VoiceNoteState.IDLE
            _durationSeconds.value = 0
            _liveAmplitudes.value = emptyList()

            if (file != null && file.exists() && file.length() > 0) {
                Log.d(TAG, "Voice recording finished successfully: ${file.absolutePath}, duration=$totalDuration s, size=${file.length()} bytes")
                currentFile = null
                Pair(file, totalDuration)
            } else {
                Log.e(TAG, "Voice recording file empty or missing")
                file?.delete()
                currentFile = null
                Pair(null, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "stopRecording failed: ${e.message}", e)
            cleanup()
            Pair(null, 0)
        }
    }

    fun cancelRecording() {
        cleanup()
    }

    fun getCurrentFilePath(): String? = currentFile?.absolutePath

    private fun cleanup() {
        pollingJob?.cancel()
        pollingJob = null
        try {
            mediaRecorder?.apply {
                try { stop() } catch (ignored: Exception) {}
                try { reset() } catch (ignored: Exception) {}
                try { release() } catch (ignored: Exception) {}
            }
        } catch (ignored: Exception) {}
        mediaRecorder = null
        _recordingState.value = VoiceNoteState.IDLE
        _durationSeconds.value = 0
        _liveAmplitudes.value = emptyList()
        currentFile?.let {
            if (it.exists()) it.delete()
        }
        currentFile = null
    }

    fun release() {
        cleanup()
        coroutineScope.cancel()
    }
}
