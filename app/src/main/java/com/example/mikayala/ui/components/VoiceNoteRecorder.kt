package com.example.mikayala.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mikayala.theme.*
import com.example.mikayala.ui.icons.AppleSvgIcons
import com.example.mikayala.utils.VoiceNoteManager
import com.example.mikayala.utils.VoiceNoteState
import java.io.File

/**
 * Barre de saisie et enregistreur de note vocale reproduisant fidèlement
 * l'ergonomie gestuelle de WhatsApp :
 * 1. Appui long sur le micro pour démarrer instantanément
 * 2. Glisser vers la gauche pour annuler
 * 3. Glisser vers le haut pour verrouiller en mode mains libres (Pause, Supprimer, Envoyer)
 * 4. Relâcher pour envoyer automatiquement
 * 5. Waveform animée en temps réel avec le niveau sonore
 */
@Composable
fun VoiceNoteRecorder(
    manager: VoiceNoteManager,
    textInput: String,
    onTextInputChange: (String) -> Unit,
    onSendText: () -> Unit,
    onSendVoiceNote: (File, Int) -> Unit,
    onRequestRecordPermission: () -> Unit,
    hasRecordPermission: () -> Boolean,
    onAttachmentClick: () -> Unit,
    onCameraClick: () -> Unit,
    onEmojiClick: () -> Unit,
    showEmojiBar: Boolean,
    modifier: Modifier = Modifier,
    /** Paramètres > Discussions > « Touche Entrée pour envoyer ». */
    enterIsSend: Boolean = false
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val recordingState by manager.recordingState.collectAsState()
    val durationSeconds by manager.durationSeconds.collectAsState()
    val liveAmplitudes by manager.liveAmplitudes.collectAsState()

    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val cancelThresholdPx = remember(density) { with(density) { 120.dp.toPx() } }
    val lockThresholdPx = remember(density) { with(density) { 85.dp.toPx() } }

    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val currentOnSendVoiceNote by rememberUpdatedState(onSendVoiceNote)
    val currentOnSendText by rememberUpdatedState(onSendText)
    val currentHasRecordPermission by rememberUpdatedState(hasRecordPermission)
    val currentOnRequestRecordPermission by rememberUpdatedState(onRequestRecordPermission)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Main Container (Capsule normale OU Barre d'enregistrement)
        Surface(
            color = MikayalaColors.surfaceElevated,
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(
                1.dp,
                if (recordingState == VoiceNoteState.RECORDING) MikayalaColors.accent.copy(alpha = 0.6f)
                else MikayalaColors.border
            ),
            modifier = Modifier.weight(1f)
        ) {
            when (recordingState) {
                VoiceNoteState.IDLE -> {
                    // ==========================================
                    // 1. ÉTAT NORMAL (Saisie de message texte)
                    // ==========================================
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onEmojiClick,
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("chat_emoji_button")
                        ) {
                            Icon(
                                imageVector = AppleSvgIcons.Emoji,
                                contentDescription = "Emojis",
                                tint = if (showEmojiBar) MikayalaColors.secondary else TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        TextField(
                            value = textInput,
                            onValueChange = onTextInputChange,
                            placeholder = { Text("Message", color = TextMuted, fontSize = 15.sp) },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = if (enterIsSend) androidx.compose.ui.text.input.ImeAction.Send else androidx.compose.ui.text.input.ImeAction.Default
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onSend = { if (textInput.isNotBlank()) currentOnSendText() }
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = MikayalaColors.accent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_message_input"),
                            maxLines = 4
                        )

                        IconButton(
                            onClick = onAttachmentClick,
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("chat_attachment_trombone_button")
                        ) {
                            Icon(
                                imageVector = AppleSvgIcons.Paperclip,
                                contentDescription = "Pièces jointes et jeux",
                                tint = TextSecondary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .rotate(-35f)
                            )
                        }

                        IconButton(
                            onClick = onCameraClick,
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("chat_camera_quick_button")
                        ) {
                            Icon(
                                imageVector = AppleSvgIcons.Camera,
                                contentDescription = "Caméra",
                                tint = MikayalaColors.accent,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }

                VoiceNoteState.RECORDING -> {
                    val c = MikayalaColors
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .graphicsLayer {
                                    scaleX = pulseScale
                                    scaleY = pulseScale
                                    alpha = pulseAlpha
                                }
                                .clip(CircleShape)
                                .background(c.danger)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = String.format("%02d:%02d", durationSeconds / 60, durationSeconds % 60),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = c.textPrimary
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val bars = liveAmplitudes.takeLast(16)
                            bars.forEach { amp ->
                                val barHeight = (6 + (amp * 16)).coerceIn(4f, 22f).dp
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(barHeight)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(c.danger)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.graphicsLayer {
                                translationX = dragOffsetX.coerceIn(-cancelThresholdPx, 0f)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = null,
                                tint = c.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Annuler",
                                fontSize = 11.sp,
                                color = c.textSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                VoiceNoteState.LOCKED, VoiceNoteState.PAUSED -> {
                    // ==========================================
                    // 3. ÉTAT VERROUILLÉ / PAUSE (Mains libres)
                    // ==========================================
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Bouton Supprimer / Corbeille
                        IconButton(
                            onClick = {
                                manager.cancelRecording()
                                Toast.makeText(context, "Enregistrement annulé", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = AppleSvgIcons.Trash,
                                contentDescription = "Supprimer",
                                tint = MikayalaColors.danger,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Timer
                        Text(
                            text = String.format("%02d:%02d", durationSeconds / 60, durationSeconds % 60),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Bouton Pause / Reprendre
                        IconButton(
                            onClick = {
                                if (recordingState == VoiceNoteState.PAUSED) {
                                    manager.resumeRecording()
                                } else {
                                    manager.pauseRecording()
                                }
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(HoverStateCyan)
                        ) {
                            Icon(
                                imageVector = if (recordingState == VoiceNoteState.PAUSED) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                contentDescription = if (recordingState == VoiceNoteState.PAUSED) "Reprendre" else "Pause",
                                tint = VibrantCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Waveform ou preview
                        if (recordingState == VoiceNoteState.PAUSED) {
                            Box(modifier = Modifier.weight(1f)) {
                                VoicePlayerWaveform(
                                    durationSeconds = durationSeconds.coerceAtLeast(1),
                                    isSender = true,
                                    audioUrl = manager.getCurrentFilePath()
                                )
                            }
                        } else {
                            // Waveform active en direct
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(20.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val bars = liveAmplitudes.takeLast(16)
                                bars.forEach { amp ->
                                    val barHeight = (6 + (amp * 14)).coerceIn(4f, 20f).dp
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(barHeight)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(VibrantCyan)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // ==========================================
        // 4. BOUTON D'ACTION PRINCIPAL & CADENAS
        // ==========================================
        Box(contentAlignment = Alignment.BottomCenter) {
            val isLockThresholdMet = dragOffsetY < -lockThresholdPx

            // Cadenas flottant au-dessus du micro pendant l'appui
            androidx.compose.animation.AnimatedVisibility(
                visible = recordingState == VoiceNoteState.RECORDING,
                enter = fadeIn(tween(150)) + slideInVertically(tween(180)) { it / 3 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(150)) { it / 3 }
            ) {
                val c = MikayalaColors
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = c.modal,
                    border = BorderStroke(
                        0.5.dp,
                        if (isLockThresholdMet) c.accent else c.borderStrong
                    ),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .graphicsLayer { translationY = with(density) { (-62).dp.toPx() } }
                        .padding(bottom = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (isLockThresholdMet) AppleSvgIcons.LockOpen else AppleSvgIcons.Lock,
                            contentDescription = "Verrouiller",
                            tint = if (isLockThresholdMet) c.accent else c.secondary,
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer {
                                    val s = if (isLockThresholdMet) 1.15f else 1f
                                    scaleX = s
                                    scaleY = s
                                }
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isLockThresholdMet) "Relâcher" else "Verrouiller",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isLockThresholdMet) c.accent else c.textSecondary
                        )
                    }
                }
            }

            // Bouton Circulaire (Micro ou Envoyer)
            Surface(
                shape = CircleShape,
                color = Color.Transparent,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("chat_send_or_mic_button")
                    // Gestion gestuelle WhatsApp (Hold & Drag) quand il n'y a pas de texte tapé
                    .then(
                        if (textInput.isBlank()) {
                            Modifier.pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val state = manager.recordingState.value

                                    // 1. Si en mode verrouillé ou en pause : le bouton rond est le bouton Envoyer (tap simple)
                                    if (state == VoiceNoteState.LOCKED || state == VoiceNoteState.PAUSED) {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) {
                                                val (file, secs) = manager.stopAndGetResult()
                                                if (file != null && file.length() > 0) {
                                                    currentOnSendVoiceNote(file, secs)
                                                } else {
                                                    Toast.makeText(context, "Erreur d'enregistrement", Toast.LENGTH_SHORT).show()
                                                }
                                                break
                                            }
                                        }
                                        return@awaitEachGesture
                                    }

                                    // Si déjà en cours d'enregistrement (pour éviter double démarrage)
                                    if (state != VoiceNoteState.IDLE) {
                                        return@awaitEachGesture
                                    }

                                    // 2. Vérification de la permission micro
                                    if (!currentHasRecordPermission()) {
                                        currentOnRequestRecordPermission()
                                        return@awaitEachGesture
                                    }

                                    // 3. Démarrage immédiat de l'enregistrement
                                    val downTime = System.currentTimeMillis()
                                    val started = manager.startRecording()
                                    if (!started) {
                                        Toast.makeText(context, "Impossible de démarrer le micro", Toast.LENGTH_SHORT).show()
                                        return@awaitEachGesture
                                    }

                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                    var isCancelled = false
                                    var isLocked = false

                                    // 4. Boucle de suivi continu du doigt
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) {
                                            break
                                        }
                                        change.consume()

                                        val dx = change.position.x - down.position.x
                                        val dy = change.position.y - down.position.y

                                        dragOffsetX = dx.coerceAtMost(0f)
                                        dragOffsetY = dy.coerceAtMost(0f)

                                        // A. Glisser à GAUCHE -> Annuler
                                        if (dx < -cancelThresholdPx && !isLocked && !isCancelled) {
                                            isCancelled = true
                                            manager.cancelRecording()
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            Toast.makeText(context, "Enregistrement annulé", Toast.LENGTH_SHORT).show()
                                            break
                                        }

                                        // B. Glisser en HAUT -> Verrouiller mains libres
                                        if (dy < -lockThresholdPx && !isLocked && !isCancelled) {
                                            isLocked = true
                                            manager.lockRecording()
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            break
                                        }
                                    }

                                    dragOffsetX = 0f
                                    dragOffsetY = 0f

                                    if (isCancelled) {
                                        // Annulé par le swipe à gauche
                                    } else if (isLocked || manager.recordingState.value == VoiceNoteState.LOCKED) {
                                        // Mode verrouillé actif : l'utilisateur relâche son doigt en toute sécurité
                                    } else {
                                        // 5. Relâchement normal : envoi automatique si durée suffisante
                                        val durationMs = System.currentTimeMillis() - downTime
                                        if (durationMs >= 800) {
                                            val (file, secs) = manager.stopAndGetResult()
                                            if (file != null && file.length() > 0) {
                                                currentOnSendVoiceNote(file, secs)
                                            } else {
                                                Toast.makeText(context, "Erreur d'enregistrement", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            // Appui accidentel trop court (< 800ms)
                                            manager.cancelRecording()
                                            Toast.makeText(context, "Maintenez le micro pour enregistrer", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        } else {
                            Modifier
                        }
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            when {
                                recordingState == VoiceNoteState.LOCKED || recordingState == VoiceNoteState.PAUSED ->
                                    androidx.compose.ui.graphics.SolidColor(MikayalaColors.accent)
                                textInput.isNotBlank() ->
                                    androidx.compose.ui.graphics.SolidColor(MikayalaColors.secondary)
                                else -> androidx.compose.ui.graphics.SolidColor(MikayalaColors.secondary)
                            }
                        )
                        .clickable(
                            enabled = textInput.isNotBlank()
                        ) {
                            if (textInput.isNotBlank()) {
                                currentOnSendText()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = textInput.isNotBlank() || recordingState == VoiceNoteState.LOCKED || recordingState == VoiceNoteState.PAUSED,
                        transitionSpec = {
                            (fadeIn(tween(140)) + scaleIn(tween(140), initialScale = 0.82f)) togetherWith
                                (fadeOut(tween(110)) + scaleOut(tween(110), targetScale = 0.82f))
                        },
                        label = "send_mic_icon"
                    ) { isSend ->
                        Icon(
                            imageVector = if (isSend) AppleSvgIcons.Send else AppleSvgIcons.Mic,
                            contentDescription = if (isSend) "Envoyer" else "Note vocale",
                            tint = Color.White,
                            modifier = Modifier
                                .size(21.dp)
                                .graphicsLayer {
                                    if (!isSend && recordingState == VoiceNoteState.RECORDING) {
                                        scaleX = pulseScale
                                        scaleY = pulseScale
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
}
