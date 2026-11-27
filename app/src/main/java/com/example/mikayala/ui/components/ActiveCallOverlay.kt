package com.example.mikayala.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.mikayala.call.CallSignaling
import com.example.mikayala.call.WebRtcCall
import kotlinx.coroutines.delay
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

/**
 * Écran d'appel avec VRAI son et vidéo (WebRTC pair à pair).
 * Appel entrant : Refuser / Décrocher. En cours : micro, haut-parleur, caméra, retourner la caméra, raccrocher.
 */
@Composable
fun ActiveCallOverlay(
    partnerName: String = "Mon partenaire",
    partnerAvatarUrl: String = "",
    isVideo: Boolean = true,
    isIncoming: Boolean = false,
    autoAccept: Boolean = false,
    resolveCallId: suspend () -> String? = { null },
    signaling: CallSignaling? = null,
    onDecline: (callId: String?) -> Unit = {},
    onEndCall: (durationSeconds: Int) -> Unit
) {
    val context = LocalContext.current
    val latestOnEnd by rememberUpdatedState(onEndCall)
    var accepted by remember { mutableStateOf(!isIncoming || autoAccept) }
    var callId by remember { mutableStateOf<String?>(null) }
    var callIdResolved by remember { mutableStateOf(false) }
    var call by remember { mutableStateOf<WebRtcCall?>(null) }
    var closing by remember { mutableStateOf(false) }

    fun close(duration: Int) {
        if (closing) return
        closing = true
        latestOnEnd(duration)
    }

    // Appel réduit : petite fenêtre flottante, le reste de l'application reste utilisable
    var minimized by remember { mutableStateOf(false) }
    // « Retour » réduit l'appel en cours (il ne se termine qu'avec le bouton rouge)
    BackHandler(enabled = !minimized) { if (accepted) minimized = true }

    LaunchedEffect(accepted) {
        if (accepted && isIncoming) {
            try { com.example.mikayala.util.NotificationHelper.dismissIncomingCallNotification(context) } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        callId = resolveCallId()
        callIdResolved = true
        if (callId == null) {
            Toast.makeText(context, "Appel impossible : vérifiez la connexion", Toast.LENGTH_LONG).show()
            delay(800)
            close(0)
        }
    }

    // Appel entrant pas encore décroché : se ferme si l'appelant raccroche
    LaunchedEffect(callId, accepted) {
        val id = callId ?: return@LaunchedEffect
        if (accepted || signaling == null) return@LaunchedEffect
        while (true) {
            delay(2000)
            val status = signaling.fetch(id)?.optString("status").orEmpty()
            if (status.isNotEmpty() && status != "ringing") {
                close(0)
                break
            }
        }
    }

    val needed = remember(isVideo) {
        if (isVideo) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        else arrayOf(Manifest.permission.RECORD_AUDIO)
    }
    var permissionsAsked by remember { mutableStateOf(false) }
    var audioGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        audioGranted = result[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!audioGranted) {
            Toast.makeText(context, "Autorise le micro pour pouvoir appeler", Toast.LENGTH_LONG).show()
            call?.hangUp() ?: run {
                if (isIncoming) onDecline(callId)
                close(0)
            }
        }
    }

    // Démarrage de l'appel réel dès que : décroché + identifiant connu + micro autorisé
    LaunchedEffect(accepted, callId, audioGranted) {
        val id = callId ?: return@LaunchedEffect
        if (!accepted || call != null || signaling == null) return@LaunchedEffect
        val missing = needed.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty() && !permissionsAsked) {
            permissionsAsked = true
            permissionLauncher.launch(missing.toTypedArray())
            return@LaunchedEffect
        }
        if (!audioGranted) return@LaunchedEffect
        val c = WebRtcCall(context, id, isCaller = !isIncoming, isVideo = isVideo, signaling = signaling, partnerName = partnerName)
        call = c
        c.start()
    }

    DisposableEffect(Unit) {
        onDispose { call?.disposeUi() }
    }

    val currentCall = call
    val state = currentCall?.state?.collectAsState()?.value
    val remoteVideo = currentCall?.remoteVideo?.collectAsState()?.value
    val localVideo = currentCall?.localVideo?.collectAsState()?.value
    val micOn = currentCall?.micOn?.collectAsState()?.value ?: true
    val speakerOn = currentCall?.speakerOn?.collectAsState()?.value ?: isVideo
    val cameraOn = currentCall?.cameraOn?.collectAsState()?.value ?: isVideo
    val frontCamera = currentCall?.frontCamera?.collectAsState()?.value ?: true

    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(state) {
        if (state == WebRtcCall.State.CONNECTED) {
            while (true) {
                seconds = currentCall?.durationSeconds() ?: 0
                delay(1000)
            }
        }
        if (state == WebRtcCall.State.ENDED || state == WebRtcCall.State.FAILED) {
            val reason = currentCall?.endReason.orEmpty()
            if (reason.isNotBlank() && reason != "Appel terminé") {
                Toast.makeText(context, reason, Toast.LENGTH_SHORT).show()
            }
            delay(600)
            close(currentCall?.durationSeconds() ?: seconds)
        }
    }

    val statusText = when {
        isIncoming && !accepted -> if (isVideo) "Appel vidéo entrant…" else "Appel vocal entrant…"
        !callIdResolved || state == null || state == WebRtcCall.State.PREPARING -> "Préparation…"
        state == WebRtcCall.State.RINGING -> "Sonnerie…"
        state == WebRtcCall.State.CONNECTING -> "Connexion…"
        state == WebRtcCall.State.CONNECTED -> formatDuration(seconds)
        state == WebRtcCall.State.FAILED -> "Connexion impossible"
        else -> "Appel terminé"
    }

    val bg = Color(0xFF15131A)
    if (minimized) {
        MinimizedCallBubble(
            showVideo = isVideo && remoteVideo != null && currentCall?.eglBase != null,
            remoteVideo = remoteVideo,
            egl = currentCall?.eglBase,
            partnerName = partnerName,
            partnerAvatarUrl = partnerAvatarUrl,
            statusText = statusText,
            onExpand = { minimized = false },
            onHangUp = {
                val c = currentCall
                if (c != null) c.hangUp() else close(0)
            }
        )
        return
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        val density = LocalDensity.current
        val maxWpx = with(density) { maxWidth.toPx() }
        val maxHpx = with(density) { maxHeight.toPx() }
        val egl = currentCall?.eglBase
        val showRemoteVideo = isVideo && remoteVideo != null && egl != null
        if (showRemoteVideo) {
            VideoRenderer(track = remoteVideo!!, egl = egl!!, mirror = false, overlay = false, modifier = Modifier.fillMaxSize())
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = if (showRemoteVideo) 16.dp else 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!showRemoteVideo) {
                Box(
                    modifier = Modifier.size(120.dp).clip(CircleShape).background(Color(0xFF2D2538)),
                    contentAlignment = Alignment.Center
                ) {
                    if (partnerAvatarUrl.isNotBlank()) {
                        coil.compose.AsyncImage(
                            model = partnerAvatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Rounded.Person, contentDescription = null, tint = Color(0xFFB9A6E8), modifier = Modifier.size(60.dp))
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            Text(partnerName, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text(statusText, fontSize = 15.sp, color = Color.White.copy(alpha = 0.75f))
        }

        // Réduire l'appel pour continuer à utiliser l'application
        if (accepted) {
            IconButton(
                onClick = { minimized = true },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 12.dp, top = 8.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Réduire l'appel", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }

        // Ma caméra en petit : se déplace avec le doigt n'importe où sur l'écran
        if (isVideo && localVideo != null && egl != null && cameraOn) {
            val pipW = with(density) { 104.dp.toPx() }
            val pipH = with(density) { 150.dp.toPx() }
            val margin = with(density) { 12.dp.toPx() }
            var pipOffset by remember { mutableStateOf(Offset(maxWpx - pipW - margin * 1.4f, with(density) { 110.dp.toPx() })) }
            Box(
                modifier = Modifier
                    .offset { IntOffset(pipOffset.x.roundToInt(), pipOffset.y.roundToInt()) }
                    .size(width = 104.dp, height = 150.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black)
                    .pointerInput(maxWpx, maxHpx) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            pipOffset = Offset(
                                (pipOffset.x + drag.x).coerceIn(margin, maxWpx - pipW - margin),
                                (pipOffset.y + drag.y).coerceIn(margin, maxHpx - pipH - margin)
                            )
                        }
                    }
            ) {
                VideoRenderer(track = localVideo, egl = egl, mirror = frontCamera, overlay = true, modifier = Modifier.fillMaxSize())
            }
        }

        // Commandes
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isIncoming && !accepted) {
                Row(horizontalArrangement = Arrangement.spacedBy(72.dp)) {
                    RoundCallButton(Icons.Rounded.CallEnd, "Refuser", Color(0xFFE5484D)) {
                        onDecline(callId)
                        close(0)
                    }
                    RoundCallButton(if (isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call, "Décrocher", Color(0xFF2FB463)) {
                        accepted = true
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    SmallCallButton(if (micOn) Icons.Rounded.Mic else Icons.Rounded.MicOff, if (micOn) "Micro" else "Muet", active = !micOn) {
                        currentCall?.toggleMic()
                    }
                    SmallCallButton(if (speakerOn) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeDown, "Haut-parleur", active = speakerOn) {
                        currentCall?.toggleSpeaker()
                    }
                    if (isVideo) {
                        SmallCallButton(if (cameraOn) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff, "Caméra", active = !cameraOn) {
                            currentCall?.toggleCamera()
                        }
                        SmallCallButton(Icons.Rounded.Cameraswitch, "Retourner", active = false) {
                            currentCall?.switchCamera()
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
                RoundCallButton(Icons.Rounded.CallEnd, "Raccrocher", Color(0xFFE5484D)) {
                    val c = currentCall
                    if (c != null) c.hangUp() else close(0)
                }
            }
        }
    }
}

/** Appel réduit : bulle flottante déplaçable (vidéo du partenaire ou photo + durée). Toucher = agrandir. */
@Composable
private fun MinimizedCallBubble(
    showVideo: Boolean,
    remoteVideo: VideoTrack?,
    egl: EglBase?,
    partnerName: String,
    partnerAvatarUrl: String,
    statusText: String,
    onExpand: () -> Unit,
    onHangUp: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val maxWpx = with(density) { maxWidth.toPx() }
        val maxHpx = with(density) { maxHeight.toPx() }
        val w = if (showVideo) 120.dp else 190.dp
        val h = if (showVideo) 176.dp else 64.dp
        val wPx = with(density) { w.toPx() }
        val hPx = with(density) { h.toPx() }
        val margin = with(density) { 12.dp.toPx() }
        var offset by remember { mutableStateOf(Offset(maxWpx - wPx - margin, with(density) { 120.dp.toPx() })) }
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .size(width = w, height = h)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF15131A))
                .pointerInput(maxWpx, maxHpx) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        offset = Offset(
                            (offset.x + drag.x).coerceIn(margin, maxWpx - wPx - margin),
                            (offset.y + drag.y).coerceIn(margin, maxHpx - hPx - margin)
                        )
                    }
                }
                .pointerInput(Unit) { detectTapGestures(onTap = { onExpand() }) }
        ) {
            if (showVideo && remoteVideo != null && egl != null) {
                VideoRenderer(track = remoteVideo, egl = egl, mirror = false, overlay = true, modifier = Modifier.fillMaxSize())
                Text(
                    statusText,
                    fontSize = 11.sp,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp)
                        .clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 6.dp, vertical = 2.dp)
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF2D2538)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (partnerAvatarUrl.isNotBlank()) {
                            coil.compose.AsyncImage(model = partnerAvatarUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Icon(Icons.Rounded.Person, contentDescription = null, tint = Color(0xFFB9A6E8), modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f).padding(end = 36.dp)) {
                        Text(partnerName, fontSize = 12.sp, color = Color.White, maxLines = 1, fontWeight = FontWeight.SemiBold)
                        Text(statusText, fontSize = 11.sp, color = Color(0xFF2FB463), maxLines = 1)
                    }
                }
            }
            // Raccrocher sans agrandir
            IconButton(
                onClick = onHangUp,
                modifier = Modifier
                    .align(if (showVideo) Alignment.BottomCenter else Alignment.CenterEnd)
                    .padding(if (showVideo) 6.dp else 0.dp)
                    .padding(end = if (showVideo) 0.dp else 8.dp)
                    .size(if (showVideo) 36.dp else 32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE5484D))
            ) {
                Icon(Icons.Rounded.CallEnd, contentDescription = "Raccrocher", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun RoundCallButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(72.dp).clip(CircleShape).background(color)
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun SmallCallButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (active) Color.White else Color.White.copy(alpha = 0.16f))
        ) {
            Icon(icon, contentDescription = label, tint = if (active) Color(0xFF15131A) else Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
    }
}

@Composable
private fun VideoRenderer(track: VideoTrack, egl: EglBase, mirror: Boolean, overlay: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val renderer = remember(egl) {
        SurfaceViewRenderer(context).apply {
            init(egl.eglBaseContext, null)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            setEnableHardwareScaler(true)
            setMirror(mirror)
            if (overlay) setZOrderMediaOverlay(true)
        }
    }
    DisposableEffect(track, renderer) {
        try { track.addSink(renderer) } catch (_: Exception) {}
        onDispose { try { track.removeSink(renderer) } catch (_: Exception) {} }
    }
    LaunchedEffect(renderer, mirror) { try { renderer.setMirror(mirror) } catch (_: Exception) {} }
    DisposableEffect(renderer) {
        onDispose { try { renderer.release() } catch (_: Exception) {} }
    }
    AndroidView(factory = { renderer }, modifier = modifier)
}

private fun formatDuration(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}
