package com.example.mikayala.call

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Lecture / écriture de la ligne d'un appel (table call_signals). */
interface CallSignaling {
    suspend fun fetch(callId: String): JSONObject?
    suspend fun update(callId: String, fields: JSONObject): Boolean
    /** Serveurs relais (TURN) temporaires, au format JSON { "iceServers": ... } ; null si indisponibles. */
    suspend fun iceServers(): String? = null
}

/**
 * Appel audio/vidéo réel entre les deux téléphones (WebRTC, pair à pair).
 *
 * Signalisation volontairement simple et robuste, sans serveur supplémentaire :
 * - l'appelant crée l'offre, attend la fin de la collecte réseau (ICE) puis l'écrit dans call_signals.offer_sdp ;
 * - l'appelé, quand il décroche, lit l'offre, crée la réponse et l'écrit dans answer_sdp (statut « active ») ;
 * - chacun surveille ensuite le statut (raccroché, refusé, annulé).
 * Les adresses réseau sont incluses dans les descriptions de session : aucun échange de candidats séparé.
 */
class WebRtcCall(
    context: Context,
    private val callId: String,
    private val isCaller: Boolean,
    val isVideo: Boolean,
    private val signaling: CallSignaling,
    private val partnerName: String = ""
) {
    enum class State { PREPARING, RINGING, CONNECTING, CONNECTED, ENDED, FAILED }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(State.PREPARING)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Raison lisible de la fin de l'appel (affichée brièvement). */
    @Volatile var endReason: String = ""
        private set

    private val _remoteVideo = MutableStateFlow<VideoTrack?>(null)
    val remoteVideo: StateFlow<VideoTrack?> = _remoteVideo.asStateFlow()
    private val _localVideo = MutableStateFlow<VideoTrack?>(null)
    val localVideo: StateFlow<VideoTrack?> = _localVideo.asStateFlow()

    private val _micOn = MutableStateFlow(true)
    val micOn: StateFlow<Boolean> = _micOn.asStateFlow()
    private val _cameraOn = MutableStateFlow(isVideo)
    val cameraOn: StateFlow<Boolean> = _cameraOn.asStateFlow()
    private val _speakerOn = MutableStateFlow(isVideo)
    val speakerOn: StateFlow<Boolean> = _speakerOn.asStateFlow()

    /** Début de la communication (ms), 0 tant que ce n'est pas connecté. */
    @Volatile var connectedAt: Long = 0L
        private set

    val eglBase: EglBase = EglBase.create()

    private var factory: PeerConnectionFactory? = null
    private var peer: PeerConnection? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var capturer: CameraVideoCapturer? = null
    private var textureHelper: SurfaceTextureHelper? = null
    private val gatheringDone = CompletableDeferred<Unit>()
    private var mainJob: Job? = null
    @Volatile private var finished = false

    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousAudioMode = AudioManager.MODE_NORMAL

    fun start() {
        CallForegroundService.start(appContext, this, partnerName)
        mainJob = scope.launch {
            try {
                setupMedia()
                if (isCaller) runCaller() else runCallee()
                monitorUntilEnd()
            } catch (e: kotlinx.coroutines.CancellationException) {
                // fin normale
            } catch (e: Exception) {
                Log.e(TAG, "Appel en échec : ${e.message}", e)
                endReason = "Connexion impossible"
                finish(State.FAILED, remoteStatus = "ended")
            }
        }
    }

    // ---------------------------------------------------------------- médias

    private suspend fun setupMedia() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions()
        )
        val adm = JavaAudioDeviceModule.builder(appContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        val f = PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
        factory = f

        val relays = fetchRelayServers()
        val iceServers = listOf(
            PeerConnection.IceServer.builder(listOf("stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302")).createIceServer(),
            PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer()
        ) + relays
        Log.i(TAG, "Serveurs relais TURN : ${relays.size}")
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
        }
        peer = f.createPeerConnection(config, observer) ?: throw IllegalStateException("PeerConnection indisponible")

        val audioSource = f.createAudioSource(MediaConstraints())
        audioTrack = f.createAudioTrack("mik_audio", audioSource).also { peer?.addTrack(it, listOf("mik")) }

        if (isVideo) startCamera(f)

        previousAudioMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = _speakerOn.value
    }

    /**
     * Relais TURN (Cloudflare, via la fonction Supabase turn-credentials). Indispensable en 4G :
     * les opérateurs bloquent souvent la connexion directe entre deux téléphones.
     * En cas d'échec, l'appel tente quand même la connexion directe.
     */
    private suspend fun fetchRelayServers(): List<PeerConnection.IceServer> {
        val raw = try {
            withTimeoutOrNull(5_000L) { signaling.iceServers() }
        } catch (e: Exception) {
            Log.w(TAG, "Relais TURN indisponible : ${e.message}"); null
        } ?: return emptyList()
        return try { parseIceServers(raw) } catch (e: Exception) {
            Log.w(TAG, "Relais TURN illisible : ${e.message}"); emptyList()
        }
    }

    private fun parseIceServers(raw: String): List<PeerConnection.IceServer> {
        val root = JSONObject(raw)
        val node = root.opt("iceServers") ?: return emptyList()
        val entries = when (node) {
            is org.json.JSONArray -> (0 until node.length()).mapNotNull { node.optJSONObject(it) }
            is JSONObject -> listOf(node)
            else -> emptyList()
        }
        return entries.mapNotNull { e ->
            val urls = when (val u = e.opt("urls")) {
                is org.json.JSONArray -> (0 until u.length()).map { u.optString(it) }
                is String -> listOf(u)
                else -> emptyList()
            }.filter { it.startsWith("turn:") || it.startsWith("turns:") || it.startsWith("stun:") }
            if (urls.isEmpty()) return@mapNotNull null
            val b = PeerConnection.IceServer.builder(urls)
            val user = e.optString("username")
            val pass = e.optString("credential")
            if (user.isNotBlank() && pass.isNotBlank()) b.setUsername(user).setPassword(pass)
            b.createIceServer()
        }
    }

    private fun startCamera(f: PeerConnectionFactory) {
        try {
            val enumerator = Camera2Enumerator(appContext)
            val name = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                ?: enumerator.deviceNames.firstOrNull() ?: return
            val cap = enumerator.createCapturer(name, null) ?: return
            val helper = SurfaceTextureHelper.create("mik_capture", eglBase.eglBaseContext)
            val source = f.createVideoSource(false)
            cap.initialize(helper, appContext, source.capturerObserver)
            cap.startCapture(640, 480, 24)
            val track = f.createVideoTrack("mik_video", source)
            peer?.addTrack(track, listOf("mik"))
            capturer = cap; textureHelper = helper; videoSource = source
            _localVideo.value = track
        } catch (e: Exception) {
            Log.w(TAG, "Caméra indisponible : ${e.message}")
            _cameraOn.value = false
        }
    }

    // ------------------------------------------------------------ signalisation

    private suspend fun runCaller() {
        _state.value = State.RINGING
        val offer = createSdp(offer = true)
        setSdp(local = true, desc = offer)
        awaitGathering()
        val sdp = peer?.localDescription?.description ?: offer.description
        if (!signaling.update(callId, JSONObject().put("offer_sdp", sdp))) {
            // Une nouvelle tentative, puis échec franc
            delay(1000)
            if (!signaling.update(callId, JSONObject().put("offer_sdp", sdp))) throw IllegalStateException("offre non enregistrée")
        }
        // Attente de la réponse (60 s de sonnerie)
        val deadline = System.currentTimeMillis() + 60_000L
        while (!finished && System.currentTimeMillis() < deadline) {
            val row = signaling.fetch(callId)
            val status = row?.optString("status").orEmpty()
            if (status in END_STATUSES) {
                endReason = if (status == "declined") "Appel refusé" else "Appel terminé"
                finish(State.ENDED, remoteStatus = null)
                return
            }
            val answer = row?.optString("answer_sdp").orEmpty().takeIf { it.isNotBlank() && it != "null" }
            if (answer != null) {
                _state.value = State.CONNECTING
                setSdp(local = false, desc = SessionDescription(SessionDescription.Type.ANSWER, answer))
                return
            }
            delay(1000)
        }
        if (!finished) {
            endReason = "Pas de réponse"
            finish(State.ENDED, remoteStatus = "missed")
        }
    }

    private suspend fun runCallee() {
        _state.value = State.CONNECTING
        // L'offre peut arriver quelques secondes après la sonnerie
        var offer: String? = null
        val deadline = System.currentTimeMillis() + 25_000L
        while (!finished && offer == null && System.currentTimeMillis() < deadline) {
            val row = signaling.fetch(callId)
            val status = row?.optString("status").orEmpty()
            if (status in END_STATUSES) {
                endReason = "Appel terminé"
                finish(State.ENDED, remoteStatus = null)
                return
            }
            offer = row?.optString("offer_sdp").orEmpty().takeIf { it.isNotBlank() && it != "null" }
            if (offer == null) delay(700)
        }
        if (offer == null) {
            endReason = "Connexion impossible"
            finish(State.FAILED, remoteStatus = "ended")
            return
        }
        setSdp(local = false, desc = SessionDescription(SessionDescription.Type.OFFER, offer))
        val answer = createSdp(offer = false)
        setSdp(local = true, desc = answer)
        awaitGathering()
        val sdp = peer?.localDescription?.description ?: answer.description
        val ok = signaling.update(
            callId,
            JSONObject().put("answer_sdp", sdp).put("status", "active").put("answered_at", System.currentTimeMillis())
        )
        if (!ok) throw IllegalStateException("réponse non enregistrée")
    }

    /** Surveille la fin de l'appel côté partenaire, et l'échec de connexion. */
    private suspend fun monitorUntilEnd() {
        val connectDeadline = System.currentTimeMillis() + 30_000L
        while (!finished) {
            delay(2000)
            if (finished) break
            val row = signaling.fetch(callId)
            val status = row?.optString("status").orEmpty()
            if (status in END_STATUSES) {
                endReason = "Appel terminé"
                finish(State.ENDED, remoteStatus = null)
                break
            }
            if (connectedAt == 0L && System.currentTimeMillis() > connectDeadline) {
                endReason = "Connexion impossible (réseau)"
                finish(State.FAILED, remoteStatus = "ended")
                break
            }
        }
    }

    private suspend fun awaitGathering() {
        // Collecte des adresses réseau : 5 s au plus (ce qui est déjà trouvé suffit en général)
        withTimeoutOrNull(5_000L) { gatheringDone.await() }
    }

    private suspend fun createSdp(offer: Boolean): SessionDescription = suspendCancellableCoroutine { cont ->
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", isVideo.toString()))
        }
        val obs = object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) { if (cont.isActive) cont.resume(desc) }
            override fun onCreateFailure(error: String?) { if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "createSdp")) }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }
        val pc = peer
        if (pc == null) cont.resumeWithException(IllegalStateException("pas de connexion"))
        else if (offer) pc.createOffer(obs, constraints) else pc.createAnswer(obs, constraints)
    }

    private suspend fun setSdp(local: Boolean, desc: SessionDescription): Unit = suspendCancellableCoroutine { cont ->
        val obs = object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {}
            override fun onCreateFailure(error: String?) {}
            override fun onSetSuccess() { if (cont.isActive) cont.resume(Unit) }
            override fun onSetFailure(error: String?) { if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "setSdp")) }
        }
        val pc = peer
        if (pc == null) cont.resumeWithException(IllegalStateException("pas de connexion"))
        else if (local) pc.setLocalDescription(obs, desc) else pc.setRemoteDescription(obs, desc)
    }

    private val observer = object : PeerConnection.Observer {
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
            if (state == PeerConnection.IceGatheringState.COMPLETE) gatheringDone.complete(Unit)
        }
        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
            when (newState) {
                PeerConnection.PeerConnectionState.CONNECTED -> {
                    if (connectedAt == 0L) connectedAt = System.currentTimeMillis()
                    _state.value = State.CONNECTED
                }
                PeerConnection.PeerConnectionState.FAILED -> {
                    endReason = "Connexion perdue"
                    scope.launch { finish(State.FAILED, remoteStatus = "ended") }
                }
                else -> {}
            }
        }
        override fun onTrack(transceiver: RtpTransceiver?) {
            val track = transceiver?.receiver?.track()
            if (track is VideoTrack) _remoteVideo.value = track
        }
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
            val track = receiver?.track()
            if (track is VideoTrack) _remoteVideo.value = track
        }
        override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceCandidate(candidate: IceCandidate?) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        override fun onAddStream(stream: MediaStream?) {}
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onDataChannel(channel: org.webrtc.DataChannel?) {}
        override fun onRenegotiationNeeded() {}
    }

    // ------------------------------------------------------------- commandes

    fun toggleMic() {
        val on = !_micOn.value
        audioTrack?.setEnabled(on)
        _micOn.value = on
    }

    fun toggleSpeaker() {
        val on = !_speakerOn.value
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = on
        _speakerOn.value = on
    }

    fun toggleCamera() {
        val track = _localVideo.value ?: return
        val on = !_cameraOn.value
        track.setEnabled(on)
        _cameraOn.value = on
    }

    fun switchCamera() {
        try { capturer?.switchCamera(null) } catch (_: Exception) {}
    }

    /** Raccrocher (ou annuler si personne n'a encore répondu). */
    fun hangUp() {
        scope.launch {
            val status = if (isCaller && connectedAt == 0L && _state.value == State.RINGING) "cancelled" else "ended"
            finish(State.ENDED, remoteStatus = status)
        }
    }

    fun durationSeconds(): Int = if (connectedAt == 0L) 0 else ((System.currentTimeMillis() - connectedAt) / 1000L).toInt()

    @Synchronized
    private fun markFinished(): Boolean {
        if (finished) return false
        finished = true
        return true
    }

    private suspend fun finish(final: State, remoteStatus: String?) {
        if (!markFinished()) return
        CallForegroundService.stop(appContext, this)
        if (remoteStatus != null) {
            try {
                signaling.update(callId, JSONObject().put("status", remoteStatus).put("ended_at", System.currentTimeMillis()))
            } catch (_: Exception) {}
        }
        release()
        _state.value = final
    }

    private fun release() {
        // D'abord détacher les vidéos de l'écran, ensuite libérer
        _localVideo.value = null
        _remoteVideo.value = null
        try { capturer?.stopCapture() } catch (_: Exception) {}
        try { capturer?.dispose() } catch (_: Exception) {}
        try { videoSource?.dispose() } catch (_: Exception) {}
        try { textureHelper?.dispose() } catch (_: Exception) {}
        try { peer?.close() } catch (_: Exception) {}
        try { peer?.dispose() } catch (_: Exception) {}
        try { factory?.dispose() } catch (_: Exception) {}
        capturer = null; videoSource = null; textureHelper = null; peer = null; factory = null
        try {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = previousAudioMode
        } catch (_: Exception) {}
    }

    /** Libère l'EGL une fois l'écran d'appel fermé (les vues vidéo l'utilisent jusque-là). */
    fun disposeUi() {
        if (!finished) hangUp()
        scope.launch {
            delay(1500)
            try { eglBase.release() } catch (_: Exception) {}
            scope.cancel()
        }
    }

    companion object {
        private const val TAG = "MikayalaCall"
        val END_STATUSES = setOf("ended", "declined", "cancelled", "missed")
    }
}
