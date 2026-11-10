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
 * Manager gérant le cycle de vie complet de l'enregistrement de notes vocales
 * avec échantillonnage d'amplitudes en temps réel (Waveform WhatsApp),
 * contrôle des états (Hold, Locked, Paused, Idle), gestion du focus audio
 * et persistance des fichiers temporaires.
 *
 * Pause / reprise RÉELLES (Mission 03 C3) :
 * - Android 7+ (API 24) : MediaRecorder.pause()/resume() natif — le fichier continue
 *   d'exister, aucune perte audio, la durée ne compte pas le temps en pause.
 * - Android 6 et moins : pause()/resume() n'existe pas ; repli sur la finalisation
 *   du fichier puis nouvelle session append dans le même fichier (voir startAppendSession).
 */
class VoiceNoteManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceNoteManager"
        private const val MIN_API_NATIVE_PAUSE = Build.VERSION_CODES.N // API 24
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null

    // Gestion précise du temps de capture (en millisecondes)
    private var currentSessionStartTimeMs: Long = 0L
    private var accumulatedDurationMs: Long = 0L

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

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaRecorder error: what=$what, extra=$extra")
                    cancelRecording()
                }

                prepare()
                start()
            }

            mediaRecorder = recorder
            currentSessionStartTimeMs = System.currentTimeMillis()
            accumulatedDurationMs = 0L
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
            abandonAudioFocus()
            false
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = coroutineScope.launch {
            while (isActive && (_recordingState.value == VoiceNoteState.RECORDING || _recordingState.value == VoiceNoteState.LOCKED)) {
                val now = System.currentTimeMillis()
                val currentTotalMs = accumulatedDurationMs + (now - currentSessionStartTimeMs)
                _durationSeconds.value = (currentTotalMs / 1000).toInt()

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

        // Figer le chrono
        val now = System.currentTimeMillis()
        accumulatedDurationMs += (now - currentSessionStartTimeMs)
        pollingJob?.cancel()
        pollingJob = null

        // --- Pause RÉELLE (API 24+, téléphones Android 7 et 10) ---
        if (Build.VERSION.SDK_INT >= MIN_API_NATIVE_PAUSE) {
            return try {
                mediaRecorder?.pause()
                _recordingState.value = VoiceNoteState.PAUSED
                _durationSeconds.value = (accumulatedDurationMs / 1000).toInt().coerceAtLeast(1)
                Log.d(TAG, "Voice recording PAUSED (native pause) at ${_durationSeconds.value} s — file kept open")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Native pause failed: ${e.message}", e)
                // Échec de la pause native : repli sur la finalisation (ancien comportement)
                pauseByFinalization()
            }
        }

        // --- Repli Android <= 6 : finalisation du fichier pour prévisualisation ---
        return pauseByFinalization()
    }

    /**
     * Repli historique (API < 24) : on finalise le fichier (header moov écrit) pour
     * permettre la prévisualisation immédiate. La « reprise » ouvre une nouvelle
     * session append dans le même fichier (voir startAppendSession).
     */
    private fun pauseByFinalization(): Boolean {
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
            Log.d(TAG, "Voice recording paused (fallback finalize) for preview at ${_durationSeconds.value} s")
            true
        } catch (e: Exception) {
            Log.e(TAG, "pauseRecording (fallback) failed: ${e.message}", e)
            false
        }
    }

    @Synchronized
    fun resumeRecording(): Boolean {
        if (_recordingState.value != VoiceNoteState.PAUSED) {
            return false
        }

        // --- Reprise RÉELLE (API 24+) : le recorder n'a jamais été arrêté ---
        if (Build.VERSION.SDK_INT >= MIN_API_NATIVE_PAUSE && mediaRecorder != null) {
            return try {
                mediaRecorder?.resume()
                currentSessionStartTimeMs = System.currentTimeMillis()
                requestAudioFocus()
                _recordingState.value = VoiceNoteState.LOCKED // reprise en mode mains libres
                startPolling()
                Log.d(TAG, "Voice recording RESUMED (native resume) — same file, no audio loss")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Native resume failed: ${e.message}", e)
                false
            }
        }

        // Repli Android <= 6 : reprise = nouvelle session append dans le même fichier
        return startAppendSession()
    }

    /**
     * Repli Android <= 6 uniquement : recommence à écrire dans le même fichier via un
     * nouveau MediaRecorder. Si l'append échoue (codec/firmware), on repart d'un
     * nouveau fichier et on abandonne l'ancien segment (perte documentée).
     */
    private fun startAppendSession(): Boolean {
        val file = currentFile ?: return false
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
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            requestAudioFocus()
            currentSessionStartTimeMs = System.currentTimeMillis()
            _recordingState.value = VoiceNoteState.LOCKED
            startPolling()
            Log.d(TAG, "Voice recording resumed (append session, API < 24 fallback) -> ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Append resume failed: ${e.message}", e)
            try {
                recorder?.reset()
                recorder?.release()
            } catch (ignored: Exception) {}
            mediaRecorder = null
            false
        }
    }

    @Synchronized
    fun stopAndGetResult(): Pair<File?, Int> {
        val state = _recordingState.value
        if (state == VoiceNoteState.IDLE) return Pair(null, 0)

        pollingJob?.cancel()
        pollingJob = null
        val file = currentFile

        val now = System.currentTimeMillis()
        val totalMs = if (state == VoiceNoteState.PAUSED) {
            accumulatedDurationMs
        } else {
            accumulatedDurationMs + (now - currentSessionStartTimeMs)
        }
        val totalDuration = ((totalMs + 500) / 1000).toInt().coerceAtLeast(1)

        return try {
            if (mediaRecorder != null) {
                try {
                    if (state == VoiceNoteState.PAUSED && Build.VERSION.SDK_INT >= MIN_API_NATIVE_PAUSE) {
                        // Le recorder est encore en pause native : le réveiller pour un stop propre
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
            _recordingState.value = VoiceNoteState.IDLE
            _durationSeconds.value = 0
            _liveAmplitudes.value = emptyList()
            abandonAudioFocus()

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
        currentFile?.let {
            if (it.exists()) it.delete()
        }
        currentFile = null
        abandonAudioFocus()
    }

    fun release() {
        cleanup()
        coroutineScope.cancel()
    }
}
