package com.example.mikayala.utils

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
 * Cycle de vie complet de l'enregistrement de notes vocales (Mission 03).
 * Pause/reprise : API 24+ = MediaRecorder.pause()/resume() natif ;
 * API < 24 = segments temporaires assembles via AudioSegmentsMerger
 * (setOutputFile n'ajoute JAMAIS a un M4A existant : l'ancien repli corrompu est supprime).
 * Anti-fuite (Mission 04 B4) : instance active suivie statiquement.
 */
class VoiceNoteManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceNoteManager"
        private const val MIN_API_NATIVE_PAUSE = Build.VERSION_CODES.N

        @Volatile
        private var activeRecordingInstance: VoiceNoteManager? = null

        /** Coupe tout enregistrement en cours (appele par MainActivity.ON_STOP). */
        fun cancelAnyActiveRecording() {
            val instance = activeRecordingInstance
            if (instance != null && instance.recordingState.value != VoiceNoteState.IDLE) {
                Log.w(TAG, "Enregistrement actif coupe (application en arriere-plan)")
                instance.cancelRecording()
            }
            activeRecordingInstance = null
        }
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private var mediaRecorder: MediaRecorder? = null

    // Fichier FINAL (fusion des segments sur API < 24, fichier direct sur API 24+)
    private var currentFile: File? = null

    // Segments temporaires (API < 24) : une partie par periode entre pause et reprise
    private val segmentFiles = mutableListOf<File>()
    private var usingSegments = false

    private var currentSessionStartTimeMs: Long = 0L
    private var accumulatedDurationMs: Long = 0L

    private val _recordingState = MutableStateFlow(VoiceNoteState.IDLE)
    val recordingState: StateFlow<VoiceNoteState> = _recordingState.asStateFlow()

    private val _durationSeconds = MutableStateFlow(0)
    val durationSeconds: StateFlow<Int> = _durationSeconds.asStateFlow()

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

    private fun requestAudioFocus(): Boolean {
        return try {
            if (audioManager == null) return true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(playbackAttributes)
                    .setOnAudioFocusChangeListener { focusChange ->
                        when (focusChange) {
                            AudioManager.AUDIOFOCUS_LOSS,
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                                Log.w(TAG, "Audio focus lost during recording ($focusChange)")
                                if (_recordingState.value == VoiceNoteState.RECORDING ||
                                    _recordingState.value == VoiceNoteState.LOCKED
                                ) {
                                    pauseRecording()
                                }
                            }
                        }
                    }
                    .build()
                audioFocusRequest = request
                audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    { focusChange ->
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                            pauseRecording()
                        }
                    },
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request audio focus: ${e.message}")
            true
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
            audioFocusRequest = null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to abandon audio focus: ${e.message}")
        }
    }

    @Synchronized
    fun startRecording(): Boolean {
        if (!hasRecordPermission()) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            return false
        }

        cleanup()
        requestAudioFocus()

        usingSegments = Build.VERSION.SDK_INT < MIN_API_NATIVE_PAUSE
        if (!usingSegments) {
            val tempFile = File(context.cacheDir, "voice_note_${System.currentTimeMillis()}.m4a")
            if (tempFile.exists()) tempFile.delete()
            currentFile = tempFile
        } else {
            currentFile = File(context.cacheDir, "voice_note_${System.currentTimeMillis()}.m4a")
            segmentFiles.clear()
        }

        val started = startNewSegmentRecorder()
        if (!started) {
            cleanup()
            abandonAudioFocus()
            return false
        }

        currentSessionStartTimeMs = System.currentTimeMillis()
        accumulatedDurationMs = 0L
        _durationSeconds.value = 0
        _liveAmplitudes.value = List(18) { 0.12f }
        _recordingState.value = VoiceNoteState.RECORDING
        activeRecordingInstance = this
        startPolling()
        Log.d(TAG, "Voice recording started (segments=$usingSegments)")
        return true
    }

    /**
     * Ouvre un recorder : fichier final unique (API 24+) ou NOUVEAU segment
     * temporaire (API < 24). Jamais setOutputFile sur un M4A deja ecrit.
     */
    private fun startNewSegmentRecorder(): Boolean {
        val targetFile: File = if (usingSegments) {
            File(context.cacheDir, "voice_note_seg_${System.currentTimeMillis()}_${segmentFiles.size}.m4a")
        } else {
            currentFile ?: return false
        }

        var recorder: MediaRecorder? = null
        return try {
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128000)
            recorder.setAudioSamplingRate(44100)
            recorder.setOutputFile(targetFile.absolutePath)
            recorder.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaRecorder error: what=$what, extra=$extra")
                cancelRecording()
            }
            recorder.prepare()
            recorder.start()
            mediaRecorder = recorder
            if (usingSegments) segmentFiles.add(targetFile)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recorder segment: ${e.message}", e)
            try {
                recorder?.reset()
                recorder?.release()
            } catch (ignored: Exception) {}
            mediaRecorder = null
            if (usingSegments) targetFile.delete()
            false
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = coroutineScope launch@{
            while (isActive && (_recordingState.value == VoiceNoteState.RECORDING || _recordingState.value == VoiceNoteState.LOCKED)) {
                val now = System.currentTimeMillis()
                val currentTotalMs = accumulatedDurationMs + (now - currentSessionStartTimeMs)
                _durationSeconds.value = (currentTotalMs / 1000).toInt()

                val rawAmp = try {
                    mediaRecorder?.maxAmplitude ?: 0
                } catch (e: Exception) {
                    0
                }

                val normalized = (rawAmp / 32767f).coerceIn(0f, 1f)
                val boosted = (normalized.toDouble().pow(0.55).toFloat()).coerceIn(0.08f, 1.0f)

                val current = _liveAmplitudes.value
                val updated = (if (current.size >= 24) current.drop(1) else current) + boosted
                _liveAmplitudes.value = updated

                delay(70)
            }
        }
    }

    @Synchronized
    fun lockRecording() {
        if (_recordingState.value == VoiceNoteState.RECORDING) {
            _recordingState.value = VoiceNoteState.LOCKED
            Log.d(TAG, "Voice recording locked into hands-free mode")
        }
    }

    @Synchronized
    fun pauseRecording(): Boolean {
        val state = _recordingState.value
        if (state != VoiceNoteState.RECORDING && state != VoiceNoteState.LOCKED) {
            return false
        }

        accumulatedDurationMs += System.currentTimeMillis() - currentSessionStartTimeMs
        pollingJob?.cancel()
        pollingJob = null

        // Pause REELLE (API 24+)
        if (!usingSegments) {
            return try {
                mediaRecorder?.pause()
                _recordingState.value = VoiceNoteState.PAUSED
                _durationSeconds.value = (accumulatedDurationMs / 1000).toInt().coerceAtLeast(1)
                Log.d(TAG, "Voice recording PAUSED (native) at ${_durationSeconds.value} s")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Native pause failed: ${e.message}", e)
                _recordingState.value = VoiceNoteState.RECORDING
                false
            }
        }

        // Repli API < 24 : finaliser CE segment (lisible, pret pour la fusion)
        return try {
            try {
                mediaRecorder?.stop()
            } catch (ignored: Exception) {}
            try {
                mediaRecorder?.reset()
                mediaRecorder?.release()
            } catch (ignored: Exception) {}
            mediaRecorder = null
            _recordingState.value = VoiceNoteState.PAUSED
            _durationSeconds.value = (accumulatedDurationMs / 1000).toInt().coerceAtLeast(1)
            abandonAudioFocus()
            Log.d(TAG, "Voice recording paused (segment finalized, count=${segmentFiles.size})")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Segment pause failed: ${e.message}", e)
            false
        }
    }

    @Synchronized
    fun resumeRecording(): Boolean {
        if (_recordingState.value != VoiceNoteState.PAUSED) {
            return false
        }

        // Reprise REELLE (API 24+)
        if (!usingSegments && mediaRecorder != null) {
            return try {
                mediaRecorder?.resume()
                currentSessionStartTimeMs = System.currentTimeMillis()
                requestAudioFocus()
                _recordingState.value = VoiceNoteState.LOCKED
                startPolling()
                Log.d(TAG, "Voice recording RESUMED (native) — same file, no audio loss")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Native resume failed: ${e.message}", e)
                false
            }
        }

        // Repli API < 24 : NOUVEAU segment
        val started = startNewSegmentRecorder()
        if (started) {
            requestAudioFocus()
            currentSessionStartTimeMs = System.currentTimeMillis()
            _recordingState.value = VoiceNoteState.LOCKED
            startPolling()
            Log.d(TAG, "Voice recording RESUMED in new segment #${segmentFiles.size}")
        }
        return started
    }

    @Synchronized
    fun stopAndGetResult(): Pair<File?, Int> {
        val state = _recordingState.value
        if (state == VoiceNoteState.IDLE) return Pair(null, 0)

        pollingJob?.cancel()
        pollingJob = null
        val file = currentFile

        val totalMs = if (state == VoiceNoteState.PAUSED) {
            accumulatedDurationMs
        } else {
            accumulatedDurationMs + (System.currentTimeMillis() - currentSessionStartTimeMs)
        }
        val totalDuration = ((totalMs + 500) / 1000).toInt().coerceAtLeast(1)

        return try {
            if (mediaRecorder != null) {
                try {
                    if (state == VoiceNoteState.PAUSED && !usingSegments) {
                        try { mediaRecorder?.resume() } catch (ignored: Exception) {}
                    }
                    mediaRecorder?.stop()
                } catch (ignored: Exception) {}
                try {
                    mediaRecorder?.reset()
                    mediaRecorder?.release()
                } catch (ignored: Exception) {}
                mediaRecorder = null
            }

            // Fusion des segments (API < 24) en un M4A unique
            if (usingSegments && segmentFiles.isNotEmpty()) {
                val target = file
                if (target != null) {
                    val ok = AudioSegmentsMerger.mergeM4aSegments(segmentFiles, target)
                    segmentFiles.forEach { it.delete() }
                    segmentFiles.clear()
                    if (!ok) {
                        Log.e(TAG, "Segment merge FAILED — voice note not produced")
                        target.delete()
                        currentFile = null
                        _recordingState.value = VoiceNoteState.IDLE
                        _durationSeconds.value = 0
                        _liveAmplitudes.value = emptyList()
                        abandonAudioFocus()
                        if (activeRecordingInstance == this) activeRecordingInstance = null
                        return Pair(null, 0)
                    }
                } else {
                    segmentFiles.forEach { it.delete() }
                    segmentFiles.clear()
                }
            }

            _recordingState.value = VoiceNoteState.IDLE
            _durationSeconds.value = 0
            _liveAmplitudes.value = emptyList()
            abandonAudioFocus()
            if (activeRecordingInstance == this) activeRecordingInstance = null

            if (file != null && file.exists() && file.length() > 0) {
                Log.d(TAG, "Voice recording finished: duration=$totalDuration s, size=${file.length()} bytes, segments=$usingSegments")
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

    @Synchronized
    fun cancelRecording() {
        cleanup()
    }

    fun getCurrentFilePath(): String? = currentFile?.absolutePath

    @Synchronized
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
        segmentFiles.forEach { it.delete() }
        segmentFiles.clear()
        currentFile?.let { if (it.exists()) it.delete() }
        currentFile = null
        if (activeRecordingInstance == this) activeRecordingInstance = null
        abandonAudioFocus()
    }

    fun release() {
        cleanup()
        coroutineScope.cancel()
    }
}
